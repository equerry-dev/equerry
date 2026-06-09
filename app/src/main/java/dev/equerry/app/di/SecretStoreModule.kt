package dev.equerry.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.equerry.app.data.EncryptedSecretStore
import dev.equerry.app.data.SecretStore
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SecretStoreModule {

    @Provides
    @Singleton
    fun provideSecretStore(@ApplicationContext context: Context): SecretStore =
        EncryptedSecretStore.create(context)
}
