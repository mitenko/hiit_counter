package com.mitenko.repkit.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class HiitThemeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `darkTheme false provides the light background colour`() {
        var background by mutableStateOf(Color.Unspecified)
        compose.setContent {
            HiitTheme(darkTheme = false) { background = MaterialTheme.colorScheme.background }
        }
        assertEquals(Color(0xFFF7F9F8), background)
    }

    @Test
    fun `darkTheme true provides the dark background colour`() {
        var background by mutableStateOf(Color.Unspecified)
        compose.setContent {
            HiitTheme(darkTheme = true) { background = MaterialTheme.colorScheme.background }
        }
        assertEquals(Color(0xFF12181B), background)
    }
}
