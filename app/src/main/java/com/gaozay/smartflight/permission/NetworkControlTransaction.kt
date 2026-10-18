package com.gaozay.smartflight.permission

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Reads and complete actions publish snapshots in the same order as radio operations. */
@Singleton
class NetworkControlTransaction @Inject constructor() {
    private val mutex = Mutex()
    suspend fun <T> run(block: suspend () -> T): T = mutex.withLock { block() }
}
