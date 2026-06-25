package com.snuabar.counter.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsHandball
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.snuabar.counter.ui.screen.counting.CountingScreen
import com.snuabar.counter.ui.screen.history.HistoryScreen
import com.snuabar.counter.ui.screen.settings.SettingsScreen
import com.snuabar.counter.ui.screen.template.TemplateScreen
import com.snuabar.counter.ui.screen.analysis.AnalysisScreen
import com.snuabar.counter.ui.screen.user.UserScreen

sealed class Screen(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Counting : Screen("counting", "计数", Icons.Default.SportsHandball)
    object History : Screen("history", "历史", Icons.Default.History)
    object Analysis : Screen("analysis", "分析", Icons.Default.Analytics)
    object Template : Screen("template", "模板", Icons.Default.SportsHandball)
    object Settings : Screen("settings", "设置", Icons.Default.Settings)
    object User : Screen("user", "用户", Icons.Default.Settings)
}

val bottomNavItems = listOf(Screen.Counting, Screen.History, Screen.Analysis, Screen.Template, Screen.Settings)

@Composable
fun BottomNavigationBar(navController: NavHostController) {
    NavigationBar {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        bottomNavItems.forEach { screen ->
            NavigationBarItem(
                icon = { Icon(screen.icon, contentDescription = screen.title) },
                label = { Text(screen.title) },
                selected = currentRoute == screen.route,
                onClick = {
                    if (currentRoute != screen.route) {
                        navController.navigate(screen.route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun NavigationGraph(
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Counting.route,
        modifier = modifier
    ) {
        composable(Screen.Counting.route) { CountingScreen() }
        composable(Screen.History.route) { HistoryScreen() }
        composable(Screen.Analysis.route) { AnalysisScreen() }
        composable(Screen.Template.route) { TemplateScreen() }
        composable(Screen.Settings.route) { SettingsScreen(navController) }
        composable(Screen.User.route) { UserScreen() }
    }
}
