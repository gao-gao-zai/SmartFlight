package com.gaozay.smartflight.apps

import com.gaozay.smartflight.data.local.entity.InstalledAppEntity

interface InstalledAppSource {
    fun scanInstalledApps(): List<InstalledAppEntity>

    /** Null means the package is no longer visible/installed; other failures propagate. */
    fun scanPackage(packageName: String): InstalledAppEntity?
}
