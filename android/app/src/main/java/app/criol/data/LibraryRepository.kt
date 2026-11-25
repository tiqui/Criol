package app.criol.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

sealed interface ScanState {
    data object Idle : ScanState
    data class Running(val progress: ScanProgress) : ScanState
    data class Finished(val songCount: Int) : ScanState
    data class Failed(val message: String) : ScanState
}

class LibraryRepository(
    private val context: Context,
    private val scope: CoroutineScope,
    private val settings: SettingsStore,
) {
    private val cacheFile = File(context.filesDir, "library.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val scanner = LibraryScanner(context)

    private val _library = MutableStateFlow(Library.EMPTY)
    val library: StateFlow<Library> = _library.asStateFlow()
    val current: Library get() = _library.value

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private var cache = LibraryCache()
    private var scanJob: Job? = null

    fun init() {
        scope.launch(Dispatchers.IO) {
            cache = runCatching {
                if (cacheFile.exists()) json.decodeFromString<LibraryCache>(cacheFile.readText()) else LibraryCache()
            }.getOrDefault(LibraryCache())
            if (cache.version != LibraryCache.CACHE_VERSION) cache = LibraryCache()
            publish()
            _loaded.value = true
        }
    }

    private var openScanRequested = false

    /**
     * Escaneia ao abrir a tela do app (uma vez por processo). Não roda quando o processo
     * sobe só por causa do widget, do serviço de música ou do launcher lendo uma capa.
     */
    fun onAppOpened() {
        if (openScanRequested) return
        openScanRequested = true
        scope.launch {
            loaded.first { it }
            val s = settings.value
            if (s.scanOnStartup && s.folders.isNotEmpty()) rescan()
        }
    }

    private suspend fun publish() = withContext(Dispatchers.Default) {
        _library.value = Library.build(cache.songs, cache.artColors, scanner.artDir)
    }

    fun rescan(clearArt: Boolean = false) {
        if (scanJob?.isActive == true) return
        scanJob = scope.launch(Dispatchers.IO) {
            try {
                _scanState.value = ScanState.Running(ScanProgress("Procurando arquivos", 0, 0))
                val prev = if (clearArt) {
                    scanner.artDir.listFiles()?.forEach { it.delete() }
                    cache.copy(artColors = emptyMap(), noArt = emptySet())
                } else cache
                val result = scanner.scan(
                    roots = settings.value.folders,
                    lyricRoots = settings.value.lyricsFolders,
                    previous = prev,
                    minDurationMs = settings.value.minDurationSeconds * 1000L,
                ) { p -> _scanState.value = ScanState.Running(p) }
                cache = result
                publish()
                runCatching {
                    val tmp = File(cacheFile.parentFile, cacheFile.name + ".tmp")
                    tmp.writeText(json.encodeToString(result))
                    if (!tmp.renameTo(cacheFile)) { cacheFile.delete(); tmp.renameTo(cacheFile) }
                }
                _scanState.value = ScanState.Finished(result.songs.size)
            } catch (e: kotlinx.coroutines.CancellationException) {
                _scanState.value = ScanState.Idle
                throw e
            } catch (e: Exception) {
                _scanState.value = ScanState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun addFolder(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val value = uri.toString()
        if (value in settings.value.folders) return
        settings.update { it.copy(folders = it.folders + value) }
        restartScan()
    }

    fun removeFolder(uri: String) {
        settings.update { it.copy(folders = it.folders - uri) }
        releaseIfUnused(uri)
        restartScan()
    }

    /** Pasta só de letras: os .lrc/.txt dela são casados com as músicas pelo nome. */
    fun addLyricsFolder(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val value = uri.toString()
        if (value in settings.value.lyricsFolders) return
        settings.update { it.copy(lyricsFolders = it.lyricsFolders + value) }
        restartScan()
    }

    fun removeLyricsFolder(uri: String) {
        settings.update { it.copy(lyricsFolders = it.lyricsFolders - uri) }
        releaseIfUnused(uri)
        restartScan()
    }

    /** A mesma pasta pode estar nas duas listas; só solta a permissão se não for mais usada. */
    private fun releaseIfUnused(uri: String) {
        val s = settings.value
        if (uri in s.folders || uri in s.lyricsFolders) return
        runCatching {
            context.contentResolver.releasePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun restartScan() {
        scope.launch {
            scanJob?.let { it.cancel(); it.join() }
            rescan()
        }
    }

    fun artPath(albumId: String): String? = current.artPath(albumId)
}

/** Índices em memória calculados a partir das faixas. */
class Library(
    val songs: List<Song>,
    val songsById: Map<String, Song>,
    val albums: List<Album>,
    val albumsById: Map<String, Album>,
    val artists: List<Artist>,
    val artistsById: Map<String, Artist>,
    val genres: List<Genre>,
    val genresById: Map<String, Genre>,
    val rootFolders: List<FolderNode>,
    val foldersByPath: Map<String, FolderNode>,
    private val artColors: Map<String, Int>,
    private val artDir: File?,
) {
    private val songKeys = songs.map { "${it.title} ${it.artist} ${it.album}".normalizeForSearch() }
    private val albumKeys = albums.map { "${it.name} ${it.artist}".normalizeForSearch() }
    private val artistKeys = artists.map { it.name.normalizeForSearch() }

    val isEmpty get() = songs.isEmpty()

    fun artPath(albumId: String?): String? =
        if (albumId != null && artDir != null && albumId in artColors) File(artDir, "$albumId.jpg").path else null

    fun artColor(albumId: String?): Int? = albumId?.let { artColors[it] }

    fun artistIdFor(name: String) = hashId("artist:" + name.normalizeForSearch())

    data class SearchResult(val songs: List<Song>, val albums: List<Album>, val artists: List<Artist>)

    fun search(query: String): SearchResult {
        val terms = query.normalizeForSearch().split(' ').filter { it.isNotBlank() }
        if (terms.isEmpty()) return SearchResult(emptyList(), emptyList(), emptyList())
        fun matches(key: String) = terms.all { key.contains(it) }
        return SearchResult(
            songs = songs.indices.filter { matches(songKeys[it]) }.take(200).map { songs[it] },
            albums = albums.indices.filter { matches(albumKeys[it]) }.take(50).map { albums[it] },
            artists = artists.indices.filter { matches(artistKeys[it]) }.take(50).map { artists[it] },
        )
    }

    companion object {
        val EMPTY = Library(
            emptyList(), emptyMap(), emptyList(), emptyMap(), emptyList(), emptyMap(),
            emptyList(), emptyMap(), emptyList(), emptyMap(), emptyMap(), null,
        )

        /** Ordena calculando a chave (normalizada) só uma vez por item. */
        private inline fun <T> List<T>.sortedByText(crossinline key: (T) -> String): List<T> =
            map { it to key(it).normalizeForSearch() }.sortedBy { it.second }.map { it.first }

        fun build(allSongs: List<Song>, artColors: Map<String, Int>, artDir: File): Library {
            fun art(albumId: String) = if (albumId in artColors) File(artDir, "$albumId.jpg").path else null

            val songs = allSongs.sortedByText { it.title }
            val songsById = songs.associateBy { it.id }
            val rank = HashMap<String, Int>(songs.size * 2).apply { songs.forEachIndexed { i, s -> put(s.id, i) } }

            // Álbuns
            val albums = songs.groupBy { it.albumId }.map { (id, list) ->
                val tracks = list.sortedWith(compareBy({ it.disc }, { it.track }, { it.fileName }))
                val albumArtists = tracks.flatMap { it.albumArtists }.distinct()
                val displayArtist = when {
                    tracks.any { it.albumArtist.isNotBlank() } ->
                        tracks.first { it.albumArtist.isNotBlank() }.albumArtist
                    tracks.map { it.artist }.distinct().size == 1 -> tracks.first().artist
                    else -> "Vários artistas"
                }
                Album(
                    id = id,
                    name = tracks.first().album,
                    artist = displayArtist,
                    artistNames = if (displayArtist == "Vários artistas") listOf(displayArtist) else albumArtists,
                    year = tracks.maxOf { it.year },
                    songs = tracks,
                    durationMs = tracks.sumOf { it.durationMs },
                    dateAdded = tracks.maxOf { it.dateAdded },
                    genres = tracks.flatMap { splitGenres(it.genre) }.distinct(),
                    artPath = art(id),
                    color = artColors[id],
                    folder = tracks.first().folder,
                )
            }.sortedByText { it.name }
            val albumsById = albums.associateBy { it.id }

            // Artistas (mapas indexados por id para evitar hash de objetos grandes)
            class ArtistAcc(val name: String) {
                val albums = LinkedHashMap<String, Album>()
                val appears = LinkedHashMap<String, Album>()
                val songs = LinkedHashMap<String, Song>()
            }
            val acc = LinkedHashMap<String, ArtistAcc>()
            fun accFor(name: String) = acc.getOrPut(name.normalizeForSearch()) { ArtistAcc(name) }
            for (album in albums) {
                for (name in album.artistNames) accFor(name).albums[album.id] = album
                for (song in album.songs) {
                    val names = (song.artists + song.albumArtists).distinctBy { it.normalizeForSearch() }
                    for (name in names) {
                        val a = accFor(name)
                        a.songs[song.id] = song
                        if (!a.albums.containsKey(album.id)) a.appears[album.id] = album
                    }
                }
            }
            val artists = acc.map { (key, a) ->
                val withArt = (a.albums.values.asSequence() + a.appears.values.asSequence())
                    .firstOrNull { it.artPath != null }
                Artist(
                    id = hashId("artist:$key"),
                    name = a.name,
                    albums = a.albums.values.sortedByDescending { it.year },
                    appearsOn = a.appears.values.filter { !a.albums.containsKey(it.id) }.sortedByDescending { it.year },
                    songs = a.songs.values.sortedBy { rank[it.id] ?: 0 },
                    artPath = withArt?.artPath,
                    color = withArt?.color,
                )
            }.sortedByText { it.name }

            // Gêneros
            val genreAcc = LinkedHashMap<String, Pair<String, MutableList<Song>>>()
            for (song in songs) for (g in splitGenres(song.genre).distinctBy { it.normalizeForSearch() }) {
                genreAcc.getOrPut(g.normalizeForSearch()) { g to mutableListOf() }.second += song
            }
            val genres = genreAcc.map { (key, pair) ->
                val genreAlbums = pair.second.map { it.albumId }.distinct().mapNotNull { albumsById[it] }
                val withArt = genreAlbums.firstOrNull { it.artPath != null }
                Genre(
                    id = hashId("genre:$key"),
                    name = pair.first,
                    songs = pair.second,
                    albums = genreAlbums,
                    artPath = withArt?.artPath,
                    color = withArt?.color,
                )
            }.sortedByDescending { it.songs.size }

            // Pastas
            val folderSongs = songs.groupBy { it.folder }
            val allPaths = LinkedHashSet<String>()
            for (path in folderSongs.keys) {
                var p = path
                while (true) {
                    if (!allPaths.add(p)) break
                    val idx = p.lastIndexOf('/')
                    if (idx <= 0) break
                    p = p.substring(0, idx)
                }
            }
            val childrenOf = allPaths.groupBy { p -> p.lastIndexOf('/').let { if (it <= 0) "" else p.substring(0, it) } }
            val nodes = HashMap<String, FolderNode>()
            fun buildNode(path: String): FolderNode {
                nodes[path]?.let { return it }
                val children = (childrenOf[path] ?: emptyList())
                    .map { buildNode(it) }
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                val own = (folderSongs[path] ?: emptyList())
                    .sortedWith(compareBy({ it.disc }, { it.track }, { it.fileName.lowercase() }))
                val node = FolderNode(
                    path = path,
                    name = path.substringAfterLast('/'),
                    children = children,
                    songs = own,
                    artPath = own.firstNotNullOfOrNull { art(it.albumId) }
                        ?: children.firstNotNullOfOrNull { it.artPath },
                    totalSongs = own.size + children.sumOf { it.totalSongs },
                )
                nodes[path] = node
                return node
            }
            val roots = (childrenOf[""] ?: emptyList()).map { buildNode(it) }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

            return Library(
                songs = songs,
                songsById = songsById,
                albums = albums,
                albumsById = albumsById,
                artists = artists,
                artistsById = artists.associateBy { it.id },
                genres = genres,
                genresById = genres.associateBy { it.id },
                rootFolders = roots,
                foldersByPath = nodes,
                artColors = artColors,
                artDir = artDir,
            )
        }

        fun splitGenres(value: String): List<String> =
            value.split(';', ',', '\u0000').map { it.trim() }.filter { it.isNotEmpty() }
    }
}
