package app.criol.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.criol.ui.theme.Criol
import kotlin.math.roundToInt

/** Estado simples de arrastar-para-reordenar para listas com itens de altura fixa. */
@Stable
class ReorderState(val itemHeightPx: Float) {
    var dragging by mutableStateOf<Int?>(null)
        private set
    var offset by mutableFloatStateOf(0f)
        private set
    var count = 0

    val target: Int?
        get() = dragging?.let { (it + (offset / itemHeightPx).roundToInt()).coerceIn(0, (count - 1).coerceAtLeast(0)) }

    fun start(index: Int) {
        dragging = index
        offset = 0f
    }

    fun drag(dy: Float) {
        offset += dy
    }

    fun end(onMove: (Int, Int) -> Unit) {
        val from = dragging
        val to = target
        dragging = null
        offset = 0f
        if (from != null && to != null && from != to) onMove(from, to)
    }

    fun cancel() {
        dragging = null
        offset = 0f
    }

    /** Deslocamento visual do item [index] durante o arraste. */
    fun shiftFor(index: Int): Float {
        val d = dragging ?: return 0f
        if (index == d) return offset
        val t = target ?: return 0f
        return when {
            d < t && index in (d + 1)..t -> -itemHeightPx
            d > t && index in t until d -> itemHeightPx
            else -> 0f
        }
    }
}

@Composable
fun rememberReorderState(itemHeight: Dp): ReorderState {
    val px = with(LocalDensity.current) { itemHeight.toPx() }
    return remember(px) { ReorderState(px) }
}

/** Aplica o deslocamento ao item da lista. */
@Composable
fun Modifier.reorderItem(state: ReorderState, index: Int): Modifier {
    val isDragging = state.dragging == index
    val shift = state.shiftFor(index)
    val animated by animateFloatAsState(shift, label = "reorder")
    return this
        .zIndex(if (isDragging) 1f else 0f)
        .graphicsLayer {
            translationY = if (isDragging) shift else animated
            shadowElevation = if (isDragging) 12f else 0f
        }
}

@Composable
fun DragHandle(state: ReorderState, index: Int, count: Int, onMove: (Int, Int) -> Unit) {
    state.count = count
    Icon(
        Icons.Rounded.DragHandle,
        contentDescription = "Arrastar",
        tint = Criol.colors.foregroundMuted,
        modifier = Modifier
            .pointerInput(index, count) {
                detectDragGestures(
                    onDragStart = { state.start(index) },
                    onDrag = { change, amount ->
                        change.consume()
                        state.drag(amount.y)
                    },
                    onDragEnd = { state.end(onMove) },
                    onDragCancel = { state.cancel() },
                )
            }
            .padding(12.dp),
    )
}

val ReorderRowHeight = 64.dp
