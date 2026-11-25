package app.criol.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.criol.Graph
import app.criol.data.Album
import app.criol.data.Artist
import app.criol.data.FolderNode
import app.criol.data.Genre
import app.criol.data.Song
import app.criol.data.albumCountLabel
import app.criol.data.normalizeForSearch
import app.criol.data.songCountLabel
import app.criol.ui.LocalNavigator
import app.criol.ui.LocalSheets
import app.criol.ui.Sheet
import app.criol.ui.components.AlbumCard
import app.criol.ui.components.Artwork
import app.criol.ui.components.EmptyState
import app.criol.ui.components.FastScroller
import app.criol.ui.components.SongRow
import app.criol.ui.components.TopBar
import app.criol.ui.components.indexLetter
import app.criol.ui.theme.Criol

enum class LibTab(val label: String) {
    SONGS("Músicas"), ALBUMS("Álbuns"), ARTISTS("Artistas"), GENRES("Gêneros"), FOLDERS("Pastas"), FAVORITES("Favoritos")
}

private val songSorts = listOf(
    "title" to "Título", "artist" to "Artista", "album" to "Álbum", "year" to "Ano",
    "added" to "Adicionadas recentemente", "plays" to "Mais tocadas", "rating" to "Avaliação", "duration" to "Duração",
)
private val albumSorts = listOf(
    "name" to "Nome", "artist" to "Artista", "year" to "Ano", "added" to "Adicionados recentemente",
    "plays" to "Mais tocados", "songs" to "Nº de músicas",
)
private val artistSorts = listOf("name" to "Nome", "songs" to "Nº de músicas", "albums" to "Nº de álbuns", "plays" to "Mais tocados")

@Composable
fun LibraryScreen() {
    var tab by rememberSaveable { mutableStateOf(LibTab.ALBUMS) }
    val settings by Graph.settings.settings.collectAsState()
    var sortOpen by remember { mutableStateOf(false) }
    val colors = Criol.colors

    Column(Modifier.fillMaxSize()) {
        TopBar("Biblioteca") {
            val options = when (tab) {
                LibTab.SONGS -> songSorts
                LibTab.ALBUMS -> albumSorts
                LibTab.ARTISTS -> artistSorts
                else -> null
            }
            if (tab == LibTab.ALBUMS) {
                IconButton(onClick = { Graph.settings.update { it.copy(albumsAsGrid = !it.albumsAsGrid) } }) {
                    Icon(
                        if (settings.albumsAsGrid) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                        "Alternar visualização",
                        tint = colors.foreground,
                    )
                }
            }
            if (options != null) {
                Box {
                    IconButton(onClick = { sortOpen = true }) {
                        Icon(Icons.AutoMirrored.Rounded.Sort, "Ordenar", tint = colors.foreground)
                    }
                    val currentSort = when (tab) {
                        LibTab.SONGS -> settings.songSort
                        LibTab.ALBUMS -> settings.albumSort
                        else -> settings.artistSort
                    }
                    DropdownMenu(sortOpen, { sortOpen = false }, containerColor = colors.surface) {
                        options.forEach { (key, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                trailingIcon = { if (key == currentSort) Icon(Icons.Rounded.Check, null, tint = colors.primary) },
                                onClick = {
                                    sortOpen = false
                                    Graph.settings.update {
                                        when (tab) {
                                            LibTab.SONGS -> it.copy(songSort = key)
                                            LibTab.ALBUMS -> it.copy(albumSort = key)
                                            else -> it.copy(artistSort = key)
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 8.dp),
        ) {
            items(LibTab.entries) { t -> Pill(t.label, t == tab) { tab = t } }
        }
        when (tab) {
            LibTab.SONGS -> SongsTab()
            LibTab.ALBUMS -> AlbumsTab()
            LibTab.ARTISTS -> ArtistsTab()
            LibTab.GENRES -> GenresTab()
            LibTab.FOLDERS -> FoldersTab()
            LibTab.FAVORITES -> FavoritesTab()
        }
    }
}

@Composable
fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = Criol.colors
    Text(
        label,
        style = MaterialTheme.typography.titleSmall,
        color = if (selected) colors.onPrimary else colors.foreground,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) colors.primary else colors.surfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
fun PlayShuffleBar(label: String, onPlay: () -> Unit, onShuffle: () -> Unit) {
    val colors = Criol.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = colors.foregroundMuted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = onShuffle) { Icon(Icons.Rounded.Shuffle, "Aleatório", tint = colors.foreground) }
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(colors.primary)
                .clickable(onClick = onPlay),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.PlayArrow, "Tocar", tint = colors.onPrimary)
        }
    }
}

// ----------------------------------------------------------------------
// Músicas
// ----------------------------------------------------------------------

@Composable
private fun SongsTab() {
    val lib by Graph.library.library.collectAsState()
    val data by Graph.userData.data.collectAsState()
    val settings by Graph.settings.settings.collectAsState()
    val player by Graph.player.state.collectAsState()
    val sheets = LocalSheets.current
    val sort = settings.songSort
    val songs = remember(lib, sort, if (sort == "plays" || sort == "rating") data else null) {
        sortSongs(lib.songs, sort, data.playCounts, data.ratings)
    }
    if (songs.isEmpty()) {
        EmptyState(Icons.Rounded.MusicNote, "Nenhuma música", "Adicione uma pasta nas configurações.")
        return
    }
    val state = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = state, contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                PlayShuffleBar(
                    songCountLabel(songs.size),
                    { Graph.player.playSongs(songs) },
                    { Graph.player.playSongs(songs, shuffle = true) },
                )
            }
            itemsIndexed(songs, key = { _, s -> s.id }) { i, s ->
                SongRow(
                    song = s,
                    artPath = lib.artPath(s.albumId),
                    onClick = { Graph.player.playSongs(songs, i) },
                    onMore = { sheets.show(Sheet.SongMenu(s)) },
                    isCurrent = player.current?.id == s.id,
                    isFavorite = s.id in data.favoriteSongs,
                )
            }
        }
        FastScroller(
            state, Modifier.align(Alignment.CenterEnd),
            label = when (sort) {
                "title" -> { i -> songs.getOrNull(i - 1)?.title?.let(::indexLetter) ?: "" }
                "artist" -> { i -> songs.getOrNull(i - 1)?.artist?.let(::indexLetter) ?: "" }
                "album" -> { i -> songs.getOrNull(i - 1)?.album?.let(::indexLetter) ?: "" }
                "year" -> { i -> songs.getOrNull(i - 1)?.year?.takeIf { it > 0 }?.toString() ?: "" }
                else -> null
            },
        )
    }
}

