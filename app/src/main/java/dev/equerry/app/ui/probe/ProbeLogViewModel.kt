package dev.equerry.app.ui.probe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.equerry.app.assistant.ProbeCsv
import dev.equerry.app.assistant.ProbeRecord
import dev.equerry.app.data.ProbeStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Backs the probe-log screen: the accumulated records and a CSV export of them. */
@HiltViewModel
class ProbeLogViewModel @Inject constructor(
    private val store: ProbeStore,
) : ViewModel() {

    val records: StateFlow<List<ProbeRecord>> =
        store.records().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Render all stored records as CSV via the real serializer (no stub). */
    suspend fun exportCsv(): String = ProbeCsv.toCsv(store.records().first())
}
