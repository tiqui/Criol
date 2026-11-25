package app.criol.ui.screens

import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.criol.Graph
import app.criol.insights.Achievement
import app.criol.insights.ListenEntry
import app.criol.insights.ListenerDna
import app.criol.insights.ListeningSummary
import app.criol.insights.MonthlyRecap
import app.criol.insights.ProfileAccents
import app.criol.insights.RankedItem
import app.criol.insights.Streaks
import app.criol.insights.achievements
import app.criol.insights.buildMonthlyRecap
import app.criol.insights.currentMonthKey
import app.criol.insights.daysLabel
import app.criol.insights.favoriteDaypart
import app.criol.insights.formatListenMinutes
import app.criol.insights.formatNumber
import app.criol.insights.listenerDna
import app.criol.insights.msToMinutes
import app.criol.insights.playsLabel
import app.criol.insights.streaks
import app.criol.insights.summarize
import app.criol.ui.LocalNavigator
import app.criol.ui.components.Artwork
import app.criol.ui.components.Bar
import app.criol.ui.components.BarStrip
import app.criol.ui.components.InsightCard
import app.criol.ui.components.Meter
import app.criol.ui.components.Pill
import app.criol.ui.components.ProfileAvatar
import app.criol.ui.components.SectionHeader
import app.criol.ui.components.icon
import app.criol.ui.theme.Criol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val ptBR: Locale = Locale.forLanguageTag("pt-BR")

/** Tudo o que a tela de perfil calcula a partir do histórico (feito fora da thread principal). */
private class ProfileData(
    val summary: ListeningSummary,
    val dna: ListenerDna,
    val achievements: List<Achievement>,
    val streaks: Streaks,
    val currentRecap: MonthlyRecap?,
)

private fun computeProfile(entries: List<ListenEntry>): ProfileData {
    val summary = summarize(entries, 10)
    val recap = buildMonthlyRecap(entries, currentMonthKey())
    return ProfileData(
        summary = summary,
        dna = listenerDna(entries, summary),
        achievements = achievements(entries, summary),
        streaks = streaks(entries),
        currentRecap = recap.takeIf { it.totalMs > 0 },
    )
}

@Composable
fun ProfileScreen() {
    val entries by Graph.listening.entries.collectAsState()
    val loaded by Graph.listening.loaded.collectAsState()
    val profile by Graph.profile.profile.collectAsState()
    val data by produceState<ProfileData?>(null, entries) {
        value = withContext(Dispatchers.Default) { computeProfile(entries) }
    }
    val name = profile.displayName.ifBlank { "Você" }
    var editing by remember { mutableStateOf(false) }

    DetailScaffold(name) {
        item { ProfileHero(data, onEdit = { editing = true }) }
        val d = data ?: return@DetailScaffold
        item { StatTiles(d) }
        if (loaded && entries.isEmpty()) item { EmptyProfile() }
        item { DnaCard(d.dna) }
        item { ClockCard(d.summary) }
        item { TopLists(entries) }
        item { Recaps(d.currentRecap) }
        item { Achievements(d.achievements) }
        item { RecentListens(entries) }
        item { HistoryData(entries.size) }
    }

    if (editing) EditProfileDialog { editing = false }
}

