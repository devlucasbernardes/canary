package com.canary.devicecare

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.canary.devicecare.ui.dashboard.DashboardScreen
import com.canary.devicecare.ui.notifications.NotificationManagerScreen
import com.canary.devicecare.ui.ramcleaner.RamCleanerScreen
import com.canary.devicecare.ui.storage.StorageAnalyzerScreen
import com.canary.devicecare.ui.tempcleaner.TempCleanerScreen
import com.canary.devicecare.ui.theme.DeviceCareTheme

sealed class Screen(val route: String, val labelRes: Int, val icon: ImageVector) {
    data object Dashboard : Screen("dashboard", R.string.nav_dashboard, Icons.Filled.Dashboard)
    data object RamCleaner : Screen("ram_cleaner", R.string.nav_ram, Icons.Filled.Memory)
    data object TempCleaner : Screen("temp_cleaner", R.string.nav_cache, Icons.Filled.CleaningServices)
    data object Storage : Screen("storage", R.string.nav_storage, Icons.Filled.Storage)
    data object Notifications : Screen("notifications", R.string.nav_notifications, Icons.Filled.Notifications)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DeviceCareTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DeviceCareNavigation()
                }
            }
        }
    }
}

@Composable
fun DeviceCareNavigation() {
    val navController = rememberNavController()
    val screens = listOf(
        Screen.Dashboard,
        Screen.RamCleaner,
        Screen.TempCleaner,
        Screen.Storage,
        Screen.Notifications
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                screens.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = null) },
                        label = { Text(stringResource(screen.labelRes)) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Dashboard.route) { DashboardScreen(navController) }
            composable(Screen.RamCleaner.route) { RamCleanerScreen() }
            composable(Screen.TempCleaner.route) { TempCleanerScreen() }
            composable(Screen.Storage.route) { StorageAnalyzerScreen() }
            composable(Screen.Notifications.route) { NotificationManagerScreen() }
        }
    }
}
