package app.criol.insights

import app.criol.data.Library
import app.criol.data.Song
import app.criol.data.UserData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.random.Random

class InsightsTest {
    private var n = 0

    private fun song(artist: String, genre: String, album: String = "$artist LP"): Song {
        n++
        return Song(
            id = "s$n", uri = "content://x/$n", rootUri = "r", title = "Faixa $n", artist = artist,
            albumArtist = artist, album = album, albumId = "$artist|$album", genre = genre, year = 2000,
            track = n, disc = 1, durationMs = 200_000, folder = "Music/$artist", fileName = "$n.mp3",
            size = 1, lastModified = 1, dateAdded = 1, mime = "audio/mpeg", bitrate = 320_000,
        )
    }

    private val songs = buildList {
        repeat(8) { add(song("Cartola", "Samba")) }
        repeat(8) { add(song("Clara Nunes", "Samba")) }
        repeat(8) { add(song("Gal Costa", "MPB")) }
        repeat(8) { add(song("Caetano", "MPB")) }
        repeat(8) { add(song("Metallica", "Metal")) }
        repeat(8) { add(song("Slayer", "Metal")) }
        repeat(8) { add(song("Miles Davis", "Jazz")) }
    }
    private val lib = Library.build(songs, emptyMap(), File("art"))

    private fun ms(t: LocalDateTime) = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun listen(s: Song, at: LocalDateTime, listened: Long = 180_000) =
        ListenEntry.from(s, listened, ms(at), id = "e${ms(at)}-${s.id}")

    @Test
    fun entryCountsAfterThirtySecondsOrHalfTheTrack() {
        val s = songs.first()
        assertFalse(ListenEntry.from(s, 20_000, 0).counted)
        assertTrue(ListenEntry.from(s, 30_000, 0).counted)
        val short = s.copy(durationMs = 40_000)
        assertTrue(ListenEntry.from(short, 20_000, 0).counted)
        assertEquals(listOf("Samba"), ListenEntry.from(s, 1, 0).genres)
    }

    @Test
    fun summaryRanksByPlaysAndBucketsByHour() {
        val night = LocalDateTime.of(2026, 11, 20, 23, 0)
        val entries = listOf(
            listen(songs[0], night),
            listen(songs[1], night.plusMinutes(4)),
            listen(songs[16], night.plusMinutes(8)),
            listen(songs[16], night.plusMinutes(12), listened = 5_000), // pulada: não conta
        )
        val s = summarize(entries)
        assertEquals(3, s.plays)
        assertEquals("Cartola", s.topArtists.first().name)
        assertEquals(2, s.topArtists.first().plays)
        assertEquals(1, s.activeDays)
        assertTrue(s.byHour[23] > 0)
        assertEquals(Daypart.NIGHT, favoriteDaypart(s.byHour))
    }

    @Test
    fun streaksCountConsecutiveDaysEndingToday() {
        val today = LocalDate.of(2026, 11, 23)
        val days = listOf(today, today.minusDays(1), today.minusDays(2), today.minusDays(10), today.minusDays(11))
        val entries = days.map { listen(songs[0], it.atTime(12, 0)) }
        assertEquals(Streaks(current = 3, longest = 3), streaks(entries, today))
        // Sem escuta hoje, a sequência ainda vale se terminou ontem.
        assertEquals(3, streaks(entries.drop(1).plus(listen(songs[0], today.minusDays(3).atTime(9, 0))), today).current)
    }

