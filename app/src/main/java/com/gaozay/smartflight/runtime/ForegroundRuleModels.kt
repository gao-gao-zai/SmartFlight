package com.gaozay.smartflight.runtime

import com.gaozay.smartflight.domain.model.AppOnlineSourceTag
import com.gaozay.smartflight.settings.UserSettings

data class ForegroundRuleContext(
    val settings: UserSettings,
    val packageName: String?,
    val appLabel: String?,
    val isInOnlineList: Boolean,
    val isInBlacklist: Boolean,
    val onlineSource: AppOnlineSourceTag?,
    val isWifiConnected: Boolean,
    val executorAvailable: Boolean,
    val previousTargetAppActive: Boolean?,
    val isCurrentlyDisconnected: Boolean? = null,
    val isAppExitDisconnectScheduled: Boolean = false,
    val allowReconnectWhenTargetAppAlreadyActive: Boolean = false,
) {
    fun displayName(): String = appLabel ?: packageName ?: "未知应用"

    fun isTargetAppActive(): Boolean =
        packageName != null && !isInBlacklist && isInOnlineList
}

data class ForegroundRuleDecision(
    val targetAppActive: Boolean,
    val action: ForegroundAction,
    val reason: String,
    val matchedRules: List<String>,
    val shouldLog: Boolean,
)

sealed interface ForegroundAction {
    data class None(
        val reason: String = "",
    ) : ForegroundAction

    data class Reconnect(
        val reason: String,
    ) : ForegroundAction

    data class Disconnect(
        val reason: String,
    ) : ForegroundAction

    data class ScheduleDisconnect(
        val reason: String,
        val delaySeconds: Int,
    ) : ForegroundAction

    data class CancelScheduledDisconnect(
        val reason: String,
    ) : ForegroundAction

    data class PauseAutomation(
        val reason: String,
    ) : ForegroundAction
}
