package app.criol.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.nio.charset.Charset

data class LyricLine(val timeMs: Long, val text: String)

data class Lyrics(val synced: Boolean, val lines: List<LyricLine>, val source: String)

/** Carrega letras de um .lrc ao lado do arquivo ou embutidas nas tags. */
object LyricsLoader {
    private val cache = object : LinkedHashMap<String, Lyrics?>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Lyrics?>?) = size > 40
    }

    suspend fun load(context: Context, song: Song): Lyrics? = withContext(Dispatchers.IO) {
        val key = song.id + "|" + song.lyricsUri
        synchronized(cache) { if (cache.containsKey(key)) return@withContext cache[key] }
        val result = runCatching {
            song.lyricsUri?.let { uri ->
                context.contentResolver.openInputStream(Uri.parse(uri))?.use { readText(it.readBytes()) }
                    ?.let { parse(it, "Arquivo de letra") }
            } ?: readEmbedded(context, song)?.let { parse(it, "Tags do arquivo") }
        }.getOrNull()?.takeIf { it.lines.isNotEmpty() }
        synchronized(cache) { cache[key] = result }
        result
    }

    private fun readText(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return String(bytes, Charsets.UTF_16LE).drop(1)
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return String(bytes, Charsets.UTF_16BE).drop(1)
        val utf8 = String(bytes, Charsets.UTF_8)
        return if (utf8.contains('�')) String(bytes, Charset.forName("windows-1252")) else utf8
    }

    private val timeTag = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")
    private val wordTag = Regex("<\\d{1,3}:\\d{1,2}(?:[.:]\\d{1,3})?>")
    private val metaTag = Regex("^\\[(ar|ti|al|by|offset|length|re|ve|au|#):.*]$", RegexOption.IGNORE_CASE)
    private val offsetTag = Regex("^\\[offset:\\s*([+-]?\\d+)]", RegexOption.IGNORE_CASE)

    fun parse(text: String, source: String): Lyrics {
        var offset = 0L
        val synced = mutableListOf<LyricLine>()
        val plain = mutableListOf<String>()
        for (raw in text.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
            val line = raw.trim()
            offsetTag.find(line)?.let { offset = it.groupValues[1].toLongOrNull() ?: 0; return@let }
            val times = mutableListOf<Long>()
            var rest = line
            while (true) {
                val m = timeTag.find(rest) ?: break
                if (m.range.first != 0) break
                val min = m.groupValues[1].toLong()
                val sec = m.groupValues[2].toLong()
                val frac = m.groupValues[3]
                val ms = when (frac.length) {
                    0 -> 0L
                    1 -> frac.toLong() * 100
                    2 -> frac.toLong() * 10
                    else -> frac.take(3).toLong()
                }
                times += min * 60_000 + sec * 1000 + ms
                rest = rest.substring(m.range.last + 1).trimStart()
            }
            if (times.isNotEmpty()) {
                val clean = rest.replace(wordTag, "").trim()
                times.forEach { synced += LyricLine(it, clean) }
            } else if (!metaTag.matches(line)) {
                plain += line
            }
        }
        return if (synced.isNotEmpty()) {
            Lyrics(true, synced.map { it.copy(timeMs = (it.timeMs - offset).coerceAtLeast(0)) }.sortedBy { it.timeMs }, source)
        } else {
            val trimmed = plain.dropWhile { it.isEmpty() }.dropLastWhile { it.isEmpty() }
            Lyrics(false, trimmed.map { LyricLine(-1, it) }, source)
        }
    }

    // ------------------------------------------------------------------
    // Letras embutidas
    // ------------------------------------------------------------------

    private fun readEmbedded(context: Context, song: Song): String? {
        val uri = Uri.parse(song.uri)
        val ext = song.fileName.substringAfterLast('.', "").lowercase()
        val cr = context.contentResolver
        return when (ext) {
            "mp3", "aac", "wav", "aif", "aiff" -> cr.openInputStream(uri)?.use { readId3Lyrics(it) }
            "flac" -> cr.openInputStream(uri)?.use { readFlacLyrics(it) }
            "ogg", "oga", "opus" -> cr.openInputStream(uri)?.use { s ->
                findVorbisLyrics(s.readNBytesCompat(2 * 1024 * 1024))
            }
            "m4a", "m4b", "alac", "mp4" -> readMp4Lyrics(context, uri, song.size)
            else -> null
        }
    }

    private fun InputStream.readNBytesCompat(n: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        var total = 0
        while (total < n) {
            val r = read(buf, 0, minOf(buf.size, n - total))
            if (r <= 0) break
            out.write(buf, 0, r)
            total += r
        }
        return out.toByteArray()
    }

    internal fun readId3Lyrics(input: InputStream): String? {
        val header = input.readNBytesCompat(10)
        if (header.size < 10 || header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) return null
        val version = header[3].toInt()
        if (version < 3) return null
        val size = syncSafe(header, 6)
        if (size <= 0 || size > 32 * 1024 * 1024) return null
        val tag = input.readNBytesCompat(size)
        var pos = 0
        if (header[5].toInt() and 0x40 != 0) { // cabeçalho estendido
            val extSize = if (version == 4) syncSafe(tag, 0) else readInt(tag, 0) + 4
            pos += extSize
        }
        while (pos + 10 <= tag.size) {
            val id = String(tag, pos, 4, Charsets.ISO_8859_1)
            if (id[0] == '\u0000') break
            val frameSize = if (version == 4) syncSafe(tag, pos + 4) else readInt(tag, pos + 4)
            if (frameSize <= 0 || pos + 10 + frameSize > tag.size) break
            val start = pos + 10
            if (id == "USLT" && frameSize > 4) {
                val enc = tag[start].toInt()
                var p = start + 4 // encoding + idioma
                p = skipTerminated(tag, p, start + frameSize, enc)
                return decode(tag, p, start + frameSize, enc).trim().takeIf { it.isNotEmpty() }
            }
            if (id == "TXXX" && frameSize > 1) {
                val enc = tag[start].toInt()
                val descEnd = skipTerminated(tag, start + 1, start + frameSize, enc)
                val desc = decode(tag, start + 1, descEnd, enc).trim().uppercase()
                if (desc == "LYRICS" || desc == "UNSYNCEDLYRICS") {
                    return decode(tag, descEnd, start + frameSize, enc).trim().takeIf { it.isNotEmpty() }
                }
            }
            pos = start + frameSize
        }
        return null
    }

    private fun syncSafe(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0x7F shl 21) or (b[o + 1].toInt() and 0x7F shl 14) or
            (b[o + 2].toInt() and 0x7F shl 7) or (b[o + 3].toInt() and 0x7F)

    private fun readInt(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF shl 24) or (b[o + 1].toInt() and 0xFF shl 16) or
            (b[o + 2].toInt() and 0xFF shl 8) or (b[o + 3].toInt() and 0xFF)

    private fun readIntLE(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or (b[o + 1].toInt() and 0xFF shl 8) or
            (b[o + 2].toInt() and 0xFF shl 16) or (b[o + 3].toInt() and 0xFF shl 24)

    /** Pula uma string terminada em zero e devolve a posição seguinte. */
    private fun skipTerminated(b: ByteArray, from: Int, end: Int, enc: Int): Int {
        var p = from
        if (enc == 1 || enc == 2) {
            while (p + 1 < end) {
                if (b[p].toInt() == 0 && b[p + 1].toInt() == 0) return p + 2
                p += 2
            }
            return end
        }
        while (p < end) {
            if (b[p].toInt() == 0) return p + 1
            p++
        }
        return end
    }

    private fun decode(b: ByteArray, from: Int, end: Int, enc: Int): String {
        if (from >= end) return ""
        val cs = when (enc) {
            0 -> Charsets.ISO_8859_1
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            else -> Charsets.UTF_8
        }
        return String(b, from, end - from, cs).trimEnd('\u0000')
    }

    internal fun readFlacLyrics(input: InputStream): String? {
        val magic = input.readNBytesCompat(4)
        if (String(magic, Charsets.ISO_8859_1) != "fLaC") return null
        while (true) {
            val h = input.readNBytesCompat(4)
            if (h.size < 4) return null
            val last = h[0].toInt() and 0x80 != 0
            val type = h[0].toInt() and 0x7F
            val len = (h[1].toInt() and 0xFF shl 16) or (h[2].toInt() and 0xFF shl 8) or (h[3].toInt() and 0xFF)
            if (type == 4) {
                val block = input.readNBytesCompat(len)
                return parseVorbisComments(block, 0)
            } else {
                var toSkip = len.toLong()
                while (toSkip > 0) {
                    val s = input.skip(toSkip)
                    if (s <= 0) return null
                    toSkip -= s
                }
            }
            if (last) return null
        }
    }

    private fun parseVorbisComments(b: ByteArray, start: Int): String? {
        var p = start
        if (p + 4 > b.size) return null
        val vendorLen = readIntLE(b, p)
        p += 4 + vendorLen
        if (p + 4 > b.size || vendorLen < 0) return null
        val count = readIntLE(b, p)
        p += 4
        repeat(count) {
            if (p + 4 > b.size) return null
            val len = readIntLE(b, p)
            p += 4
            if (len < 0 || p + len > b.size) return null
            val entry = String(b, p, len, Charsets.UTF_8)
            p += len
            val key = entry.substringBefore('=').uppercase()
            if (key == "LYRICS" || key == "UNSYNCEDLYRICS" || key == "SYNCEDLYRICS") {
                return entry.substringAfter('=').trim().takeIf { it.isNotEmpty() }
            }
        }
        return null
    }

    /** Ogg/Opus: procura o pacote de comentários dentro dos primeiros bytes. */
    internal fun findVorbisLyrics(bytes: ByteArray): String? {
        val markers = listOf("OpusTags".toByteArray(), byteArrayOf(3) + "vorbis".toByteArray())
        for (marker in markers) {
            val idx = indexOf(bytes, marker, 0)
            if (idx >= 0) return parseVorbisComments(bytes, idx + marker.size)
        }
        return null
    }

    private fun readMp4Lyrics(context: Context, uri: Uri, size: Long): String? {
        val cr = context.contentResolver
        val atom = byteArrayOf(0xA9.toByte(), 'l'.code.toByte(), 'y'.code.toByte(), 'r'.code.toByte())
        val chunk = 3 * 1024 * 1024
        val head = cr.openInputStream(uri)?.use { it.readNBytesCompat(chunk) } ?: return null
        extractMp4Atom(head, atom)?.let { return it }
        if (size > chunk) {
            val tail = cr.openInputStream(uri)?.use { s ->
                var toSkip = size - chunk
                while (toSkip > 0) {
                    val r = s.skip(toSkip)
                    if (r <= 0) break
                    toSkip -= r
                }
                s.readNBytesCompat(chunk)
            } ?: return null
            return extractMp4Atom(tail, atom)
        }
        return null
    }

    private fun extractMp4Atom(b: ByteArray, atom: ByteArray): String? {
        var from = 0
        while (true) {
            val idx = indexOf(b, atom, from)
            if (idx < 4) return null
            val dataPos = idx + 4
            if (dataPos + 16 <= b.size && String(b, dataPos + 4, 4, Charsets.ISO_8859_1) == "data") {
                val dataSize = readInt(b, dataPos)
                val start = dataPos + 16
                val end = (dataPos + dataSize).coerceAtMost(b.size)
                if (end > start) return String(b, start, end - start, Charsets.UTF_8).trim()
            }
            from = idx + 4
        }
    }

    private fun indexOf(b: ByteArray, pattern: ByteArray, from: Int): Int {
        outer@ for (i in from..b.size - pattern.size) {
            for (j in pattern.indices) if (b[i + j] != pattern[j]) continue@outer
            return i
        }
        return -1
    }
}
