package com.gaozay.smartflight.i18n

import android.content.Context
import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gaozay.smartflight.R
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalizationResourcesTest {
    private fun context(locale: Locale): Context {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        return target.createConfigurationContext(Configuration(target.resources.configuration).apply {
            setLocale(locale)
        })
    }

    @Test
    fun androidResolvesEnglishNotificationTileAndAccessibilityCopy() {
        val en = context(Locale.ENGLISH)
        assertEquals("SmartFlight", en.getString(R.string.app_name))
        assertEquals("SmartFlight is running", en.getString(R.string.automation_notification_title))
        assertEquals("SmartFlight", en.getString(R.string.automation_tile_label))
        assertEquals("App switch", en.getString(R.string.app_switch))
        assertEquals(" · Executor: ", en.getString(R.string.executor))
        for (field in R.string::class.java.fields) {
            assertFalse(field.name, Regex("[\\u3400-\\u9fff]").containsMatchIn(en.getString(field.getInt(null))))
        }
    }

    @Test
    fun androidPreservesChineseFallbackAndFormatsEnglishQuantities() {
        val zh = context(Locale.CHINESE)
        val en = context(Locale.ENGLISH)
        assertEquals("自动飞行", zh.getString(R.string.app_name))
        assertEquals(" · 执行器：", zh.getString(R.string.executor))
        assertEquals("1 second", en.resources.getQuantityString(R.plurals.seconds, 1, 1))
        assertEquals("2 seconds", en.resources.getQuantityString(R.plurals.seconds, 2, 2))
        assertEquals("1 秒", zh.resources.getQuantityString(R.plurals.seconds, 1, 1))
    }
}
