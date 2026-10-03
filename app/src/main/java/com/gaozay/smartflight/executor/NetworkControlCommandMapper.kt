package com.gaozay.smartflight.executor

import com.gaozay.smartflight.R
import com.gaozay.smartflight.domain.model.NetworkControlMode
import com.gaozay.smartflight.i18n.AppStrings
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkControlCommandMapper @Inject constructor() {
    fun readCommandFor(mode: NetworkControlMode): ExecutorCommand =
        when (mode) {
            NetworkControlMode.AirplaneMode -> ExecutorReadonlyCommands.ReadAirplaneModeState
            NetworkControlMode.MobileData -> ExecutorReadonlyCommands.ReadMobileDataState
        }

    fun writeCommandFor(mode: NetworkControlMode, enabled: Boolean): ExecutorCommand =
        when (mode) {
            NetworkControlMode.AirplaneMode -> ExecutorWriteCommands.setAirplaneModeState(enabled)
            NetworkControlMode.MobileData -> ExecutorWriteCommands.setMobileDataEnabled(enabled)
        }

    fun labelFor(mode: NetworkControlMode): String =
        when (mode) {
            NetworkControlMode.AirplaneMode -> AppStrings.get(R.string.airplane_mode)
            NetworkControlMode.MobileData -> AppStrings.get(R.string.mobile_data)
        }

    fun alreadyInStateSummary(mode: NetworkControlMode, enabled: Boolean): String =
        when (mode) {
            NetworkControlMode.AirplaneMode ->
                if (enabled) AppStrings.get(R.string.airplane_mode_is_already_enabled) else AppStrings.get(R.string.airplane_mode_is_already_disabled)
            NetworkControlMode.MobileData ->
                if (enabled) AppStrings.get(R.string.mobile_data_is_already_enabled) else AppStrings.get(R.string.mobile_data_is_already_disabled)
        }

    fun enabledChangedSummary(mode: NetworkControlMode, enabled: Boolean): String =
        when (mode) {
            NetworkControlMode.AirplaneMode ->
                if (enabled) AppStrings.get(R.string.airplane_mode_enabled) else AppStrings.get(R.string.airplane_mode_disabled)
            NetworkControlMode.MobileData ->
                if (enabled) AppStrings.get(R.string.mobile_data_enabled) else AppStrings.get(R.string.mobile_data_disabled)
        }

    fun noReadExecutorSummary(mode: NetworkControlMode): String =
        AppStrings.get(R.string.no_executor_available_to_read_the_state, labelFor(mode))

    fun noToggleExecutorSummary(mode: NetworkControlMode): String =
        AppStrings.get(R.string.no_executor_available_to_toggle, labelFor(mode))

    fun unresolvedSetSummary(mode: NetworkControlMode): String =
        AppStrings.get(R.string.unable_to_parse_the_current_state_setting_canceled, labelFor(mode))

    fun unresolvedToggleSummary(mode: NetworkControlMode): String =
        AppStrings.get(R.string.unable_to_parse_the_current_state_toggle_canceled, labelFor(mode))

    fun writeFailedSummary(mode: NetworkControlMode): String =
        AppStrings.get(R.string.failed_to_write, labelFor(mode))

    fun withMode(
        mode: NetworkControlMode,
        result: ExecutorCommandResult,
        summary: String = result.summary,
    ): ExecutorCommandResult = result.copy(
        controlMode = mode,
        controlledEnabled = parseBinaryToggleState(result.stdout),
        summary = summary,
    )
}
