package com.gaozay.smartflight.permission

data class AdbBootstrapSnapshot(
    val bootstrapped: Boolean = false,
    val completedAtMillis: Long = 0,
    val commandVersion: Int = 1,
)

interface AdbBootstrapRepository {
    suspend fun getSnapshot(): AdbBootstrapSnapshot

    suspend fun setBootstrapped(bootstrapped: Boolean)
}
