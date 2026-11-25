package app.criol.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.criol.Graph
import app.criol.data.Library
import app.criol.insights.MonthlyRecap
import app.criol.insights.buildMonthlyRecap
import app.criol.insights.currentMonthKey
import app.criol.insights.formatListenMinutes
import app.criol.insights.formatNumber
import app.criol.insights.msToMinutes
import app.criol.insights.playsLabel
import app.criol.ui.LocalNavigator
import app.criol.ui.components.Artwork
import app.criol.ui.components.Bar
import app.criol.ui.components.BarStrip
import app.criol.ui.components.EmptyState
import app.criol.ui.components.InsightCard
import app.criol.ui.components.TopBar
import app.criol.ui.theme.Criol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val ptBR: Locale = Locale.forLanguageTag("pt-BR")

/** "Novembro 2026" (ou só "novembro"). */
fun monthTitle(month: String, withYear: Boolean = true): String {
    val ym = runCatching { YearMonth.parse(month) }.getOrNull() ?: return month
    val name = ym.month.getDisplayName(TextStyle.FULL, ptBR)
    return if (withYear) name.replaceFirstChar { it.titlecase(ptBR) } + " " + ym.year else name
}

private val BlueHighlight = Color(0xFF2FB8FF)
private val YellowHighlight = Color(0xFFFFD23F)

private fun Library.artistArt(name: String, albumId: String) =
    artistsById[artistIdFor(name)]?.artPath ?: artPath(albumId)

@Composable
fun RecapCard(recap: MonthlyRecap, partial: Boolean = false, isNew: Boolean = false) {
    val nav = LocalNavigator.current
    val colors = Criol.colors
    val lib = Graph.library.current
    val topArtist = recap.topArtists.firstOrNull()
    val topSong = recap.topSongs.firstOrNull()
    Column(
        Modifier
            .width(290.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceHigh)
            .clickable { nav.recap(recap.month) }
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(monthTitle(recap.month, withYear = false).replaceFirstChar { it.titlecase(ptBR) }, style = MaterialTheme.typography.titleLarge, color = colors.foreground)
            Spacer(Modifier.width(6.dp))
            Text(recap.month.take(4), style = MaterialTheme.typography.titleMedium, color = colors.foregroundMuted)
            Spacer(Modifier.weight(1f))
            if (partial) Chip("Até agora", colors.foreground.copy(alpha = 0.12f), colors.foreground)
            if (isNew) Chip("Novo", colors.primary, colors.onPrimary)
        }
        Spacer(Modifier.height(10.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.18f)).padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tempo ouvindo", style = MaterialTheme.typography.bodySmall, color = colors.foregroundMuted, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.ChevronRight, null, tint = colors.foregroundMuted, modifier = Modifier.size(18.dp))
            }
            Text(formatListenMinutes(recap.totalMs), style = MaterialTheme.typography.headlineMedium, color = colors.foreground)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HighlightTile(
                "Artista mais ouvido", topArtist?.name ?: "—", BlueHighlight,
                topArtist?.let { lib.artistArt(it.name, it.albumId) }, circle = true, modifier = Modifier.weight(1f),
            )
            HighlightTile(
                "Música mais ouvida", topSong?.name ?: "—", YellowHighlight,
                topSong?.let { lib.artPath(it.albumId) }, circle = false, modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Chip(text: String, background: Color, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .padding(start = 6.dp)
            .clip(RoundedCornerShape(50))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun HighlightTile(label: String, value: String, color: Color, art: String?, circle: Boolean, modifier: Modifier) {
    val colors = Criol.colors
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.18f)).padding(10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.foregroundMuted, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = color, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        Artwork(art, Modifier.size(56.dp), if (circle) CircleShape else RoundedCornerShape(6.dp), if (circle) Icons.Rounded.Person else Icons.Rounded.Album)
    }
}

// ----------------------------------------------------------------------
// Página do resumo
// ----------------------------------------------------------------------

