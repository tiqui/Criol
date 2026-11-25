package app.criol.playback

import app.criol.data.Library
import app.criol.data.Song
import app.criol.data.UserData
import app.criol.data.normalizeForSearch
import app.criol.insights.ListenEntry
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * "Rádio da música", no estilo do Spotify: a partir de uma música, monta uma fila de faixas
 * parecidas que nunca acaba (o [PlayerController] pede mais quando está terminando).
 *
 * Sem servidor para pedir recomendações, a semelhança é calculada só com o que existe no
 * aparelho, e vai além do Auto DJ:
 * - gêneros, artista e época da música-semente;
 * - **artistas relacionados**: os que dividem faixas com o artista (participações) e os que você
 *   costuma ouvir na mesma sessão que ele (histórico de escuta);
 * - **ouvidas junto**: músicas que você tocou perto (±45 min) de quando tocou a semente;
 * - favoritos e avaliações; o que tocou nas últimas horas perde peso.
 *
 * Variedade: no máximo 2 por álbum, poucas do mesmo artista, sem versões repetidas da mesma
 * música (ao vivo/remaster) e nunca o mesmo artista duas vezes seguidas. Quando o rádio
 * continua, os gêneros das últimas faixas entram com peso menor, então ele "passeia" devagar,
 * como no Spotify, sem perder a semente de vista.
 */
