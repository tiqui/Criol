package app.criol.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.max

/** Desenha a barra de progresso fina dos widgets (trilha + parte tocada, cantos redondos). */
object ProgressBarRenderer {
    fun render(widthPx: Int, heightPx: Int, progress: Float, played: Int, track: Int): Bitmap {
        val w = widthPx.coerceAtLeast(1)
        val h = heightPx.coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val r = h / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = track
        canvas.drawRoundRect(0f, 0f, w.toFloat(), h.toFloat(), r, r, paint)
        val x = w * progress.coerceIn(0f, 1f)
        if (x > 0f) {
            paint.color = played
            canvas.drawRoundRect(0f, 0f, max(x, h.toFloat()), h.toFloat(), r, r, paint)
        }
        return bmp
    }

    /**
     * De quanto em quanto tempo a barra é redesenhada: ~90 passos por música,
     * entre 2 e 10 segundos (os tempos em texto andam sozinhos a cada segundo).
     */
    fun tickInterval(durationMs: Long): Long =
        if (durationMs <= 0) 5_000 else (durationMs / 90).coerceIn(2_000, 10_000)
}
