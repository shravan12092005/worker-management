package com.workermanagement.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Attendance : Screen("attendance", "Attendance", Icons.Default.CalendarToday)
    data object Workers    : Screen("workers",    "Workers",    Icons.Default.Groups)
    data object Sites      : Screen("sites",      "Sites",      Icons.Default.LocationOn)
    data object Roles      : Screen("roles",      "Roles",      Icons.Default.WorkOutline)

    // Sub-routes (no bottom nav item)
    data object Home        : Screen("home",                 "Home",         Icons.Default.Home)
    data object AddWorker   : Screen("workers/add",          "Add Worker",   Icons.Default.Groups)
    data object EditWorker  : Screen("workers/{workerId}",   "Worker Detail",Icons.Default.Groups)
}

val bottomNavItems = listOf(Screen.Attendance, Screen.Workers, Screen.Sites, Screen.Roles)

@Composable
fun BottomNavBar(navController: NavController) {
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    NavigationBar {
        bottomNavItems.forEach { screen ->
            NavigationBarItem(
                selected = currentRoute == screen.route,
                onClick = {
                    if (currentRoute != screen.route) {
                        navController.navigate(screen.route) {
                            popUpTo(Screen.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = { Icon(screen.icon, contentDescription = screen.label) },
                label = { Text(screen.label) }
            )
        }
    }
}
