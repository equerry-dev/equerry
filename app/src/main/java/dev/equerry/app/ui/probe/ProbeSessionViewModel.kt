package dev.equerry.app.ui.probe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.equerry.app.assistant.ProbeRecord
import dev.equerry.app.data.ProbeStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the live assist-session dashboard. [onCapture] records each invocation to the
 * shared [ProbeStore] (so it joins the accumulated log) and exposes the most recent
 * capture for the session UI to render.
 */
@HiltViewModel
class ProbeSessionViewModel @Inject constructor(
    private val store: ProbeStore,
) : ViewModel() {

    private val _currentCapture = MutableStateFlow<ProbeRecord?>(null)
    val currentCapture: StateFlow<ProbeRecord?> = _currentCapture.asStateFlow()

    fun onCapture(record: ProbeRecord) {
        _currentCapture.value = record
        viewModelScope.launch { store.append(record) }
    }
}
