package com.gaozay.smartflight.apps

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Gives instrumentation access to the same singleton used by the process listener. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppSyncTestEntryPoint {
    fun quickRuleRepository(): com.gaozay.smartflight.quickrule.QuickRuleRepository
    fun quickRuleTargets(): com.gaozay.smartflight.quickrule.QuickRuleTargetResolver
    fun installedAppRepository(): InstalledAppRepository
    fun activityRepository(): com.gaozay.smartflight.activities.ActivityRepository
    fun foregroundDetector(): com.gaozay.smartflight.runtime.ForegroundAppDetector
    fun foregroundTracker(): com.gaozay.smartflight.runtime.AccessibilityForegroundAppTracker
    fun foregroundObservations(): com.gaozay.smartflight.runtime.ForegroundObservationStore
    fun activityRecorder(): com.gaozay.smartflight.activities.ActivityRecorder
    fun settingsRepository(): com.gaozay.smartflight.settings.SettingsRepository
    fun accessRepository(): com.gaozay.smartflight.permission.AccessRepository
}