// ----------------------------------------------------------------------
// Cabeçalho
// ----------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileHero(data: ProfileData?, onEdit: () -> Unit) {
    val profile by Graph.profile.profile.collectAsState()
    val colors = Criol.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = Color(profile.accent)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            runCatching { Graph.profile.setAvatar(uri) }
                .onFailure { Toast.makeText(context, "Não foi possível usar essa imagem", Toast.LENGTH_SHORT).show() }
        }
    }
    val pinned = data?.achievements?.firstOrNull { it.kind.name == profile.pinnedBadge && it.unlocked }
    val since = data?.summary?.firstListenAt ?: profile.createdAt
    val sinceText = Instant.ofEpochMilli(since).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MMMM 'de' yyyy", ptBR))

    Column(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        accent.copy(alpha = 0.65f).compositeOver(colors.background),
                        accent.copy(alpha = 0.18f).compositeOver(colors.background),
                        colors.background,
                    ),
                ),
            )
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Spacer(Modifier.height(64.dp))
        Box(Modifier.clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
            ProfileAvatar(120.dp)
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(colors.surface)
                    .border(2.dp, colors.background, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.CameraAlt, "Trocar foto", tint = colors.foreground, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("PERFIL", style = MaterialTheme.typography.labelSmall, color = colors.foreground.copy(alpha = 0.75f))
        Text(
            profile.displayName.ifBlank { "Você" },
            style = MaterialTheme.typography.displaySmall,
            color = colors.foreground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (profile.bio.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(profile.bio, style = MaterialTheme.typography.bodyMedium, color = colors.foreground.copy(alpha = 0.85f))
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            data?.let { Pill(it.dna.archetype.label, it.dna.archetype.icon) }
            pinned?.let { Pill(it.kind.label, it.kind.icon, color = Color.Black, background = Color(0xFFFFD23F)) }
        }
        Spacer(Modifier.height(8.dp))
        Text("Ouvinte desde $sinceText", style = MaterialTheme.typography.bodySmall, color = colors.foregroundMuted)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onEdit) {
            Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Editar perfil")
        }
        Spacer(Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditProfileDialog(onDismiss: () -> Unit) {
    val profile = Graph.profile.value
    val colors = Criol.colors
    var name by remember { mutableStateOf(profile.displayName) }
    var bio by remember { mutableStateOf(profile.bio) }
    var accent by remember { mutableStateOf(profile.accent) }
    val hasAvatar = Graph.profile.avatarPath(profile) != null

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text("Editar perfil") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("Nome de exibição") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it.take(160) },
                    label = { Text("Bio") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                Text("Cor do perfil", style = MaterialTheme.typography.titleSmall, color = colors.foreground)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProfileAccents.forEach { c ->
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .border(if (c == accent) 3.dp else 0.dp, colors.foreground, CircleShape)
                                .clickable { accent = c },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (c == accent) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                if (hasAvatar) {
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = { Graph.profile.removeAvatar() }) { Text("Remover foto") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { Graph.profile.update(name, bio, accent); onDismiss() }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

// ----------------------------------------------------------------------
// Números
// ----------------------------------------------------------------------

@Composable
private fun StatTiles(d: ProfileData) {
    val colors = Criol.colors
    val tiles = listOf(
        Triple(Icons.Rounded.Schedule, "Minutos ouvidos", formatNumber(msToMinutes(d.summary.totalMs))),
        Triple(Icons.Rounded.PlayArrow, "Reproduções", formatNumber(d.summary.plays)),
        Triple(Icons.Rounded.Person, "Artistas", formatNumber(d.summary.uniqueArtists)),
        Triple(Icons.Rounded.LocalFireDepartment, "Sequência atual", daysLabel(d.streaks.current)),
        Triple(Icons.Rounded.EmojiEvents, "Maior sequência", daysLabel(d.streaks.longest)),
    )
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        tiles.forEach { (icon, label, value) ->
            Column(
                Modifier
                    .width(132.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surfaceHigh)
                    .padding(14.dp),
            ) {
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(colors.primary.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, null, tint = colors.primary, modifier = Modifier.size(20.dp)) }
                Spacer(Modifier.height(10.dp))
                Text(value, style = MaterialTheme.typography.headlineSmall, color = colors.foreground, maxLines = 1)
                Text(label, style = MaterialTheme.typography.bodySmall, color = colors.foregroundMuted, maxLines = 1)
            }
        }
    }
}

@Composable
private fun EmptyProfile() {
    InsightCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = Criol.colors.primary, modifier = Modifier.size(36.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Seu perfil começa agora", style = MaterialTheme.typography.titleMedium, color = Criol.colors.foreground)
                Text(
                    "O Criol registra o que você ouve (só neste celular). Toque algumas músicas e seu perfil, " +
                        "suas mixes e seus resumos mensais começam a ganhar vida.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Criol.colors.foregroundMuted,
                )
            }
        }
    }
}

@Composable
private fun DnaCard(dna: ListenerDna) {
    val colors = Criol.colors
    InsightCard(title = "Seu DNA musical") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(colors.primary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) { Icon(dna.archetype.icon, null, tint = colors.primary, modifier = Modifier.size(32.dp)) }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(dna.archetype.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = colors.foreground)
                Text(dna.archetype.description, style = MaterialTheme.typography.bodySmall, color = colors.foregroundMuted)
            }
        }
        Spacer(Modifier.height(14.dp))
        listOf("Variedade" to dna.variety, "Fidelidade" to dna.loyalty, "Novidade (últimos 30 dias)" to dna.novelty).forEach { (label, v) ->
            Row(Modifier.fillMaxWidth()) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.foreground, modifier = Modifier.weight(1f))
                Text("$v%", style = MaterialTheme.typography.bodySmall, color = colors.foregroundMuted)
            }
            Spacer(Modifier.height(4.dp))
            Meter(v / 100f)
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun ClockCard(summary: ListeningSummary) {
    val colors = Criol.colors
    val favorite = favoriteDaypart(summary.byHour)
    val peakHour = summary.byHour.indices.maxByOrNull { summary.byHour[it] } ?: 0
    val hourBars = summary.byHour.mapIndexed { h, ms ->
        Bar(ms, if (h % 6 == 0) "${h}h" else "", "${h}h: ${msToMinutes(ms)} min", h == peakHour && ms > 0)
    }
    val peakDay = summary.byWeekday.indices.maxByOrNull { summary.byWeekday[it] } ?: 0
    val dayBars = summary.byWeekday.mapIndexed { i, ms ->
        val label = DayOfWeek.of(i + 1).getDisplayName(TextStyle.SHORT, ptBR).trimEnd('.')
        Bar(ms, label, "$label: ${msToMinutes(ms)} min", i == peakDay && ms > 0)
    }
    InsightCard(title = "Seu relógio musical") {
        Text(
            if (favorite != null) "Você ouve mais música ${favorite.phrase}."
            else "Ouça algumas músicas para descobrir seu horário favorito.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.foregroundMuted,
        )
        Spacer(Modifier.height(10.dp))
        BarStrip(hourBars, 110.dp)
        Spacer(Modifier.height(16.dp))
        Text("Dias da semana", style = MaterialTheme.typography.titleSmall, color = colors.foreground)
        Spacer(Modifier.height(6.dp))
        BarStrip(dayBars, 60.dp)
    }
}

