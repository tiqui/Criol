package app.criol.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import app.criol.MainActivity
import app.criol.R

/**
 * Monta os RemoteViews dos três widgets com a cara do app: cores do tema escolhido,
 * play redondo como o do player, barra de progresso fina com os tempos, e nos widgets
 * maiores a capa desfocada de fundo. Cada widget é desenhado com o próprio tamanho.
 */
object WidgetRenderer {

    enum class Kind(
        val provider: Class<*>,
        val layout: Int,
        /** Tamanho usado se o launcher não informar (dp, retrato). */
        val fallbackW: Int,
        val fallbackH: Int,
        /** Margem interna lateral (dp) do layout. */
        val padding: Int,
        /** Tem capa desfocada, barra de progresso e controles completos. */
        val full: Boolean,
    ) {
        SMALL(CriolWidgetSmall::class.java, R.layout.widget_small, 250, 72, 10, false),
        MEDIUM(CriolWidgetMedium::class.java, R.layout.widget_medium, 250, 250, 16, true),
        LARGE(CriolWidgetLarge::class.java, R.layout.widget_large, 330, 330, 18, true),
    }

    private data class SizeDp(val width: Int, val height: Int)

    private const val CONTROL = 44
    private const val PLAY = 56
    private const val BAR_HEIGHT = 4
    private const val SMALL_PLAY = 40

    /** Largura útil mínima para anterior/próxima ao lado do play. */
    private const val THREE_FROM = CONTROL * 2 + PLAY

    /** Largura mínima para também caber aleatório e repetir. */
    private const val FIVE_FROM = CONTROL * 4 + PLAY + 48

    /** No 3x1, o botão de próxima só aparece a partir de 4 colunas. */
    private const val SMALL_NEXT_FROM = 300

    fun hasWidgets(context: Context): Boolean {
        val mgr = AppWidgetManager.getInstance(context)
        return Kind.entries.any { mgr.getAppWidgetIds(ComponentName(context, it.provider)).isNotEmpty() }
    }

    fun renderAll(context: Context, state: WidgetState) {
        val mgr = AppWidgetManager.getInstance(context)
        val look = WidgetLook.current()
        for (kind in Kind.entries) {
            for (id in mgr.getAppWidgetIds(ComponentName(context, kind.provider))) {
                runCatching { mgr.updateAppWidget(id, build(context, kind, state, look, sizeOf(mgr, id, kind))) }
            }
        }
    }

    /** Atualização leve: só a barra de progresso e os tempos. */
    fun renderProgress(context: Context, positionMs: Long, durationMs: Long, isPlaying: Boolean) {
        val mgr = AppWidgetManager.getInstance(context)
        val look = WidgetLook.current()
        for (kind in Kind.entries) {
            if (!kind.full) continue
            for (id in mgr.getAppWidgetIds(ComponentName(context, kind.provider))) {
                val views = RemoteViews(context.packageName, kind.layout)
                setProgress(context, views, kind, sizeOf(mgr, id, kind), positionMs, durationMs, isPlaying, look)
                runCatching { mgr.partiallyUpdateAppWidget(id, views) }
            }
        }
    }

    private fun sizeOf(mgr: AppWidgetManager, id: Int, kind: Kind): SizeDp {
        val options = mgr.getAppWidgetOptions(id)
        // Em retrato a largura é a mínima e a altura é a máxima.
        val w = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val h = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        return SizeDp(if (w > 0) w else kind.fallbackW, if (h > 0) h else kind.fallbackH)
    }

