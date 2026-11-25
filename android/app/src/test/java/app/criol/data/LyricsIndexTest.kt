package app.criol.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LyricsIndexTest {
    private fun song(fileName: String, title: String, artist: String, album: String, folder: String) = Song(
        id = fileName, uri = "content://m/$fileName", rootUri = "r", title = title, artist = artist,
        albumArtist = artist, album = album, albumId = "$artist|$album", genre = "", year = 0, track = 1, disc = 1,
        durationMs = 1, folder = folder, fileName = fileName, size = 1, lastModified = 1, dateAdded = 1,
        mime = "audio/mpeg", bitrate = 0,
    )

    private val lyricsDir = "\u0001letras"

    @Test
    fun sameFolderSameName() {
        val idx = LyricsIndex()
        idx.add("01 - Alvorecer.lrc", "Music/Clara/Alvorecer", "lrc1")
        val s = song("01 - Alvorecer.mp3", "Alvorecer", "Clara Nunes", "Alvorecer", "Music/Clara/Alvorecer")
        assertEquals("lrc1", idx.find(s))
    }

    @Test
    fun lyricsFolderArtistTitleIgnoringCaseAccentsAndPunctuation() {
        val idx = LyricsIndex()
        idx.add("clara nunes - o mar serenou.lrc", lyricsDir, "a")
        idx.add("Cartola_-_As Rosas Nao Falam.txt", "$lyricsDir/sub", "b")
        val s1 = song("03 O Mar Serenou.flac", "O Mar Serenou", "Clara Nunes", "Claridade", "Music/x")
        val s2 = song("faixa2.mp3", "As Rosas Não Falam", "Cartola", "Verde", "Music/y")
        assertEquals("a", idx.find(s1))
        assertEquals("b", idx.find(s2))
    }

    @Test
    fun fileNameWithoutTrackNumberAndTitleWithExtras() {
        val idx = LyricsIndex()
        idx.add("Samba.lrc", lyricsDir, "a")
        idx.add("Cartola - Alvorada.lrc", lyricsDir, "b")
        assertEquals("a", idx.find(song("1-02. Samba.mp3", "Outro título", "X", "Y", "Music/z")))
        assertEquals("b", idx.find(song("x.mp3", "Alvorada (Ao Vivo)", "Cartola", "Y", "Music/z")))
    }

    @Test
    fun ambiguousTitleOnlyPrefersArtistFolderOrGivesUp() {
        val idx = LyricsIndex()
        idx.add("Intro.lrc", "$lyricsDir/Metallica", "m")
        idx.add("Intro.lrc", "$lyricsDir/Slayer", "s")
        assertEquals("s", idx.find(song("a.mp3", "Intro", "Slayer", "Reign", "Music/s")))
        // Sem pista de pasta e com dois candidatos: melhor não mostrar letra errada.
        assertNull(idx.find(song("b.mp3", "Intro", "Outro Artista", "Disco", "Music/o")))
    }
}
