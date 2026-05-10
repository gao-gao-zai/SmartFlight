package com.gaozay.smartflight

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gaozay.smartflight.ui.SmartFlightRoot
import com.gaozay.smartflight.ui.theme.SmartFlightTheme
import dagger.hilt.android.AndroidEntryPoint
import rikka.shizuku.Shizuku

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private companion object {
        const val SHIZUKU_REQUEST_CODE = 1001
    }

    private val viewModel: MainViewModel by viewModels()
    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
        if (requestCode == SHIZUKU_REQUEST_CODE) {
            viewModel.refreshAccessChecks()
        }
    }
    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.refreshAccessChecks()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        enableEdgeToEdge()
        setContent {
            val uiState = viewModel.uiState.collectAsStateWithLifecycle()
            val appsUiState = viewModel.appsUiState.collectAsStateWithLifecycle()
            SmartFlightTheme(settings = uiState.value.settings) {
                SmartFlightRoot(
                    state = uiState.value,
                    appsState = appsUiState.value,
                    onUpdateSettings = viewModel::updateSettings,
                    onSetNetworkControlMode = viewModel::setNetworkControlMode,
                    onSetPreferredExecutorType = viewModel::setPreferredExecutorType,
                    onSetThemeMode = viewModel::setThemeMode,
                    onSetThemePalette = viewModel::setThemePalette,
                    onSetCustomSeedColor = viewModel::setCustomSeedColor,
                    onSetThemeIntensity = viewModel::setThemeIntensity,
                    onSetCornerStyle = viewModel::setCornerStyle,
                    onSetAutomationEnabled = viewModel::setAutomationEnabled,
                    onSetMonitorForegroundWhenScreenOff = viewModel::setMonitorForegroundWhenScreenOff,
                    onAppQueryChange = viewModel::updateAppQuery,
                    onAppFilterChange = viewModel::updateAppFilter,
                    onAppInternetPermissionFilterChange = viewModel::updateAppInternetPermissionFilter,
                    onAppTypeFilterChange = viewModel::updateAppTypeFilter,
                    onAppLauncherFilterChange = viewModel::updateAppLauncherFilter,
                    onClearAppAdvancedFilters = viewModel::clearAppAdvancedFilters,
                    onRefreshApps = viewModel::refreshInstalledApps,
                    onSetAppManualOnline = viewModel::setAppManualOnline,
                    onSetAppManualOffline = viewModel::setAppManualOffline,
                    onResetAppToDefault = viewModel::resetAppToDefault,
                    onRefreshAccessChecks = viewModel::refreshAccessChecks,
                    onProbeCurrentNetworkControlState = viewModel::probeCurrentNetworkControlState,
                    onToggleCurrentNetworkControlState = viewModel::toggleCurrentNetworkControlState,
                    onSimulateScreenOff = viewModel::simulateScreenOff,
                    onSimulateScreenOn = viewModel::simulateScreenOn,
                    onClearExecutionLogs = viewModel::clearExecutionLogs,
                    onRequestBluetoothPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                            ContextCompat.checkSelfPermission(
                                this,
                                Manifest.permission.BLUETOOTH_CONNECT,
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                        }
                    },
                    onRequestShizukuPermission = {
                        runCatching {
                            Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
                        }.onFailure {
                            viewModel.refreshAccessChecks()
                        }
                    },
                    onProbeRootAccess = viewModel::probeRootAccess,
                    onSetAdbBootstrapped = viewModel::setAdbBootstrapped,
                    onAutoGrantCompanionPermissions = viewModel::autoGrantCompanionPermissions,
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

    override fun onResume() {
        super.onResume()
        viewModel.refreshAccessChecks()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        super.onDestroy()
    }
}
