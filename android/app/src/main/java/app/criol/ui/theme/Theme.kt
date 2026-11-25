package app.criol.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Paleta de um tema do app. */
@Immutable
data class CriolPalette(
    val id: String,
    val name: String,
    val isDark: Boolean,
    val background: Color,
    val backgroundAlt: Color,
    val surface: Color,
    val foreground: Color,
    val foregroundMuted: Color,
    val primary: Color,
) {
    val border: Color get() = foreground.copy(alpha = if (isDark) 0.09f else 0.12f)
    val onPrimary: Color get() = if (primary.luminance() > 0.5f) Color(0xFF111111) else Color.White
    val surfaceHigh: Color get() = foreground.copy(alpha = if (isDark) 0.06f else 0.05f).compositeOver(surface)
}

private fun rgb(r: Int, g: Int, b: Int) = Color(r, g, b)

val Palettes: List<CriolPalette> = listOf(
    CriolPalette("default-dark", "Padrão escuro", true, rgb(12, 12, 12), rgb(8, 8, 8), rgb(20, 20, 20), rgb(225, 225, 225), rgb(150, 150, 150), rgb(53, 116, 252)),
    CriolPalette("default-light", "Padrão claro", false, rgb(235, 235, 235), rgb(240, 240, 240), rgb(225, 225, 225), rgb(25, 25, 25), rgb(80, 80, 80), rgb(0, 122, 255)),
    CriolPalette("nord", "Nord", true, rgb(46, 52, 64), rgb(37, 41, 54), rgb(59, 66, 82), rgb(236, 239, 244), rgb(216, 222, 233), rgb(136, 192, 208)),
    CriolPalette("dracula", "Dracula", true, rgb(40, 42, 54), rgb(30, 31, 41), rgb(68, 71, 90), rgb(248, 248, 242), rgb(189, 147, 249), rgb(189, 147, 249)),
    CriolPalette("one-dark", "One Dark", true, rgb(40, 44, 52), rgb(30, 33, 40), rgb(55, 59, 70), rgb(171, 178, 191), rgb(152, 161, 178), rgb(97, 175, 239)),
    CriolPalette("catppuccin-mocha", "Catppuccin Mocha", true, rgb(24, 24, 37), rgb(17, 17, 27), rgb(30, 30, 46), rgb(205, 214, 244), rgb(186, 194, 222), rgb(137, 180, 250)),
    CriolPalette("catppuccin-latte", "Catppuccin Latte", false, rgb(239, 241, 245), rgb(230, 233, 239), rgb(220, 224, 232), rgb(76, 79, 105), rgb(108, 111, 133), rgb(30, 102, 245)),
    CriolPalette("tokyo-night", "Tokyo Night", true, rgb(26, 27, 38), rgb(20, 21, 30), rgb(35, 36, 51), rgb(192, 202, 245), rgb(169, 177, 214), rgb(125, 207, 255)),
    CriolPalette("gruvbox-dark", "Gruvbox Dark", true, rgb(40, 40, 40), rgb(29, 32, 33), rgb(50, 48, 47), rgb(235, 219, 178), rgb(189, 174, 147), rgb(250, 189, 47)),
    CriolPalette("rose-pine", "Rosé Pine", true, Color(0xFF191724), Color(0xFF191724), Color(0xFF1F1D2E), Color(0xFFE0DEF4), Color(0xFF908CAA), Color(0xFFEBBCBA)),
    CriolPalette("everforest-dark", "Everforest Dark", true, rgb(35, 42, 46), rgb(35, 42, 46), rgb(52, 63, 56), rgb(211, 198, 170), rgb(157, 169, 160), rgb(167, 192, 128)),
    CriolPalette("monokai", "Monokai", true, rgb(39, 40, 34), rgb(30, 31, 28), rgb(50, 51, 45), rgb(248, 248, 242), rgb(150, 146, 125), rgb(174, 129, 255)),
    CriolPalette("night-owl", "Night Owl", true, rgb(1, 22, 39), rgb(0, 16, 28), rgb(11, 41, 66), rgb(214, 222, 235), rgb(171, 178, 191), rgb(130, 170, 255)),
    CriolPalette("github-dark", "GitHub Dark", true, rgb(13, 17, 23), rgb(22, 27, 34), rgb(30, 37, 46), rgb(201, 209, 217), rgb(139, 148, 158), rgb(88, 166, 255)),
    CriolPalette("ayu-dark", "Ayu Dark", true, rgb(31, 36, 48), rgb(23, 27, 36), rgb(39, 46, 57), rgb(203, 204, 198), rgb(170, 173, 166), rgb(115, 192, 203)),
    CriolPalette("solarized-dark", "Solarized Dark", true, rgb(0, 43, 54), rgb(7, 54, 66), rgb(14, 65, 78), rgb(147, 161, 161), rgb(101, 123, 131), rgb(38, 139, 210)),
)

/** Cores de destaque sugeridas. */
val AccentPresets: List<Color> = listOf(
    rgb(53, 116, 252), rgb(0, 168, 255), rgb(29, 185, 84), rgb(46, 204, 113), rgb(255, 193, 7),
    rgb(255, 128, 0), rgb(240, 60, 60), rgb(233, 30, 99), rgb(171, 71, 188), rgb(126, 87, 194),
    rgb(0, 188, 212), rgb(255, 255, 255),
)

fun paletteFor(id: String): CriolPalette = Palettes.firstOrNull { it.id == id } ?: Palettes.first()

val LocalPalette = staticCompositionLocalOf { Palettes.first() }

object Criol {
    val colors: CriolPalette
        @Composable get() = LocalPalette.current
}

private fun schemeFrom(p: CriolPalette): ColorScheme {
    val base = if (p.isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = p.primary,
        onPrimary = p.onPrimary,
        primaryContainer = p.primary.copy(alpha = 0.25f).compositeOver(p.surface),
        onPrimaryContainer = p.foreground,
        secondary = p.primary,
        onSecondary = p.onPrimary,
        secondaryContainer = p.primary.copy(alpha = 0.18f).compositeOver(p.backgroundAlt),
        onSecondaryContainer = p.foreground,
        background = p.background,
        onBackground = p.foreground,
        surface = p.background,
        onSurface = p.foreground,
        surfaceVariant = p.surface,
        onSurfaceVariant = p.foregroundMuted,
        surfaceContainerLowest = p.backgroundAlt,
        surfaceContainerLow = p.surface,
        surfaceContainer = p.surface,
        surfaceContainerHigh = p.surfaceHigh,
        surfaceContainerHighest = p.surfaceHigh,
        surfaceBright = p.surfaceHigh,
        outline = p.foreground.copy(alpha = 0.25f),
        outlineVariant = p.border,
        inverseSurface = p.foreground,
        inverseOnSurface = p.background,
    )
}

private val typography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.Black, fontSize = 32.sp, lineHeight = 38.sp),
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.Black, fontSize = 30.sp, lineHeight = 36.sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Black, fontSize = 26.sp, lineHeight = 32.sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 22.sp),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
        titleSmall = t.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
        bodyMedium = t.bodyMedium.copy(fontSize = 14.sp),
        bodySmall = t.bodySmall.copy(fontSize = 13.sp),
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp),
    )
}

@Composable
fun CriolTheme(themeId: String, accent: Int?, content: @Composable () -> Unit) {
    val base = paletteFor(themeId)
    val palette = if (accent != null) base.copy(primary = Color(accent)) else base
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = schemeFrom(palette), typography = typography, content = content)
    }
}
