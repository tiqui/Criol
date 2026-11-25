package app.criol.widget

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.media3.common.Player
import app.criol.Graph
import java.util.concurrent.Executors

/**
 * Mantém os widgets em dia com o player. Fica ligado ao ExoPlayer do PlaybackService,
 * salva o estado (para desenhar com o app fechado) e desenha em uma thread separada.
 */
object WidgetController {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var player: Player? = null
    private var appContext: Context? = null

    private val fullUpdate = Runnable { pushFull() }

    private val progressTicker = object : Runnable {
        override fun run() {
            val p = player ?: return
            val ctx = appContext ?: return
            if (!p.isPlaying) return
            val screenOn = (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
            if (screenOn) {
                val pos = p.currentPosition
                val dur = p.duration.takeIf { it > 0 } ?: 0
                executor.execute { if (WidgetRenderer.hasWidgets(ctx)) WidgetRenderer.renderProgress(ctx, pos, dur, true) }
            }
            main.postDelayed(this, ProgressBarRenderer.tickInterval(p.duration))
        }
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_REPEAT_MODE_CHANGED,
                    Player.EVENT_MEDIA_METADATA_CHANGED,
                    Player.EVENT_POSITION_DISCONTINUITY,
                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                )
            ) scheduleFull()
        }
    }

    /** Chamado pelo PlaybackService quando o player é criado. */
    fun attach(context: Context, p: Player) {
        appContext = context.applicationContext
        player = p
        p.addListener(listener)
        scheduleFull()
    }

    /** Chamado quando o serviço é destruído: os widgets ficam pausados com a última música. */
    fun detach() {
        val p = player ?: return
        main.removeCallbacks(fullUpdate)
        main.removeCallbacks(progressTicker)
        val ctx = appContext
        if (ctx != null) {
            val state = snapshot(p).copy(isPlaying = false)
            WidgetStore.save(ctx, state)
            executor.execute { WidgetRenderer.renderAll(ctx, state) }
        }
        p.removeListener(listener)
        player = null
    }

    /** Redesenha (favorito, aleatório ou tema mudaram, ou o sistema pediu). */
    fun refresh(context: Context) {
        appContext = appContext ?: context.applicationContext
        if (player != null) {
            scheduleFull()
            return
        }
        val ctx = context.applicationContext
        val saved = WidgetStore.load(ctx)
        val state = if (Graph.player.state.value.connected) saved.copy(shuffle = Graph.player.state.value.shuffled) else saved
        executor.execute { WidgetRenderer.renderAll(ctx, state) }
    }

    private fun scheduleFull() {
        main.removeCallbacks(fullUpdate)
        main.postDelayed(fullUpdate, 120)
    }

    private fun pushFull() {
        val p = player ?: return
        val ctx = appContext ?: return
        val state = snapshot(p)
        WidgetStore.save(ctx, state)
        executor.execute { if (WidgetRenderer.hasWidgets(ctx)) WidgetRenderer.renderAll(ctx, state) }
        main.removeCallbacks(progressTicker)
        if (p.isPlaying) main.postDelayed(progressTicker, ProgressBarRenderer.tickInterval(p.duration))
    }

    private fun snapshot(p: Player): WidgetState {
        val shuffle = Graph.player.state.value.shuffled
        val item = p.currentMediaItem ?: return WidgetState(repeatMode = p.repeatMode, shuffle = shuffle)
        val lib = Graph.library.current
        val song = lib.songsById[item.mediaId]
        val md = item.mediaMetadata
        return WidgetState(
            songId = item.mediaId,
            title = md.title?.toString() ?: song?.title ?: "",
            artist = md.artist?.toString() ?: song?.artist ?: "",
            artPath = md.artworkUri?.takeIf { it.scheme == "file" }?.path ?: lib.artPath(song?.albumId),
            isPlaying = p.isPlaying,
            positionMs = p.currentPosition.coerceAtLeast(0),
            durationMs = p.duration.takeIf { it > 0 } ?: song?.durationMs ?: 0,
            shuffle = shuffle,
            repeatMode = p.repeatMode,
        )
    }
}
