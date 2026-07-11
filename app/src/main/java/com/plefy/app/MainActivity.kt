package com.plefy.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.plefy.app.feature.importer.LibraryScreen
import com.plefy.app.feature.viewer.ChartScreen
import com.plefy.app.feature.viewer.ViewerScreen
import com.plefy.app.ui.theme.MyApplicationTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * The single activity hosting the app's Navigation-Compose graph.
 *
 * [@AndroidEntryPoint][AndroidEntryPoint] makes this activity a Hilt injection site, which is what
 * lets the composables it hosts obtain `@HiltViewModel`s via `hiltViewModel()`.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ExcelApp()
                }
            }
        }
    }
}

/**
 * The app's navigation host.
 *
 * Two destinations:
 * - `library` (start): the [LibraryScreen] listing imported sheets; picking one navigates to that
 *   sheet's viewer.
 * - `viewer/{tableId}`: the [ViewerScreen] for one table. `tableId` is a [NavType.LongType]
 *   argument that Navigation places into the destination's `SavedStateHandle`, from where the
 *   `ViewerViewModel` reads it (via `hiltViewModel()`).
 */
@Composable
private fun ExcelApp() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "library") {
        composable(route = "library") {
            LibraryScreen(
                onOpenSheet = { tableId -> navController.navigate("viewer/$tableId") },
            )
        }
        composable(
            route = "viewer/{tableId}",
            arguments = listOf(navArgument("tableId") { type = NavType.LongType }),
        ) {
            ViewerScreen(
                onBack = { navController.popBackStack() },
                onOpenChart = { tableId -> navController.navigate("chart/$tableId") },
            )
        }
        composable(
            route = "chart/{tableId}",
            arguments = listOf(navArgument("tableId") { type = NavType.LongType }),
        ) {
            ChartScreen(onBack = { navController.popBackStack() })
        }
    }
}