// ----------------------------------------------------------------------
// Favoritos
// ----------------------------------------------------------------------

private enum class Range(val label: String, val days: Long?) {
    WEEKS_4("4 semanas", 28), MONTHS_6("6 meses", 182), ALL("Sempre", null)
}

@Composable
private fun TopLists(entries: List<ListenEntry>) {
    val colors = Criol.colors
    val nav = LocalNavigator.current
    val lib by Graph.library.library.collectAsState()
    var range by rememberSaveable { mutableStateOf(Range.WEEKS_4) }
    val summary by produceState<ListeningSummary?>(null, entries, range) {
        value = withContext(Dispatchers.Default) {
            val from = range.days?.let { System.currentTimeMillis() - it * app.criol.insights.DAY_MS } ?: 0L
            summarize(entries.filter { it.playedAt >= from }, 10)
        }
    }

    InsightCard {
        Text("Seus favoritos", style = MaterialTheme.typography.titleLarge, color = colors.foreground)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Range.entries.forEach { r ->
                val sel = r == range
                Text(
                    r.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (sel) colors.onPrimary else colors.foreground,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (sel) colors.primary else colors.foreground.copy(alpha = 0.08f))
                        .clickable { range = r }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
        val s = summary ?: return@InsightCard

        Spacer(Modifier.height(16.dp))
        Text("Top artistas", style = MaterialTheme.typography.titleMedium, color = colors.foreground)
        Spacer(Modifier.height(8.dp))
        if (s.topArtists.isEmpty()) Text("—", color = colors.foregroundMuted)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 4.dp)) {
            items(s.topArtists.take(8).withIndex().toList(), key = { it.value.id }) { (i, artist) ->
                val libArtist = lib.artistsById[lib.artistIdFor(artist.name)]
                Column(
                    Modifier
                        .width(88.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = libArtist != null) { libArtist?.let { nav.artist(it.id) } },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box {
                        Artwork(libArtist?.artPath ?: lib.artPath(artist.albumId), Modifier.size(84.dp), CircleShape, Icons.Rounded.Person)
                        Box(
                            Modifier
                                .align(Alignment.BottomStart)
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(colors.primary),
                            contentAlignment = Alignment.Center,
                        ) { Text("${i + 1}", color = colors.onPrimary, style = MaterialTheme.typography.labelLarge) }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(artist.name, style = MaterialTheme.typography.titleSmall, color = colors.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    Text(playsLabel(artist.plays), style = MaterialTheme.typography.bodySmall, color = colors.foregroundMuted, maxLines = 1)
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Text("Top músicas", style = MaterialTheme.typography.titleMedium, color = colors.foreground)
        Spacer(Modifier.height(4.dp))
        val topSongs = s.topSongs.mapNotNull { lib.songsById[it.id] }
        s.topSongs.forEachIndexed { i, item ->
            RankRow(i + 1, item, lib.artPath(item.albumId), playsLabel(item.plays)) {
                val idx = topSongs.indexOfFirst { it.id == item.id }
                if (idx >= 0) Graph.player.playSongs(topSongs, idx)
            }
        }

        Spacer(Modifier.height(14.dp))
        Text("Top gêneros", style = MaterialTheme.typography.titleMedium, color = colors.foreground)
        Spacer(Modifier.height(8.dp))
        val maxGenre = (s.topGenres.firstOrNull()?.listenedMs ?: 1L).coerceAtLeast(1L)
        s.topGenres.take(8).forEach { g ->
            Row(Modifier.fillMaxWidth()) {
                Text(g.name, style = MaterialTheme.typography.bodyMedium, color = colors.foreground, modifier = Modifier.weight(1f), maxLines = 1)
                Text(formatListenMinutes(g.listenedMs), style = MaterialTheme.typography.bodySmall, color = colors.foregroundMuted)
            }
            Spacer(Modifier.height(4.dp))
            Meter(g.listenedMs.toFloat() / maxGenre)
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
fun RankRow(
    rank: Int,
    item: RankedItem,
    artPath: String?,
    trailing: String,
    placeholder: ImageVector = Icons.Rounded.PlayArrow,
    circle: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = Criol.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$rank",
            style = MaterialTheme.typography.titleSmall,
            color = colors.foregroundMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(26.dp),
        )
        Artwork(artPath, Modifier.size(42.dp), if (circle) CircleShape else RoundedCornerShape(4.dp), placeholder)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.titleSmall, color = colors.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.subtitle.isNotBlank()) {
                Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = colors.foregroundMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(trailing, style = MaterialTheme.typography.bodySmall, color = colors.foregroundMuted)
    }
}

// ----------------------------------------------------------------------
// Resumos, conquistas, recentes e dados
// ----------------------------------------------------------------------

@Composable
private fun Recaps(current: MonthlyRecap?) {
    val profile by Graph.profile.profile.collectAsState()
    val colors = Criol.colors
    val saved = profile.recaps.values.sortedByDescending { it.month }
    SectionHeader("Resumo mensal")
    Text(
        "Todo fim de mês o Criol gera um resumo do que você ouviu e salva aqui no seu perfil.",
        style = MaterialTheme.typography.bodySmall,
        color = colors.foregroundMuted,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    Spacer(Modifier.height(8.dp))
    if (current == null && saved.isEmpty()) {
        Text(
            "Nenhum resumo ainda. Ouça músicas este mês e o primeiro aparece aqui.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.foregroundMuted,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        return
    }
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (current != null) item(key = "current") { RecapCard(current, partial = true) }
        items(saved, key = { it.month }) { RecapCard(it, isNew = it.month !in profile.seenRecaps) }
    }
}

@Composable
private fun Achievements(list: List<Achievement>) {
    val profile by Graph.profile.profile.collectAsState()
    val colors = Criol.colors
    SectionHeader("Conquistas") {
        Text(
            "${list.count { it.unlocked }} de ${list.size}",
            style = MaterialTheme.typography.bodySmall,
            color = colors.foregroundMuted,
            modifier = Modifier.padding(end = 8.dp),
        )
    }
    Text(
        "Toque em uma conquista desbloqueada para exibi-la no seu perfil.",
        style = MaterialTheme.typography.bodySmall,
        color = colors.foregroundMuted,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    Spacer(Modifier.height(8.dp))
    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        list.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { a ->
                    val pinned = profile.pinnedBadge == a.kind.name
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surfaceHigh)
                            .border(2.dp, if (pinned) colors.primary else Color.Transparent, RoundedCornerShape(12.dp))
                            .clickable(enabled = a.unlocked) { Graph.profile.togglePinned(a.kind.name) }
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (a.unlocked) colors.primary.copy(alpha = 0.2f) else colors.foreground.copy(alpha = 0.06f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (a.unlocked) a.kind.icon else Icons.Rounded.Lock, null,
                                tint = if (a.unlocked) colors.primary else colors.foregroundMuted,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            a.kind.label,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (a.unlocked) colors.foreground else colors.foregroundMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            a.kind.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.foregroundMuted,
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                            minLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(6.dp))
                        Meter(a.progress)
                        Spacer(Modifier.height(4.dp))
                        Text("${a.current}/${a.target}", style = MaterialTheme.typography.labelSmall, color = colors.foregroundMuted)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun RecentListens(entries: List<ListenEntry>) {
    val lib = Graph.library.current
    val recent = remember(entries) { entries.takeLast(15).asReversed() }
    if (recent.isEmpty()) return
    val colors = Criol.colors
    val now = System.currentTimeMillis()
    InsightCard(title = "Ouvidas recentemente") {
        recent.forEach { e ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(lib.artPath(e.albumId), Modifier.size(42.dp), RoundedCornerShape(4.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.title, style = MaterialTheme.typography.titleSmall, color = colors.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(e.artist, style = MaterialTheme.typography.bodySmall, color = colors.foregroundMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    DateUtils.getRelativeTimeSpanString(e.playedAt, now, DateUtils.MINUTE_IN_MILLIS).toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.foregroundMuted,
                )
            }
        }
    }
}

@Composable
private fun HistoryData(count: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = Criol.colors
    var confirmClear by remember { mutableStateOf(false) }
    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(Graph.listening.exportJson().toByteArray()) }
                }.isSuccess
            }
            toast(if (ok) "Histórico exportado" else "Não foi possível exportar")
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val raw = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }.orEmpty()
                }
                Graph.listening.importJson(raw)
            }.onSuccess { n -> toast(if (n == 1) "1 registro importado" else "$n registros importados") }
                .onFailure { toast("Arquivo de histórico inválido") }
        }
    }

    InsightCard(title = "Seus dados") {
        Text(
            (if (count == 1) "1 registro de escuta salvo" else "${formatNumber(count)} registros de escuta salvos") +
                " só neste celular. Nada é enviado para fora.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.foregroundMuted,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { exporter.launch("criol-historico-${currentMonthKey()}.json") }) {
                Icon(Icons.Rounded.FileDownload, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Exportar")
            }
            OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) {
                Icon(Icons.Rounded.FileUpload, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Importar")
            }
        }
        TextButton(onClick = { confirmClear = true }) { Text("Apagar histórico", color = Color(0xFFE2453C)) }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = colors.surface,
            title = { Text("Apagar histórico?") },
            text = {
                Text(
                    "Isso apaga todo o seu histórico de escuta do Criol (perfil, mixes e resumos futuros). " +
                        "Os resumos mensais já gerados continuam salvos.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch { Graph.listening.clear(); toast("Histórico apagado") }
                }) { Text("Apagar") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
        )
    }
}
