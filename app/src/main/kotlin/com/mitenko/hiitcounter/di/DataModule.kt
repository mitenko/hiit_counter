package com.mitenko.hiitcounter.di

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.mitenko.hiitcounter.data.CounterRepository
import com.mitenko.hiitcounter.data.DataStoreCounterRepository
import com.mitenko.hiitcounter.data.DataStoreSettingsRepository
import com.mitenko.hiitcounter.data.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class SettingsStore
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class CounterStore

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    private fun create(context: Context, name: String): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { e ->
            Log.e("DataModule", "$name.preferences_pb corrupt; replacing with defaults", e)
            emptyPreferences()
        },
        produceFile = { context.preferencesDataStoreFile(name) },
    )

    @Provides @Singleton @SettingsStore
    fun settingsStore(@ApplicationContext context: Context): DataStore<Preferences> = create(context, "settings")

    @Provides @Singleton @CounterStore
    fun counterStore(@ApplicationContext context: Context): DataStore<Preferences> = create(context, "counter")

    @Provides @Singleton
    fun settingsRepository(@SettingsStore store: DataStore<Preferences>): SettingsRepository =
        DataStoreSettingsRepository(store)

    @Provides @Singleton
    fun counterRepository(@CounterStore store: DataStore<Preferences>, settings: SettingsRepository): CounterRepository =
        DataStoreCounterRepository(store, settings)
}
