package com.gaozay.smartflight

import android.app.Application
import android.content.Context
import com.gaozay.smartflight.i18n.AppStrings
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SmartFlightApp : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        AppStrings.initialize(base)
    }
}