private fun trackKey(s: Song) = "%03d%05d".format(s.disc, s.track)

/** Ordena por texto calculando a chave uma vez por item. */
fun <T> List<T>.sortedByKey(key: (T) -> String): List<T> =
    map { it to key(it) }.sortedBy { it.second }.map { it.first }

fun sortSongs(songs: List<Song>, sort: String, plays: Map<String, Int>, ratings: Map<String, Int>): List<Song> = when (sort) {
    "artist" -> songs.sortedByKey { "${it.artist.normalizeForSearch()}\u0001${it.album.normalizeForSearch()}\u0001${trackKey(it)}" }
    "album" -> songs.sortedByKey { "${it.album.normalizeForSearch()}\u0001${trackKey(it)}" }
    "year" -> songs.sortedByKey { "%04d".format(9999 - it.year) + it.album.normalizeForSearch() + "\u0001" + trackKey(it) }
    "added" -> songs.sortedByDescending { it.dateAdded }
    "plays" -> songs.sortedByDescending { plays[it.id] ?: 0 }
    "rating" -> songs.sortedByDescending { ratings[it.id] ?: 0 }
    "duration" -> songs.sortedByDescending { it.durationMs }
    else -> songs
}

// ----------------------------------------------------------------------
// Álbuns
// ----------------------------------------------------------------------

@Composable
private fun AlbumsTab() {
    val lib by Graph.library.library.collectAsState()
    val data by Graph.userData.data.collectAsState()
    val settings by Graph.settings.settings.collectAsState()
    val sort = settings.albumSort
    val albums = remember(lib, sort, if (sort == "plays") data.playCounts else null) {
        when (sort) {
            "artist" -> lib.albums.sortedByKey { it.artist.normalizeForSearch() + "\u0001" + "%04d".format(it.year) }
            "year" -> lib.albums.sortedByDescending { it.year }
            "added" -> lib.albums.sortedByDescending { it.dateAdded }
            "plays" -> lib.albums.sortedByDescending { a -> a.songs.sumOf { data.playCounts[it.id] ?: 0 } }
            "songs" -> lib.albums.sortedByDescending { it.songs.size }
            else -> lib.albums
        }
    }
    val label: ((Int) -> String)? = when (sort) {
        "name" -> { i -> albums.getOrNull(i - 1)?.name?.let(::indexLetter) ?: "" }
        "artist" -> { i -> albums.getOrNull(i - 1)?.artist?.let(::indexLetter) ?: "" }
        "year" -> { i -> albums.getOrNull(i - 1)?.year?.takeIf { it > 0 }?.toString() ?: "" }
        else -> null
    }
    AlbumCollection(albums, settings.albumsAsGrid, settings.albumColumns, label)
}

