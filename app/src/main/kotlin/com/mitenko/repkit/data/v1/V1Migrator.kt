package com.mitenko.repkit.data.v1

import android.util.Log
import androidx.datastore.core.CorruptionException
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.room.withTransaction
import com.mitenko.repkit.data.AppPreferences
import com.mitenko.repkit.data.MigrationGate
import com.mitenko.repkit.data.db.HiitDatabase
import com.mitenko.repkit.data.db.MetaEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * One-time import of the v1 DataStore files into Room (spec §6). [ready] completes once the gate,
 * the import (if any), the flag copy and the cleanup have run; every EntryRepository entry point
 * awaits it. HiitApp.onCreate starts it on [io] — never with runBlocking; awaiting also starts it.
 * A failure is logged and still completes [ready]: nothing is deleted before the import commits
 * and no marker is written without it, so the next launch retries.
 */
class V1Migrator(
    private val dataStoreDir: File,
    private val db: HiitDatabase,
    private val appPreferences: AppPreferences,
    scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    /** Cleanup's file deletion; tests replace it to inspect files after the import. */
    private val deleteFile: (File) -> Boolean = { it.delete() },
) : MigrationGate {
    val ready: Deferred<Unit> = scope.async(io, start = CoroutineStart.LAZY) { migrate() }

    fun start() {
        ready.start()
    }

    override suspend fun awaitReady() = ready.await()

    private suspend fun migrate() {
        try {
            val settingsFile = File(dataStoreDir, SETTINGS_FILE)
            val counterFile = File(dataStoreDir, COUNTER_FILE)
            val hasSettings = settingsFile.exists()
            val hasCounter = counterFile.exists()
            // Step 1: the gate. Completion is recorded in meta, never inferred from an empty table.
            val migrated = db.metaDao().get(HiitDatabase.KEY_V1_MIGRATED) == MARKER_VALUE
            val needsImport = !migrated && (hasSettings || hasCounter)
            // Step 2: read through migration-only DataStores. Settings are read whenever the file
            // exists, because step 4 still needs the flag after a crash that followed the commit.
            val (settings, counter) = readV1Files(
                settingsFile.takeIf { hasSettings },
                counterFile.takeIf { needsImport && hasCounter },
            )
            // Step 3: the entry (shifting any existing rows up) and the marker commit together.
            if (!migrated) {
                db.withTransaction {
                    if (needsImport) {
                        db.entryDao().shiftPositions(low = 0, high = Int.MAX_VALUE, delta = 1)
                        db.entryDao().insert(v1Entry(settings, counter))
                    }
                    db.metaDao().put(MetaEntity(HiitDatabase.KEY_V1_MIGRATED, MARKER_VALUE))
                }
            }
            // Step 4: copy the sticky flag (idempotent; never resets it, never creates it without v1 settings).
            if (hasSettings && settings[V1Keys.NOTIFICATION_ASKED] == true) appPreferences.markNotificationPermissionAsked()
            // Step 5: only now, after the commit, delete the v1 data.
            cleanUp()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "v1 migration failed; it is retried on the next launch", e)
        }
    }

    private suspend fun readV1Files(settingsFile: File?, counterFile: File?): Pair<Preferences, Preferences> {
        if (settingsFile == null && counterFile == null) return emptyPreferences() to emptyPreferences()
        val job = SupervisorJob()
        val scope = CoroutineScope(io + job)
        try {
            val settings = settingsFile?.let { read(scope, it) } ?: emptyPreferences()
            val counter = counterFile?.let { read(scope, it) } ?: emptyPreferences()
            return settings to counter
        } finally {
            // Cancel and join the migration scope so both DataStores close and release their files.
            // NonCancellable: this cleanup must run even if the outer migration was itself cancelled.
            withContext(NonCancellable) { job.cancelAndJoin() }
        }
    }

    /**
     * No corruption handler: ReplaceFileCorruptionHandler would rewrite the v1 file before the import
     * commits. A corrupted file reads as empty (the v1 defaults / a fresh counter) and its bytes stay
     * untouched until cleanup. Any other IOException propagates, aborting the migration so it retries.
     */
    private suspend fun read(scope: CoroutineScope, file: File): Preferences = try {
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }).data.first()
    } catch (e: CorruptionException) {
        Log.w(TAG, "${file.name} is corrupt; using defaults", e)
        emptyPreferences()
    }

    /** Deletes the two v1 files and their DataStore `.tmp` siblings; app.preferences_pb is never touched. */
    private fun cleanUp() {
        for (name in listOf(SETTINGS_FILE, COUNTER_FILE)) {
            for (file in listOf(File(dataStoreDir, name), File(dataStoreDir, "$name.tmp"))) {
                if (file.exists() && !deleteFile(file)) Log.w(TAG, "Couldn't delete ${file.name}")
            }
        }
    }

    companion object {
        const val SETTINGS_FILE = "settings.preferences_pb"
        const val COUNTER_FILE = "counter.preferences_pb"
        private const val MARKER_VALUE = "true"
        private const val TAG = "V1Migrator"
    }
}
