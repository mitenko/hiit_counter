package com.mitenko.repkit.ui.common

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.repkit.monetisation.ProEvent
import com.mitenko.repkit.ui.theme.HiitTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ProMessageHostTest {
    @get:Rule val compose = createComposeRule()

    private val events = MutableSharedFlow<ProEvent>(extraBufferCapacity = 8)

    private fun show() {
        compose.setContent { HiitTheme { ProMessageHost(events) } }
        compose.waitForIdle()
    }

    private fun emit(event: ProEvent) {
        compose.runOnIdle { check(events.tryEmit(event)) }
        compose.waitForIdle()
    }

    @Test fun `a billing failure shows as a snackbar`() {
        show()
        emit(ProEvent.BILLING_UNAVAILABLE)
        compose.onNodeWithText("Couldn't reach Google Play. Try again later.").assertExists()
    }

    @Test fun `restore outcomes show their text`() {
        show()
        emit(ProEvent.RESTORED)
        compose.onNodeWithText("Pro restored").assertExists()
        emit(ProEvent.NOTHING_TO_RESTORE)
        compose.onNodeWithText("No purchase found on this Google account").assertExists()
        compose.onNodeWithText("Pro restored").assertDoesNotExist()
    }
}
