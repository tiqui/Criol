package app.criol.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.criol.Graph
import app.criol.insights.AchievementKind
import app.criol.insights.Archetype
import app.criol.insights.Daypart
import app.criol.insights.Mix
import app.criol.insights.MixKind
import app.criol.ui.theme.Criol
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import java.io.File
import kotlin.math.max

// ----------------------------------------------------------------------
// Ícones
// ----------------------------------------------------------------------

val Archetype.icon: ImageVector
    get() = when (this) {
        Archetype.BALANCED, Archetype.NEWCOMER -> Icons.Rounded.AutoAwesome
        Archetype.EARLY_BIRD -> Icons.Rounded.WbTwilight
        Archetype.EXPLORER -> Icons.Rounded.Explore
        Archetype.GENRE_HOPPER -> Icons.Rounded.Category
        Archetype.LOYAL -> Icons.Rounded.Favorite
        Archetype.NIGHT_OWL -> Icons.Rounded.NightsStay
        Archetype.REPEATER -> Icons.Rounded.AllInclusive
    }

val AchievementKind.icon: ImageVector
    get() = when (this) {
        AchievementKind.FIRST_PLAY -> Icons.Rounded.PlayArrow
        AchievementKind.HOURS_10 -> Icons.Rounded.Schedule
        AchievementKind.HOURS_100 -> Icons.Rounded.EmojiEvents
        AchievementKind.MARATHON -> Icons.Rounded.LocalFireDepartment
        AchievementKind.NIGHT_OWL -> Icons.Rounded.NightsStay
        AchievementKind.EARLY_BIRD -> Icons.Rounded.WbTwilight
        AchievementKind.EXPLORER -> Icons.Rounded.Explore
        AchievementKind.GENRE_HOPPER -> Icons.Rounded.Category
        AchievementKind.SUPERFAN -> Icons.Rounded.Favorite
        AchievementKind.BROKEN_RECORD -> Icons.Rounded.AllInclusive
        AchievementKind.STREAK_7, AchievementKind.STREAK_30 -> Icons.Rounded.CalendarMonth
    }

val Daypart.icon: ImageVector
    get() = when (this) {
        Daypart.MORNING -> Icons.Rounded.WbTwilight
        Daypart.AFTERNOON -> Icons.Rounded.WbSunny
        Daypart.NIGHT -> Icons.Rounded.NightsStay
        Daypart.DAWN -> Icons.Rounded.Bedtime
    }

// ----------------------------------------------------------------------
// Capa das mixes
// ----------------------------------------------------------------------

private data class DaypartPalette(val bg: Color, val floor: Color, val orb: Color, val edge: Color)

private fun paletteOf(daypart: Daypart) = when (daypart) {
    Daypart.DAWN -> DaypartPalette(Color(0xFF070B22), Color(0xFF2A1B5C), Color(0xFF8F7BFF), Color(0xFF3C2D8F))
    Daypart.MORNING -> DaypartPalette(Color(0xFF1B2F66), Color(0xFFF48C5B), Color(0xFFFFD98A), Color(0xFFFF8F5A))
    Daypart.AFTERNOON -> DaypartPalette(Color(0xFF0A3F73), Color(0xFF59C3FF), Color(0xFFC9ECFF), Color(0xFF6AB7FF))
    Daypart.NIGHT -> DaypartPalette(Color(0xFF0B0A2A), Color(0xFF3B1A78), Color(0xFFC7B2FF), Color(0xFF6D3FD6))
}

/** Cor estável a partir de um texto (usada nas mixes de gênero). */
fun stringColor(value: String): Color {
    val hue = (value.lowercase().hashCode() and 0x7fffffff) % 360
    return Color.hsl(hue.toFloat(), 0.55f, 0.40f)
}

/** Cor do cabeçalho da página de uma mix. */
fun mixColor(mix: Mix): Int = when (mix.kind) {
    MixKind.DAYLIST, MixKind.DAYPART -> paletteOf(mix.daypart ?: Daypart.now()).floor
    MixKind.ON_REPEAT -> Color(0xFF2B2A8C)
    MixKind.REPEAT_REWIND -> Color(0xFF0E5A55)
    MixKind.DISCOVER -> Color(0xFFB0384A)
    MixKind.GENRE -> stringColor(mix.genre.orEmpty())
}.toArgb()

