package com.openclaw.dashboard

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openclaw.dashboard.data.repository.ThemeMode
import com.openclaw.dashboard.presentation.MainViewModel
import com.openclaw.dashboard.presentation.OpenClawDashboardApp
import com.openclaw.dashboard.presentation.theme.OpenClawTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            val mainViewModel: MainViewModel = viewModel()
            val themeMode by mainViewModel.themeMode.collectAsState()
            val useDynamicColor by mainViewModel.useDynamicColor.collectAsState()
            
            val isDarkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            
            OpenClawTheme(
                darkTheme = isDarkTheme,
                dynamicColor = useDynamicColor
            ) {
                OpenClawDashboardApp(mainViewModel = mainViewModel)
            }
        }
    }
}

