package com.gaozay.smartflight.quickrule

import com.gaozay.smartflight.R
import com.gaozay.smartflight.activities.ActivityRepository
import com.gaozay.smartflight.activities.ActivityRuleMode
import com.gaozay.smartflight.activities.normalizeActivityName
import com.gaozay.smartflight.apps.InstalledAppRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class QuickRuleException(val messageRes: Int) : IllegalStateException()

class QuickRuleRepository @Inject constructor(
    private val apps: InstalledAppRepository,
    private val activities: ActivityRepository,
    private val resolver: QuickRuleTargetResolver,
) {
    suspend fun load(request: QuickRuleRequest): QuickRuleTarget {
        if (request.invalid) throw QuickRuleException(R.string.quick_rule_invalid_request)
        val detected = if (request.packageName == null) resolver.capture() else null
        val pkg = request.packageName ?: detected?.packageName ?: throw QuickRuleException(R.string.quick_rule_no_target)
        apps.refreshPackage(pkg, removeIfMissing = true)
        val app = apps.getApp(pkg) ?: throw QuickRuleException(R.string.quick_rule_app_missing)
        var activity: String? = null
        var mode = QuickRuleMode.Auto
        var enabled = true
        val raw = request.activityName ?: detected?.activityName
        if (raw != null) {
            try {
                activities.refreshActivities(pkg)
                val details = activities.observeDetails(pkg).first()
                val normalized = normalizeActivityName(pkg, raw)
                val component = details.components.find { it.className == normalized && it.isPresent && it.isEnabled && it.isDeclared }
                // An alias must have a valid enabled target, which owns the rule.
                activity = component?.canonicalName?.takeIf { name ->
                    details.components.any { it.className == name && it.targetActivity == null && it.isPresent && it.isEnabled && it.isDeclared }
                }
                enabled = details.config?.rulesEnabled != false
                mode = QuickRuleMode.forActivity(ActivityRuleMode.from(details.rules.find { it.activityName == activity }?.mode.orEmpty()))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* The app default is still editable when Activity scanning fails. */ }
        }
        return QuickRuleTarget(app, activity, QuickRuleMode.forApp(app), mode, enabled, request.packageName != null)
    }

    suspend fun save(target: QuickRuleTarget, scope: QuickRuleScope, mode: QuickRuleMode) {
        val pkg = target.app.packageName
        apps.refreshPackage(pkg, removeIfMissing = true)
        if (apps.getApp(pkg) == null) throw QuickRuleException(R.string.quick_rule_app_missing)
        when (scope) {
            QuickRuleScope.App -> when (mode) {
                QuickRuleMode.Online -> apps.setManualOnline(pkg)
                QuickRuleMode.Offline -> apps.setManualOffline(pkg)
                QuickRuleMode.Auto -> apps.resetToDefault(pkg)
            }
            QuickRuleScope.Activity -> {
                val name = target.activityName ?: throw QuickRuleException(R.string.quick_rule_activity_unavailable)
                // Revalidate at save time in case an update/uninstall occurred while the dialog was open.
                activities.refreshActivities(pkg)
                val details = activities.observeDetails(pkg).first()
                if (details.config?.scanError != null || details.components.none {
                    it.className == name && it.targetActivity == null && it.isPresent && it.isEnabled && it.isDeclared
                }) throw QuickRuleException(R.string.quick_rule_activity_unavailable)
                activities.saveRule(pkg, name, mode.activityMode(), note = null)
            }
        }
    }
}
