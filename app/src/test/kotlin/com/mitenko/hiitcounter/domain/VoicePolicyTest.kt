package com.mitenko.hiitcounter.domain

import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.domain.model.TimingConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicePolicyTest {
    @Test
    fun `a speaker is wanted only while a voice run prepares or runs`() {
        val voice = WorkoutSnapshot(1L, "Burpees", TimingConfig(), CueConfig(voice = true))
        val silent = voice.copy(cues = CueConfig())
        assertTrue(VoicePolicy.speakerWanted(RunStatus.PREPARING, voice))
        assertTrue(VoicePolicy.speakerWanted(RunStatus.RUNNING, voice))
        assertFalse(VoicePolicy.speakerWanted(RunStatus.DONE, voice))
        assertFalse(VoicePolicy.speakerWanted(RunStatus.IDLE, voice))
        assertFalse(VoicePolicy.speakerWanted(RunStatus.RUNNING, silent))
        assertFalse(VoicePolicy.speakerWanted(RunStatus.PREPARING, null))
    }
}
