package com.gaozay.smartflight.i18n

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.preferencesOf
import com.gaozay.smartflight.R
import com.gaozay.smartflight.data.local.entity.ExecutionLogEntity
import com.gaozay.smartflight.domain.model.ExecutionResult
import com.gaozay.smartflight.domain.model.ExecutorType
import com.gaozay.smartflight.domain.model.NetworkControlMode
import com.gaozay.smartflight.executor.ExecutorCommandResult
import com.gaozay.smartflight.executor.ExecutorReadonlyCommands
import com.gaozay.smartflight.permission.AccessGateState
import com.gaozay.smartflight.permission.AccessKind
import com.gaozay.smartflight.permission.AccessResultFormatter
import com.gaozay.smartflight.runtime.RuntimeSnapshot
import com.gaozay.smartflight.runtime.buildRuntimeSummary
import com.gaozay.smartflight.settings.AutomationDisableMode
import com.gaozay.smartflight.settings.UserSettings
import com.gaozay.smartflight.settings.toUserSettings
import com.gaozay.smartflight.settings.writeUserSettings
import com.gaozay.smartflight.toUiItem
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalizationRegressionTest : LocalizedStringsTest() {
    @Test
    fun labelsAndCommandPurposesAreResolvedAfterLocaleChanges() {
        assertEquals("飞行模式", NetworkControlMode.AirplaneMode.label)
        assertEquals("读取移动数据状态", ExecutorReadonlyCommands.ReadMobileDataState.purpose)
        useLocale(Locale.ENGLISH)
        assertEquals("Airplane mode", NetworkControlMode.AirplaneMode.label)
        assertEquals("Read mobile data state", ExecutorReadonlyCommands.ReadMobileDataState.purpose)
        assertEquals("App switch", AutomationDisableMode.UntilAppSwitch.tileLabel)
        assertEquals("Permanent", AutomationDisableMode.Permanent.tileLabel)
        useLocale(Locale.CHINESE)
        assertEquals("飞行模式", NetworkControlMode.AirplaneMode.label)
    }

    @Test
    fun permissionIdentityDoesNotDependOnTranslatedTitles() {
        useLocale(Locale.ENGLISH)
        val gate = AccessGateState()
        assertEquals("Usage access permission", gate.usageStatsAccess.title)
        assertEquals(AccessKind.UsageStats, gate.usageStatsAccess.kind)
        assertEquals(AccessKind.Accessibility, gate.accessibilityAccess.kind)
        assertEquals(AccessKind.Notifications, gate.notificationAccess.kind)
        assertEquals(AccessKind.BatteryOptimization, gate.batteryOptimization.kind)
    }

    @Test
    fun skippedResultsUseStructuredStateInEitherLanguage() {
        val formatter = AccessResultFormatter()
        val skipped = ExecutorCommandResult(
            executorType = ExecutorType.Root,
            executed = false,
            controlledEnabled = true,
            alreadyInRequestedState = true,
            summary = "A completely different message",
        )
        assertEquals(ExecutionResult.Skipped, formatter.executionResultFor(skipped))
        useLocale(Locale.ENGLISH)
        assertEquals(ExecutionResult.Skipped, formatter.executionResultFor(skipped))
        // A failure that happens to contain old translated words is still a failure.
        assertEquals(ExecutionResult.Failed, formatter.executionResultFor(skipped.copy(
            alreadyInRequestedState = false,
            controlledEnabled = null,
            summary = "已处于",
        )))
    }

    @Test
    fun englishRuntimeCountdownUsesSingularAndPluralWithoutChangingTiming() {
        useLocale(Locale.ENGLISH)
        val settings = UserSettings(automationEnabled = true)
        val snapshot = RuntimeSnapshot(
            isAppExitDisconnectScheduled = true,
            pendingAppExitDisconnectAtMillis = 2000L,
        )
        assertTrue(buildRuntimeSummary(settings, snapshot, nowMillis = 1000L).endsWith("1 second"))
        assertTrue(buildRuntimeSummary(settings, snapshot, nowMillis = 0L).endsWith("2 seconds"))
        useLocale(Locale.CHINESE)
        assertEquals("联网应用已离开前台，将在 1 秒后断网", buildRuntimeSummary(settings, snapshot, 1000L))
    }

    @Test
    fun historyWrittenInEitherLanguageKeepsProbeClassification() {
        val chinese = AppStrings.get(R.string.probe_airplane_mode_prefix) + "原始错误"
        useLocale(Locale.ENGLISH)
        val english = AppStrings.get(R.string.probe_mobile_data_prefix) + "original error"
        for (reason in listOf(chinese, english)) {
            val item = ExecutionLogEntity(
                timestampMillis = 1,
                foregroundPackageName = null,
                foregroundAppLabel = null,
                isWifiConnected = false,
                isWifiEnabled = false,
                isBluetoothEnabled = false,
                matchedRules = "",
                errorMessage = reason,
            ).toUiItem()
            assertEquals("State probe", item.action)
            assertEquals(reason, item.detail)
        }
    }

    @Test
    fun builtInPromptsFollowLocaleButCustomPromptsSurviveRoundTrips() {
        val preferences = mutablePreferencesOf()
        preferences.writeUserSettings(preferencesOf().toUserSettings())
        useLocale(Locale.ENGLISH)
        val defaults = preferences.toUserSettings()
        assertTrue(defaults.reconnectPromptText.isEmpty())
        assertEquals("SmartFlight reconnected", defaults.reconnectPromptText.ifBlank {
            AppStrings.get(R.string.prompt_reconnected_default)
        })
        val custom = defaults.copy(reconnectPromptText = "已回来", disconnectPromptText = "Offline now")
        preferences.writeUserSettings(custom)
        useLocale(Locale.CHINESE)
        assertEquals(custom, preferences.toUserSettings())
    }
}
