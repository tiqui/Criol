package app.criol.widget

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** O que os widgets mostram. Salvo para poder desenhar mesmo com o app fechado. */
@Serializable
data class WidgetState(
    val songId: String? = null,
    val title: String = "",
    val artist: String = "",
    val artPath: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = 0,
)

object WidgetStore {
    private val json = Json { ignoreUnknownKeys = true }

    private fun prefs(context: Context) =
        context.getSharedPreferences("criol_widget", Context.MODE_PRIVATE)

    fun load(context: Context): WidgetState = runCatching {
        prefs(context).getString("state", null)?.let { json.decodeFromString<WidgetState>(it) }
    }.getOrNull() ?: WidgetState()

    fun save(context: Context, state: WidgetState) {
        prefs(context).edit().putString("state", json.encodeToString(state)).apply()
    }
}