@Composable
fun AlbumCollection(
    albums: List<Album>,
    asGrid: Boolean,
    columns: Int,
    label: ((Int) -> String)? = null,
    header: (@Composable () -> Unit)? = null,
) {
    val nav = LocalNavigator.current
    val sheets = LocalSheets.current
    if (albums.isEmpty()) {
        EmptyState(Icons.Rounded.Album, "Nenhum álbum")
        return
    }
    if (asGrid) {
        val state = rememberLazyGridState()
        Box(Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns.coerceIn(2, 4)),
                state = state,
                contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 24.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        header?.invoke()
                        Text(
                            albumCountLabel(albums.size),
                            color = Criol.colors.foregroundMuted,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 6.dp, top = 4.dp, bottom = 4.dp),
                        )
                    }
                }
                items(albums, key = { it.id }) { a ->
                    AlbumCard(
                        album = a,
                        onClick = { nav.album(a.id) },
                        onLongClick = { sheets.show(Sheet.AlbumMenu(a)) },
                        subtitle = if (a.year > 0) "${a.artist} • ${a.year}" else a.artist,
                    )
                }
            }
            FastScroller(state, Modifier.align(Alignment.CenterEnd), label)
        }
    } else {
        val state = rememberLazyListState()
        Box(Modifier.fillMaxSize()) {
            LazyColumn(state = state, contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    header?.invoke()
                    Text(
                        albumCountLabel(albums.size),
                        color = Criol.colors.foregroundMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                    )
                }
                items(albums, key = { it.id }) { a ->
                    MediaRow(
                        artPath = a.artPath,
                        title = a.name,
                        subtitle = listOfNotNull(a.artist, a.year.takeIf { it > 0 }?.toString(), songCountLabel(a.songs.size)).joinToString(" • "),
                        onClick = { nav.album(a.id) },
                        onMore = { sheets.show(Sheet.AlbumMenu(a)) },
                    )
                }
            }
            FastScroller(state, Modifier.align(Alignment.CenterEnd), label)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaRow(
    artPath: String?,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    onMore: (() -> Unit)? = null,
    circle: Boolean = false,
    placeholder: androidx.compose.ui.graphics.vector.ImageVector = Icons.Rounded.Album,
) {
    val colors = Criol.colors
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onMore)
            .padding(start = 16.dp, end = 4.dp)
            .height(68.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(artPath, Modifier.size(52.dp), if (circle) CircleShape else RoundedCornerShape(6.dp), placeholder)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = colors.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.foregroundMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onMore != null) {
            IconButton(onClick = onMore) { Icon(Icons.Rounded.MoreVert, "Mais", tint = colors.foregroundMuted) }
        } else {
            Spacer(Modifier.width(12.dp))
        }
    }
}

// ----------------------------------------------------------------------
// Artistas
// ----------------------------------------------------------------------

@Composable
private fun ArtistsTab() {
    val lib by Graph.library.library.collectAsState()
    val data by Graph.userData.data.collectAsState()
    val settings by Graph.settings.settings.collectAsState()
    val sort = settings.artistSort
    val artists = remember(lib, sort, if (sort == "plays") data.playCounts else null) {
        when (sort) {
            "songs" -> lib.artists.sortedByDescending { it.songs.size }
            "albums" -> lib.artists.sortedByDescending { it.albums.size }
            "plays" -> lib.artists.sortedByDescending { a -> a.songs.sumOf { data.playCounts[it.id] ?: 0 } }
            else -> lib.artists
        }
    }
    ArtistList(artists, if (sort == "name") { i -> artists.getOrNull(i)?.name?.let(::indexLetter) ?: "" } else null)
}

@Composable
fun ArtistList(artists: List<Artist>, label: ((Int) -> String)? = null) {
    val nav = LocalNavigator.current
    val sheets = LocalSheets.current
    if (artists.isEmpty()) {
        EmptyState(Icons.Rounded.Person, "Nenhum artista")
        return
    }
    val state = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = state, contentPadding = PaddingValues(bottom = 24.dp)) {
            items(artists, key = { it.id }) { a ->
                MediaRow(
                    artPath = a.artPath,
                    title = a.name,
                    subtitle = artistSubtitle(a),
                    onClick = { nav.artist(a.id) },
                    onMore = { sheets.show(Sheet.ArtistMenu(a)) },
                    circle = true,
                    placeholder = Icons.Rounded.Person,
                )
            }
        }
        FastScroller(state, Modifier.align(Alignment.CenterEnd), label)
    }
}

