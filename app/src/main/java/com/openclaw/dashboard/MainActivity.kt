package com.openclaw.dashboard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openclaw.dashboard.data.repository.ThemeMode
import com.openclaw.dashboard.presentation.MainViewModel
import com.openclaw.dashboard.presentation.OpenClawDashboardApp
import com.openclaw.dashboard.presentation.theme.OpenClawTheme

class MainActivity : AppCompatActivity() {
    
    private var mainViewModel: MainViewModel? = null
    
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> /* Permission result handled by system */ }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        setContent {
            val vm: MainViewModel = viewModel()
            mainViewModel = vm
            val themeMode by vm.themeMode.collectAsState()
            val useDynamicColor by vm.useDynamicColor.collectAsState()
            
            val isDarkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            
            OpenClawTheme(
                darkTheme = isDarkTheme,
                dynamicColor = useDynamicColor
            ) {
                OpenClawDashboardApp(mainViewModel = vm)
            }
        }
    }
    
    override fun onStart() {
        super.onStart()
        mainViewModel?.setAppInForeground(true)
    }
    
    override fun onStop() {
        super.onStop()
        mainViewModel?.setAppInForeground(false)
    }
}
