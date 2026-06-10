package dev.equerry.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
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
}