private fun Modifier.diagonalGradient(colors: List<Color>) = drawBehind {
    drawRect(Brush.linearGradient(colors, Offset(size.width * 0.3f, 0f), Offset(size.width * 0.7f, size.height)))
}

@Composable
fun MixCover(mix: Mix, label: String, modifier: Modifier = Modifier, large: Boolean = false) {
    val shape = RoundedCornerShape(if (large) 12.dp else 8.dp)
    Box(
        modifier
            .aspectRatio(1f)
            .clip(shape),
    ) {
        val daypart = mix.daypart ?: Daypart.now()
        when (mix.kind) {
            MixKind.DAYLIST -> DaylistArt(paletteOf(daypart))
            MixKind.DAYPART -> {
                val p = paletteOf(daypart)
                Glyph(daypart.icon, p.orb, Modifier.diagonalGradient(listOf(p.floor, p.bg)))
            }
            MixKind.ON_REPEAT ->
                Glyph(Icons.Rounded.AllInclusive, Color(0xFFFF4FB1), Modifier.diagonalGradient(listOf(Color(0xFF2B2A8C), Color(0xFF1B1F5E))))
            MixKind.REPEAT_REWIND ->
                Glyph(Icons.Rounded.History, Color(0xFFFFC04D), Modifier.diagonalGradient(listOf(Color(0xFF0E5A55), Color(0xFF12324F))))
            MixKind.DISCOVER -> Glyph(
                Icons.Rounded.Explore, Color(0xFFFFF3D6),
                Modifier.diagonalGradient(listOf(Color(0xFF6A1B6F), Color(0xFFE0533D), Color(0xFFF7B33B))),
            )
            MixKind.GENRE -> GenreCollage(mix)
        }
        // Selo do "Feito para você".
        val brand = if (large) 26.dp else 18.dp
        Box(
            Modifier
                .padding(if (large) 14.dp else 8.dp)
                .size(brand)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(brand * 0.62f))
        }
        Text(
            label,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(
                fontSize = if (large) 26.sp else 15.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = if (large) 30.sp else 17.sp,
                shadow = Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 1f), 8f),
            ),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = if (large) 18.dp else 10.dp, vertical = if (large) 16.dp else 10.dp),
        )
    }
}

