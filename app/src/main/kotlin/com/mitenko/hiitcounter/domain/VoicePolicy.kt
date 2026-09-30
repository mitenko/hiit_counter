package com.mitenko.hiitcounter.domain

/**
 * Spec R4 §5 lifecycle: TimerService holds a speaker only while a run whose frozen snapshot has
 * the voice on is preparing or running. DONE and IDLE shut it down (the Finished cue says nothing).
 */
object VoicePolicy {
    fun speakerWanted(status: RunStatus, snapshot: WorkoutSnapshot?): Boolean =
        (status == RunStatus.PREPARING || status == RunStatus.RUNNING) && snapshot?.cues?.voice == true
}
