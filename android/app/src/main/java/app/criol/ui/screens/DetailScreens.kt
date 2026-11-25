package app.criol.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.criol.Graph
import app.criol.data.Album
import app.criol.data.Song
import app.criol.data.albumCountLabel
import app.criol.data.formatLongDuration
import app.criol.data.songCountLabel
import app.criol.ui.LocalNavigator
import app.criol.ui.LocalSheets
import app.criol.ui.Sheet
import app.criol.ui.components.AlbumCard
import app.criol.ui.components.Artwork
import app.criol.ui.components.DetailActions
import app.criol.ui.components.DetailHeader
import app.criol.ui.components.EmptyState
import app.criol.ui.components.SectionHeader
import app.criol.ui.components.SongRow
import app.criol.ui.components.TopBar
import app.criol.ui.theme.Criol

/** Lista com cabeçalho grande e barra superior que aparece ao rolar. */
@Composable
fun DetailScaffold(
    title: String,
    state: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    val nav = LocalNavigator.current
    val solid by remember { derivedStateOf { state.firstVisibleItemIndex > 0 } }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = state, contentPadding = PaddingValues(bottom = 32.dp), content = content)
        TopBar(title = "", onBack = { nav.back() }, transparent = true)
        AnimatedVisibility(solid, enter = fadeIn(), exit = fadeOut()) {
            TopBar(title = title, onBack = { nav.back() })
        }
    }
}

@Composable
private fun NotFound(what: String) {
    val nav = LocalNavigator.current
    Column(Modifier.fillMaxSize()) {
        TopBar("", onBack = { nav.back() })
        EmptyState(Icons.Rounded.Album, "$what não encontrado", "Talvez o arquivo tenha sido removido.")
    }
}

@Composable
private fun SubtitleLine(parts: List<String>, link: String? = null, onLink: () -> Unit = {}) {
    val colors = Criol.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (link != null) {
            Text(
                link,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = colors.foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .clickable(onClick = onLink),
            )
        }
        val rest = parts.joinToString(" • ")
        if (rest.isNotEmpty()) {
            Text(
                (if (link != null) " • " else "") + rest,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.foreground.copy(alpha = 0.8f),
                maxLines = 1,
            )
        }
    }
}

// ----------------------------------------------------------------------
// Álbum
// ----------------------------------------------------------------------

