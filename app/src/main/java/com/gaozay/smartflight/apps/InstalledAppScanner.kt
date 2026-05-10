package com.gaozay.smartflight.apps

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.gaozay.smartflight.data.local.entity.InstalledAppEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InstalledAppScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun scanInstalledApps(): List<InstalledAppEntity> {
        val packageManager = context.packageManager
        val launcherPackages = queryLauncherPackages(packageManager)
        val now = System.currentTimeMillis()
        return queryInstalledPackages(packageManager)
            .mapNotNull { packageInfo ->
                val applicationInfo = packageInfo.applicationInfo ?: return@mapNotNull null
                val packageName = packageInfo.packageName
                val hasLauncherEntry = launcherPackages.contains(packageName)
                val declaresInternetPermission = packageInfo.requestedPermissions
                    ?.contains(Manifest.permission.INTERNET) == true
                val isSystemApp = applicationInfo.isSystemApp()
                if (isSystemApp) return@mapNotNull null
                val isAutoDetectedOnline = declaresInternetPermission && hasLauncherEntry
                InstalledAppEntity(
                    packageName = packageName,
                    label = applicationInfo.loadLabel(packageManager).toString(),
                    iconCacheKey = packageName,
                    isSystemApp = isSystemApp,
                    hasLauncherEntry = hasLauncherEntry,
                    declaresInternetPermission = declaresInternetPermission,
                    isAutoDetectedOnline = isAutoDetectedOnline,
                    isInOnlineList = isAutoDetectedOnline,
                    isInWhitelist = false,
                    isInBlacklist = false,
                    lastScannedAtMillis = now,
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    private fun ApplicationInfo.isSystemApp(): Boolean =
        flags and ApplicationInfo.FLAG_SYSTEM != 0 ||
            flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0

    private fun queryInstalledPackages(packageManager: PackageManager): List<PackageInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledPackages(
                PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        }

    private fun queryLauncherPackages(packageManager: PackageManager): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }.map { it.activityInfo.packageName }.toSet()
    }
}
