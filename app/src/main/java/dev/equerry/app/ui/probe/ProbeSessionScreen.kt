package dev.equerry.app.ui.probe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.equerry.app.assistant.ProbeRecord

/**
 * Live dashboard shown inside the assist session: the capture from the current
 * invocation (foreground app, whether the AssistStructure + screenshot arrived). One
 * invocation at a time — the accumulated table lives on the probe-log screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProbeSessionScreen(record: ProbeRecord?) {
    Scaffold(topBar = { TopAppBar(title = { Text("Assist probe") }) }) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (record == null) {
                Text("Waiting for an assist invocation…", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(record.packageName, style = MaterialTheme.typography.titleMedium)
                Text(structureLine(record), style = MaterialTheme.typography.bodyMedium)
                Text(screenshotLine(record), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

internal fun structureLine(r: ProbeRecord): String =
    if (r.structureProvided) "AssistStructure: yes (${r.nodeCount} nodes)" else "AssistStructure: no"

internal fun screenshotLine(r: ProbeRecord): String =
    if (r.screenshotArrived) "Screenshot: arrived (${r.screenshotWidth}x${r.screenshotHeight})" else "Screenshot: blocked"
