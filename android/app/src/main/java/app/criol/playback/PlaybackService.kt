package app.criol.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import app.criol.Graph
import app.criol.MainActivity
import app.criol.widget.WidgetController
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val hints = ShuffleHints()

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        // Aleatório dentro do player: a fila física fica como está, só a ordem de tocar muda.
        player.setShuffleOrder(CriolShuffleOrder(IntArray(0), hints))
        player.addListener(object : Player.Listener {
            override fun onEvents(p: Player, events: Player.Events) {
                hints.current.set(p.currentMediaItemIndex)
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                // Vale também para o botão da notificação, do widget e do fone.
                if (shuffleModeEnabled) reshuffle(player)
            }
        })

        val intent = Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_PLAYER, true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        session = MediaSession.Builder(this, player)
            .setSessionActivity(pending)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                ): MediaSession.ConnectionResult {
                    val builder = MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    // Os comandos próprios do Criol só valem para o próprio app.
                    if (controller.packageName == packageName) {
                        builder.setAvailableSessionCommands(
                            MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                                .add(SessionCommand(CMD_RESHUFFLE, Bundle.EMPTY))
                                .add(SessionCommand(CMD_SET_SHUFFLE_ORDER, Bundle.EMPTY))
                                .add(SessionCommand(CMD_INSERT_HINT, Bundle.EMPTY))
                                .build(),
                        )
                    }
                    return builder.build()
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle,
                ): ListenableFuture<SessionResult> {
                    when (customCommand.customAction) {
                        CMD_RESHUFFLE -> reshuffle(player)
                        CMD_SET_SHUFFLE_ORDER -> {
                            val order = args.getIntArray(ARG_ORDER)?.toList().orEmpty()
                            CriolShuffleOrder.fromOrder(order, player.mediaItemCount, hints)
                                ?.let(player::setShuffleOrder)
                        }
                        CMD_INSERT_HINT -> hints.nextInsert.set(args.getInt(ARG_INSERT, ShuffleHints.INSERT_RANDOM))
                        else -> return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }

                // O controller manda só id + URI; aqui o item ganha título, artista, capa etc.
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>,
                ): ListenableFuture<MutableList<MediaItem>> =
                    Futures.immediateFuture(mediaItems.map(::resolve).toMutableList())
            })
            .build()

        WidgetController.attach(this, player)
    }

    @OptIn(UnstableApi::class)
    private fun reshuffle(player: ExoPlayer) {
        player.setShuffleOrder(CriolShuffleOrder.currentFirst(player.mediaItemCount, player.currentMediaItemIndex, hints))
    }

    private fun resolve(item: MediaItem): MediaItem {
        val lib = Graph.library.current
        lib.songsById[item.mediaId]?.let { return it.toPlayableMediaItem(lib) }
        if (item.localConfiguration != null) return item
        val uri = item.requestMetadata.mediaUri ?: return item
        return item.buildUpon().setUri(uri).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        WidgetController.detach()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    companion object {
        /** Sorteia de novo, com a música atual primeiro. */
        const val CMD_RESHUFFLE = "app.criol.RESHUFFLE"

        /** Define a ordem aleatória exata (fila restaurada ou item arrastado na fila). */
        const val CMD_SET_SHUFFLE_ORDER = "app.criol.SET_SHUFFLE_ORDER"

        /** Onde a próxima inserção entra no aleatório (a seguir ou no fim). */
        const val CMD_INSERT_HINT = "app.criol.INSERT_HINT"
        const val ARG_ORDER = "order"
        const val ARG_INSERT = "insert"
    }
}
