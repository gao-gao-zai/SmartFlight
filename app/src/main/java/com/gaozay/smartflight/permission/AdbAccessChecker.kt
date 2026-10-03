package com.gaozay.smartflight.permission

import com.gaozay.smartflight.R
import com.gaozay.smartflight.i18n.AppStrings
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdbAccessChecker @Inject constructor(
    private val adbBootstrapRepository: AdbBootstrapRepository,
) {
    suspend fun check(): AccessCheckResult {
        val snapshot = adbBootstrapRepository.getSnapshot()
        val bootstrapped = snapshot.bootstrapped
        val commandText = buildString {
            append("adb devices\n")
            append("adb shell settings get global airplane_mode_on\n")
            append("adb shell cmd appops get com.gaozay.smartflight\n")
        }
        return AccessCheckResult(
            kind = AccessKind.Adb,
            title = AppStrings.get(R.string.adb_initialization),
            status = if (bootstrapped) AccessCheckStatus.Ready else AccessCheckStatus.Missing,
            summary = if (bootstrapped) AppStrings.get(R.string.adb_initialization_recorded_as_complete) else AppStrings.get(R.string.adb_initialization_is_not_complete_yet),
            recommendation = if (bootstrapped) {
                AppStrings.get(R.string.adb_initialization_recorded_description)
            } else {
                AppStrings.get(R.string.adb_setup_recommendation)
            },
            isBlocking = true,
            actionType = AccessActionType.Refresh,
            detail = if (bootstrapped) {
                buildString {
                    append(AppStrings.get(R.string.command_version_v))
                    append(snapshot.commandVersion)
                    if (snapshot.completedAtMillis > 0) {
                        append(AppStrings.get(R.string.recorded_at))
                        append(DateFormat.getDateTimeInstance().format(Date(snapshot.completedAtMillis)))
                    }
                    append(AppStrings.get(R.string.actual_command_execution_still_needs_integration_testing))
                }
            } else {
                AppStrings.get(R.string.adb_setup_steps)
            },
            copyText = commandText,
            copyLabel = AppStrings.get(R.string.copy_adb_check_commands),
        )
    }
}
