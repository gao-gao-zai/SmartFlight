package com.gaozay.smartflight.permission

import android.content.Context
import android.content.pm.PackageManager
import com.gaozay.smartflight.R
import com.gaozay.smartflight.i18n.AppStrings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import rikka.shizuku.Shizuku

@Singleton
class ShizukuAccessChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun check(): AccessCheckResult {
        val installed = runCatching {
            context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
        }.isSuccess
        if (!installed) {
            return AccessCheckResult(
                kind = AccessKind.Shizuku,
                title = "Shizuku",
                status = AccessCheckStatus.Missing,
                summary = AppStrings.get(R.string.shizuku_app_not_detected),
                recommendation = AppStrings.get(R.string.shizuku_install_recommendation),
                isBlocking = true,
                actionType = AccessActionType.Refresh,
                detail = AppStrings.get(R.string.shizuku_setup_description),
            )
        }

        val binderAlive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!binderAlive) {
            return AccessCheckResult(
                kind = AccessKind.Shizuku,
                title = "Shizuku",
                status = AccessCheckStatus.Detected,
                summary = AppStrings.get(R.string.shizuku_is_installed_but_its_service_is_not_running),
                recommendation = AppStrings.get(R.string.open_and_start_shizuku_then_return_to_smartflight_and_check_again),
                isBlocking = true,
                actionType = AccessActionType.Refresh,
                detail = AppStrings.get(R.string.shizuku_binder_required_description),
            )
        }

        val granted = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        val shouldShowRationale = runCatching {
            Shizuku.shouldShowRequestPermissionRationale()
        }.getOrDefault(false)
        val remoteUid = runCatching { Shizuku.getUid() }.getOrNull()

        return AccessCheckResult(
            kind = AccessKind.Shizuku,
            title = "Shizuku",
            status = if (granted) AccessCheckStatus.Ready else AccessCheckStatus.Detected,
            summary = if (granted) {
                AppStrings.get(R.string.shizuku_service_is_running_and_smartflight_is_authorized)
            } else {
                AppStrings.get(R.string.shizuku_service_is_running_but_smartflight_is_not_authorized_yet)
            },
            recommendation = if (granted) {
                AppStrings.get(R.string.shizuku_can_be_used_as_an_advanced_execution_channel)
            } else if (shouldShowRationale) {
                AppStrings.get(R.string.shizuku_permission_denied_recommendation)
            } else {
                AppStrings.get(R.string.you_can_now_request_shizuku_permission_directly)
            },
            isBlocking = true,
            actionType = if (granted) AccessActionType.None else AccessActionType.RequestPermission,
            detail = buildString {
                append(AppStrings.get(R.string.binder_connected))
                if (remoteUid != null) {
                    append(AppStrings.get(R.string.remote_uid))
                    append(remoteUid)
                    append(if (remoteUid == 0) AppStrings.get(R.string.root_uid_suffix) else if (remoteUid == 2000) AppStrings.get(R.string.adb_uid_suffix) else "")
                }
                if (!granted) {
                    append(AppStrings.get(R.string.sentence_separator))
                    append(
                        if (shouldShowRationale) {
                            AppStrings.get(R.string.permission_appears_to_have_been_denied_by_the_user)
                        } else {
                            AppStrings.get(R.string.permission_is_not_granted_yet_you_can_request_it)
                        },
                    )
                }
            },
            satisfiesRequirement = granted,
        )
    }
}
