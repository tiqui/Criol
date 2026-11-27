package app.criol.insights

import app.criol.data.Library
import app.criol.data.Song
import app.criol.data.normalizeForSearch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow

/**
 * "Humor" de cada horário: o que você costuma ouvir naquela hora do dia.
 *
 * Cada escuta conta numa janela de ~4 horas em volta da hora em que aconteceu (a hora exata
 * pesa mais), então quem ouve jazz das 5h às 8h e trap a partir das 9h tem um perfil de
 * jazz às 6h e de trap às 10h. Escutas recentes valem mais, e músicas puladas naquele horário
 * contam contra o gênero e o artista.
 *
 * Com pouco histórico no horário, entra uma "energia esperada" por hora (calmo cedo, mais
 * pesado à tarde e à noite) junto do seu gosto geral, até o histórico do horário ganhar peso.
 *
 * A versão desktop/web usa a mesma conta (desktop/src/renderer/insights/mood.ts).
 */
object Mood {
    /** Peso de uma escuta pela distância (horas no relógio) até o horário alvo. */
    private val HOUR_WEIGHTS = doubleArrayOf(1.0, 0.6, 0.25)
    private const val HALF_LIFE_DAYS = 45.0
    /** Uma música pulada conta como 0,6 minuto contra. */
    private const val SKIP_WEIGHT = -0.6
    /** Minutos (já com os pesos) para confiar metade no histórico do horário. */
    const val CONFIDENCE_MINUTES = 45.0
    /** Na daylist, escutas no mesmo dia da semana valem um pouco mais. */
    const val SAME_WEEKDAY_BONUS = 1.3

    /** Energia esperada (0 calmo a 1 pesado) em cada hora, para quando falta histórico. */
    private val EXPECTED_ENERGY = doubleArrayOf(
        0.35, 0.3, 0.25, 0.2, 0.2, 0.25, 0.3, 0.35, 0.45, 0.5, 0.55, 0.6,
        0.6, 0.6, 0.6, 0.65, 0.65, 0.7, 0.7, 0.7, 0.65, 0.6, 0.5, 0.4,
    )

    /** Energia típica por palavra do gênero (comparada sem acentos, em minúsculas). */
    private val GENRE_ENERGY: List<Pair<String, Double>> = listOf(
        "sleep" to 0.05, "meditat" to 0.05, "ambient" to 0.1, "new age" to 0.1, "classic" to 0.15,
        "classica" to 0.15, "erudit" to 0.15, "piano" to 0.15, "lo-fi" to 0.2, "lofi" to 0.2,
        "lounge" to 0.2, "chill" to 0.2, "acoustic" to 0.25, "acustic" to 0.25, "instrumental" to 0.25,
        "bossa" to 0.25, "jazz" to 0.3, "folk" to 0.3, "soundtrack" to 0.3, "trilha" to 0.3,
        "mpb" to 0.35, "blues" to 0.35, "soul" to 0.4, "gospel" to 0.4, "worship" to 0.4,
        "indie" to 0.45, "r&b" to 0.45, "rnb" to 0.45, "reggae" to 0.45, "country" to 0.45,
        "pop" to 0.55, "samba" to 0.55, "pagode" to 0.55, "sertanejo" to 0.55, "forro" to 0.6,
        "alternative" to 0.6, "alternativ" to 0.6, "rock" to 0.65, "hip hop" to 0.65, "hip-hop" to 0.65,
        "rap" to 0.65, "reggaeton" to 0.7, "latin" to 0.65, "axe" to 0.7, "disco" to 0.7, "emo" to 0.7,
        "electronic" to 0.7, "eletronic" to 0.7, "house" to 0.75, "dance" to 0.75, "funk" to 0.75,
        "trap" to 0.75, "grunge" to 0.75, "drill" to 0.8, "trance" to 0.8, "edm" to 0.85, "techno" to 0.85,
        "punk" to 0.85, "drum and bass" to 0.9, "dnb" to 0.9, "dubstep" to 0.9, "metal" to 0.95,
        "heavy" to 0.95, "hardcore" to 0.95, "thrash" to 1.0, "death" to 1.0, "grindcore" to 1.0,
    )

