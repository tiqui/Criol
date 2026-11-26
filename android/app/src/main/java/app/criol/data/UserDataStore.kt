package app.criol.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Favoritos, avaliações, contagem de reproduções e playlists.
 *
 * Fica tudo em memória em [data] (as telas leem dali) e cada mudança grava só a linha que
 * mudou no [CriolDatabase]. Antes o arquivo userdata.json inteiro era regravado a cada
 * reprodução; na primeira abertura com o banco, ele é importado e guardado como
 * userdata.json.migrated.
 */
class UserDataStore(context: Context, scope: CoroutineScope) {
    private val db = CriolDatabase.get(context)
    private val legacyFile = File(context.filesDir, "userdata.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val writeScope = CoroutineScope(scope.coroutineContext + Dispatchers.IO.limitedParallelism(1))

    private val _data = MutableStateFlow(load())
    val data: StateFlow<UserData> = _data.asStateFlow()
    val value: UserData get() = _data.value

    /** Carrega na abertura (rápido: poucas tabelas pequenas), migrando o JSON antigo uma vez. */
    private fun load(): UserData = runCatching {
        if (db.meta(MIGRATED_KEY) == null) {
            val legacy = runCatching {
                if (legacyFile.exists()) json.decodeFromString<UserData>(legacyFile.readText()) else null
            }.getOrNull()
            if (legacy != null) db.replaceUserData(legacy)
            db.setMeta(MIGRATED_KEY, System.currentTimeMillis().toString())
            if (legacyFile.exists()) legacyFile.renameTo(File(legacyFile.parentFile, "userdata.json.migrated"))
        }
        db.loadUserData()
    }.getOrDefault(UserData())

    /** Aplica a mudança na memória e grava no banco, em ordem, fora da thread principal. */
    private fun change(transform: (UserData) -> UserData, persist: CriolDatabase.(UserData) -> Unit) {
        val updated = _data.updateAndGet(transform)
        writeScope.launch { runCatching { db.persist(updated) } }
    }

    // ---- Favoritos ----
    fun toggleFavoriteSong(id: String) = change({ it.copy(favoriteSongs = it.favoriteSongs.toggle(id)) }) {
        setFavorite(CriolDatabase.KIND_SONG, id, id in it.favoriteSongs)
    }

    fun toggleFavoriteAlbum(id: String) = change({ it.copy(favoriteAlbums = it.favoriteAlbums.toggle(id)) }) {
        setFavorite(CriolDatabase.KIND_ALBUM, id, id in it.favoriteAlbums)
    }

    fun toggleFavoriteArtist(id: String) = change({ it.copy(favoriteArtists = it.favoriteArtists.toggle(id)) }) {
        setFavorite(CriolDatabase.KIND_ARTIST, id, id in it.favoriteArtists)
    }

    private fun Set<String>.toggle(id: String) = if (id in this) this - id else this + id

    // ---- Avaliação (0 a 5) ----
    fun setRating(id: String, rating: Int) {
        val r = rating.coerceIn(0, 5)
        change({ it.copy(ratings = if (r == 0) it.ratings - id else it.ratings + (id to r)) }) { setRating(id, r) }
    }

    // ---- Reproduções ----
    fun registerPlay(songId: String) = change({
        it.copy(
            playCounts = it.playCounts + (songId to ((it.playCounts[songId] ?: 0) + 1)),
            lastPlayed = it.lastPlayed + (songId to System.currentTimeMillis()),
        )
    }) { setPlays(songId, it.playCounts[songId] ?: 0, it.lastPlayed[songId]) }

    // ---- Playlists ----
    fun createPlaylist(name: String, songIds: List<String> = emptyList()): String {
        val id = UUID.randomUUID().toString()
        change({ it.copy(playlists = it.playlists + Playlist(id, name.trim(), songIds)) }) {
            savePlaylists(it.playlists, setOf(id))
        }
        return id
    }

    fun renamePlaylist(id: String, name: String) = editPlaylist(id, songsChanged = false) { it.copy(name = name.trim()) }

    fun deletePlaylist(id: String) = change({ it.copy(playlists = it.playlists.filterNot { p -> p.id == id }) }) {
        savePlaylists(it.playlists, emptySet())
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

    private fun editPlaylist(id: String, songsChanged: Boolean = true, transform: (Playlist) -> Playlist) = change({
        it.copy(playlists = it.playlists.map { p ->
            if (p.id == id) transform(p).copy(updated = System.currentTimeMillis()) else p
        })
    }) { savePlaylists(it.playlists, if (songsChanged) setOf(id) else emptySet()) }

    private companion object {
        const val MIGRATED_KEY = "userdata_migrated"
    }
}

/**
 * Fila salva entre sessões.
 *
 * A lista de músicas (queue.json) só é regravada quando muda. A posição, que muda o tempo todo,
 * vai para SharedPreferences: gravar alguns números é barato, regravar a fila inteira a cada
 * 15 segundos não é.
 */
class QueueStore(context: Context) {
    private val file = File(context.filesDir, "queue.json")
    private val prefs = context.getSharedPreferences("queue_position", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): QueueSnapshot? = runCatching {
        if (!file.exists()) return@runCatching null
        val snap = json.decodeFromString<QueueSnapshot>(file.readText())
        // A posição salva só vale para a mesma fila (mesmo tamanho).
        if (prefs.getInt(KEY_SIZE, -1) == snap.songIds.size) {
            snap.copy(index = prefs.getInt(KEY_INDEX, snap.index), positionMs = prefs.getLong(KEY_POSITION, snap.positionMs))
        } else snap
    }.getOrNull()

    fun save(snapshot: QueueSnapshot) {
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(snapshot))
            tmp.renameTo(file) || run { file.delete(); tmp.renameTo(file) }
        }
    }

    fun savePosition(index: Int, positionMs: Long, queueSize: Int) {
        prefs.edit().putInt(KEY_INDEX, index).putLong(KEY_POSITION, positionMs).putInt(KEY_SIZE, queueSize).apply()
    }

    private companion object {
        const val KEY_INDEX = "index"
        const val KEY_POSITION = "position"
        const val KEY_SIZE = "size"
    }
}
