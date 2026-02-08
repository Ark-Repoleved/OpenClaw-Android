package com.openclaw.dashboard.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Navigation destinations for the app
 */
sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Setup : Screen(
        route = "setup",
        title = "設定",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )
    
    data object Overview : Screen(
        route = "overview",
        title = "總覽",
        selectedIcon = Icons.Filled.Dashboard,
        unselectedIcon = Icons.Outlined.Dashboard
    )
    
    data object Sessions : Screen(
        route = "sessions",
        title = "Sessions",
        selectedIcon = Icons.Filled.List,
        unselectedIcon = Icons.Outlined.List
    )
    
    data object Chat : Screen(
        route = "chat",
        title = "聊天",
        selectedIcon = Icons.Filled.Chat,
        unselectedIcon = Icons.Outlined.Chat
    )
    
    data object Instances : Screen(
        route = "instances",
        title = "裝置",
        selectedIcon = Icons.Filled.Devices,
        unselectedIcon = Icons.Outlined.Devices
    )
    
    companion object {
        val bottomNavItems = listOf(Overview, Sessions, Chat, Instances)
    }
}
