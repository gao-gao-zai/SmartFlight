package com.gaozay.smartflight.runtime

import com.gaozay.smartflight.apps.sourceTag
import com.gaozay.smartflight.data.local.entity.InstalledAppEntity
import com.gaozay.smartflight.domain.model.AppOnlineSourceTag
import com.gaozay.smartflight.domain.model.ScreenState
import com.gaozay.smartflight.settings.UserSettings
import kotlinx.coroutines.CompletableDeferred

sealed interface RuntimeEvent {
    data class Started(val initialScreenState: ScreenState) : RuntimeEvent
    data class Stopped(
        val finalScreenState: ScreenState,
        val ack: CompletableDeferred<Unit>?,
    ) : RuntimeEvent

    data object ScreenOff : RuntimeEvent
    data object ScreenOn : RuntimeEvent
    data object UserUnlocked : RuntimeEvent
    data object ForegroundProbeTick : RuntimeEvent
    data class ForegroundAppChanged(val foregroundApp: ForegroundAppInfo) : RuntimeEvent
    data object ForegroundEventSourceChanged : RuntimeEvent
    data class SettingsChanged(val settings: UserSettings) : RuntimeEvent
    data class AppsChanged(
        val appRulesByPackageName: Map<String, AppRuntimeRuleInfo>,
    ) : RuntimeEvent
    data object NetworkChanged : RuntimeEvent
    data object TemporaryDisableExpired : RuntimeEvent
    data object ScreenOffDisconnectDue : RuntimeEvent
    data object AppExitDisconnectDue : RuntimeEvent
}

fun RuntimeEvent.nameForLog(): String = when (this) {
    is RuntimeEvent.Started -> "Started"
    is RuntimeEvent.Stopped -> "Stopped"
    RuntimeEvent.ScreenOff -> "ScreenOff"
    RuntimeEvent.ScreenOn -> "ScreenOn"
    RuntimeEvent.UserUnlocked -> "UserUnlocked"
    RuntimeEvent.ForegroundProbeTick -> "ForegroundProbeTick"
    is RuntimeEvent.ForegroundAppChanged -> "ForegroundAppChanged"
    RuntimeEvent.ForegroundEventSourceChanged -> "ForegroundEventSourceChanged"
    is RuntimeEvent.SettingsChanged -> "SettingsChanged"
    is RuntimeEvent.AppsChanged -> "AppsChanged"
    RuntimeEvent.NetworkChanged -> "NetworkChanged"
    RuntimeEvent.TemporaryDisableExpired -> "TemporaryDisableExpired"
    RuntimeEvent.ScreenOffDisconnectDue -> "ScreenOffDisconnectDue"
    RuntimeEvent.AppExitDisconnectDue -> "AppExitDisconnectDue"
}

data class RuntimeState(
    val settings: UserSettings = UserSettings(),
    val screenState: ScreenState = ScreenState.Unknown,
    val lastTargetAppActive: Boolean? = null,
    val appRulesByPackageName: Map<String, AppRuntimeRuleInfo> = emptyMap(),
    val lastKnownForegroundApp: ForegroundAppInfo? = null,
)

data class AppRuntimeRuleInfo(
    val isInOnlineList: Boolean,
    val isInBlacklist: Boolean,
    val sourceTag: AppOnlineSourceTag?,
)

fun List<InstalledAppEntity>.toRuntimeRuleMap(): Map<String, AppRuntimeRuleInfo> =
    associate { app ->
        app.packageName to AppRuntimeRuleInfo(
            isInOnlineList = app.isInOnlineList,
            isInBlacklist = app.isInBlacklist,
            sourceTag = app.sourceTag(),
        )
    }
