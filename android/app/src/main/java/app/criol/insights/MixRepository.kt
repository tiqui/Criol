package app.criol.insights

import android.content.Context
import app.criol.data.LibraryRepository
import app.criol.data.UserDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Mixes "Feito para você", salvas em `mixes.json`. Só refaz as que venceram: a daylist a cada
 * período do dia, as do período e o No Repeat por dia, as outras por semana.
 */
class MixRepository(
    context: Context,
    private val scope: CoroutineScope,
    private val library: LibraryRepository,
    private val userData: UserDataStore,
    private val listening: ListeningStore,
) {
    private val file = File(context.filesDir, "mixes.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val mutex = Mutex()
    private var readFromDisk = false

    private val _mixes = MutableStateFlow<List<Mix>>(emptyList())
    val mixes: StateFlow<List<Mix>> = _mixes.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** Verifica (barato) e refaz o que venceu. [force] refaz tudo. */
    fun ensureFresh(force: Boolean = false) {
        scope.launch(Dispatchers.Default) {
            mutex.withLock {
                prepare()
                val lib = library.current
                if (lib.isEmpty) return@withLock
                val gen = MixGenerator(lib, userData.value, listening.entries.value)
                if (!force && gen.allFresh(_mixes.value)) return@withLock
                _busy.value = true
                try {
                    publish(gen.refresh(_mixes.value, force))
                } finally {
                    _busy.value = false
                }
            }
        }
    }

    fun regenerate(id: String) {
        scope.launch(Dispatchers.Default) {
            mutex.withLock {
                prepare()
                val current = _mixes.value.firstOrNull { it.id == id } ?: return@withLock
                _busy.value = true
                try {
                    val next = MixGenerator(library.current, userData.value, listening.entries.value).rebuild(current)
                    publish(_mixes.value.map { if (it.id == id) next else it })
                } finally {
                    _busy.value = false
                }
            }
        }
    }

    private suspend fun prepare() {
        library.loaded.first { it }
        listening.loaded.first { it }
        if (!readFromDisk) {
            readFromDisk = true
            _mixes.value = withContext(Dispatchers.IO) {
                runCatching { if (file.exists()) json.decodeFromString<List<Mix>>(file.readText()) else null }.getOrNull()
            } ?: emptyList()
        }
    }

    private suspend fun publish(mixes: List<Mix>) {
        val sorted = sortMixes(mixes)
        _mixes.value = sorted
        withContext(Dispatchers.IO) {
            runCatching {
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeText(json.encodeToString(sorted))
                if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
            }
        }
    }
}

/**
 * Gera o resumo de cada mês assim que ele fecha. Avisa (via [ready]) qual foi o último criado.
 */
class RecapGenerator(
    private val scope: CoroutineScope,
    private val listening: ListeningStore,
    private val profile: ProfileStore,
) {
    private val _ready = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val ready: SharedFlow<String> = _ready.asSharedFlow()
    private val mutex = Mutex()

    fun check() {
        scope.launch(Dispatchers.Default) {
            mutex.withLock {
                listening.loaded.first { it }
                val entries = listening.entries.value
                val pending = pendingRecapMonths(entries, profile.value.recaps.keys)
                var latest: String? = null
                for (month in pending) {
                    val recap = buildMonthlyRecap(entries, month)
                    if (recap.totalMs <= 0) continue
                    profile.saveRecap(recap)
                    latest = month
                }
                latest?.let { _ready.tryEmit(it) }
            }
        }
    }
}
