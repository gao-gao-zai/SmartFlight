package com.gaozay.smartflight.apps

import com.gaozay.smartflight.activities.ActivityRepository
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** Process-lifetime listener; a fresh process reconciles events missed while it was stopped. */
@Singleton
class InstalledAppChangeMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: InstalledAppRepository,
    private val activities: ActivityRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val changes = Channel<PackageChange>(Channel.UNLIMITED)
    private var started = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val change = intent.toPackageChange() ?: return
            // Return promptly; scanning and Room writes happen outside the main thread.
            changes.trySend(change)
        }
    }

    @Synchronized
    fun start() {
        if (started) return
        // Queue reconciliation first, then register before scanning. Changes during the full scan
        // are replayed afterwards, so they cannot be lost or overwritten by an older snapshot.
        changes.trySend(PackageChange.Reconcile)
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        started = true
        scope.launch {
            for (change in changes) {
                try {
                    when (change) {
                        PackageChange.Reconcile -> {
                            repository.refreshInstalledApps()
                            activities.refreshTrackedPackages()
                        }
                        is PackageChange.Refresh -> {
                            repository.refreshPackage(change.packageName, change.removed)
                            if (activities.isTracked(change.packageName)) activities.refreshActivities(change.packageName)
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    // Keep the listener alive. A manual refresh or next process start can reconcile.
                    Log.w("InstalledAppSync", "Unable to synchronize $change", error)
                }
            }
        }
    }
}

internal sealed interface PackageChange {
    data object Reconcile : PackageChange
    data class Refresh(val packageName: String, val removed: Boolean = false) : PackageChange
}

internal fun Intent.toPackageChange(): PackageChange.Refresh? {
    val removed = when (action) {
        Intent.ACTION_PACKAGE_ADDED, Intent.ACTION_PACKAGE_REPLACED, Intent.ACTION_PACKAGE_CHANGED -> false
        Intent.ACTION_PACKAGE_REMOVED -> {
            // An upgrade emits REMOVED(replacing=true) followed by ADDED/REPLACED.
            if (getBooleanExtra(Intent.EXTRA_REPLACING, false)) return null
            true
        }
        else -> return null
    }
    val uri = data?.takeIf { it.scheme == "package" } ?: return null
    val packageName = uri.schemeSpecificPart?.takeIf { it.isNotBlank() } ?: return null
    return PackageChange.Refresh(packageName, removed)
}