    fun hourWeight(hour: Int, target: Int): Double {
        val d = abs(hour - target).let { min(it, 24 - it) }
        return HOUR_WEIGHTS.getOrElse(d) { 0.0 }
    }

    /** Horas do bloco de 4 horas da daylist (0–3, 4–7, 8–11, 12–15, 16–19, 20–23). */
    fun slotHours(hour: Int): List<Int> = (hour / 4 * 4).let { (it until it + 4).toList() }

    fun daypartHours(daypart: Daypart): List<Int> = when (daypart) {
        Daypart.DAWN -> (0..4).toList()
        Daypart.MORNING -> (5..11).toList()
        Daypart.AFTERNOON -> (12..17).toList()
        Daypart.NIGHT -> (18..23).toList()
    }

    fun expectedEnergy(hours: List<Int>): Double = hours.map { EXPECTED_ENERGY[it.mod(24)] }.average()

    /** Energia de uma música pelos gêneros (média das palavras reconhecidas), ou null. */
    fun genreEnergy(genres: List<String>): Double? {
        val found = genres.flatMap { g ->
            val key = g.normalizeForSearch()
            val tokens = key.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
            // Palavra simples: começo de uma palavra do gênero ("classic" acha "classical", mas
            // "rap" não acha "trap"). Com espaço ou símbolo ("hip hop", "r&b"): o texto inteiro.
            GENRE_ENERGY.filter { (word, _) ->
                if (word.all { it.isLetterOrDigit() }) tokens.any { it.startsWith(word) } else key.contains(word)
            }.map { it.second }
        }
        return if (found.isEmpty()) null else found.average()
    }

    /** Quanto a energia da música combina com a esperada para o horário (0 a 1; 0,6 se não sabe). */
    fun energyFit(genres: List<String>, hours: List<Int>): Double {
        val energy = genreEnergy(genres) ?: return 0.6
        return 1.0 - min(1.0, abs(energy - expectedEnergy(hours)) * 1.6)
    }

    /**
     * Perfil das [entries] em volta das [hours] (null = o dia inteiro, o gosto geral).
     * [weekday] (1 = segunda ... 7 = domingo) dá o bônus do mesmo dia da semana.
     */
    fun profile(entries: List<ListenEntry>, hours: List<Int>?, nowMs: Long, weekday: Int? = null): MoodProfile {
        val genres = HashMap<String, Pair<String, Double>>()
        val artists = HashMap<String, Double>()
        var mass = 0.0
        for (e in entries) {
            val t = zoned(e.playedAt)
            val hourW = if (hours == null) 1.0 else hours.sumOf { hourWeight(t.hour, it) } / hours.size
            if (hourW <= 0.0) continue
            val recency = 0.5.pow((nowMs - e.playedAt).coerceAtLeast(0).toDouble() / DAY_MS / HALF_LIFE_DAYS)
            val dayW = if (weekday != null && t.dayOfWeek.value == weekday) SAME_WEEKDAY_BONUS else 1.0
            val base = hourW * recency * dayW
            val w = if (e.counted) base * min(e.listenedMs / 60_000.0, 8.0) else base * SKIP_WEIGHT
            if (w > 0) mass += w
            for (g in e.genres) {
                val key = g.normalizeForSearch()
                if (key.isEmpty()) continue
                val cur = genres[key]
                genres[key] = (cur?.first ?: g) to ((cur?.second ?: 0.0) + w)
            }
            // Mesmo artista que a música usa (o primeiro), para as duas chaves baterem.
            val artist = (e.artists.firstOrNull() ?: e.artist).normalizeForSearch()
            if (artist.isNotEmpty()) artists[artist] = (artists[artist] ?: 0.0) + w
        }
        return MoodProfile(genres, artists, mass)
    }

