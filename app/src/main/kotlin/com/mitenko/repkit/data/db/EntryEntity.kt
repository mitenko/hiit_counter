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
    /** Legacy (spec rev 16 §5): mirrors the first hold (64 when there is none). Read only when [holds] is "". */
    @ColumnInfo(name = "hold_at") val holdAt: Int,
    /** Legacy (spec rev 16 §5): mirrors the first hold (4 when there is none). Read only when [holds] is "". */
    @ColumnInfo(name = "hold_for") val holdFor: Int,
    /**
     * The holds list (spec rev 16 §5), added in schema v5 as TEXT NOT NULL DEFAULT '' and encoded
     * by HoldsCodec. "" (never written by v5 code) means "read the legacy hold_at / hold_for".
     */
    @ColumnInfo(defaultValue = "") val holds: String,
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
    /** Spec rev 26 §5 (schema v7): REPS / WEIGHT / REPS_THEN_WEIGHT. EntryMapping reads an unknown value as REPS. */
    @ColumnInfo(name = "progress_mode", defaultValue = "REPS") val progressMode: String = "REPS",
    /** KG / LB. NULL until the workout first switches into a weight mode (spec rev 26 §9.3). */
    @ColumnInfo(name = "weight_unit") val weightUnit: String? = null,
    /** STEPS / LIST (spec rev 26 §5). */
    @ColumnInfo(name = "weights_kind", defaultValue = "STEPS") val weightsKind: String = "STEPS",
    /** WeightCodecs "start:step:top" in hundredths; '' = WeightSteps.DEFAULT. */
    @ColumnInfo(name = "weight_steps", defaultValue = "") val weightSteps: String = "",
    /** WeightCodecs "800,1200,1600" in hundredths; '' = empty. */
    @ColumnInfo(name = "weight_list", defaultValue = "") val weightList: String = "",
    /** Weight-mode holds by value, WeightCodecs "weight:reps:for" (plan Spec note 2); '' = none. */
    @ColumnInfo(name = "weight_holds", defaultValue = "") val weightHolds: String = "",
    @ColumnInfo(name = "reps_per_set", defaultValue = "10") val repsPerSet: Int = 10,
    @ColumnInfo(name = "rep_min", defaultValue = "8") val repMin: Int = 8,
    @ColumnInfo(name = "rep_max", defaultValue = "12") val repMax: Int = 12,
    /** NULL = the lightest weight (spec rev 26 §5). */
    @ColumnInfo(name = "start_weight") val startWeight: Int? = null,
    /** NULL = rep_min (spec rev 26 §5). */
    @ColumnInfo(name = "start_reps") val startReps: Int? = null,
    /**
     * Set by Start fresh until the next recorded Counter check-in, which is then performed at the start
     * (plan Spec note 13, user ruling A). Schema v7, INTEGER NOT NULL DEFAULT 0.
     */
    @ColumnInfo(name = "fresh_start", defaultValue = "0") val freshStart: Boolean = false,
)
