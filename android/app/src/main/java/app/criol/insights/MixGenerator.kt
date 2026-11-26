package app.criol.insights

import app.criol.data.Library
import app.criol.data.Song
import app.criol.data.UserData
import app.criol.data.normalizeForSearch
import kotlinx.serialization.Serializable
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.IsoFields
import java.util.Locale
import kotlin.random.Random

@Serializable
enum class MixKind { DAYLIST, DAYPART, ON_REPEAT, REPEAT_REWIND, DISCOVER, GENRE }

/** Uma playlist gerada automaticamente ("Feito para você"). */
@Serializable
data class Mix(
    val id: String,
    val kind: MixKind,
    val daypart: Daypart? = null,
    val genre: String? = null,
    /** Até três gêneros que descrevem a mix (usados no título da daylist). */
    val tags: List<String> = emptyList(),
    val songIds: List<String> = emptyList(),
    val coverAlbumIds: List<String> = emptyList(),
    val generatedAt: Long,
    val periodKey: String,
    /** 1 = segunda ... 7 = domingo. */
    val weekday: Int,
    /** false quando ainda não há histórico suficiente e a mix usa só contagens e a biblioteca. */
    val personalized: Boolean,
)

val MixOrder = listOf(
    "daylist", "on-repeat", "repeat-rewind", "daypart-morning", "daypart-afternoon", "daypart-night", "discover",
)

fun sortMixes(mixes: List<Mix>): List<Mix> =
    mixes.sortedBy { MixOrder.indexOf(it.id).let { i -> if (i == -1) 99 else i } }

private fun weekKey(now: LocalDateTime) =
    "${now.get(IsoFields.WEEK_BASED_YEAR)}-w${now.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}"

fun mixPeriodKey(kind: MixKind, now: LocalDateTime, daypart: Daypart? = null): String = when (kind) {
    MixKind.DAYLIST -> "${now.toLocalDate()}-${(daypart ?: Daypart.ofHour(now.hour)).name.lowercase()}"
    MixKind.DAYPART, MixKind.ON_REPEAT -> now.toLocalDate().toString()
    else -> weekKey(now)
}

/** Período atual da daylist; muda quatro vezes por dia. */
fun currentDaylistPeriod(now: LocalDateTime = LocalDateTime.now()) = mixPeriodKey(MixKind.DAYLIST, now)

/**
 * Monta as mixes a partir do histórico de escuta do Criol. Sem histórico ainda, usa as
 * contagens de reprodução e a própria biblioteca (gêneros mais tocados, mais tocadas etc.).
 */