class SongRadio(
    private val library: Library,
    private val userData: UserData,
    history: List<ListenEntry>,
    private val random: Random = Random.Default,
    private val now: Long = System.currentTimeMillis(),
) {
    private val history = history.sortedBy { it.playedAt }

    /**
     * @param exclude músicas que não podem entrar (a fila atual, por exemplo).
     * @param recent últimas faixas tocadas no rádio, para ele ir mudando aos poucos.
     */
    fun build(seed: Song, size: Int, exclude: Set<String> = emptySet(), recent: List<Song> = emptyList()): List<Song> {
        if (size <= 0) return emptyList()
        val ctx = Profile(seed, recent)
        val excluded = exclude + seed.id
        val candidates = library.songs.filter { it.id !in excluded }
        if (candidates.isEmpty()) return emptyList()

        val scored = candidates.mapNotNull { s -> score(ctx, s).takeIf { it > 0 }?.let { s to it } }
        // Peso elevado: as mais parecidas aparecem bem mais, mas ainda há surpresa.
        val ordered = weightedOrder(scored.map { it.first to it.second.pow(1.6) })

        val picked = ArrayList<Song>(size)
        val taken = HashSet<String>()
        val perAlbum = HashMap<String, Int>()
        val perArtist = HashMap<String, Int>()
        val titles = HashSet<String>().apply { add(titleKey(seed)) }
        recent.forEach { titles += titleKey(it) }
        val seedArtistCap = max(3, size / 5)

        fun tryAdd(s: Song, caps: Boolean): Boolean {
            if (s.id in taken) return false
            val tk = titleKey(s)
            if (tk in titles) return false
            val artist = mainArtist(s)
            if (caps) {
                if ((perAlbum[s.albumId] ?: 0) >= MAX_PER_ALBUM) return false
                val cap = if (artist in ctx.seedArtists) seedArtistCap else MAX_PER_ARTIST
                if ((perArtist[artist] ?: 0) >= cap) return false
            }
            picked += s
            taken += s.id
            titles += tk
            perAlbum[s.albumId] = (perAlbum[s.albumId] ?: 0) + 1
            perArtist[artist] = (perArtist[artist] ?: 0) + 1
            return true
        }

        for (s in ordered) {
            if (picked.size >= size) break
            tryAdd(s, caps = true)
        }
        // Biblioteca pequena: afrouxa os limites, depois completa com o que estiver mais perto.
        if (picked.size < size) for (s in ordered) {
            if (picked.size >= size) break
            tryAdd(s, caps = false)
        }
        if (picked.size < size) {
            val rest = candidates.filter { it.id !in taken }
                .sortedBy { s -> yearGap(seed, s) + random.nextDouble() * 6 }
            for (s in rest) {
                if (picked.size >= size) break
                tryAdd(s, caps = false)
            }
        }
        return spaceArtists(picked, recent.lastOrNull() ?: seed)
    }

    // ------------------------------------------------------------------

    private inner class Profile(val seed: Song, recent: List<Song>) {
        val seedArtists: Set<String> = (seed.artists + seed.albumArtists).map { it.normalizeForSearch() }.toSet()

        /** gênero normalizado -> peso (semente 1.0, faixas recentes 0.35 cada, no máximo 1.0). */
        val genres: Map<String, Double> = buildMap<String, Double> {
            genresOf(seed).forEach { put(it, 1.0) }
            recent.takeLast(5).forEach { r ->
                genresOf(r).forEach { g -> put(g, min(1.0, (get(g) ?: 0.0) + 0.35)) }
            }
        }
        val genreTotal = genres.values.sum().coerceAtLeast(1e-9)
        val year = seed.year

        /** artista normalizado -> 0..1. */
        val related: Map<String, Double>

        /** id da música -> 0..1. */
        val heardTogether: Map<String, Double>

        init {
            val rel = HashMap<String, Double>()
            // Participações: quem aparece em faixas junto com o artista da semente.
            for (s in library.songs) {
                val names = (s.artists + s.albumArtists).map { it.normalizeForSearch() }.toSet()
                if (names.none { it in seedArtists }) continue
                for (n in names) if (n !in seedArtists) rel[n] = (rel[n] ?: 0.0) + 1.0
            }
            // Histórico: o que tocou perto (no tempo) da semente ou do artista dela.
            val together = HashMap<String, Double>()
            val anchors = history.filter { e ->
                e.songId == seed.id || e.artists.any { it.normalizeForSearch() in seedArtists }
            }
            for (a in anchors) {
                val strong = a.songId == seed.id
                val from = a.playedAt - SESSION_WINDOW_MS
                val to = a.playedAt + SESSION_WINDOW_MS
                for (e in window(from, to)) {
                    if (e.songId == a.songId) continue
                    val w = if (strong) 1.0 else 0.4
                    together[e.songId] = (together[e.songId] ?: 0.0) + w
                    for (n in e.artists) {
                        val k = n.normalizeForSearch()
                        if (k !in seedArtists) rel[k] = (rel[k] ?: 0.0) + 0.5 * w
                    }
                }
            }
            related = normalize(rel)
            heardTogether = normalize(together)
        }
    }

    private fun score(ctx: Profile, s: Song): Double {
        val genres = genresOf(s)
        val genre = genres.sumOf { ctx.genres[it] ?: 0.0 } / ctx.genreTotal
        val names = (s.artists + s.albumArtists).map { it.normalizeForSearch() }
        val sameArtist = names.any { it in ctx.seedArtists }
        val related = names.maxOfOrNull { ctx.related[it] ?: 0.0 } ?: 0.0
        val together = ctx.heardTogether[s.id] ?: 0.0

        // Sem nenhuma ligação com a semente não entra (o preenchimento final cuida da falta).
        if (genre <= 0.0 && !sameArtist && related <= 0.0 && together <= 0.0) return 0.0

        var score = 4.0 * min(1.0, genre)
        if (sameArtist) score += 3.0
        score += 2.5 * related
        score += 4.5 * together
        val gap = yearGap(ctx.seed, s)
        score += when {
            gap <= 3 -> 1.2
            gap <= 8 -> 0.6
            else -> 0.0
        }
        if (s.id in userData.favoriteSongs) score += 0.6
        score += (userData.ratings[s.id] ?: 0) * 0.25
        // O que tocou há pouco perde espaço, como no Spotify.
        val last = userData.lastPlayed[s.id]
        if (last != null && now - last < RECENT_MS) score *= 0.35
        return score
    }

    /** Escutas entre [from] e [to] (histórico ordenado por horário). */
    private fun window(from: Long, to: Long): List<ListenEntry> {
        var lo = 0
        var hi = history.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (history[mid].playedAt < from) lo = mid + 1 else hi = mid
        }
        val out = ArrayList<ListenEntry>()
        var i = lo
        while (i < history.size && history[i].playedAt <= to) out += history[i++]
        return out
    }

    /** Reordena para o mesmo artista não tocar duas vezes seguidas (quando der). */
    private fun spaceArtists(songs: List<Song>, previous: Song): List<Song> {
        val pool = songs.toMutableList()
        val out = ArrayList<Song>(songs.size)
        var last = mainArtist(previous)
        while (pool.isNotEmpty()) {
            val i = pool.indexOfFirst { mainArtist(it) != last }.takeIf { it >= 0 } ?: 0
            val s = pool.removeAt(i)
            out += s
            last = mainArtist(s)
        }
        return out
    }

    /** Sorteio ponderado sem reposição (Efraimidis–Spirakis), devolvendo a ordem toda. */
    private fun <T> weightedOrder(items: List<Pair<T, Double>>): List<T> =
        items.map { (item, w) -> item to (ln(random.nextDouble().coerceAtLeast(1e-12)) / w) }
            .sortedByDescending { it.second }
            .map { it.first }

    private fun normalize(map: Map<String, Double>): Map<String, Double> {
        val top = map.values.maxOrNull() ?: return emptyMap()
        if (top <= 0) return emptyMap()
        return map.mapValues { it.value / top }
    }

    private fun genresOf(s: Song) = Library.splitGenres(s.genre).map { it.normalizeForSearch() }.toSet()

    private fun mainArtist(s: Song) = (s.artists.firstOrNull() ?: s.artist).normalizeForSearch()

    private fun yearGap(a: Song, b: Song) = if (a.year > 0 && b.year > 0) abs(a.year - b.year) else 99

    companion object {
        /** Tamanho da fila ao começar o rádio (fora a semente). */
        const val START_SIZE = 50

        /** Quantas entram cada vez que o rádio está acabando. */
        const val REFILL_SIZE = 25

        /** O rádio pede mais quando faltam menos que isso. */
        const val REFILL_AT = 5

        private const val MAX_PER_ALBUM = 2
        private const val MAX_PER_ARTIST = 3
        private const val SESSION_WINDOW_MS = 45 * 60 * 1000L
        private const val RECENT_MS = 3 * 60 * 60 * 1000L

        private val versionNoise = Regex("""\s*[(\[][^)\]]*[)\]]\s*|\s+-\s+.*$""")

        /** "Música (Ao Vivo)" e "Música - Remaster 2011" contam como a mesma música. */
        fun titleKey(s: Song): String {
            val base = s.title.replace(versionNoise, " ").normalizeForSearch().trim().ifEmpty { s.title.normalizeForSearch() }
            val artist = (s.artists.firstOrNull() ?: s.artist).normalizeForSearch()
            return "$artist|$base"
        }
    }
}
