package com.gaozay.smartflight.runtime

import com.gaozay.smartflight.R
import com.gaozay.smartflight.domain.model.ExecutionAction
import com.gaozay.smartflight.domain.model.ExecutionResult
import com.gaozay.smartflight.domain.model.NetworkControlMode
import com.gaozay.smartflight.domain.model.ScreenState
import com.gaozay.smartflight.domain.model.TriggerSource
import com.gaozay.smartflight.i18n.AppStrings
import com.gaozay.smartflight.settings.UserSettings
import com.gaozay.smartflight.settings.temporaryDisableSummary

internal fun buildRuntimeSummary(
    settings: UserSettings,
    snapshot: RuntimeSnapshot,
    nowMillis: Long = System.currentTimeMillis(),
): String {
    val mode = settings.networkControlMode
    settings.temporaryDisableSummary(nowMillis)?.let { return it }
    if (!settings.automationEnabled &&
        snapshot.lastAction == ExecutionAction.DoNothing &&
        snapshot.lastActionResult == ExecutionResult.Pending
    ) {
        return AppStrings.get(R.string.automation_permanently_disabled)
    }
    if (snapshot.isAppExitDisconnectScheduled) {
        val remainingSeconds = remainingSeconds(
            pendingAtMillis = snapshot.pendingAppExitDisconnectAtMillis,
            fallbackSeconds = settings.appExitDelaySeconds,
            nowMillis = nowMillis,
        )
        return AppStrings.quantity(R.plurals.an_online_app_left_the_foreground_disconnecting_in_seconds, (remainingSeconds).toInt(), remainingSeconds)
    }
    if (snapshot.isScreenOffDisconnectScheduled) {
        val remainingSeconds = remainingSeconds(
            pendingAtMillis = snapshot.pendingScreenOffDisconnectAtMillis,
            fallbackSeconds = settings.screenOffDelaySeconds,
            nowMillis = nowMillis,
        )
        return AppStrings.quantity(R.plurals.screen_turned_off_disconnecting_in_seconds, (remainingSeconds).toInt(), remainingSeconds)
    }
    if (snapshot.screenState == ScreenState.ScreenOff &&
        !settings.monitorForegroundWhenScreenOff
    ) {
        return AppStrings.get(R.string.screen_turned_off_foreground_monitoring_paused_as_configured)
    }
    if (snapshot.lastAction == ExecutionAction.CancelScheduledDisconnect) {
        return when (snapshot.lastActionResult) {
            ExecutionResult.Success -> when (snapshot.lastTriggerSource) {
                TriggerSource.UserUnlocked -> AppStrings.get(R.string.device_unlocked_pending_screen_off_disconnect_canceled)
                TriggerSource.ScreenOn -> AppStrings.get(R.string.screen_turned_on_pending_screen_off_disconnect_canceled)
                TriggerSource.AppForegroundChanged -> AppStrings.get(R.string.runtime_app_return_delayed_disconnect_canceled)
                else -> AppStrings.get(R.string.pending_screen_off_disconnect_canceled)
            }

            ExecutionResult.Skipped -> when (snapshot.lastTriggerSource) {
                TriggerSource.UserUnlocked -> AppStrings.get(R.string.device_unlocked_no_pending_screen_off_disconnect_to_cancel)
                TriggerSource.ScreenOn -> AppStrings.get(R.string.screen_turned_on_no_pending_screen_off_disconnect_to_cancel)
                TriggerSource.AppForegroundChanged -> AppStrings.get(R.string.no_pending_app_exit_disconnect_to_cancel)
                else -> AppStrings.get(R.string.no_pending_screen_off_disconnect_to_cancel)
            }

            else -> snapshot.lastActionReason
        }
    }
    if (snapshot.lastAction == ExecutionAction.ScheduleAppExitDisconnect) {
        val remainingSeconds = remainingSeconds(
            pendingAtMillis = snapshot.pendingAppExitDisconnectAtMillis,
            fallbackSeconds = settings.appExitDelaySeconds,
            nowMillis = nowMillis,
        )
        return AppStrings.quantity(R.plurals.an_online_app_left_the_foreground_disconnecting_in_seconds, (remainingSeconds).toInt(), remainingSeconds)
    }
    if (snapshot.lastAction == ExecutionAction.DoNothing &&
        snapshot.lastTriggerSource == TriggerSource.Manual
    ) {
        return when (snapshot.lastActionResult) {
            ExecutionResult.Success -> buildProbeSuccessSummary(mode, snapshot)
            ExecutionResult.Failed -> snapshot.lastActionReason.ifBlank { AppStrings.get(R.string.state_probe_failed, mode.label) }
            else -> snapshot.lastActionReason.ifBlank { AppStrings.get(R.string.state_is_unconfirmed, mode.label) }
        }
    }
    if (snapshot.lastAction == ExecutionAction.DisconnectNow) {
        return when (snapshot.lastActionResult) {
            ExecutionResult.Success -> when (mode) {
                NetworkControlMode.AirplaneMode -> AppStrings.get(R.string.airplane_mode_enabled_currently_offline)
                NetworkControlMode.MobileData -> AppStrings.get(R.string.mobile_data_disabled_currently_offline, settings.mobileDataNoOpSuffix())
            }
            ExecutionResult.Skipped -> when (mode) {
                NetworkControlMode.AirplaneMode -> AppStrings.get(R.string.airplane_mode_was_already_enabled_no_additional_disconnect_needed)
                NetworkControlMode.MobileData -> AppStrings.get(R.string.mobile_data_was_already_disabled_no_additional_disconnect_needed, settings.mobileDataNoOpSuffix())
            }
            ExecutionResult.PartialSuccess -> snapshot.lastActionReason.ifBlank { AppStrings.get(R.string.unexpected_verification_result_after_writing, mode.label) }
            ExecutionResult.Failed -> snapshot.lastActionReason.ifBlank {
                when (mode) {
                    NetworkControlMode.AirplaneMode -> AppStrings.get(R.string.failed_to_enable_airplane_mode)
                    NetworkControlMode.MobileData -> AppStrings.get(R.string.failed_to_disable_mobile_data)
                }
            }
            else -> snapshot.lastActionReason.ifBlank { AppStrings.get(R.string.disconnecting) }
        }
    }
    if (snapshot.lastAction == ExecutionAction.ReconnectNow) {
        return when (snapshot.lastActionResult) {
            ExecutionResult.Success -> when (mode) {
                NetworkControlMode.AirplaneMode -> AppStrings.get(R.string.airplane_mode_disabled_connectivity_restored)
                NetworkControlMode.MobileData -> AppStrings.get(R.string.mobile_data_enabled_connectivity_restored, settings.mobileDataNoOpSuffix())
            }
            ExecutionResult.Skipped -> when (mode) {
                NetworkControlMode.AirplaneMode -> AppStrings.get(R.string.airplane_mode_was_already_disabled_no_additional_reconnect_needed)
                NetworkControlMode.MobileData -> AppStrings.get(R.string.mobile_data_was_already_enabled_no_additional_reconnect_needed, settings.mobileDataNoOpSuffix())
            }
            ExecutionResult.PartialSuccess -> snapshot.lastActionReason.ifBlank { AppStrings.get(R.string.unexpected_verification_result_after_writing, mode.label) }
            ExecutionResult.Failed -> snapshot.lastActionReason.ifBlank {
                when (mode) {
                    NetworkControlMode.AirplaneMode -> AppStrings.get(R.string.failed_to_disable_airplane_mode)
                    NetworkControlMode.MobileData -> AppStrings.get(R.string.failed_to_enable_mobile_data)
                }
            }
            else -> snapshot.lastActionReason.ifBlank { AppStrings.get(R.string.reconnecting) }
        }
    }
    return snapshot.lastActionReason
}

private fun remainingSeconds(
    pendingAtMillis: Long?,
    fallbackSeconds: Int,
    nowMillis: Long,
): Long = pendingAtMillis?.let {
    ((it - nowMillis).coerceAtLeast(0L) + 999L) / 1000L
} ?: fallbackSeconds.toLong()

private fun buildProbeSuccessSummary(
    mode: NetworkControlMode,
    snapshot: RuntimeSnapshot,
): String = when (mode) {
    NetworkControlMode.AirplaneMode -> when (snapshot.isAirplaneModeEnabled) {
        true -> AppStrings.get(R.string.airplane_mode_is_currently_enabled)
        false -> AppStrings.get(R.string.airplane_mode_is_currently_disabled)
        null -> AppStrings.get(R.string.airplane_mode_state_synchronized)
    }
    NetworkControlMode.MobileData -> when (snapshot.isMobileDataEnabled) {
        true -> AppStrings.get(R.string.mobile_data_is_currently_enabled)
        false -> AppStrings.get(R.string.mobile_data_is_currently_disabled)
        null -> AppStrings.get(R.string.mobile_data_state_synchronized)
    }
}
