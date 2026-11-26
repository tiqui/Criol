package app.criol.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlin.math.abs

/**
 * Índice de músicas do próprio Android (MediaStore), a mesma fonte que o Boo usa.
 *
 * O Criol continua respeitando as pastas escolhidas (SAF), mas, com a permissão de ler músicas,
 * pega as tags daqui em uma consulta só, em vez de abrir arquivo por arquivo. Também serve
 * para saber se algo mudou desde a última leitura ([generations]) e para avisar quando
 * aparecem músicas novas ([observe]).
 */
class MediaStoreIndex(private val context: Context) {

    /** Tags de um arquivo segundo o Android. */
    data class Row(
        val title: String?,
        val artist: String?,
        val albumArtist: String?,
        val album: String?,
        val genre: String?,
        val year: Int,
        val track: Int,
        val disc: Int,
        val durationMs: Long,
        val mime: String?,
        val bitrate: Int,
        val composer: String?,
        val size: Long,
        val dateModifiedMs: Long,
    ) {
        /** Só confia no índice se ele descreve exatamente o arquivo que a varredura achou. */
        fun matches(size: Long, lastModified: Long): Boolean =
            this.size == size && (lastModified <= 0 || abs(dateModifiedMs - lastModified) <= 2_000)
    }

    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** Todas as músicas indexadas, pela chave de [keyFor]. Vazio sem permissão ou em erro. */
    fun load(): Map<String, Row> {
        if (!hasPermission()) return emptyMap()
        return runCatching { query() }.getOrDefault(emptyMap())
    }

