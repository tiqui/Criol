package app.criol.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animate
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.criol.Graph
import app.criol.ui.components.Artwork
import app.criol.ui.theme.Criol
import app.criol.ui.theme.Motion
import app.criol.ui.theme.rememberSmoothPosition
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun MiniPlayer(onExpand: () -> Unit) {
    val state by Graph.player.state.collectAsState()
    val lib by Graph.library.library.collectAsState()
    val song = state.current ?: return
    val colors = Criol.colors
    val scope = rememberCoroutineScope()
    var swipe by remember { mutableFloatStateOf(0f) }
    var swipeJob by remember { mutableStateOf<Job?>(null) }
    // Lida só no desenho: a barra anda a cada quadro sem recompor o mini player.
    val position = rememberSmoothPosition()
    val duration = state.durationMs

    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.backgroundAlt),
    ) {
        // Progresso fino no topo
        val track = colors.border
        val bar = colors.primary
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .drawBehind {
                    drawRect(track)
                    val f = if (duration > 0) (position.value.toFloat() / duration).coerceIn(0f, 1f) else 0f
                    drawRect(bar, size = Size(size.width * f, size.height))
                },
        )
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clickable(onClick = onExpand)
                .pointerInput(Unit) {
                    var opened = false
                    detectVerticalDragGestures(
                        onDragStart = { opened = false },
                        onVerticalDrag = { _, dy ->
                            if (dy < -12 && !opened) {
                                opened = true
                                onExpand()
                            }
                        },
                    )
                }
                .pointerInput(Unit) {
                    val tracker = VelocityTracker()
                    detectHorizontalDragGestures(
                        onDragStart = { swipeJob?.cancel(); tracker.resetTracking() },
                        onDragEnd = {
                            val velocity = tracker.calculateVelocity().x
                            val w = size.width.toFloat()
                            val dir = when {
                                swipe < -w * 0.22f || velocity < -1500f -> -1
                                swipe > w * 0.22f || velocity > 1500f -> 1
                                else -> 0
                            }
                            swipeJob = scope.launch {
                                if (dir == 0) {
                                    animate(swipe, 0f, velocity, Motion.settle()) { v, _ -> swipe = v }
                                    return@launch
                                }
                                animate(swipe, dir * w * 0.6f, velocity, Motion.exit(140)) { v, _ -> swipe = v }
                                if (dir < 0) Graph.player.next() else Graph.player.previous()
                                swipe = -dir * w * 0.3f
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
                }
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .graphicsLayer {
                        translationX = swipe
                        alpha = 1f - (abs(swipe) / 500f).coerceIn(0f, 0.8f)
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(lib.artPath(song.albumId), Modifier.size(48.dp), RoundedCornerShape(5.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        song.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.foreground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.foregroundMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = { Graph.player.togglePlayPause() }) {
                AnimatedContent(
                    targetState = state.isPlaying,
                    transitionSpec = {
                        (fadeIn(Motion.enter(160)) + scaleIn(Motion.enter(200), initialScale = 0.6f)) togetherWith
                            (fadeOut(Motion.exit(90)) + scaleOut(Motion.exit(90), targetScale = 0.6f))
                    },
                    contentAlignment = Alignment.Center,
                    label = "miniPlay",
                ) { playing ->
                    Icon(
                        if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (playing) "Pausar" else "Tocar",
                        tint = colors.foreground,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
            IconButton(onClick = { Graph.player.next() }, enabled = state.hasNext) {
                Icon(
                    Icons.Rounded.SkipNext,
                    "Próxima",
                    tint = if (state.hasNext) colors.foreground else colors.foregroundMuted.copy(alpha = 0.4f),
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(50)),
                )
            }
        }
    }
}
