package app.criol.ui.screens

import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.criol.Graph
import app.criol.data.formatLongDuration
import app.criol.data.songCountLabel
import app.criol.insights.Mix
import app.criol.insights.MixKind
import app.criol.insights.mixText
import app.criol.ui.LocalNavigator
import app.criol.ui.LocalSheets
import app.criol.ui.Sheet
import app.criol.ui.components.DetailActions
import app.criol.ui.components.DetailHeader
import app.criol.ui.components.EmptyState
import app.criol.ui.components.MixCover
import app.criol.ui.components.SongRow
import app.criol.ui.components.TopBar
import app.criol.ui.components.mixColor
import app.criol.ui.theme.Criol
import kotlinx.coroutines.delay
import java.time.LocalTime

private fun greeting(name: String): String {
    val hour = LocalTime.now().hour
    val base = when {
        hour < 5 -> "Boa noite"
        hour < 12 -> "Bom dia"
        hour < 18 -> "Boa tarde"
        else -> "Boa noite"
    }
    return if (name.isBlank()) base else "$base, ${name.trim()}"
}

private fun mixSongs(mix: Mix) = Graph.library.current.let { lib -> mix.songIds.mapNotNull { lib.songsById[it] } }

/** Seção "Feito para você" do Início: daylist, mixes do dia e mixes por gênero. */
@Composable
fun MadeForYouSection() {
    val mixes by Graph.mixes.mixes.collectAsState()
    val busy by Graph.mixes.busy.collectAsState()
    val profile by Graph.profile.profile.collectAsState()
    val colors = Criol.colors

    // Confere a cada minuto: a daylist acompanha o relógio.
    LaunchedEffect(Unit) {
        while (true) {
            Graph.mixes.ensureFresh()
            delay(60_000)
        }
    }

    val visible = mixes.filter { it.songIds.isNotEmpty() }
    val featured = visible.filter { it.kind != MixKind.GENRE }
    val genreMixes = visible.filter { it.kind == MixKind.GENRE }

    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 20.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(Modifier.weight(1f)) {
                Text(greeting(profile.displayName), style = MaterialTheme.typography.bodyMedium, color = colors.foregroundMuted)
                Text("Feito para você", style = MaterialTheme.typography.headlineSmall, color = colors.foreground)
            }
            val spin = rememberInfiniteTransition(label = "refresh")
            val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "angle")
            IconButton(onClick = { if (!busy) Graph.mixes.ensureFresh(force = true) }) {
                Icon(
                    Icons.Rounded.Refresh, "Gerar mixes novamente",
                    tint = colors.foregroundMuted,
                    modifier = if (busy) Modifier.rotate(angle) else Modifier,
                )
            }
        }
        Text(
            "100% você",
            style = MaterialTheme.typography.titleMedium,
            color = colors.foreground,
            modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 4.dp),
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 10.dp)) {
            if (featured.isEmpty()) {
                items(4) { MixCardPlaceholder() }
            } else {
                items(featured, key = { it.id }) { MixCard(it) }
            }
        }
        if (genreMixes.isNotEmpty()) {
            Text(
                "Suas mixes",
                style = MaterialTheme.typography.titleMedium,
                color = colors.foreground,
                modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp),
            )
            LazyRow(contentPadding = PaddingValues(horizontal = 10.dp)) {
                items(genreMixes, key = { it.id }) { MixCard(it) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MixCard(mix: Mix) {
    val nav = LocalNavigator.current
    val sheets = LocalSheets.current
    val colors = Criol.colors
    val text = mixText(mix)
    Column(
        Modifier
            .width(162.dp)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = { nav.mix(mix.id) },
                onLongClick = { sheets.show(Sheet.SongsMenu(text.title, mixSongs(mix))) },
            )
            .padding(6.dp),
    ) {
        Box {
            MixCover(mix, text.coverLabel, Modifier.fillMaxWidth())
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(38.dp)
                    .shadow(6.dp, CircleShape)
                    .clip(CircleShape)
                    .background(colors.primary)
                    .clickable { Graph.player.playSongs(mixSongs(mix)) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.PlayArrow, "Tocar", tint = colors.onPrimary, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text.title,
            style = MaterialTheme.typography.titleSmall,
            color = colors.foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text.description,
            style = MaterialTheme.typography.bodySmall,
            color = colors.foregroundMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MixCardPlaceholder() {
    val colors = Criol.colors
    Column(Modifier.width(162.dp).padding(6.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(colors.surfaceHigh))
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth(0.7f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(colors.surfaceHigh))
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth(0.9f).height(10.dp).clip(RoundedCornerShape(4.dp)).background(colors.surfaceHigh))
    }
}

// ----------------------------------------------------------------------
// Página da mix
// ----------------------------------------------------------------------

@Composable
fun MixScreen(id: String) {
    val mixes by Graph.mixes.mixes.collectAsState()
    val busy by Graph.mixes.busy.collectAsState()
    val lib by Graph.library.library.collectAsState()
    val data by Graph.userData.data.collectAsState()
    val currentId by Graph.player.currentId.collectAsState()
    val nav = LocalNavigator.current
    val sheets = LocalSheets.current
    val context = LocalContext.current
    val colors = Criol.colors

    LaunchedEffect(Unit) { Graph.mixes.ensureFresh() }

    val mix = mixes.firstOrNull { it.id == id }
    if (mix == null) {
        Column(Modifier.fillMaxSize()) {
            TopBar("", onBack = { nav.back() })
            if (busy || mixes.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.primary)
                }
            } else {
                EmptyState(
                    Icons.Rounded.AutoAwesome,
                    "Mix não encontrada",
                    "Essa mix não existe mais. Volte para o Início para gerar novas.",
                )
            }
        }
        return
    }

    val text = mixText(mix)
    val songs = remember(mix, lib) { mix.songIds.mapNotNull { lib.songsById[it] } }
    var menuOpen by remember { mutableStateOf(false) }
    val updated = DateUtils.getRelativeTimeSpanString(mix.generatedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)

    DetailScaffold(text.title) {
        item {
            DetailHeader(
                kicker = "Playlist • Feito para você",
                title = text.title,
                color = mixColor(mix),
                art = {
                    MixCover(
                        mix, text.coverLabel,
                        Modifier
                            .size(230.dp)
                            .shadow(18.dp, RoundedCornerShape(12.dp)),
                        large = true,
                    )
                },
                subtitle = {
                    Column {
                        Text(text.description, style = MaterialTheme.typography.bodyMedium, color = colors.foreground.copy(alpha = 0.85f))
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${songCountLabel(songs.size)} • ${formatLongDuration(songs.sumOf { it.durationMs })} • atualizada $updated",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.foreground.copy(alpha = 0.7f),
                        )
                    }
                },
            )
        }
        item {
            Box {
                DetailActions(
                    onPlay = { Graph.player.playSongs(songs) },
                    onShuffle = { Graph.player.playSongs(songs, shuffle = true) },
                    onMore = { menuOpen = true },
                )
                Box(Modifier.align(Alignment.TopEnd)) {
                    DropdownMenu(menuOpen, { menuOpen = false }, containerColor = colors.surface) {
                        DropdownMenuItem(
                            text = { Text("Gerar esta mix novamente") },
                            leadingIcon = { Icon(Icons.Rounded.Refresh, null) },
                            enabled = !busy,
                            onClick = { menuOpen = false; Graph.mixes.regenerate(mix.id) },
                        )
                        DropdownMenuItem(
                            text = { Text("Salvar como playlist") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) },
                            onClick = {
                                menuOpen = false
                                Graph.userData.createPlaylist(text.title, songs.map { it.id })
                                Toast.makeText(context, "Playlist \"${text.title}\" salva", Toast.LENGTH_SHORT).show()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Adicionar à fila") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null) },
                            onClick = { menuOpen = false; Graph.player.addToQueue(songs) },
                        )
                    }
                }
            }
        }
        if (!mix.personalized) {
            item {
                Row(
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.primary.copy(alpha = 0.12f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Info, null, tint = colors.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Ainda aprendendo seu gosto: por enquanto esta mix usa as contagens de reprodução e a " +
                            "biblioteca. Ela fica mais pessoal conforme você ouve.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.foreground,
                    )
                }
            }
        }
        if (songs.isEmpty()) {
            item {
                EmptyState(Icons.Rounded.AutoAwesome, "Mix vazia", "Toque em \"Gerar esta mix novamente\" no menu.")
            }
        }
        itemsIndexed(songs, key = { _, s -> s.id }) { i, s ->
            SongRow(
                song = s,
                artPath = lib.artPath(s.albumId),
                onClick = { Graph.player.playSongs(songs, i) },
                onMore = { sheets.show(Sheet.SongMenu(s)) },
                isCurrent = currentId == s.id,
                isFavorite = s.id in data.favoriteSongs,
            )
        }
    }
}
