package app.criol.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.criol.Graph
import app.criol.data.Library
import app.criol.data.Song
import app.criol.data.UserData
import app.criol.data.formatLongDuration
import app.criol.data.songCountLabel
import app.criol.ui.LocalNavigator
import app.criol.ui.LocalSheets
import app.criol.ui.NameDialog
import app.criol.ui.Sheet
import app.criol.ui.components.Artwork
import app.criol.ui.components.DetailActions
import app.criol.ui.components.DetailHeader
import app.criol.ui.components.DragHandle
import app.criol.ui.components.EmptyState
import app.criol.ui.components.ReorderRowHeight
import app.criol.ui.components.SectionHeader
import app.criol.ui.components.SongRow
import app.criol.ui.components.TopBar
import app.criol.ui.components.reorderItem
import app.criol.ui.components.rememberReorderState
import app.criol.ui.theme.Criol

private data class AutoPlaylist(val id: String, val name: String, val icon: ImageVector)

private val autoPlaylists = listOf(
    AutoPlaylist("__fav", "Músicas favoritas", Icons.Rounded.Favorite),
    AutoPlaylist("__top", "Mais tocadas", Icons.Rounded.TrendingUp),
    AutoPlaylist("__recent", "Tocadas recentemente", Icons.Rounded.History),
    AutoPlaylist("__new", "Adicionadas recentemente", Icons.Rounded.NewReleases),
)

private fun autoSongs(id: String, lib: Library, data: UserData): List<Song> = when (id) {
    "__fav" -> lib.songs.filter { it.id in data.favoriteSongs }
    "__top" -> lib.songs.filter { (data.playCounts[it.id] ?: 0) > 0 }
        .sortedByDescending { data.playCounts[it.id] ?: 0 }.take(100)
    "__recent" -> lib.songs.filter { data.lastPlayed.containsKey(it.id) }
        .sortedByDescending { data.lastPlayed[it.id] ?: 0 }.take(100)
    "__new" -> lib.songs.sortedByDescending { it.dateAdded }.take(100)
    else -> emptyList()
}

@Composable
fun PlaylistsScreen() {
    val lib by Graph.library.library.collectAsState()
    val data by Graph.userData.data.collectAsState()
    val nav = LocalNavigator.current
    val sheets = LocalSheets.current
    var creating by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopBar("Playlists") {
            IconButton(onClick = { creating = true }) {
                Icon(Icons.Rounded.Add, "Nova playlist", tint = Criol.colors.foreground)
            }
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item { SectionHeader("Automáticas", Modifier.padding(top = 0.dp)) }
            items(autoPlaylists, key = { it.id }) { p ->
                val songs = remember(lib, data) { autoSongs(p.id, lib, data) }
                IconRow(p.icon, p.name, songCountLabel(songs.size)) { nav.playlist(p.id) }
            }
            item {
                SectionHeader("Minhas playlists") {
                    TextButton(onClick = { creating = true }) { Text("Nova") }
                }
            }
            if (data.playlists.isEmpty()) {
                item {
                    EmptyState(
                        Icons.AutoMirrored.Rounded.QueueMusic,
                        "Nenhuma playlist",
                        "Crie uma playlist ou use \"Adicionar à playlist\" no menu de qualquer música.",
                    )
                }
            }
            items(data.playlists.sortedBy { it.name.lowercase() }, key = { it.id }) { p ->
                val songs = p.songIds.mapNotNull { lib.songsById[it] }
                MediaRowMosaic(
                    arts = songs.map { it.albumId }.distinct().take(4).map { lib.artPath(it) },
                    title = p.name,
                    subtitle = songCountLabel(songs.size),
                    onClick = { nav.playlist(p.id) },
                    onMore = { sheets.show(Sheet.SongsMenu(p.name, songs)) },
                )
            }
        }
    }
    if (creating) {
        NameDialog("Nova playlist", confirm = "Criar", onDismiss = { creating = false }) { name ->
            val id = Graph.userData.createPlaylist(name)
            creating = false
            nav.playlist(id)
        }
    }
}

@Composable
private fun IconRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val colors = Criol.colors
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickableCompat(onClick)
            .padding(horizontal = 16.dp)
            .height(68.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(colors.primary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = colors.primary)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = colors.foreground)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.foregroundMuted)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit) = this.combinedClickable(onClick = onClick)

