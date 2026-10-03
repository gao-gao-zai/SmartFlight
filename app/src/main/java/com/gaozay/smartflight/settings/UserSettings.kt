package com.gaozay.smartflight.settings

import com.gaozay.smartflight.R
import com.gaozay.smartflight.domain.model.CornerStyle
import com.gaozay.smartflight.domain.model.ExecutorType
import com.gaozay.smartflight.domain.model.NetworkControlMode
import com.gaozay.smartflight.domain.model.ThemeIntensity
import com.gaozay.smartflight.domain.model.ThemeMode
import com.gaozay.smartflight.domain.model.ThemePalette
import com.gaozay.smartflight.i18n.AppStrings
import com.gaozay.smartflight.i18n.ResourceLabel

enum class AutomationDisableMode(
    override val labelRes: Int,
    val shortLabelRes: Int,
    val tileLabelRes: Int,
    val durationMillis: Long? = null,
) : ResourceLabel {
    None(R.string.not_disabled, R.string.running, R.string.enable),
    UntilAppSwitch(R.string.disable_until_app_switch, R.string.until_app_switch, R.string.app_switch),
    UntilScreenOff(R.string.disable_until_screen_off, R.string.until_screen_off, R.string.screen_off),
    For1Minute(R.string.disable_for_1_minute, R.string.duration_1_minute, R.string.tile_1m, 60_000L),
    For5Minutes(R.string.disable_for_5_minutes, R.string.duration_5_minutes, R.string.tile_5m, 5 * 60_000L),
    For10Minutes(R.string.disable_for_10_minutes, R.string.duration_10_minutes, R.string.tile_10m, 10 * 60_000L),
    For20Minutes(R.string.disable_for_20_minutes, R.string.duration_20_minutes, R.string.tile_20m, 20 * 60_000L),
    For30Minutes(R.string.disable_for_30_minutes, R.string.duration_30_minutes, R.string.tile_30m, 30 * 60_000L),
    Permanent(R.string.disable_permanently, R.string.permanent, R.string.permanent);

    val shortLabel: String get() = AppStrings.get(shortLabelRes)
    val tileLabel: String get() = AppStrings.get(tileLabelRes)
}

enum class ForegroundMonitorMode(override val labelRes: Int) : ResourceLabel {
    Auto(R.string.automatic_selection),
    Accessibility(R.string.accessibility_events),
    UsageStats(R.string.usage_stats_polling),
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
    val foregroundMonitorMode: ForegroundMonitorMode = ForegroundMonitorMode.Auto,
    val monitorForegroundWhenScreenOff: Boolean = false,
    val skipReconnectOnWifi: Boolean = true,
    val skipDisconnectOnWifi: Boolean = true,
    val preserveWifiState: Boolean = true,
    val preserveBluetoothState: Boolean = true,
    val disableScreenOnReconnect: Boolean = true,
    val disableUnlockReconnect: Boolean = true,
    val showReconnectPrompt: Boolean = true,
    // An empty value means the localized built-in prompt. User text stays verbatim.
    val reconnectPromptText: String = "",
    val showDisconnectPrompt: Boolean = true,
    val disconnectPromptText: String = "",
    val themeMode: ThemeMode = ThemeMode.System,
    val themePalette: ThemePalette = ThemePalette.LogoOriginal,
    val customSeedColorArgb: Int = ThemePalette.LogoOriginal.seedColorArgb,
    val themeIntensity: ThemeIntensity = ThemeIntensity.Standard,
    val cornerStyle: CornerStyle = CornerStyle.Standard,
    val skippedUpdateVersion: String? = null,
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
        AutomationDisableMode.UntilAppSwitch -> AppStrings.get(R.string.temporarily_disabled_automation_resumes_on_the_next_app_switch)
        AutomationDisableMode.UntilScreenOff -> AppStrings.get(R.string.temporarily_disabled_automation_resumes_when_the_screen_turns_off)
        AutomationDisableMode.For1Minute,
        AutomationDisableMode.For5Minutes,
        AutomationDisableMode.For10Minutes,
        AutomationDisableMode.For20Minutes,
        AutomationDisableMode.For30Minutes -> {
            val remainingSeconds = temporaryDisableUntilMillis?.let {
                ((it - nowMillis).coerceAtLeast(0L) + 999L) / 1000L
            }
            if (remainingSeconds != null) {
                AppStrings.quantity(R.plurals.temporarily_disabled_seconds_remaining, (remainingSeconds).toInt(), remainingSeconds)
            } else {
                AppStrings.get(R.string.temporarily_disabled_automation_will_resume_later)
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