    private fun build(context: Context, kind: Kind, s: WidgetState, look: WidgetLook, size: SizeDp): RemoteViews {
        val v = RemoteViews(context.packageName, kind.layout)
        val hasSong = s.songId != null

        // Fundo: cor do tema; nos maiores, a capa desfocada por cima (os cantos
        // arredondados da imagem dependem do Android 12+).
        v.tint(R.id.wg_card, look.background)
        if (kind.full && Build.VERSION.SDK_INT >= 31) {
            v.setImageViewBitmap(R.id.wg_backdrop, Backdrop.render(s.artPath.takeIf { hasSong }, look))
            v.setViewVisibility(R.id.wg_backdrop, View.VISIBLE)
        }

        // Textos
        v.setTextViewText(R.id.wg_title, if (hasSong) s.title else context.getString(R.string.widget_idle_title))
        v.setTextViewText(R.id.wg_artist, if (hasSong) s.artist else context.getString(R.string.widget_idle_subtitle))
        v.setTextColor(R.id.wg_title, look.foreground)
        v.setTextColor(R.id.wg_artist, look.muted)

        // Capa (o launcher carrega pelo endereço; a capa vazia fica por baixo)
        v.tint(R.id.wg_art_tile, look.surface)
        v.tint(R.id.wg_art_note, look.muted)
        val artUri = if (hasSong) WidgetArtProvider.uriFor(context, s.artPath) else null
        if (artUri != null) {
            v.setImageViewUri(R.id.wg_art, artUri)
            v.setViewVisibility(R.id.wg_art, View.VISIBLE)
        } else {
            v.setViewVisibility(R.id.wg_art, View.GONE)
        }

        // Play/pause: círculo na cor do texto, ícone na cor do fundo (igual ao player)
        v.tint(R.id.wg_play_bg, look.foreground)
        v.tint(R.id.wg_play_icon, look.background)
        v.setImageViewResource(R.id.wg_play_icon, if (s.isPlaying) R.drawable.wg_pause else R.drawable.wg_play)
        v.setContentDescription(R.id.wg_play, if (s.isPlaying) "Pausar" else "Tocar")
        v.tint(R.id.wg_next, look.foreground)

        // Cliques
        val open = PendingIntent.getActivity(
            context, 1,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_PLAYER, hasSong)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        v.setOnClickPendingIntent(R.id.wg_root, open)
        v.setOnClickPendingIntent(R.id.wg_play, action(context, WidgetActions.PLAY_PAUSE))
        v.setOnClickPendingIntent(R.id.wg_next, action(context, WidgetActions.NEXT))

        if (kind.full) {
            val content = size.width - kind.padding * 2
            val skips = content >= THREE_FROM
            val flanked = content >= FIVE_FROM
            v.setViewVisibility(R.id.wg_prev, if (skips) View.VISIBLE else View.GONE)
            v.setViewVisibility(R.id.wg_next, if (skips) View.VISIBLE else View.GONE)
            v.setViewVisibility(R.id.wg_shuffle, if (flanked) View.VISIBLE else View.GONE)
            v.setViewVisibility(R.id.wg_repeat, if (flanked) View.VISIBLE else View.GONE)
            v.tint(R.id.wg_prev, look.foreground)

            // Ligados ficam na cor de destaque, como na barra do player.
            v.tint(R.id.wg_shuffle, if (s.shuffle) look.primary else look.muted)
            v.tint(R.id.wg_repeat, if (s.repeatMode != 0) look.primary else look.muted)
            v.setImageViewResource(R.id.wg_repeat, if (s.repeatMode == 1) R.drawable.wg_repeat_one_on else R.drawable.wg_repeat)
            v.setOnClickPendingIntent(R.id.wg_prev, action(context, WidgetActions.PREVIOUS))
            v.setOnClickPendingIntent(R.id.wg_shuffle, action(context, WidgetActions.SHUFFLE))
            v.setOnClickPendingIntent(R.id.wg_repeat, action(context, WidgetActions.REPEAT))

            v.setViewVisibility(R.id.wg_elapsed, if (hasSong) View.VISIBLE else View.INVISIBLE)
            v.setViewVisibility(R.id.wg_remaining, if (hasSong && s.durationMs > 0) View.VISIBLE else View.INVISIBLE)
            v.setTextColor(R.id.wg_elapsed, look.muted)
            v.setTextColor(R.id.wg_remaining, look.muted)
            v.setChronometerCountDown(R.id.wg_remaining, true)
            setProgress(context, v, kind, size, s.positionMs, s.durationMs, s.isPlaying, look)
        } else {
            v.setViewVisibility(R.id.wg_next, if (size.width >= SMALL_NEXT_FROM) View.VISIBLE else View.GONE)
            if (Build.VERSION.SDK_INT >= 31) {
                // Play acompanha a altura do widget (de 32 a 40dp).
                val button = (size.height - kind.padding * 2 - 8).coerceIn(32, SMALL_PLAY).toFloat()
                v.setViewLayoutWidth(R.id.wg_play, button, TypedValue.COMPLEX_UNIT_DIP)
                v.setViewLayoutHeight(R.id.wg_play, button, TypedValue.COMPLEX_UNIT_DIP)
            }
        }
        return v
    }

    /**
     * Barra desenhada com o progresso atual, e tempos em Chronometer: o próprio launcher
     * conta os segundos, então o texto anda sem o app precisar redesenhar.
     */
    private fun setProgress(
        context: Context,
        v: RemoteViews,
        kind: Kind,
        size: SizeDp,
        positionMs: Long,
        durationMs: Long,
        isPlaying: Boolean,
        look: WidgetLook,
    ) {
        val density = context.resources.displayMetrics.density
        val widthPx = ((size.width - kind.padding * 2).coerceAtLeast(40) * density).toInt()
        val heightPx = (BAR_HEIGHT * density).toInt()
        val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
        v.setImageViewBitmap(R.id.wg_bar, ProgressBarRenderer.render(widthPx, heightPx, progress, look.foreground, look.track))

        val now = SystemClock.elapsedRealtime()
        val pos = positionMs.coerceAtLeast(0)
        v.setChronometer(R.id.wg_elapsed, now - pos, null, isPlaying)
        val remaining = (durationMs - pos).coerceAtLeast(0)
        v.setChronometer(R.id.wg_remaining, now + remaining, "-%s", isPlaying && durationMs > 0)
    }

    /** Pinta um ImageView (funciona em qualquer Android, ao contrário dos tints remotos). */
    private fun RemoteViews.tint(id: Int, color: Int) = setInt(id, "setColorFilter", color)

    private fun action(context: Context, action: String): PendingIntent {
        val intent = Intent(context, WidgetActionReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context, action.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
