package com.gaozay.smartflight.activities

import com.gaozay.smartflight.R
import com.gaozay.smartflight.data.local.entity.*
import com.gaozay.smartflight.i18n.ResourceLabel

/** Exact component matching only. A short or relative manifest name becomes a full class name. */
fun normalizeActivityName(packageName: String, name: String?): String? {
    val value = name?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val full = when {
        value.startsWith('.') -> packageName + value
        '.' !in value -> "$packageName.$value"
        else -> value
    }
    return full.takeIf { it.split('.').all(::isClassNameSegment) }
}

private fun isClassNameSegment(value: String): Boolean {
    if (value.isEmpty()) return false
    var offset = 0
    while (offset < value.length) {
        val point = value.codePointAt(offset)
        if (Character.isISOControl(point) || !(if (offset == 0) Character.isJavaIdentifierStart(point) else Character.isJavaIdentifierPart(point))) return false
        offset += Character.charCount(point)
    }
    return true
}

enum class ActivityRuleMode(override val labelRes: Int) : ResourceLabel {
    FollowApp(R.string.activity_follow_app),
    Online(R.string.online),
    Offline(R.string.offline);
    companion object {
        fun from(value: String): ActivityRuleMode = entries.find { it.name == value } ?: FollowApp
    }
}

data class ActivityPackageScan(val versionCode: Long, val components: List<ActivityComponentEntity>)

interface ActivitySource {
    fun scanPackage(packageName: String): ActivityPackageScan?
}

data class ActivityRuntimeRule(
    val packageName: String,
    val activityName: String,
    val mode: String,
    val rulesEnabled: Boolean,
    val isValid: Boolean,
)

data class ActivityRuleSummary(
    val packageName: String,
    val ruleCount: Int,
    val validCount: Int,
    val rulesEnabled: Boolean,
)

data class ActivityDetails(
    val components: List<ActivityComponentEntity> = emptyList(),
    val rules: List<ActivityRuleEntity> = emptyList(),
    val visits: List<ActivityVisitEntity> = emptyList(),
    val config: AppActivityConfigEntity? = null,
)
