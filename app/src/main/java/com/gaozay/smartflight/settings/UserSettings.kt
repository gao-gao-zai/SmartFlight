package com.gaozay.smartflight.settings

import com.gaozay.smartflight.domain.model.CornerStyle
import com.gaozay.smartflight.domain.model.ExecutorType
import com.gaozay.smartflight.domain.model.NetworkControlMode
import com.gaozay.smartflight.domain.model.ThemeIntensity
import com.gaozay.smartflight.domain.model.ThemeMode
import com.gaozay.smartflight.domain.model.ThemePalette

enum class AutomationDisableMode(
    val label: String,
    val shortLabel: String,
    val tileLabel: String,
    val durationMillis: Long? = null,
) {
    None("未禁用", "运行中", "启用"),
    UntilAppSwitch("禁用直到应用切换", "直到应用切换", "应用切换"),
    UntilScreenOff("禁用直到息屏", "直到息屏", "息屏"),
    For1Minute("禁用 1 分钟", "1 分钟", "1m", 60_000L),
    For5Minutes("禁用 5 分钟", "5 分钟", "5m", 5 * 60_000L),
    For10Minutes("禁用 10 分钟", "10 分钟", "10m", 10 * 60_000L),
    For20Minutes("禁用 20 分钟", "20 分钟", "20m", 20 * 60_000L),
    For30Minutes("禁用 30 分钟", "30 分钟", "30m", 30 * 60_000L),
    Permanent("永久禁用", "永久", "永久"),
}

data class UserSettings(
    val automationEnabled: Boolean = false,
    val pauseAutomationOnExternalNetworkChange: Boolean = true,
    val temporaryDisableMode: AutomationDisableMode = AutomationDisableMode.None,
    val temporaryDisableStartedAtMillis: Long = 0L,
    val temporaryDisableUntilMillis: Long? = null,
    val temporaryDisableForegroundPackageName: String? = null,
    val networkControlMode: NetworkControlMode = NetworkControlMode.AirplaneMode,
    val preferredExecutorType: ExecutorType = ExecutorType.Auto,
    val screenOffDisconnectEnabled: Boolean = true,
    val screenOffDelaySeconds: Int = 60,
    val appExitDisconnectEnabled: Boolean = true,
    val appExitDelaySeconds: Int = 30,
    val reconnectOnTargetAppLaunch: Boolean = true,
    val monitorForegroundWhenScreenOff: Boolean = false,
    val skipReconnectOnWifi: Boolean = true,
    val skipDisconnectOnWifi: Boolean = true,
    val preserveWifiState: Boolean = true,
    val preserveBluetoothState: Boolean = true,
    val disableScreenOnReconnect: Boolean = true,
    val disableUnlockReconnect: Boolean = true,
    val showReconnectPrompt: Boolean = true,
    val reconnectPromptText: String = "SmartFlight 已恢复联网",
    val showDisconnectPrompt: Boolean = true,
    val disconnectPromptText: String = "SmartFlight 已断网",
    val themeMode: ThemeMode = ThemeMode.System,
    val themePalette: ThemePalette = ThemePalette.LogoOriginal,
    val customSeedColorArgb: Int = ThemePalette.LogoOriginal.seedColorArgb,
    val themeIntensity: ThemeIntensity = ThemeIntensity.Standard,
    val cornerStyle: CornerStyle = CornerStyle.Standard,
)

fun UserSettings.isTemporaryDisableActive(nowMillis: Long = System.currentTimeMillis()): Boolean {
    if (!automationEnabled) {
        return false
    }
    if (temporaryDisableMode == AutomationDisableMode.None ||
        temporaryDisableMode == AutomationDisableMode.Permanent
    ) {
        return false
    }
    return temporaryDisableUntilMillis?.let { it > nowMillis } ?: true
}

fun UserSettings.shouldClearExpiredTemporaryDisable(nowMillis: Long = System.currentTimeMillis()): Boolean =
    automationEnabled &&
        temporaryDisableMode != AutomationDisableMode.None &&
        temporaryDisableMode != AutomationDisableMode.Permanent &&
        temporaryDisableUntilMillis != null &&
        temporaryDisableUntilMillis <= nowMillis

fun UserSettings.isAutomationEffectivelyEnabled(nowMillis: Long = System.currentTimeMillis()): Boolean =
    automationEnabled && !isTemporaryDisableActive(nowMillis)

fun UserSettings.temporaryDisableSummary(nowMillis: Long = System.currentTimeMillis()): String? {
    if (!isTemporaryDisableActive(nowMillis)) {
        return null
    }
    return when (temporaryDisableMode) {
        AutomationDisableMode.UntilAppSwitch -> "已临时禁用，切换应用后恢复自动化"
        AutomationDisableMode.UntilScreenOff -> "已临时禁用，息屏后恢复自动化"
        AutomationDisableMode.For1Minute,
        AutomationDisableMode.For5Minutes,
        AutomationDisableMode.For10Minutes,
        AutomationDisableMode.For20Minutes,
        AutomationDisableMode.For30Minutes -> {
            val remainingSeconds = temporaryDisableUntilMillis?.let {
                ((it - nowMillis).coerceAtLeast(0L) + 999L) / 1000L
            }
            if (remainingSeconds != null) {
                "已临时禁用，剩余 ${remainingSeconds} 秒"
            } else {
                "已临时禁用，稍后恢复自动化"
            }
        }
        AutomationDisableMode.None,
        AutomationDisableMode.Permanent -> null
    }
}

fun UserSettings.withAutomationEnabled(): UserSettings = copy(
    automationEnabled = true,
    temporaryDisableMode = AutomationDisableMode.None,
    temporaryDisableStartedAtMillis = 0L,
    temporaryDisableUntilMillis = null,
    temporaryDisableForegroundPackageName = null,
)

fun UserSettings.withTemporaryDisableCleared(): UserSettings = copy(
    temporaryDisableMode = AutomationDisableMode.None,
    temporaryDisableStartedAtMillis = 0L,
    temporaryDisableUntilMillis = null,
    temporaryDisableForegroundPackageName = null,
)

fun UserSettings.withAutomationDisabled(
    mode: AutomationDisableMode,
    nowMillis: Long = System.currentTimeMillis(),
    foregroundPackageName: String? = null,
): UserSettings {
    if (mode == AutomationDisableMode.Permanent) {
        return copy(
            automationEnabled = false,
            temporaryDisableMode = AutomationDisableMode.None,
            temporaryDisableStartedAtMillis = 0L,
            temporaryDisableUntilMillis = null,
            temporaryDisableForegroundPackageName = null,
        )
    }
    val effectiveMode = if (mode == AutomationDisableMode.None) {
        AutomationDisableMode.UntilAppSwitch
    } else {
        mode
    }
    return copy(
        automationEnabled = true,
        temporaryDisableMode = effectiveMode,
        temporaryDisableStartedAtMillis = nowMillis,
        temporaryDisableUntilMillis = effectiveMode.durationMillis?.let { nowMillis + it },
        temporaryDisableForegroundPackageName = foregroundPackageName.takeIf {
            effectiveMode == AutomationDisableMode.UntilAppSwitch
        },
    )
}
