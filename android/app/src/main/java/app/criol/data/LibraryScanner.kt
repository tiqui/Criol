package app.criol.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max

data class ScanProgress(val phase: String, val done: Int, val total: Int)

/**
 * Percorre as pastas escolhidas pelo usuário (Storage Access Framework),
 * lê as tags com MediaMetadataRetriever e extrai as capas para o cache local.
 */
class LibraryScanner(private val context: Context) {
    private val cr = context.contentResolver
    val mediaStore = MediaStoreIndex(context)
    val artDir = File(context.filesDir, "art").apply { mkdirs() }

    private data class AudioFile(
        val uri: Uri,
        val rootUri: String,
        val name: String,
        val mime: String,
        val lastModified: Long,
        val size: Long,
        val folder: String,
    )

    private class FolderInfo {
        val images = mutableListOf<Pair<String, Uri>>()
    }

    suspend fun scan(
        roots: List<String>,
        lyricRoots: List<String>,
        previous: LibraryCache,
        minDurationMs: Long,
        onProgress: (ScanProgress) -> Unit,
    ): LibraryCache = withContext(Dispatchers.IO) {
        val files = mutableListOf<AudioFile>()
        val folders = HashMap<String, FolderInfo>()
        val seenDocs = HashSet<String>()
        val lyrics = LyricsIndex()

        for (root in roots) {
            ensureActive()
            val treeUri = Uri.parse(root)
            val rootDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
                ?: continue
            val rootName = runCatching { DocumentFile.fromTreeUri(context, treeUri)?.name }.getOrNull()
                ?: rootDocId.substringAfterLast(':').substringAfterLast('/').ifBlank { "Música" }
            walk(treeUri, root, rootDocId, rootName, files, folders, seenDocs, lyrics, lyricsOnly = false, onProgress)
        }
        // Pastas só de letras (.lrc / .txt), em qualquer lugar do aparelho.
        for (root in lyricRoots) {
            ensureActive()
            val treeUri = Uri.parse(root)
            val rootDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
                ?: continue
            onProgress(ScanProgress("Procurando letras", lyrics.size, 0))
            walk(treeUri, root, rootDocId, LYRICS_PREFIX, files, folders, seenDocs, lyrics, lyricsOnly = true, onProgress)
        }

        val prevByUri = previous.songs.associateBy { it.uri }
        val artColors = ConcurrentHashMap(previous.artColors)
        val noArt = ConcurrentHashMap.newKeySet<String>().apply { addAll(previous.noArt) }
        val artClaimed = ConcurrentHashMap.newKeySet<String>().apply {
            addAll(previous.artColors.keys)
        }
        val total = files.size
        val done = AtomicInteger()
        val now = System.currentTimeMillis()
        onProgress(ScanProgress("Lendo metadados", 0, total))
        // Tags do índice do Android numa consulta só (com permissão). Só para arquivos novos/alterados.
        val needsTags = files.any { f ->
            val prev = prevByUri[f.uri.toString()]
            prev == null || prev.lastModified != f.lastModified || prev.size != f.size
        }
        val index = if (needsTags) mediaStore.load() else emptyMap()

        val workers = Dispatchers.IO.limitedParallelism(4)
        val songs = coroutineScope {
            files.map { f ->
                async(workers) {
                    ensureActive()
                    val info = folders[f.folder]
                    val prev = prevByUri[f.uri.toString()]
                    val song = if (prev != null && prev.lastModified == f.lastModified && prev.size == f.size) {
                        prev.copy(folder = f.folder, rootUri = f.rootUri)
                    } else {
                        val row = MediaStoreIndex.keyFor(f.uri)?.let(index::get)?.takeIf { it.matches(f.size, f.lastModified) }
                        if (row != null) songFromIndex(f, row, prev, now) else readSong(f, prev, info, now, artColors, artClaimed)
                    }
                    val n = done.incrementAndGet()
                    if (n % 25 == 0 || n == total) onProgress(ScanProgress("Lendo metadados", n, total))
                    song
                }
            }.awaitAll()
        }.filterNotNull()
            .filter { s -> minDurationMs <= 0 || s.durationMs == 0L || s.durationMs >= minDurationMs }
            // Letras: procurado depois das tags, para poder casar por artista e título.
            .map { s -> s.copy(lyricsUri = lyrics.find(s)) }

        // Capas que ainda faltam: imagem da pasta ou, para faixas reaproveitadas do cache, a embutida.
        val missing = songs.groupBy { it.albumId }.filterKeys { !artColors.containsKey(it) && it !in noArt }
        var artDone = 0
        for ((albumId, albumSongs) in missing) {
            ensureActive()
            artDone++
            if (artDone % 10 == 0) onProgress(ScanProgress("Procurando capas", artDone, missing.size))
            val folderImage = albumSongs.asSequence().map { it.folder }.distinct()
                .mapNotNull { folders[it]?.let(::pickCover) }.firstOrNull()
            val color = if (folderImage != null) {
                saveArt(albumId, null, folderImage)
            } else {
                albumSongs.take(3).firstNotNullOfOrNull { s ->
                    embeddedPicture(Uri.parse(s.uri))?.let { saveArt(albumId, it, null) }
                }
            }
            if (color != null) artColors[albumId] = color else noArt.add(albumId)
        }

        val albumIds = songs.mapTo(HashSet()) { it.albumId }
        // Remove capas órfãs.
        artDir.listFiles()?.forEach { file ->
            if (file.nameWithoutExtension !in albumIds) file.delete()
        }

        LibraryCache(
            songs = songs,
            artColors = artColors.filterKeys { it in albumIds },
            noArt = noArt.filterTo(HashSet()) { it in albumIds },
            scannedAt = System.currentTimeMillis(),
        )
    }

