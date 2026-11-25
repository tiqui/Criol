package app.criol.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class LyricsAndTagsTest {

    @Test
    fun parsesSyncedLrcWithMultipleTimestampsAndOffset() {
        val lrc = """
            [ar:Artista]
            [ti:Título]
            [offset:+500]
            [00:12.50]Primeira linha
            [00:05.00][01:00.123]Refrão <00:05.50>com palavras
            [00:20]
        """.trimIndent()
        val l = LyricsLoader.parse(lrc, "t")
        assertTrue(l.synced)
        assertEquals(listOf(4500L, 12000L, 19500L, 59623L), l.lines.map { it.timeMs })
        assertEquals("Refrão com palavras", l.lines[0].text)
        assertEquals("Primeira linha", l.lines[1].text)
        assertEquals("", l.lines[2].text)
    }

    @Test
    fun parsesPlainLyrics() {
        val l = LyricsLoader.parse("\n\nLinha 1\r\nLinha 2\n\n", "t")
        assertFalse(l.synced)
        assertEquals(listOf("Linha 1", "Linha 2"), l.lines.map { it.text })
    }

    private fun syncSafe(n: Int) = byteArrayOf(
        (n shr 21 and 0x7F).toByte(), (n shr 14 and 0x7F).toByte(),
        (n shr 7 and 0x7F).toByte(), (n and 0x7F).toByte(),
    )

    private fun id3Frame(id: String, body: ByteArray, v4: Boolean): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(id.toByteArray(Charsets.ISO_8859_1))
        if (v4) out.write(syncSafe(body.size)) else {
            val n = body.size
            out.write(byteArrayOf((n shr 24).toByte(), (n shr 16).toByte(), (n shr 8).toByte(), n.toByte()))
        }
        out.write(byteArrayOf(0, 0))
        out.write(body)
        return out.toByteArray()
    }

    private fun id3Tag(frames: List<ByteArray>, v4: Boolean): ByteArray {
        val body = frames.fold(ByteArray(0)) { a, b -> a + b } + ByteArray(32) // padding
        return "ID3".toByteArray() + byteArrayOf(if (v4) 4 else 3, 0, 0) + syncSafe(body.size) + body + ByteArray(100)
    }

    @Test
    fun readsUsltUtf8FromId3v24() {
        val text = "[00:01.00]Olá, coração"
        val body = byteArrayOf(3) + "por".toByteArray() + byteArrayOf(0) + text.toByteArray(Charsets.UTF_8)
        val tag = id3Tag(listOf(id3Frame("TIT2", byteArrayOf(3) + "x".toByteArray(), true), id3Frame("USLT", body, true)), v4 = true)
        assertEquals(text, LyricsLoader.readId3Lyrics(ByteArrayInputStream(tag)))
    }

    @Test
    fun readsUsltUtf16FromId3v23() {
        val text = "Canção sem tempo"
        val desc = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0, 0) // BOM + descrição vazia
        val body = byteArrayOf(1) + "por".toByteArray() + desc +
            byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + text.toByteArray(Charsets.UTF_16LE)
        val tag = id3Tag(listOf(id3Frame("USLT", body, false)), v4 = false)
        assertEquals(text, LyricsLoader.readId3Lyrics(ByteArrayInputStream(tag)))
    }

    @Test
    fun returnsNullWithoutId3() {
        assertNull(LyricsLoader.readId3Lyrics(ByteArrayInputStream(ByteArray(64))))
    }

    private fun le(n: Int) = byteArrayOf(n.toByte(), (n shr 8).toByte(), (n shr 16).toByte(), (n shr 24).toByte())

    private fun vorbisComments(vararg entries: String): ByteArray {
        var b = le(3) + "abc".toByteArray() + le(entries.size)
        for (e in entries) {
            val bytes = e.toByteArray(Charsets.UTF_8)
            b += le(bytes.size) + bytes
        }
        return b
    }

    @Test
    fun readsFlacVorbisLyrics() {
        val streamInfo = byteArrayOf(0, 0, 0, 34) + ByteArray(34)
        val comments = vorbisComments("TITLE=Faixa", "LYRICS=[00:02.00]Letra FLAC")
        val block = byteArrayOf((0x80 or 4).toByte(), (comments.size shr 16).toByte(), (comments.size shr 8).toByte(), comments.size.toByte()) + comments
        val file = "fLaC".toByteArray() + streamInfo + block
        assertEquals("[00:02.00]Letra FLAC", LyricsLoader.readFlacLyrics(ByteArrayInputStream(file)))
    }

    @Test
    fun readsOpusTags() {
        val data = ByteArray(40) + "OpusTags".toByteArray() + vorbisComments("UNSYNCEDLYRICS=Letra Opus") + ByteArray(10)
        assertEquals("Letra Opus", LyricsLoader.findVorbisLyrics(data))
    }

    @Test
    fun parsesYearsTrackNumbersAndGenres() {
        assertEquals(1999, LibraryScanner.parseYear("1999-05-01"))
        assertEquals(2004, LibraryScanner.parseYear("20040101T000000.000Z"))
        assertNull(LibraryScanner.parseYear("abc"))
        assertEquals(3, LibraryScanner.parseNumber("3/12"))
        assertEquals(0, LibraryScanner.parseNumber(null))
        assertEquals("Rock", LibraryScanner.normalizeGenre("(17)"))
        assertEquals("Hip-Hop", LibraryScanner.normalizeGenre("7"))
        assertEquals("Samba", LibraryScanner.normalizeGenre("Samba"))
    }

    @Test
    fun splitsArtistsAndNormalizes() {
        assertEquals(listOf("A", "B"), splitArtists("A; B"))
        assertEquals(listOf("AC/DC"), splitArtists("AC/DC"))
        assertEquals(listOf("Tom Jobim", "Elis Regina"), splitArtists("Tom Jobim / Elis Regina"))
        assertEquals("joao", "João".normalizeForSearch())
    }
}