@Composable
fun AlbumScreen(id: String) {
    val lib by Graph.library.library.collectAsState()
    val data by Graph.userData.data.collectAsState()
    val player by Graph.player.state.collectAsState()
    val nav = LocalNavigator.current
    val sheets = LocalSheets.current
    val album = lib.albumsById[id] ?: return NotFound("Álbum")
    val colors = Criol.colors
    val discs = album.songs.map { it.disc }.distinct()
    val multiDisc = discs.size > 1
    val moreByArtist = remember(lib, id) {
        album.artistNames.firstOrNull()?.let { name ->
            lib.artistsById[lib.artistIdFor(name)]?.albums?.filter { it.id != id }
        } ?: emptyList()
    }

    DetailScaffold(album.name) {
        item {
            DetailHeader(
                kicker = "Álbum",
                title = album.name,
                color = album.color,
                art = {
                    Artwork(
                        album.artPath,
                        Modifier
                            .size(230.dp)
                            .shadow(18.dp, RoundedCornerShape(10.dp)),
                        RoundedCornerShape(10.dp),
                        Icons.Rounded.Album,
                    )
                },
                subtitle = {
                    SubtitleLine(
                        parts = listOfNotNull(
                            album.year.takeIf { it > 0 }?.toString(),
                            songCountLabel(album.songs.size),
                            formatLongDuration(album.durationMs),
                        ),
                        link = album.artist,
                        onLink = { album.artistNames.firstOrNull()?.let { if (it != "Vários artistas") nav.artistByName(it) } },
                    )
                },
            )
        }
        item {
            DetailActions(
                onPlay = { Graph.player.playSongs(album.songs) },
                onShuffle = { Graph.player.playSongs(album.songs, shuffle = true) },
                isFavorite = album.id in data.favoriteAlbums,
                onFavorite = { Graph.userData.toggleFavoriteAlbum(album.id) },
                onMore = { sheets.show(Sheet.AlbumMenu(album)) },
            )
        }
        album.songs.forEachIndexed { i, s ->
            if (multiDisc && (i == 0 || album.songs[i - 1].disc != s.disc)) {
                item(key = "disc-${s.disc}-$i") {
                    Text(
                        "Disco ${s.disc.coerceAtLeast(1)}",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.foregroundMuted,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
            }
            item(key = s.id) {
                SongRow(
                    song = s,
                    artPath = null,
                    trackNumber = s.track,
                    subtitle = s.artist,
                    onClick = { Graph.player.playSongs(album.songs, i) },
                    onMore = { sheets.show(Sheet.SongMenu(s)) },
                    isCurrent = player.current?.id == s.id,
                    isFavorite = s.id in data.favoriteSongs,
                )
            }
        }
        if (album.genres.isNotEmpty()) {
            item {
                Text(
                    album.genres.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.foregroundMuted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                )
            }
        }
        if (moreByArtist.isNotEmpty()) {
            item { AlbumCarousel("Mais de ${album.artist}", moreByArtist) }
        }
    }
}

// ----------------------------------------------------------------------
// Artista
// ----------------------------------------------------------------------

@Composable
fun ArtistScreen(id: String) {
    val lib by Graph.library.library.collectAsState()
    val data by Graph.userData.data.collectAsState()
    val player by Graph.player.state.collectAsState()
    val nav = LocalNavigator.current
    val sheets = LocalSheets.current
    val artist = lib.artistsById[id] ?: return NotFound("Artista")
    var showAllTop by rememberSaveable { mutableStateOf(false) }

    val allSongs = remember(artist) {
        artist.albums.flatMap { it.songs } + artist.songs.filter { s -> artist.albums.none { it.id == s.albumId } }
    }
    val top = remember(artist, data.playCounts) {
        val played = artist.songs.filter { (data.playCounts[it.id] ?: 0) > 0 }
            .sortedByDescending { data.playCounts[it.id] ?: 0 }
        val playedIds = played.mapTo(HashSet()) { it.id }
        (played + artist.songs.filter { it.id !in playedIds })
    }

    DetailScaffold(artist.name) {
        item {
            DetailHeader(
                kicker = "Artista",
                title = artist.name,
                color = artist.color,
                art = {
                    Artwork(
                        artist.artPath,
                        Modifier
                            .size(190.dp)
                            .shadow(18.dp, CircleShape),
                        CircleShape,
                        Icons.Rounded.Person,
                    )
                },
                subtitle = { SubtitleLine(listOf(artistSubtitle(artist))) },
            )
        }
        item {
            DetailActions(
                onPlay = { Graph.player.playSongs(allSongs) },
                onShuffle = { Graph.player.playSongs(allSongs, shuffle = true) },
                isFavorite = artist.id in data.favoriteArtists,
                onFavorite = { Graph.userData.toggleFavoriteArtist(artist.id) },
                onMore = { sheets.show(Sheet.ArtistMenu(artist)) },
            )
        }
        item {
            SectionHeader("Principais músicas") {
                if (top.size > 5) {
                    TextButton(onClick = { showAllTop = !showAllTop }) {
                        Text(if (showAllTop) "Mostrar menos" else "Ver todas")
                    }
                }
            }
        }
        val shown = if (showAllTop) top else top.take(5)
        itemsIndexed(shown, key = { _, s -> "top-" + s.id }) { i, s ->
            SongRow(
                song = s,
                artPath = lib.artPath(s.albumId),
                subtitle = s.album,
                onClick = { Graph.player.playSongs(top, i) },
                onMore = { sheets.show(Sheet.SongMenu(s)) },
                isCurrent = player.current?.id == s.id,
                isFavorite = s.id in data.favoriteSongs,
            )
        }
        if (artist.albums.isNotEmpty()) {
            item { SectionHeader("Álbuns") }
            albumGridRows(artist.albums) { a -> nav.album(a.id) }
        }
        if (artist.appearsOn.isNotEmpty()) {
            item { AlbumCarousel("Aparece em", artist.appearsOn) }
        }
    }
}

/** Grade de 2 colunas dentro de uma LazyColumn. */
fun LazyListScope.albumGridRows(albums: List<Album>, onClick: (Album) -> Unit) {
    items(albums.chunked(2), key = { row -> "row-" + row.first().id }) { row ->
        val sheets = LocalSheets.current
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
            row.forEach { a ->
                AlbumCard(
                    album = a,
                    onClick = { onClick(a) },
                    onLongClick = { sheets.show(Sheet.AlbumMenu(a)) },
                    subtitle = if (a.year > 0) a.year.toString() else a.artist,
                    modifier = Modifier.weight(1f),
                )
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

// ----------------------------------------------------------------------
// Gênero
// ----------------------------------------------------------------------

@Composable
fun GenreScreen(id: String) {
    val lib by Graph.library.library.collectAsState()
    val data by Graph.userData.data.collectAsState()
    val player by Graph.player.state.collectAsState()
    val sheets = LocalSheets.current
    val genre = lib.genresById[id] ?: return NotFound("Gênero")

    DetailScaffold(genre.name) {
        item {
            DetailHeader(
                kicker = "Gênero",
                title = genre.name,
                color = genre.color,
                art = {
                    Artwork(
                        genre.artPath,
                        Modifier
                            .size(170.dp)
                            .shadow(18.dp, RoundedCornerShape(10.dp)),
                        RoundedCornerShape(10.dp),
                    )
                },
                subtitle = { SubtitleLine(listOf(albumCountLabel(genre.albums.size), songCountLabel(genre.songs.size))) },
            )
        }
        item {
            DetailActions(
                onPlay = { Graph.player.playSongs(genre.songs) },
                onShuffle = { Graph.player.playSongs(genre.songs, shuffle = true) },
                onMore = { sheets.show(Sheet.SongsMenu(genre.name, genre.songs)) },
            )
        }
        if (genre.albums.isNotEmpty()) item { AlbumCarousel("Álbuns", genre.albums) }
        item { SectionHeader("Músicas") }
        itemsIndexed(genre.songs, key = { _, s -> s.id }) { i, s ->
            SongRow(
                song = s,
                artPath = lib.artPath(s.albumId),
                onClick = { Graph.player.playSongs(genre.songs, i) },
                onMore = { sheets.show(Sheet.SongMenu(s)) },
                isCurrent = player.current?.id == s.id,
                isFavorite = s.id in data.favoriteSongs,
            )
        }
    }
}

// ----------------------------------------------------------------------
// Pasta
// ----------------------------------------------------------------------

@Composable
fun FolderScreen(path: String) {
    val lib by Graph.library.library.collectAsState()
    val data by Graph.userData.data.collectAsState()
    val player by Graph.player.state.collectAsState()
    val nav = LocalNavigator.current
    val sheets = LocalSheets.current
    val folder = lib.foldersByPath[path] ?: return NotFound("Pasta")
    val all = remember(folder) { collectFolderSongs(folder) }
    val colors = Criol.colors

    Column(Modifier.fillMaxSize()) {
        TopBar(folder.name, onBack = { nav.back() })
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Folder, null, tint = colors.foregroundMuted, modifier = Modifier.size(16.dp))
                    Text(
                        "  " + folder.path,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.foregroundMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(4.dp))
                PlayShuffleBar(
                    songCountLabel(all.size),
                    { Graph.player.playSongs(all) },
                    { Graph.player.playSongs(all, shuffle = true) },
                )
            }
            items(folder.children, key = { "f-" + it.path }) { child -> FolderRow(child) { nav.folder(child.path) } }
            itemsIndexed(folder.songs, key = { _, s -> s.id }) { i, s: Song ->
                SongRow(
                    song = s,
                    artPath = lib.artPath(s.albumId),
                    onClick = { Graph.player.playSongs(folder.songs, i) },
                    onMore = { sheets.show(Sheet.SongMenu(s)) },
                    isCurrent = player.current?.id == s.id,
                    isFavorite = s.id in data.favoriteSongs,
                )
            }
        }
    }
}
