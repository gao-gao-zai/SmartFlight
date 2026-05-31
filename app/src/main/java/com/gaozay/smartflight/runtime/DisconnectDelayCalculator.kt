package com.gaozay.smartflight.runtime

object DisconnectDelayCalculator {
    fun remainingDelayMillis(
        delaySeconds: Int,
        baseTimestampMillis: Long,
        nowMillis: Long,
    ): Long {
        val totalDelayMillis = delaySeconds.coerceAtLeast(0) * 1_000L
        val effectiveBaseMillis = baseTimestampMillis.coerceAtMost(nowMillis)
        val elapsedMillis = (nowMillis - effectiveBaseMillis).coerceAtLeast(0L)
        return (totalDelayMillis - elapsedMillis).coerceAtLeast(0L)
    }
}
