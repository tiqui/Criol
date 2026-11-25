package app.criol.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Settings(
    /** URIs de árvore (SAF) das pastas da biblioteca. */
    val folders: List<String> = emptyList(),
    /** Pastas extras só com letras (.lrc / .txt). */
    val lyricsFolders: List<String> = emptyList(),
    val themeId: String = "default-dark",
    /** Cor de destaque personalizada (ARGB). null = cor do tema. */
    val accent: Int? = null,
    val dynamicPlayerBackground: Boolean = true,
    val playerBlur: Boolean = true,
    val skipBackSeconds: Int = 10,
    val skipForwardSeconds: Int = 30,
    val showRatings: Boolean = true,
    val resumeQueue: Boolean = true,
    val scanOnStartup: Boolean = true,
    val albumColumns: Int = 2,
    val minDurationSeconds: Int = 0,
    val albumsAsGrid: Boolean = true,
    val songSort: String = "title",
    val albumSort: String = "name",
    val artistSort: String = "name",
    // Auto DJ
    val autoDjEnabled: Boolean = false,
    /** "songs" ou "albums" */
    val autoDjMode: String = "songs",
    /** "similar" ou "random" */
    val autoDjStrategy: String = "similar",
    /** Quantas músicas (ou álbuns) adicionar por vez. */
    val autoDjCount: Int = 5,
    /** Dispara quando restam menos que N músicas na fila. */
    val autoDjTiming: Int = 1,
    val autoDjAllowDuplicates: Boolean = false,
    val autoDjOnlySimilar: Boolean = false,
    /** Registra o histórico de escuta (perfil, daylist, mixes e resumos mensais). */
    val recordHistory: Boolean = true,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("criol_settings", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<Settings> = _settings.asStateFlow()
    val value: Settings get() = _settings.value

    private fun load(): Settings {
        val raw = prefs.getString("settings", null) ?: return Settings()
        return runCatching { json.decodeFromString<Settings>(raw) }.getOrDefault(Settings())
    }

    fun update(transform: (Settings) -> Settings) {
        val next = transform(_settings.value)
        _settings.value = next
        prefs.edit().putString("settings", json.encodeToString(next)).apply()
    }
}
