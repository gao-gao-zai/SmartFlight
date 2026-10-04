package com.gaozay.smartflight.quickrule

import com.gaozay.smartflight.R
import com.gaozay.smartflight.activities.ActivityRuleMode
import com.gaozay.smartflight.data.local.entity.InstalledAppEntity

/** Public callers may suggest a target, but cannot write a rule without the dialog. */
object QuickRuleContract {
    const val ACTION = "com.gaozay.smartflight.action.QUICK_RULE"
    const val EXTRA_PACKAGE_NAME = "package_name"
    const val EXTRA_ACTIVITY_NAME = "activity_name"
    const val ACTIVITY_CLASS = "com.gaozay.smartflight.quickrule.QuickRuleActivity"
}

data class QuickRuleRequest(val packageName: String? = null, val activityName: String? = null, val invalid: Boolean = false)

enum class QuickRuleScope(val labelRes: Int) {
    App(R.string.quick_rule_scope_app), Activity(R.string.quick_rule_scope_activity),
}

enum class QuickRuleMode(val labelRes: Int) {
    Online(R.string.quick_rule_online), Offline(R.string.quick_rule_offline), Auto(R.string.quick_rule_auto);
    fun activityMode(): ActivityRuleMode = when (this) {
        Online -> ActivityRuleMode.Online
        Offline -> ActivityRuleMode.Offline
        Auto -> ActivityRuleMode.FollowApp
    }
    companion object {
        fun forApp(app: InstalledAppEntity): QuickRuleMode = when {
            app.isInBlacklist -> Offline
            app.isInWhitelist -> Online
            else -> Auto
        }
        fun forActivity(mode: ActivityRuleMode): QuickRuleMode = when (mode) {
            ActivityRuleMode.Online -> Online
            ActivityRuleMode.Offline -> Offline
            ActivityRuleMode.FollowApp -> Auto
        }
    }
}

data class QuickRuleTarget(
    val app: InstalledAppEntity,
    val activityName: String?,
    val appMode: QuickRuleMode,
    val activityMode: QuickRuleMode,
    val activityRulesEnabled: Boolean,
    val suppliedTarget: Boolean,
)

/** Ignore only our transient editor, never MainActivity or a different app. */
fun isQuickRuleOverlay(packageName: String, className: String?, hostPackage: String): Boolean =
    packageName == hostPackage && className == QuickRuleContract.ACTIVITY_CLASS

/** Host-owned Dialog windows must also leave the underlying foreground app unchanged. */
object QuickRuleOverlayState {
    @Volatile private var count = 0
    val visible: Boolean get() = count > 0
    @Synchronized fun entered() { count++ }
    @Synchronized fun left() { count = (count - 1).coerceAtLeast(0) }
}

fun isQuickRuleWindow(packageName: String, className: String?, hostPackage: String): Boolean =
    isQuickRuleOverlay(packageName, className, hostPackage) || (packageName == hostPackage && QuickRuleOverlayState.visible)
