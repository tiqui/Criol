package app.criol.widget

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.util.LruCache
import androidx.core.graphics.ColorUtils
import java.io.File

/**
 * Fundo dos widgets 3x3 e 4x4: a capa bem desfocada, com um véu na cor de fundo do
 * tema por cima (como o fundo dinâmico do player), para o texto ficar legível com
 * qualquer capa. É uma imagem pequena: desfocada não tem detalhe, então o launcher
 * amplia sem perder nada, e ela passa pelo sistema sem pesar.
 */
object Backdrop {
    private const val SIZE = 128
    private const val RADIUS = 10

    private val cache = LruCache<String, Bitmap>(3)

    fun render(artPath: String?, look: WidgetLook): Bitmap {
        val file = artPath?.let(::File)?.takeIf { it.isFile }
        val key = "${file?.path}|${file?.lastModified()}|${look.background}|${look.primary}"
        cache.get(key)?.let { return it }

        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val full = SIZE.toFloat()
        val art = file?.let(::decode)
        if (art != null) {
            canvas.drawBitmap(art, null, Rect(0, 0, SIZE, SIZE), Paint(Paint.FILTER_BITMAP_FLAG))
            art.recycle()
            blur(out, RADIUS)
        } else {
            // Sem capa: um brilho da cor de destaque vindo de cima.
            canvas.drawColor(look.background)
            val glow = Paint().apply {
                shader = RadialGradient(
                    full * 0.5f, 0f, full,
                    ColorUtils.setAlphaComponent(look.primary, 0x59), Color.TRANSPARENT,
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawRect(0f, 0f, full, full, glow)
        }
        val scrim = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, full,
                ColorUtils.setAlphaComponent(look.background, 0x8C),
                ColorUtils.setAlphaComponent(look.background, 0xD9),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, full, full, scrim)
        cache.put(key, out)
        return out
    }

    private fun decode(file: File): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= SIZE && bounds.outHeight / (sample * 2) >= SIZE) sample *= 2
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    /** Três passadas de média (horizontal e vertical): fica quase igual a um desfoque gaussiano. */
    private fun blur(bmp: Bitmap, radius: Int) {
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        val tmp = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        repeat(3) {
            boxPass(px, tmp, lines = h, len = w, lineStep = w, step = 1, r = radius)
            boxPass(tmp, px, lines = w, len = h, lineStep = 1, step = w, r = radius)
        }
        bmp.setPixels(px, 0, w, 0, 0, w, h)
    }

    private fun boxPass(src: IntArray, dst: IntArray, lines: Int, len: Int, lineStep: Int, step: Int, r: Int) {
        val div = 2 * r + 1
        for (line in 0 until lines) {
            val start = line * lineStep
            var sr = 0
            var sg = 0
            var sb = 0
            for (i in -r..r) {
                val c = src[start + i.coerceIn(0, len - 1) * step]
                sr += c shr 16 and 0xFF
                sg += c shr 8 and 0xFF
                sb += c and 0xFF
            }
            for (i in 0 until len) {
                dst[start + i * step] = (0xFF shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                val add = src[start + (i + r + 1).coerceAtMost(len - 1) * step]
                val rem = src[start + (i - r).coerceAtLeast(0) * step]
                sr += (add shr 16 and 0xFF) - (rem shr 16 and 0xFF)
                sg += (add shr 8 and 0xFF) - (rem shr 8 and 0xFF)
                sb += (add and 0xFF) - (rem and 0xFF)
            }
        }
    }
}
