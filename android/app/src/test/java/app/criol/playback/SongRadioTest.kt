package app.criol.playback

import app.criol.data.Library
import app.criol.data.Song
import app.criol.data.UserData
import app.criol.insights.ListenEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class SongRadioTest {
    private var n = 0

    private fun song(artist: String, album: String, genre: String, year: Int = 2000, title: String? = null): Song {
        n++
        return Song(
            id = "s$n", uri = "content://x/$n", rootUri = "r", title = title ?: "Faixa $n", artist = artist,
            albumArtist = artist, album = album, albumId = "$artist|$album", genre = genre, year = year,
            track = n, disc = 1, durationMs = 180_000, folder = "Music/$artist/$album", fileName = "$n.mp3",
            size = 1, lastModified = 1, dateAdded = 1, mime = "audio/mpeg", bitrate = 320_000,
        )
    }

    private val songs = buildList {
        repeat(6) { add(song("Cartola", "Verde", "Samba", 1976)) }
        repeat(6) { add(song("Clara Nunes", "Alvorecer", "Samba", 1974)) }
        repeat(6) { add(song("Paulinho da Viola", "Nervos", "Samba; MPB", 1973)) }
        repeat(6) { add(song("Elis Regina", "Falso", "MPB", 1976)) }
        repeat(6) { add(song("Metallica", "Black", "Metal", 1991)) }
        repeat(6) { add(song("Slayer", "Reign", "Metal", 1986)) }
    }
    private val lib = Library.build(songs, emptyMap(), File("art"))

    @Test
    fun radioFollowsTheSeedAndKeepsVariety() {
        val seed = songs.first()
        val radio = SongRadio(lib, UserData(), emptyList(), Random(3)).build(seed, 20)

        assertEquals(20, radio.size)
        assertFalse(seed in radio)
        assertEquals(radio.size, radio.map { it.id }.toSet().size)
        // Com tudo o que é parecido à disposição, as primeiras são do mesmo universo (samba/MPB).
        assertTrue(radio.take(12).all { "Samba" in it.genre || "MPB" in it.genre })
        // No máximo 2 por álbum enquanto ainda há opções parecidas.
        assertTrue(radio.take(6).groupingBy { it.albumId }.eachCount().values.all { it <= 2 })
        // Nunca o mesmo artista duas vezes seguidas quando dá para evitar.
        assertTrue(radio.take(12).zipWithNext().none { (a, b) -> a.artist == b.artist })
        assertTrue(radio.first().artist != seed.artist)
    }

    @Test
    fun heardTogetherPullsInOtherGenres() {
        val seed = songs.first()
        val metal = songs.first { it.artist == "Metallica" }
        // Sempre que você ouve essa música de samba, ouve Metallica logo depois.
        val history = (0 until 6).flatMap { d ->
            val t = 1_000_000_000L + d * 86_400_000L
            listOf(
                ListenEntry.from(seed, 180_000, t, "a$d"),
                ListenEntry.from(metal, 180_000, t + 4 * 60_000, "b$d"),
            )
        }
        val hits = (1..20).count { r ->
            metal in SongRadio(lib, UserData(), history, Random(r)).build(seed, 15)
        }
        val baseline = (1..20).count { r ->
            metal in SongRadio(lib, UserData(), emptyList(), Random(r)).build(seed, 15)
        }
        assertTrue("ouvida junto: $hits vs sem histórico: $baseline", hits > baseline)
        assertTrue(hits >= 15)
    }

    @Test
    fun refillSkipsQueueAndOtherVersionsOfTheSameSong() {
        val a = song("Tom Jobim", "Wave", "Bossa", 1967, title = "Wave")
        val live = song("Tom Jobim", "Ao Vivo", "Bossa", 1990, title = "Wave (Ao Vivo)")
        val remaster = song("Tom Jobim", "Remaster", "Bossa", 2010, title = "Wave - Remaster 2010")
        val other = (1..10).map { song("João Gilberto", "Álbum $it", "Bossa", 1960) }
        val small = Library.build(listOf(a, live, remaster) + other, emptyMap(), File("art"))

        val queue = setOf(a.id, other[0].id, other[1].id)
        val more = SongRadio(small, UserData(), emptyList(), Random(9)).build(a, 25, exclude = queue)

        assertTrue(more.none { it.id in queue })
        assertFalse(live in more)
        assertFalse(remaster in more)
        // Biblioteca pequena: entrega o que existe, sem travar nem repetir.
        assertEquals(8, more.size)
    }
}
