package app.criol.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import app.criol.Graph

/** Base dos três widgets: desenha a partir do estado salvo/atual. */
abstract class CriolWidgetBase : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetController.refresh(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        WidgetController.refresh(context)
    }
}

/** 3x1 */
class CriolWidgetSmall : CriolWidgetBase()

/** 3x3 */
class CriolWidgetMedium : CriolWidgetBase()

/** 4x4 */
class CriolWidgetLarge : CriolWidgetBase()

object WidgetActions {
    const val PLAY_PAUSE = "app.criol.widget.PLAY_PAUSE"
    const val NEXT = "app.criol.widget.NEXT"
    const val PREVIOUS = "app.criol.widget.PREVIOUS"
    const val SHUFFLE = "app.criol.widget.SHUFFLE"
    const val REPEAT = "app.criol.widget.REPEAT"
}

/**
 * Recebe os toques nos botões dos widgets. Se o app estava fechado, o controlador
 * se conecta ao serviço e restaura a fila salva antes de executar o comando.
 */
class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val player = Graph.player
        when (intent.action) {
            WidgetActions.PLAY_PAUSE -> player.togglePlayPause()
            WidgetActions.NEXT -> player.next()
            WidgetActions.PREVIOUS -> player.previous()
            WidgetActions.SHUFFLE -> player.toggleShuffle()
            WidgetActions.REPEAT -> player.cycleRepeat()
        }
        // Dá tempo para a conexão com o serviço e a restauração da fila.
        Handler(Looper.getMainLooper()).postDelayed({ pending.finish() }, 4000)
    }
}
