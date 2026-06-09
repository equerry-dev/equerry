package dev.equerry.app.ui.providers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.equerry.app.providers.ProviderProfile
import dev.equerry.app.providers.ProviderRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Backs the provider-list screen: the saved profiles and a delete action. */
@HiltViewModel
class ProviderListViewModel @Inject constructor(
    private val repository: ProviderRepository,
) : ViewModel() {

    val profiles: StateFlow<List<ProviderProfile>> =
        repository.observeProfiles()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(profileId: String) {
        viewModelScope.launch { repository.deleteProfile(profileId) }
    }
}