@Composable
fun RecapScreen(month: String) {
    val nav = LocalNavigator.current
    val profile by Graph.profile.profile.collectAsState()
    val entries by Graph.listening.entries.collectAsState()
    val lib by Graph.library.library.collectAsState()
    val context = LocalContext.current
    val colors = Criol.colors
    val saved = profile.recaps[month]
    val recap by produceState(saved, saved, entries, month) {
        value = saved ?: withContext(Dispatchers.Default) { runCatching { buildMonthlyRecap(entries, month) }.getOrNull() }
    }
    LaunchedEffect(saved != null) { if (saved != null) Graph.profile.markRecapSeen(month) }

    val r = recap
    if (r == null || r.totalMs <= 0) {
        Column(Modifier.fillMaxSize()) {
            TopBar("", onBack = { nav.back() })
            if (r != null) EmptyState(Icons.Rounded.PlayArrow, "Nada por aqui", "Nenhuma escuta registrada neste mês.")
        }
        return
    }

    val isCurrent = month == currentMonthKey()
    val accent = Color(profile.accent)
    val topSongs = r.topSongs.mapNotNull { lib.songsById[it.id] }
    val minutes = msToMinutes(r.totalMs)
    val change = r.previousTotalMs?.takeIf { it > 0 }?.let { ((r.totalMs - it) * 100.0 / it).roundToInt() }

    DetailScaffold(monthTitle(month)) {
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(accent.copy(alpha = 0.6f).compositeOver(colors.background), colors.background),
                        ),
                    )
                    .padding(horizontal = 20.dp),
            ) {
                Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                Spacer(Modifier.height(72.dp))
                Text(
                    (if (isCurrent) "Até agora" else "Resumo mensal").uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.foreground.copy(alpha = 0.75f),
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        monthTitle(month, withYear = false).replaceFirstChar { it.titlecase(ptBR) },
                        style = MaterialTheme.typography.displaySmall,
                        color = colors.foreground,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(month.take(4), style = MaterialTheme.typography.headlineSmall, color = colors.foregroundMuted)
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton("Tocar as mais ouvidas", Icons.Rounded.PlayArrow) {
                        if (topSongs.isNotEmpty()) Graph.player.playSongs(topSongs)
                    }
                    OutlinedButton(onClick = {
                        if (topSongs.isEmpty()) return@OutlinedButton
                        val name = "Resumo de ${monthTitle(month, withYear = false)} ${month.take(4)}"
                        Graph.userData.createPlaylist(name, topSongs.map { it.id })
                        Toast.makeText(context, "Playlist \"$name\" salva", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Salvar")
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }

        item {
            InsightCard {
                Text("Tempo ouvindo", style = MaterialTheme.typography.bodySmall, color = colors.foregroundMuted)
                Text(
                    if (minutes == 1) "1 minuto" else "${formatNumber(minutes)} minutos",
                    style = MaterialTheme.typography.displaySmall,
                    color = colors.foreground,
                )
                if (change != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (change >= 0) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward, null,
                            tint = if (change >= 0) Color(0xFF1DB954) else Color(0xFFE2453C),
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("${abs(change)}% em relação ao mês anterior", style = MaterialTheme.typography.bodySmall, color = colors.foregroundMuted)
                    }
                }
            }
        }

        r.topArtists.firstOrNull()?.let { top ->
            item {
                InsightCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Artwork(lib.artistArt(top.name, top.albumId), Modifier.size(76.dp), CircleShape, Icons.Rounded.Person)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("Artista mais ouvido", style = MaterialTheme.typography.bodySmall, color = colors.foregroundMuted)
                            Text(top.name, style = MaterialTheme.typography.headlineSmall, color = BlueHighlight, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${formatNumber(msToMinutes(top.listenedMs))} minutos • ${playsLabel(top.plays)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.foregroundMuted,
                            )
                        }
                    }
                }
            }
        }

        item {
            val facts = listOf(
                "Reproduções" to r.plays, "Músicas" to r.uniqueSongs, "Artistas" to r.uniqueArtists,
                "Gêneros" to r.uniqueGenres, "Dias ouvindo" to r.activeDays, "Artistas novos" to r.newArtists,
            )
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                facts.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (label, value) ->
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.surfaceHigh)
                                    .padding(12.dp),
                            ) {
                                Text(formatNumber(value), style = MaterialTheme.typography.headlineSmall, color = colors.foreground)
                                Text(label, style = MaterialTheme.typography.bodySmall, color = colors.foregroundMuted, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }

        item {
            val ym = YearMonth.parse(month)
            val bars = (1..ym.lengthOfMonth()).map { d ->
                val key = ym.atDay(d).toString()
                val ms = r.byDay[key] ?: 0L
                val date = LocalDate.parse(key).format(DateTimeFormatter.ofPattern("d 'de' MMM", ptBR))
                Bar(ms, if (d == 1 || d % 5 == 0) "$d" else "", "$date: ${msToMinutes(ms)} min", r.busiestDay?.day == key)
            }
            InsightCard(title = "Minutos por dia") {
                r.busiestDay?.let { b ->
                    val date = LocalDate.parse(b.day).format(DateTimeFormatter.ofPattern("d 'de' MMMM", ptBR))
                    val daypart = r.favoriteDaypart?.let { " Seu período favorito foi a ${it.label.lowercase(ptBR)}." }.orEmpty()
                    Text(
                        "Seu dia mais intenso foi $date, com ${msToMinutes(b.ms)} minutos.$daypart",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.foregroundMuted,
                    )
                    Spacer(Modifier.height(10.dp))
                }
                BarStrip(bars, 130.dp, color = accent)
            }
        }

        item {
            InsightCard(title = "Top músicas") {
                r.topSongs.forEachIndexed { i, s ->
                    RankRow(i + 1, s, lib.artPath(s.albumId), playsLabel(s.plays)) {
                        val idx = topSongs.indexOfFirst { it.id == s.id }
                        if (idx >= 0) Graph.player.playSongs(topSongs, idx)
                    }
                }
            }
        }
        item {
            InsightCard(title = "Top artistas") {
                r.topArtists.forEachIndexed { i, a ->
                    val artist = lib.artistsById[lib.artistIdFor(a.name)]
                    RankRow(i + 1, a, lib.artistArt(a.name, a.albumId), playsLabel(a.plays), Icons.Rounded.Person, circle = true,
                        onClick = artist?.let { { nav.artist(it.id) } })
                }
            }
        }
        item {
            InsightCard(title = "Álbuns mais ouvidos") {
                r.topAlbums.forEachIndexed { i, a ->
                    RankRow(i + 1, a, lib.artPath(a.id), playsLabel(a.plays), Icons.Rounded.Album,
                        onClick = if (lib.albumsById.containsKey(a.id)) ({ nav.album(a.id) }) else null)
                }
            }
        }
        if (r.topGenres.isNotEmpty()) {
            item {
                InsightCard(title = "Top gêneros") {
                    r.topGenres.forEachIndexed { i, g ->
                        val genre = lib.genres.firstOrNull { it.name.equals(g.name, ignoreCase = true) }
                        RankRow(i + 1, g.copy(subtitle = formatListenMinutes(g.listenedMs)), genre?.artPath, playsLabel(g.plays), Icons.Rounded.Category,
                            onClick = genre?.let { { nav.genre(it.id) } })
                    }
                }
            }
        }
    }
}
