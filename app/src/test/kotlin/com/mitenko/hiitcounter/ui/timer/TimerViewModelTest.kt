package com.mitenko.hiitcounter.ui.timer

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.mitenko.hiitcounter.data.AppPreferences
import com.mitenko.hiitcounter.domain.RunStatus
import com.mitenko.hiitcounter.domain.TimerController
import com.mitenko.hiitcounter.domain.WorkoutSnapshot
import com.mitenko.hiitcounter.domain.model.CueConfig
import com.mitenko.hiitcounter.domain.model.TimingConfig
import com.mitenko.hiitcounter.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class TimerViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()

    private fun TestScope.preferences() = AppPreferences(
        PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(tmp.root, "app.preferences_pb") }),
    )

    @Test
    fun `toggle pause, finish and leave done`() = runTest {
        val controller = TimerController(backgroundScope) { testScheduler.currentTime }
        val vm = TimerViewModel(controller, preferences())
        backgroundScope.launch { vm.uiState.collect {} }
        controller.prepare(WorkoutSnapshot(1L, "Burpees", TimingConfig(prepareSec = 0, sets = 1, workSec = 2, restSec = 0), CueConfig()))
        controller.onServiceStarted()
        controller.start(listOf(5))
        runCurrent()
        assertEquals(5, vm.uiState.value?.centerNumber)

        vm.togglePause()
        assertTrue(vm.uiState.value!!.paused)
        vm.togglePause()
        assertFalse(vm.uiState.value!!.paused)

        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(RunStatus.DONE, vm.status.value)
        vm.leaveDone()
        assertEquals(RunStatus.IDLE, vm.status.value)
    }

    @Test
    fun `stop returns to idle`() = runTest {
        val controller = TimerController(backgroundScope) { testScheduler.currentTime }
        val vm = TimerViewModel(controller, preferences())
        controller.prepare(WorkoutSnapshot(1L, "Burpees", TimingConfig(), CueConfig()))
        controller.onServiceStarted()
        controller.start(List(8) { 8 })
        runCurrent()
        vm.stop()
        assertEquals(RunStatus.IDLE, vm.status.value)
    }

    @Test
    fun `ui state shows the snapshot's entry name`() = runTest {
        val controller = TimerController(backgroundScope) { testScheduler.currentTime }
        val vm = TimerViewModel(controller, preferences())
        backgroundScope.launch { vm.uiState.collect {} }
        controller.prepare(WorkoutSnapshot(7L, "Kettlebell Lunges", TimingConfig(), CueConfig()))
        controller.onServiceStarted()
        controller.start(List(8) { 8 })
        runCurrent()
        assertEquals("Kettlebell Lunges", vm.uiState.value?.entryName)
    }

    @Test
    fun `notification permission is asked once`() = runTest {
        val prefs = preferences()
        val vm = TimerViewModel(TimerController(backgroundScope) { testScheduler.currentTime }, prefs)
        assertFalse(vm.notificationPermissionAsked.first())
        vm.onNotificationPermissionAsked()
        runCurrent()
        assertTrue(prefs.notificationPermissionAsked.first())
    }
}
