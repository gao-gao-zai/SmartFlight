package com.gaozay.smartflight.runtime

import com.gaozay.smartflight.R
import com.gaozay.smartflight.activities.ActivityRuleMode
import com.gaozay.smartflight.activities.ActivityRuntimeRule
import com.gaozay.smartflight.domain.model.AppOnlineSourceTag
import com.gaozay.smartflight.i18n.ResourceLabel

enum class ActivityMatchReason(override val labelRes: Int) : ResourceLabel {
    AppDefault(R.string.activity_match_app_default),
    ActivityOverride(R.string.activity_match_override),
    Unknown(R.string.activity_match_unknown),
    Stale(R.string.activity_match_stale),
    Paused(R.string.activity_match_paused),
    Invalid(R.string.activity_match_invalid),
}

data class ResolvedForegroundRule(val rule: AppRuntimeRuleInfo?, val reason: ActivityMatchReason, val activityName: String? = null)

fun resolveActivityRule(
    app: ForegroundAppInfo?,
    defaultRule: AppRuntimeRuleInfo?,
    activityRules: List<ActivityRuntimeRule>,
    nowMillis: Long = System.currentTimeMillis(),
): ResolvedForegroundRule {
    if (app == null) return ResolvedForegroundRule(defaultRule, ActivityMatchReason.Unknown)
    val candidates = activityRules.filter { it.packageName == app.packageName }
    if (candidates.isEmpty()) return ResolvedForegroundRule(defaultRule, ActivityMatchReason.AppDefault)
    if (candidates.none { it.rulesEnabled }) return ResolvedForegroundRule(defaultRule, ActivityMatchReason.Paused)
    val name = app.confirmedActivity(nowMillis) ?: return ResolvedForegroundRule(defaultRule,
        if (app.activityConfirmed && app.activityName != null) ActivityMatchReason.Stale else ActivityMatchReason.Unknown)
    val match = candidates.firstOrNull { it.activityName == name && it.rulesEnabled }
        ?: return ResolvedForegroundRule(defaultRule, ActivityMatchReason.AppDefault)
    if (!match.isValid) return ResolvedForegroundRule(defaultRule, ActivityMatchReason.Invalid, name)
    val rule = when (ActivityRuleMode.from(match.mode)) {
        ActivityRuleMode.Online -> AppRuntimeRuleInfo(true, false, AppOnlineSourceTag.Manual)
        ActivityRuleMode.Offline -> AppRuntimeRuleInfo(false, true, AppOnlineSourceTag.Manual)
        ActivityRuleMode.FollowApp -> return ResolvedForegroundRule(defaultRule, ActivityMatchReason.AppDefault)
    }
    return ResolvedForegroundRule(rule, ActivityMatchReason.ActivityOverride, name)
}
