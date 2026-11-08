@file:OptIn(ExperimentalMaterial3Api::class)

package com.alphasim.brain.ui.screens

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alphasim.brain.AppContainer
import com.alphasim.brain.data.ExpressionTools
import com.alphasim.brain.data.api.BrainApi
import com.alphasim.brain.data.db.AlphaEntity
import com.alphasim.brain.data.db.checks
import com.alphasim.brain.data.db.overrideSettings
import com.alphasim.brain.data.db.settings
import com.alphasim.brain.data.model.AlphaStatus
import com.alphasim.brain.data.model.CheckResult
import com.alphasim.brain.data.model.Recordset
import com.alphasim.brain.data.model.SimSettings
import com.alphasim.brain.ui.components.ConfirmDialog
import com.alphasim.brain.ui.components.Fmt
import com.alphasim.brain.ui.components.Metric
import com.alphasim.brain.ui.components.PnlChart
import com.alphasim.brain.ui.components.SectionCard
import com.alphasim.brain.ui.components.SimSettingsDialog
import com.alphasim.brain.ui.components.StatusChip
import com.alphasim.brain.ui.components.TextInputDialog
import com.alphasim.brain.ui.components.fitnessColor
import com.alphasim.brain.ui.components.rememberRunStarter
import com.alphasim.brain.ui.components.sharpeColor
import com.alphasim.brain.ui.components.tagList
import com.alphasim.brain.ui.components.turnoverColor
import com.alphasim.brain.ui.theme.CodeStyle
import com.alphasim.brain.ui.theme.Signals
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@Composable
fun AlphaDetailScreen(container: AppContainer, alphaRowId: Long, onBack: () -> Unit, onLogin: () -> Unit) {
    val vm: AlphaDetailViewModel = viewModel(key = "alpha-$alphaRowId") { AlphaDetailViewModel(container, alphaRowId) }
    val alpha by vm.alpha.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val session by container.session.state.collectAsStateWithLifecycle()
    val listFlow = remember(alpha?.listId) { alpha?.listId?.let { container.repo.observeList(it) } ?: flowOf(null) }
    val list by listFlow.collectAsStateWithLifecycle(initialValue = null)
    val snackbar = remember { SnackbarHostState() }
    val uri = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val startRun = rememberRunStarter()

    var editExpression by remember { mutableStateOf(false) }
    var editSettings by remember { mutableStateOf(false) }
    var editProps by remember { mutableStateOf(false) }
    var confirmSubmit by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(alpha?.alphaId, alpha?.status) {
        val a = alpha
        if (a?.alphaId != null && a.status == AlphaStatus.COMPLETE) vm.ensureLoaded(a.alphaId)
    }
    LaunchedEffect(ui.message) {
        ui.message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    val a = alpha
    val settings: SimSettings = a?.overrideSettings ?: list?.settings ?: SimSettings()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(a?.alphaName?.takeIf { it.isNotBlank() } ?: "Alpha #${a?.position ?: ""}")
                        Text(list?.name.orEmpty(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (a != null) {
                        IconButton(onClick = { vm.toggleFavorite() }) {
                            Icon(if (a.favorite) Icons.Filled.Star else Icons.Filled.StarBorder, "Star", tint = if (a.favorite) Signals.current.warn else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    a?.alphaId?.let { id ->
                        IconButton(onClick = { vm.refresh() }, enabled = ui.busy == null) { Icon(Icons.Filled.Refresh, "Refresh from BRAIN") }
                        IconButton(onClick = { runCatching { uri.openUri(BrainApi.alphaWebUrl(id)) } }) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, "Open on BRAIN")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (a == null) {
            Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(48.dp))
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ui.busy?.let { label ->
                item(key = "busy") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("$label…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }
            if (!session.loggedIn) {
                item(key = "login") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Not signed in to BRAIN", style = MaterialTheme.typography.bodySmall, color = Signals.current.warn, modifier = Modifier.weight(1f))
                        TextButton(onClick = onLogin) { Text("Sign in") }
                    }
                }
            }

            item(key = "header") { HeaderCard(a, settings, onCopyId = { id ->
                clipboard.setText(AnnotatedString(id))
                vm.say("Alpha id copied")
            }) }

            item(key = "expression") {
                SectionCard("Expression", trailing = {
                    Row {
                        IconButton(onClick = {
                            clipboard.setText(AnnotatedString(a.expression))
                            vm.say("Expression copied")
                        }) { Icon(Icons.Filled.ContentCopy, "Copy") }
                        IconButton(onClick = {
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, a.expression)
                            context.startActivity(Intent.createChooser(send, "Share expression"))
                        }) { Icon(Icons.Filled.Share, "Share") }
                        IconButton(
                            onClick = { editExpression = true },
                            enabled = a.status != AlphaStatus.RUNNING && a.status != AlphaStatus.QUEUED,
                        ) { Icon(Icons.Filled.Edit, "Edit") }
                    }
                }) {
                    SelectionContainer { Text(a.expression, style = CodeStyle) }
                    val tokens = remember(a.expression) { ExpressionTools.tokens(a.expression) }
                    if (tokens.operators.isNotEmpty()) {
                        Text("Operators: " + tokens.operators.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (tokens.fields.isNotEmpty()) {
                        Text("Fields: " + tokens.fields.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    ExpressionTools.validate(a.expression)?.let {
                        Text("Possible problem: $it", style = MaterialTheme.typography.bodySmall, color = Signals.current.warn)
                    }
                }
            }

            when (a.status) {
                AlphaStatus.RUNNING, AlphaStatus.QUEUED -> item(key = "progress") {
                    SectionCard(if (a.status == AlphaStatus.RUNNING) "Simulating" else "Queued") {
                        val p = a.progress
                        if (a.status == AlphaStatus.RUNNING && p != null && p > 0) {
                            LinearProgressIndicator(progress = { p.toFloat() }, modifier = Modifier.fillMaxWidth())
                            Text("${(p * 100).toInt()}%", style = CodeStyle)
                        } else LinearProgressIndicator(Modifier.fillMaxWidth())
                        a.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
                AlphaStatus.ERROR -> item(key = "error") {
                    SectionCard("Simulation error") {
                        Text(a.message ?: "Unknown error", style = CodeStyle, color = Signals.current.bad)
                    }
                }
                else -> if (a.message != null) item(key = "note") {
                    Text(a.message, style = MaterialTheme.typography.bodySmall, color = Signals.current.warn)
                }
            }

            if (a.sharpe != null || a.fitness != null) {
                item(key = "metrics") { MetricsCard(a, settings.delay) }
            }

            if (a.alphaId != null && a.status == AlphaStatus.COMPLETE) {
                item(key = "pnl") {
                    SectionCard("Cumulative PnL (in-sample)", trailing = {
                        IconButton(onClick = { vm.loadPnl(a.alphaId, force = true) }) { Icon(Icons.Filled.Refresh, "Reload PnL") }
                    }) {
                        when {
                            ui.pnlLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                                Text("Loading PnL from BRAIN…", style = MaterialTheme.typography.bodySmall)
                            }
                            ui.pnlError != null -> Text(ui.pnlError ?: "", style = MaterialTheme.typography.bodySmall, color = Signals.current.bad)
                            ui.pnl != null -> PnlChart(ui.pnl ?: emptyList())
                            else -> TextButton(onClick = { vm.loadPnl(a.alphaId, force = false) }) { Text("Load PnL") }
                        }
                    }
                }
                item(key = "checks") {
                    ChecksCard(
                        alpha = a,
                        busy = ui.busy != null,
                        onRunChecks = { vm.runChecks() },
                        onSelfCorr = { vm.correlation("self") },
                        onProdCorr = { vm.correlation("prod") },
                    )
                }
                ui.yearly?.let { rs ->
                    if (rs.rows.isNotEmpty()) item(key = "yearly") { YearlyCard(rs) }
                }
            }

            item(key = "settings") {
                SectionCard(if (a.overrideSettings != null) "Settings (custom for this alpha)" else "Settings (from list)", trailing = {
                    TextButton(onClick = { editSettings = true }, enabled = a.status != AlphaStatus.RUNNING) { Text("Change") }
                }) {
                    SettingsTable(settings)
                }
            }

            item(key = "actions") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                container.appScope.launch {
                                    container.repo.reset(listOf(a.id))
                                    container.repo.queue(listOf(a.id))
                                }
                                startRun()
                            },
                            enabled = a.status != AlphaStatus.RUNNING && a.status != AlphaStatus.QUEUED,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.PlayArrow, null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (a.status == AlphaStatus.IDLE) "Simulate" else "Re-simulate")
                        }
                        FilledTonalButton(
                            onClick = { confirmSubmit = true },
                            enabled = a.alphaId != null && ui.busy == null && a.alphaStatus != "ACTIVE",
                            modifier = Modifier.weight(1f),
                        ) { Text(if (a.alphaStatus == "ACTIVE") "Submitted" else "Submit") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { editProps = true }, enabled = ui.busy == null, modifier = Modifier.weight(1f)) {
                            Text("Name & tags")
                        }
                        OutlinedButton(
                            onClick = { confirmDelete = true },
                            enabled = a.status != AlphaStatus.RUNNING,
                            modifier = Modifier.weight(1f),
                        ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }

    if (a != null && editExpression) {
        TextInputDialog(
            title = "Edit expression",
            initial = a.expression,
            label = "FASTEXPR",
            confirmLabel = "Save",
            singleLine = false,
            supporting = "Saving clears this alpha's stored results.",
            onConfirm = { e -> container.appScope.launch { container.repo.updateExpression(a.id, e) } },
            onDismiss = { editExpression = false },
        )
    }
    if (a != null && editSettings) {
        SimSettingsDialog(
            title = "Settings for this alpha",
            initial = settings,
            onDismiss = { editSettings = false },
            onSave = { s -> container.appScope.launch { container.repo.setOverride(a.id, s) } },
            extraAction = if (a.overrideSettings != null) {
                {
                    TextButton(onClick = {
                        container.appScope.launch { container.repo.setOverride(a.id, null) }
                        editSettings = false
                    }) { Text("Use list defaults") }
                }
            } else null,
        )
    }
    if (a != null && editProps) {
        TextInputDialog(
            title = "Name & tags",
            initial = listOfNotNull(a.alphaName, a.tagList().takeIf { it.isNotEmpty() }?.joinToString(", ")).joinToString(" | "),
            label = "name | tag1, tag2",
            confirmLabel = "Save",
            supporting = "Format: name | comma-separated tags. Saved on BRAIN too when the alpha has been simulated.",
            onConfirm = { raw ->
                val name = raw.substringBefore('|').trim().ifBlank { null }
                val tags = if ('|' in raw) raw.substringAfter('|').split(',').map { it.trim() }.filter { it.isNotEmpty() } else emptyList()
                vm.saveProperties(name, tags)
            },
            onDismiss = { editProps = false },
        )
    }
    if (a != null && confirmSubmit) {
        ConfirmDialog(
            title = "Submit this alpha?",
            text = "BRAIN runs its submission checks and, if they pass, the alpha moves out of your unsubmitted list. This can't be undone from the app.",
            confirmLabel = "Submit",
            onConfirm = { vm.submit() },
            onDismiss = { confirmSubmit = false },
        )
    }
    if (a != null && confirmDelete) {
        ConfirmDialog(
            title = "Delete this alpha from the list?",
            text = "Only removes it from AlphaSim. A simulated alpha stays on BRAIN.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                container.appScope.launch { container.repo.delete(listOf(a.id)) }
                onBack()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun HeaderCard(a: AlphaEntity, settings: SimSettings, onCopyId: (String) -> Unit) {
    SectionCard(null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip(a.status, a.failedChecks)
            a.grade?.let { com.alphasim.brain.ui.components.Pill(it.lowercase().replaceFirstChar { c -> c.uppercase() }, Signals.current.info) }
            a.alphaStatus?.let { com.alphasim.brain.ui.components.Pill(it, if (it == "ACTIVE") Signals.current.good else Signals.current.muted) }
            Spacer(Modifier.weight(1f))
            Text("#${a.position}", style = CodeStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        a.alphaId?.let { id ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(id, style = MaterialTheme.typography.titleLarge.copy(fontFamily = CodeStyle.fontFamily), modifier = Modifier.weight(1f))
                IconButton(onClick = { onCopyId(id) }) { Icon(Icons.Filled.ContentCopy, "Copy id") }
            }
        }
        Text(settings.summary(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val tags = a.tagList()
        if (tags.isNotEmpty()) Text("Tags: " + tags.joinToString(", "), style = MaterialTheme.typography.bodySmall)
        if (a.startedAt != null && a.finishedAt != null && a.finishedAt >= a.startedAt) {
            Text("Simulated in ${Fmt.duration(a.finishedAt - a.startedAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MetricsCard(a: AlphaEntity, delay: Int) {
    SectionCard("In-sample performance") {
        Row(Modifier.fillMaxWidth()) {
            Metric("Sharpe", Fmt.num(a.sharpe), sharpeColor(a.sharpe, delay), Modifier.weight(1f))
            Metric("Fitness", Fmt.num(a.fitness), fitnessColor(a.fitness, delay), Modifier.weight(1f))
            Metric("Turnover", Fmt.pct(a.turnover), turnoverColor(a.turnover), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth()) {
            Metric("Returns", Fmt.pct(a.returns), modifier = Modifier.weight(1f))
            Metric("Drawdown", Fmt.pct(a.drawdown), modifier = Modifier.weight(1f))
            Metric("Margin", Fmt.bps(a.margin), modifier = Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth()) {
            Metric("PnL", Fmt.money(a.pnl), modifier = Modifier.weight(1f))
            Metric("Long", Fmt.int(a.longCount), modifier = Modifier.weight(1f))
            Metric("Short", Fmt.int(a.shortCount), modifier = Modifier.weight(1f))
        }
        Text(
            "Thresholds for delay $delay: Sharpe ≥ ${com.alphasim.brain.ui.components.Thresholds.sharpe(delay)}, fitness ≥ ${com.alphasim.brain.ui.components.Thresholds.fitness(delay)}, turnover 1%–70%.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChecksCard(alpha: AlphaEntity, busy: Boolean, onRunChecks: () -> Unit, onSelfCorr: () -> Unit, onProdCorr: () -> Unit) {
    val checks = remember(alpha.checksJson) { alpha.checks }
    SectionCard("Checks") {
        if (checks.isEmpty()) {
            Text("No checks stored yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        checks.forEach { CheckRow(it) }
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Self-correlation", style = MaterialTheme.typography.bodyMedium)
                Text(
                    alpha.selfCorr?.let { String.format(java.util.Locale.US, "max %.3f", it) } ?: "not computed",
                    style = CodeStyle,
                    color = alpha.selfCorr?.let { if (it < 0.7) Signals.current.good else Signals.current.bad } ?: Signals.current.muted,
                )
            }
            TextButton(onClick = onSelfCorr, enabled = !busy) { Text("Compute") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Production correlation", style = MaterialTheme.typography.bodyMedium)
                Text(
                    alpha.prodCorr?.let { String.format(java.util.Locale.US, "max %.3f", it) } ?: "not computed (consultant accounts)",
                    style = CodeStyle,
                    color = alpha.prodCorr?.let { if (it < 0.7) Signals.current.good else Signals.current.bad } ?: Signals.current.muted,
                )
            }
            TextButton(onClick = onProdCorr, enabled = !busy) { Text("Compute") }
        }
        FilledTonalButton(onClick = onRunChecks, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text("Run full submission check")
        }
    }
}

@Composable
private fun CheckRow(c: CheckResult) {
    val s = Signals.current
    val (icon, tint) = when {
        c.passed -> Icons.Filled.CheckCircle to s.good
        c.failed -> Icons.Filled.Error to s.bad
        c.pending -> Icons.Filled.HourglassTop to s.info
        else -> Icons.Filled.Warning to s.warn
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, c.result, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(c.label, style = MaterialTheme.typography.bodyMedium)
            c.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        val detail = listOfNotNull(
            c.value?.let { Fmt.num(it, 3) },
            c.limit?.let { "limit ${Fmt.num(it, 3)}" },
        ).joinToString(" / ")
        if (detail.isNotEmpty()) Text(detail, style = CodeStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun YearlyCard(rs: Recordset) {
    val wanted = listOf("year", "sharpe", "returns", "turnover", "drawdown", "fitness", "margin", "longCount", "shortCount")
    val cols = wanted.map { it to rs.col(it) }.filter { it.second >= 0 }
    SectionCard("Yearly stats") {
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            Row {
                cols.forEach { (name, _) ->
                    Text(
                        when (name) { "longCount" -> "long"; "shortCount" -> "short"; else -> name },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(if (name == "year") 52.dp else 76.dp),
                    )
                }
            }
            rs.rows.indices.forEach { r ->
                Row(Modifier.padding(top = 4.dp)) {
                    cols.forEach { (name, c) ->
                        val v = rs.doubleAt(r, c)
                        val text = when (name) {
                            "year" -> rs.stringAt(r, c) ?: ""
                            "returns", "turnover", "drawdown" -> Fmt.pct(v)
                            "margin" -> Fmt.bps(v)
                            "longCount", "shortCount" -> v?.toInt()?.toString() ?: "—"
                            else -> Fmt.num(v)
                        }
                        Text(text, style = CodeStyle, modifier = Modifier.width(if (name == "year") 52.dp else 76.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsTable(s: SimSettings) {
    val rows = listOf(
        "Region" to s.region, "Universe" to s.universe, "Delay" to s.delay.toString(), "Decay" to s.decay.toString(),
        "Neutralization" to s.neutralization, "Truncation" to s.truncation.toString(),
        "Pasteurization" to s.pasteurization, "NaN handling" to s.nanHandling, "Unit handling" to s.unitHandling,
        "Test period" to s.testPeriod, "Max trade" to s.maxTrade, "Language" to s.language,
    )
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        rows.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth()) {
                pair.forEach { (k, v) ->
                    Row(Modifier.weight(1f)) {
                        Text(k, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(100.dp))
                        Text(v, style = CodeStyle)
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
