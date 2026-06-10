package dev.equerry.app.ui.probe

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.equerry.app.assistant.ProbeRecord
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/** Stateful entry point: collects the accumulated records and wires CSV export. */
@Composable
fun ProbeLogRoute(viewModel: ProbeLogViewModel = hiltViewModel()) {
    val records by viewModel.records.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    ProbeLogScreen(
        records = records,
        onExport = {
            scope.launch {
                val intent = ProbeShare.csvShareIntent(viewModel.exportCsv())
                context.startActivity(Intent.createChooser(intent, "Export probe results"))
            }
        },
    )
}

/**
 * The accumulated probe table: one row per recorded invocation, with a CSV export
 * affordance. Empty state guides the user to invoke Equerry as the assistant.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProbeLogScreen(records: List<ProbeRecord>, onExport: () -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Probe log") }) },
        floatingActionButton = {
            if (records.isNotEmpty()) {
                ExtendedFloatingActionButton(onClick = onExport) { Text("Export CSV") }
            }
        },
    ) { pad ->
        if (records.isEmpty()) {
            Column(
                Modifier.padding(pad).padding(24.dp).fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("No probes yet", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Invoke Equerry as the assistant to record a probe.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(pad).padding(horizontal = 16.dp),
            ) {
                items(records, key = { "${it.packageName}-${it.timestamp}" }) { r ->
                    Column {
                        Text(r.packageName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${structureLine(r)} · ${screenshotLine(r)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
