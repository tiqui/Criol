package app.criol.ui.player

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import app.criol.ui.theme.rememberSmoothPosition
import app.criol.ui.theme.Motion
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.Job
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.Forward5
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Replay30
import androidx.compose.material.icons.rounded.Replay5
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import app.criol.Graph
import app.criol.data.Lyrics
import app.criol.data.LyricsLoader
import app.criol.data.Song
import app.criol.data.formatDuration
import app.criol.data.formatLongDuration
import app.criol.data.songCountLabel
import app.criol.ui.LocalNavigator
import app.criol.ui.LocalSheets
import app.criol.ui.Sheet
import app.criol.ui.components.Artwork
import app.criol.ui.components.DragHandle
import app.criol.ui.components.RatingStars
import app.criol.ui.components.ReorderRowHeight
import app.criol.ui.components.SeekBar
import app.criol.ui.components.headerColor
import app.criol.ui.components.reorderItem
import app.criol.ui.components.rememberReorderState
import app.criol.ui.theme.Criol
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

private enum class PlayerView { ART, QUEUE, LYRICS }

/** Cores usadas no player: brancas sobre o fundo dinâmico, ou as do tema. */
private data class PlayerColors(val fg: Color, val muted: Color, val accent: Color)

@Composable
fun FullPlayer(onClose: () -> Unit) {
    val state by Graph.player.state.collectAsState()
    val lib by Graph.library.library.collectAsState()
    val settings by Graph.settings.settings.collectAsState()
    val song = state.current
    val colors = Criol.colors
    var view by rememberSaveable { mutableStateOf(PlayerView.ART) }
    var menuOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Arraste para fechar: o dedo move o estado direto (sem corrotina por evento);
    // ao soltar, decide pela distância OU pela velocidade do gesto.
    var dragY by remember { mutableFloatStateOf(0f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val density = LocalDensity.current
    val closeThreshold = with(density) { 140.dp.toPx() }
    val flingThreshold = with(density) { 1200.dp.toPx() }
    val cornerMax = with(density) { 28.dp.toPx() }
    val screenHeight = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }

    BackHandler { if (view != PlayerView.ART) view = PlayerView.ART else onClose() }
    LaunchedEffect(song == null) { if (song == null) onClose() }
    if (song == null) return

    val artPath = lib.artPath(song.albumId)
    val dynamic = settings.dynamicPlayerBackground
    val pc = if (dynamic || colors.isDark) {
        PlayerColors(Color.White, Color.White.copy(alpha = 0.7f), colors.primary)
    } else {
        PlayerColors(colors.foreground, colors.foregroundMuted, colors.primary)
    }

    val dismissDrag = Modifier.pointerInput(Unit) {
        val tracker = VelocityTracker()
        fun settle(velocity: Float) {
            val close = dragY > closeThreshold || velocity > flingThreshold
            settleJob = scope.launch {
                if (close) {
                    // Termina a descida no embalo do dedo e só então fecha.
                    animate(dragY, screenHeight, velocity, Motion.exit(220)) { v, _ -> dragY = v }
                    onClose()
                } else {
                    animate(dragY, 0f, velocity, Motion.settle()) { v, _ -> dragY = v }
                }
            }
        }
        detectVerticalDragGestures(
            onDragStart = { settleJob?.cancel(); tracker.resetTracking() },
            onDragEnd = { settle(tracker.calculateVelocity().y) },
            onDragCancel = { settle(0f) },
            onVerticalDrag = { change, dy ->
                change.consume()
                tracker.addPosition(change.uptimeMillis, change.position)
                dragY = (dragY + dy).coerceAtLeast(0f)
            },
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = dragY
                // Enquanto desce, os cantos arredondam e o player encolhe de leve.
                val f = (dragY / (closeThreshold * 3)).coerceIn(0f, 1f)
                val s = 1f - 0.06f * f
                scaleX = s
                scaleY = s
                transformOrigin = TransformOrigin(0.5f, 0f)
                shape = RoundedCornerShape(cornerMax * f)
                clip = f > 0f
            }
            .background(colors.background)
            // Impede que toques atravessem para a tela por baixo do player.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        PlayerBackground(artPath, lib.artColor(song.albumId), dynamic, settings.playerBlur)
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            // Cabeçalho
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(dismissDrag)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.KeyboardArrowDown, "Fechar", tint = pc.fg, modifier = Modifier.size(32.dp))
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    val radio = state.radio
                    Text(
                        when (view) {
                            PlayerView.ART -> if (radio != null) "TOCANDO DO RÁDIO" else "TOCANDO AGORA"
                            PlayerView.QUEUE -> "FILA"
                            PlayerView.LYRICS -> "LETRA"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = pc.muted,
                    )
                    SleepTimerLabel(pc)
                    Text(
                        if (radio != null) "Rádio de ${radio.title}" else song.album,
                        style = MaterialTheme.typography.bodySmall,
                        color = pc.fg,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, "Mais", tint = pc.fg)
                }
            }

            // Conteúdo central
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AnimatedContent(
                    targetState = view,
                    // "Fade through": o antigo some rápido, o novo entra crescendo de leve.
                    transitionSpec = {
                        (fadeIn(Motion.enter(220, delay = 70)) + scaleIn(Motion.enter(260, delay = 70), initialScale = 0.94f)) togetherWith
                            fadeOut(Motion.exit(90))
                    },
                    label = "playerView",
                ) { v ->
                    when (v) {
                        PlayerView.ART -> ArtView(song, artPath, pc, Modifier.then(dismissDrag))
                        PlayerView.QUEUE -> QueueView(pc)
                        PlayerView.LYRICS -> LyricsView(song, pc)
                    }
                }
            }

            if (view != PlayerView.ART) CompactInfo(song, artPath, pc) { view = PlayerView.ART }
            Progress(pc)
            Controls(pc)
            BottomBar(
                pc = pc,
                view = view,
                onQueue = { view = if (view == PlayerView.QUEUE) PlayerView.ART else PlayerView.QUEUE },
                onLyrics = { view = if (view == PlayerView.LYRICS) PlayerView.ART else PlayerView.LYRICS },
                onMore = { menuOpen = true },
            )
        }
    }

    if (menuOpen) PlayerMenu(song, onDismiss = { menuOpen = false })
}

