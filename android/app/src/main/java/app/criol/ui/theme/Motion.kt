package app.criol.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import app.criol.Graph
import kotlin.math.abs

/**
 * Curvas e tempos de animação do app (Material 3 "emphasized"), num lugar só para
 * tudo se mover do mesmo jeito.
 */
object Motion {
    /** Entra rápido e desacelera longamente: elementos chegando. */
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Começa devagar e sai rápido: elementos indo embora. */
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** Movimento padrão, para coisas que ficam na tela. */
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    const val SHORT = 150
    const val MEDIUM = 300
    const val LONG = 450

    fun <T> enter(duration: Int = MEDIUM, delay: Int = 0): FiniteAnimationSpec<T> =
        tween(duration, delay, EmphasizedDecelerate)

    fun <T> exit(duration: Int = SHORT + 50): FiniteAnimationSpec<T> = tween(duration, 0, EmphasizedAccelerate)

    fun <T> standard(duration: Int = MEDIUM): FiniteAnimationSpec<T> = tween(duration, 0, Standard)

    /** Mola sem quique, para soltar arrastes (volta ao lugar com a velocidade do dedo). */
    fun <T> settle(): FiniteAnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
}

/**
 * Posição da música atualizada a cada quadro. O player informa a posição a cada 250 ms;
 * entre uma leitura e outra ela é estimada pelo relógio da tela, então a barra anda
 * continuamente em vez de pular. Pequenas diferenças são corrigidas aos poucos para
 * nunca andar para trás de forma visível.
 */
@Composable
fun rememberSmoothPosition(): State<Long> {
    val reported by Graph.player.position.collectAsState()
    val state by Graph.player.state.collectAsState()
    val smooth = remember { mutableLongStateOf(reported) }
    val playing = state.isPlaying
    val speed = state.speed
    val duration = state.durationMs

    LaunchedEffect(reported, playing, speed, duration) {
        val current = smooth.longValue
        val diff = reported - current
        // Salto real (seek, troca de faixa): vai direto. Diferença pequena: corrige 1/4.
        val base = if (!playing || abs(diff) > 400) reported else current + diff / 4
        smooth.longValue = base
        if (!playing) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                val elapsed = ((now - start) / 1_000_000.0 * speed).toLong()
                val value = base + elapsed
                smooth.longValue = if (duration > 0) value.coerceAtMost(duration) else value
            }
        }
    }
    return smooth
}
