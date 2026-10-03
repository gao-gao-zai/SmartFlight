package com.gaozay.smartflight.activities

import org.junit.Assert.*
import org.junit.Test

class ActivityIdentityTest {
    @Test fun acceptsLegalUnicodeActivityNamesAndNestedClassNames() {
        assertEquals("example.app.支付Activity", normalizeActivityName("example.app", ".支付Activity"))
        assertEquals("example.app.页面\$Inner", normalizeActivityName("example.app", "页面\$Inner"))
        assertNull(normalizeActivityName("example.app", "Page\u0000Name"))
    }

    @Test fun relativeAndShortNamesBecomeExactCanonicalClassNames() {
        assertEquals("example.app.Payment", normalizeActivityName("example.app", ".Payment"))
        assertEquals("example.app.Payment", normalizeActivityName("example.app", "Payment"))
        assertEquals("example.app.Payment\$Inner", normalizeActivityName("example.app", "example.app.Payment\$Inner"))
        assertEquals("android.app.Activity", normalizeActivityName("example.app", "android.app.Activity"))
    }
    @Test fun emptyWidgetDescriptionsAndPatternsAreNotClassNames() {
        for (raw in listOf(null, "", " ", "example.*", "example/app/Payment", ".", "a..b", "Some screen"))
            assertNull("$raw", normalizeActivityName("example.app", raw))
    }
}
