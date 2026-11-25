package app.criol.playback

import app.criol.data.Library
import app.criol.data.Song
import app.criol.data.UserData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class AutoDjTest {
    private var n = 0

    private fun song(artist: String, album: String, genre: String, year: Int = 2000): Song {
        n++
        return Song(
            id = "s$n", uri = "content://x/$n", rootUri = "r", title = "Faixa $n", artist = artist,
            albumArtist = artist, album = album, albumId = "$artist|$album", genre = genre, year = year,
            track = n, disc = 1, durationMs = 180_000, folder = "Music/$artist/$album", fileName = "$n.mp3",
            size = 1, lastModified = 1, dateAdded = 1, mime = "audio/mpeg", bitrate = 320_000,
        )
    }

    private val songs = buildList {
        repeat(4) { add(song("Cartola", "Verde", "Samba", 1976)) }
        repeat(4) { add(song("Clara Nunes", "Alvorecer", "Samba", 1974)) }
        repeat(4) { add(song("Paulinho da Viola", "Nervos", "Samba; MPB", 1973)) }
        repeat(4) { add(song("Metallica", "Black", "Metal", 1991)) }
        repeat(4) { add(song("Slayer", "Reign", "Metal", 1986)) }
    }
    private val lib = Library.build(songs, emptyMap(), File("art"))

    @Test
    fun similarSongsPreferSameGenreAndAvoidQueue() {
        val current = songs.first()
        val queue = setOf(current.id, songs[1].id)
        val opt = AutoDj.Options(count = 5, onlySimilar = true)
        repeat(20) { seed ->
            val picked = AutoDj(lib, UserData(), Random(seed)).pickSongs(current, queue, opt)
            assertEquals(5, picked.size)
            assertTrue(picked.all { it.genre.contains("Samba") })
            assertTrue(picked.none { it.id in queue })
            assertEquals(picked.size, picked.map { it.id }.distinct().size)
            // No máximo 2 por álbum para variar.
            assertTrue(picked.groupBy { it.albumId }.values.all { it.size <= 2 })
        }
    }

    @Test
    fun fillsWithRandomWhenNotOnlySimilar() {
        val current = songs.first()
        val queue = songs.filter { it.genre.contains("Samba") }.map { it.id }.toSet()
        val picked = AutoDj(lib, UserData(), Random(1)).pickSongs(current, queue, AutoDj.Options(count = 3))
        assertEquals(3, picked.size)
        assertTrue(picked.all { it.genre == "Metal" })
    }

    @Test
    fun albumsModePicksSimilarAlbumsNotInQueue() {
        val current = songs.first()
        val opt = AutoDj.Options(mode = AutoDj.MODE_ALBUMS, count = 2, onlySimilar = true)
        val picked = AutoDj(lib, UserData(), Random(3)).pickAlbums(current, setOf(current.albumId), opt)
        assertEquals(2, picked.size)
        assertTrue(picked.all { a -> a.genres.contains("Samba") })
        assertFalse(picked.any { it.id == current.albumId })
    }

    @Test
    fun largeCountsUpTo150() {
        // Biblioteca grande: 60 álbuns de 5 músicas, metade samba e metade metal.
        val big = (1..60).flatMap { a ->
            val genre = if (a % 2 == 0) "Samba" else "Metal"
            List(5) { song("Artista $a", "Álbum $a", genre, 1970 + a) }
        }
        val bigLib = Library.build(big, emptyMap(), File("art"))
        val current = big.first()

        val picked = AutoDj(bigLib, UserData(), Random(5)).pickSongs(current, setOf(current.id), AutoDj.Options(count = 150))
        assertEquals(150, picked.size)
        assertEquals(150, picked.map { it.id }.distinct().size)
        assertTrue(picked.none { it.id == current.id })

        // Acima do limite fica em 150; álbuns ficam em 20.
        val capped = AutoDj(bigLib, UserData(), Random(6)).pickSongs(current, emptySet(), AutoDj.Options(count = 500))
        assertEquals(AutoDj.MAX_SONGS, capped.size)
        val albums = AutoDj(bigLib, UserData(), Random(7))
            .pickAlbums(current, emptySet(), AutoDj.Options(mode = AutoDj.MODE_ALBUMS, count = 150))
        assertEquals(AutoDj.MAX_ALBUMS, albums.size)
    }

    @Test
    fun randomStrategyAndEmptyLibrary() {
        val current = songs.first()
        val picked = AutoDj(lib, UserData(), Random(7))
            .pickSongs(current, emptySet(), AutoDj.Options(strategy = AutoDj.STRATEGY_RANDOM, count = 4))
        assertEquals(4, picked.size)
        val all = songs.map { it.id }.toSet()
        assertTrue(AutoDj(lib, UserData()).pickSongs(current, all, AutoDj.Options()).isEmpty())
    }
}
