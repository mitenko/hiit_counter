package com.mitenko.hiitcounter.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mitenko.hiitcounter.domain.RunStatus
import com.mitenko.hiitcounter.domain.TimerController
import com.mitenko.hiitcounter.ui.home.HomeRoute
import com.mitenko.hiitcounter.ui.settings.CuesSettingsRoute
import com.mitenko.hiitcounter.ui.settings.CurrentStateRoute
import com.mitenko.hiitcounter.ui.settings.ProgressionSettingsRoute
import com.mitenko.hiitcounter.ui.settings.SettingsListScreen
import com.mitenko.hiitcounter.ui.settings.TimingSettingsRoute
import com.mitenko.hiitcounter.ui.timer.TimerRoute

@Composable
fun HiitNavHost(controller: TimerController) {
    val nav = rememberNavController()
    val status by controller.status.collectAsStateWithLifecycle()

    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeRoute(
                onOpenSettings = dropUnlessResumed { nav.navigate(Routes.SETTINGS) { launchSingleTop = true } },
            )
        }
        // Active-run routing relies on TimerRoute's BackHandler blocking back navigation while RUNNING,
        // so onExit here only ever fires once the workout has already stopped.
        composable(Routes.TIMER) { TimerRoute(onExit = { nav.popBackStack(Routes.HOME, inclusive = false) }) }
        composable(Routes.SETTINGS) {
            // dropUnlessResumed only wraps zero-arg lambdas, so each destination gets its own
            // guarded lambda and onOpen dispatches to the one matching the tapped route.
            val openTiming = dropUnlessResumed { nav.navigate(Routes.SETTINGS_TIMING) { launchSingleTop = true } }
            val openProgression =
                dropUnlessResumed { nav.navigate(Routes.SETTINGS_PROGRESSION) { launchSingleTop = true } }
            val openCurrent = dropUnlessResumed { nav.navigate(Routes.SETTINGS_CURRENT) { launchSingleTop = true } }
            val openCues = dropUnlessResumed { nav.navigate(Routes.SETTINGS_CUES) { launchSingleTop = true } }
            SettingsListScreen(
                onBack = dropUnlessResumed { nav.popBackStack() },
                onOpen = { route ->
                    when (route) {
                        Routes.SETTINGS_TIMING -> openTiming()
                        Routes.SETTINGS_PROGRESSION -> openProgression()
                        Routes.SETTINGS_CURRENT -> openCurrent()
                        Routes.SETTINGS_CUES -> openCues()
                        else -> Unit
                    }
                },
            )
        }
        composable(Routes.SETTINGS_TIMING) {
            TimingSettingsRoute(onBack = dropUnlessResumed { nav.popBackStack() })
        }
        composable(Routes.SETTINGS_PROGRESSION) {
            ProgressionSettingsRoute(onBack = dropUnlessResumed { nav.popBackStack() })
        }
        composable(Routes.SETTINGS_CURRENT) {
            CurrentStateRoute(onBack = dropUnlessResumed { nav.popBackStack() })
        }
        composable(Routes.SETTINGS_CUES) {
            CuesSettingsRoute(onBack = dropUnlessResumed { nav.popBackStack() })
        }
    }

    LaunchedEffect(status) {
        if (status == RunStatus.RUNNING && nav.currentDestination?.route != Routes.TIMER) {
            nav.navigate(Routes.TIMER) { launchSingleTop = true }
        }
    }
}