/** Daylist: um orbe luminoso cujas cores seguem o período do dia. */
@Composable
private fun DaylistArt(p: DaypartPalette) {
    val transition = rememberInfiniteTransition(label = "orb")
    val scale by transition.animateFloat(
        1f, 1.06f,
        infiniteRepeatable(tween(4500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "orbScale",
    )
    Canvas(Modifier.fillMaxSize()) {
        drawRect(
            Brush.radialGradient(
                listOf(p.floor, p.bg),
                center = Offset(size.width / 2, size.height * 1.1f),
                radius = size.width * 0.85f,
            ),
        )
    }
    Canvas(Modifier.fillMaxSize().blur(5.dp)) {
        val base = size.width * 0.62f
        val d = base * scale
        val center = Offset(size.width / 2, size.height * 0.14f + base / 2)
        val topLeft = Offset(center.x - d / 2, center.y - d / 2)
        drawCircle(
            Brush.radialGradient(
                0f to Color.White,
                0.32f to p.orb,
                0.62f to p.edge,
                0.72f to Color.Transparent,
                center = Offset(topLeft.x + d * 0.45f, topLeft.y + d * 0.40f),
                radius = d * 0.81f,
            ),
            radius = d / 2,
            center = center,
        )
    }
}

@Composable
private fun Glyph(icon: ImageVector, tint: Color, background: Modifier) {
    Box(background.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(
            icon, null, tint = tint,
            modifier = Modifier
                .fillMaxSize(0.46f)
                .padding(bottom = 6.dp),
        )
    }
}

@Composable
private fun GenreCollage(mix: Mix) {
    val color = stringColor(mix.genre.orEmpty())
    val lib = Graph.library.current
    val arts = mix.coverAlbumIds.mapNotNull { lib.artPath(it) }
    Box(Modifier.fillMaxSize().background(color)) {
        if (arts.isNotEmpty()) {
            Column(Modifier.fillMaxSize()) {
                for (row in 0..1) {
                    Row(Modifier.weight(1f)) {
                        for (col in 0..1) {
                            val path = arts.getOrElse(row * 2 + col) { arts.first() }
                            Artwork(path, Modifier.weight(1f).fillMaxHeight(), RoundedCornerShape(0.dp))
                        }
                    }
                }
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.38f)
                .background(Brush.verticalGradient(0f to Color.Transparent, 0.45f to color, 1f to color)),
        )
    }
}

// ----------------------------------------------------------------------
// Avatar do perfil
// ----------------------------------------------------------------------

@Composable
fun ProfileAvatar(size: Dp, modifier: Modifier = Modifier) {
    val profile by Graph.profile.profile.collectAsState()
    val path = Graph.profile.avatarPath(profile)
    val initial = profile.displayName.trim().firstOrNull()?.uppercase() ?: ""
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(profile.accent)),
        contentAlignment = Alignment.Center,
    ) {
        if (path != null) {
            val context = LocalContext.current
            val request = remember(path) { ImageRequest.Builder(context).data(File(path)).crossfade(true).build() }
            AsyncImage(request, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else if (initial.isNotEmpty()) {
            Text(initial, color = Color.White, fontWeight = FontWeight.Black, fontSize = (size.value * 0.42f).sp)
        } else {
            Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(size * 0.45f))
        }
    }
}

// ----------------------------------------------------------------------
// Cartões, medidores e gráfico de barras
// ----------------------------------------------------------------------

@Composable
fun InsightCard(modifier: Modifier = Modifier, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val colors = Criol.colors
    Column(
        modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceHigh)
            .padding(16.dp),
    ) {
        if (title != null) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = colors.foreground)
            Spacer(Modifier.height(10.dp))
        }
        content()
    }
}

@Composable
fun Meter(fraction: Float, modifier: Modifier = Modifier, color: Color = Criol.colors.primary) {
    Box(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Criol.colors.border),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(3.dp))
                .background(color),
        )
    }
}

data class Bar(val value: Long, val axisLabel: String, val caption: String, val highlight: Boolean = false)

/** Barras simples; tocar numa barra mostra o valor dela. */
@Composable
fun BarStrip(bars: List<Bar>, height: Dp, modifier: Modifier = Modifier, color: Color = Criol.colors.primary) {
    val colors = Criol.colors
    var selected by remember(bars) { mutableStateOf<Int?>(null) }
    val maxValue = max(1L, bars.maxOfOrNull { it.value } ?: 1L)
    Column(modifier.fillMaxWidth()) {
        Text(
            selected?.let { bars.getOrNull(it)?.caption } ?: "Toque numa barra para ver o valor",
            style = MaterialTheme.typography.bodySmall,
            color = if (selected != null) colors.foreground else colors.foregroundMuted,
            maxLines = 1,
        )
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            bars.forEachIndexed { i, bar ->
                val fraction = (bar.value.toFloat() / maxValue).coerceAtLeast(0.025f)
                val active = selected == i || (selected == null && bar.highlight)
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { selected = if (selected == i) null else i },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(fraction)
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(if (active) color else color.copy(alpha = if (bar.value > 0) 0.45f else 0.15f)),
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            bars.forEach { bar ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (bar.axisLabel.isNotEmpty()) {
                        Text(
                            bar.axisLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.foregroundMuted,
                            softWrap = false,
                            maxLines = 1,
                            modifier = Modifier.wrapContentWidth(unbounded = true),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun Pill(text: String, icon: ImageVector? = null, color: Color = Criol.colors.foreground, background: Color = Criol.colors.foreground.copy(alpha = 0.12f)) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(text, color = color, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}
