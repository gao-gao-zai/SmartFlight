package com.gaozay.smartflight.permission

data class RootProbeSnapshot(
    val confirmedAvailable: Boolean = false,
    val lastProbeAtMillis: Long = 0,
    val lastProbeSummary: String = "",
)

interface RootAccessProbeRepository {
    suspend fun getSnapshot(): RootProbeSnapshot

    suspend fun updateSnapshot(snapshot: RootProbeSnapshot)
}
