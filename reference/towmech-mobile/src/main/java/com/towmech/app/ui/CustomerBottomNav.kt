package com.towmech.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.towmech.app.navigation.Routes

data class BottomNavItem(
    val route: String,
    val icon: ImageVector,
    val label: String
)

@Composable
fun CustomerBottomNav(
    navController: NavController,
    unreadCount: Int = 0
) {
    val items = listOf(
        BottomNavItem(Routes.CUSTOMER_HOME_TAB, Icons.Filled.Home, "Home"),
        BottomNavItem(Routes.CUSTOMER_ACTIVE_TAB, Icons.AutoMirrored.Filled.List, "Active"),
        BottomNavItem(Routes.CUSTOMER_HISTORY_TAB, Icons.AutoMirrored.Filled.List, "History"),
        BottomNavItem(Routes.CUSTOMER_SUPPORT_TAB, Icons.Filled.SupportAgent, "Support"),
        BottomNavItem(Routes.CUSTOMER_PROFILE_TAB, Icons.Filled.Person, "Profile")
    )

    val navBackStackEntry = navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry.value?.destination?.route

    NavigationBar {
        items.forEach { item ->
            // ✅ show badge on Active tab (you can change this to Support if you prefer)
            val showBadgeOnThis = (item.route == Routes.CUSTOMER_ACTIVE_TAB) && unreadCount > 0

            NavigationBarItem(
                selected = currentRoute == item.route,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(Routes.CUSTOMER_HOME_TAB) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    BadgedBox(
                        badge = {
                            if (showBadgeOnThis) {
                                Badge(containerColor = Color.Red) {
                                    Text(if (unreadCount > 99) "99+" else unreadCount.toString())
                                }
                            }
                        }
                    ) {
                        Icon(item.icon, contentDescription = item.label)
                    }
                },
                label = { Text(item.label) }
            )
        }
    }
}