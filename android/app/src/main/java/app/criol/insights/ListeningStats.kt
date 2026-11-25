package app.criol.insights

import app.criol.data.normalizeForSearch
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.pow
import kotlin.math.roundToInt

const val DAY_MS = 24L * 60 * 60 * 1000

@Serializable
enum class Daypart(val label: String, val phrase: String) {
    MORNING("Manhã", "de manhã"),
    AFTERNOON("Tarde", "à tarde"),
    NIGHT("Noite", "à noite"),
    DAWN("Madrugada", "de madrugada");

    companion object {
        fun ofHour(hour: Int): Daypart = when {
            hour < 5 -> DAWN
            hour < 12 -> MORNING
            hour < 18 -> AFTERNOON
            else -> NIGHT
        }

        fun of(epochMs: Long): Daypart = ofHour(zoned(epochMs).hour)
        fun now(): Daypart = ofHour(LocalDateTime.now().hour)
    }
}

fun zoned(epochMs: Long): ZonedDateTime = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())
fun dayKey(epochMs: Long): String = zoned(epochMs).toLocalDate().toString()
fun monthKey(epochMs: Long): String = YearMonth.from(zoned(epochMs)).toString()
fun currentMonthKey(): String = YearMonth.now().toString()

/** Início (inclusivo) e fim (exclusivo) de um mês "AAAA-MM" em epoch ms. */
fun monthRange(month: String): LongRange {
    val ym = YearMonth.parse(month)
    val zone = ZoneId.systemDefault()
    val from = ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val to = ym.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    return from until to
}

fun previousMonth(month: String): String = YearMonth.parse(month).minusMonths(1).toString()

fun List<ListenEntry>.inRange(from: Long, to: Long = Long.MAX_VALUE) = filter { it.playedAt in from until to }

fun msToMinutes(ms: Long): Int = (ms / 60_000.0).roundToInt()

/** Chave do artista principal da escuta (nome sem acentos e minúsculo). */
val ListenEntry.artistKey: String get() = artist.normalizeForSearch()

/** Um artista, música, álbum ou gênero no ranking, com reproduções e tempo ouvido. */
@Serializable
data class RankedItem(
    /** id da música/álbum, ou nome normalizado para artista e gênero. */
    val id: String,
    val name: String,
    /** Linha extra (artista, para músicas e álbuns). */
    val subtitle: String = "",
    /** Álbum usado para mostrar a capa. */
    val albumId: String = "",
    val plays: Int = 0,
    val listenedMs: Long = 0,
)

data class ListeningSummary(
    val totalMs: Long,
    val plays: Int,
    val activeDays: Int,
    val byDay: Map<String, Long>,
    val byHour: List<Long>,
    /** 0 = segunda ... 6 = domingo. */
    val byWeekday: List<Long>,
    val firstListenAt: Long?,
    val lastListenAt: Long?,
    val topSongs: List<RankedItem>,
    val topArtists: List<RankedItem>,
    val topAlbums: List<RankedItem>,
    val topGenres: List<RankedItem>,
    val uniqueSongs: Int,
    val uniqueArtists: Int,
    val uniqueGenres: Int,
)

private class Acc(val id: String, var name: String, val subtitle: String, var albumId: String) {
    var plays = 0
    var listenedMs = 0L
    fun add(e: ListenEntry) {
        listenedMs += e.listenedMs
        if (e.counted) plays++
        if (albumId.isEmpty() && e.albumId.isNotEmpty()) albumId = e.albumId
    }

    fun toItem() = RankedItem(id, name, subtitle, albumId, plays, listenedMs)
}

private fun HashMap<String, Acc>.bump(key: String, e: ListenEntry, create: () -> Acc) =
    getOrPut(key, create).add(e)

private fun HashMap<String, Acc>.rank(limit: Int): List<RankedItem> =
    values.asSequence()
        .filter { it.plays > 0 || it.listenedMs > 0 }
        .sortedWith(compareByDescending<Acc> { it.plays }.thenByDescending { it.listenedMs })
        .take(limit)
        .map { it.toItem() }
        .toList()

fun summarize(entries: List<ListenEntry>, limit: Int = 10): ListeningSummary {
    val songs = HashMap<String, Acc>()
    val artists = HashMap<String, Acc>()
    val albums = HashMap<String, Acc>()
    val genres = HashMap<String, Acc>()
    val byHour = LongArray(24)
    val byWeekday = LongArray(7)
    val byDay = HashMap<String, Long>()
    var total = 0L
    var plays = 0
    var first: Long? = null
    var last: Long? = null

    for (e in entries) {
        total += e.listenedMs
        if (e.counted) plays++
        first = minOf(first ?: e.playedAt, e.playedAt)
        last = maxOf(last ?: e.playedAt, e.playedAt)
        val t = zoned(e.playedAt)
        byHour[t.hour] += e.listenedMs
        byWeekday[t.dayOfWeek.value - 1] += e.listenedMs
        val day = t.toLocalDate().toString()
        byDay[day] = (byDay[day] ?: 0L) + e.listenedMs

        songs.bump(e.songId, e) { Acc(e.songId, e.title, e.artist, e.albumId) }
        if (e.artist.isNotBlank()) artists.bump(e.artistKey, e) { Acc(e.artistKey, e.artist, "", e.albumId) }
        if (e.albumId.isNotBlank()) albums.bump(e.albumId, e) { Acc(e.albumId, e.album, e.artist, e.albumId) }
        for (g in e.genres) {
            val key = g.normalizeForSearch()
            genres.bump(key, e) { Acc(key, g, "", "") }
        }
    }

    return ListeningSummary(
        totalMs = total,
        plays = plays,
        activeDays = byDay.size,
        byDay = byDay,
        byHour = byHour.toList(),
        byWeekday = byWeekday.toList(),
        firstListenAt = first,
        lastListenAt = last,
        topSongs = songs.rank(limit),
        topArtists = artists.rank(limit),
        topAlbums = albums.rank(limit),
        topGenres = genres.rank(limit),
        uniqueSongs = songs.size,
        uniqueArtists = artists.size,
        uniqueGenres = genres.size,
    )
}

