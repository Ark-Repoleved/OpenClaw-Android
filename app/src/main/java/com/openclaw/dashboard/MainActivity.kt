package com.openclaw.dashboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.openclaw.dashboard.presentation.OpenClawDashboardApp
import com.openclaw.dashboard.presentation.theme.OpenClawTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            OpenClawTheme {
                OpenClawDashboardApp()
            }
        }
    }
}