    private fun query(): Map<String, Row> {
        val api30 = Build.VERSION.SDK_INT >= 30
        val cols = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.VOLUME_NAME)
            add(MediaStore.Audio.Media.RELATIVE_PATH)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(ALBUM_ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.YEAR)
            add(MediaStore.Audio.Media.TRACK)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.MIME_TYPE)
            add(MediaStore.Audio.Media.COMPOSER)
            add(MediaStore.Audio.Media.SIZE)
            add(MediaStore.Audio.Media.DATE_MODIFIED)
            if (api30) {
                add(MediaStore.Audio.Media.GENRE)
                add(MediaStore.Audio.Media.BITRATE)
                add(MediaStore.Audio.Media.DISC_NUMBER)
            }
        }
        val genresById = if (api30) emptyMap() else genresApi29()
        val uri = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val out = HashMap<String, Row>()
        context.contentResolver.query(uri, cols.toTypedArray(), null, null, null)?.use { c ->
            fun idx(name: String) = c.getColumnIndex(name)
            val iId = idx(MediaStore.Audio.Media._ID)
            val iVol = idx(MediaStore.Audio.Media.VOLUME_NAME)
            val iRel = idx(MediaStore.Audio.Media.RELATIVE_PATH)
            val iName = idx(MediaStore.Audio.Media.DISPLAY_NAME)
            val iTitle = idx(MediaStore.Audio.Media.TITLE)
            val iArtist = idx(MediaStore.Audio.Media.ARTIST)
            val iAlbumArtist = idx(ALBUM_ARTIST)
            val iAlbum = idx(MediaStore.Audio.Media.ALBUM)
            val iYear = idx(MediaStore.Audio.Media.YEAR)
            val iTrack = idx(MediaStore.Audio.Media.TRACK)
            val iDur = idx(MediaStore.Audio.Media.DURATION)
            val iMime = idx(MediaStore.Audio.Media.MIME_TYPE)
            val iComposer = idx(MediaStore.Audio.Media.COMPOSER)
            val iSize = idx(MediaStore.Audio.Media.SIZE)
            val iMod = idx(MediaStore.Audio.Media.DATE_MODIFIED)
            val iGenre = if (api30) idx(MediaStore.Audio.Media.GENRE) else -1
            val iBitrate = if (api30) idx(MediaStore.Audio.Media.BITRATE) else -1
            val iDisc = if (api30) idx(MediaStore.Audio.Media.DISC_NUMBER) else -1

            fun str(i: Int) = if (i < 0 || c.isNull(i)) null else c.getString(i)?.trim()?.takeIf { v ->
                v.isNotEmpty() && v != MediaStore.UNKNOWN_STRING
            }
            fun int(i: Int) = if (i < 0 || c.isNull(i)) 0 else c.getInt(i)
            fun long(i: Int) = if (i < 0 || c.isNull(i)) 0L else c.getLong(i)

            while (c.moveToNext()) {
                val key = key(str(iVol) ?: continue, c.getString(iRel) ?: "", c.getString(iName) ?: continue)
                // TRACK guarda disco e faixa juntos: 2005 = disco 2, faixa 5.
                val rawTrack = int(iTrack)
                val disc = LibraryScanner.parseNumber(str(iDisc)).takeIf { it > 0 } ?: (rawTrack / 1000)
                out[key] = Row(
                    title = str(iTitle),
                    artist = str(iArtist),
                    albumArtist = str(iAlbumArtist),
                    album = str(iAlbum),
                    genre = str(iGenre) ?: genresById[long(iId)],
                    year = int(iYear),
                    track = rawTrack % 1000,
                    disc = disc,
                    durationMs = long(iDur),
                    mime = str(iMime),
                    bitrate = int(iBitrate),
                    composer = str(iComposer),
                    size = long(iSize),
                    dateModifiedMs = long(iMod) * 1000,
                )
            }
        }
        return out
    }

    /** Android 10 não tem a coluna de gênero na tabela de músicas: vem da tabela de gêneros. */
    private fun genresApi29(): Map<Long, String> = runCatching {
        val out = HashMap<Long, String>()
        val cr = context.contentResolver
        val genres = cr.query(
            MediaStore.Audio.Genres.getContentUri(MediaStore.VOLUME_EXTERNAL),
            arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME), null, null, null,
        )?.use { c -> buildList { while (c.moveToNext()) add(c.getLong(0) to (c.getString(1) ?: "")) } }.orEmpty()
        for ((id, name) in genres) {
            if (name.isBlank()) continue
            @Suppress("DEPRECATION")
            cr.query(
                MediaStore.Audio.Genres.Members.getContentUri(MediaStore.VOLUME_EXTERNAL, id),
                arrayOf(MediaStore.Audio.Genres.Members.AUDIO_ID), null, null, null,
            )?.use { c -> while (c.moveToNext()) out.putIfAbsent(c.getLong(0), name) }
        }
        out
    }.getOrDefault(emptyMap())

    /**
     * Contador de mudanças de cada volume (Android 11+). Se não mudou desde a última leitura,
     * nenhum arquivo foi criado, apagado ou alterado e dá para pular a varredura das pastas.
     */
    fun generations(): Map<String, Long>? {
        if (Build.VERSION.SDK_INT < 30) return null
        return runCatching {
            MediaStore.getExternalVolumeNames(context).associateWith { MediaStore.getGeneration(context, it) }
        }.getOrNull()
    }

    /** Avisa (com atraso, juntando rajadas de eventos) quando o índice de músicas muda. */
    fun observe(onChange: () -> Unit): ContentObserver {
        val handler = Handler(Looper.getMainLooper())
        val fire = Runnable(onChange)
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                handler.removeCallbacks(fire)
                handler.postDelayed(fire, OBSERVER_DELAY_MS)
            }
        }
        context.contentResolver.registerContentObserver(
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL), true, observer,
        )
        return observer
    }

    companion object {
        val permission: String =
            if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

        /** Existe desde antes do Android 10, mas a constante pública só veio no 11. */
        private const val ALBUM_ARTIST = "album_artist"
        private const val OBSERVER_DELAY_MS = 4_000L
        private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

        private fun key(volume: String, relativePath: String, name: String): String {
            val root = if (volume == MediaStore.VOLUME_EXTERNAL_PRIMARY) "primary" else volume
            return "$root:$relativePath$name".lowercase()
        }

        /**
         * Chave do arquivo achado pelas pastas escolhidas. Só existe para pastas do armazenamento
         * do aparelho/cartão (o id do documento é "primary:Music/Artista/faixa.mp3").
         */
        fun keyFor(documentUri: Uri): String? {
            if (documentUri.authority != EXTERNAL_STORAGE_AUTHORITY) return null
            val docId = runCatching { DocumentsContract.getDocumentId(documentUri) }.getOrNull() ?: return null
            if (':' !in docId) return null
            return docId.lowercase()
        }

        /** As pastas só podem pular a varredura se todas forem do armazenamento local. */
        fun coversAll(treeUris: List<String>): Boolean =
            treeUris.all { Uri.parse(it).authority == EXTERNAL_STORAGE_AUTHORITY }
    }
}
