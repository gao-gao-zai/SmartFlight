package com.gaozay.smartflight.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparatorTest {
    private val comparator = VersionComparator()

    @Test
    fun prefixedRemoteVersionIsNewerThanCurrentVersion() {
        assertTrue(comparator.isRemoteNewer(currentVersion = "0.0.16", releaseTag = "v0.0.17"))
    }

    @Test
    fun prefixedAndPlainSameVersionAreEqual() {
        assertEquals(0, comparator.compare("0.0.16", "v0.0.16"))
        assertTrue(comparator.isSameVersion("0.0.16", "v0.0.16"))
    }

    @Test
    fun multiSegmentVersionComparisonPadsMissingSegments() {
        assertTrue(comparator.compare("1.2.3.1", "1.2.3") > 0)
        assertEquals(0, comparator.compare("1.2.0", "1.2"))
        assertFalse(comparator.isRemoteNewer(currentVersion = "1.10.0", releaseTag = "1.2.9"))
    }
}
