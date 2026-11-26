package app.criol.ui.theme

import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DaylightTest {
    private fun at(h: Int, m: Int = 0) = Daylight.nightAmount(h * 60 + m)

    @Test
    fun claroDeManhaEscuroANoite() {
        assertEquals(0f, at(8), 0f)
        assertEquals(0f, at(11, 59), 0f)
        assertEquals(1f, at(21), 0f)
        assertEquals(1f, at(3), 0f)
        assertEquals(0.5f, at(16), 0.001f)
        assertEquals(0.5f, at(6), 0.001f)
    }

    @Test
    fun escureceSempreDuranteATarde() {
        var last = -1f
        for (m in 12 * 60..20 * 60) {
            val t = Daylight.nightAmount(m)
            assertTrue("minuto $m", t >= last)
            last = t
        }
    }

    @Test
    fun pontasSeguemOTemaEscolhido() {
        val (day, night) = Daylight.pair("nord")
        assertEquals("default-light", day.id)
        assertEquals("nord", night.id)
        val (day2, night2) = Daylight.pair("catppuccin-latte")
        assertEquals("catppuccin-latte", day2.id)
        assertEquals("default-dark", night2.id)
    }

    @Test
    fun textoSempreContrastaComOFundo() {
        fun contrast(a: Float, b: Float) = (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
        // Nas pontas fica a cor do próprio tema; no meio do caminho o texto tem de ser legível.
        for (theme in Palettes) {
            val (day, night) = Daylight.pair(theme.id)
            for (step in 1..99) {
                val p = Daylight.blend(day, night, step / 100f)
                val bg = p.background.luminance()
                val text = contrast(bg, p.foreground.luminance())
                val muted = contrast(bg, p.foregroundMuted.luminance())
                assertTrue("${theme.id} passo $step: texto $text", text >= 4.5f)
                assertTrue("${theme.id} passo $step: secundário $muted", muted >= 3f)
            }
        }
        val (day, night) = Daylight.pair("default-dark")
        assertFalse(Daylight.blend(day, night, 0.2f).isDark)
        assertTrue(Daylight.blend(day, night, 0.8f).isDark)
    }
}
