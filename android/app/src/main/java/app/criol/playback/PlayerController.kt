package app.criol.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.criol.data.LibraryRepository
import app.criol.data.QueueSnapshot
import app.criol.data.QueueStore
import app.criol.data.SettingsStore
import app.criol.data.Song
import app.criol.data.UserDataStore
import app.criol.insights.ListenEntry
import app.criol.insights.ListeningStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import app.criol.data.albumCountLabel
import app.criol.data.songCountLabel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class PlayerState(
    val connected: Boolean = false,
    val queue: List<Song> = emptyList(),
    val index: Int = -1,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val durationMs: Long = 0,
    val shuffled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val speed: Float = 1f,
    val sleepTimerEnd: Long? = null,
    val sleepAtEndOfTrack: Boolean = false,
    /** Música-semente do "Rádio da música" tocando agora (null = fila comum). */
    val radio: Song? = null,
) {
    val current: Song? get() = queue.getOrNull(index)
    val hasNext: Boolean get() = index < queue.size - 1 || repeatMode != Player.REPEAT_MODE_OFF
}

/**
 * Ponte entre a UI e o [PlaybackService]. Mantém a fila espelhada em [state],
 * conta reproduções, salva/restaura a fila e controla o timer de sono.
 */
class PlayerController(
    private val context: Context,
    private val library: LibraryRepository,
    private val userData: UserDataStore,
    private val settings: SettingsStore,
    private val queueStore: QueueStore,
    private val listening: ListeningStore,
    private val scope: CoroutineScope,
) {
    private var controller: MediaController? = null
    private var connecting = false
    private val pending = mutableListOf<(MediaController) -> Unit>()

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()

    private var originalOrder: List<String> = emptyList()
    private var shuffled = false
    private var radioSeed: Song? = null
    private var counted = false
    private var listenedMs = 0L
    private var lastTickPos = -1L
    private var lastSave = 0L

    // Escuta em andamento para o histórico (perfil, daylist e mixes).
    private var sessionSong: Song? = null
    private var sessionStart = 0L
    private var sessionId = java.util.UUID.randomUUID().toString()

    fun connect() {
        if (controller != null || connecting) return
        connecting = true
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            connecting = false
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(listener)
            _state.update { it.copy(connected = true) }
            refreshQueue()
            refreshFlags()
            val restore = restoreIfNeeded(c)
            restoreJob = restore
            // Comandos feitos antes da conexão (ex.: botão do widget com o app fechado)
            // só rodam depois que a fila salva foi restaurada.
            scope.launch {
                restore.join()
                pending.toList().forEach { it(c) }
                pending.clear()
            }
        }, ContextCompat.getMainExecutor(context))

        scope.launch { ticker() }
        // Atualiza as faixas da fila quando a biblioteca é reescaneada.
        scope.launch { library.library.drop(1).collect { refreshQueue() } }
    }

    private var restoreJob: Job? = null

    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        val restoring = restoreJob?.takeIf { it.isActive }
        when {
            c == null -> {
                pending += block
                connect()
            }
            restoring != null -> scope.launch { restoring.join(); block(c) }
            else -> block(c)
        }
    }

    // ------------------------------------------------------------------
    // Estado
    // ------------------------------------------------------------------

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                refreshQueue()
            }
            refreshFlags()
            if (events.contains(Player.EVENT_REPEAT_MODE_CHANGED)) maybeAutoDj()
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED) && !player.isPlaying) saveSnapshot()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // Também dispara ao repetir a mesma faixa: cada volta é uma escuta.
            commitListen()
            counted = false
            listenedMs = 0
            lastTickPos = -1
            _position.value = 0
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && _state.value.sleepAtEndOfTrack) {
                controller?.pause()
                _state.update { it.copy(sleepAtEndOfTrack = false) }
            }
            saveSnapshot()
        }
    }

    private fun refreshQueue() {
        val c = controller ?: return
        val lib = library.current
        val songs = (0 until c.mediaItemCount).map { i ->
            val item = c.getMediaItemAt(i)
            lib.songsById[item.mediaId] ?: stubSong(item)
        }
        _state.update { it.copy(queue = songs, index = c.currentMediaItemIndex) }
        maybeAutoDj()
    }

    // ------------------------------------------------------------------
    // Auto DJ
    // ------------------------------------------------------------------

    private val _autoDjEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** Mensagens para avisar o usuário quando o Auto DJ adiciona músicas. */
    val autoDjEvents: SharedFlow<String> = _autoDjEvents.asSharedFlow()
    private var autoDjBusy = false
    private var autoDjLastKey: String? = null

    private fun maybeAutoDj() {
        val s = settings.value
        val radio = radioSeed
        if ((radio == null && !s.autoDjEnabled) || autoDjBusy) return
        val c = controller ?: return
        val st = _state.value
        val current = st.current ?: return
        if (c.repeatMode == Player.REPEAT_MODE_ONE) return
        val remaining = st.queue.size - st.index - 1
        val refillAt = if (radio != null) SongRadio.REFILL_AT else s.autoDjTiming.coerceAtLeast(1)
        if (remaining >= refillAt) return
        // Evita disparar de novo para a mesma situação (ex.: biblioteca sem mais opções).
        val key = "${current.id}:${st.queue.size}:${st.index}"
        if (key == autoDjLastKey) return
        autoDjLastKey = key
        autoDjBusy = true
        if (radio != null) {
            extendRadio(radio, st)
            return
        }
        scope.launch {
            try {
                val lib = library.current
                val opt = AutoDj.Options(
                    mode = s.autoDjMode,
                    strategy = s.autoDjStrategy,
                    count = s.autoDjCount,
                    allowDuplicates = s.autoDjAllowDuplicates,
                    onlySimilar = s.autoDjOnlySimilar,
                )
                val (songs, label) = withContext(Dispatchers.Default) {
                    val dj = AutoDj(lib, userData.value)
                    if (opt.mode == AutoDj.MODE_ALBUMS) {
                        val albums = dj.pickAlbums(current, st.queue.mapTo(HashSet()) { it.albumId }, opt)
                        albums.flatMap { it.songs } to albumCountLabel(albums.size)
                    } else {
                        val picked = dj.pickSongs(current, st.queue.mapTo(HashSet()) { it.id }, opt)
                        picked to songCountLabel(picked.size)
                    }
                }
                if (songs.isNotEmpty() && settings.value.autoDjEnabled) {
                    addToQueue(songs)
                    _autoDjEvents.tryEmit("Auto DJ adicionou $label")
                }
            } finally {
                autoDjBusy = false
            }
        }
    }

    // ------------------------------------------------------------------
    // Rádio da música
    // ------------------------------------------------------------------

    /** Toca a música e, depois dela, uma fila de parecidas que se renova sozinha. */
    fun startRadio(seed: Song) {
        scope.launch {
            val lib = library.current
            val base = lib.songsById[seed.id] ?: seed
            val songs = withContext(Dispatchers.Default) {
                SongRadio(lib, userData.value, listening.entries.value).build(base, SongRadio.START_SIZE)
            }
            play(listOf(base) + songs, 0, shuffle = false, radio = base)
        }
    }

    /** Volta a ser uma fila comum (o que já está nela continua). */
    fun stopRadio() {
        setRadio(null)
        saveSnapshot()
    }

    private fun setRadio(seed: Song?) {
        radioSeed = seed
        autoDjLastKey = null
        _state.update { it.copy(radio = seed) }
    }

    private fun extendRadio(seed: Song, st: PlayerState) {
        scope.launch {
            try {
                val lib = library.current
                val recent = st.queue.subList((st.index - 4).coerceAtLeast(0), st.index + 1).toList()
                val exclude = st.queue.mapTo(HashSet()) { it.id }
                val more = withContext(Dispatchers.Default) {
                    SongRadio(lib, userData.value, listening.entries.value)
                        .build(seed, SongRadio.REFILL_SIZE, exclude, recent)
                }
                // O usuário pode ter trocado de fila enquanto calculava.
                if (more.isNotEmpty() && radioSeed?.id == seed.id) addToQueue(more)
            } finally {
                autoDjBusy = false
            }
        }
    }

    fun setAutoDj(enabled: Boolean) {
        settings.update { it.copy(autoDjEnabled = enabled) }
        autoDjLastKey = null
        if (enabled) maybeAutoDj()
    }

    private fun refreshFlags() {
        val c = controller ?: return
        val current = _state.value.queue.getOrNull(c.currentMediaItemIndex)
        val dur = c.duration.takeIf { it > 0 } ?: current?.durationMs ?: 0
        _state.update {
            it.copy(
                index = c.currentMediaItemIndex,
                isPlaying = c.isPlaying,
                isBuffering = c.playbackState == Player.STATE_BUFFERING,
                durationMs = dur,
                shuffled = shuffled,
                repeatMode = c.repeatMode,
                speed = c.playbackParameters.speed,
            )
        }
    }

    private fun stubSong(item: MediaItem): Song {
        val md = item.mediaMetadata
        return Song(
            id = item.mediaId, uri = item.localConfiguration?.uri?.toString() ?: "", rootUri = "",
            title = md.title?.toString() ?: "Desconhecido", artist = md.artist?.toString() ?: "",
            albumArtist = "", album = md.albumTitle?.toString() ?: "", albumId = "", genre = "",
            year = 0, track = 0, disc = 0, durationMs = 0, folder = "", fileName = "", size = 0,
            lastModified = 0, dateAdded = 0, mime = "", bitrate = 0,
        )
    }

    private suspend fun ticker() {
        while (true) {
            val c = controller
            if (c != null) {
                val pos = c.currentPosition
                _position.value = pos
                if (c.isPlaying) {
                    if (lastTickPos >= 0) {
                        val delta = pos - lastTickPos
                        if (delta in 1..3000) listenedMs += delta
                    }
                    val song = _state.value.current?.takeIf { it.id == c.currentMediaItem?.mediaId }
                    if (song != null && sessionSong == null) {
                        sessionSong = song
                        sessionStart = System.currentTimeMillis() - listenedMs
                    }
                    val dur = _state.value.durationMs
                    val threshold = if (dur > 0) (dur / 2).coerceIn(10_000, 240_000) else 30_000
                    if (!counted && song != null && listenedMs >= threshold) {
                        counted = true
                        userData.registerPlay(song.id)
                    }
                    if (System.currentTimeMillis() - lastSave > 15_000) saveSnapshot()
                }
                lastTickPos = pos
                val end = _state.value.sleepTimerEnd
                if (end != null && System.currentTimeMillis() >= end) {
                    c.pause()
                    _state.update { it.copy(sleepTimerEnd = null) }
                }
                if (_state.value.durationMs <= 0 && c.duration > 0) refreshFlags()
            }
            delay(if (c?.isPlaying == true) 250 else 700)
        }
    }

    // ------------------------------------------------------------------
    // Persistência da fila
    // ------------------------------------------------------------------

    /** Fecha a escuta atual e manda para o histórico (se foi longa o bastante). */
    private fun commitListen() {
        val song = sessionSong
        val ms = listenedMs
        val id = sessionId
        sessionSong = null
        sessionId = java.util.UUID.randomUUID().toString()
        if (song == null || ms < ListenEntry.MIN_STORED_MS || !settings.value.recordHistory) {
            listening.setPending(null)
            return
        }
        listening.add(ListenEntry.from(song, ms, sessionStart, id))
    }

    /** Guarda a escuta em andamento, para não perdê-la se o sistema fechar o app. */
    private fun savePendingListen() {
        val song = sessionSong ?: return
        if (listenedMs < ListenEntry.MIN_STORED_MS || !settings.value.recordHistory) return
        listening.setPending(ListenEntry.from(song, listenedMs, sessionStart, sessionId))
    }

    fun saveSnapshot() {
        val c = controller ?: return
        lastSave = System.currentTimeMillis()
        savePendingListen()
        val snap = QueueSnapshot(
            songIds = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).mediaId },
            index = c.currentMediaItemIndex,
            positionMs = c.currentPosition,
            shuffled = shuffled,
            originalOrder = originalOrder,
            repeatMode = c.repeatMode,
            speed = c.playbackParameters.speed,
            radioSeedId = radioSeed?.id,
        )
        scope.launch(Dispatchers.IO) { queueStore.save(snap) }
    }

    private fun restoreIfNeeded(c: MediaController): Job =
        scope.launch {
            library.loaded.first { it }
            if (c.mediaItemCount > 0) {
                refreshQueue()
                return@launch
            }
            val snap = withContext(Dispatchers.IO) { queueStore.load() } ?: return@launch
            c.repeatMode = snap.repeatMode
            if (snap.speed > 0) c.playbackParameters = PlaybackParameters(snap.speed)
            if (!settings.value.resumeQueue) return@launch
            val lib = library.current
            val pairs = snap.songIds.withIndex().mapNotNull { (i, id) -> lib.songsById[id]?.let { i to it } }
            if (pairs.isEmpty()) return@launch
            val newIndex = pairs.indexOfFirst { it.first >= snap.index }.coerceAtLeast(0)
            val samePos = pairs.getOrNull(newIndex)?.first == snap.index
            shuffled = snap.shuffled
            originalOrder = snap.originalOrder.filter { it in lib.songsById }
            snap.radioSeedId?.let { lib.songsById[it] }?.let(::setRadio)
            c.setMediaItems(pairs.map { it.second.toMediaItem() }, newIndex, if (samePos) snap.positionMs else 0)
            c.prepare()
            refreshQueue()
            refreshFlags()
        }

    // ------------------------------------------------------------------
    // Comandos
    // ------------------------------------------------------------------

    fun playSongs(songs: List<Song>, startIndex: Int = 0, shuffle: Boolean = false) =
        play(songs, startIndex, shuffle, radio = null)

    private fun play(songs: List<Song>, startIndex: Int, shuffle: Boolean, radio: Song?) {
        if (songs.isEmpty()) return
        withController { c ->
            setRadio(radio)
            val list: List<Song>
            val start: Int
            if (shuffle) {
                list = songs.shuffled()
                start = 0
                originalOrder = songs.map { it.id }
                shuffled = true
            } else {
                list = songs
                start = startIndex.coerceIn(0, songs.lastIndex)
                originalOrder = emptyList()
                shuffled = false
            }
            c.setMediaItems(list.map { it.toMediaItem() }, start, 0)
            c.prepare()
            c.play()
            refreshFlags()
        }
    }

    fun shuffleAll() = playSongs(library.current.songs, shuffle = true)

    fun togglePlayPause() = withController { c ->
        when {
            c.mediaItemCount == 0 -> return@withController
            c.playbackState == Player.STATE_IDLE -> { c.prepare(); c.play() }
            c.playbackState == Player.STATE_ENDED -> { c.seekTo(0, 0); c.play() }
            c.isPlaying -> c.pause()
            else -> c.play()
        }
    }

    fun next() = withController { it.seekToNext() }

    fun previous() = withController { it.seekToPrevious() }

    fun seekTo(ms: Long) = withController {
        it.seekTo(ms.coerceAtLeast(0))
        _position.value = ms
    }

    fun skipBack() = withController { c ->
        seekTo(c.currentPosition - settings.value.skipBackSeconds * 1000L)
    }

    fun skipForward() = withController { c ->
        val target = c.currentPosition + settings.value.skipForwardSeconds * 1000L
        if (c.duration > 0 && target >= c.duration) c.seekToNext() else seekTo(target)
    }

    fun playAt(index: Int) = withController { c ->
        if (index in 0 until c.mediaItemCount) {
            c.seekTo(index, 0)
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun playNext(songs: List<Song>) {
        if (songs.isEmpty()) return
        withController { c ->
            if (c.mediaItemCount == 0) {
                playSongs(songs); return@withController
            }
            c.addMediaItems(c.currentMediaItemIndex + 1, songs.map { it.toMediaItem() })
            if (shuffled) {
                val curId = c.currentMediaItem?.mediaId
                val pos = originalOrder.indexOf(curId)
                originalOrder = originalOrder.toMutableList().apply {
                    addAll(if (pos >= 0) pos + 1 else size, songs.map { it.id })
                }
            }
        }
    }

    fun addToQueue(songs: List<Song>) {
        if (songs.isEmpty()) return
        withController { c ->
            if (c.mediaItemCount == 0) {
                playSongs(songs); return@withController
            }
            c.addMediaItems(songs.map { it.toMediaItem() })
            if (shuffled) originalOrder = originalOrder + songs.map { it.id }
        }
    }

    fun removeAt(index: Int) = withController { c ->
        if (index !in 0 until c.mediaItemCount) return@withController
        val id = c.getMediaItemAt(index).mediaId
        c.removeMediaItem(index)
        if (shuffled) {
            val i = originalOrder.indexOf(id)
            if (i >= 0) originalOrder = originalOrder.toMutableList().apply { removeAt(i) }
        }
    }

    fun move(from: Int, to: Int) = withController { c ->
        if (from == to || from !in 0 until c.mediaItemCount || to !in 0 until c.mediaItemCount) return@withController
        c.moveMediaItem(from, to)
    }

    fun clearQueue() = withController { c ->
        c.stop()
        c.clearMediaItems()
        setRadio(null)
        shuffled = false
        originalOrder = emptyList()
        refreshFlags()
        saveSnapshot()
    }

    /** Embaralha fisicamente a fila (a atual vai para o topo) ou restaura a ordem original. */
    fun toggleShuffle() = withController { c ->
        val count = c.mediaItemCount
        val idx = c.currentMediaItemIndex
        val items = (0 until count).map { c.getMediaItemAt(it) }
        if (!shuffled) {
            originalOrder = items.map { it.mediaId }
            shuffled = true
            if (count > 1 && idx >= 0) {
                val rest = items.filterIndexed { i, _ -> i != idx }.shuffled()
                if (idx + 1 < count) c.removeMediaItems(idx + 1, count)
                if (idx > 0) c.removeMediaItems(0, idx)
                c.addMediaItems(rest)
            }
        } else {
            shuffled = false
            if (count > 1 && idx >= 0) {
                val byId = items.groupBy { it.mediaId }.mapValues { it.value.toMutableList() }
                val remaining = items.map { it.mediaId }.groupingBy { it }.eachCount().toMutableMap()
                val ordered = mutableListOf<String>()
                for (id in originalOrder) {
                    val n = remaining[id] ?: 0
                    if (n > 0) { ordered += id; remaining[id] = n - 1 }
                }
                for (item in items) {
                    val n = remaining[item.mediaId] ?: 0
                    if (n > 0) { ordered += item.mediaId; remaining[item.mediaId] = n - 1 }
                }
                val currentId = items[idx].mediaId
                val pos = ordered.indexOf(currentId)
                val pool = byId.mapValues { it.value.toMutableList() }
                pool[currentId]?.removeAt(0)
                fun take(id: String) = pool[id]?.removeFirstOrNull() ?: items.first { it.mediaId == id }
                val before = ordered.subList(0, pos).map(::take)
                val after = ordered.subList(pos + 1, ordered.size).map(::take)
                if (idx + 1 < count) c.removeMediaItems(idx + 1, count)
                if (idx > 0) c.removeMediaItems(0, idx)
                if (before.isNotEmpty()) c.addMediaItems(0, before)
                if (after.isNotEmpty()) c.addMediaItems(after)
            }
            originalOrder = emptyList()
        }
        refreshFlags()
        saveSnapshot()
    }

    fun cycleRepeat() = withController { c ->
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun setSpeed(speed: Float) = withController { it.playbackParameters = PlaybackParameters(speed) }

    fun setSleepTimer(minutes: Int) {
        _state.update {
            it.copy(sleepTimerEnd = System.currentTimeMillis() + minutes * 60_000L, sleepAtEndOfTrack = false)
        }
    }

    fun setSleepAtEndOfTrack() {
        _state.update { it.copy(sleepTimerEnd = null, sleepAtEndOfTrack = true) }
    }

    fun cancelSleepTimer() {
        _state.update { it.copy(sleepTimerEnd = null, sleepAtEndOfTrack = false) }
    }

    private fun Song.toMediaItem(): MediaItem {
        val art = library.current.artPath(albumId)?.let { Uri.fromFile(File(it)) }
        val fileUri = Uri.parse(uri)
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(fileUri)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(fileUri).build())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setAlbumArtist(albumArtist.ifBlank { null })
                    .setArtworkUri(art)
                    .setTrackNumber(track.takeIf { it > 0 })
                    .setDiscNumber(disc.takeIf { it > 0 })
                    .setReleaseYear(year.takeIf { it > 0 })
                    .setGenre(genre.ifBlank { null })
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .build(),
            )
            .build()
    }
}
