package app.criol.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueuePlayNext
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.criol.Graph
import app.criol.data.Album
import app.criol.data.Artist
import app.criol.data.Song
import app.criol.data.formatDuration
import app.criol.data.songCountLabel
import app.criol.ui.components.Artwork
import app.criol.ui.components.RatingStars
import app.criol.ui.theme.Criol
import java.text.DateFormat
import java.util.Date

interface Navigator {
    fun album(id: String)
    fun artist(id: String)
    fun artistByName(name: String)
    fun genre(id: String)
    fun folder(path: String)
    fun playlist(id: String)
    fun settings()
    fun profile()
    fun recap(month: String)
    fun mix(id: String)
    fun back()
}

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("Navigator ausente") }

sealed interface Sheet {
    data class SongMenu(
        val song: Song,
        val queueIndex: Int? = null,
        val playlistId: String? = null,
        val playlistIndex: Int? = null,
    ) : Sheet

    data class AlbumMenu(val album: Album) : Sheet
    data class ArtistMenu(val artist: Artist) : Sheet
    data class SongsMenu(val title: String, val songs: List<Song>) : Sheet
    data class AddToPlaylist(val songs: List<Song>) : Sheet
    data class SongInfo(val song: Song) : Sheet
    data class NewPlaylist(val songs: List<Song>) : Sheet
}

class SheetController {
    var current by mutableStateOf<Sheet?>(null)
        private set

    fun show(sheet: Sheet) {
        current = sheet
    }

    fun dismiss() {
        current = null
    }
}

