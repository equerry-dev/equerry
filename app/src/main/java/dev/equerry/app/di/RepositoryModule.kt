package dev.equerry.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.equerry.app.assistant.DefaultAssistantDetector
import dev.equerry.app.data.ProfileStore
import dev.equerry.app.data.SecretStore
import dev.equerry.app.data.SlotMappingStore
import dev.equerry.app.providers.ProviderRepository
import dev.equerry.app.providers.drivers.ChatHttpClient
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideProviderRepository(
        profileStore: ProfileStore,
        secretStore: SecretStore,
        slotMappingStore: SlotMappingStore,
    ): ProviderRepository = ProviderRepository(profileStore, secretStore, slotMappingStore)

    /** The single shared, key-redacting OkHttp client every chat driver streams through (t-3). */
    @Provides
    @Singleton
    fun provideChatHttpClient(): OkHttpClient = ChatHttpClient.create()

    /** Detects whether Equerry is the system default assistant — drives the onboarding step (t-1). */
    @Provides
    @Singleton
    fun provideDefaultAssistantDetector(
        @ApplicationContext context: Context,
    ): DefaultAssistantDetector = DefaultAssistantDetector(context)
}
