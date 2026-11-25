package app.criol.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.criol.data.Album
import app.criol.data.Artist
import app.criol.data.Song
import app.criol.data.formatDuration
import app.criol.ui.theme.Criol
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

// ----------------------------------------------------------------------
// Capas
// ----------------------------------------------------------------------

@Composable
fun Artwork(
    path: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(6.dp),
    placeholder: ImageVector = Icons.Rounded.MusicNote,
) {
    val colors = Criol.colors
    Box(
        modifier
            .clip(shape)
            .background(colors.surfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (path != null) {
            val context = LocalContext.current
            val request = remember(path) {
                ImageRequest.Builder(context).data(File(path)).crossfade(true).build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                placeholder,
                contentDescription = null,
                tint = colors.foregroundMuted.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxSize(0.4f),
            )
        }
    }
}

// ----------------------------------------------------------------------
// Barra superior
// ----------------------------------------------------------------------

@Composable
fun TopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    transparent: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = Criol.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (transparent) Color.Transparent else colors.background)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Voltar", tint = colors.foreground)
                }
            } else {
                Spacer(Modifier.width(12.dp))
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = colors.foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            actions()
        }
    }
}

// ----------------------------------------------------------------------
// Linhas e cards
// ----------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: Song,
    artPath: String?,
    onClick: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    trackNumber: Int? = null,
    subtitle: String = song.artist + if (song.album.isNotBlank()) " • ${song.album}" else "",
    isFavorite: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = Criol.colors
    Row(
        modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onMore)
            .padding(start = 16.dp, end = 4.dp)
            .height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (trackNumber != null) {
            Box(Modifier.width(32.dp), contentAlignment = Alignment.CenterStart) {
                if (isCurrent) {
                    Icon(Icons.Rounded.GraphicEq, null, tint = colors.primary, modifier = Modifier.size(18.dp))
                } else {
                    Text(
                        if (trackNumber > 0) trackNumber.toString() else "–",
                        color = colors.foregroundMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        } else {
            Box {
                Artwork(artPath, Modifier.size(46.dp), RoundedCornerShape(4.dp))
                if (isCurrent) {
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.GraphicEq, null, tint = colors.primary, modifier = Modifier.size(22.dp))
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                color = if (isCurrent) colors.primary else colors.foreground,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isFavorite) {
                    Icon(
                        Icons.Rounded.Favorite, null, tint = colors.primary,
                        modifier = Modifier.size(12.dp).padding(end = 2.dp),
                    )
                    Spacer(Modifier.width(3.dp))
                }
                Text(
                    subtitle,
                    color = colors.foregroundMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) trailing() else {
            Text(
                formatDuration(song.durationMs),
                color = colors.foregroundMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        IconButton(onClick = onMore) {
            Icon(Icons.Rounded.MoreVert, "Mais opções", tint = colors.foregroundMuted)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumCard(
    album: Album,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String = album.artist,
) {
    val colors = Criol.colors
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(6.dp),
    ) {
        Artwork(
            album.artPath,
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            RoundedCornerShape(6.dp),
            placeholder = Icons.Rounded.Album,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            album.name,
            color = colors.foreground,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            subtitle,
            color = colors.foregroundMuted,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArtistCircle(
    artist: Artist,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Criol.colors
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Artwork(
            artist.artPath,
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            CircleShape,
            placeholder = Icons.Rounded.MusicNote,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            artist.name,
            color = colors.foreground,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 20.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = Criol.colors.foreground,
            modifier = Modifier.weight(1f),
        )
        action?.invoke()
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = Criol.colors
    Column(
        modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = colors.foregroundMuted, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = colors.foreground)
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.foregroundMuted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}

// ----------------------------------------------------------------------
// Botões
// ----------------------------------------------------------------------

@Composable
fun PlayCircleButton(
    isPlaying: Boolean = false,
    size: Dp = 56.dp,
    onClick: () -> Unit,
) {
    val colors = Criol.colors
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.primary)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = if (isPlaying) "Pausar" else "Tocar",
            tint = colors.onPrimary,
            modifier = Modifier.size(size * 0.55f),
        )
    }
}

@Composable
fun DetailActions(
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    isFavorite: Boolean? = null,
    onFavorite: () -> Unit = {},
    onMore: (() -> Unit)? = null,
) {
    val colors = Criol.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PlayCircleButton(onClick = onPlay)
        IconButton(onClick = onShuffle) {
            Icon(Icons.Rounded.Shuffle, "Aleatório", tint = colors.foreground, modifier = Modifier.size(26.dp))
        }
        if (isFavorite != null) {
            IconButton(onClick = onFavorite) {
                Icon(
                    if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    "Favorito",
                    tint = if (isFavorite) colors.primary else colors.foreground,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        if (onMore != null) {
            IconButton(onClick = onMore) {
                Icon(Icons.Rounded.MoreVert, "Mais", tint = colors.foreground)
            }
        }
    }
}

@Composable
fun RatingStars(rating: Int, onRate: (Int) -> Unit, size: Dp = 22.dp, tint: Color = Criol.colors.primary) {
    val colors = Criol.colors
    Row {
        for (i in 1..5) {
            Icon(
                if (i <= rating) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                contentDescription = "$i estrelas",
                tint = if (i <= rating) tint else colors.foregroundMuted.copy(alpha = 0.6f),
                modifier = Modifier
                    .size(size + 8.dp)
                    .clip(CircleShape)
                    .clickable { onRate(if (rating == i) 0 else i) }
                    .padding(4.dp),
            )
        }
    }
}

// ----------------------------------------------------------------------
// Cabeçalho de detalhes (álbum, artista, gênero, playlist)
// ----------------------------------------------------------------------

/** Cor do gradiente do cabeçalho, escurecida para manter o texto legível. */
fun headerColor(color: Int?, fallback: Color, isDark: Boolean): Color {
    val c = if (color != null) Color(color) else fallback
    if (!isDark) return c.copy(alpha = 0.45f)
    return if (c.luminance() > 0.35f) Color.Black.copy(alpha = 0.45f).compositeOver(c) else c
}

@Composable
fun DetailHeader(
    kicker: String,
    title: String,
    color: Int?,
    art: @Composable () -> Unit,
    subtitle: @Composable () -> Unit,
) {
    val colors = Criol.colors
    val top = headerColor(color, colors.primary.copy(alpha = 0.6f), colors.isDark)
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(top, top.copy(alpha = 0.35f).compositeOver(colors.background), colors.background))),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Spacer(Modifier.height(56.dp))
        art()
        Spacer(Modifier.height(20.dp))
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text(kicker.uppercase(), style = MaterialTheme.typography.labelSmall, color = colors.foreground.copy(alpha = 0.75f))
            Spacer(Modifier.height(4.dp))
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                color = colors.foreground,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            subtitle()
        }
    }
}

// ----------------------------------------------------------------------
// Barra de progresso
// ----------------------------------------------------------------------

@Composable
fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = Criol.colors.foreground,
    inactiveColor: Color = Criol.colors.foreground.copy(alpha = 0.2f),
    onScrub: ((Long?) -> Unit)? = null,
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val fraction = dragFraction ?: if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(durationMs) {
                detectTapGestures { off ->
                    if (durationMs > 0) onSeek((off.x / size.width * durationMs).toLong())
                }
            }
            .pointerInput(durationMs) {
                detectHorizontalDragGestures(
                    onDragStart = { off ->
                        dragFraction = (off.x / size.width).coerceIn(0f, 1f)
                        onScrub?.invoke((dragFraction!! * durationMs).toLong())
                    },
                    onDragEnd = {
                        dragFraction?.let { if (durationMs > 0) onSeek((it * durationMs).toLong()) }
                        dragFraction = null
                        onScrub?.invoke(null)
                    },
                    onDragCancel = { dragFraction = null; onScrub?.invoke(null) },
                    onHorizontalDrag = { change, _ ->
                        dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        onScrub?.invoke((dragFraction!! * durationMs).toLong())
                    },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val width = maxWidth
        val dragging = dragFraction != null
        val trackH = if (dragging) 6.dp else 4.dp
        Box(
            Modifier
                .fillMaxWidth()
                .height(trackH)
                .clip(RoundedCornerShape(3.dp))
                .background(inactiveColor),
        )
        Box(
            Modifier
                .width(width * fraction)
                .height(trackH)
                .clip(RoundedCornerShape(3.dp))
                .background(activeColor),
        )
        val thumb = if (dragging) 16.dp else 12.dp
        Box(
            Modifier
                .offset { IntOffset(with(density) { (width * fraction - thumb / 2).roundToPx() }, 0) }
                .size(thumb)
                .clip(CircleShape)
                .background(activeColor),
        )
    }
}

// ----------------------------------------------------------------------
// Rolagem rápida
// ----------------------------------------------------------------------

@Composable
fun FastScroller(
    state: LazyListState,
    modifier: Modifier = Modifier,
    label: ((Int) -> String)? = null,
) = FastScrollerImpl(
    itemCount = { state.layoutInfo.totalItemsCount },
    firstVisible = { state.firstVisibleItemIndex },
    visibleCount = { state.layoutInfo.visibleItemsInfo.size },
    isScrolling = state.isScrollInProgress,
    scrollTo = { state.scrollToItem(it) },
    label = label,
    modifier = modifier,
)

@Composable
fun FastScroller(
    state: LazyGridState,
    modifier: Modifier = Modifier,
    label: ((Int) -> String)? = null,
) = FastScrollerImpl(
    itemCount = { state.layoutInfo.totalItemsCount },
    firstVisible = { state.firstVisibleItemIndex },
    visibleCount = { state.layoutInfo.visibleItemsInfo.size },
    isScrolling = state.isScrollInProgress,
    scrollTo = { state.scrollToItem(it) },
    label = label,
    modifier = modifier,
)

@Composable
private fun FastScrollerImpl(
    itemCount: () -> Int,
    firstVisible: () -> Int,
    visibleCount: () -> Int,
    isScrolling: Boolean,
    scrollTo: suspend (Int) -> Unit,
    label: ((Int) -> String)?,
    modifier: Modifier,
) {
    val colors = Criol.colors
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(isScrolling, dragging) {
        if (isScrolling || dragging) visible = true else {
            delay(1500)
            visible = false
        }
    }
    val count = itemCount()
    if (count < 40) return
    val alpha by animateFloatAsState(if (visible) 1f else 0f, label = "scroller")
    val thumbH = 52.dp
    BoxWithConstraints(
        modifier
            .fillMaxHeight()
            .width(if (visible) 36.dp else 0.dp),
    ) {
        val density = LocalDensity.current
        val trackPx = with(density) { maxHeight.toPx() }
        val thumbPx = with(density) { thumbH.toPx() }
        val maxFirst = (count - visibleCount()).coerceAtLeast(1)
        val fraction = if (dragging) dragFraction else (firstVisible().toFloat() / maxFirst).coerceIn(0f, 1f)
        fun jump(y: Float) {
            dragFraction = ((y - thumbPx / 2) / (trackPx - thumbPx)).coerceIn(0f, 1f)
            val target = (dragFraction * (count - 1)).roundToInt()
            scope.launch { scrollTo(target) }
        }
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(count) {
                    detectVerticalDragGestures(
                        onDragStart = { dragging = true; jump(it.y) },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                        onVerticalDrag = { change, _ -> change.consume(); jump(change.position.y) },
                    )
                },
        )
        val y = (fraction * (trackPx - thumbPx)).roundToInt()
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, y) }
                .padding(end = 4.dp)
                .width(if (dragging) 8.dp else 6.dp)
                .height(thumbH)
                .alpha(alpha)
                .clip(RoundedCornerShape(4.dp))
                .background(if (dragging) colors.primary else colors.foreground.copy(alpha = 0.45f)),
        )
        if (dragging && label != null) {
            val text = label((dragFraction * (count - 1)).roundToInt().coerceIn(0, count - 1))
            if (text.isNotEmpty()) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset { IntOffset(-with(density) { 44.dp.roundToPx() }, y) }
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(colors.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text, color = colors.onPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Primeira letra para o rótulo da rolagem rápida. */
fun indexLetter(text: String): String {
    val c = text.trim().firstOrNull() ?: return ""
    val n = java.text.Normalizer.normalize(c.toString(), java.text.Normalizer.Form.NFD).first()
    return if (n.isLetter()) n.uppercase() else "#"
}

val ListBottomPadding = PaddingValues(bottom = 24.dp)
