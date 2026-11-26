package app.criol.ui

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.scaleIn
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.ui.draw.clipToBounds
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavGraphBuilder
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import app.criol.ui.theme.Motion
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.criol.Graph
import app.criol.ui.player.FullPlayer
import app.criol.ui.player.MiniPlayer
import app.criol.ui.screens.AlbumScreen
import app.criol.ui.screens.ArtistScreen
import app.criol.ui.screens.FolderScreen
import app.criol.ui.screens.GenreScreen
import app.criol.ui.screens.HomeScreen
import app.criol.ui.screens.LibraryScreen
import app.criol.ui.screens.MixScreen
import app.criol.ui.screens.PlaylistScreen
import app.criol.ui.screens.PlaylistsScreen
import app.criol.ui.screens.ProfileScreen
import app.criol.ui.screens.RecapScreen
import app.criol.ui.screens.monthTitle
import app.criol.ui.screens.SearchScreen
import app.criol.ui.screens.SettingsScreen
import app.criol.ui.theme.Criol
import app.criol.ui.theme.CriolTheme

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Início", Icons.Rounded.Home),
    Tab("search", "Buscar", Icons.Rounded.Search),
    Tab("library", "Biblioteca", Icons.Rounded.LibraryMusic),
    Tab("playlists", "Playlists", Icons.AutoMirrored.Rounded.QueueMusic),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CriolRoot(openPlayerSignal: Int) {
    val settings by Graph.settings.settings.collectAsState()
    CriolTheme(settings.themeId, settings.accent, settings.themeFollowsDay) {
        val navController = rememberNavController()
        val sheets = remember { SheetController() }
        val playerOpen = rememberSaveable { mutableStateOf(false) }
        // Só precisa saber se há música; ler o estado inteiro recompunha a raiz a cada play/pause.
        val currentId by Graph.player.currentId.collectAsState()
        val colors = Criol.colors
        val activity = LocalContext.current as? ComponentActivity

        LaunchedEffect(openPlayerSignal) {
            if (openPlayerSignal > 0) playerOpen.value = true
        }
        val darkBars = colors.isDark || (playerOpen.value && settings.dynamicPlayerBackground)
        LaunchedEffect(darkBars) {
            val transparent = android.graphics.Color.TRANSPARENT
            activity?.enableEdgeToEdge(
                statusBarStyle = if (darkBars) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent),
                navigationBarStyle = if (darkBars) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent),
            )
        }

        val context = LocalContext.current
        LaunchedEffect(Unit) {
            Graph.player.autoDjEvents.collect { msg ->
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        // Resumo mensal: gera assim que o mês fecha e avisa.
        LaunchedEffect(Unit) {
            Graph.recaps.ready.collect { month ->
                android.widget.Toast.makeText(
                    context, "Seu Resumo de ${monthTitle(month, withYear = false)} já está no seu perfil.",
                    android.widget.Toast.LENGTH_LONG,
                ).show()
            }
        }
        LaunchedEffect(Unit) {
            while (true) {
                Graph.recaps.check()
                kotlinx.coroutines.delay(30 * 60 * 1000L)
            }
        }

        // Leitura rápida (índice de músicas do Android): pede uma vez, ao escolher a primeira
        // pasta ou, para quem já usa o app, na próxima abertura. Dá para ligar depois nas Configurações.
        val mediaPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) Graph.library.onMediaPermissionChanged()
        }
        fun askMediaPermissionOnce() {
            if (Graph.library.hasMediaPermission || Graph.settings.value.mediaPermissionAsked) return
            Graph.settings.update { it.copy(mediaPermissionAsked = true) }
            mediaPermission.launch(app.criol.data.MediaStoreIndex.permission)
        }
        LaunchedEffect(Unit) {
            if (Graph.settings.value.folders.isNotEmpty()) askMediaPermissionOnce()
        }
        val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                Graph.library.addFolder(uri)
                askMediaPermissionOnce()
            }
        }
        val addFolder = { folderPicker.launch(null) }

        val navigator = remember(navController) { AppNavigator(navController) { playerOpen.value = false } }

        CompositionLocalProvider(LocalNavigator provides navigator, LocalSheets provides sheets) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(colors.background),
            ) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).clipToBounds()) {
                        NavHost(
                            navController = navController,
                            startDestination = "home",
                            enterTransition = { navEnter(forward = true) },
                            exitTransition = { navExit(forward = true) },
                            popEnterTransition = { navEnter(forward = false) },
                            popExitTransition = { navExit(forward = false) },
                        ) {
                            screen("home") { HomeScreen(addFolder) }
                            screen("search") { SearchScreen() }
                            screen("library") { LibraryScreen() }
                            screen("playlists") { PlaylistsScreen() }
                            screen("settings") { SettingsScreen(addFolder) }
                            screen("album/{id}") { AlbumScreen(it.arguments?.getString("id").orEmpty()) }
                            screen("artist/{id}") { ArtistScreen(it.arguments?.getString("id").orEmpty()) }
                            screen("genre/{id}") { GenreScreen(it.arguments?.getString("id").orEmpty()) }
                            screen("playlist/{id}") { PlaylistScreen(it.arguments?.getString("id").orEmpty()) }
                            screen("profile") { ProfileScreen() }
                            screen("recap/{month}") { RecapScreen(it.arguments?.getString("month").orEmpty()) }
                            screen("mix/{id}") { MixScreen(it.arguments?.getString("id").orEmpty()) }
                            screen(
                                "folder?path={path}",
                                arguments = listOf(navArgument("path") { type = NavType.StringType; defaultValue = "" }),
                            ) { FolderScreen(it.arguments?.getString("path").orEmpty()) }
                        }
                    }
                    // Com o teclado aberto (busca), o mini player e as abas saem da frente:
                    // senão ficam escondidos atrás do teclado ocupando espaço e a lista é cortada.
                    if (!WindowInsets.isImeVisible) {
                        MiniPlayer(onExpand = { playerOpen.value = true })
                        BottomNav(navController)
                    }
                }

                AnimatedVisibility(
                    visible = playerOpen.value && currentId != null,
                    // Sobe desacelerando e desce acelerando, sem esmaecer (o player é opaco).
                    enter = slideInVertically(Motion.enter(Motion.LONG)) { it },
                    exit = slideOutVertically(Motion.exit(280)) { it },
                ) {
                    FullPlayer(onClose = { playerOpen.value = false })
                }

                SheetHost(sheets)
            }
        }
    }
}

