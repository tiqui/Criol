package app.criol.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/** Favoritos, avaliações, contagem de reproduções e playlists. Salvo em JSON. */
@OptIn(FlowPreview::class)
class UserDataStore(context: Context, scope: CoroutineScope) {
    private val file = File(context.filesDir, "userdata.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    private val _data = MutableStateFlow(load())
    val data: StateFlow<UserData> = _data.asStateFlow()
    val value: UserData get() = _data.value

    init {
        _data.drop(1).debounce(400).onEach { save(it) }
            .launchIn(CoroutineScope(scope.coroutineContext + Dispatchers.IO))
    }

    private fun load(): UserData = runCatching {
        if (file.exists()) json.decodeFromString<UserData>(file.readText()) else UserData()
    }.getOrDefault(UserData())

    private fun save(data: UserData) {
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(data))
            tmp.renameTo(file) || run { file.delete(); tmp.renameTo(file) }
        }
    }

    // ---- Favoritos ----
    fun toggleFavoriteSong(id: String) = _data.update {
        it.copy(favoriteSongs = it.favoriteSongs.toggle(id))
    }

    fun toggleFavoriteAlbum(id: String) = _data.update {
        it.copy(favoriteAlbums = it.favoriteAlbums.toggle(id))
    }

    fun toggleFavoriteArtist(id: String) = _data.update {
        it.copy(favoriteArtists = it.favoriteArtists.toggle(id))
    }

    private fun Set<String>.toggle(id: String) = if (id in this) this - id else this + id

    // ---- Avaliação (0 a 5) ----
    fun setRating(id: String, rating: Int) = _data.update {
        val r = rating.coerceIn(0, 5)
        it.copy(ratings = if (r == 0) it.ratings - id else it.ratings + (id to r))
    }

    // ---- Reproduções ----
    fun registerPlay(songId: String) = _data.update {
        it.copy(
            playCounts = it.playCounts + (songId to ((it.playCounts[songId] ?: 0) + 1)),
            lastPlayed = it.lastPlayed + (songId to System.currentTimeMillis()),
        )
    }

    // ---- Playlists ----
    fun createPlaylist(name: String, songIds: List<String> = emptyList()): String {
        val id = UUID.randomUUID().toString()
        _data.update { it.copy(playlists = it.playlists + Playlist(id, name.trim(), songIds)) }
        return id
    }

    fun renamePlaylist(id: String, name: String) = editPlaylist(id) { it.copy(name = name.trim()) }

    fun deletePlaylist(id: String) = _data.update {
        it.copy(playlists = it.playlists.filterNot { p -> p.id == id })
    }

    fun addToPlaylist(id: String, songIds: List<String>) =
        editPlaylist(id) { it.copy(songIds = it.songIds + songIds) }

    fun removeFromPlaylist(id: String, index: Int) = editPlaylist(id) {
        if (index !in it.songIds.indices) it
        else it.copy(songIds = it.songIds.toMutableList().apply { removeAt(index) })
    }

    fun movePlaylistItem(id: String, from: Int, to: Int) = editPlaylist(id) {
        if (from !in it.songIds.indices || to !in it.songIds.indices) it
        else it.copy(songIds = it.songIds.toMutableList().apply { add(to, removeAt(from)) })
    }

    private fun editPlaylist(id: String, transform: (Playlist) -> Playlist) = _data.update {
        it.copy(playlists = it.playlists.map { p ->
            if (p.id == id) transform(p).copy(updated = System.currentTimeMillis()) else p
        })
    }
}

/** Fila salva entre sessões. */
class QueueStore(context: Context) {
    private val file = File(context.filesDir, "queue.json")
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): QueueSnapshot? = runCatching {
        if (file.exists()) json.decodeFromString<QueueSnapshot>(file.readText()) else null
    }.getOrNull()

    fun save(snapshot: QueueSnapshot) {
        runCatching { file.writeText(json.encodeToString(snapshot)) }
    }
}
