package app.criol.playback

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CriolShuffleOrderTest {
    private val hints = ShuffleHints()

    private fun walk(o: androidx.media3.exoplayer.source.ShuffleOrder): List<Int> {
        val out = mutableListOf<Int>()
        var i = o.firstIndex
        while (i != C.INDEX_UNSET) { out += i; i = o.getNextIndex(i) }
        return out
    }

    private fun isPermutation(list: List<Int>, n: Int) = list.size == n && list.sorted() == (0 until n).toList()

    @Test
    fun currentSongPlaysFirstAndEverythingPlaysOnce() {
        val o = CriolShuffleOrder.currentFirst(20, 7, hints, Random(1))
        val seq = walk(o)
        assertEquals(7, seq.first())
        assertTrue(isPermutation(seq, 20))
        assertEquals(seq.last(), o.lastIndex)
        assertEquals(seq[3], o.getPreviousIndex(seq[4]))
    }

    @Test
    fun playNextGoesRightAfterCurrentAndAddToQueueGoesLast() {
        val base = CriolShuffleOrder(intArrayOf(3, 0, 4, 1, 2), hints, Random(2))
        // Tocando o físico 4 (terceiro na ordem). "Tocar a seguir" insere 2 itens em 5 e 6.
        hints.current.set(4)
        hints.nextInsert.set(ShuffleHints.INSERT_NEXT)
        val next = base.cloneAndInsert(5, 2)
        assertEquals(listOf(3, 0, 4, 5, 6, 1, 2), walk(next))

        // "Adicionar à fila" no fim da fila física, com a atual também no fim dela.
        hints.current.set(6)
        hints.nextInsert.set(ShuffleHints.INSERT_END)
        val end = next.cloneAndInsert(7, 1)
        assertEquals(listOf(3, 0, 4, 5, 6, 1, 2, 7), walk(end))
        // A dica vale só uma vez.
        assertEquals(ShuffleHints.INSERT_RANDOM, hints.nextInsert.get())
    }

    @Test
    fun insertBeforeCurrentShiftsIndicesAndRandomStaysAfterCurrent() {
        val base = CriolShuffleOrder(intArrayOf(2, 0, 1), hints, Random(3))
        hints.current.set(0)
        val o = base.cloneAndInsert(0, 2) // físicos antigos 0,1,2 viram 2,3,4
        val seq = walk(o)
        assertTrue(isPermutation(seq, 5))
        assertEquals(listOf(4, 2), seq.take(2)) // antes da atual nada muda
        assertTrue(seq.indexOf(0) > seq.indexOf(2) && seq.indexOf(1) > seq.indexOf(2))
    }

    @Test
    fun removeAndMoveKeepEveryoneInTheirTurn() {
        val base = CriolShuffleOrder(intArrayOf(3, 0, 4, 1, 2), hints)
        // Remove os físicos 1 e 2: sobram 0, 3->1, 4->2.
        assertEquals(listOf(1, 0, 2), walk(base.cloneAndRemove(1, 3)))

        // Move o físico 0 para a posição 4: 1..4 descem um, 0 vira 4.
        val moved = walk(base.cloneAndMove(0, 1, 4))
        assertEquals(listOf(2, 4, 3, 0, 1), moved)
    }

    @Test
    fun setAndRestore() {
        val set = walk(CriolShuffleOrder(IntArray(0), hints, Random(4)).cloneAndSet(10, 6))
        assertEquals(6, set.first())
        assertTrue(isPermutation(set, 10))
        assertEquals(listOf(2, 0, 1), walk(CriolShuffleOrder.fromOrder(listOf(2, 0, 1), 3, hints)!!))
        assertNull(CriolShuffleOrder.fromOrder(listOf(2, 2, 1), 3, hints))
        assertNull(CriolShuffleOrder.fromOrder(listOf(0, 1), 3, hints))
        assertEquals(C.INDEX_UNSET, CriolShuffleOrder(IntArray(0), hints).firstIndex)
    }
}