@Composable
private fun PlayerBackground(artPath: String?, color: Int?, dynamic: Boolean, blur: Boolean) {
    val colors = Criol.colors
    if (dynamic && artPath != null) {
        val context = LocalContext.current
        Crossfade(artPath, animationSpec = Motion.standard(700), label = "bg") { path ->
            Box(Modifier.fillMaxSize()) {
                val request = remember(path) { ImageRequest.Builder(context).data(File(path)).crossfade(true).build() }
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { scaleX = 1.3f; scaleY = 1.3f }
                        .then(if (blur && Build.VERSION.SDK_INT >= 31) Modifier.blur(48.dp) else Modifier),
                )
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = if (blur) 0.35f else 0.55f), Color.Black.copy(alpha = 0.88f)),
                    ),
                ),
        )
    } else {
        val c = headerColor(color, colors.primary.copy(alpha = 0.5f), colors.isDark)
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(c, c.copy(alpha = 0.4f).compositeOver(colors.background), colors.background))),
        )
    }
}

@Composable
private fun SleepTimerLabel(pc: PlayerColors) {
    val state by Graph.player.state.collectAsState()
    val end = state.sleepTimerEnd
    if (end == null && !state.sleepAtEndOfTrack) return
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(end) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val text = if (end != null) "Sono em ${formatDuration(end - now)}" else "Sono ao fim da música"
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Bedtime, null, tint = pc.accent, modifier = Modifier.size(12.dp))
        Text(" $text", style = MaterialTheme.typography.labelSmall, color = pc.accent)
    }
}

