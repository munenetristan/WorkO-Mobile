package com.towmech.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.towmech.app.navigation.Routes
import com.towmech.app.realtime.JobChatController

data class ProviderBottomNavItem(
    val label: String,
    val route: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@Composable
fun ProviderBottomNav(navController: NavController) {

    val darkBlue = Color(0xFF0033A0)

    // ✅ Global unread (sum of all jobs)
    val totalUnread by JobChatController.observeTotalUnread().collectAsState(initial = 0)

    // ✅ REMOVE "Jobs" to create space (as requested)
    val items = listOf(
        ProviderBottomNavItem("Home", Routes.PROVIDER_HOME_TAB, Icons.Default.Home),
        ProviderBottomNavItem("Requests", Routes.PROVIDER_INCOMING_REQUEST_TAB, Icons.Default.Notifications),
        ProviderBottomNavItem("Active", Routes.PROVIDER_ACTIVE_JOB_TAB, Icons.Default.Work),
        ProviderBottomNavItem("History", Routes.PROVIDER_JOB_HISTORY_TAB, Icons.Default.History),
        ProviderBottomNavItem("Profile", Routes.PROVIDER_PROFILE_TAB, Icons.Default.Person)
    )

    val navBackStackEntry = navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry.value?.destination?.route

    NavigationBar(
        containerColor = Color.White,
        contentColor = darkBlue
    ) {
        items.forEach { item ->

            val showBadge =
                totalUnread > 0 && (item.route == Routes.PROVIDER_INCOMING_REQUEST_TAB)

            NavigationBarItem(
                selected = currentRoute == item.route,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(Routes.PROVIDER_MAIN) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                label = { Text(item.label, color = darkBlue) },
                icon = {
                    if (showBadge) {
                        BadgedBox(
                            badge = {
                                // ✅ Red dot (or you can show a number)
                                Badge(
                                    containerColor = Color.Red,
                                    contentColor = Color.White
                                ) {
                                    // If you want a number instead of dot:
                                    // Text(if (totalUnread > 99) "99+" else totalUnread.toString())
                                }
                            }
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = if (currentRoute == item.route) darkBlue else Color.Gray
                            )
                        }
                    } else {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = if (currentRoute == item.route) darkBlue else Color.Gray
                        )
                    }
                }
            )
        }
    }
}