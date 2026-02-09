package com.openclaw.dashboard.presentation.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.openclaw.dashboard.R

/**
 * Navigation destinations for the app
 */
sealed class Screen(
    val route: String,
    @StringRes val titleResId: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Setup : Screen(
        route = "setup",
        titleResId = R.string.nav_setup,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )
    
    data object Overview : Screen(
        route = "overview",
        titleResId = R.string.nav_overview,
        selectedIcon = Icons.Filled.Dashboard,
        unselectedIcon = Icons.Outlined.Dashboard
    )
    
    data object Sessions : Screen(
        route = "sessions",
        titleResId = R.string.nav_sessions,
        selectedIcon = Icons.Filled.List,
        unselectedIcon = Icons.Outlined.List
    )
    
    data object Chat : Screen(
        route = "chat",
        titleResId = R.string.nav_chat,
        selectedIcon = Icons.Filled.Chat,
        unselectedIcon = Icons.Outlined.Chat
    )
    
    data object Config : Screen(
        route = "config",
        titleResId = R.string.nav_config,
        selectedIcon = Icons.Filled.Code,
        unselectedIcon = Icons.Outlined.Code
    )
    
    data object AgentFiles : Screen(
        route = "agent_files",
        titleResId = R.string.nav_agent_files,
        selectedIcon = Icons.Filled.Description,
        unselectedIcon = Icons.Outlined.Description
    )
    
    companion object {
        val bottomNavItems = listOf(Overview, Sessions, Chat, Config, AgentFiles)
    }
}
