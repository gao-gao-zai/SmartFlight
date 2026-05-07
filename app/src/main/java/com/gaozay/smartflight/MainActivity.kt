package com.gaozay.smartflight

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gaozay.smartflight.ui.SmartFlightRoot
import com.gaozay.smartflight.ui.theme.SmartFlightTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = hiltViewModel()
            val uiState = viewModel.uiState.collectAsStateWithLifecycle()
            val appsUiState = viewModel.appsUiState.collectAsStateWithLifecycle()
            val (darkMode, setDarkMode) = rememberSaveable { mutableStateOf(false) }
            SmartFlightTheme(darkTheme = darkMode) {
                SmartFlightRoot(
                    state = uiState.value,
                    appsState = appsUiState.value,
                    darkMode = darkMode,
                    onToggleDarkMode = { setDarkMode(!darkMode) },
                    onAppQueryChange = viewModel::updateAppQuery,
                    onAppFilterChange = viewModel::updateAppFilter,
                    onRefreshApps = viewModel::refreshInstalledApps,
                    onSetAppListStatus = viewModel::setAppListStatus,
                    onRefreshAccessChecks = viewModel::refreshAccessChecks,
                    onOpenUsageAccessSettings = {
                        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    },
                    onOpenNotificationSettings = {
                        startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                        })
                    },
                    onOpenBatteryOptimizationSettings = {
                        startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:$packageName")
                        })
                    },
                )
            }
        }
    }
}
