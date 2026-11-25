package app.criol.insights

import android.content.Context
import app.criol.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Uma escuta registrada no histórico do Criol.
 *
 * Guarda uma cópia dos dados da música (título, artista, gêneros...) para que o perfil e os
 * resumos continuem fazendo sentido mesmo se o arquivo sair da biblioteca.
 */
@Serializable
data class ListenEntry(
    val id: String,
    val songId: String,
    val title: String,
    val artist: String,
    val artists: List<String> = emptyList(),
    val album: String = "",
    val albumId: String = "",
    val genres: List<String> = emptyList(),
    val durationMs: Long = 0,
    val listenedMs: Long,
    val playedAt: Long,
    /** Escuta longa o bastante para contar como reprodução (30 s ou metade da faixa). */
    val counted: Boolean,
) {
    companion object {
        /** Escutas mais curtas que isso são puladas e nem entram no histórico. */
        const val MIN_STORED_MS = 10_000L
        private const val PLAY_THRESHOLD_MS = 30_000L

        fun from(song: Song, listenedMs: Long, playedAt: Long, id: String = UUID.randomUUID().toString()): ListenEntry {
            val dur = song.durationMs
            val threshold = if (dur > 0) minOf(PLAY_THRESHOLD_MS, dur / 2) else PLAY_THRESHOLD_MS
            return ListenEntry(
                id = id,
                songId = song.id,
                title = song.title,
                artist = song.artists.firstOrNull() ?: song.albumArtist,
                artists = song.artists,
                album = song.album,
                albumId = song.albumId,
                genres = app.criol.data.Library.splitGenres(song.genre),
                durationMs = dur,
                listenedMs = listenedMs,
                playedAt = playedAt,
                counted = listenedMs >= threshold,
            )
        }
    }
}

/**
 * Histórico de escuta, separado por mês (`listening/2026-11.json`).
 *
 * Tudo é carregado na memória ao abrir: mesmo quem ouve muito gera só algumas dezenas de
 * milhares de registros pequenos por ano. A escuta em andamento é salva em `pending.json`
 * para não se perder se o sistema matar o app no meio da música.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ListeningStore(context: Context, private val scope: CoroutineScope) {
    private val dir = File(context.filesDir, "listening")
    private val pendingFile = File(dir, "pending.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    /** Todas as gravações passam por aqui, uma de cada vez e na ordem. */
    private val io = Dispatchers.IO.limitedParallelism(1)

    private val _entries = MutableStateFlow<List<ListenEntry>>(emptyList())
    /** Ordenadas da mais antiga para a mais recente. */
    val entries: StateFlow<List<ListenEntry>> = _entries.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    fun init() {
        scope.launch(io) {
            dir.mkdirs()
            val all = (dir.listFiles { f -> monthFile.matches(f.name) } ?: emptyArray())
                .flatMap { readMonth(it) }
                .toMutableList()
            val pending = runCatching {
                if (pendingFile.exists()) json.decodeFromString<ListenEntry>(pendingFile.readText()) else null
            }.getOrNull()
            if (pending != null && all.none { it.id == pending.id }) all += pending
            _entries.value = all.sortedBy { it.playedAt }
            if (pending != null) {
                writeMonth(monthKey(pending.playedAt))
                pendingFile.delete()
            }
            _loaded.value = true
        }
    }

    fun add(entry: ListenEntry) {
        scope.launch(io) {
            _entries.value = _entries.value + entry
            writeMonth(monthKey(entry.playedAt))
            pendingFile.delete()
        }
    }

    /** Escuta em andamento (ou null quando não há nada para guardar). */
    fun setPending(entry: ListenEntry?) {
        scope.launch(io) {
            runCatching {
                if (entry == null) pendingFile.delete()
                else {
                    dir.mkdirs()
                    pendingFile.writeText(json.encodeToString(entry))
                }
            }
        }
    }

    suspend fun clear() = withContext(io) {
        dir.listFiles()?.forEach { it.delete() }
        _entries.value = emptyList()
    }

    fun exportJson(): String = json.encodeToString(_entries.value)

    /** Junta um histórico exportado ao atual. Retorna quantos registros eram novos. */
    suspend fun importJson(raw: String): Int = withContext(io) {
        val parsed = json.decodeFromString<List<ListenEntry>>(raw)
        val known = _entries.value.mapTo(HashSet()) { it.id }
        val fresh = parsed.filter { it.id.isNotBlank() && it.songId.isNotBlank() && it.id !in known }
        if (fresh.isEmpty()) return@withContext 0
        _entries.value = (_entries.value + fresh).sortedBy { it.playedAt }
        fresh.map { monthKey(it.playedAt) }.distinct().forEach { writeMonth(it) }
        fresh.size
    }

    private fun readMonth(file: File): List<ListenEntry> = runCatching {
        json.decodeFromString<List<ListenEntry>>(file.readText())
    }.getOrDefault(emptyList())

    private fun writeMonth(month: String) {
        runCatching {
            dir.mkdirs()
            val items = _entries.value.filter { monthKey(it.playedAt) == month }
            val file = File(dir, "$month.json")
            val tmp = File(dir, "$month.json.tmp")
            tmp.writeText(json.encodeToString(items))
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        }
    }

    private companion object {
        val monthFile = Regex("""\d{4}-\d{2}\.json""")
    }
}
