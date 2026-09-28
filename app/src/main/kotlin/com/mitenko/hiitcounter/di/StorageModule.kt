package com.mitenko.hiitcounter.di

import android.content.Context
import android.util.Log
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.mitenko.hiitcounter.data.AppPreferences
import com.mitenko.hiitcounter.data.EntryRepository
import com.mitenko.hiitcounter.data.RoomEntryRepository
import com.mitenko.hiitcounter.data.db.HiitDatabase
import com.mitenko.hiitcounter.data.v1.V1Migrator
import com.mitenko.hiitcounter.domain.Clock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.io.File
import javax.inject.Singleton

/** Room + app preferences + the v1 migrator (spec §4). app.preferences_pb is the only Hilt DataStore after 15.2. */
@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): HiitDatabase =
        Room.databaseBuilder(context, HiitDatabase::class.java, HiitDatabase.NAME).build()

    @Provides @Singleton
    fun appPreferences(@ApplicationContext context: Context): AppPreferences = AppPreferences(
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { e ->
                Log.e("StorageModule", "app.preferences_pb corrupt; replacing with defaults", e)
                emptyPreferences()
            },
            produceFile = { context.preferencesDataStoreFile(AppPreferences.FILE_NAME) },
        ),
    )

    /** The v1 files live where v1's preferencesDataStoreFile put them: filesDir/datastore. */
    @Provides @Singleton
    fun v1Migrator(
        @ApplicationContext context: Context,
        db: HiitDatabase,
        preferences: AppPreferences,
        @ApplicationScope scope: CoroutineScope,
    ): V1Migrator = V1Migrator(File(context.filesDir, "datastore"), db, preferences, scope, Dispatchers.IO)

    @Provides @Singleton
    fun entryRepository(db: HiitDatabase, migrator: V1Migrator, clock: Clock): EntryRepository =
        RoomEntryRepository(db, migrator, clock)
}
