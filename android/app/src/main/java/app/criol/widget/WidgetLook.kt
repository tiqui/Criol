package app.criol.widget

import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import app.criol.Graph
import app.criol.ui.theme.Daylight
import app.criol.ui.theme.paletteFor

/**
 * Cores dos widgets: as mesmas do app (tema e cor de destaque escolhidos nas
 * configurações), para o widget parecer um pedaço do player.
 */
data class WidgetLook(
    val background: Int,
    val surface: Int,
    val foreground: Int,
    val muted: Int,
    val primary: Int,
) {
    /** Trilha da barra de progresso (como o SeekBar do player: texto com 22%). */
    val track: Int get() = ColorUtils.setAlphaComponent(foreground, 0x38)

    companion object {
        fun current(): WidgetLook {
            val s = runCatching { Graph.settings.value }.getOrNull()
            val themeId = s?.themeId ?: "default-dark"
            // Segue o dia junto com o app: a cor é a do momento em que o widget é desenhado.
            val p = if (s?.themeFollowsDay == true) Daylight.palette(themeId, Daylight.minuteNow()) else paletteFor(themeId)
            val bg = p.background.toArgb()
            val fg = p.foreground.toArgb()
            return WidgetLook(
                background = bg,
                // Um degrau acima do fundo, para a capa vazia aparecer sobre o cartão.
                surface = ColorUtils.blendARGB(bg, fg, if (p.isDark) 0.1f else 0.08f),
                foreground = fg,
                muted = p.foregroundMuted.toArgb(),
                primary = s?.accent ?: p.primary.toArgb(),
            )
        }
    }
}
