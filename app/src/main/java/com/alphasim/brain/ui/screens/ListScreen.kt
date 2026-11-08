@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.alphasim.brain.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alphasim.brain.AppContainer
import com.alphasim.brain.data.CsvExporter
import com.alphasim.brain.data.db.AlphaEntity
import com.alphasim.brain.data.db.overrideSettings
import com.alphasim.brain.data.db.passesAllChecks
import com.alphasim.brain.data.db.settings
import com.alphasim.brain.data.model.AlphaStatus
import com.alphasim.brain.data.model.SimSettings
import com.alphasim.brain.ui.components.ConfirmDialog
import com.alphasim.brain.ui.components.EmptyState
import com.alphasim.brain.ui.components.Fmt
import com.alphasim.brain.ui.components.SimSettingsDialog
import com.alphasim.brain.ui.components.StatusChip
import com.alphasim.brain.ui.components.TextInputDialog
import com.alphasim.brain.ui.components.fitnessColor
import com.alphasim.brain.ui.components.rememberRunStarter
import com.alphasim.brain.ui.components.sharpeColor
import com.alphasim.brain.ui.components.turnoverColor
import com.alphasim.brain.ui.theme.CodeStyle
import com.alphasim.brain.ui.theme.Signals
import kotlinx.coroutines.launch

private enum class Filter(val label: String) {
    ALL("All"), NOT_RUN("Not run"), QUEUED("Queued"), RUNNING("Running"),
    DONE("Done"), PASSING("Passing"), FAILING("Failing checks"), ERROR("Errors"), FAVORITES("Starred"),
}

private enum class Sort(val label: String) {
    POSITION("List order"), SHARPE("Sharpe"), FITNESS("Fitness"), RETURNS("Returns"),
    TURNOVER("Turnover (low first)"), DRAWDOWN("Drawdown (low first)"), MARGIN("Margin"),
}

private fun Filter.matches(a: AlphaEntity): Boolean = when (this) {
    Filter.ALL -> true
    Filter.NOT_RUN -> a.status == AlphaStatus.IDLE
    Filter.QUEUED -> a.status == AlphaStatus.QUEUED
    Filter.RUNNING -> a.status == AlphaStatus.RUNNING
    Filter.DONE -> a.status == AlphaStatus.COMPLETE
    Filter.PASSING -> a.passesAllChecks
    Filter.FAILING -> a.status == AlphaStatus.COMPLETE && (a.failedChecks ?: 0) > 0
    Filter.ERROR -> a.status == AlphaStatus.ERROR
    Filter.FAVORITES -> a.favorite
}

