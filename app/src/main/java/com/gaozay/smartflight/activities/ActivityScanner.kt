package com.gaozay.smartflight.activities

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.gaozay.smartflight.data.local.entity.ActivityComponentEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActivityScanner @Inject constructor(@ApplicationContext private val context: Context) : ActivitySource {
    override fun scanPackage(packageName: String): ActivityPackageScan? {
        val pm = context.packageManager
        val flags = PackageManager.GET_ACTIVITIES or PackageManager.MATCH_DISABLED_COMPONENTS
        val info = try {
            if (Build.VERSION.SDK_INT >= 33) pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
            else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, flags)
            }
        } catch (_: PackageManager.NameNotFoundException) {
            return null
        }
        val version = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        val now = System.currentTimeMillis()
        val components = info.activities.orEmpty().mapNotNull { activity ->
            val name = normalizeActivityName(packageName, activity.name) ?: return@mapNotNull null
            val componentEnabled = when (pm.getComponentEnabledSetting(ComponentName(packageName, name))) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> activity.enabled
                else -> false
            }
            ActivityComponentEntity(
                packageName = packageName,
                className = name,
                targetActivity = normalizeActivityName(packageName, activity.targetActivity),
                label = activity.nonLocalizedLabel?.toString() ?: activity.labelRes.takeIf { it != 0 }?.let { activity.loadLabel(pm).toString() },
                isEnabled = componentEnabled && activity.applicationInfo.enabled,
                isExported = activity.exported,
                versionCode = version,
                scannedAtMillis = now,
            )
        }
        return ActivityPackageScan(version, components)
    }
}

/** Accessibility classes must resolve to an enabled declared Activity, never a Dialog/View. */
@Singleton
class DeclaredActivityResolver @Inject constructor(@ApplicationContext private val context: Context) {
    fun resolve(packageName: String, rawName: String?): String? {
        val name = normalizeActivityName(packageName, rawName) ?: return null
        val pm = context.packageManager
        val info = try {
            if (Build.VERSION.SDK_INT >= 33) pm.getActivityInfo(ComponentName(packageName, name), PackageManager.ComponentInfoFlags.of(0))
            else {
                @Suppress("DEPRECATION")
                pm.getActivityInfo(ComponentName(packageName, name), 0)
            }
        } catch (_: PackageManager.NameNotFoundException) {
            return null
        }
        val enabled = when (pm.getComponentEnabledSetting(ComponentName(packageName, name))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> info.enabled
            else -> false
        }
        if (!enabled || !info.applicationInfo.enabled) return null
        return normalizeActivityName(packageName, info.targetActivity) ?: name
    }
}
