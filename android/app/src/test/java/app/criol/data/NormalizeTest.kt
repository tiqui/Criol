package app.criol.data

import org.junit.Assert.assertEquals
import org.junit.Test

class NormalizeTest {
    @Test
    fun asciiAndAccentedTextNormalizeTheSameWayAsBefore() {
        assertEquals("ac/dc", "AC/DC".normalizeForSearch())
        assertEquals("rock and roll", "Rock and Roll".normalizeForSearch())
        assertEquals("acao", "Ação".normalizeForSearch())
        assertEquals("joao gilberto", "João Gilberto".normalizeForSearch())
        assertEquals("musica popular brasileira", "Música Popular Brasileira".normalizeForSearch())
        // Segunda chamada vem do cache e precisa dar o mesmo resultado.
        assertEquals("acao", "Ação".normalizeForSearch())
        assertEquals("", "".normalizeForSearch())
    }
}