@Composable
fun ListScreen(
    container: AppContainer,
    listId: Long,
    onBack: () -> Unit,
    onOpenAlpha: (Long) -> Unit,
    onLogin: () -> Unit,
) {
    val repo = container.repo
    val scope = container.appScope
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val list by repo.observeList(listId).collectAsStateWithLifecycle(initialValue = null)
    val alphas by repo.observeAlphas(listId).collectAsStateWithLifecycle(initialValue = emptyList())
    val engine by container.engine.state.collectAsStateWithLifecycle()
    val session by container.session.state.collectAsStateWithLifecycle()
    val startRun = rememberRunStarter()

    var filter by remember { mutableStateOf(Filter.ALL) }
    var sort by remember { mutableStateOf(Sort.POSITION) }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf(setOf<Long>()) }
    var showSettings by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var menu by remember { mutableStateOf(false) }
    var runMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }

    val listSettings = remember(list?.settingsJson) { list?.settings ?: SimSettings() }
    val counts = remember(alphas) { Filter.entries.associateWith { f -> alphas.count { f.matches(it) } } }
    val visible = remember(alphas, filter, sort, query) {
        val q = query.trim().lowercase()
        val filtered = alphas.filter { filter.matches(it) && (q.isEmpty() || it.expression.lowercase().contains(q) || it.alphaId?.lowercase()?.contains(q) == true) }
        when (sort) {
            Sort.POSITION -> filtered
            Sort.SHARPE -> filtered.sortedByDescending { it.sharpe ?: Double.NEGATIVE_INFINITY }
            Sort.FITNESS -> filtered.sortedByDescending { it.fitness ?: Double.NEGATIVE_INFINITY }
            Sort.RETURNS -> filtered.sortedByDescending { it.returns ?: Double.NEGATIVE_INFINITY }
            Sort.TURNOVER -> filtered.sortedBy { it.turnover ?: Double.POSITIVE_INFINITY }
            Sort.DRAWDOWN -> filtered.sortedBy { it.drawdown ?: Double.POSITIVE_INFINITY }
            Sort.MARGIN -> filtered.sortedByDescending { it.margin ?: Double.NEGATIVE_INFINITY }
        }
    }

    fun ask(text: String, action: () -> Unit) {
        confirm = text to action
    }

    fun queueAndRun(ids: List<Long>, reset: Boolean = false) {
        if (ids.isEmpty()) return
        scope.launch {
            if (reset) repo.reset(ids)
            repo.queue(ids)
        }
        startRun()
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val l = list ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            scope.launch {
                val ok = CsvExporter.writeTo(context, uri, CsvExporter.build(l, repo.alphasOf(l.id)))
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    Toast.makeText(context, if (ok) "Exported" else "Export failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val notRunIds = alphas.filter { it.status == AlphaStatus.IDLE }.map { it.id }
    val errorIds = alphas.filter { it.status == AlphaStatus.ERROR }.map { it.id }

    Scaffold(
        topBar = {
            if (selection.isNotEmpty()) {
                TopAppBar(
                    title = { Text("${selection.size} selected") },
                    navigationIcon = { IconButton(onClick = { selection = emptySet() }) { Icon(Icons.Filled.Close, "Clear selection") } },
                    actions = {
                        IconButton(onClick = { selection = visible.map { it.id }.toSet() }) { Icon(Icons.Filled.SelectAll, "Select all") }
                        IconButton(onClick = { queueAndRun(selection.toList(), reset = true); selection = emptySet() }) { Icon(Icons.Filled.PlayArrow, "Simulate") }
                        IconButton(onClick = {
                            val ids = selection.toList()
                            ask("Reset results for ${ids.size} alphas?") { scope.launch { repo.reset(ids) }; selection = emptySet() }
                        }) { Icon(Icons.Filled.RestartAlt, "Reset") }
                        IconButton(onClick = {
                            val ids = selection.toList()
                            ask("Delete ${ids.size} alphas from this list?") { scope.launch { repo.delete(ids) }; selection = emptySet() }
                        }) { Icon(Icons.Filled.Delete, "Delete") }
                    },
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(list?.name ?: "List", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(listSettings.summary(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                    actions = {
                        IconButton(onClick = { searching = !searching; if (!searching) query = "" }) { Icon(Icons.Filled.Search, "Search") }
                        IconButton(onClick = { showSettings = true }) { Icon(Icons.Filled.Tune, "List settings") }
                        Box {
                            IconButton(onClick = { runMenu = true }) { Icon(Icons.Filled.PlayArrow, "Run") }
                            DropdownMenu(expanded = runMenu, onDismissRequest = { runMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Run not-run (${notRunIds.size})") }, enabled = notRunIds.isNotEmpty(),
                                    onClick = { runMenu = false; queueAndRun(notRunIds) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Retry errors (${errorIds.size})") }, enabled = errorIds.isNotEmpty(),
                                    onClick = { runMenu = false; queueAndRun(errorIds, reset = true) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Run what's shown (${visible.size})") }, enabled = visible.isNotEmpty(),
                                    onClick = {
                                        runMenu = false
                                        val ids = visible.filter { it.status != AlphaStatus.RUNNING && it.status != AlphaStatus.QUEUED }.map { it.id }
                                        ask("Simulate ${ids.size} alphas (existing results are cleared)?") { queueAndRun(ids, reset = true) }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Re-run everything") },
                                    onClick = {
                                        runMenu = false
                                        val ids = alphas.filter { it.status != AlphaStatus.RUNNING && it.status != AlphaStatus.QUEUED }.map { it.id }
                                        ask("Re-simulate all ${ids.size} alphas? Stored results are cleared first.") { queueAndRun(ids, reset = true) }
                                    },
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Remove this list from the queue") },
                                    onClick = { runMenu = false; scope.launch { repo.unqueueList(listId) } },
                                )
                            }
                        }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Add expressions") }, onClick = { menu = false; showAdd = true })
                                DropdownMenuItem(text = { Text("Export CSV…") }, onClick = {
                                    menu = false
                                    exportLauncher.launch("${list?.name ?: "alphas"}.csv")
                                })
                                DropdownMenuItem(text = { Text("Share CSV") }, onClick = {
                                    menu = false
                                    val l = list
                                    if (l != null) {
                                        scope.launch {
                                            val csv = CsvExporter.build(l, repo.alphasOf(l.id))
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { CsvExporter.share(context, l.name, csv) }
                                        }
                                    }
                                })
                                DropdownMenuItem(text = { Text("Copy passing alpha IDs") }, onClick = {
                                    menu = false
                                    val ids = alphas.filter { it.passesAllChecks }.mapNotNull { it.alphaId }
                                    clipboard.setText(AnnotatedString(ids.joinToString("\n")))
                                    Toast.makeText(context, "Copied ${ids.size} alpha IDs", Toast.LENGTH_SHORT).show()
                                })
                                DropdownMenuItem(text = { Text("Copy all expressions") }, onClick = {
                                    menu = false
                                    clipboard.setText(AnnotatedString(alphas.joinToString("\n") { it.expression }))
                                    Toast.makeText(context, "Copied ${alphas.size} expressions", Toast.LENGTH_SHORT).show()
                                })
                                HorizontalDivider()
                                DropdownMenuItem(text = { Text("Rename list") }, onClick = { menu = false; showRename = true })
                                DropdownMenuItem(text = { Text("Reset all results") }, onClick = {
                                    menu = false
                                    ask("Clear every stored result in this list?") { scope.launch { repo.reset(alphas.map { it.id }) } }
                                })
                                DropdownMenuItem(text = { Text("Delete list") }, onClick = {
                                    menu = false
                                    ask("Delete this list and its results from the phone?") {
                                        scope.launch { repo.unqueueList(listId); repo.deleteList(listId) }
                                        onBack()
                                    }
                                })
                            }
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (selection.isEmpty() && notRunIds.isNotEmpty() && !engine.running) {
                ExtendedFloatingActionButton(
                    onClick = { queueAndRun(notRunIds) },
                    icon = { Icon(Icons.Filled.PlayArrow, null) },
                    text = { Text("Simulate ${notRunIds.size}") },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!session.loggedIn) {
                item(key = "login") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                session.error ?: "Not signed in — queued alphas start once you sign in.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = onLogin) { Text("Sign in") }
                        }
                    }
                }
            }
            if (engine.running || engine.message != null) {
                item(key = "engine") {
                    Text(
                        listOfNotNull(
                            if (engine.running) "${engine.active} in flight · ${engine.completed} done · ${engine.failed} failed this run" else null,
                            engine.message,
                        ).joinToString(" — "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
            item(key = "summary") { SummaryRow(alphas, listSettings.delay) }
            if (searching) {
                item(key = "search") {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search expression or alpha id") },
                        singleLine = true,
                        textStyle = CodeStyle,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item(key = "filters") {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        FilterChip(
                            selected = sort != Sort.POSITION,
                            onClick = { sortMenu = true },
                            label = { Text(sort.label) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, null) },
                        )
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            Sort.entries.forEach { s ->
                                DropdownMenuItem(text = { Text(s.label) }, onClick = { sort = s; sortMenu = false })
                            }
                        }
                    }
                    Filter.entries.forEach { f ->
                        val n = counts[f] ?: 0
                        if (f == Filter.ALL || n > 0 || filter == f) {
                            FilterChip(
                                selected = filter == f,
                                onClick = { filter = f },
                                label = { Text("${f.label} $n") },
                            )
                        }
                    }
                }
            }
            if (visible.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = if (alphas.isEmpty()) "This list is empty" else "Nothing matches",
                        body = if (alphas.isEmpty()) "Use ⋮ → Add expressions to paste some." else "Try another filter or search.",
                    )
                }
            }
            items(visible, key = { it.id }) { a ->
                AlphaRow(
                    alpha = a,
                    delay = (a.overrideSettings ?: listSettings).delay,
                    selected = a.id in selection,
                    onClick = {
                        if (selection.isNotEmpty()) selection = selection.toggle(a.id) else onOpenAlpha(a.id)
                    },
                    onLongClick = { selection = selection.toggle(a.id) },
                )
            }
        }
    }

    if (showSettings && list != null) {
        SimSettingsDialog(
            title = "List settings",
            initial = listSettings,
            onDismiss = { showSettings = false },
            onSave = { s -> scope.launch { repo.updateListSettings(listId, s) } },
        )
    }
    if (showAdd) {
        TextInputDialog(
            title = "Add expressions",
            initial = "",
            label = "One expression per line",
            confirmLabel = "Add",
            singleLine = false,
            supporting = "Duplicates of expressions already in the list are skipped.",
            onConfirm = { text ->
                scope.launch {
                    val r = repo.addExpressions(listId, text)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(context, "Added ${r.expressions.size}" + if (r.duplicates > 0) " · ${r.duplicates} duplicates skipped" else "", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDismiss = { showAdd = false },
        )
    }
    if (showRename) {
        TextInputDialog(
            title = "Rename list", initial = list?.name.orEmpty(), label = "Name", confirmLabel = "Rename",
            onConfirm = { n -> scope.launch { repo.renameList(listId, n) } },
            onDismiss = { showRename = false },
        )
    }
    confirm?.let { (text, action) ->
        ConfirmDialog(title = "Are you sure?", text = text, confirmLabel = "Continue", onConfirm = action, onDismiss = { confirm = null })
    }
}

private fun Set<Long>.toggle(id: Long) = if (id in this) this - id else this + id

@Composable
private fun SummaryRow(alphas: List<AlphaEntity>, delay: Int) {
    val done = alphas.filter { it.status == AlphaStatus.COMPLETE }
    val best = done.mapNotNull { it.sharpe }.maxOrNull()
    val avg = done.mapNotNull { it.sharpe }.takeIf { it.isNotEmpty() }?.average()
    val passing = done.count { it.passesAllChecks }
    val total = alphas.size
    val finished = done.size + alphas.count { it.status == AlphaStatus.ERROR }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                com.alphasim.brain.ui.components.Metric("Progress", "$finished/$total")
                com.alphasim.brain.ui.components.Metric("Passing", "$passing", color = Signals.current.good)
                com.alphasim.brain.ui.components.Metric("Best Sharpe", Fmt.num(best), color = sharpeColor(best, delay))
                com.alphasim.brain.ui.components.Metric("Avg Sharpe", Fmt.num(avg))
            }
            LinearProgressIndicator(
                progress = { if (total == 0) 0f else finished.toFloat() / total },
                modifier = Modifier.fillMaxWidth(),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        }
    }
}

@Composable
private fun AlphaRow(alpha: AlphaEntity, delay: Int, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
    Card(
        colors = CardDefaults.cardColors(containerColor = container),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("#${alpha.position}", style = CodeStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (alpha.favorite) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Filled.Star, null, tint = Signals.current.warn, modifier = Modifier.padding(0.dp))
                }
                if (alpha.overrideSettings != null) {
                    Spacer(Modifier.width(6.dp))
                    Text("custom settings", style = MaterialTheme.typography.labelSmall, color = Signals.current.info)
                }
                Spacer(Modifier.weight(1f))
                alpha.alphaId?.let {
                    Text(it, style = CodeStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                }
                StatusChip(alpha.status, alpha.failedChecks)
            }
            Text(alpha.expression, style = CodeStyle, maxLines = 2, overflow = TextOverflow.Ellipsis)
            when (alpha.status) {
                AlphaStatus.RUNNING -> {
                    val p = alpha.progress
                    if (p != null && p > 0) LinearProgressIndicator(progress = { p.toFloat() }, modifier = Modifier.fillMaxWidth())
                    else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                AlphaStatus.COMPLETE -> if (alpha.sharpe != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        MetricInline("SR", Fmt.num(alpha.sharpe), sharpeColor(alpha.sharpe, delay))
                        MetricInline("Fit", Fmt.num(alpha.fitness), fitnessColor(alpha.fitness, delay))
                        MetricInline("TO", Fmt.pct(alpha.turnover, 1), turnoverColor(alpha.turnover))
                        MetricInline("Ret", Fmt.pct(alpha.returns, 1), MaterialTheme.colorScheme.onSurface)
                        MetricInline("DD", Fmt.pct(alpha.drawdown, 1), MaterialTheme.colorScheme.onSurface)
                    }
                } else {
                    alpha.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Signals.current.warn, maxLines = 2) }
                }
                AlphaStatus.ERROR -> alpha.message?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = Signals.current.bad, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun MetricInline(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(3.dp))
        Text(value, style = CodeStyle, color = color)
    }
}
