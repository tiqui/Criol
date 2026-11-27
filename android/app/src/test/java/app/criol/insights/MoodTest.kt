package app.criol.insights

import app.criol.data.Library
import app.criol.data.Song
import app.criol.data.UserData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.random.Random

class MoodTest {
    private var n = 0

    private fun song(artist: String, genre: String): Song {
        n++
        return Song(
            id = "s$n", uri = "content://x/$n", rootUri = "r", title = "Faixa $n", artist = artist,
            albumArtist = artist, album = "$artist LP", albumId = "$artist|LP", genre = genre, year = 2000,
            track = n, disc = 1, durationMs = 200_000, folder = "Music/$artist", fileName = "$n.mp3",
            size = 1, lastModified = 1, dateAdded = 1, mime = "audio/mpeg", bitrate = 320_000,
        )
    }

    private val genres = mapOf(
        "Jazz" to listOf("Miles Davis", "Chet Baker", "Bill Evans", "Nina Simone"),
        "MPB" to listOf("Gal Costa", "Caetano", "Milton", "Elis"),
        "Trap" to listOf("Travis Scott", "Future", "Matuê", "Teto"),
        "Heavy Metal" to listOf("Metallica", "Slayer", "Iron Maiden", "Sepultura"),
    )
    private val songs = genres.flatMap { (genre, artists) -> artists.flatMap { a -> List(6) { song(a, genre) } } }
    private val lib = Library.build(songs, emptyMap(), File("art"))
    private fun of(genre: String) = songs.filter { it.genre == genre }

    private val today = LocalDateTime.of(2026, 11, 27, 0, 0)
    private fun ms(t: LocalDateTime) = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Escutas de [genre] em [hours] nos últimos [days] dias ([listened] < 30 s = pulada). */
    private fun history(genre: String, hours: List<Int>, days: Int = 14, listened: Long = 180_000) = buildList {
        for (d in 1..days) for (h in hours) for (s in of(genre).shuffled(Random(d * 31 + h)).take(3)) {
            val at = today.minusDays(d.toLong()).withHour(h).withMinute(n++ % 50)
            add(ListenEntry.from(s, listened, ms(at), id = "e${ms(at)}-${s.id}"))
        }
    }

    private fun daylistAt(hour: Int, entries: List<ListenEntry>, data: UserData = UserData()): Pair<Mix, Map<String, Int>> {
        val gen = MixGenerator(lib, data, entries, today.withHour(hour).withMinute(15), Random(hour))
        val mix = gen.daypartMix(Daypart.ofHour(hour), daylist = true)
        val count = mix.songIds.map { lib.songsById.getValue(it).genre }.groupingBy { it }.eachCount()
        return mix to count
    }

    @Test
    fun semHistoricoNaoTocaMetalDeManha() {
        val (mix, count) = daylistAt(7, emptyList())
        assertEquals(0, count["Heavy Metal"] ?: 0)
        assertTrue(mix.songIds.size >= 20)
        assertNotNull(mix.moodMatch)
        assertTrue(mix.tags.none { it == "Heavy Metal" })
    }

    @Test
    fun quemOuveMetalTodaManhaRecebeMetalDeManha() {
        val (mix, count) = daylistAt(7, history("Heavy Metal", listOf(6, 7, 8)))
        assertEquals("Heavy Metal", mix.tags.first())
        assertTrue("$count", (count["Heavy Metal"] ?: 0) > mix.songIds.size / 2)
        assertTrue(mix.moodMatch!! >= 70)
    }

    @Test
    fun jazzCedoETrapDepoisDasNove() {
        val entries = history("Jazz", listOf(5, 6, 7)) + history("Trap", listOf(9, 10, 11))
        val now = ms(today)
        assertEquals("Jazz", Mood.profile(entries, listOf(6), now).topGenres(1).first())
        assertEquals("Trap", Mood.profile(entries, listOf(10), now).topGenres(1).first())

        val (early, earlyCount) = daylistAt(5, entries)
        val (late, lateCount) = daylistAt(9, entries)
        assertEquals("Jazz", early.tags.first())
        assertEquals("Trap", late.tags.first())
        assertTrue("$earlyCount", (earlyCount["Jazz"] ?: 0) > (earlyCount["Trap"] ?: 0))
        assertTrue("$lateCount", (lateCount["Trap"] ?: 0) > (lateCount["Jazz"] ?: 0))
    }

    @Test
    fun puladasDeManhaSaemDaManhaMasFicamANoite() {
        val entries = history("Heavy Metal", listOf(21, 22)) +
            history("Heavy Metal", listOf(8, 9), listened = 15_000) +
            history("MPB", listOf(8, 9))
        val (_, morning) = daylistAt(9, entries)
        val (_, night) = daylistAt(21, entries)
        assertEquals("$morning", 0, morning["Heavy Metal"] ?: 0)
        assertTrue("$night", (night["Heavy Metal"] ?: 0) > 10)
    }

    @Test
    fun energiaPorGenero() {
        assertTrue(Mood.genreEnergy(listOf("Heavy Metal"))!! >= 0.9)
        assertTrue(Mood.genreEnergy(listOf("Bossa Nova"))!! <= 0.3)
        assertEquals(0.75, Mood.genreEnergy(listOf("Trap"))!!, 0.001) // não confunde com "rap"
        assertEquals(null, Mood.genreEnergy(listOf("Desconhecido")))
        assertTrue(Mood.energyFit(listOf("Jazz"), listOf(7)) > Mood.energyFit(listOf("Metal"), listOf(7)))
    }
}
