package app.criol

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.criol.data.LibraryRepository
import app.criol.data.QueueStore
import app.criol.data.SettingsStore
import app.criol.data.UserDataStore
import app.criol.insights.ListeningStore
import app.criol.insights.MixRepository
import app.criol.insights.ProfileStore
import app.criol.insights.RecapGenerator
import app.criol.playback.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import app.criol.widget.WidgetController

class CriolApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val settings = SettingsStore(this)
        val userData = UserDataStore(this, scope)
        val library = LibraryRepository(this, scope, settings)
        val listening = ListeningStore(this, scope)
        val profile = ProfileStore(this, scope)
        val player = PlayerController(this, library, userData, settings, QueueStore(this), listening, scope)
        val mixes = MixRepository(this, scope, library, userData, listening)
        val recaps = RecapGenerator(scope, listening, profile)
        Graph.init(settings, userData, library, player, listening, profile, mixes, recaps)
        library.init()
        listening.init()

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = recaps.check()
            override fun onStop(owner: LifecycleOwner) = player.saveSnapshot()
        })

        // Widgets: redesenha quando muda o aleatório, o tema ou a cor de destaque.
        val refreshWidgets: suspend (Any?) -> Unit = { WidgetController.refresh(this) }
        scope.launch { player.state.map { it.shuffled }.distinctUntilChanged().drop(1).collect(refreshWidgets) }
        scope.launch {
            settings.settings.map { Triple(it.themeId, it.accent, it.themeFollowsDay) }.distinctUntilChanged().drop(1).collect(refreshWidgets)
        }
        // Tema seguindo o dia: enquanto o app está vivo, os widgets acompanham a cada 15 min.
        scope.launch {
            while (true) {
                delay(15 * 60_000L)
                if (settings.value.themeFollowsDay) WidgetController.refresh(this@CriolApp)
            }
        }
    }
}

/** Dependências globais do app (app pessoal, sem framework de DI). */
object Graph {
    lateinit var settings: SettingsStore
        private set
    lateinit var userData: UserDataStore
        private set
    lateinit var library: LibraryRepository
        private set
    lateinit var player: PlayerController
        private set
    lateinit var listening: ListeningStore
        private set
    lateinit var profile: ProfileStore
        private set
    lateinit var mixes: MixRepository
        private set
    lateinit var recaps: RecapGenerator
        private set

    fun init(
        s: SettingsStore,
        u: UserDataStore,
        l: LibraryRepository,
        p: PlayerController,
        listening: ListeningStore,
        profile: ProfileStore,
        mixes: MixRepository,
        recaps: RecapGenerator,
    ) {
        settings = s
        userData = u
        library = l
        player = p
        this.listening = listening
        this.profile = profile
        this.mixes = mixes
        this.recaps = recaps
    }
}