// ----------------------------------------------------------------------
// Capa + metadados
// ----------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArtView(song: Song, artPath: String?, pc: PlayerColors, modifier: Modifier) {
    val data by Graph.userData.data.collectAsState()
    val settings by Graph.settings.settings.collectAsState()
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    var swipe by remember { mutableFloatStateOf(0f) }
    var swipeJob by remember { mutableStateOf<Job?>(null) }
    val isFav = song.id in data.favoriteSongs

    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            val side = minOf(maxWidth, maxHeight)
            val sidePx = with(LocalDensity.current) { side.toPx() }
            Box(
                Modifier
                    .size(side)
                    .graphicsLayer {
                        // Carrossel: a capa segue o dedo, inclina e esmaece um pouco.
                        val f = (swipe / sidePx).coerceIn(-1.5f, 1.5f)
                        translationX = swipe
                        rotationZ = f * 4f
                        alpha = 1f - (kotlin.math.abs(f) * 0.6f).coerceAtMost(0.7f)
                        val s = 1f - kotlin.math.abs(f) * 0.06f
                        scaleX = s
                        scaleY = s
                    }
                    .pointerInput(Unit) {
                        val tracker = VelocityTracker()
                        detectHorizontalDragGestures(
                            onDragStart = { swipeJob?.cancel(); tracker.resetTracking() },
                            onDragEnd = {
                                val velocity = tracker.calculateVelocity().x
                                val w = size.width.toFloat()
                                // Distância ou um "peteleco" rápido trocam de faixa.
                                val dir = when {
                                    swipe < -w * 0.25f || velocity < -1500f -> -1
                                    swipe > w * 0.25f || velocity > 1500f -> 1
                                    else -> 0
                                }
                                swipeJob = scope.launch {
                                    if (dir == 0) {
                                        animate(swipe, 0f, velocity, Motion.settle()) { v, _ -> swipe = v }
                                        return@launch
                                    }
                                    // Sai pelo lado do gesto, troca a faixa e a nova entra pelo outro lado.
                                    animate(swipe, dir * w * 1.1f, velocity, Motion.exit(160)) { v, _ -> swipe = v }
                                    if (dir < 0) Graph.player.next() else Graph.player.previous()
                                    swipe = -dir * w * 0.6f
                                    animate(swipe, 0f, 0f, Motion.enter(Motion.MEDIUM)) { v, _ -> swipe = v }
                                }
                            },
                            onDragCancel = {
                                swipeJob = scope.launch { animate(swipe, 0f, 0f, Motion.settle()) { v, _ -> swipe = v } }
                            },
                            onHorizontalDrag = { change, dx ->
                                change.consume()
                                tracker.addPosition(change.uptimeMillis, change.position)
                                swipe += dx
                            },
                        )
                    },
            ) {
                Crossfade(artPath, animationSpec = Motion.standard(Motion.MEDIUM), label = "art") { path ->
                    Artwork(
                        path,
                        Modifier
                            .fillMaxSize()
                            .shadow(24.dp, RoundedCornerShape(12.dp)),
                        RoundedCornerShape(12.dp),
                        Icons.Rounded.Album,
                    )
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 12.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    song.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = pc.fg,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(),
                )
                Text(
                    song.artist,
                    style = MaterialTheme.typography.titleMedium,
                    color = pc.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { song.artists.firstOrNull()?.let { nav.artistByName(it) } },
                )
            }
            IconButton(onClick = { Graph.userData.toggleFavoriteSong(song.id) }) {
                Icon(
                    if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    "Favorito",
                    tint = if (isFav) pc.accent else pc.fg,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        if (settings.showRatings) {
            Box(Modifier.padding(start = 18.dp)) {
                RatingStars(data.ratings[song.id] ?: 0, { Graph.userData.setRating(song.id, it) }, size = 18.dp, tint = pc.accent)
            }
        }
    }
}

