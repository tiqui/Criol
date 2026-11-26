package app.criol.data

import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.text.Normalizer

/** Uma faixa de áudio encontrada em uma das pastas da biblioteca. */
@Serializable
data class Song(
    val id: String,
    val uri: String,
    val rootUri: String,
    val title: String,
    val artist: String,
    val albumArtist: String,
    val album: String,
    val albumId: String,
    val genre: String,
    val year: Int,
    val track: Int,
    val disc: Int,
    val durationMs: Long,
    val folder: String,
    val fileName: String,
    val size: Long,
    val lastModified: Long,
    val dateAdded: Long,
    val mime: String,
    val bitrate: Int,
    val sampleRate: Int = 0,
    val composer: String = "",
    val lyricsUri: String? = null,
) {
    /** Artistas da faixa (separados por ; ou /). */
    val artists: List<String> get() = splitArtists(artist)

    val albumArtists: List<String>
        get() = splitArtists(albumArtist).ifEmpty { artists }
}

/** Cache persistido da biblioteca escaneada. */
@Serializable
data class LibraryCache(
    val version: Int = CACHE_VERSION,
    val songs: List<Song> = emptyList(),
    /** albumId -> cor dominante (ARGB) da capa. Presença da chave = capa existe em disco. */
    val artColors: Map<String, Int> = emptyMap(),
    /** Álbuns cuja capa já foi procurada sem sucesso. */
    val noArt: Set<String> = emptySet(),
    val scannedAt: Long = 0,
    /** Contadores de mudança do Android no início da última leitura (ver [MediaStoreIndex.generations]). */
    val generations: Map<String, Long> = emptyMap(),
    /** Pastas e filtros usados na última leitura; se mudarem, é preciso ler de novo. */
    val scanKey: String = "",
) {
    companion object {
        const val CACHE_VERSION = 2
    }
}

data class Album(
    val id: String,
    val name: String,
    val artist: String,
    val artistNames: List<String>,
    val year: Int,
    val songs: List<Song>,
    val durationMs: Long,
    val dateAdded: Long,
    val genres: List<String>,
    val artPath: String?,
    val color: Int?,
    val folder: String,
)

data class Artist(
    val id: String,
    val name: String,
    /** Álbuns em que é artista do álbum. */
    val albums: List<Album>,
    /** Álbuns em que só aparece em alguma faixa. */
    val appearsOn: List<Album>,
    val songs: List<Song>,
    val artPath: String?,
    val color: Int?,
)

data class Genre(
    val id: String,
    val name: String,
    val songs: List<Song>,
    val albums: List<Album>,
    val artPath: String?,
    val color: Int?,
)

data class FolderNode(
    val path: String,
    val name: String,
    val children: List<FolderNode>,
    val songs: List<Song>,
    val artPath: String?,
    val totalSongs: Int,
)

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val songIds: List<String> = emptyList(),
    val created: Long = System.currentTimeMillis(),
    val updated: Long = System.currentTimeMillis(),
)

@Serializable
data class UserData(
    val favoriteSongs: Set<String> = emptySet(),
    val favoriteAlbums: Set<String> = emptySet(),
    val favoriteArtists: Set<String> = emptySet(),
    val ratings: Map<String, Int> = emptyMap(),
    val playCounts: Map<String, Int> = emptyMap(),
    val lastPlayed: Map<String, Long> = emptyMap(),
    val playlists: List<Playlist> = emptyList(),
)

@Serializable
data class QueueSnapshot(
    val songIds: List<String> = emptyList(),
    val index: Int = 0,
    val positionMs: Long = 0,
    /** songIds fica na ordem física; no aleatório, [shuffleOrder] diz a ordem de tocar. */
    val shuffled: Boolean = false,
    /** Formato antigo (aleatório reordenava a fila): só serve para reconhecer filas antigas. */
    val originalOrder: List<String> = emptyList(),
    /** Índices de songIds na ordem em que tocam (só no aleatório). */
    val shuffleOrder: List<Int> = emptyList(),
    val repeatMode: Int = 0,
    val speed: Float = 1f,
    /** Semente do "Rádio da música", se a fila salva era um rádio. */
    val radioSeedId: String? = null,
)

fun hashId(value: String): String {
    val md = MessageDigest.getInstance("MD5").digest(value.toByteArray())
    return md.take(8).joinToString("") { "%02x".format(it) }
}

private val accentRegex = Regex("\\p{Mn}+")

private val normalizedCache = java.util.concurrent.ConcurrentHashMap<String, String>()

/**
 * Normaliza para busca: minúsculas e sem acentos.
 *
 * É chamada milhares de vezes (montar a biblioteca, ordenar listas, busca, mixes, rádio).
 * Texto só ASCII não tem acento, então basta o lowercase; o resto (Normalizer + regex, caro)
 * fica guardado, já que nomes de artista/álbum/gênero se repetem muito.
 */
fun String.normalizeForSearch(): String {
    var ascii = true
    for (ch in this) if (ch.code >= 0x80) { ascii = false; break }
    if (ascii) return lowercase()
    normalizedCache[this]?.let { return it }
    val out = Normalizer.normalize(lowercase(), Normalizer.Form.NFD).replace(accentRegex, "")
    if (normalizedCache.size > 50_000) normalizedCache.clear()
    normalizedCache[this] = out
    return out
}

private val artistSeparators = Regex(";|\u0000|\\s+/\\s+")

fun splitArtists(value: String): List<String> {
    if (value.isBlank()) return emptyList()
    // "/" só separa quando tem espaços ao redor, para não quebrar nomes como "AC/DC".
    return value.split(artistSeparators)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
}

fun formatDuration(ms: Long): String {
    val totalSec = (ms.coerceAtLeast(0) / 1000)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatLongDuration(ms: Long): String {
    val totalMin = ms / 60000
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h > 0 && m > 0 -> "$h h $m min"
        h > 0 -> "$h h"
        else -> "$m min"
    }
}

fun songCountLabel(n: Int) = if (n == 1) "1 música" else "$n músicas"
fun albumCountLabel(n: Int) = if (n == 1) "1 álbum" else "$n álbuns"
