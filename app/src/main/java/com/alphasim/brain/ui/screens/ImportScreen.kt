@file:OptIn(ExperimentalMaterial3Api::class)

package com.alphasim.brain.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.alphasim.brain.AppContainer
import com.alphasim.brain.data.ExpressionTools
import com.alphasim.brain.data.model.SettingsCatalog
import com.alphasim.brain.data.model.SimSettings
import com.alphasim.brain.ui.components.SectionCard
import com.alphasim.brain.ui.components.SimSettingsEditor
import com.alphasim.brain.ui.components.rememberRunStarter
import com.alphasim.brain.ui.theme.CodeStyle
import com.alphasim.brain.ui.theme.Signals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class Bundled(val file: String, val label: String)

private val BUNDLED = listOf(
    Bundled("brain_alphas_GLB_200.txt", "GLB · 200"),
    Bundled("brain_alphas_USA_200.txt", "USA · 200"),
    Bundled("brain_alphas_GLB_200_v2.txt", "GLB · 200 v2"),
)

@Composable
fun ImportScreen(container: AppContainer, onBack: () -> Unit, onImported: (Long) -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val startRun = rememberRunStarter()

    var name by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var source by remember { mutableStateOf<String?>(null) }
    var settings by remember { mutableStateOf(SimSettings()) }
    var dedupe by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    val parsed = remember(text, dedupe) { ExpressionTools.parse(text, dedupe) }

    fun load(label: String, fileName: String?, content: String) {
        text = content
        source = label
        name = fileName?.substringBeforeLast('.')?.ifBlank { null } ?: label
        settings = SettingsCatalog.defaultsFor(SettingsCatalog.guessRegion(fileName, content))
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val (fileName, content) = withContext(Dispatchers.IO) { readUri(context, uri) }
                if (content == null) Toast.makeText(context, "Couldn't read that file", Toast.LENGTH_SHORT).show()
                else load(fileName ?: "Imported file", fileName, content)
            }
        }
    }

    fun doImport(run: Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            val repo = container.repo
            val id = withContext(Dispatchers.IO) {
                val listId = repo.createList(name.trim().ifBlank { "Alpha list" }, source, settings, parsed.expressions)
                if (run) repo.queue(repo.alphasOf(listId).map { it.id })
                listId
            }
            busy = false
            if (run) startRun()
            onImported(id)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import alphas") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionCard("Source") {
                Text("Bundled lists", style = MaterialTheme.typography.labelMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BUNDLED.forEach { b ->
                        AssistChip(
                            onClick = {
                                val content = runCatching {
                                    context.assets.open("alphas/${b.file}").bufferedReader().use { it.readText() }
                                }.getOrNull()
                                if (content != null) load(b.label, b.file, content)
                            },
                            label = { Text(b.label) },
                            leadingIcon = { Icon(Icons.Filled.Inventory2, null) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { picker.launch(arrayOf("text/*", "application/octet-stream", "*/*")) }) {
                        Icon(Icons.Filled.FileOpen, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Open file")
                    }
                    FilledTonalButton(onClick = {
                        val clip = clipboard.getText()?.text.orEmpty()
                        if (clip.isBlank()) Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                        else load("Pasted", null, clip)
                    }) {
                        Icon(Icons.Filled.ContentPaste, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Paste")
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Expressions — one per line") },
                    textStyle = CodeStyle,
                    minLines = 5,
                    maxLines = 12,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = dedupe, onCheckedChange = { dedupe = it })
                    Text("Skip duplicate expressions", style = MaterialTheme.typography.bodyMedium)
                }
                ParseSummary(parsed)
            }

            SectionCard("List") {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("List name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionCard("Simulation settings") {
                Text(
                    "Applied to every alpha in the list. You can override settings for a single alpha later.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SimSettingsEditor(settings, { settings = it })
            }

            val ready = parsed.expressions.isNotEmpty() && !busy
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(onClick = { doImport(run = false) }, enabled = ready, modifier = Modifier.weight(1f)) {
                    Text("Import")
                }
                Button(onClick = { doImport(run = true) }, enabled = ready, modifier = Modifier.weight(1f)) {
                    Text("Import & run ${parsed.expressions.size}")
                }
            }
        }
    }
}

@Composable
private fun ParseSummary(parsed: ExpressionTools.ParseResult) {
    val parts = buildList {
        add("${parsed.expressions.size} expressions ready")
        if (parsed.duplicates > 0) add("${parsed.duplicates} duplicates skipped")
        if (parsed.comments > 0) add("${parsed.comments} comments ignored")
    }
    Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (parsed.warnings.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("${parsed.warnings.size} possible syntax problems:", style = MaterialTheme.typography.labelMedium, color = Signals.current.warn)
            parsed.warnings.take(5).forEach { Text(it, style = CodeStyle, color = Signals.current.warn) }
            if (parsed.warnings.size > 5) Text("…and ${parsed.warnings.size - 5} more", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun readUri(context: Context, uri: Uri): Pair<String?, String?> {
    val name = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()
    val content = runCatching {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
    }.getOrNull()
    return name to content
}
