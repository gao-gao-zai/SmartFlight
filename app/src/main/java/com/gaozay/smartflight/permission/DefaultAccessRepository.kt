package com.gaozay.smartflight.permission

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultAccessRepository @Inject constructor(
    private val accessGateRefresher: AccessGateRefresher,
    private val adbBootstrapRepository: AdbBootstrapRepository,
    private val rootAccessChecker: RootAccessChecker,
    private val companionPermissionGrantService: CompanionPermissionGrantService,
    private val networkControlStateSyncer: NetworkControlStateSyncer,
    private val networkControlActionExecutor: NetworkControlActionExecutor,
) : AccessRepository {
    private val mutableAccessGateState = MutableStateFlow(AccessGateState())

    override val accessGateState: StateFlow<AccessGateState> = mutableAccessGateState.asStateFlow()

    override suspend fun refresh() {
        mutableAccessGateState.value = accessGateRefresher.refresh()
    }

    override suspend fun setAdbBootstrapped(bootstrapped: Boolean) {
        adbBootstrapRepository.setBootstrapped(bootstrapped)
        refresh()
    }

    override suspend fun probeRootAccess() {
        rootAccessChecker.probeAuthorization()
        refresh()
    }

    override suspend fun autoGrantCompanionPermissions() {
        companionPermissionGrantService.autoGrantCompanionPermissions(accessGateState.value)
        refresh()
    }

    override suspend fun syncCurrentNetworkControlState() {
        networkControlStateSyncer.syncCurrentNetworkControlState()
    }

    override suspend fun probeCurrentNetworkControlState() {
        networkControlStateSyncer.probeCurrentNetworkControlState()
    }

    override suspend fun toggleCurrentNetworkControlState() {
        networkControlActionExecutor.toggleCurrentNetworkControlState()
    }

    override suspend fun setDisconnectedState(
        disconnected: Boolean,
        triggerSource: com.gaozay.smartflight.domain.model.TriggerSource,
        reason: String?,
    ) {
        networkControlActionExecutor.setDisconnectedState(
            disconnected = disconnected,
            triggerSource = triggerSource,
            reason = reason,
        )
    }
}