@Composable
private fun CompactInfo(song: Song, artPath: String?, pc: PlayerColors, onClick: () -> Unit) {
    val data by Graph.userData.data.collectAsState()
    val isFav = song.id in data.favoriteSongs
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(artPath, Modifier.size(48.dp), RoundedCornerShape(6.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, color = pc.fg, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = pc.muted, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = { Graph.userData.toggleFavoriteSong(song.id) }) {
            Icon(
                if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                "Favorito",
                tint = if (isFav) pc.accent else pc.fg,
            )
        }
    }
}

// ----------------------------------------------------------------------
// Progresso e controles
// ----------------------------------------------------------------------

@Composable
private fun Progress(pc: PlayerColors) {
    val state by Graph.player.state.collectAsState()
    val position by rememberSmoothPosition()
    var scrub by remember { mutableStateOf<Long?>(null) }
    val shown = scrub ?: position
    Column(Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
        SeekBar(
            positionMs = position,
            durationMs = state.durationMs,
            onSeek = { Graph.player.seekTo(it) },
            activeColor = pc.fg,
            inactiveColor = pc.fg.copy(alpha = 0.22f),
            onScrub = { scrub = it },
        )
        Row(Modifier.fillMaxWidth()) {
            Text(formatDuration(shown), color = pc.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Text(
                if (state.durationMs > 0) "-" + formatDuration(state.durationMs - shown) else "--:--",
                color = pc.muted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun backIcon(sec: Int): ImageVector = when (sec) {
    5 -> Icons.Rounded.Replay5
    10 -> Icons.Rounded.Replay10
    30 -> Icons.Rounded.Replay30
    else -> Icons.Rounded.FastRewind
}

private fun forwardIcon(sec: Int): ImageVector = when (sec) {
    5 -> Icons.Rounded.Forward5
    10 -> Icons.Rounded.Forward10
    30 -> Icons.Rounded.Forward30
    else -> Icons.Rounded.FastForward
}

@Composable
private fun Controls(pc: PlayerColors) {
    val state by Graph.player.state.collectAsState()
    val settings by Graph.settings.settings.collectAsState()
    val player = Graph.player
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { player.previous() }, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Rounded.SkipPrevious, "Anterior", tint = pc.fg, modifier = Modifier.size(38.dp))
        }
        IconButton(onClick = { player.skipBack() }, modifier = Modifier.size(52.dp)) {
            Icon(backIcon(settings.skipBackSeconds), "Voltar ${settings.skipBackSeconds} segundos", tint = pc.muted, modifier = Modifier.size(28.dp))
        }
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        // Afunda um pouco ao tocar e volta com mola.
        val pressScale by animateFloatAsState(
            if (pressed) 0.9f else 1f,
            spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
            label = "playPress",
        )
        Box(
            Modifier
                .size(72.dp)
                .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
                .clip(CircleShape)
                .background(pc.fg)
                .clickable(interaction, LocalIndication.current) { player.togglePlayPause() },
            contentAlignment = Alignment.Center,
        ) {
            val iconTint = if (pc.fg == Color.White) Color.Black else Criol.colors.background
            val mode = when {
                state.isBuffering && !state.isPlaying -> 2
                state.isPlaying -> 1
                else -> 0
            }
            AnimatedContent(
                targetState = mode,
                transitionSpec = {
                    (fadeIn(Motion.enter(180)) + scaleIn(Motion.enter(220), initialScale = 0.6f)) togetherWith
                        (fadeOut(Motion.exit(100)) + scaleOut(Motion.exit(100), targetScale = 0.6f))
                },
                contentAlignment = Alignment.Center,
                label = "playIcon",
            ) { m ->
                when (m) {
                    2 -> CircularProgressIndicator(Modifier.size(30.dp), color = iconTint, strokeWidth = 3.dp)
                    1 -> Icon(Icons.Rounded.Pause, "Pausar", tint = iconTint, modifier = Modifier.size(42.dp))
                    else -> Icon(Icons.Rounded.PlayArrow, "Tocar", tint = iconTint, modifier = Modifier.size(42.dp))
                }
            }
        }
        IconButton(onClick = { player.skipForward() }, modifier = Modifier.size(52.dp)) {
            Icon(forwardIcon(settings.skipForwardSeconds), "Avançar ${settings.skipForwardSeconds} segundos", tint = pc.muted, modifier = Modifier.size(28.dp))
        }
        IconButton(onClick = { player.next() }, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Rounded.SkipNext, "Próxima", tint = pc.fg, modifier = Modifier.size(38.dp))
        }
    }
}

@Composable
private fun BottomBar(pc: PlayerColors, view: PlayerView, onQueue: () -> Unit, onLyrics: () -> Unit, onMore: () -> Unit) {
    val state by Graph.player.state.collectAsState()
    HorizontalDivider(color = pc.fg.copy(alpha = 0.1f))
    Row(
        Modifier
            .fillMaxWidth()
            .height(60.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarButton(Icons.Rounded.Shuffle, "Aleatório", state.shuffled, pc) { Graph.player.toggleShuffle() }
        BarButton(
            if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
            "Repetir",
            state.repeatMode != Player.REPEAT_MODE_OFF,
            pc,
        ) { Graph.player.cycleRepeat() }
        val settings by Graph.settings.settings.collectAsState()
        BarButton(Icons.Rounded.AllInclusive, "Auto DJ", settings.autoDjEnabled, pc) {
            Graph.player.setAutoDj(!settings.autoDjEnabled)
        }
        BarButton(Icons.AutoMirrored.Rounded.QueueMusic, "Fila", view == PlayerView.QUEUE, pc, onQueue)
        BarButton(Icons.Rounded.Lyrics, "Letra", view == PlayerView.LYRICS, pc, onLyrics)
        BarButton(Icons.Rounded.MoreVert, "Mais", false, pc, onMore)
    }
}

@Composable
private fun BarButton(icon: ImageVector, label: String, active: Boolean, pc: PlayerColors, onClick: () -> Unit) {
    // A cor muda suavemente ao ligar/desligar.
    val tint by animateColorAsState(if (active) pc.accent else pc.fg.copy(alpha = 0.85f), Motion.standard(250), label = "bar")
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(25.dp))
    }
}

