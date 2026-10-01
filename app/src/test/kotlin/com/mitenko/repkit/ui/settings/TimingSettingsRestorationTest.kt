package com.mitenko.repkit.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.repkit.testutil.FakeEntryRepository
import com.mitenko.repkit.testutil.testEntry
import com.mitenko.repkit.ui.common.ENTRY_ID_ARG
import com.mitenko.repkit.ui.theme.HiitTheme
import kotlinx.coroutines.MainScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TimingSettingsRestorationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val repo = FakeEntryRepository(listOf(testEntry(1)))
    private val factory = viewModelFactory {
        initializer { TimingSettingsViewModel(SavedStateHandle(mapOf(ENTRY_ID_ARG to 1L)), repo, MainScope()) }
    }

    @Test
    fun `the typed draft survives recreation of the view model's owner`() {
        lateinit var before: TimingSettingsViewModel
        compose.setContent {
            HiitTheme {
                val vm: TimingSettingsViewModel = viewModel(factory = factory)
                before = vm
                TimingPage(vm)
            }
        }
        compose.onNodeWithContentDescription("Increase SETS").performScrollTo().performClick()
        compose.onNodeWithTag("value_SETS").assertTextEquals("9")
        // Recreate the activity (the ViewModelStoreOwner). The recreated activity has no content of
        // its own, so the draft is checked on the ViewModel it gets back from its store.
        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.onActivity { activity ->
            val after = ViewModelProvider(activity, factory)[TimingSettingsViewModel::class.java]
            assertSame(before, after)
            assertEquals(9, after.draft.value?.sets)
        }
    }

    @Test
    fun `an open edit dialog keeps its text across recreation`() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { HiitTheme { TimingPage(viewModel(factory = factory)) } }
        compose.onNodeWithTag("value_PREPARE").performScrollTo().performClick()
        compose.onNodeWithTag("edit_field").performTextReplacement("1:3")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("edit_field").assertTextContains("1:3")
        compose.onNodeWithTag("edit_ok").assertIsNotEnabled()
    }
}
