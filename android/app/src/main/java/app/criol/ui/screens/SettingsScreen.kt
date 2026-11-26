package app.criol.ui.screens

import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.criol.Graph
import app.criol.data.ScanState
import app.criol.data.songCountLabel
import app.criol.playback.AutoDj
import kotlin.math.roundToInt
import app.criol.ui.LocalNavigator
import app.criol.ui.components.TopBar
import app.criol.ui.theme.AccentPresets
import app.criol.ui.theme.Criol
import app.criol.ui.theme.Palettes

fun folderLabel(uri: String): String = runCatching {
    val id = DocumentsContract.getTreeDocumentId(Uri.parse(uri))
    val volume = id.substringBefore(':')
    val path = id.substringAfter(':', "")
    val volName = if (volume == "primary") "Armazenamento interno" else "Cartão SD ($volume)"
    if (path.isBlank()) volName else "$volName/$path"
}.getOrDefault(uri)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onAddFolder: () -> Unit) {
    val settings by Graph.settings.settings.collectAsState()
    val scan by Graph.library.scanState.collectAsState()
    val lib by Graph.library.library.collectAsState()
    val nav = LocalNavigator.current
    val colors = Criol.colors
    val lyricsPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) Graph.library.addLyricsFolder(uri)
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("Configurações", onBack = { nav.back() })
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            // ---------------- Biblioteca ----------------
            item { GroupTitle("Biblioteca") }
            item {
                Text(
                    "${songCountLabel(lib.songs.size)} • ${lib.albums.size} álbuns • ${lib.artists.size} artistas",
                    color = colors.foregroundMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            if (settings.folders.isEmpty()) {
                item {
                    Text(
                        "Nenhuma pasta adicionada.",
                        color = colors.foregroundMuted,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            }
            settings.folders.forEach { f ->
                item(key = f) {
                    FolderRow(folderLabel(f), Icons.Rounded.Folder) { Graph.library.removeFolder(f) }
                }
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PrimaryButton("Adicionar pasta", Icons.Rounded.CreateNewFolder, onAddFolder)
                    OutlinedButton(
                        onClick = { Graph.library.rescan() },
                        enabled = scan !is ScanState.Running && settings.folders.isNotEmpty(),
                    ) {
                        Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Escanear")
                    }
                }
            }
            item {
                val s = scan
                val text = when (s) {
                    is ScanState.Running -> null
                    is ScanState.Finished -> "Última leitura: ${songCountLabel(s.songCount)}"
                    is ScanState.Failed -> "Falha ao ler: ${s.message}"
                    ScanState.Idle -> null
                }
                if (s is ScanState.Running) ScanBanner()
                if (text != null) {
                    Text(text, color = colors.foregroundMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp))
                }
            }
            item { FastReadRow() }
            item {
                SwitchRow("Escanear ao abrir o app", "Procura arquivos novos ou alterados sempre que o app inicia.", settings.scanOnStartup) {
                    Graph.settings.update { s -> s.copy(scanOnStartup = it) }
                }
            }
            item {
                ChoiceRow(
                    "Ignorar áudios curtos",
                    settings.minDurationSeconds,
                    listOf(0 to "Não ignorar", 10 to "Menos de 10 s", 30 to "Menos de 30 s", 60 to "Menos de 1 min"),
                ) {
                    Graph.settings.update { s -> s.copy(minDurationSeconds = it) }
                    Graph.library.rescan()
                }
            }
            item {
                ActionRow("Ler as capas novamente", "Apaga o cache de capas e extrai de novo dos arquivos.") {
                    Graph.library.rescan(clearArt = true)
                }
            }

            // ---------------- Letras ----------------
            item { GroupTitle("Letras") }
            item {
                val withLyrics = lib.songs.count { it.lyricsUri != null }
                Text(
                    "Pastas com arquivos .lrc ou .txt. O nome do arquivo pode ser igual ao da música, " +
                        "\"Artista - Título\" ou só o título. $withLyrics de ${lib.songs.size} músicas têm arquivo de letra.",
                    color = colors.foregroundMuted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            settings.lyricsFolders.forEach { f ->
                item(key = "lyrics:$f") {
                    FolderRow(folderLabel(f), Icons.Rounded.Lyrics) { Graph.library.removeLyricsFolder(f) }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                    PrimaryButton("Adicionar pasta de letras", Icons.Rounded.Lyrics) { lyricsPicker.launch(null) }
                }
            }

            // ---------------- Aparência ----------------
            item { GroupTitle("Aparência") }
            item { Label("Tema") }
            item {
                FlowRow(
                    Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Palettes.forEach { p ->
                        val selected = p.id == settings.themeId
                        Column(
                            Modifier
                                .width(96.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(2.dp, if (selected) colors.primary else colors.border, RoundedCornerShape(10.dp))
                                .clickable { Graph.settings.update { it.copy(themeId = p.id) } }
                                .background(p.background)
                                .padding(10.dp),
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.size(18.dp).clip(CircleShape).background(p.primary))
                                Box(Modifier.size(18.dp).clip(CircleShape).background(p.surface))
                                Box(Modifier.size(18.dp).clip(CircleShape).background(p.foreground))
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(p.name, color = p.foreground, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            item {
                SwitchRow(
                    "Seguir o dia",
                    "Clara de manhã, vai escurecendo à tarde até ficar escura à noite. O tema escolhido acima é uma das pontas.",
                    settings.themeFollowsDay,
                ) { Graph.settings.update { s -> s.copy(themeFollowsDay = it) } }
            }
            item { Label("Cor de destaque") }
            item {
                FlowRow(
                    Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .border(2.dp, if (settings.accent == null) colors.primary else colors.border, RoundedCornerShape(50))
                            .clickable { Graph.settings.update { it.copy(accent = null) } }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                    ) {
                        Text("Do tema", color = colors.foreground, style = MaterialTheme.typography.bodySmall)
                    }
                    AccentPresets.forEach { c ->
                        val selected = settings.accent == c.toArgb()
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(if (selected) 3.dp else 0.dp, colors.foreground, CircleShape)
                                .clickable { Graph.settings.update { it.copy(accent = c.toArgb()) } },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) Icon(Icons.Rounded.Check, null, tint = if (c == Color.White) Color.Black else Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            item {
                ChoiceRow("Colunas na grade de álbuns", settings.albumColumns, listOf(2 to "2", 3 to "3", 4 to "4")) {
                    Graph.settings.update { s -> s.copy(albumColumns = it) }
                }
            }
            item {
                SwitchRow("Mostrar avaliações", "Estrelas no player e no menu das músicas.", settings.showRatings) {
                    Graph.settings.update { s -> s.copy(showRatings = it) }
                }
            }

            // ---------------- Player ----------------
            item { GroupTitle("Player") }
            item {
                SwitchRow("Fundo dinâmico", "Usa a capa do álbum como fundo do player em tela cheia.", settings.dynamicPlayerBackground) {
                    Graph.settings.update { s -> s.copy(dynamicPlayerBackground = it) }
                }
            }
            if (Build.VERSION.SDK_INT >= 31) {
                item {
                    SwitchRow("Desfocar fundo", "Aplica desfoque na capa usada como fundo.", settings.playerBlur) {
                        Graph.settings.update { s -> s.copy(playerBlur = it) }
                    }
                }
            }
            item {
                ChoiceRow("Voltar", settings.skipBackSeconds, listOf(5 to "5 s", 10 to "10 s", 15 to "15 s", 30 to "30 s")) {
                    Graph.settings.update { s -> s.copy(skipBackSeconds = it) }
                }
            }
            item {
                ChoiceRow("Avançar", settings.skipForwardSeconds, listOf(10 to "10 s", 15 to "15 s", 30 to "30 s", 60 to "60 s")) {
                    Graph.settings.update { s -> s.copy(skipForwardSeconds = it) }
                }
            }
            item {
                SwitchRow("Retomar a fila", "Restaura a fila e a posição ao abrir o app.", settings.resumeQueue) {
                    Graph.settings.update { s -> s.copy(resumeQueue = it) }
                }
            }

            // ---------------- Auto DJ ----------------
            item { GroupTitle("Auto DJ") }
            item {
                SwitchRow(
                    "Ativar Auto DJ",
                    "Quando a fila está acabando, adiciona automaticamente mais músicas parecidas com a que está tocando.",
                    settings.autoDjEnabled,
                ) { Graph.player.setAutoDj(it) }
            }
            item {
                TextChoiceRow("Adicionar", settings.autoDjMode, listOf("songs" to "Músicas", "albums" to "Álbuns inteiros")) {
                    Graph.settings.update { s -> s.copy(autoDjMode = it) }
                }
            }
            item {
                TextChoiceRow(
                    "Estratégia",
                    settings.autoDjStrategy,
                    listOf("similar" to "Parecidas", "random" to "Aleatórias da biblioteca"),
                ) { Graph.settings.update { s -> s.copy(autoDjStrategy = it) } }
            }
            item {
                val albums = settings.autoDjMode == "albums"
                val max = if (albums) AutoDj.MAX_ALBUMS else AutoDj.MAX_SONGS
                SliderRow(
                    if (albums) "Álbuns por vez" else "Músicas por vez",
                    settings.autoDjCount.coerceIn(1, max),
                    1..max,
                ) { Graph.settings.update { s -> s.copy(autoDjCount = it) } }
            }
            item {
                ChoiceRow(
                    "Disparar quando restarem",
                    settings.autoDjTiming,
                    listOf(1 to "Menos de 1 música", 2 to "Menos de 2", 3 to "Menos de 3", 4 to "Menos de 4", 5 to "Menos de 5"),
                ) { Graph.settings.update { s -> s.copy(autoDjTiming = it) } }
            }
            item {
                SwitchRow(
                    "Somente parecidas",
                    "Não completa com músicas aleatórias quando encontra parecidas.",
                    settings.autoDjOnlySimilar,
                ) { Graph.settings.update { s -> s.copy(autoDjOnlySimilar = it) } }
            }
            item {
                SwitchRow(
                    "Permitir repetidas",
                    "Pode adicionar músicas (ou álbuns) que já estão na fila.",
                    settings.autoDjAllowDuplicates,
                ) { Graph.settings.update { s -> s.copy(autoDjAllowDuplicates = it) } }
            }

            // ---------------- Perfil e histórico ----------------
            item { GroupTitle("Perfil e histórico") }
            item {
                SwitchRow(
                    "Registrar o que eu ouço",
                    "Alimenta o perfil, a daylist, as mixes e os resumos mensais. Fica só neste celular.",
                    settings.recordHistory,
                ) { Graph.settings.update { s -> s.copy(recordHistory = it) } }
            }
            item {
                ActionRow("Abrir perfil", "Estatísticas, DNA musical, conquistas, resumos e exportação do histórico.") {
                    nav.profile()
                }
            }

            // ---------------- Sobre ----------------
            item { GroupTitle("Sobre") }
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                    Text("Criol 1.0", color = colors.foreground, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Player de música para as pastas do seu celular, sem servidor.",
                        color = colors.foregroundMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderRow(label: String, icon: ImageVector, onRemove: () -> Unit) {
    val colors = Criol.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = colors.primary)
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            color = colors.foreground,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRemove) {
            Icon(Icons.Rounded.Close, "Remover pasta", tint = colors.foregroundMuted)
        }
    }
}

@Composable
private fun GroupTitle(text: String) {
    Column {
        HorizontalDivider(Modifier.padding(top = 16.dp), color = Criol.colors.border)
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Criol.colors.primary,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = Criol.colors.foreground,
        modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 10.dp),
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = Criol.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.foreground, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, color = colors.foregroundMuted, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = colors.primary, checkedThumbColor = colors.onPrimary),
        )
    }
}

/** Valor numérico: arraste para ir rápido, − e + para acertar o número exato. */
@Composable
private fun SliderRow(title: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    val colors = Criol.colors
    // Durante o arraste só muda a tela; salva quando solta.
    var current by remember(value) { mutableFloatStateOf(value.toFloat()) }
    val shown = current.roundToInt()
    fun commit(v: Int) {
        val clamped = v.coerceIn(range.first, range.last)
        current = clamped.toFloat()
        if (clamped != value) onChange(clamped)
    }
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = colors.foreground, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { commit(shown - 1) }, enabled = shown > range.first) {
                Icon(Icons.Rounded.Remove, "Diminuir", tint = colors.foregroundMuted)
            }
            Text(
                shown.toString(),
                color = colors.primary,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(40.dp),
            )
            IconButton(onClick = { commit(shown + 1) }, enabled = shown < range.last) {
                Icon(Icons.Rounded.Add, "Aumentar", tint = colors.foregroundMuted)
            }
        }
        Slider(
            value = current,
            onValueChange = { current = it },
            onValueChangeFinished = { commit(current.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = colors.primary,
                activeTrackColor = colors.primary,
                inactiveTrackColor = colors.primary.copy(alpha = 0.24f),
            ),
            modifier = Modifier.padding(end = 12.dp),
        )
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String, onClick: () -> Unit) {
    val colors = Criol.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(title, color = colors.foreground, style = MaterialTheme.typography.bodyLarge)
        Text(subtitle, color = colors.foregroundMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TextChoiceRow(title: String, value: String, options: List<Pair<String, String>>, onChange: (String) -> Unit) =
    ChoiceRow(title, value, options, onChange)

@Composable
private fun <T> ChoiceRow(title: String, value: T, options: List<Pair<T, String>>, onChange: (T) -> Unit) {
    val colors = Criol.colors
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { open = true }
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, color = colors.foreground, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(options.firstOrNull { it.first == value }?.second ?: value.toString(), color = colors.primary, style = MaterialTheme.typography.bodyMedium)
        }
        Box(Modifier.align(Alignment.TopEnd).padding(end = 20.dp)) {
            DropdownMenu(open, { open = false }, containerColor = colors.surface) {
                options.forEach { (v, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        trailingIcon = { if (v == value) Icon(Icons.Rounded.Check, null, tint = colors.primary) },
                        onClick = { open = false; onChange(v) },
                    )
                }
            }
        }
    }
}

/**
 * Leitura rápida: com a permissão de ler músicas, o Criol pega as tags do índice do Android
 * (uma consulta só), pula a varredura quando nada mudou e percebe músicas novas sozinho.
 */
@Composable
private fun FastReadRow() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? android.app.Activity
    var granted by remember { mutableStateOf(Graph.library.hasMediaPermission) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        granted = Graph.library.hasMediaPermission
        if (granted) Graph.library.onMediaPermissionChanged()
        onPauseOrDispose { }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (ok) Graph.library.onMediaPermissionChanged()
    }
    fun openAppSettings() {
        context.startActivity(
            android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", context.packageName, null)),
        )
    }
    SwitchRow(
        "Leitura rápida",
        if (granted) {
            "Ativada: usa o índice de músicas do Android. Músicas novas aparecem sozinhas e a leitura é bem mais rápida."
        } else {
            "Permita ler as músicas do aparelho para ler a biblioteca bem mais rápido e achar músicas novas sozinho."
        },
        granted,
    ) { want ->
        val permission = app.criol.data.MediaStoreIndex.permission
        when {
            // Desligar só pelas configurações do Android.
            !want -> openAppSettings()
            Graph.settings.value.mediaPermissionAsked && activity != null &&
                !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, permission) -> openAppSettings()
            else -> {
                Graph.settings.update { it.copy(mediaPermissionAsked = true) }
                launcher.launch(permission)
            }
        }
    }
}
