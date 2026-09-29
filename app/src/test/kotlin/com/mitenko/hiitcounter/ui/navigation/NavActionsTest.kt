package com.mitenko.hiitcounter.ui.navigation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitenko.hiitcounter.ui.common.ENTRY_ID_ARG
import com.mitenko.hiitcounter.ui.settings.SettingsPage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class NavActionsTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var nav: NavHostController

    private fun graph() {
        compose.setContent {
            nav = rememberNavController()
            NavHost(nav, startDestination = Routes.ENTRIES) {
                val idArg = listOf(navArgument(ENTRY_ID_ARG) { type = NavType.LongType })
                composable(Routes.ENTRIES) { }
                composable(Routes.ENTRY, arguments = idArg) { }
                composable(Routes.ENTRY_SETTINGS, arguments = idArg) { }
                composable(
                    Routes.SETTINGS_PAGES,
                    arguments = idArg + navArgument(Routes.PAGE_ARG) {
                        type = NavType.IntType
                        defaultValue = 0
                    },
                ) { }
                composable(Routes.TIMER) { }
            }
        }
    }

    @Test
    fun `exiting the timer pops to the run's entry`() {
        graph()
        compose.runOnIdle {
            nav.navigate(Routes.entry(1))
            nav.navigate(Routes.entrySettings(1))
            nav.navigate(Routes.TIMER)
            nav.exitTimer(1L)
        }
        compose.runOnIdle {
            assertEquals(Routes.ENTRY, nav.currentDestination?.route)
            assertEquals(1L, nav.currentBackStackEntry?.arguments?.getLong(ENTRY_ID_ARG))
        }
    }

    @Test
    fun `exiting the timer pops to the list when the run's entry isn't on the back stack`() {
        graph()
        compose.runOnIdle {
            nav.navigate(Routes.entry(2))
            nav.navigate(Routes.TIMER)
            nav.exitTimer(1L)
        }
        compose.runOnIdle { assertEquals(Routes.ENTRIES, nav.currentDestination?.route) }
    }

    @Test
    fun `a created entry opens with only the list beneath it`() {
        graph()
        compose.runOnIdle { nav.openEntryOverList(5) }
        compose.runOnIdle {
            assertEquals(5L, nav.currentBackStackEntry?.arguments?.getLong(ENTRY_ID_ARG))
            nav.popBackStack()
        }
        compose.runOnIdle { assertEquals(Routes.ENTRIES, nav.currentDestination?.route) }
    }

    @Test
    fun `a duplicate opens with only the list beneath it`() {
        graph()
        compose.runOnIdle {
            nav.navigate(Routes.entry(1))
            nav.navigate(Routes.entrySettings(1))
            nav.openEntryOverList(2)
        }
        compose.runOnIdle {
            assertEquals(Routes.ENTRY, nav.currentDestination?.route)
            assertEquals(2L, nav.currentBackStackEntry?.arguments?.getLong(ENTRY_ID_ARG))
            nav.popBackStack()
        }
        compose.runOnIdle { assertEquals(Routes.ENTRIES, nav.currentDestination?.route) }
    }

    @Test
    fun `a settings page route carries its page and defaults to the first`() {
        graph()
        compose.runOnIdle { nav.navigate(Routes.settingsPage(1, SettingsPage.CURRENT)) }
        compose.runOnIdle {
            assertEquals(Routes.SETTINGS_PAGES, nav.currentDestination?.route)
            assertEquals(2, nav.currentBackStackEntry?.arguments?.getInt(Routes.PAGE_ARG))
            nav.navigate("entry/1/settings/pages")
        }
        compose.runOnIdle { assertEquals(0, nav.currentBackStackEntry?.arguments?.getInt(Routes.PAGE_ARG)) }
    }
}
