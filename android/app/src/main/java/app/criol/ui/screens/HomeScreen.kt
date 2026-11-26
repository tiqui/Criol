package app.criol.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.criol.Graph
import app.criol.data.Album
import app.criol.data.Genre
import app.criol.data.ScanState
import app.criol.ui.LocalNavigator
import app.criol.ui.LocalSheets
import app.criol.ui.Sheet
import app.criol.ui.components.AlbumCard
import app.criol.ui.components.Artwork
import app.criol.ui.components.EmptyState
import app.criol.ui.components.ProfileAvatar
import app.criol.ui.components.SectionHeader
import app.criol.ui.components.TopBar
import app.criol.ui.components.headerColor
import app.criol.ui.theme.Criol

@Composable
fun HomeScreen(onAddFolder: () -> Unit) {
    val lib by Graph.library.library.collectAsState()
    val loaded by Graph.library.loaded.collectAsState()
    val data by Graph.userData.data.collectAsState()
    val settings by Graph.settings.settings.collectAsState()
    val scan by Graph.library.scanState.collectAsState()
    val nav = LocalNavigator.current
    var exploreSeed by rememberSaveable { mutableIntStateOf(0) }

    val recentlyPlayed = remember(lib, data.lastPlayed) {
        lib.albums.map { a -> a to (a.songs.maxOfOrNull { data.lastPlayed[it.id] ?: 0L } ?: 0L) }
            .filter { it.second > 0 }.sortedByDescending { it.second }.take(20).map { it.first }
    }
    val newlyAdded = remember(lib) { lib.albums.sortedByDescending { it.dateAdded }.take(20) }
    val mostPlayed = remember(lib, data.playCounts) {
        lib.albums.map { a -> a to a.songs.sumOf { data.playCounts[it.id] ?: 0 } }
            .filter { it.second > 0 }.sortedByDescending { it.second }.take(20).map { it.first }
    }
    val explore = remember(lib, exploreSeed) { lib.albums.shuffled().take(20) }
    val releases = remember(lib) { lib.albums.filter { it.year > 0 }.sortedByDescending { it.year }.take(20) }
    val favoriteAlbums = remember(lib, data.favoriteAlbums) {
        lib.albums.filter { it.id in data.favoriteAlbums }.take(20)
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            TopBar("Início") {
                IconButton(onClick = { nav.profile() }) {
                    ProfileAvatar(32.dp)
                }
                IconButton(onClick = { nav.settings() }) {
                    Icon(Icons.Rounded.Settings, "Configurações", tint = Criol.colors.foreground)
                }
            }
        }
        item { ScanBanner(showBackground = false) }

        if (lib.isEmpty && (!loaded || scan is ScanState.Running)) return@LazyColumn
        if (loaded && lib.isEmpty) {
            item {
                EmptyState(
                    icon = Icons.Rounded.LibraryMusic,
                    title = if (settings.folders.isEmpty()) "Bem-vindo ao Criol" else "Nenhuma música encontrada",
                    subtitle = if (settings.folders.isEmpty()) {
                        "Escolha a pasta do celular onde estão suas músicas para montar a biblioteca."
                    } else {
                        "As pastas escolhidas não têm arquivos de áudio compatíveis (ou ainda estão sendo lidas)."
                    },
                    modifier = Modifier.padding(top = 48.dp),
                ) {
                    Button(onClick = onAddFolder) {
                        Icon(Icons.Rounded.CreateNewFolder, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Adicionar pasta")
                    }
                }
            }
            return@LazyColumn
        }

        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                QuickButton(Icons.Rounded.Shuffle, "Aleatório", Modifier.weight(1f)) { Graph.player.shuffleAll() }
                QuickButton(Icons.Rounded.Favorite, "Favoritas", Modifier.weight(1f)) {
                    val favs = lib.songs.filter { it.id in data.favoriteSongs }
                    Graph.player.playSongs(favs, shuffle = true)
                }
            }
        }

        item { MadeForYouSection() }
        if (recentlyPlayed.isNotEmpty()) item { AlbumCarousel("Tocados recentemente", recentlyPlayed) }
        if (newlyAdded.isNotEmpty()) item { AlbumCarousel("Adicionados recentemente", newlyAdded) }
        if (mostPlayed.isNotEmpty()) item { AlbumCarousel("Mais tocados", mostPlayed) }
        if (explore.isNotEmpty()) {
            item {
                AlbumCarousel("Explorar", explore) {
                    IconButton(onClick = { exploreSeed++ }) {
                        Icon(Icons.Rounded.Refresh, "Atualizar", tint = Criol.colors.foregroundMuted)
                    }
                }
            }
        }
        if (lib.genres.isNotEmpty()) {
            item {
                SectionHeader("Gêneros")
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(lib.genres.take(20), key = { it.id }) { g -> GenreTile(g) { nav.genre(g.id) } }
                }
            }
        }
        if (favoriteAlbums.isNotEmpty()) item { AlbumCarousel("Álbuns favoritos", favoriteAlbums) }
        if (releases.isNotEmpty()) item { AlbumCarousel("Lançamentos recentes", releases) }
    }
}

@Composable
private fun QuickButton(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val colors = Criol.colors
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = colors.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, color = colors.foreground)
    }
}

@Composable
fun AlbumCarousel(title: String, albums: List<Album>, action: (@Composable () -> Unit)? = null) {
    val nav = LocalNavigator.current
    val sheets = LocalSheets.current
    Column {
        SectionHeader(title, action = action)
        LazyRow(contentPadding = PaddingValues(horizontal = 10.dp)) {
            items(albums, key = { it.id }) { album ->
                AlbumCard(
                    album = album,
                    onClick = { nav.album(album.id) },
                    onLongClick = { sheets.show(Sheet.AlbumMenu(album)) },
                    modifier = Modifier.width(150.dp),
                )
            }
        }
    }
}

@Composable
fun GenreTile(genre: Genre, modifier: Modifier = Modifier.size(width = 160.dp, height = 84.dp), onClick: () -> Unit) {
    val colors = Criol.colors
    val c = headerColor(genre.color, colors.primary, true)
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.linearGradient(listOf(c, c.copy(alpha = 0.55f))))
            .clickable(onClick = onClick),
    ) {
        Artwork(
            genre.artPath,
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 0.dp)
                .size(64.dp),
            RoundedCornerShape(topStart = 8.dp),
        )
        Text(
            genre.name,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(12.dp)
                .padding(end = 50.dp),
        )
    }
}

@Composable
fun ScanBanner(showBackground: Boolean = true) {
    val scan by Graph.library.scanState.collectAsState()
    val s = scan
    if (s !is ScanState.Running) return
    // A leitura automática ao abrir o app roda escondida: a faixa aparecia e sumia empurrando a tela.
    if (s.background && !showBackground) return
    val colors = Criol.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surfaceHigh)
            .padding(14.dp),
    ) {
        val p = s.progress
        Text(
            if (p.total > 0) "${p.phase}… ${p.done} de ${p.total}" else "${p.phase}… ${p.done}",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.foreground,
        )
        Spacer(Modifier.height(8.dp))
        if (p.total > 0) {
            LinearProgressIndicator(
                progress = { p.done.toFloat() / p.total },
                modifier = Modifier.fillMaxWidth(),
                color = colors.primary,
                trackColor = colors.border,
            )
        } else {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = colors.primary, trackColor = colors.border)
        }
    }
}

@Composable
fun PrimaryButton(text: String, icon: ImageVector? = null, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Criol.colors.primary, contentColor = Criol.colors.onPrimary),
    ) {
        if (icon != null) {
            Icon(icon, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text)
    }
}
