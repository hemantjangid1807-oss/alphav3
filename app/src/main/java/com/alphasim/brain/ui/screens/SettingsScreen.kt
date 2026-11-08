@file:OptIn(ExperimentalMaterial3Api::class)

package com.alphasim.brain.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alphasim.brain.AppContainer
import com.alphasim.brain.data.prefs.AppSettings
import com.alphasim.brain.ui.components.ConfirmDialog
import com.alphasim.brain.ui.components.Fmt
import com.alphasim.brain.ui.components.SectionCard
import com.alphasim.brain.ui.theme.CodeStyle
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit, onLogin: () -> Unit) {
    val settings by container.prefs.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val session by container.session.state.collectAsStateWithLifecycle()
    val engine by container.engine.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = container.appScope
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }

    fun ask(text: String, action: () -> Unit) {
        confirm = text to action
    }

    fun update(t: (AppSettings) -> AppSettings) {
        scope.launch { container.prefs.update(t) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionCard("BRAIN account") {
                if (session.loggedIn) {
                    Text(session.userId ?: "Signed in", style = MaterialTheme.typography.titleMedium.copy(fontFamily = CodeStyle.fontFamily))
                    session.expiresAt?.let {
                        val left = it - System.currentTimeMillis()
                        Text(
                            if (left > 0) "Token valid for ${Fmt.duration(left)} · renews automatically" else "Token will renew on the next request",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (session.permissions.isNotEmpty()) {
                        Text(session.permissions.joinToString(" · "), style = CodeStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { container.session.refreshInfo() }) { Text("Refresh") }
                        OutlinedButton(onClick = { container.session.logout() }) { Text("Sign out") }
                    }
                } else {
                    Text("Not signed in", style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = onLogin) { Text("Sign in") }
                }
            }

            SectionCard("Simulation engine") {
                if (engine.running) {
                    Text(
                        "Changes apply to the next run (current run: ${engine.active} in flight).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                SliderRow(
                    title = "Concurrent simulations",
                    value = settings.concurrency,
                    range = 1..10,
                    help = "Most BRAIN accounts allow 3 simulations at once; consultants get more. Extra slots just wait for a free one.",
                ) { v -> update { it.copy(concurrency = v) } }

                SwitchRow(
                    title = "Multi-simulation",
                    checked = settings.multiSim,
                    help = "Sends several alphas per request (needs BRAIN's multi-simulation permission). Faster for big lists.",
                ) { v -> update { it.copy(multiSim = v) } }
                if (settings.multiSim) {
                    SliderRow(title = "Alphas per request", value = settings.multiBatch, range = 2..10, help = null) { v ->
                        update { it.copy(multiBatch = v) }
                    }
                }
                SliderRow(
                    title = "Retries on server/network errors",
                    value = settings.maxRetries,
                    range = 0..10,
                    help = "Expression errors are never retried. Rate limits wait and don't count.",
                ) { v -> update { it.copy(maxRetries = v) } }
                SliderRow(
                    title = "Minimum polling interval (seconds)",
                    value = settings.minPollSec.roundToInt(),
                    range = 1..15,
                    help = "BRAIN tells the app when to check back; this is the lower bound.",
                ) { v -> update { it.copy(minPollSec = v.toDouble()) } }
                SwitchRow(
                    title = "Compute self-correlation after each simulation",
                    checked = settings.autoSelfCorr,
                    help = "Slower, but you see correlation with your submitted alphas in the list.",
                ) { v -> update { it.copy(autoSelfCorr = v) } }
            }

            SectionCard("Running in the background") {
                Text(
                    "Runs continue with the screen off through a foreground service. For very long lists, exempt AlphaSim from battery optimisation so the phone doesn't pause it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                    }) { Text("Battery settings") }
                    OutlinedButton(onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            )
                        }
                    }) { Text("Notifications") }
                }
            }

            SectionCard("Appearance") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("dark" to "Dark", "light" to "Light", "system" to "System").forEach { (key, label) ->
                        FilterChip(selected = settings.theme == key, onClick = { update { it.copy(theme = key) } }, label = { Text(label) })
                    }
                }
            }

            SectionCard("Data") {
                TextButton(onClick = {
                    ask("Remove every alpha from the simulation queue?") { scope.launch { container.repo.unqueueAll() } }
                }) { Text("Clear the queue") }
                TextButton(onClick = {
                    ask("Clear cached PnL charts and yearly stats?") { scope.launch { container.repo.clearCache() } }
                }) { Text("Clear cached charts") }
                TextButton(onClick = {
                    ask("Delete ALL lists and results from this phone? Alphas on BRAIN are not affected.") {
                        container.engine.stop()
                        scope.launch { container.repo.resetEverything() }
                    }
                }) { Text("Delete all local data", color = MaterialTheme.colorScheme.error) }
            }

            SectionCard("About") {
                Text("AlphaSim 1.0 — an unofficial client for the WorldQuant BRAIN simulation API.", style = MaterialTheme.typography.bodySmall)
                Text(
                    "Simulations count against your BRAIN account's limits. Use it within BRAIN's terms of use.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://platform.worldquantbrain.com"))) }
                }) { Text("Open BRAIN platform") }
            }
        }
    }

    confirm?.let { (text, action) ->
        ConfirmDialog(title = "Are you sure?", text = text, confirmLabel = "Continue", destructive = true, onConfirm = action, onDismiss = { confirm = null })
    }
}

@Composable
private fun SliderRow(title: String, value: Int, range: IntRange, help: String?, onChange: (Int) -> Unit) {
    var local by remember(value) { mutableStateOf(value.toFloat()) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(local.roundToInt().toString(), style = CodeStyle)
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onChange(local.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
            modifier = Modifier.fillMaxWidth(),
        )
        help?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, help: String?, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            help?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
