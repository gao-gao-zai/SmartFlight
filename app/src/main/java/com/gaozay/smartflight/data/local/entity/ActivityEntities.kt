package com.gaozay.smartflight.data.local.entity

import androidx.room.Entity

@Entity(tableName = "activity_components", primaryKeys = ["packageName", "className"])
data class ActivityComponentEntity(
    val packageName: String,
    val className: String,
    val targetActivity: String? = null,
    val label: String? = null,
    val isEnabled: Boolean,
    val isExported: Boolean,
    val isPresent: Boolean = true,
    val isDeclared: Boolean = true,
    val versionCode: Long,
    val scannedAtMillis: Long,
) {
    val canonicalName: String get() = targetActivity ?: className
}

@Entity(tableName = "activity_rules", primaryKeys = ["packageName", "activityName"])
data class ActivityRuleEntity(
    val packageName: String,
    val activityName: String,
    val mode: String = "FollowApp",
    val note: String = "",
)

@Entity(tableName = "app_activity_configs", primaryKeys = ["packageName"])
data class AppActivityConfigEntity(
    val packageName: String,
    val rulesEnabled: Boolean = true,
    val lastScannedAtMillis: Long = 0,
    val versionCode: Long = 0,
    val scanError: String? = null,
    val needsReview: Boolean = false,
)

@Entity(tableName = "activity_visits", primaryKeys = ["packageName", "activityName"])
data class ActivityVisitEntity(
    val packageName: String,
    val activityName: String,
    val firstEnteredAtMillis: Long,
    val lastEnteredAtMillis: Long,
    val source: String,
    val sessionId: String? = null,
)