    /** Gosto geral sem histórico de escuta: gêneros e artistas das mais tocadas (contagens). */
    fun fromPlayCounts(songs: List<Song>, playCounts: Map<String, Int>): MoodProfile {
        val genres = HashMap<String, Pair<String, Double>>()
        val artists = HashMap<String, Double>()
        var mass = 0.0
        for (s in songs) {
            val n = (playCounts[s.id] ?: 0).toDouble()
            if (n <= 0) continue
            mass += n
            for (g in Library.splitGenres(s.genre)) {
                val key = g.normalizeForSearch()
                genres[key] = (genres[key]?.first ?: g) to ((genres[key]?.second ?: 0.0) + n)
            }
            val artist = (s.artists.firstOrNull() ?: s.artist).normalizeForSearch()
            if (artist.isNotEmpty()) artists[artist] = (artists[artist] ?: 0.0) + n
        }
        return MoodProfile(genres, artists, mass)
    }
}

/** Gêneros e artistas de um horário, com pontos (negativos quando você costuma pular). */
class MoodProfile(
    private val genres: Map<String, Pair<String, Double>>,
    private val artists: Map<String, Double>,
    /** Minutos ouvidos (com os pesos) que formam o perfil. */
    val minutes: Double,
) {
    private val maxGenre = genres.values.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: 1.0
    private val maxArtist = artists.values.maxOfOrNull { it }?.takeIf { it > 0 } ?: 1.0

    val isEmpty: Boolean get() = minutes <= 0.0

    /** Quanto dá para confiar neste perfil (0 sem histórico, perto de 1 com muito). */
    val confidence: Double get() = minutes / (minutes + Mood.CONFIDENCE_MINUTES)

    fun topGenres(limit: Int): List<String> =
        genres.values.filter { it.second > 0 }.sortedByDescending { it.second }.take(limit).map { it.first }

    /**
     * Quanto a música combina com o perfil: -1 (você pula) a 1 (o que mais toca). O artista
     * também conta, para músicas sem gênero ou de artistas que você ouve naquela hora.
     */
    fun affinity(genreKeys: List<String>, artistKey: String): Double {
        val g = genreKeys.mapNotNull { genres[it]?.second }.maxOrNull()?.let { (it / maxGenre).coerceIn(-1.0, 1.0) }
        val a = artists[artistKey]?.let { (it / maxArtist).coerceIn(-1.0, 1.0) }
        return when {
            g == null && a == null -> 0.0
            g == null -> a!! * 0.85
            a == null -> g
            g < 0 || a < 0 -> minOf(g, a)
            else -> maxOf(g, a * 0.85)
        }
    }
}

/** Nota de encaixe de cada música num horário (0 a 1), juntando o perfil, o gosto geral e a energia. */
class MoodScorer(
    private val hourProfile: MoodProfile,
    private val general: MoodProfile,
    private val hours: List<Int>,
) {
    private val cache = HashMap<String, Double>()

    fun fit(song: Song): Double = cache.getOrPut(song.id) {
        val genreNames = Library.splitGenres(song.genre)
        val genreKeys = genreNames.map { it.normalizeForSearch() }
        val artist = (song.artists.firstOrNull() ?: song.artist).normalizeForSearch()
        val c = hourProfile.confidence
        val personal = hourProfile.affinity(genreKeys, artist)
        val taste = if (general.isEmpty) 0.5 else (general.affinity(genreKeys, artist) + 1) / 2
        val energy = Mood.energyFit(genreNames, hours)
        (c * personal + (1 - c) * (0.55 * energy + 0.45 * taste)).coerceIn(0.0, 1.0)
    }

    /** Encaixe médio da playlist com o horário, em porcentagem. */
    fun matchPercent(songs: List<Song>): Int? =
        if (songs.isEmpty()) null else (songs.sumOf { fit(it) } / songs.size * 100).toInt().coerceIn(0, 100)
}
