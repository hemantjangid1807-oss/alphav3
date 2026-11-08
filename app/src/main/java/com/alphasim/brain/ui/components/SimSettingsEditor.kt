package com.alphasim.brain.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alphasim.brain.data.model.SettingsCatalog
import com.alphasim.brain.data.model.SimSettings

@Composable
fun SimSettingsEditor(value: SimSettings, onChange: (SimSettings) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DropdownField(
                "Region", value.region, SettingsCatalog.regions,
                onSelect = { r ->
                    if (r in SettingsCatalog.regions) {
                        val universes = SettingsCatalog.universesFor(r)
                        val delays = SettingsCatalog.delaysFor(r)
                        onChange(
                            value.copy(
                                region = r,
                                universe = if (value.universe in universes) value.universe else universes.first(),
                                delay = if (value.delay in delays) value.delay else delays.last(),
                            )
                        )
                    } else {
                        onChange(value.copy(region = r)) // a region typed by hand
                    }
                },
                modifier = Modifier.weight(1f),
                allowCustom = true,
            )
            DropdownField(
                "Universe", value.universe, SettingsCatalog.universesFor(value.region),
                onSelect = { onChange(value.copy(universe = it)) },
                modifier = Modifier.weight(1.3f),
                allowCustom = true,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DropdownField(
                "Delay", value.delay.toString(), SettingsCatalog.delaysFor(value.region).map { it.toString() },
                onSelect = { d -> d.toIntOrNull()?.let { onChange(value.copy(delay = it)) } },
                modifier = Modifier.weight(1f),
            )
            IntField("Decay", value.decay, { onChange(value.copy(decay = it)) }, Modifier.weight(1f), 0..512)
            DecimalField("Truncation", value.truncation, { onChange(value.copy(truncation = it)) }, Modifier.weight(1.1f), 0.0..1.0)
        }
        DropdownField(
            "Neutralization", value.neutralization, SettingsCatalog.neutralizations,
            onSelect = { onChange(value.copy(neutralization = it)) },
            modifier = Modifier.fillMaxWidth(),
            allowCustom = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DropdownField(
                "Pasteurize", value.pasteurization, SettingsCatalog.onOff,
                onSelect = { onChange(value.copy(pasteurization = it)) },
                modifier = Modifier.weight(1f),
            )
            DropdownField(
                "NaN handling", value.nanHandling, SettingsCatalog.onOff,
                onSelect = { onChange(value.copy(nanHandling = it)) },
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DropdownField(
                "Unit handling", value.unitHandling, SettingsCatalog.unitHandling,
                onSelect = { onChange(value.copy(unitHandling = it)) },
                modifier = Modifier.weight(1f),
            )
            DropdownField(
                "Max trade", value.maxTrade, SettingsCatalog.maxTrade,
                onSelect = { onChange(value.copy(maxTrade = it)) },
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DropdownField(
                "Test period", value.testPeriod, SettingsCatalog.testPeriods,
                onSelect = { onChange(value.copy(testPeriod = it)) },
                modifier = Modifier.weight(1f),
                allowCustom = true,
            )
            DropdownField(
                "Language", value.language, SettingsCatalog.languages,
                onSelect = { onChange(value.copy(language = it)) },
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            "Universe, neutralization and region accept typed values too, in case your account has options not listed here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun SimSettingsDialog(
    title: String,
    initial: SimSettings,
    onDismiss: () -> Unit,
    onSave: (SimSettings) -> Unit,
    extraAction: (@Composable () -> Unit)? = null,
) {
    var draft by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SimSettingsEditor(draft, { draft = it })
            }
        },
        confirmButton = { TextButton(onClick = { onSave(draft); onDismiss() }) { Text("Save") } },
        dismissButton = {
            Row {
                extraAction?.invoke()
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
