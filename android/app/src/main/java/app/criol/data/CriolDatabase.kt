package app.criol.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import app.criol.insights.ListenEntry
import kotlinx.serialization.json.Json

/**
 * Banco SQLite do Criol (SQLite nativo do Android, sem biblioteca extra).
 *
 * Guarda o que muda o tempo todo: favoritos, avaliações, reproduções, playlists e o histórico
 * de escuta. Antes isso ficava em arquivos JSON regravados inteiros a cada mudança; aqui cada
 * mudança grava só a linha que mudou.
 *
 * Os métodos fazem acesso a disco: chame fora da thread principal (exceto [loadUserData] e
 * a migração, que rodam uma vez na abertura).
 */
class CriolDatabase(context: Context) : SQLiteOpenHelper(context, NAME, null, VERSION) {

    private val json = Json { ignoreUnknownKeys = true }

    override fun onConfigure(db: SQLiteDatabase) {
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT)")
        db.execSQL("CREATE TABLE favorites (kind TEXT NOT NULL, id TEXT NOT NULL, PRIMARY KEY (kind, id))")
        db.execSQL("CREATE TABLE ratings (song_id TEXT PRIMARY KEY, rating INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE plays (song_id TEXT PRIMARY KEY, count INTEGER NOT NULL DEFAULT 0, last_played INTEGER)")
        db.execSQL(
            "CREATE TABLE playlists (id TEXT PRIMARY KEY, name TEXT NOT NULL, created INTEGER NOT NULL, " +
                "updated INTEGER NOT NULL, position INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE playlist_songs (playlist_id TEXT NOT NULL, position INTEGER NOT NULL, " +
                "song_id TEXT NOT NULL, PRIMARY KEY (playlist_id, position))",
        )
        db.execSQL(
            "CREATE TABLE listens (id TEXT PRIMARY KEY, song_id TEXT NOT NULL, title TEXT NOT NULL, " +
                "artist TEXT NOT NULL, artists TEXT NOT NULL, album TEXT NOT NULL, album_id TEXT NOT NULL, " +
                "genres TEXT NOT NULL, duration_ms INTEGER NOT NULL, listened_ms INTEGER NOT NULL, " +
                "played_at INTEGER NOT NULL, counted INTEGER NOT NULL)",
        )
        db.execSQL("CREATE INDEX listens_played_at ON listens (played_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    // ------------------------------------------------------------------
    // Meta
    // ------------------------------------------------------------------

    fun meta(key: String): String? =
        readableDatabase.rawQuery("SELECT value FROM meta WHERE key = ?", arrayOf(key)).use {
            if (it.moveToFirst()) it.getString(0) else null
        }

    fun setMeta(key: String, value: String) {
        writableDatabase.insertWithOnConflict(
            "meta", null, ContentValues().apply { put("key", key); put("value", value) },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    // ------------------------------------------------------------------
    // Dados do usuário
    // ------------------------------------------------------------------

    fun loadUserData(): UserData {
        val db = readableDatabase
        val favSongs = HashSet<String>()
        val favAlbums = HashSet<String>()
        val favArtists = HashSet<String>()
        db.rawQuery("SELECT kind, id FROM favorites", null).use { c ->
            while (c.moveToNext()) {
                when (c.getString(0)) {
                    KIND_SONG -> favSongs += c.getString(1)
                    KIND_ALBUM -> favAlbums += c.getString(1)
                    KIND_ARTIST -> favArtists += c.getString(1)
                }
            }
        }
        val ratings = HashMap<String, Int>()
        db.rawQuery("SELECT song_id, rating FROM ratings", null).use { c ->
            while (c.moveToNext()) ratings[c.getString(0)] = c.getInt(1)
        }
        val counts = HashMap<String, Int>()
        val last = HashMap<String, Long>()
        db.rawQuery("SELECT song_id, count, last_played FROM plays", null).use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0)
                if (c.getInt(1) > 0) counts[id] = c.getInt(1)
                if (!c.isNull(2)) last[id] = c.getLong(2)
            }
        }
        val songsByPlaylist = HashMap<String, MutableList<String>>()
        db.rawQuery("SELECT playlist_id, song_id FROM playlist_songs ORDER BY playlist_id, position", null).use { c ->
            while (c.moveToNext()) songsByPlaylist.getOrPut(c.getString(0)) { mutableListOf() } += c.getString(1)
        }
        val playlists = ArrayList<Playlist>()
        db.rawQuery("SELECT id, name, created, updated FROM playlists ORDER BY position", null).use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0)
                playlists += Playlist(id, c.getString(1), songsByPlaylist[id].orEmpty(), c.getLong(2), c.getLong(3))
            }
        }
        return UserData(favSongs, favAlbums, favArtists, ratings, counts, last, playlists)
    }

    fun setFavorite(kind: String, id: String, favorite: Boolean) {
        val db = writableDatabase
        if (favorite) {
            db.insertWithOnConflict(
                "favorites", null, ContentValues().apply { put("kind", kind); put("id", id) },
                SQLiteDatabase.CONFLICT_IGNORE,
            )
        } else {
            db.delete("favorites", "kind = ? AND id = ?", arrayOf(kind, id))
        }
    }

    fun setRating(songId: String, rating: Int) {
        val db = writableDatabase
        if (rating <= 0) db.delete("ratings", "song_id = ?", arrayOf(songId))
        else db.insertWithOnConflict(
            "ratings", null, ContentValues().apply { put("song_id", songId); put("rating", rating) },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun setPlays(songId: String, count: Int, lastPlayed: Long?) {
        writableDatabase.insertWithOnConflict(
            "plays", null,
            ContentValues().apply {
                put("song_id", songId)
                put("count", count)
                if (lastPlayed != null) put("last_played", lastPlayed) else putNull("last_played")
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    /** Grava a lista de playlists (nomes e ordem) e as músicas só das que mudaram. */
    fun savePlaylists(playlists: List<Playlist>, changedIds: Set<String>) = transaction { db ->
        val ids = playlists.map { it.id }
        val existing = db.rawQuery("SELECT id FROM playlists", null).use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }
        for (gone in existing - ids.toSet()) {
            db.delete("playlists", "id = ?", arrayOf(gone))
            db.delete("playlist_songs", "playlist_id = ?", arrayOf(gone))
        }
        playlists.forEachIndexed { i, p ->
            db.insertWithOnConflict(
                "playlists", null,
                ContentValues().apply {
                    put("id", p.id); put("name", p.name); put("created", p.created)
                    put("updated", p.updated); put("position", i)
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
            if (p.id in changedIds || p.id !in existing) writePlaylistSongs(db, p)
        }
    }

    private fun writePlaylistSongs(db: SQLiteDatabase, p: Playlist) {
        db.delete("playlist_songs", "playlist_id = ?", arrayOf(p.id))
        val stmt = db.compileStatement("INSERT INTO playlist_songs (playlist_id, position, song_id) VALUES (?, ?, ?)")
        p.songIds.forEachIndexed { i, songId ->
            stmt.bindString(1, p.id); stmt.bindLong(2, i.toLong()); stmt.bindString(3, songId)
            stmt.executeInsert()
            stmt.clearBindings()
        }
    }

    /** Importa tudo de uma vez (migração do userdata.json). */
    fun replaceUserData(data: UserData) = transaction { db ->
        listOf("favorites", "ratings", "plays", "playlists", "playlist_songs").forEach { db.delete(it, null, null) }
        data.favoriteSongs.forEach { setFavorite(KIND_SONG, it, true) }
        data.favoriteAlbums.forEach { setFavorite(KIND_ALBUM, it, true) }
        data.favoriteArtists.forEach { setFavorite(KIND_ARTIST, it, true) }
        data.ratings.forEach { (id, r) -> setRating(id, r) }
        (data.playCounts.keys + data.lastPlayed.keys).forEach { id ->
            setPlays(id, data.playCounts[id] ?: 0, data.lastPlayed[id])
        }
        savePlaylists(data.playlists, data.playlists.mapTo(HashSet()) { it.id })
    }

    // ------------------------------------------------------------------
    // Histórico de escuta
    // ------------------------------------------------------------------

    fun loadListens(): List<ListenEntry> =
        readableDatabase.rawQuery(
            "SELECT id, song_id, title, artist, artists, album, album_id, genres, duration_ms, " +
                "listened_ms, played_at, counted FROM listens ORDER BY played_at",
            null,
        ).use { c ->
            ArrayList<ListenEntry>(c.count).apply {
                while (c.moveToNext()) {
                    add(
                        ListenEntry(
                            id = c.getString(0),
                            songId = c.getString(1),
                            title = c.getString(2),
                            artist = c.getString(3),
                            artists = decodeList(c.getString(4)),
                            album = c.getString(5),
                            albumId = c.getString(6),
                            genres = decodeList(c.getString(7)),
                            durationMs = c.getLong(8),
                            listenedMs = c.getLong(9),
                            playedAt = c.getLong(10),
                            counted = c.getInt(11) != 0,
                        ),
                    )
                }
            }
        }

    fun insertListens(entries: Collection<ListenEntry>) = transaction { db ->
        for (e in entries) {
            db.insertWithOnConflict(
                "listens", null,
                ContentValues().apply {
                    put("id", e.id); put("song_id", e.songId); put("title", e.title); put("artist", e.artist)
                    put("artists", json.encodeToString(e.artists)); put("album", e.album); put("album_id", e.albumId)
                    put("genres", json.encodeToString(e.genres)); put("duration_ms", e.durationMs)
                    put("listened_ms", e.listenedMs); put("played_at", e.playedAt); put("counted", if (e.counted) 1 else 0)
                },
                SQLiteDatabase.CONFLICT_IGNORE,
            )
        }
    }

    fun clearListens() {
        writableDatabase.delete("listens", null, null)
    }

    private fun decodeList(raw: String?): List<String> =
        if (raw.isNullOrEmpty()) emptyList() else runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())

    private inline fun transaction(block: (SQLiteDatabase) -> Unit) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            block(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    companion object {
        private const val NAME = "criol.db"
        private const val VERSION = 1
        const val KIND_SONG = "song"
        const val KIND_ALBUM = "album"
        const val KIND_ARTIST = "artist"

        @Volatile
        private var instance: CriolDatabase? = null

        fun get(context: Context): CriolDatabase =
            instance ?: synchronized(this) {
                instance ?: CriolDatabase(context.applicationContext).also { instance = it }
            }
    }
}