class MixGenerator(
    private val library: Library,
    private val userData: UserData,
    allEntries: List<ListenEntry>,
    private val now: LocalDateTime = LocalDateTime.now(),
    private val random: Random = Random.Default,
) {
    private val nowMs = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val entries = allEntries.filter { it.playedAt >= nowMs - 400 * DAY_MS }
    private val heard: Set<String> = entries.mapTo(HashSet()) { it.songId }
    private val genreIndex = library.genres.associateBy { it.name.normalizeForSearch() }

    /** Equivalente às "mais tocadas do servidor" no Crify: as contagens de reprodução do Criol. */
    private val topPlayed: List<Song> by lazy {
        library.songs.filter { (userData.playCounts[it.id] ?: 0) > 0 }
            .sortedByDescending { userData.playCounts[it.id] ?: 0 }
            .take(300)
    }

    // ------------------------------------------------------------------
    // Ajudantes
    // ------------------------------------------------------------------

    private fun resolve(ids: List<String>) = ids.mapNotNull { library.songsById[it] }

    private fun randomByGenre(name: String, limit: Int, neverPlayed: Boolean = false): List<Song> {
        var list = genreIndex[name.normalizeForSearch()]?.songs ?: return emptyList()
        if (neverPlayed) list = list.filter { it.id !in heard && (userData.playCounts[it.id] ?: 0) == 0 }
        return list.shuffled(random).take(limit)
    }

    /** Gêneros pelo histórico; sem histórico, pelos gêneros das mais tocadas; senão, os maiores. */
    private fun tasteGenres(filter: ((ListenEntry) -> Boolean)? = null): Pair<List<String>, Boolean> {
        fun known(names: List<String>) = names.filter { genreIndex.containsKey(it.normalizeForSearch()) }

        val fromHistory = known(scoreGenres(entries, now = nowMs, filter = filter).map { it.name })
        if (fromHistory.size >= 2) return fromHistory to true
        val fallback = known(scoreGenres(entries, now = nowMs).map { it.name })
        if (fallback.size >= 2) return fallback to true

        val counts = LinkedHashMap<String, Pair<String, Int>>()
        for (s in topPlayed) for (g in Library.splitGenres(s.genre)) {
            val key = g.normalizeForSearch()
            val cur = counts[key]
            counts[key] = (cur?.first ?: g) to ((cur?.second ?: 0) + (userData.playCounts[s.id] ?: 1))
        }
        var genres = known(counts.values.sortedByDescending { it.second }.map { it.first })
        if (genres.isEmpty()) genres = library.genres.take(12).shuffled(random).map { it.name }
        return genres to false
    }

    private fun interleave(lists: List<List<Song>>): List<Song> {
        val result = ArrayList<Song>()
        val max = lists.maxOfOrNull { it.size } ?: 0
        for (i in 0 until max) for (list in lists) list.getOrNull(i)?.let { result += it }
        return result
    }

    /** Sem repetidas e no máximo [maxPerArtist] músicas do mesmo artista. */
    private fun finalize(songs: List<Song>, limit: Int = MIX_SIZE, maxPerArtist: Int = MAX_PER_ARTIST): List<Song> {
        val seen = HashSet<String>()
        val perArtist = HashMap<String, Int>()
        val result = ArrayList<Song>()
        for (s in songs) {
            if (!seen.add(s.id)) continue
            val artist = (s.artists.firstOrNull() ?: s.albumArtist).normalizeForSearch()
            val count = perArtist[artist] ?: 0
            if (artist.isNotEmpty() && count >= maxPerArtist) continue
            perArtist[artist] = count + 1
            result += s
            if (result.size >= limit) break
        }
        return result
    }

    /** Pega uma fatia variada das mais bem pontuadas. */
    private fun sampleTop(ids: List<String>, take: Int): List<String> {
        val headSize = (take * 0.6).toInt().coerceAtLeast(1)
        val head = ids.take(headSize)
        val tail = ids.drop(head.size).take(take * 3).shuffled(random).take(take - head.size)
        return (head + tail).shuffled(random)
    }

    private fun build(
        id: String,
        kind: MixKind,
        songs: List<Song>,
        personalized: Boolean,
        tags: List<String> = emptyList(),
        daypart: Daypart? = null,
        genre: String? = null,
    ): Mix {
        val covers = songs.map { it.albumId }.distinct().filter { library.artPath(it) != null }.take(4)
        return Mix(
            id = id,
            kind = kind,
            daypart = daypart,
            genre = genre,
            tags = tags,
            songIds = songs.map { it.id },
            coverAlbumIds = covers,
            generatedAt = nowMs,
            periodKey = mixPeriodKey(kind, now, daypart),
            weekday = now.dayOfWeek.value,
            personalized = personalized,
        )
    }

    // ------------------------------------------------------------------
    // Mixes
    // ------------------------------------------------------------------

    fun daypartMix(daypart: Daypart, daylist: Boolean): Mix {
        val weekday = now.dayOfWeek
        val inDaypart = { e: ListenEntry -> Daypart.of(e.playedAt) == daypart }
        // A daylist também puxa o que você costuma ouvir neste dia da semana.
        val weighted: (ListenEntry) -> Boolean =
            if (daylist) { e -> inDaypart(e) || zoned(e.playedAt).dayOfWeek == weekday } else inDaypart

        val (genres, personalized) = tasteGenres(weighted)
        val top = if (daylist) genres.take(3) else genres.take(5).shuffled(random).take(3)

        val favoriteIds = scoreSongs(entries, now = nowMs, filter = weighted).map { it.songId }
        val favorites = resolve(sampleTop(favoriteIds, 18))
        val discovered = interleave(top.map { randomByGenre(it, 20) })

        var songs = finalize(interleave(listOf(favorites, discovered)))
        if (songs.size < 10) songs = finalize(songs + topPlayed.shuffled(random))
        if (songs.size < 10) songs = finalize(songs + library.songs.shuffled(random).take(300))

        return build(
            id = if (daylist) "daylist" else "daypart-${daypart.name.lowercase()}",
            kind = if (daylist) MixKind.DAYLIST else MixKind.DAYPART,
            songs = songs,
            personalized = personalized,
            tags = top,
            daypart = daypart,
        )
    }

    fun onRepeat(): Mix {
        val since = nowMs - 30 * DAY_MS
        val ranked = scoreSongs(entries, halfLifeDays = 14.0, now = nowMs) { it.playedAt >= since }
            .filter { it.plays >= 2 }
        var songs = resolve(ranked.take(30).map { it.songId })
        val personalized = songs.size >= 8
        if (!personalized) {
            val recentTop = topPlayed.filter { (userData.lastPlayed[it.id] ?: 0L) >= since }
            songs = songs + recentTop + topPlayed
        }
        return build("on-repeat", MixKind.ON_REPEAT, finalize(songs, 30, 5), personalized)
    }

    fun repeatRewind(): Mix {
        val recentCut = nowMs - 30 * DAY_MS
        val oldCut = nowMs - 400 * DAY_MS
        val recentlyPlayed = entries.filter { it.playedAt >= recentCut }.mapTo(HashSet()) { it.songId }
        val ranked = scoreSongs(entries, halfLifeDays = 120.0, now = nowMs) {
            it.playedAt in oldCut until recentCut && it.songId !in recentlyPlayed
        }.filter { it.plays >= 3 }
        var songs = resolve(ranked.take(30).map { it.songId })
        val personalized = songs.size >= 8
        if (!personalized) {
            songs = songs + topPlayed.filter {
                it.id !in recentlyPlayed && (userData.lastPlayed[it.id] ?: 0L) < recentCut
            }
        }
        return build("repeat-rewind", MixKind.REPEAT_REWIND, finalize(songs, 30, 4), personalized)
    }

    fun discover(): Mix {
        val (genres, personalized) = tasteGenres()
        var fresh = interleave(genres.take(5).map { randomByGenre(it, 25, neverPlayed = true) })
        if (fresh.size < 15) {
            fresh = fresh + library.songs
                .filter { it.id !in heard && (userData.playCounts[it.id] ?: 0) == 0 }
                .shuffled(random).take(60)
        }
        return build("discover", MixKind.DISCOVER, finalize(fresh, 30, 2), personalized, tags = genres.take(3))
    }

    fun genreMix(genre: String, personalized: Boolean): Mix {
        val key = genre.normalizeForSearch()
        val favoriteIds = scoreSongs(entries, halfLifeDays = 60.0, now = nowMs) { e ->
            e.genres.any { it.normalizeForSearch() == key }
        }.map { it.songId }
        val favorites = resolve(sampleTop(favoriteIds, 12))
        val others = randomByGenre(genre, 40)
        return build(
            id = "genre-" + key.replace(Regex("[^a-z0-9]+"), "-").trim('-'),
            kind = MixKind.GENRE,
            songs = finalize(interleave(listOf(favorites, others))),
            personalized = personalized,
            tags = listOf(genre),
            genre = genre,
        )
    }

    fun genreMixes(): List<Mix> {
        val (genres, personalized) = tasteGenres()
        return genres.take(3).map { genreMix(it, personalized) }
    }

    // ------------------------------------------------------------------
    // Atualização
    // ------------------------------------------------------------------

    fun isFresh(mix: Mix): Boolean {
        val daypart = if (mix.kind == MixKind.DAYLIST) Daypart.ofHour(now.hour) else mix.daypart
        if (mix.periodKey != mixPeriodKey(mix.kind, now, daypart)) return false
        // Uma mix que saiu vazia tenta de novo depois de uma hora (o histórico pode ter crescido).
        return mix.songIds.isNotEmpty() || nowMs - mix.generatedAt < 60 * 60 * 1000
    }

    private val tasks: List<Pair<String, () -> Mix>> = listOf(
        "daylist" to { daypartMix(Daypart.ofHour(now.hour), daylist = true) },
        "on-repeat" to { onRepeat() },
        "repeat-rewind" to { repeatRewind() },
        "daypart-morning" to { daypartMix(Daypart.MORNING, daylist = false) },
        "daypart-afternoon" to { daypartMix(Daypart.AFTERNOON, daylist = false) },
        "daypart-night" to { daypartMix(Daypart.NIGHT, daylist = false) },
        "discover" to { discover() },
    )

    fun allFresh(stored: List<Mix>): Boolean {
        val byId = stored.associateBy { it.id }
        val genre = stored.filter { it.kind == MixKind.GENRE }
        return tasks.all { (id, _) -> byId[id]?.let(::isFresh) == true } &&
            genre.isNotEmpty() && genre.all(::isFresh)
    }

    /** Refaz só as mixes cujo período mudou (daylist a cada período do dia, diárias, semanais). */
    fun refresh(stored: List<Mix>, force: Boolean = false): List<Mix> {
        val byId = if (force) emptyMap() else stored.associateBy { it.id }
        val result = ArrayList<Mix>()
        for ((id, make) in tasks) {
            val existing = byId[id]
            result += if (existing != null && isFresh(existing)) existing else make()
        }
        val genre = if (force) emptyList() else stored.filter { it.kind == MixKind.GENRE }
        result += if (genre.isNotEmpty() && genre.all(::isFresh)) genre else genreMixes()
        return sortMixes(result)
    }

    /** Refaz uma mix só (botão "Gerar esta mix novamente"). */
    fun rebuild(mix: Mix): Mix = when (mix.kind) {
        MixKind.DAYLIST -> daypartMix(Daypart.ofHour(now.hour), daylist = true)
        MixKind.DAYPART -> daypartMix(mix.daypart ?: Daypart.MORNING, daylist = false)
        MixKind.ON_REPEAT -> onRepeat()
        MixKind.REPEAT_REWIND -> repeatRewind()
        MixKind.DISCOVER -> discover()
        MixKind.GENRE -> genreMix(mix.genre.orEmpty(), mix.personalized)
    }

    companion object {
        const val MIX_SIZE = 40
        const val MAX_PER_ARTIST = 3
    }
}

