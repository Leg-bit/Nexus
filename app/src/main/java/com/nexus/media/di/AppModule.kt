package com.nexus.media.di

import android.app.Application
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun provideContainer(app: Application): AppContainer = AppContainer(app)

    @Provides fun provideRepository(c: AppContainer) = c.repository
    @Provides fun providePreferences(c: AppContainer) = c.prefs
    @Provides fun provideDatabase(c: AppContainer) = c.db
}