    @Test
    fun daylistFollowsNightListeningGenres() {
        // O histórico mostra Metal à noite e Samba de manhã.
        val base = LocalDateTime.of(2026, 11, 22, 0, 0)
        val entries = buildList {
            for (d in 0 until 10) {
                songs.filter { it.genre == "Metal" }.take(6).forEach { add(listen(it, base.minusDays(d.toLong()).withHour(22))) }
                songs.filter { it.genre == "Samba" }.take(6).forEach { add(listen(it, base.minusDays(d.toLong()).withHour(9))) }
                songs.filter { it.genre == "Jazz" }.take(2).forEach { add(listen(it, base.minusDays(d.toLong()).withHour(23))) }
            }
        }
        val now = LocalDateTime.of(2026, 11, 23, 21, 30)
        val gen = MixGenerator(lib, UserData(), entries, now, Random(7))
        val daylist = gen.daypartMix(Daypart.ofHour(now.hour), daylist = true)

        assertEquals(Daypart.NIGHT, daylist.daypart)
        assertEquals("Metal", daylist.tags.first())
        assertTrue(daylist.personalized)
        assertTrue(daylist.songIds.size in 10..MixGenerator.MIX_SIZE)
        val picked = daylist.songIds.map { lib.songsById.getValue(it) }
        assertTrue(picked.groupBy { it.artist }.values.all { it.size <= MixGenerator.MAX_PER_ARTIST })
        assertEquals(picked.size, picked.map { it.id }.distinct().size)
        val text = mixText(daylist)
        assertTrue(text.title, text.title.startsWith("daylist • metal"))
        assertTrue(text.title, text.title.endsWith("segunda-feira à noite"))
    }

    @Test
    fun coldStartFallsBackToPlayCountsAndLibrary() {
        val counts = UserData(playCounts = songs.filter { it.genre == "Jazz" }.associate { it.id to 5 })
        val gen = MixGenerator(lib, counts, emptyList(), LocalDateTime.of(2026, 11, 23, 10, 0), Random(1))
        val daylist = gen.daypartMix(Daypart.MORNING, daylist = true)
        assertFalse(daylist.personalized)
        assertEquals(listOf("Jazz"), daylist.tags)
        assertTrue(daylist.songIds.size >= 10)
        // Sem nenhuma escuta, o Radar de descobertas traz músicas nunca tocadas.
        val discover = gen.discover()
        assertTrue(discover.songIds.isNotEmpty())
        assertTrue(discover.songIds.none { (counts.playCounts[it] ?: 0) > 0 })
    }

    @Test
    fun refreshOnlyRebuildsExpiredMixes() {
        val data = UserData(playCounts = songs.filter { it.genre == "Metal" }.associate { it.id to 3 })
        val morning = LocalDateTime.of(2026, 11, 23, 9, 0)
        val first = MixGenerator(lib, data, emptyList(), morning, Random(1)).refresh(emptyList())
        assertEquals("daylist", first.first().id)
        assertTrue(first.all { it.songIds.isNotEmpty() })
        assertTrue(MixGenerator(lib, data, emptyList(), morning.plusHours(2)).allFresh(first))

        // Mesmo período: nada muda.
        val again = MixGenerator(lib, data, emptyList(), morning.plusHours(2), Random(2)).refresh(first)
        assertEquals(first, again)

        // À tarde a daylist vira outra; as mixes do dia continuam.
        val afternoon = MixGenerator(lib, data, emptyList(), morning.withHour(14), Random(3)).refresh(first)
        val oldDaylist = first.first { it.id == "daylist" }
        val newDaylist = afternoon.first { it.id == "daylist" }
        assertNotEquals(oldDaylist.periodKey, newDaylist.periodKey)
        assertEquals(Daypart.AFTERNOON, newDaylist.daypart)
        assertEquals(first.first { it.id == "on-repeat" }, afternoon.first { it.id == "on-repeat" })
    }

    @Test
    fun recapIsBuiltOnlyForClosedMonths() {
        val entries = listOf(
            listen(songs[0], LocalDateTime.of(2026, 9, 10, 12, 0)),
            listen(songs[8], LocalDateTime.of(2026, 10, 3, 12, 0)),
            listen(songs[8], LocalDateTime.of(2026, 10, 4, 12, 0)),
            listen(songs[16], LocalDateTime.of(2026, 11, 2, 12, 0)),
        )
        assertEquals(listOf("2026-10"), pendingRecapMonths(entries, setOf("2026-09"), current = "2026-11"))
        val october = buildMonthlyRecap(entries, "2026-10")
        assertEquals(2, october.plays)
        assertEquals("Clara Nunes", october.topArtists.first().name)
        assertEquals(1, october.newArtists)
        assertEquals(entries[0].listenedMs, october.previousTotalMs)
        assertEquals(2, october.activeDays)
    }
}