fun artistSubtitle(a: Artist): String = buildList {
    if (a.albums.isNotEmpty()) add(albumCountLabel(a.albums.size))
    add(songCountLabel(a.songs.size))
}.joinToString(" • ")

// ----------------------------------------------------------------------
// Gêneros e pastas
// ----------------------------------------------------------------------

@Composable
private fun GenresTab() {
    val lib by Graph.library.library.collectAsState()
    val nav = LocalNavigator.current
    val sheets = LocalSheets.current
    if (lib.genres.isEmpty()) {
        EmptyState(Icons.Rounded.MusicNote, "Nenhum gênero", "As músicas não têm a tag de gênero preenchida.")
        return
    }
    val sorted = remember(lib) { lib.genres.sortedByKey { it.name.normalizeForSearch() } }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        items(sorted, key = { it.id }) { g: Genre ->
            MediaRow(
                artPath = g.artPath,
                title = g.name,
                subtitle = "${albumCountLabel(g.albums.size)} • ${songCountLabel(g.songs.size)}",
                onClick = { nav.genre(g.id) },
                onMore = { sheets.show(Sheet.SongsMenu(g.name, g.songs)) },
            )
        }
    }
}

@Composable
private fun FoldersTab() {
    val lib by Graph.library.library.collectAsState()
    val nav = LocalNavigator.current
    if (lib.rootFolders.isEmpty()) {
        EmptyState(Icons.Rounded.Folder, "Nenhuma pasta", "Adicione uma pasta nas configurações.")
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        items(lib.rootFolders, key = { it.path }) { f -> FolderRow(f) { nav.folder(f.path) } }
    }
}

@Composable
fun FolderRow(folder: FolderNode, onClick: () -> Unit) {
    val sheets = LocalSheets.current
    MediaRow(
        artPath = folder.artPath,
        title = folder.name,
        subtitle = buildList {
            if (folder.children.isNotEmpty()) add(if (folder.children.size == 1) "1 pasta" else "${folder.children.size} pastas")
            add(songCountLabel(folder.totalSongs))
        }.joinToString(" • "),
        onClick = onClick,
        onMore = { sheets.show(Sheet.SongsMenu(folder.name, collectFolderSongs(folder))) },
        placeholder = Icons.Rounded.Folder,
    )
}

fun collectFolderSongs(folder: FolderNode): List<Song> =
    folder.songs + folder.children.flatMap { collectFolderSongs(it) }

// ----------------------------------------------------------------------
// Favoritos
// ----------------------------------------------------------------------

@Composable
private fun FavoritesTab() {
    val lib by Graph.library.library.collectAsState()
    val data by Graph.userData.data.collectAsState()
    val player by Graph.player.state.collectAsState()
    val sheets = LocalSheets.current
    var sub by rememberSaveable { mutableStateOf(0) }
    Column {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Músicas", "Álbuns", "Artistas").forEachIndexed { i, l -> Pill(l, sub == i) { sub = i } }
        }
        when (sub) {
            0 -> {
                val songs = remember(lib, data.favoriteSongs) { lib.songs.filter { it.id in data.favoriteSongs } }
                if (songs.isEmpty()) {
                    EmptyState(Icons.Rounded.FavoriteBorder, "Nenhuma música favorita", "Toque no coração para favoritar.")
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                        item {
                            PlayShuffleBar(songCountLabel(songs.size), { Graph.player.playSongs(songs) }, { Graph.player.playSongs(songs, shuffle = true) })
                        }
                        itemsIndexed(songs, key = { _, s -> s.id }) { i, s ->
                            SongRow(
                                song = s,
                                artPath = lib.artPath(s.albumId),
                                onClick = { Graph.player.playSongs(songs, i) },
                                onMore = { sheets.show(Sheet.SongMenu(s)) },
                                isCurrent = player.current?.id == s.id,
                            )
                        }
                    }
                }
            }
            1 -> {
                val albums = remember(lib, data.favoriteAlbums) { lib.albums.filter { it.id in data.favoriteAlbums } }
                if (albums.isEmpty()) EmptyState(Icons.Rounded.FavoriteBorder, "Nenhum álbum favorito")
                else AlbumCollection(albums, true, Graph.settings.value.albumColumns)
            }
            else -> {
                val artists = remember(lib, data.favoriteArtists) { lib.artists.filter { it.id in data.favoriteArtists } }
                if (artists.isEmpty()) EmptyState(Icons.Rounded.FavoriteBorder, "Nenhum artista favorito")
                else ArtistList(artists)
            }
        }
    }
}
