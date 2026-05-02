package com.jadeai.solvertracker.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jadeai.solvertracker.ui.coffee.CoffeeScreen
import com.jadeai.solvertracker.ui.detail.TaskDetailScreen
import com.jadeai.solvertracker.ui.history.HistoryScreen
import com.jadeai.solvertracker.ui.home.HomeScreen
import com.jadeai.solvertracker.ui.settings.SettingsScreen
import com.jadeai.solvertracker.ui.stats.StatsScreen

object Routes {
    const val HOME = "home"
    const val HISTORY = "history"
    const val TASK_DETAIL = "task_detail/{taskId}"
    const val STATS = "stats"
    const val COFFEE = "coffee"
    const val SETTINGS = "settings"

    fun taskDetail(taskId: Long) = "task_detail/$taskId"
}

@Composable
fun SolverTrackerNavGraph(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onNavigateHistory = { navController.navigate(Routes.HISTORY) },
                onNavigateStats = { navController.navigate(Routes.STATS) },
                onNavigateCoffee = { navController.navigate(Routes.COFFEE) },
                onTaskCreated = { taskId -> navController.navigate(Routes.taskDetail(taskId)) }
            )
        }

        composable(Routes.HISTORY) {
            HistoryScreen(
                onNavigateBack = { navController.popBackStack() },
                onTaskClick = { taskId -> navController.navigate(Routes.taskDetail(taskId)) }
            )
        }

        composable(
            route = Routes.TASK_DETAIL,
            arguments = listOf(navArgument("taskId") { type = NavType.LongType })
        ) { _ ->
            TaskDetailScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.STATS) {
            StatsScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Routes.COFFEE) {
            CoffeeScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
