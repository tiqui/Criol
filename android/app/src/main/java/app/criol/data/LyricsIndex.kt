package app.criol.data

/**
 * Arquivos de letra encontrados nas pastas de música e nas pastas de letras, com busca
 * tolerante a nomes diferentes. Para uma música, tenta nesta ordem:
 *  1. mesmo nome do arquivo de áudio ("01 - Samba.mp3" → "01 - Samba.lrc");
 *  2. nome sem o número da faixa ("Samba.lrc");
 *  3. "Artista - Título", "Título - Artista", "Artista - Álbum - Título";
 *  4. só o título (aceito apenas se não houver dúvida).
 * Maiúsculas, acentos e pontuação não importam. Quando há mais de um arquivo com o mesmo
 * nome, vence o da mesma pasta da música e depois o que estiver numa pasta com o nome do
 * artista ou do álbum.
 */
class LyricsIndex {
    private class Entry(val uri: String, val dir: String, val dirKey: String)

    private val byKey = HashMap<String, MutableList<Entry>>()
    private val seen = HashSet<String>()

    val size: Int get() = seen.size

    /** [dir] é a pasta do arquivo (mesmo formato do [Song.folder] para as pastas de música). */
    @Synchronized
    fun add(fileName: String, dir: String, uri: String) {
        if (!seen.add(uri)) return
        val base = fileName.substringBeforeLast('.')
        val entry = Entry(uri, dir, normalize(dir))
        for (k in linkedSetOf(normalize(base), normalize(stripTrack(base)))) {
            if (k.isNotEmpty()) byKey.getOrPut(k) { mutableListOf() } += entry
        }
    }

    @Synchronized
    fun find(song: Song): String? {
        val base = song.fileName.substringBeforeLast('.')
        val title = song.title
        val cleanTitle = stripExtras(title)
        val artists = (song.artists + song.albumArtists + song.artist).filter { it.isNotBlank() }.distinct()
        val artistKeys = artists.map(::normalize)
        val albumKey = normalize(song.album)

        fun best(key: String, weak: Boolean): String? {
            if (key.isEmpty()) return null
            val list = byKey[normalize(key)] ?: return null
            val scored = list.map { e ->
                var score = 0
                if (e.dir == song.folder) score += 100
                if (albumKey.length >= 3 && e.dirKey.contains(albumKey)) score += 10
                if (artistKeys.any { it.length >= 3 && e.dirKey.contains(it) }) score += 10
                e to score
            }
            val top = scored.maxBy { it.second }
            // Só o título é um palpite fraco: exige ser o único ou estar na pasta certa.
            if (weak && list.size > 1 && top.second == 0) return null
            return top.first.uri
        }

        val strong = buildList {
            add(base)
            add(stripTrack(base))
            for (a in artists) {
                add("$a - $title")
                add("$title - $a")
                add("$a - ${song.album} - $title")
                if (cleanTitle != title) {
                    add("$a - $cleanTitle")
                    add("$cleanTitle - $a")
                }
            }
        }
        for (k in strong) best(k, weak = false)?.let { return it }
        for (k in linkedSetOf(title, cleanTitle)) best(k, weak = true)?.let { return it }
        return null
    }

    companion object {
        val EXTENSIONS = setOf("lrc", "txt")

        private val trackPrefix = Regex("^\\s*(\\d{1,2}[-.])?\\d{1,3}\\s*([-._)]\\s*)?(?=\\D)")
        private val extras = Regex("\\s*[(\\[][^)\\]]*[)\\]]")
        private val nonWord = Regex("[^\\p{L}\\p{N}]+")

        /** "01 - Samba" → "Samba"; "1-02. Samba" → "Samba". */
        fun stripTrack(name: String): String = name.replaceFirst(trackPrefix, "").trim().ifEmpty { name }

        /** "Samba (feat. X) [Ao Vivo]" → "Samba". */
        fun stripExtras(name: String): String = name.replace(extras, "").trim().ifEmpty { name }

        fun normalize(s: String): String = s.normalizeForSearch().replace(nonWord, " ").trim()
    }
}
