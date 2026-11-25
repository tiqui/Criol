package app.criol.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.criol.Graph
import app.criol.data.Library
import app.criol.ui.LocalNavigator
import app.criol.ui.LocalSheets
import app.criol.ui.Sheet
import app.criol.ui.components.AlbumCard
import app.criol.ui.components.ArtistCircle
import app.criol.ui.components.EmptyState
import app.criol.ui.components.SectionHeader
import app.criol.ui.components.SongRow
import app.criol.ui.theme.Criol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun SearchScreen() {
    val lib by Graph.library.library.collectAsState()
    val data by Graph.userData.data.collectAsState()
    val player by Graph.player.state.collectAsState()
    val nav = LocalNavigator.current
    val sheets = LocalSheets.current
    val colors = Criol.colors
    val focus = LocalFocusManager.current
    var query by rememberSaveable { mutableStateOf("") }
    var result by remember { mutableStateOf<Library.SearchResult?>(null) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(query, lib) {
        if (query.isBlank()) {
            result = null
            return@LaunchedEffect
        }
        delay(150)
        result = withContext(Dispatchers.Default) { lib.search(query) }
    }
    LaunchedEffect(Unit) { if (query.isEmpty()) runCatching { focusRequester.requestFocus() } }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Músicas, álbuns ou artistas") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Limpar") }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { focus.clearFocus() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = colors.surfaceHigh,
                    unfocusedContainerColor = colors.surfaceHigh,
                    focusedIndicatorColor = colors.surfaceHigh,
                    unfocusedIndicatorColor = colors.surfaceHigh,
                    cursorColor = colors.primary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        }

        val r = result
        when {
            query.isBlank() -> {
                LazyColumn(Modifier.imePadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    if (lib.genres.isNotEmpty()) {
                        item { SectionHeader("Navegar por gênero") }
                        val rows = lib.genres.chunked(2)
                        items(rows) { row ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 5.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                row.forEach { g ->
                                    GenreTile(g, Modifier.weight(1f).height(92.dp)) { nav.genre(g.id) }
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            r != null && r.songs.isEmpty() && r.albums.isEmpty() && r.artists.isEmpty() -> {
                EmptyState(Icons.Rounded.SearchOff, "Nada encontrado", "Tente outro termo.")
            }
            r != null -> {
                LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    if (r.artists.isNotEmpty()) {
                        item {
                            SectionHeader("Artistas")
                            LazyRow(contentPadding = PaddingValues(horizontal = 10.dp)) {
                                items(r.artists, key = { it.id }) { a ->
                                    ArtistCircle(
                                        a, { focus.clearFocus(); nav.artist(a.id) },
                                        { sheets.show(Sheet.ArtistMenu(a)) },
                                        Modifier.width(112.dp),
                                    )
                                }
                            }
                        }
                    }
                    if (r.albums.isNotEmpty()) {
                        item {
                            SectionHeader("Álbuns")
                            LazyRow(contentPadding = PaddingValues(horizontal = 10.dp)) {
                                items(r.albums, key = { it.id }) { a ->
                                    AlbumCard(
                                        a, { focus.clearFocus(); nav.album(a.id) },
                                        { sheets.show(Sheet.AlbumMenu(a)) },
                                        Modifier.width(140.dp),
                                    )
                                }
                            }
                        }
                    }
                    if (r.songs.isNotEmpty()) {
                        item { SectionHeader("Músicas") }
                        items(r.songs, key = { it.id }) { s ->
                            SongRow(
                                song = s,
                                artPath = lib.artPath(s.albumId),
                                onClick = { focus.clearFocus(); Graph.player.playSongs(r.songs, r.songs.indexOf(s)) },
                                onMore = { sheets.show(Sheet.SongMenu(s)) },
                                isCurrent = player.current?.id == s.id,
                                isFavorite = s.id in data.favoriteSongs,
                            )
                        }
                    }
                }
            }
        }
    }
}