// ----------------------------------------------------------------------
// Fila
// ----------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueueView(pc: PlayerColors) {
    val state by Graph.player.state.collectAsState()
    val lib by Graph.library.library.collectAsState()
    val sheets = LocalSheets.current
    val listState = rememberLazyListState()
    val reorder = rememberReorderState(ReorderRowHeight)
    val queue = state.queue

    LaunchedEffect(Unit) {
        if (state.index > 0) listState.scrollToItem((state.index - 1).coerceAtLeast(0))
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${songCountLabel(queue.size)} • ${formatLongDuration(queue.sumOf { it.durationMs })}",
                color = pc.muted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            val settings by Graph.settings.settings.collectAsState()
            if (state.radio == null) TextButton(onClick = { Graph.player.setAutoDj(!settings.autoDjEnabled) }) {
                Icon(
                    Icons.Rounded.AllInclusive, null,
                    tint = if (settings.autoDjEnabled) pc.accent else pc.fg,
                    modifier = Modifier.size(18.dp),
                )
                Text(" Auto DJ", color = if (settings.autoDjEnabled) pc.accent else pc.fg)
            }
            TextButton(onClick = { Graph.player.clearQueue() }) {
                Icon(Icons.Rounded.ClearAll, null, tint = pc.fg, modifier = Modifier.size(18.dp))
                Text(" Limpar", color = pc.fg)
            }
        }
        state.radio?.let { seed -> RadioBanner(seed, pc) }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 8.dp)) {
            itemsIndexed(queue, key = { i, s -> "$i-${s.id}" }) { i, s ->
                val isCurrent = i == state.index
                Row(
                    Modifier
                        .reorderItem(reorder, i)
                        .fillMaxWidth()
                        .height(ReorderRowHeight)
                        .background(if (isCurrent) pc.fg.copy(alpha = 0.10f) else Color.Transparent)
                        .combinedClickable(
                            onClick = { Graph.player.playAt(i) },
                            onLongClick = { sheets.show(Sheet.SongMenu(s, queueIndex = i)) },
                        )
                        .padding(start = 16.dp, end = 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(lib.artPath(s.albumId), Modifier.size(44.dp), RoundedCornerShape(4.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            s.title,
                            color = if (isCurrent) pc.accent else pc.fg,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            s.artist,
                            color = pc.muted,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(formatDuration(s.durationMs), color = pc.muted, style = MaterialTheme.typography.bodySmall)
                    IconButton(onClick = { sheets.show(Sheet.SongMenu(s, queueIndex = i)) }) {
                        Icon(Icons.Rounded.MoreVert, "Mais", tint = pc.muted)
                    }
                    DragHandle(reorder, i, queue.size) { from, to -> Graph.player.move(from, to) }
                }
            }
        }
    }
}

// ----------------------------------------------------------------------
// Letra
// ----------------------------------------------------------------------

private sealed interface LyricsState {
    data object Loading : LyricsState
    data class Loaded(val lyrics: Lyrics?) : LyricsState
}

@Composable
private fun LyricsView(song: Song, pc: PlayerColors) {
    val context = LocalContext.current
    val lyricsState by produceState<LyricsState>(LyricsState.Loading, song.id) {
        value = LyricsState.Loading
        value = LyricsState.Loaded(LyricsLoader.load(context, song))
    }
    when (val s = lyricsState) {
        LyricsState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = pc.fg)
        }
        is LyricsState.Loaded -> {
            val lyrics = s.lyrics
            when {
                lyrics == null -> Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Rounded.Lyrics, null, tint = pc.muted, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Nenhuma letra encontrada", color = pc.fg, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Coloque um .lrc ao lado da música, adicione a pasta das letras em Configurações > Letras, ou use letras embutidas nas tags.",
                        color = pc.muted,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
                lyrics.synced -> SyncedLyrics(lyrics, pc)
                else -> Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                ) {
                    lyrics.lines.forEach {
                        Text(
                            it.text.ifEmpty { " " },
                            color = pc.fg,
                            fontSize = 20.sp,
                            lineHeight = 30.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(lyrics.source, color = pc.muted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun SyncedLyrics(lyrics: Lyrics, pc: PlayerColors) {
    val position by Graph.player.position.collectAsState()
    val listState = rememberLazyListState()
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    var lastUserScroll by remember { mutableLongStateOf(0L) }
    // Marca o início e o fim do arraste manual; a rolagem automática espera 3 s.
    LaunchedEffect(dragged) { if (dragged || lastUserScroll > 0) lastUserScroll = System.currentTimeMillis() }

    val current = remember(position, lyrics) {
        var idx = -1
        for (i in lyrics.lines.indices) {
            if (lyrics.lines[i].timeMs <= position + 250) idx = i else break
        }
        idx
    }
    LaunchedEffect(current) {
        if (current >= 0 && System.currentTimeMillis() - lastUserScroll > 3000) {
            // Se a linha está na tela, desliza com curva suave; senão, usa a rolagem padrão.
            val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == current }
            if (info != null) {
                listState.animateScrollBy(info.offset.toFloat(), Motion.standard(650))
            } else {
                listState.animateScrollToItem(current)
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val topPad = maxHeight * 0.35f
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = topPad, bottom = maxHeight * 0.6f, start = 24.dp, end = 24.dp),
        ) {
            itemsIndexed(lyrics.lines) { i, line ->
                val active = i == current
                // Tamanho fixo + escala no desenho: a linha ativa cresce sem empurrar o texto.
                val emphasis by animateFloatAsState(if (active) 1f else 0f, Motion.standard(400), label = "lyric")
                Text(
                    line.text.ifEmpty { "♪" },
                    color = pc.fg,
                    fontSize = 22.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = 0.38f + 0.62f * emphasis
                            val s = 1f + 0.12f * emphasis
                            scaleX = s
                            scaleY = s
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        }
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            lastUserScroll = 0
                            Graph.player.seekTo(line.timeMs)
                        }
                        .padding(vertical = 8.dp),
                )
            }
        }
    }
}

// ----------------------------------------------------------------------
// Menu do player
// ----------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerMenu(song: Song, onDismiss: () -> Unit) {
    val state by Graph.player.state.collectAsState()
    val nav = LocalNavigator.current
    val sheets = LocalSheets.current
    val lib = Graph.library.current
    var page by remember { mutableStateOf(0) } // 0 = principal, 1 = timer, 2 = velocidade
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = Criol.colors

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = colors.surface) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        ) {
            when (page) {
                0 -> {
                    MenuRow(Icons.Rounded.Bedtime, "Timer de sono", trailing = when {
                        state.sleepTimerEnd != null -> "Ativo"
                        state.sleepAtEndOfTrack -> "Fim da música"
                        else -> null
                    }) { page = 1 }
                    MenuRow(Icons.Rounded.Speed, "Velocidade", trailing = "${state.speed}x") { page = 2 }
                    MenuRow(Icons.AutoMirrored.Rounded.PlaylistAdd, "Adicionar à playlist") {
                        onDismiss(); sheets.show(Sheet.AddToPlaylist(listOf(song)))
                    }
                    MenuRow(Icons.Rounded.Radio, "Rádio da música") {
                        onDismiss(); Graph.player.startRadio(song)
                    }
                    if (lib.albumsById.containsKey(song.albumId)) {
                        MenuRow(Icons.Rounded.Album, "Ir para o álbum") { onDismiss(); nav.album(song.albumId) }
                    }
                    song.artists.firstOrNull()?.let { name ->
                        MenuRow(Icons.Rounded.Person, "Ir para $name") { onDismiss(); nav.artistByName(name) }
                    }
                    MenuRow(Icons.Rounded.Info, "Informações") { onDismiss(); sheets.show(Sheet.SongInfo(song)) }
                    MenuRow(Icons.Rounded.ClearAll, "Limpar fila") { onDismiss(); Graph.player.clearQueue() }
                }
                1 -> {
                    Text("Timer de sono", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                    listOf(5, 10, 15, 30, 45, 60, 90).forEach { m ->
                        MenuRow(null, if (m < 60) "$m minutos" else if (m == 60) "1 hora" else "1 h 30 min") {
                            Graph.player.setSleepTimer(m); onDismiss()
                        }
                    }
                    MenuRow(null, "Ao fim da música atual") { Graph.player.setSleepAtEndOfTrack(); onDismiss() }
                    if (state.sleepTimerEnd != null || state.sleepAtEndOfTrack) {
                        MenuRow(null, "Desligar timer") { Graph.player.cancelSleepTimer(); onDismiss() }
                    }
                }
                else -> {
                    Text("Velocidade", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).forEach { sp ->
                        MenuRow(null, "${sp}x", trailing = if (sp == state.speed) "✓" else null) {
                            Graph.player.setSpeed(sp); onDismiss()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector?, label: String, trailing: String? = null, onClick: () -> Unit) {
    val colors = Criol.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = colors.foregroundMuted, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(20.dp))
        }
        Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.foreground, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, color = colors.primary, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Aviso no topo da fila enquanto um rádio está tocando, com opção de parar. */
@Composable
private fun RadioBanner(seed: Song, pc: PlayerColors) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(pc.fg.copy(alpha = 0.08f))
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Radio, null, tint = pc.accent, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(
                "Rádio de ${seed.title}",
                color = pc.fg,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "Novas músicas parecidas entram sozinhas",
                color = pc.muted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = { Graph.player.stopRadio() }) { Text("Parar", color = pc.fg) }
    }
}
