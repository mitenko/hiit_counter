package com.mitenko.hiitcounter.testutil

import com.mitenko.hiitcounter.data.SettingsRepository
import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.domain.model.ProgressionConfig
import com.mitenko.hiitcounter.domain.model.TimingConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSettingsRepository(
    timing: TimingConfig = TimingConfig(),
    progression: ProgressionConfig = ProgressionConfig(),
    cues: CueConfig = CueConfig(),
    asked: Boolean = false,
) : SettingsRepository {
    val timingFlow = MutableStateFlow(timing)
    val progressionFlow = MutableStateFlow(progression)
    val cuesFlow = MutableStateFlow(cues)
    val askedFlow = MutableStateFlow(asked)

    override val timing: Flow<TimingConfig> = timingFlow
    override val progression: Flow<ProgressionConfig> = progressionFlow
    override val cues: Flow<CueConfig> = cuesFlow
    override val notificationPermissionAsked: Flow<Boolean> = askedFlow

    override suspend fun setTiming(config: TimingConfig) { timingFlow.value = config }
    override suspend fun setProgression(config: ProgressionConfig) { progressionFlow.value = config }
    override suspend fun setCues(config: CueConfig) { cuesFlow.value = config }
    override suspend fun markNotificationPermissionAsked() { askedFlow.value = true }
}