val LocalSheets = staticCompositionLocalOf { SheetController() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetHost(controller: SheetController) {
    val sheet = controller.current ?: return
    when (sheet) {
        is Sheet.SongInfo -> SongInfoDialog(sheet.song) { controller.dismiss() }
        is Sheet.NewPlaylist -> NewPlaylistDialog(sheet.songs) { controller.dismiss() }
        else -> {
            val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { controller.dismiss() },
                sheetState = state,
                containerColor = Criol.colors.surface,
                contentColor = Criol.colors.foreground,
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(bottom = 12.dp),
                ) {
                    when (sheet) {
                        is Sheet.SongMenu -> SongMenuContent(sheet, controller)
                        is Sheet.AlbumMenu -> AlbumMenuContent(sheet.album, controller)
                        is Sheet.ArtistMenu -> ArtistMenuContent(sheet.artist, controller)
                        is Sheet.SongsMenu -> SongsMenuContent(sheet, controller)
                        is Sheet.AddToPlaylist -> AddToPlaylistContent(sheet.songs, controller)
                        else -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Criol.colors.foregroundMuted, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(20.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Criol.colors.foreground)
    }
}

@Composable
private fun SheetHeader(artPath: String?, title: String, subtitle: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(artPath, Modifier.size(52.dp), RoundedCornerShape(6.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Criol.colors.foregroundMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    HorizontalDivider(Modifier.padding(top = 12.dp, bottom = 4.dp), color = Criol.colors.border)
}

private fun toast(context: android.content.Context, msg: String) =
    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

@Composable
private fun SongMenuContent(sheet: Sheet.SongMenu, controller: SheetController) {
    val song = sheet.song
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val player = Graph.player
    val data by Graph.userData.data.collectAsState()
    val settings by Graph.settings.settings.collectAsState()
    val lib = Graph.library.current
    val isFav = song.id in data.favoriteSongs

    SheetHeader(lib.artPath(song.albumId), song.title, "${song.artist} • ${song.album}")
    if (settings.showRatings) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Avaliação", color = Criol.colors.foregroundMuted, modifier = Modifier.weight(1f).padding(start = 6.dp))
            RatingStars(data.ratings[song.id] ?: 0, { Graph.userData.setRating(song.id, it) })
        }
    }
    if (sheet.queueIndex == null) {
        SheetItem(Icons.Rounded.QueuePlayNext, "Tocar a seguir") {
            player.playNext(listOf(song)); toast(context, "Vai tocar a seguir"); controller.dismiss()
        }
        SheetItem(Icons.AutoMirrored.Rounded.QueueMusic, "Adicionar à fila") {
            player.addToQueue(listOf(song)); toast(context, "Adicionada à fila"); controller.dismiss()
        }
    }
    SheetItem(Icons.Rounded.Radio, "Rádio da música") {
        player.startRadio(song); toast(context, "Tocando o rádio de “${song.title}”"); controller.dismiss()
    }
    SheetItem(Icons.AutoMirrored.Rounded.PlaylistAdd, "Adicionar à playlist") {
        controller.show(Sheet.AddToPlaylist(listOf(song)))
    }
    SheetItem(
        if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
        if (isFav) "Remover dos favoritos" else "Adicionar aos favoritos",
    ) { Graph.userData.toggleFavoriteSong(song.id); controller.dismiss() }
    if (lib.albumsById.containsKey(song.albumId)) {
        SheetItem(Icons.Rounded.Album, "Ir para o álbum") { controller.dismiss(); nav.album(song.albumId) }
    }
    song.artists.take(3).forEach { name ->
        SheetItem(Icons.Rounded.Person, if (song.artists.size > 1) "Ir para $name" else "Ir para o artista") {
            controller.dismiss(); nav.artistByName(name)
        }
    }
    if (sheet.queueIndex != null) {
        SheetItem(Icons.Rounded.RemoveCircleOutline, "Remover da fila") {
            player.removeAt(sheet.queueIndex); controller.dismiss()
        }
    }
    if (sheet.playlistId != null && sheet.playlistIndex != null) {
        SheetItem(Icons.Rounded.RemoveCircleOutline, "Remover da playlist") {
            Graph.userData.removeFromPlaylist(sheet.playlistId, sheet.playlistIndex); controller.dismiss()
        }
    }
    SheetItem(Icons.Rounded.Info, "Informações") { controller.show(Sheet.SongInfo(song)) }
}

@Composable
private fun AlbumMenuContent(album: Album, controller: SheetController) {
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val player = Graph.player
    val data by Graph.userData.data.collectAsState()
    val isFav = album.id in data.favoriteAlbums
    SheetHeader(album.artPath, album.name, album.artist)
    SheetItem(Icons.Rounded.PlayArrow, "Tocar") { player.playSongs(album.songs); controller.dismiss() }
    SheetItem(Icons.Rounded.Shuffle, "Tocar aleatório") { player.playSongs(album.songs, shuffle = true); controller.dismiss() }
    SheetItem(Icons.Rounded.QueuePlayNext, "Tocar a seguir") {
        player.playNext(album.songs); toast(context, "Vai tocar a seguir"); controller.dismiss()
    }
    SheetItem(Icons.AutoMirrored.Rounded.QueueMusic, "Adicionar à fila") {
        player.addToQueue(album.songs); toast(context, "Adicionado à fila"); controller.dismiss()
    }
    SheetItem(Icons.AutoMirrored.Rounded.PlaylistAdd, "Adicionar à playlist") {
        controller.show(Sheet.AddToPlaylist(album.songs))
    }
    SheetItem(
        if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
        if (isFav) "Remover dos favoritos" else "Adicionar aos favoritos",
    ) { Graph.userData.toggleFavoriteAlbum(album.id); controller.dismiss() }
    album.artistNames.take(3).forEach { name ->
        if (name != "Vários artistas") {
            SheetItem(Icons.Rounded.Person, "Ir para $name") { controller.dismiss(); nav.artistByName(name) }
        }
    }
}

@Composable
private fun ArtistMenuContent(artist: Artist, controller: SheetController) {
    val context = LocalContext.current
    val player = Graph.player
    val data by Graph.userData.data.collectAsState()
    val isFav = artist.id in data.favoriteArtists
    val songs = artist.albums.flatMap { it.songs } + artist.songs.filter { s -> artist.albums.none { it.id == s.albumId } }
    SheetHeader(artist.artPath, artist.name, songCountLabel(artist.songs.size))
    SheetItem(Icons.Rounded.PlayArrow, "Tocar tudo") { player.playSongs(songs); controller.dismiss() }
    SheetItem(Icons.Rounded.Shuffle, "Tocar aleatório") { player.playSongs(songs, shuffle = true); controller.dismiss() }
    SheetItem(Icons.Rounded.QueuePlayNext, "Tocar a seguir") {
        player.playNext(songs); toast(context, "Vai tocar a seguir"); controller.dismiss()
    }
    SheetItem(Icons.AutoMirrored.Rounded.QueueMusic, "Adicionar à fila") {
        player.addToQueue(songs); toast(context, "Adicionado à fila"); controller.dismiss()
    }
    SheetItem(
        if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
        if (isFav) "Remover dos favoritos" else "Adicionar aos favoritos",
    ) { Graph.userData.toggleFavoriteArtist(artist.id); controller.dismiss() }
}

@Composable
private fun SongsMenuContent(sheet: Sheet.SongsMenu, controller: SheetController) {
    val context = LocalContext.current
    val player = Graph.player
    val lib = Graph.library.current
    SheetHeader(sheet.songs.firstNotNullOfOrNull { lib.artPath(it.albumId) }, sheet.title, songCountLabel(sheet.songs.size))
    SheetItem(Icons.Rounded.PlayArrow, "Tocar") { player.playSongs(sheet.songs); controller.dismiss() }
    SheetItem(Icons.Rounded.Shuffle, "Tocar aleatório") { player.playSongs(sheet.songs, shuffle = true); controller.dismiss() }
    SheetItem(Icons.Rounded.QueuePlayNext, "Tocar a seguir") {
        player.playNext(sheet.songs); toast(context, "Vai tocar a seguir"); controller.dismiss()
    }
    SheetItem(Icons.AutoMirrored.Rounded.QueueMusic, "Adicionar à fila") {
        player.addToQueue(sheet.songs); toast(context, "Adicionado à fila"); controller.dismiss()
    }
    SheetItem(Icons.AutoMirrored.Rounded.PlaylistAdd, "Adicionar à playlist") {
        controller.show(Sheet.AddToPlaylist(sheet.songs))
    }
}

@Composable
private fun AddToPlaylistContent(songs: List<Song>, controller: SheetController) {
    val context = LocalContext.current
    val data by Graph.userData.data.collectAsState()
    Text(
        "Adicionar à playlist",
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
    SheetItem(Icons.Rounded.Add, "Nova playlist") { controller.show(Sheet.NewPlaylist(songs)) }
    HorizontalDivider(color = Criol.colors.border)
    if (data.playlists.isEmpty()) {
        Text(
            "Você ainda não tem playlists.",
            color = Criol.colors.foregroundMuted,
            modifier = Modifier.padding(24.dp),
        )
    }
    data.playlists.sortedBy { it.name.lowercase() }.forEach { p ->
        SheetItem(Icons.AutoMirrored.Rounded.QueueMusic, "${p.name}  ·  ${songCountLabel(p.songIds.size)}") {
            Graph.userData.addToPlaylist(p.id, songs.map { it.id })
            toast(context, "Adicionado a ${p.name}")
            controller.dismiss()
        }
    }
}

@Composable
fun NameDialog(
    title: String,
    initial: String = "",
    confirm: String = "Salvar",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Criol.colors.surface,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text("Nome") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text) }, enabled = text.isNotBlank()) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun NewPlaylistDialog(songs: List<Song>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    NameDialog(title = "Nova playlist", confirm = "Criar", onDismiss = onDismiss) { name ->
        Graph.userData.createPlaylist(name, songs.map { it.id })
        if (songs.isNotEmpty()) toast(context, "Playlist \"$name\" criada")
        onDismiss()
    }
}

@Composable
private fun SongInfoDialog(song: Song, onDismiss: () -> Unit) {
    val data by Graph.userData.data.collectAsState()
    val df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    val rows = buildList {
        add("Título" to song.title)
        add("Artista" to song.artist)
        add("Álbum" to song.album)
        if (song.albumArtist.isNotBlank()) add("Artista do álbum" to song.albumArtist)
        if (song.composer.isNotBlank()) add("Compositor" to song.composer)
        if (song.genre.isNotBlank()) add("Gênero" to song.genre)
        if (song.year > 0) add("Ano" to song.year.toString())
        if (song.track > 0) add("Faixa" to song.track.toString())
        if (song.disc > 0) add("Disco" to song.disc.toString())
        add("Duração" to formatDuration(song.durationMs))
        add("Formato" to song.mime.substringAfter('/').uppercase().ifBlank { song.fileName.substringAfterLast('.').uppercase() })
        if (song.bitrate > 0) add("Bitrate" to "${song.bitrate / 1000} kbps")
        if (song.sampleRate > 0) add("Taxa de amostragem" to "%.1f kHz".format(song.sampleRate / 1000f))
        add("Tamanho" to "%.1f MB".format(song.size / 1_048_576f))
        add("Pasta" to song.folder)
        add("Arquivo" to song.fileName)
        add("Letra" to if (song.lyricsUri != null) "Arquivo de letra" else "—")
        add("Reproduções" to (data.playCounts[song.id] ?: 0).toString())
        data.lastPlayed[song.id]?.let { add("Última reprodução" to df.format(Date(it))) }
        add("Adicionada em" to df.format(Date(song.dateAdded)))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Criol.colors.surface,
        title = { Text("Informações") },
        text = {
            LazyColumn(Modifier.height(420.dp)) {
                items(rows) { (k, v) ->
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Text(k, style = MaterialTheme.typography.labelSmall, color = Criol.colors.foregroundMuted)
                        Text(v, style = MaterialTheme.typography.bodyMedium, color = Criol.colors.foreground)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
    )
}
