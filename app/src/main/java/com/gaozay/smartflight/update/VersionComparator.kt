package com.gaozay.smartflight.update

import javax.inject.Inject

class VersionComparator @Inject constructor() {
    fun isRemoteNewer(currentVersion: String, releaseTag: String): Boolean =
        compare(releaseTag, currentVersion) > 0

    fun isSameVersion(first: String?, second: String?): Boolean {
        if (first.isNullOrBlank() || second.isNullOrBlank()) {
            return false
        }
        return compare(first, second) == 0
    }

    fun compare(left: String, right: String): Int {
        val leftSegments = left.toVersionSegments()
        val rightSegments = right.toVersionSegments()
        val maxSize = maxOf(leftSegments.size, rightSegments.size)
        repeat(maxSize) { index ->
            val leftValue = leftSegments.getOrElse(index) { 0 }
            val rightValue = rightSegments.getOrElse(index) { 0 }
            if (leftValue != rightValue) {
                return leftValue.compareTo(rightValue)
            }
        }
        return 0
    }

    private fun String.toVersionSegments(): List<Int> =
        trim()
            .removePrefix("v")
            .removePrefix("V")
            .split('.', '-', '_')
            .mapNotNull { segment ->
                segment.takeWhile { it.isDigit() }.takeIf { it.isNotBlank() }?.toIntOrNull()
            }
            .ifEmpty { listOf(0) }
}
