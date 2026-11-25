package app.criol.insights

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

enum class Archetype(val label: String, val description: String) {
    BALANCED("Equilibrado", "Você equilibra favoritos e novidades, sem exagerar em nenhum lado."),
    EARLY_BIRD("Madrugador", "Suas manhãs têm trilha sonora. Boa parte da sua escuta acontece cedo."),
    EXPLORER("Explorador", "Sempre atrás de algo novo: você ouve muitos artistas diferentes."),
    GENRE_HOPPER("Eclético", "Nenhum gênero te prende. Sua biblioteca é um passeio por estilos."),
    LOYAL("Fiel", "Quando você gosta, você gosta de verdade. Seus artistas favoritos dominam a playlist."),
    NEWCOMER("Recém-chegado", "Seu perfil ainda está se formando. Continue ouvindo para revelar seu DNA musical."),
    NIGHT_OWL("Coruja", "A noite é sua. A maior parte da sua escuta acontece depois das 22h."),
    REPEATER("Repetidor", "Achou a música certa? Repete até cansar (e não cansa)."),
}

/** Traços em porcentagem (0 a 100). */
data class ListenerDna(val archetype: Archetype, val variety: Int, val loyalty: Int, val novelty: Int)

private fun share(part: Double, total: Double) = if (total > 0) part / total else 0.0

fun listenerDna(
    entries: List<ListenEntry>,
    summary: ListeningSummary,
    now: Long = System.currentTimeMillis(),
): ListenerDna {
    val total = summary.totalMs.toDouble()
    val night = summary.byHour.withIndex().sumOf { (h, ms) -> if (h >= 22 || h < 5) ms else 0L }.toDouble()
    val early = summary.byHour.withIndex().sumOf { (h, ms) -> if (h in 5..9) ms else 0L }.toDouble()
    val plays = summary.plays.toDouble()

    val variety = minOf(1.0, share(summary.uniqueArtists.toDouble(), maxOf(1.0, plays)) * 2)
    val loyalty = share(summary.topArtists.take(5).sumOf { it.plays }.toDouble(), plays)

    val monthAgo = now - 30 * DAY_MS
    val knownBefore = entries.filter { it.playedAt < monthAgo }.mapTo(HashSet()) { it.artistKey }
    val recentArtists = entries.filter { it.playedAt >= monthAgo }.mapTo(HashSet()) { it.artistKey }
    val novelty = share(recentArtists.count { it !in knownBefore }.toDouble(), recentArtists.size.toDouble())

    val topSongShare = share((summary.topSongs.firstOrNull()?.plays ?: 0).toDouble(), plays)
    val topArtistShare = share((summary.topArtists.firstOrNull()?.plays ?: 0).toDouble(), plays)

    val archetype = when {
        summary.plays < 25 -> Archetype.NEWCOMER
        share(night, total) > 0.4 -> Archetype.NIGHT_OWL
        share(early, total) > 0.35 -> Archetype.EARLY_BIRD
        topSongShare > 0.08 -> Archetype.REPEATER
        topArtistShare > 0.25 -> Archetype.LOYAL
        summary.uniqueGenres >= 15 -> Archetype.GENRE_HOPPER
        variety > 0.6 -> Archetype.EXPLORER
        else -> Archetype.BALANCED
    }
    return ListenerDna(
        archetype = archetype,
        variety = (variety * 100).roundToInt(),
        loyalty = (loyalty * 100).roundToInt(),
        novelty = (novelty * 100).roundToInt(),
    )
}

enum class AchievementKind(val label: String, val description: String) {
    FIRST_PLAY("Primeira reprodução", "Ouça sua primeira música no Criol."),
    HOURS_10("10 horas de som", "Acumule 10 horas ouvindo música."),
    HOURS_100("Centenário", "Acumule 100 horas ouvindo música."),
    MARATHON("Maratona", "Ouça 3 horas de música em um único dia."),
    NIGHT_OWL("Coruja", "Toque 50 músicas entre meia-noite e 5h."),
    EARLY_BIRD("Madrugador", "Toque 50 músicas entre 5h e 8h da manhã."),
    EXPLORER("Explorador", "Ouça 100 artistas diferentes."),
    GENRE_HOPPER("Eclético", "Ouça músicas de 20 gêneros diferentes."),
    SUPERFAN("Fã nº 1", "Toque 100 músicas do mesmo artista."),
    BROKEN_RECORD("Disco arranhado", "Ouça a mesma música 10 vezes em um único dia."),
    STREAK_7("Semana perfeita", "Ouça música 7 dias seguidos."),
    STREAK_30("Mês sem pausa", "Ouça música 30 dias seguidos."),
}

data class Achievement(val kind: AchievementKind, val current: Int, val target: Int) {
    val unlocked: Boolean get() = current >= target
    val progress: Float get() = (current.toFloat() / target).coerceIn(0f, 1f)
}