/**
 * Cada tela tem fundo opaco: durante a transição a tela que sai fica por cima da que entra,
 * e sem fundo as duas apareciam misturadas.
 */
private fun NavGraphBuilder.screen(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit,
) = composable(route, arguments) { entry ->
    Box(
        Modifier
            .fillMaxSize()
            .background(Criol.colors.background),
    ) { content(entry) }
}

private val tabRoutes = setOf("home", "search", "library", "playlists")

/**
 * Troca de aba na barra de baixo: só esmaece.
 * Abrir/voltar uma página: igual ao player — a página sobe de baixo cobrindo a anterior e, ao
 * voltar, desce inteira (acelerando, sem esmaecer) revelando a de baixo, que fica parada.
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(forward: Boolean) =
    targetState.destination.route in tabRoutes &&
        // Tocar numa aba estando numa página também só esmaece; voltar de uma página para a aba desce.
        (forward || initialState.destination.route in tabRoutes)

private const val PAGE_ENTER_MS = 380
private const val PAGE_EXIT_MS = 280

private fun AnimatedContentTransitionScope<NavBackStackEntry>.navEnter(forward: Boolean): EnterTransition =
    when {
        isTabSwitch(forward) ->
            fadeIn(Motion.enter(220, delay = 60)) + scaleIn(Motion.enter(220, delay = 60), initialScale = 0.98f)
        forward -> slideInVertically(Motion.enter(PAGE_ENTER_MS)) { it }
        // A página de baixo já está no lugar: só espera a de cima sair.
        else -> EnterTransition.None
    }

@OptIn(ExperimentalAnimationApi::class)
private fun AnimatedContentTransitionScope<NavBackStackEntry>.navExit(forward: Boolean): ExitTransition =
    when {
        isTabSwitch(forward) -> fadeOut(Motion.exit(90))
        // Fica visível embaixo enquanto a nova página sobe por cima.
        forward -> ExitTransition.KeepUntilTransitionsFinished
        else -> slideOutVertically(Motion.exit(PAGE_EXIT_MS)) { it }
    }

private class AppNavigator(
    private val nav: NavHostController,
    private val closePlayer: () -> Unit,
) : Navigator {
    private fun go(route: String) {
        closePlayer()
        if (nav.currentBackStackEntry?.let { routeOf(it) } == route) return
        nav.navigate(route)
    }

    private fun routeOf(entry: androidx.navigation.NavBackStackEntry): String? {
        val pattern = entry.destination.route ?: return null
        val args = entry.arguments ?: return pattern
        return Regex("\\{(\\w+)\\}").replace(pattern) { m ->
            val v = args.getString(m.groupValues[1]).orEmpty()
            if (pattern.contains("?")) Uri.encode(v) else v
        }
    }

    override fun album(id: String) = go("album/$id")
    override fun artist(id: String) = go("artist/$id")
    override fun artistByName(name: String) = artist(Graph.library.current.artistIdFor(name))
    override fun genre(id: String) = go("genre/$id")
    override fun folder(path: String) = go("folder?path=${Uri.encode(path)}")
    override fun playlist(id: String) = go("playlist/$id")
    override fun settings() = go("settings")
    override fun profile() = go("profile")
    override fun recap(month: String) = go("recap/$month")
    override fun mix(id: String) = go("mix/$id")
    override fun back() {
        nav.popBackStack()
    }
}

@Composable
private fun BottomNav(navController: NavHostController) {
    val colors = Criol.colors
    val entry by navController.currentBackStackEntryAsState()
    var selected by rememberSaveable { mutableStateOf("home") }
    val route = entry?.destination?.route
    LaunchedEffect(route) {
        if (route != null && tabs.any { it.route == route }) selected = route
    }

    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.backgroundAlt)
            .navigationBarsPadding()
            .height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            val isSel = tab.route == selected
            Column(
                Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        if (isSel && route != tab.route) {
                            // Toque na aba atual volta à raiz dela.
                            navController.popBackStack(tab.route, inclusive = false)
                        } else if (!isSel) {
                            selected = tab.route
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // A pílula da aba cresce a partir do centro e as cores mudam suavemente.
                val p by animateFloatAsState(if (isSel) 1f else 0f, Motion.standard(Motion.MEDIUM), label = "tab")
                Box(Modifier.size(width = 56.dp, height = 30.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .graphicsLayer {
                                scaleX = 0.4f + 0.6f * p
                                alpha = p
                            }
                            .clip(RoundedCornerShape(50))
                            .background(colors.primary.copy(alpha = 0.18f))
                            .size(width = 56.dp, height = 30.dp),
                    )
                    Icon(tab.icon, tab.label, tint = lerp(colors.foregroundMuted, colors.primary, p))
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    tab.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = lerp(colors.foregroundMuted, colors.foreground, p),
                )
            }
        }
    }
}
