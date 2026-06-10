package dev.equerry.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.equerry.app.data.ProbeStore
import dev.equerry.app.data.ProfileStore
import dev.equerry.app.data.SlotMappingStore
import dev.equerry.app.data.VoiceSettingsStore
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "equerry_settings")

@Module
@InstallIn(SingletonComponent::class)
object PersistenceModule {

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.settingsDataStore

    @Provides
    @Singleton
    fun provideProfileStore(dataStore: DataStore<Preferences>): ProfileStore = ProfileStore(dataStore)

    @Provides
    @Singleton
    fun provideSlotMappingStore(dataStore: DataStore<Preferences>): SlotMappingStore =
        SlotMappingStore(dataStore)

    @Provides
    @Singleton
    fun provideProbeStore(dataStore: DataStore<Preferences>): ProbeStore = ProbeStore(dataStore)

    @Provides
    @Singleton
    fun provideVoiceSettingsStore(dataStore: DataStore<Preferences>): VoiceSettingsStore =
        VoiceSettingsStore(dataStore)
}
