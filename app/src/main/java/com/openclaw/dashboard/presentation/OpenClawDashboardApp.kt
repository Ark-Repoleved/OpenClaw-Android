package com.openclaw.dashboard.presentation

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.openclaw.dashboard.presentation.navigation.Screen
import com.openclaw.dashboard.presentation.screen.chat.ChatScreen
import com.openclaw.dashboard.presentation.screen.config.ConfigScreen
import com.openclaw.dashboard.presentation.screen.instances.InstancesScreen
import com.openclaw.dashboard.presentation.screen.overview.OverviewScreen
import com.openclaw.dashboard.presentation.screen.sessions.SessionsScreen
import com.openclaw.dashboard.presentation.screen.setup.SetupScreen

/**
 * Main App Composable
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenClawDashboardApp(
    mainViewModel: MainViewModel = viewModel()
) {
    val navController = rememberNavController()
    val isConfigured by mainViewModel.isConfigured.collectAsState()
    val connectionState by mainViewModel.connectionState.collectAsState()
    
    // Determine start destination
    val startDestination = if (isConfigured) Screen.Overview.route else Screen.Setup.route
    
    Scaffold(
        bottomBar = {
            if (isConfigured) {
                NavigationBar {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentDestination = navBackStackEntry?.destination
                    
                    Screen.bottomNavItems.forEach { screen ->
                        val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                        
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                    contentDescription = screen.title
                                )
                            },
                            label = { Text(screen.title) },
                            selected = selected,
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
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Setup.route) {
                SetupScreen(
                    viewModel = mainViewModel,
                    onConnected = {
                        navController.navigate(Screen.Overview.route) {
                            popUpTo(Screen.Setup.route) { inclusive = true }
                        }
                    }
                )
            }
            
            composable(Screen.Overview.route) {
                OverviewScreen(viewModel = mainViewModel)
            }
            
            composable(Screen.Sessions.route) {
                SessionsScreen(viewModel = mainViewModel)
            }
            
            composable(Screen.Chat.route) {
                ChatScreen(viewModel = mainViewModel)
            }
            
            composable(Screen.Config.route) {
                ConfigScreen(viewModel = mainViewModel)
            }
            
            composable(Screen.Instances.route) {
                InstancesScreen(viewModel = mainViewModel)
            }
        }
    }
}