    private fun walk(
        treeUri: Uri,
        rootUri: String,
        rootDocId: String,
        rootName: String,
        out: MutableList<AudioFile>,
        folders: MutableMap<String, FolderInfo>,
        seenDocs: MutableSet<String>,
        lyrics: LyricsIndex,
        lyricsOnly: Boolean,
        onProgress: (ScanProgress) -> Unit,
    ) {
        val stack = ArrayDeque<Pair<String, String>>()
        stack.addLast(rootDocId to rootName)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        while (stack.isNotEmpty()) {
            val (docId, path) = stack.removeLast()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            val cursor = runCatching { cr.query(childrenUri, projection, null, null, null) }.getOrNull()
                ?: continue
            cursor.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    val mime = c.getString(2) ?: ""
                    if (name.startsWith(".")) continue
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        stack.addLast(id to "$path/$name")
                        continue
                    }
                    val ext = name.substringAfterLast('.', "").lowercase()
                    val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                    if (lyricsOnly) {
                        if (ext in LyricsIndex.EXTENSIONS) {
                            lyrics.add(name, path, uri.toString())
                            if (lyrics.size % 100 == 0) onProgress(ScanProgress("Procurando letras", lyrics.size, 0))
                        }
                        continue
                    }
                    when {
                        ext in AUDIO_EXT || (mime.startsWith("audio/") && ext !in BAD_AUDIO_EXT) -> {
                            if (!seenDocs.add(id)) continue
                            out += AudioFile(
                                uri = uri,
                                rootUri = rootUri,
                                name = name,
                                mime = mime,
                                lastModified = if (c.isNull(3)) 0 else c.getLong(3),
                                size = if (c.isNull(4)) 0 else c.getLong(4),
                                folder = path,
                            )
                            if (out.size % 50 == 0) onProgress(ScanProgress("Procurando arquivos", out.size, 0))
                        }
                        ext in IMAGE_EXT -> folders.getOrPut(path, ::FolderInfo).images += name to uri
                        ext == "lrc" -> lyrics.add(name, path, uri.toString())
                    }
                }
            }
        }
    }

    private fun readSong(
        f: AudioFile,
        prev: Song?,
        info: FolderInfo?,
        now: Long,
        artColors: MutableMap<String, Int>,
        artClaimed: MutableSet<String>,
    ): Song? {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(context, f.uri)
            fun k(key: Int) = runCatching { mmr.extractMetadata(key) }.getOrNull()
                ?.trim()?.takeIf { it.isNotEmpty() }

            val folderName = f.folder.substringAfterLast('/')
            val title = k(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: f.name.substringBeforeLast('.')
            val albumArtist = k(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST) ?: ""
            val artist = k(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: albumArtist.ifBlank { "Artista desconhecido" }
            val album = k(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: folderName
            val year = parseYear(k(MediaMetadataRetriever.METADATA_KEY_YEAR))
                ?: parseYear(k(MediaMetadataRetriever.METADATA_KEY_DATE)) ?: 0
            val sampleRate = if (Build.VERSION.SDK_INT >= 31) {
                k(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull() ?: 0
            } else 0

            val albumId = albumIdFor(album, albumArtist, f.folder)

            // Capa embutida, só quando a pasta não tem imagem própria.
            if (info?.let(::pickCover) == null && albumId !in artColors && artClaimed.add(albumId)) {
                val pic = runCatching { mmr.embeddedPicture }.getOrNull()
                val color = pic?.let { saveArt(albumId, it, null) }
                if (color != null) artColors[albumId] = color else artClaimed.remove(albumId)
            }

            Song(
                id = hashId(f.uri.toString()),
                uri = f.uri.toString(),
                rootUri = f.rootUri,
                title = title,
                artist = artist,
                albumArtist = albumArtist,
                album = album,
                albumId = albumId,
                genre = normalizeGenre(k(MediaMetadataRetriever.METADATA_KEY_GENRE)),
                year = year,
                track = parseNumber(k(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)),
                disc = parseNumber(k(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)),
                durationMs = k(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0,
                folder = f.folder,
                fileName = f.name,
                size = f.size,
                lastModified = f.lastModified,
                dateAdded = prev?.dateAdded ?: f.lastModified.takeIf { it > 0 } ?: now,
                mime = k(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: f.mime,
                bitrate = k(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0,
                sampleRate = sampleRate,
                composer = k(MediaMetadataRetriever.METADATA_KEY_COMPOSER) ?: "",
            )
        } catch (_: Exception) {
            null
        } finally {
            runCatching { mmr.release() }
        }
    }

    /**
     * Música montada com as tags do índice do Android, sem abrir o arquivo. Mesmas regras de
     * [readSong] (título pelo nome do arquivo, álbum pela pasta...). A capa embutida, se a pasta
     * não tiver imagem, é extraída depois, uma vez por álbum.
     */
    private fun songFromIndex(f: AudioFile, r: MediaStoreIndex.Row, prev: Song?, now: Long): Song {
        val albumArtist = r.albumArtist ?: ""
        val album = r.album ?: f.folder.substringAfterLast('/')
        return Song(
            id = hashId(f.uri.toString()),
            uri = f.uri.toString(),
            rootUri = f.rootUri,
            title = r.title ?: f.name.substringBeforeLast('.'),
            artist = r.artist ?: albumArtist.ifBlank { "Artista desconhecido" },
            albumArtist = albumArtist,
            album = album,
            albumId = albumIdFor(album, albumArtist, f.folder),
            genre = normalizeGenre(r.genre),
            year = r.year.takeIf { it in 1000..2999 } ?: 0,
            track = r.track,
            disc = r.disc,
            durationMs = r.durationMs,
            folder = f.folder,
            fileName = f.name,
            size = f.size,
            lastModified = f.lastModified,
            dateAdded = prev?.dateAdded ?: f.lastModified.takeIf { it > 0 } ?: now,
            mime = r.mime ?: f.mime,
            bitrate = r.bitrate,
            composer = r.composer ?: "",
        )
    }

    private fun albumIdFor(album: String, albumArtist: String, folder: String): String =
        hashId(album.normalizeForSearch() + "|" + albumArtist.normalizeForSearch().ifBlank { folder.lowercase() })

    private fun embeddedPicture(uri: Uri): ByteArray? {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(context, uri)
            mmr.embeddedPicture
        } catch (_: Exception) {
            null
        } finally {
            runCatching { mmr.release() }
        }
    }

    private fun pickCover(info: FolderInfo): Uri? {
        if (info.images.isEmpty()) return null
        val ranked = info.images.sortedBy { (name, _) ->
            val base = name.substringBeforeLast('.').lowercase()
            COVER_NAMES.indexOfFirst { base == it || base.startsWith(it) }.let { if (it < 0) 99 else it }
        }
        val best = ranked.first()
        val base = best.first.substringBeforeLast('.').lowercase()
        val isKnown = COVER_NAMES.any { base == it || base.startsWith(it) }
        // Se o nome não é conhecido, só usa quando há uma única imagem na pasta.
        return if (isKnown || info.images.size == 1) best.second else null
    }

    /** Salva a capa redimensionada em disco e devolve a cor dominante. */
    private fun saveArt(albumId: String, bytes: ByteArray?, uri: Uri?): Int? = runCatching {
        fun decode(opts: BitmapFactory.Options): Bitmap? = when {
            bytes != null -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            uri != null -> cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            else -> null
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        decode(bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= ART_SIZE) sample *= 2
        val decoded = decode(BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val longest = max(decoded.width, decoded.height)
        val bmp = if (longest > ART_SIZE * 1.3) {
            val scale = ART_SIZE.toFloat() / longest
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
        } else decoded
        File(artDir, "$albumId.jpg").outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        val palette = Palette.from(bmp).maximumColorCount(16).generate()
        val color = palette.dominantSwatch?.rgb ?: palette.mutedSwatch?.rgb ?: 0xFF333333.toInt()
        if (bmp !== decoded) bmp.recycle()
        decoded.recycle()
        color
    }.getOrNull()

    companion object {
        private const val ART_SIZE = 800

        /** Prefixo das pastas de letras: nunca coincide com a pasta de uma música. */
        private const val LYRICS_PREFIX = "\u0001letras"
        val AUDIO_EXT = setOf(
            "mp3", "flac", "m4a", "m4b", "aac", "ogg", "oga", "opus", "wav", "aif", "aiff",
            "mka", "webm", "3gp", "amr", "alac",
        )
        private val BAD_AUDIO_EXT = setOf("mid", "midi", "wma", "ape", "m3u", "m3u8", "pls")
        private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp")
        private val COVER_NAMES = listOf("cover", "folder", "front", "album", "albumart", "artwork", "capa")

        fun parseYear(v: String?): Int? {
            if (v == null) return null
            val m = Regex("(\\d{4})").find(v) ?: return null
            return m.value.toIntOrNull()?.takeIf { it in 1000..2999 }
        }

        fun parseNumber(v: String?): Int =
            v?.substringBefore('/')?.trim()?.toIntOrNull() ?: 0

        fun normalizeGenre(v: String?): String {
            if (v == null) return ""
            val m = Regex("^\\(?(\\d{1,3})\\)?$").find(v.trim())
            if (m != null) {
                val idx = m.groupValues[1].toInt()
                return ID3_GENRES.getOrNull(idx) ?: v
            }
            return v
        }

        private val ID3_GENRES = (
            "Blues,Classic Rock,Country,Dance,Disco,Funk,Grunge,Hip-Hop,Jazz,Metal,New Age,Oldies," +
                "Other,Pop,R&B,Rap,Reggae,Rock,Techno,Industrial,Alternative,Ska,Death Metal,Pranks," +
                "Soundtrack,Euro-Techno,Ambient,Trip-Hop,Vocal,Jazz+Funk,Fusion,Trance,Classical," +
                "Instrumental,Acid,House,Game,Sound Clip,Gospel,Noise,Alternative Rock,Bass,Soul,Punk," +
                "Space,Meditative,Instrumental Pop,Instrumental Rock,Ethnic,Gothic,Darkwave," +
                "Techno-Industrial,Electronic,Pop-Folk,Eurodance,Dream,Southern Rock,Comedy,Cult," +
                "Gangsta,Top 40,Christian Rap,Pop/Funk,Jungle,Native American,Cabaret,New Wave," +
                "Psychedelic,Rave,Showtunes,Trailer,Lo-Fi,Tribal,Acid Punk,Acid Jazz,Polka,Retro," +
                "Musical,Rock & Roll,Hard Rock"
            ).split(',')
    }
}
