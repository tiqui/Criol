package app.criol.playback

import androidx.media3.common.C
import androidx.media3.exoplayer.source.ShuffleOrder
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/**
 * Informações que o [PlaybackService] mantém atualizadas para a ordem aleatória:
 * qual item está tocando e onde o próximo item inserido deve entrar.
 */
class ShuffleHints {
    /** Índice (na fila física) da música tocando agora. */
    val current = AtomicInteger(C.INDEX_UNSET)

    /** Vale para a próxima inserção só: [INSERT_NEXT], [INSERT_END] ou [INSERT_RANDOM]. */
    val nextInsert = AtomicInteger(INSERT_RANDOM)

    companion object {
        const val INSERT_RANDOM = 0
        const val INSERT_NEXT = 1
        const val INSERT_END = 2
    }
}

/**
 * Ordem aleatória do Criol, usada pelo ExoPlayer no modo aleatório.
 *
 * A fila física não muda quando se liga o aleatório: só a ordem de reprodução. Ao desligar,
 * o player volta naturalmente para a ordem original. Diferente da ordem padrão do ExoPlayer:
 * - a música atual vira a primeira (nada do que vem "antes" dela fica sem tocar);
 * - "Tocar a seguir" entra logo depois da atual e "Adicionar à fila" entra no fim,
 *   em vez de cair em posições sorteadas;
 * - mover itens na fila física não embaralha a ordem de quem já estava nela.
 *
 * @param shuffled índices da fila física, na ordem em que vão tocar.
 */
class CriolShuffleOrder(
    private val shuffled: IntArray,
    private val hints: ShuffleHints,
    private val random: Random = Random.Default,
) : ShuffleOrder {

    /** Posição de cada índice físico dentro de [shuffled]. */
    private val positionOf = IntArray(shuffled.size).also { pos ->
        for (p in shuffled.indices) pos[shuffled[p]] = p
    }

    /** A ordem de reprodução, para testes e para salvar a fila. */
    fun order(): List<Int> = shuffled.toList()

    override fun getLength(): Int = shuffled.size

    override fun getNextIndex(index: Int): Int {
        val p = positionOf[index] + 1
        return if (p < shuffled.size) shuffled[p] else C.INDEX_UNSET
    }

    override fun getPreviousIndex(index: Int): Int {
        val p = positionOf[index] - 1
        return if (p >= 0) shuffled[p] else C.INDEX_UNSET
    }

    override fun getLastIndex(): Int = if (shuffled.isEmpty()) C.INDEX_UNSET else shuffled.last()

    override fun getFirstIndex(): Int = if (shuffled.isEmpty()) C.INDEX_UNSET else shuffled[0]

    override fun cloneAndInsert(insertionIndex: Int, insertionCount: Int): ShuffleOrder {
        val mode = hints.nextInsert.getAndSet(ShuffleHints.INSERT_RANDOM)
        if (insertionCount <= 0) return this
        val shifted = shuffled.map { if (it >= insertionIndex) it + insertionCount else it }.toMutableList()
        val added = (insertionIndex until insertionIndex + insertionCount).toList()
        if (shifted.isEmpty()) return copy(added)

        val current = hints.current.get()
        // A atual, já com o deslocamento da inserção.
        val currentNow = when {
            current == C.INDEX_UNSET || current >= shuffled.size -> -1
            current >= insertionIndex -> current + insertionCount
            else -> current
        }
        val currentPos = if (currentNow >= 0) shifted.indexOf(currentNow) else -1
        when {
            mode == ShuffleHints.INSERT_END -> shifted.addAll(added)
            mode == ShuffleHints.INSERT_NEXT && currentPos >= 0 -> shifted.addAll(currentPos + 1, added)
            else -> for (i in added) {
                // Sorteia uma posição depois da atual, para tudo o que foi inserido ainda tocar.
                val from = currentPos + 1
                shifted.add(from + random.nextInt(shifted.size - from + 1), i)
            }
        }
        return copy(shifted)
    }

    override fun cloneAndRemove(indexFrom: Int, indexToExclusive: Int): ShuffleOrder {
        val count = indexToExclusive - indexFrom
        if (count <= 0) return this
        return copy(
            shuffled.filter { it < indexFrom || it >= indexToExclusive }
                .map { if (it >= indexToExclusive) it - count else it },
        )
    }

    /** Move o bloco físico [indexFrom, indexToExclusive) para [newIndexFrom], sem mudar a vez de ninguém. */
    override fun cloneAndMove(indexFrom: Int, indexToExclusive: Int, newIndexFrom: Int): ShuffleOrder {
        val n = shuffled.size
        if (indexFrom >= indexToExclusive || indexFrom == newIndexFrom) return this
        val physical = (0 until n).toMutableList()
        val block = physical.subList(indexFrom, indexToExclusive).toList()
        repeat(block.size) { physical.removeAt(indexFrom) }
        physical.addAll(newIndexFrom, block)
        // physical[novo] = antigo -> inverte para saber o novo índice de cada antigo.
        val newIndexOf = IntArray(n)
        physical.forEachIndexed { newIndex, old -> newIndexOf[old] = newIndex }
        return copy(shuffled.map { newIndexOf[it] })
    }

    override fun cloneAndClear(): ShuffleOrder = copy(emptyList())

    /** A fila foi trocada inteira: [startIndex] toca primeiro, o resto sorteado. */
    override fun cloneAndSet(insertionCount: Int, startIndex: Int): ShuffleOrder =
        currentFirst(insertionCount, startIndex, hints, random)

    private fun copy(order: List<Int>) = CriolShuffleOrder(order.toIntArray(), hints, random)

    companion object {
        /** Ordem nova: [current] primeiro (se válida), o resto em ordem sorteada. */
        fun currentFirst(length: Int, current: Int, hints: ShuffleHints, random: Random = Random.Default): CriolShuffleOrder {
            val rest = (0 until length).filter { it != current }.shuffled(random)
            val order = if (current in 0 until length) listOf(current) + rest else rest
            return CriolShuffleOrder(order.toIntArray(), hints, random)
        }

        /** Usa [order] se for uma permutação válida de 0 até length-1. */
        fun fromOrder(order: List<Int>, length: Int, hints: ShuffleHints): CriolShuffleOrder? {
            if (order.size != length) return null
            val seen = BooleanArray(length)
            for (i in order) {
                if (i !in 0 until length || seen[i]) return null
                seen[i] = true
            }
            return CriolShuffleOrder(order.toIntArray(), hints)
        }
    }
}
