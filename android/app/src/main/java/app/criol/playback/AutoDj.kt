package app.criol.playback

import app.criol.data.Album
import app.criol.data.Library
import app.criol.data.Song
import app.criol.data.UserData
import app.criol.data.normalizeForSearch
import kotlin.math.abs
import kotlin.math.ln
import kotlin.random.Random

/**
 * Auto DJ: quando a fila está acabando, escolhe mais músicas ou álbuns.
 *
 * Estratégia "parecidas": sem servidor para pedir músicas similares, calcula uma pontuação
 * local (gênero, artista, época, favoritos/avaliação) e sorteia com peso. Mistura ~20% de
 * músicas aleatórias para variar.
 * Estratégia "aleatória": qualquer música/álbum da biblioteca.
 */
class AutoDj(
    private val library: Library,
    private val userData: UserData,
    private val random: Random = Random.Default,
    private val now: Long = System.currentTimeMillis(),
) {
    data class Options(
        val mode: String = MODE_SONGS,
        val strategy: String = STRATEGY_SIMILAR,
        val count: Int = 5,
        val allowDuplicates: Boolean = false,
        val onlySimilar: Boolean = false,
    )

    fun pickSongs(current: Song, queueIds: Set<String>, opt: Options): List<Song> {
        val count = opt.count.coerceIn(1, MAX_SONGS)
        val available = library.songs.filter { it.id != current.id && (opt.allowDuplicates || it.id !in queueIds) }
        if (available.isEmpty()) return emptyList()
        if (opt.strategy == STRATEGY_RANDOM) return available.shuffled(random).take(count)

        val selected = LinkedHashMap<String, Song>()
        val ctx = Context(current)
        val scored = available.mapNotNull { s -> songScore(ctx, s).takeIf { it >= MIN_SIMILAR }?.let { s to it } }

        // Variedade: no máximo 2 músicas do mesmo álbum por rodada.
        val perAlbum = HashMap<String, Int>()
        val spice = if (opt.onlySimilar) 0 else (count * 0.2).toInt()
        for (s in weightedPick(scored, scored.size)) {
            if (selected.size >= count - spice) break
            if ((perAlbum[s.albumId] ?: 0) >= 2) continue
            perAlbum[s.albumId] = (perAlbum[s.albumId] ?: 0) + 1
            selected[s.id] = s
        }
        if (opt.onlySimilar && selected.isNotEmpty()) return selected.values.toList()

        // Tempero aleatório + completa com aleatórias se faltou.
        for (s in available.shuffled(random)) {
            if (selected.size >= count) break
            if (s.id !in selected) selected[s.id] = s
        }
        return selected.values.toList()
    }

    fun pickAlbums(current: Song, queueAlbumIds: Set<String>, opt: Options): List<Album> {
        val count = opt.count.coerceIn(1, MAX_ALBUMS)
        val available = library.albums.filter {
            it.id != current.albumId && (opt.allowDuplicates || it.id !in queueAlbumIds)
        }
        if (available.isEmpty()) return emptyList()
        if (opt.strategy == STRATEGY_RANDOM) return available.shuffled(random).take(count)

        val ctx = Context(current)
        val scored = available.mapNotNull { a -> albumScore(ctx, a).takeIf { it >= MIN_SIMILAR }?.let { a to it } }
        val selected = LinkedHashMap<String, Album>()
        val spice = if (opt.onlySimilar || count < 3) 0 else (count * 0.2).toInt()
        for (a in weightedPick(scored, count - spice)) selected[a.id] = a
        if (opt.onlySimilar && selected.isNotEmpty()) return selected.values.toList()
        for (a in available.shuffled(random)) {
            if (selected.size >= count) break
            if (a.id !in selected) selected[a.id] = a
        }
        return selected.values.toList()
    }

    // ------------------------------------------------------------------

    private inner class Context(val song: Song) {
        val genres = Library.splitGenres(song.genre).map { it.normalizeForSearch() }.toSet()
        val artists = song.artists.map { it.normalizeForSearch() }.toSet()
        val albumArtists = song.albumArtists.map { it.normalizeForSearch() }.toSet()
        val year = song.year
    }

    private fun commonScore(ctx: Context, genres: Collection<String>, artists: Collection<String>, year: Int): Double {
        var score = 0.0
        val g = genres.count { it.normalizeForSearch() in ctx.genres }
        if (g > 0) score += 3.0 + (g - 1)
        val a = artists.map { it.normalizeForSearch() }
        if (a.any { it in ctx.artists || it in ctx.albumArtists }) score += 4.0
        if (ctx.year > 0 && year > 0) {
            val d = abs(ctx.year - year)
            score += when {
                d <= 3 -> 1.5
                d <= 8 -> 0.75
                else -> 0.0
            }
        }
        return score
    }

    private fun songScore(ctx: Context, s: Song): Double {
        var score = commonScore(ctx, Library.splitGenres(s.genre), s.artists + s.albumArtists, s.year)
        if (score <= 0) return 0.0
        if (s.id in userData.favoriteSongs) score += 1.0
        score += (userData.ratings[s.id] ?: 0) * 0.3
        // Evita repetir o que tocou nas últimas 2 horas.
        val last = userData.lastPlayed[s.id]
        if (last != null && now - last < 2 * 60 * 60 * 1000) score *= 0.25
        return score
    }

    private fun albumScore(ctx: Context, a: Album): Double {
        var score = commonScore(ctx, a.genres, a.artistNames, a.year)
        if (score <= 0) return 0.0
        if (a.id in userData.favoriteAlbums) score += 1.0
        return score
    }

    /** Sorteio ponderado sem reposição (Efraimidis–Spirakis). */
    private fun <T> weightedPick(items: List<Pair<T, Double>>, n: Int): List<T> {
        if (n <= 0 || items.isEmpty()) return emptyList()
        return items
            .map { (item, w) -> item to (ln(random.nextDouble().coerceAtLeast(1e-12)) / w) }
            .sortedByDescending { it.second }
            .take(n)
            .map { it.first }
    }

    companion object {
        const val MODE_SONGS = "songs"
        const val MODE_ALBUMS = "albums"
        const val STRATEGY_SIMILAR = "similar"
        const val STRATEGY_RANDOM = "random"

        /** Máximo de músicas por vez no modo músicas. */
        const val MAX_SONGS = 150

        /** Máximo de álbuns por vez (álbuns inteiros viram muitas músicas). */
        const val MAX_ALBUMS = 20
        private const val MIN_SIMILAR = 3.0
    }
}
