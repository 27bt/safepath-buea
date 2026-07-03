package com.safepathbuea.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.safepathbuea.app.SafePathViewModel
import com.safepathbuea.app.ui.screens.HomeScreen
import com.safepathbuea.app.ui.screens.NearbyHazardsScreen
import com.safepathbuea.app.ui.screens.ReportHazardScreen
import com.safepathbuea.app.ui.screens.SettingsScreen

/** Every non-Home destination is one hop from Home and always returns to it;
 * there is no deeper back stack to get lost in. */
@Composable
fun SafePathNavHost(viewModel: SafePathViewModel, onCallForHelp: () -> Unit) {
    val navController: NavHostController = rememberNavController()

    fun returnHome() {
        navController.navigate(Routes.HOME) {
            popUpTo(Routes.HOME) { inclusive = true }
        }
    }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                viewModel = viewModel,
                onReportHazard = { navController.navigate(Routes.REPORT_HAZARD) },
                onNearbyHazards = { navController.navigate(Routes.NEARBY_HAZARDS) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
                onCallForHelp = onCallForHelp,
            )
        }
        composable(Routes.REPORT_HAZARD) {
            ReportHazardScreen(viewModel = viewModel, onDone = { returnHome() })
        }
        composable(Routes.NEARBY_HAZARDS) {
            NearbyHazardsScreen(viewModel = viewModel, onDone = { returnHome() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(viewModel = viewModel, onDone = { returnHome() })
        }
    }
}
