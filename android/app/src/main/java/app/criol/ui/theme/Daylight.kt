package app.criol.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlinx.coroutines.delay
import java.util.Calendar

/**
 * Tema que segue o dia: claro de manhã, vai escurecendo durante a tarde até o tema
 * escuro à noite, e volta a clarear no amanhecer. A versão desktop/web usa a mesma
 * conta (desktop/src/renderer/styles/daylight.ts), para as duas mudarem juntas.
 */
object Daylight {
    private const val DAWN_START = 5 * 60
    private const val DAWN_END = 7 * 60
    private const val DUSK_START = 12 * 60
    private const val DUSK_END = 20 * 60

    /**
     * Luminância do fundo em que o texto troca de escuro para claro: nesse ponto preto e
     * branco têm o mesmo contraste (~4,6:1), então a troca nunca deixa o texto ilegível.
     */
    private const val TEXT_FLIP = 0.18f

    /** Quanto de "noite" no minuto do dia (0 a 1439): 0 = tema claro, 1 = tema escuro. */
    fun nightAmount(minuteOfDay: Int): Float {
        val m = minuteOfDay.mod(24 * 60)
        return when {
            m < DAWN_START -> 1f
            m < DAWN_END -> 1f - smooth((m - DAWN_START).toFloat() / (DAWN_END - DAWN_START))
            m < DUSK_START -> 0f
            m < DUSK_END -> smooth((m - DUSK_START).toFloat() / (DUSK_END - DUSK_START))
            else -> 1f
        }
    }

    private fun smooth(x: Float): Float {
        val t = x.coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }

    /**
     * As duas pontas: o tema escolhido é uma delas. Escolheu um tema escuro? À noite é
     * ele e de dia o Padrão claro. Escolheu um claro? De dia é ele e à noite o Padrão escuro.
     */
    fun pair(themeId: String): Pair<CriolPalette, CriolPalette> {
        val chosen = paletteFor(themeId)
        return if (chosen.isDark) paletteFor("default-light") to chosen else chosen to paletteFor("default-dark")
    }

    fun blend(day: CriolPalette, night: CriolPalette, t: Float): CriolPalette {
        if (t <= 0f) return day
        if (t >= 1f) return night
        val background = mix(day.background, night.background, t)
        val dark = background.luminance() < TEXT_FLIP
        val text = if (dark) night else day
        // Perto da troca o fundo fica cinza médio: o texto vai para o branco (ou preto)
        // só o necessário para continuar legível.
        val extreme = if (dark) Color.White else Color.Black
        return CriolPalette(
            id = if (dark) night.id else day.id,
            name = text.name,
            isDark = dark,
            background = background,
            backgroundAlt = mix(day.backgroundAlt, night.backgroundAlt, t),
            surface = mix(day.surface, night.surface, t),
            foreground = readable(text.foreground, background, 4.5f, extreme),
            foregroundMuted = readable(text.foregroundMuted, background, 3f, extreme),
            primary = mix(day.primary, night.primary, t),
        )
    }

    private fun readable(fg: Color, bg: Color, min: Float, toward: Color): Color {
        var c = fg
        var step = 0
        while (contrast(c, bg) < min && step < 20) {
            step++
            c = mix(fg, toward, step / 20f)
        }
        return c
    }

    private fun contrast(a: Color, b: Color): Float {
        val x = a.luminance()
        val y = b.luminance()
        return (maxOf(x, y) + 0.05f) / (minOf(x, y) + 0.05f)
    }

    fun palette(themeId: String, minuteOfDay: Int): CriolPalette {
        val (day, night) = pair(themeId)
        return blend(day, night, nightAmount(minuteOfDay))
    }

    fun minuteNow(): Int = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }

    /** Mistura em sRGB (a mesma conta do desktop). */
    private fun mix(a: Color, b: Color, t: Float) = Color(
        red = a.red + (b.red - a.red) * t,
        green = a.green + (b.green - a.green) * t,
        blue = a.blue + (b.blue - a.blue) * t,
    )
}

/** Minuto do dia que muda a cada minuto (só fica ativo quando o tema segue o dia). */
@Composable
fun rememberMinuteOfDay(enabled: Boolean): Int {
    var minute by remember { mutableIntStateOf(Daylight.minuteNow()) }
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        while (true) {
            minute = Daylight.minuteNow()
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }
    return minute
}