fun achievements(entries: List<ListenEntry>, summary: ListeningSummary): List<Achievement> {
    val hours = (summary.totalMs / 3_600_000).toInt()
    val maxDayMinutes = ((summary.byDay.values.maxOrNull() ?: 0L) / 60_000).toInt()
    val counted = entries.filter { it.counted }
    val hours0 = counted.map { zoned(it.playedAt).hour }
    val nightPlays = hours0.count { it < 5 }
    val earlyPlays = hours0.count { it in 5..7 }
    val maxSongInDay = counted.groupingBy { dayKey(it.playedAt) + "|" + it.songId }.eachCount().values.maxOrNull() ?: 0
    val longest = streaks(entries).longest

    fun make(kind: AchievementKind, current: Int, target: Int) = Achievement(kind, current.coerceAtMost(target), target)
    return listOf(
        make(AchievementKind.FIRST_PLAY, summary.plays, 1),
        make(AchievementKind.HOURS_10, hours, 10),
        make(AchievementKind.HOURS_100, hours, 100),
        make(AchievementKind.MARATHON, maxDayMinutes, 180),
        make(AchievementKind.NIGHT_OWL, nightPlays, 50),
        make(AchievementKind.EARLY_BIRD, earlyPlays, 50),
        make(AchievementKind.EXPLORER, summary.uniqueArtists, 100),
        make(AchievementKind.GENRE_HOPPER, summary.uniqueGenres, 20),
        make(AchievementKind.SUPERFAN, summary.topArtists.firstOrNull()?.plays ?: 0, 100),
        make(AchievementKind.BROKEN_RECORD, maxSongInDay, 10),
        make(AchievementKind.STREAK_7, longest, 7),
        make(AchievementKind.STREAK_30, longest, 30),
    )
}

// ----------------------------------------------------------------------
// Resumo mensal
// ----------------------------------------------------------------------

@Serializable
data class BusiestDay(val day: String, val ms: Long)

@Serializable
data class MonthlyRecap(
    val month: String,
    val generatedAt: Long,
    val totalMs: Long,
    val previousTotalMs: Long? = null,
    val plays: Int,
    val activeDays: Int,
    val busiestDay: BusiestDay? = null,
    val byDay: Map<String, Long> = emptyMap(),
    val byHour: List<Long> = emptyList(),
    val favoriteDaypart: Daypart? = null,
    val newArtists: Int = 0,
    val topSongs: List<RankedItem> = emptyList(),
    val topArtists: List<RankedItem> = emptyList(),
    val topAlbums: List<RankedItem> = emptyList(),
    val topGenres: List<RankedItem> = emptyList(),
    val uniqueSongs: Int = 0,
    val uniqueArtists: Int = 0,
    val uniqueGenres: Int = 0,
)

fun buildMonthlyRecap(entries: List<ListenEntry>, month: String, now: Long = System.currentTimeMillis()): MonthlyRecap {
    val range = monthRange(month)
    val monthEntries = entries.inRange(range.first, range.last + 1)
    val summary = summarize(monthEntries, 10)
    val prev = monthRange(previousMonth(month))
    val prevEntries = entries.inRange(prev.first, prev.last + 1)

    val hasHistoryBefore = entries.any { it.playedAt < range.first }
    val before = entries.filter { it.playedAt < range.first }.mapTo(HashSet()) { it.artistKey }
    val newArtists = monthEntries.mapTo(HashSet()) { it.artistKey }.count { it.isNotEmpty() && it !in before }
    val busiest = summary.byDay.maxByOrNull { it.value }

    return MonthlyRecap(
        month = month,
        generatedAt = now,
        totalMs = summary.totalMs,
        previousTotalMs = if (prevEntries.isNotEmpty()) prevEntries.sumOf { it.listenedMs } else null,
        plays = summary.plays,
        activeDays = summary.activeDays,
        busiestDay = busiest?.let { BusiestDay(it.key, it.value) },
        byDay = summary.byDay,
        byHour = summary.byHour,
        favoriteDaypart = favoriteDaypart(summary.byHour),
        newArtists = if (hasHistoryBefore) newArtists else 0,
        topSongs = summary.topSongs.take(10),
        topArtists = summary.topArtists.take(5),
        topAlbums = summary.topAlbums.take(5),
        topGenres = summary.topGenres.take(5),
        uniqueSongs = summary.uniqueSongs,
        uniqueArtists = summary.uniqueArtists,
        uniqueGenres = summary.uniqueGenres,
    )
}

/** Meses já encerrados que têm histórico mas ainda não têm resumo salvo. */
fun pendingRecapMonths(entries: List<ListenEntry>, saved: Set<String>, current: String = currentMonthKey()): List<String> =
    entries.mapTo(sortedSetOf()) { monthKey(it.playedAt) }.filter { it < current && it !in saved }