@Composable
fun Mosaic(arts: List<String?>, size: Dp, corner: Dp = 6.dp) {
    val list = arts.filterNotNull()
    if (list.size < 4) {
        Artwork(list.firstOrNull(), Modifier.size(size), RoundedCornerShape(corner), Icons.AutoMirrored.Rounded.QueueMusic)
        return
    }
    Column(Modifier.size(size).clip(RoundedCornerShape(corner))) {
        Row {
            Artwork(list[0], Modifier.size(size / 2), RoundedCornerShape(0.dp))
            Artwork(list[1], Modifier.size(size / 2), RoundedCornerShape(0.dp))
        }
        Row {
            Artwork(list[2], Modifier.size(size / 2), RoundedCornerShape(0.dp))
            Artwork(list[3], Modifier.size(size / 2), RoundedCornerShape(0.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaRowMosaic(arts: List<String?>, title: String, subtitle: String, onClick: () -> Unit, onMore: () -> Unit) {
    val colors = Criol.colors
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onMore)
            .padding(start = 16.dp, end = 4.dp)
            .height(68.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Mosaic(arts, 52.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = colors.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.foregroundMuted)
        }
        IconButton(onClick = onMore) { Icon(Icons.Rounded.MoreVert, "Mais", tint = colors.foregroundMuted) }
    }
}

@Composable
fun PlaylistScreen(id: String) {
    val lib by Graph.library.library.collectAsState()
    val data by Graph.userData.data.collectAsState()
    val currentId by Graph.player.currentId.collectAsState()
    val nav = LocalNavigator.current
    val sheets = LocalSheets.current
    val auto = autoPlaylists.firstOrNull { it.id == id }
    val playlist = data.playlists.firstOrNull { it.id == id }
    if (auto == null && playlist == null) {
        Column(Modifier.fillMaxSize()) {
            TopBar("", onBack = { nav.back() })
            EmptyState(Icons.AutoMirrored.Rounded.QueueMusic, "Playlist não encontrada")
        }
        return
    }
    val name = auto?.name ?: playlist!!.name
    // Para playlists do usuário mantemos o índice original (para remover/reordenar).
    val entries: List<Pair<Int, Song>> = remember(lib, data, id) {
        if (auto != null) autoSongs(id, lib, data).mapIndexed { i, s -> i to s }
        else playlist!!.songIds.mapIndexedNotNull { i, sid -> lib.songsById[sid]?.let { i to it } }
    }
    val songs = entries.map { it.second }
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val reorder = rememberReorderState(ReorderRowHeight)
    val arts = songs.map { it.albumId }.distinct().take(4).map { lib.artPath(it) }
    val firstColor = songs.firstNotNullOfOrNull { lib.artColor(it.albumId) }

    DetailScaffold(name) {
        item {
            DetailHeader(
                kicker = if (auto != null) "Playlist automática" else "Playlist",
                title = name,
                color = firstColor,
                art = {
                    Box(Modifier.shadow(18.dp, RoundedCornerShape(10.dp))) { Mosaic(arts, 210.dp, 10.dp) }
                },
                subtitle = {
                    Text(
                        "${songCountLabel(songs.size)} • ${formatLongDuration(songs.sumOf { it.durationMs })}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Criol.colors.foreground.copy(alpha = 0.8f),
                    )
                },
            )
        }
        item {
            Box {
                DetailActions(
                    onPlay = { Graph.player.playSongs(songs) },
                    onShuffle = { Graph.player.playSongs(songs, shuffle = true) },
                    onMore = { if (playlist != null) menuOpen = true else sheets.show(Sheet.SongsMenu(name, songs)) },
                )
                if (playlist != null) {
                    Box(Modifier.align(Alignment.TopEnd)) {
                        DropdownMenu(menuOpen, { menuOpen = false }, containerColor = Criol.colors.surface) {
                            DropdownMenuItem(
                                text = { Text("Adicionar à fila") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null) },
                                onClick = { menuOpen = false; Graph.player.addToQueue(songs) },
                            )
                            DropdownMenuItem(
                                text = { Text("Renomear") },
                                leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                                onClick = { menuOpen = false; renaming = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Excluir playlist") },
                                leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                                onClick = { menuOpen = false; deleting = true },
                            )
                        }
                    }
                }
            }
        }
        if (songs.isEmpty()) {
            item {
                EmptyState(
                    Icons.AutoMirrored.Rounded.QueueMusic,
                    "Playlist vazia",
                    if (auto != null) "Ainda não há músicas aqui." else "Use \"Adicionar à playlist\" no menu de uma música.",
                )
            }
        }
        itemsIndexed(entries, key = { _, e -> "${e.first}-${e.second.id}" }) { i, (origIndex, s) ->
            SongRow(
                song = s,
                artPath = lib.artPath(s.albumId),
                onClick = { Graph.player.playSongs(songs, i) },
                onMore = { sheets.show(Sheet.SongMenu(s, playlistId = playlist?.id, playlistIndex = origIndex.takeIf { playlist != null })) },
                isCurrent = currentId == s.id,
                isFavorite = s.id in data.favoriteSongs,
                modifier = if (playlist != null) Modifier.reorderItem(reorder, i) else Modifier,
                trailing = if (playlist != null) {
                    {
                        DragHandle(reorder, i, entries.size) { from, to ->
                            Graph.userData.movePlaylistItem(playlist.id, entries[from].first, entries[to].first)
                        }
                    }
                } else null,
            )
        }
    }

    if (renaming && playlist != null) {
        NameDialog("Renomear playlist", initial = playlist.name, onDismiss = { renaming = false }) {
            Graph.userData.renamePlaylist(playlist.id, it)
            renaming = false
        }
    }
    if (deleting && playlist != null) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            containerColor = Criol.colors.surface,
            title = { Text("Excluir playlist?") },
            text = { Text("\"${playlist.name}\" será excluída. As músicas não são apagadas.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    Graph.userData.deletePlaylist(playlist.id)
                    nav.back()
                }) { Text("Excluir") }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancelar") } },
        )
    }
}
