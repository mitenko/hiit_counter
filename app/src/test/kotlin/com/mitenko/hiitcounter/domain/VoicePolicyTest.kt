package com.mitenko.hiitcounter.domain

import com.mitenko.hiitcounter.domain.model.CueConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicePolicyTest {
    @Test
    fun `a speaker is wanted only while the live cues have voice on and the run prepares or runs`() {
        val voice = CueConfig(voice = true)
        val silent = CueConfig()
        assertTrue(VoicePolicy.speakerWanted(RunStatus.PREPARING, voice))
        assertTrue(VoicePolicy.speakerWanted(RunStatus.RUNNING, voice))
        assertFalse(VoicePolicy.speakerWanted(RunStatus.DONE, voice))
        assertFalse(VoicePolicy.speakerWanted(RunStatus.IDLE, voice))
        assertFalse(VoicePolicy.speakerWanted(RunStatus.RUNNING, silent))
        assertFalse(VoicePolicy.speakerWanted(RunStatus.PREPARING, null))
    }

    @Test
    fun `a live toggle changes what's wanted mid-run without changing status`() {
        assertFalse(VoicePolicy.speakerWanted(RunStatus.RUNNING, CueConfig(voice = false)))
        assertTrue(VoicePolicy.speakerWanted(RunStatus.RUNNING, CueConfig(voice = true)))
    }
}
