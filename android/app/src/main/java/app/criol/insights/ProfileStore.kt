package app.criol.insights

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** Cores sugeridas para o perfil (ARGB). */
val ProfileAccents: List<Int> = listOf(
    0xFF7C5CFF, 0xFF1DB954, 0xFFFF4FB1, 0xFFFF8A3D, 0xFF2FB8FF, 0xFFFFD23F, 0xFFE2453C, 0xFF14B8A6,
).map { it.toInt() }

@Serializable
data class Profile(
    val displayName: String = "",
    val bio: String = "",
    val accent: Int = ProfileAccents.first(),
    /** Nome do arquivo da foto em filesDir (muda a cada troca para não pegar cache antigo). */
    val avatarFile: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val pinnedBadge: String? = null,
    val recaps: Map<String, MonthlyRecap> = emptyMap(),
    val seenRecaps: Set<String> = emptySet(),
)

/** Perfil do usuário e resumos mensais já gerados. Salvo em `profile.json`. */
@OptIn(FlowPreview::class)
class ProfileStore(private val context: Context, scope: CoroutineScope) {
    private val file = File(context.filesDir, "profile.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _profile = MutableStateFlow(load())
    val profile: StateFlow<Profile> = _profile.asStateFlow()
    val value: Profile get() = _profile.value

    init {
        _profile.drop(1).debounce(300).onEach { save(it) }
            .launchIn(CoroutineScope(scope.coroutineContext + Dispatchers.IO))
    }

    private fun load(): Profile = runCatching {
        if (file.exists()) json.decodeFromString<Profile>(file.readText()) else null
    }.getOrNull() ?: Profile().also { save(it) }

    private fun save(p: Profile) {
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(p))
            tmp.renameTo(file) || run { file.delete(); tmp.renameTo(file) }
        }
    }

    fun avatarPath(p: Profile = value): String? =
        p.avatarFile?.let { File(context.filesDir, it) }?.takeIf { it.exists() }?.path

    fun update(displayName: String, bio: String, accent: Int) = _profile.update {
        it.copy(displayName = displayName.trim().take(40), bio = bio.trim().take(160), accent = accent)
    }

    fun togglePinned(badge: String) = _profile.update {
        it.copy(pinnedBadge = if (it.pinnedBadge == badge) null else badge)
    }

    fun saveRecap(recap: MonthlyRecap) = _profile.update { it.copy(recaps = it.recaps + (recap.month to recap)) }

    fun markRecapSeen(month: String) = _profile.update {
        if (month in it.seenRecaps) it else it.copy(seenRecaps = it.seenRecaps + month)
    }

    /** Recorta a imagem no centro, reduz para 512×512 e salva como foto do perfil. */
    suspend fun setAvatar(uri: Uri) = withContext(Dispatchers.IO) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val side = minOf(info.size.width, info.size.height)
            if (side > 1024) {
                val scale = 1024f / side
                decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
            }
        }
        val side = minOf(bitmap.width, bitmap.height)
        val square = Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
        val scaled = Bitmap.createScaledBitmap(square, 512, 512, true)
        val name = "avatar_${System.currentTimeMillis()}.jpg"
        File(context.filesDir, name).outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        val old = value.avatarFile
        _profile.update { it.copy(avatarFile = name) }
        if (old != null && old != name) File(context.filesDir, old).delete()
    }

    fun removeAvatar() {
        val old = value.avatarFile ?: return
        _profile.update { it.copy(avatarFile = null) }
        File(context.filesDir, old).delete()
    }
}
