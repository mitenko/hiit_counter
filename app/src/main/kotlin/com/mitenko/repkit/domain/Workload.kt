package com.mitenko.repkit.domain

import com.mitenko.repkit.domain.model.Entry
import com.mitenko.repkit.domain.model.EntryType
import com.mitenko.repkit.domain.model.ProgressMode
import com.mitenko.repkit.domain.model.ProgressionConfig
import com.mitenko.repkit.domain.model.SetWeight
import com.mitenko.repkit.domain.model.WeightUnit

/*
 * What the next workout asks for (spec rev 26 §4), one answer for the entry screen, the list, the timer
 * and the session log. Pure.
 */

/** The reps of each of [sets] sets at [level]: Reps mode splits the total (RepDistributor), a weight mode repeats the level's reps per set. */
fun ProgressionConfig.setReps(level: Int, sets: Int): List<Int> = when (val s = scale()) {
    is ProgressionScale.Reps -> RepDistributor.distribute(level, sets)
    is ProgressionScale.Ladder -> List(sets) { s.prescription(level).reps }
}

/**
 * This entry's sets at [level]. A Counter follows its mode ([setReps]); Timer only keeps today's split,
 * which nothing shows (plan Spec note 61).
 */
fun Entry.setReps(level: Int = counter.total): List<Int> = setReps(progression, level)

/**
 * This entry's sets at [level], against [progression] rather than this entry's own (final review fix,
 * M1): a Start's check-in can race a progression edit, so the run must follow the progression re-read
 * after the check-in, not the one captured before it — while the set count still comes from this
 * entry's frozen [timing] (`TabataEngine` requires `repsPerSet.size == timing.sets`).
 */
fun Entry.setReps(progression: ProgressionConfig, level: Int): List<Int> =
    if (type == EntryType.WORKOUT) progression.setReps(level, timing.sets) else RepDistributor.distribute(level, timing.sets)

/** A Counter's load at [level] in a weight mode; null in Reps mode and for Timer only. */
fun Entry.load(level: Int = counter.total): Prescription.Load? = load(progression, level)

/** This entry's load at [level] against [progression] (final review fix, M1); see [setReps]. */
fun Entry.load(progression: ProgressionConfig, level: Int): Prescription.Load? =
    if (type == EntryType.WORKOUT) progression.loadAt(level) else null

/** The run's weight at [level] (spec rev 26 §4) in the workout's unit; KG stands in for a missing unit (§10 note 11). */
fun Entry.setWeight(level: Int = counter.total): SetWeight? = setWeight(progression, level)

/** This entry's run weight at [level] against [progression] (final review fix, M1); see [setReps]. */
fun Entry.setWeight(progression: ProgressionConfig, level: Int): SetWeight? =
    load(progression, level)?.let { SetWeight(it.weight, progression.weight.unit ?: WeightUnit.KG) }

/** The history family the chart and the tile show (spec rev 26 §9.4): a Counter's mode; Timer only reads as REPS. */
val Entry.historyMode: ProgressMode
    get() = if (type == EntryType.WORKOUT) progression.mode else ProgressMode.REPS

/** The next workout's reps (EntryRow.reps, plan Spec note 60): the total in Reps mode and for Timer only; sets × reps per set in a weight mode. */
fun Entry.nextWorkoutReps(): Int = if (load() != null) setReps().sum() else counter.total
