package com.gaozay.smartflight.apps

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Gives instrumentation access to the same singleton used by the process listener. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppSyncTestEntryPoint {
    fun installedAppRepository(): InstalledAppRepository
}
