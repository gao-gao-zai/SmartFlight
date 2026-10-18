package com.gaozay.smartflight.executor

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** Keep a stalled Binder/pipe off the caller; late completion cannot resume a cancelled call. */
internal suspend fun <T> cancellableBlockingCall(onCancel: () -> Unit = {}, block: () -> T): T =
    suspendCancellableCoroutine { continuation ->
        val task = BoundedProcessExecution.daemonTask("SmartFlight-blocking-call") {
            try {
                val result = block()
                continuation.resume(result)
            } catch (error: Throwable) {
                continuation.resumeWithException(error)
            }
        }
        continuation.invokeOnCancellation {
            task.cancel(true)
            onCancel()
        }
    }
