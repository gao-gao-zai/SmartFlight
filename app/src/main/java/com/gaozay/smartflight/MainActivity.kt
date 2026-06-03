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
import com.gaozay.smartflight.ui.AccessActions
import com.gaozay.smartflight.ui.AppsActions
import com.gaozay.smartflight.ui.AutomationActions
import com.gaozay.smartflight.ui.DiagnosticsActions
import com.gaozay.smartflight.ui.SettingsActions
import com.gaozay.smartflight.ui.SmartFlightActions
import com.gaozay.smartflight.ui.SmartFlightRoot
import com.gaozay.smartflight.ui.SystemIntentActions
import com.gaozay.smartflight.ui.UpdateActions
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
            val updateUiState = viewModel.updateUiState.collectAsStateWithLifecycle()
            SmartFlightTheme(settings = uiState.value.settings) {
                SmartFlightRoot(
                    state = uiState.value,
                    appsState = appsUiState.value,
                    updateState = updateUiState.value,
                    actions = buildSmartFlightActions(),
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

    private fun buildSmartFlightActions(): SmartFlightActions =
        SmartFlightActions(
            settings = SettingsActions(
                updateSettings = viewModel::updateSettings,
                setNetworkControlMode = viewModel::setNetworkControlMode,
                setPreferredExecutorType = viewModel::setPreferredExecutorType,
                setForegroundMonitorMode = viewModel::setForegroundMonitorMode,
                setMonitorForegroundWhenScreenOff = viewModel::setMonitorForegroundWhenScreenOff,
                setThemeMode = viewModel::setThemeMode,
                setThemePalette = viewModel::setThemePalette,
                setCustomSeedColor = viewModel::setCustomSeedColor,
                setThemeIntensity = viewModel::setThemeIntensity,
                setCornerStyle = viewModel::setCornerStyle,
            ),
            automation = AutomationActions(
                setAutomationEnabled = viewModel::setAutomationEnabled,
                disableAutomation = viewModel::disableAutomation,
            ),
            apps = AppsActions(
                queryChange = viewModel::updateAppQuery,
                filterChange = viewModel::updateAppFilter,
                internetPermissionFilterChange = viewModel::updateAppInternetPermissionFilter,
                typeFilterChange = viewModel::updateAppTypeFilter,
                launcherFilterChange = viewModel::updateAppLauncherFilter,
                clearAdvancedFilters = viewModel::clearAppAdvancedFilters,
                refreshApps = viewModel::refreshInstalledApps,
                setManualOnline = viewModel::setAppManualOnline,
                setManualOffline = viewModel::setAppManualOffline,
                resetToDefault = viewModel::resetAppToDefault,
            ),
            access = AccessActions(
                refreshAccessChecks = viewModel::refreshAccessChecks,
                requestShizukuPermission = ::requestShizukuPermission,
                probeRootAccess = viewModel::probeRootAccess,
                setAdbBootstrapped = viewModel::setAdbBootstrapped,
                autoGrantCompanionPermissions = viewModel::autoGrantCompanionPermissions,
            ),
            diagnostics = DiagnosticsActions(
                probeCurrentNetworkControlState = viewModel::probeCurrentNetworkControlState,
                toggleCurrentNetworkControlState = viewModel::toggleCurrentNetworkControlState,
                simulateScreenOff = viewModel::simulateScreenOff,
                simulateScreenOn = viewModel::simulateScreenOn,
                clearExecutionLogs = viewModel::clearExecutionLogs,
                requestBluetoothPermission = ::requestBluetoothPermission,
            ),
            system = SystemIntentActions(
                openUsageAccessSettings = {
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                },
                openAccessibilitySettings = {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
                openNotificationSettings = {
                    startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    })
                },
                openBatteryOptimizationSettings = {
                    startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    })
                },
            ),
            update = UpdateActions(
                checkForUpdates = viewModel::checkForUpdates,
                dismissUpdatePrompt = viewModel::dismissUpdatePrompt,
                skipUpdateVersion = viewModel::skipUpdateVersion,
                copyUpdateLink = viewModel::copyUpdateLink,
                openUpdateLink = viewModel::openUpdateLink,
            ),
        )

    private fun requestBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    private fun requestShizukuPermission() {
        runCatching {
            Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
        }.onFailure {
            viewModel.refreshAccessChecks()
        }
    }
}