data class Streaks(val current: Int, val longest: Int)

/** Dias seguidos com pelo menos uma escuta (terminando hoje ou ontem) e o recorde. */
fun streaks(entries: List<ListenEntry>, today: LocalDate = LocalDate.now()): Streaks {
    val days = entries.mapTo(HashSet()) { zoned(it.playedAt).toLocalDate() }
    if (days.isEmpty()) return Streaks(0, 0)
    val sorted = days.sorted()
    var longest = 1
    var run = 1
    for (i in 1 until sorted.size) {
        run = if (ChronoUnit.DAYS.between(sorted[i - 1], sorted[i]) == 1L) run + 1 else 1
        longest = maxOf(longest, run)
    }
    var cursor = if (today in days) today else today.minusDays(1)
    var current = 0
    while (cursor in days) {
        current++
        cursor = cursor.minusDays(1)
    }
    return Streaks(current, longest)
}

fun favoriteDaypart(byHour: List<Long>): Daypart? {
    val totals = HashMap<Daypart, Long>()
    byHour.forEachIndexed { hour, ms -> totals[Daypart.ofHour(hour)] = (totals[Daypart.ofHour(hour)] ?: 0L) + ms }
    val best = totals.maxByOrNull { it.value } ?: return null
    return best.key.takeIf { best.value > 0 }
}

data class SongScore(val songId: String, val plays: Int, val score: Double)

/**
 * Pontua músicas pelas reproduções, com decaimento exponencial pela idade: favoritas recentes
 * ficam na frente. O filtro permite restringir a um período do dia, dia da semana etc.
 */
fun scoreSongs(
    entries: List<ListenEntry>,
    halfLifeDays: Double = 30.0,
    now: Long = System.currentTimeMillis(),
    filter: ((ListenEntry) -> Boolean)? = null,
): List<SongScore> {
    val scores = HashMap<String, DoubleArray>() // [score, plays]
    for (e in entries) {
        if (!e.counted) continue
        if (filter != null && !filter(e)) continue
        val ageDays = (now - e.playedAt).toDouble() / DAY_MS
        val weight = 0.5.pow(ageDays / halfLifeDays)
        val s = scores.getOrPut(e.songId) { DoubleArray(2) }
        s[0] += weight
        s[1] += 1.0
    }
    return scores.map { (id, s) -> SongScore(id, s[1].toInt(), s[0]) }.sortedByDescending { it.score }
}

data class GenreScore(val name: String, val score: Double)

fun scoreGenres(
    entries: List<ListenEntry>,
    halfLifeDays: Double = 45.0,
    now: Long = System.currentTimeMillis(),
    filter: ((ListenEntry) -> Boolean)? = null,
): List<GenreScore> {
    val scores = LinkedHashMap<String, Pair<String, Double>>()
    for (e in entries) {
        if (filter != null && !filter(e)) continue
        val ageDays = (now - e.playedAt).toDouble() / DAY_MS
        val weight = 0.5.pow(ageDays / halfLifeDays) * (e.listenedMs / 60_000.0)
        for (g in e.genres) {
            val key = g.normalizeForSearch()
            val cur = scores[key]
            scores[key] = (cur?.first ?: g) to ((cur?.second ?: 0.0) + weight)
        }
    }
    return scores.values.map { GenreScore(it.first, it.second) }.sortedByDescending { it.score }
}

/** "12 minutos", "1 minuto" ou "menos de 1 minuto" (nunca "0 minutos" para algo que tocou). */
fun formatListenMinutes(ms: Long): String {
    val m = msToMinutes(ms)
    return when {
        m == 0 && ms > 0 -> "menos de 1 minuto"
        m == 1 -> "1 minuto"
        else -> "${formatNumber(m)} minutos"
    }
}

fun formatNumber(n: Int): String = java.text.NumberFormat.getIntegerInstance(java.util.Locale.forLanguageTag("pt-BR")).format(n)

fun playsLabel(n: Int) = if (n == 1) "1 reprodução" else "${formatNumber(n)} reproduções"
fun daysLabel(n: Int) = if (n == 1) "1 dia" else "$n dias"
