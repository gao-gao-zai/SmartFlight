package com.gaozay.smartflight.runtime

import com.gaozay.smartflight.apps.InstalledAppRepository
import com.gaozay.smartflight.data.local.entity.InstalledAppEntity
import com.gaozay.smartflight.domain.model.TriggerSource
import com.gaozay.smartflight.domain.model.ExecutorType
import com.gaozay.smartflight.permission.AdvancedAccessState
import com.gaozay.smartflight.permission.AccessGateState
import com.gaozay.smartflight.permission.AccessCheckResult
import com.gaozay.smartflight.permission.AccessCheckStatus
import com.gaozay.smartflight.permission.AccessRepository
import com.gaozay.smartflight.settings.SettingsRepository
import com.gaozay.smartflight.settings.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal class FakeRuntimeStatusRepository(
    initialSnapshot: RuntimeSnapshot = RuntimeSnapshot(),
) : RuntimeStatusRepository {
    private val mutableSnapshot = MutableStateFlow(initialSnapshot)
    override val snapshot: Flow<RuntimeSnapshot> = mutableSnapshot

    override suspend fun updateSnapshot(transform: (RuntimeSnapshot) -> RuntimeSnapshot) {
        mutableSnapshot.value = transform(mutableSnapshot.value)
    }

    val currentSnapshot: RuntimeSnapshot
        get() = mutableSnapshot.value
}

internal class FakeSettingsRepository(
    initialSettings: UserSettings = UserSettings(),
) : SettingsRepository {
    private val mutableSettings = MutableStateFlow(initialSettings)
    override val settings: Flow<UserSettings> = mutableSettings

    override suspend fun setAutomationEnabled(enabled: Boolean) {
        mutableSettings.value = mutableSettings.value.copy(automationEnabled = enabled)
    }

    override suspend fun updateSettings(transform: (UserSettings) -> UserSettings) {
        mutableSettings.value = transform(mutableSettings.value)
    }

    val currentSettings: UserSettings
        get() = mutableSettings.value
}

internal class FakeAccessRepository(
    initialAccessGateState: AccessGateState = AccessGateState(),
) : AccessRepository {
    private val mutableAccessGateState = MutableStateFlow(initialAccessGateState)
    override val accessGateState: StateFlow<AccessGateState> = mutableAccessGateState
    val disconnectedRequests = mutableListOf<Boolean>()

    override suspend fun refresh() = Unit
    override suspend fun setAdbBootstrapped(bootstrapped: Boolean) = Unit
    override suspend fun probeRootAccess() = Unit
    override suspend fun autoGrantCompanionPermissions() = Unit
    override suspend fun syncCurrentNetworkControlState() = Unit
    override suspend fun probeCurrentNetworkControlState() = Unit
    override suspend fun toggleCurrentNetworkControlState() = Unit

    override suspend fun setDisconnectedState(
        disconnected: Boolean,
        triggerSource: TriggerSource,
        reason: String?,
    ) {
        disconnectedRequests += disconnected
    }
}

internal class FakeInstalledAppRepository(
    apps: List<InstalledAppEntity> = emptyList(),
) : InstalledAppRepository {
    private val mutableApps = MutableStateFlow(apps)
    override fun observeApps(): Flow<List<InstalledAppEntity>> = mutableApps
    override fun observeAppCount(): Flow<Int> = MutableStateFlow(mutableApps.value.size)
    override suspend fun getApp(packageName: String): InstalledAppEntity? =
        mutableApps.value.firstOrNull { it.packageName == packageName }

    override suspend fun refreshInstalledApps(): Int = mutableApps.value.size
    override suspend fun upsertApps(apps: List<InstalledAppEntity>) {
        mutableApps.value = apps
    }
    override suspend fun setManualOnline(packageName: String) = Unit
    override suspend fun setManualOffline(packageName: String) = Unit
    override suspend fun resetToDefault(packageName: String) = Unit
}

internal class FakeForegroundAppSource(
    var app: ForegroundAppInfo? = null,
) : ForegroundAppSource {
    var detectCalls: Int = 0
    override fun detect(): ForegroundAppInfo? {
        detectCalls++
        return app
    }
}

internal class NoOpRuntimePromptNotifier : RuntimePromptNotifier {
    val automationPausedPrompts = mutableListOf<String>()
    val automationRestoredPrompts = mutableListOf<String>()

    override suspend fun showReconnectPrompt(settings: UserSettings) = Unit

    override suspend fun showDisconnectPrompt(settings: UserSettings) = Unit

    override suspend fun showAutomationPausedPrompt(settings: UserSettings, reason: String) {
        automationPausedPrompts += reason
    }

    override suspend fun showAutomationRestoredPrompt(settings: UserSettings, reason: String) {
        automationRestoredPrompts += reason
    }
}

internal fun availableAccessGateState(): AccessGateState =
    AccessGateState(
        advancedAccess = AdvancedAccessState(
            selectedExecutorType = ExecutorType.Root,
            checks = listOf(
                AccessCheckResult(
                    title = "Root",
                    status = AccessCheckStatus.Ready,
                    summary = "可用",
                    recommendation = "",
                    isBlocking = true,
                ),
            ),
        ),
    )
