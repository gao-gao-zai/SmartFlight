package com.gaozay.smartflight

import android.app.Application
import android.content.Context
import com.gaozay.smartflight.apps.InstalledAppChangeMonitor
import javax.inject.Inject
import com.gaozay.smartflight.i18n.AppStrings
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SmartFlightApp : Application() {
    @Inject lateinit var installedAppChangeMonitor: InstalledAppChangeMonitor

    override fun onCreate() {
        super.onCreate()
        installedAppChangeMonitor.start()
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        AppStrings.initialize(base)
    }
}
