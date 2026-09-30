package com.mitenko.hiitcounter.domain

import com.mitenko.hiitcounter.domain.model.CueConfig

/**
 * Spec R4 §5 lifecycle, amended by revision 7 (cue toggles): TimerService holds a speaker only
 * while a run's live cues have the voice on and the run is preparing or running. DONE and IDLE
 * shut it down (the Finished cue says nothing). Cues are live, so toggling Voice mid-run creates
 * or tears down the speaker without any status change.
 */
object VoicePolicy {
    fun speakerWanted(status: RunStatus, cues: CueConfig?): Boolean =
        (status == RunStatus.PREPARING || status == RunStatus.RUNNING) && cues?.voice == true
}