// ----------------------------------------------------------------------
// Textos
// ----------------------------------------------------------------------

data class MixText(val title: String, val description: String, val coverLabel: String)

private val ptBR: Locale = Locale.forLanguageTag("pt-BR")

private fun String.capitalized() = replaceFirstChar { if (it.isLowerCase()) it.titlecase(ptBR) else it.toString() }

fun weekdayName(day: Int): String =
    java.time.DayOfWeek.of(day.coerceIn(1, 7)).getDisplayName(TextStyle.FULL, ptBR).lowercase(ptBR)

fun mixText(mix: Mix): MixText {
    val daypart = mix.daypart ?: Daypart.now()
    return when (mix.kind) {
        MixKind.DAYLIST -> {
            val tags = mix.tags.joinToString(" ") { it.lowercase(ptBR) }
            MixText(
                title = "daylist • $tags ${weekdayName(mix.weekday)} ${daypart.phrase}".replace(Regex("\\s+"), " ").trim(),
                description = "Seu dia em uma playlist. Muda ao longo do dia.",
                coverLabel = "daylist",
            )
        }
        MixKind.DAYPART -> {
            val genres = mix.tags.take(2).joinToString(", ")
            val description = if (genres.isNotEmpty()) when (daypart) {
                Daypart.MORNING -> "Pra começar o dia com $genres."
                Daypart.AFTERNOON -> "Pra tarde render: $genres e mais."
                Daypart.NIGHT -> "Pra noite: $genres e mais."
                Daypart.DAWN -> "Pra madrugada: $genres e mais."
            } else "O que combina com as suas ${daypart.label.lowercase(ptBR)}s."
            MixText("Mix da ${daypart.label}", description, daypart.label)
        }
        MixKind.DISCOVER -> MixText(
            "Radar de descobertas",
            "Músicas da sua biblioteca que você nunca ouviu, nos gêneros que você curte.",
            "Radar de descobertas",
        )
        MixKind.GENRE -> {
            val genre = mix.genre.orEmpty()
            MixText("Mix ${genre.capitalized()}", "Seus favoritos de $genre misturados com faixas pra descobrir.", genre.capitalized())
        }
        MixKind.ON_REPEAT -> MixText("No Repeat", "As músicas que você está curtindo agora.", "No Repeat")
        MixKind.REPEAT_REWIND -> MixText("De volta pro repeat", "Recordar é viver.", "De volta pro repeat")
    }
}
