@file:OptIn(ExperimentalMaterial3Api::class)

package com.alphasim.brain.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alphasim.brain.AppContainer
import com.alphasim.brain.data.SessionManager
import com.alphasim.brain.data.db.AlphaListEntity
import com.alphasim.brain.data.db.ListStats
import com.alphasim.brain.data.db.settings
import com.alphasim.brain.data.model.AlphaStatus
import com.alphasim.brain.sim.SimulationEngine
import com.alphasim.brain.ui.components.ConfirmDialog
import com.alphasim.brain.ui.components.EmptyState
import com.alphasim.brain.ui.components.Fmt
import com.alphasim.brain.ui.components.Metric
import com.alphasim.brain.ui.components.TextInputDialog
import com.alphasim.brain.ui.components.rememberRunStarter
import com.alphasim.brain.ui.components.sharpeColor
import com.alphasim.brain.ui.theme.CodeStyle
import com.alphasim.brain.ui.theme.Signals
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    container: AppContainer,
    onOpenList: (Long) -> Unit,
    onImport: () -> Unit,
    onLogin: () -> Unit,
    onSettings: () -> Unit,
) {
    val repo = container.repo
    val lists by repo.observeLists().collectAsStateWithLifecycle(initialValue = emptyList())
    val stats by repo.observeStats().collectAsStateWithLifecycle(initialValue = emptyList())
    val queued by repo.observeCount(AlphaStatus.QUEUED).collectAsStateWithLifecycle(initialValue = 0)
    val session by container.session.state.collectAsStateWithLifecycle()
    val engine by container.engine.state.collectAsStateWithLifecycle()
    val startRun = rememberRunStarter()
    val statsById = remember(stats) { stats.associateBy { it.listId } }
    var renaming by remember { mutableStateOf<AlphaListEntity?>(null) }
    var deleting by remember { mutableStateOf<AlphaListEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("AlphaSim")
                        Text(
                            when {
                                session.loggedIn -> "BRAIN · ${session.userId ?: "signed in"}"
                                session.checking -> "Checking BRAIN session…"
                                else -> "Not signed in"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Settings") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onImport,
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Import alphas") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!session.loggedIn) {
                item(key = "session") { SessionBanner(session, onLogin) }
            }
            if (engine.running || queued > 0) {
                item(key = "engine") {
                    EngineCard(
                        engine = engine,
                        queued = queued,
                        onStart = startRun,
                        onPause = { container.engine.pause() },
                        onStop = { container.engine.stop() },
                        onClearQueue = { container.appScope.launch { repo.unqueueAll() } },
                    )
                }
            }
            if (lists.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = "No alpha lists yet",
                        body = "Import your 200-alpha files (three are bundled), pick a file from your phone, or paste expressions.",
                    ) { Button(onClick = onImport) { Text("Import alphas") } }
                }
            }
            items(lists, key = { it.id }) { list ->
                ListCard(
                    list = list,
                    stats = statsById[list.id],
                    onClick = { onOpenList(list.id) },
                    onRename = { renaming = list },
                    onDuplicate = { container.appScope.launch { repo.duplicateList(list.id) } },
                    onDelete = { deleting = list },
                )
            }
        }
    }

    renaming?.let { l ->
        TextInputDialog(
            title = "Rename list", initial = l.name, label = "Name", confirmLabel = "Rename",
            onConfirm = { name -> container.appScope.launch { repo.renameList(l.id, name) } },
            onDismiss = { renaming = null },
        )
    }
    deleting?.let { l ->
        ConfirmDialog(
            title = "Delete \"${l.name}\"?",
            text = "Removes the list and its stored results from this phone. Alphas already on BRAIN are not affected.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                container.appScope.launch {
                    repo.unqueueList(l.id)
                    repo.deleteList(l.id)
                }
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun SessionBanner(session: SessionManager.State, onLogin: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (session.checking) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Icon(Icons.Filled.CloudOff, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        session.personaUrl != null -> "Biometric check pending"
                        session.checking -> "Connecting to BRAIN…"
                        else -> "Sign in to simulate"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    session.error ?: "You can import and organise lists offline; simulations need a BRAIN session.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            TextButton(onClick = onLogin) { Text("Sign in") }
        }
    }
}

@Composable
private fun EngineCard(
    engine: SimulationEngine.State,
    queued: Int,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onClearQueue: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val onC = MaterialTheme.colorScheme.onPrimaryContainer
            Text(
                when {
                    engine.pausing -> "Pausing — waiting for in-flight simulations"
                    engine.running -> "Simulating on BRAIN"
                    else -> "$queued alphas waiting in the queue"
                },
                style = MaterialTheme.typography.titleMedium,
                color = onC,
            )
            if (engine.running) {
                Text(
                    "${engine.active} in flight · $queued queued · ${engine.completed} done · ${engine.failed} failed",
                    style = CodeStyle,
                    color = onC,
                )
                val total = engine.completed + engine.failed + queued + engine.active
                if (total > 0) {
                    LinearProgressIndicator(
                        progress = { (engine.completed + engine.failed).toFloat() / total },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                engine.startedAt?.let {
                    Text("Running for ${Fmt.duration(System.currentTimeMillis() - it)}", style = MaterialTheme.typography.labelSmall, color = onC)
                }
            } else {
                engine.lastRun?.let { Text("Last run: $it", style = MaterialTheme.typography.bodySmall, color = onC) }
            }
            engine.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = onC) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    !engine.running -> {
                        Button(onClick = onStart) { Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Start") }
                        OutlinedButton(onClick = onClearQueue) { Text("Clear queue") }
                    }
                    engine.pausing -> {
                        FilledTonalButton(onClick = onStart) { Text("Resume") }
                        OutlinedButton(onClick = onStop) { Icon(Icons.Filled.Stop, null); Text("Stop now") }
                    }
                    else -> {
                        FilledTonalButton(onClick = onPause) { Icon(Icons.Filled.Pause, null); Spacer(Modifier.width(6.dp)); Text("Pause") }
                        OutlinedButton(onClick = onStop) { Icon(Icons.Filled.Stop, null); Spacer(Modifier.width(6.dp)); Text("Stop") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ListCard(
    list: AlphaListEntity,
    stats: ListStats?,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val settings = remember(list.settingsJson) { list.settings }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(list.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(settings.summary(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                        DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menu = false; onDuplicate() })
                        DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
                    }
                }
            }
            val total = stats?.total ?: 0
            val done = (stats?.complete ?: 0) + (stats?.errors ?: 0)
            Column(Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(
                    progress = { if (total == 0) 0f else done.toFloat() / total },
                    modifier = Modifier.fillMaxWidth(),
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Metric("Alphas", "$total")
                    Metric("Done", "${stats?.complete ?: 0}")
                    Metric("Pass", "${stats?.passing ?: 0}", color = Signals.current.good)
                    if ((stats?.errors ?: 0) > 0) Metric("Errors", "${stats?.errors}", color = Signals.current.bad)
                    if ((stats?.running ?: 0) + (stats?.queued ?: 0) > 0) {
                        Metric("Queue", "${(stats?.running ?: 0) + (stats?.queued ?: 0)}", color = Signals.current.info)
                    }
                    Metric("Best SR", Fmt.num(stats?.bestSharpe), color = sharpeColor(stats?.bestSharpe, settings.delay))
                }
            }
        }
    }
}
