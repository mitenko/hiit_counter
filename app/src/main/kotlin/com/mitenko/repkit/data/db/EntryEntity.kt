package com.mitenko.repkit.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per entry (spec §5.2). `total` NULL reads as `starting_total`. The `position` index is
 * deliberately non-unique so in-transaction shifts never hit a constraint. Validity is enforced
 * by RoomEntryRepository on write and repaired per field on read (EntryMapping.kt).
 */
@Entity(tableName = "entry", indices = [Index(value = ["position"])])
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val position: Int,
    /**
     * WORKOUT or CHECK_IN (spec R4 §3.2), added in schema v3 as TEXT NOT NULL DEFAULT 'WORKOUT'.
     * A plain string: EntryMapping reads an unknown value as WORKOUT (plan Spec note 5).
     */
    @ColumnInfo(defaultValue = "WORKOUT") val type: String = "WORKOUT",
    @ColumnInfo(name = "prepare_sec") val prepareSec: Int,
    val sets: Int,
    @ColumnInfo(name = "work_sec") val workSec: Int,
    @ColumnInfo(name = "rest_sec") val restSec: Int,
    @ColumnInfo(name = "cooldown_sec") val cooldownSec: Int,
    @ColumnInfo(name = "starting_total") val startingTotal: Int,
    val floor: Int,
    val cap: Int,
    @ColumnInfo(name = "hold_at") val holdAt: Int,
    @ColumnInfo(name = "hold_for") val holdFor: Int,
    /** The Hold switch (spec R3 §5.2), added in schema v2 as INTEGER NOT NULL DEFAULT 1. */
    @ColumnInfo(name = "hold_enabled", defaultValue = "1") val holdEnabled: Boolean = true,
    @ColumnInfo(name = "window_hours") val windowHours: Int,
    @ColumnInfo(name = "penalty_hours_per_rep") val penaltyHoursPerRep: Double,
    @ColumnInfo(name = "cue_sound") val cueSound: Boolean,
    @ColumnInfo(name = "cue_vibration") val cueVibration: Boolean,
    /** The Voice cue (spec R4 §3.2), added in schema v3 as INTEGER NOT NULL DEFAULT 0. */
    @ColumnInfo(name = "cue_voice", defaultValue = "0") val cueVoice: Boolean = false,
    val total: Int?,
    @ColumnInfo(name = "best_streak") val bestStreak: Int,
    @ColumnInfo(name = "current_streak") val currentStreak: Int,
    @ColumnInfo(name = "hold_count") val holdCount: Int,
    @ColumnInfo(name = "last_check_in") val lastCheckIn: Long?,
)
