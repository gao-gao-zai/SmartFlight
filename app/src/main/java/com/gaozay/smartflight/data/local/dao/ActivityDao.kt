package com.gaozay.smartflight.data.local.dao

import androidx.room.*
import com.gaozay.smartflight.activities.ActivityRuntimeRule
import com.gaozay.smartflight.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activity_components WHERE packageName = :packageName ORDER BY className")
    fun observeComponents(packageName: String): Flow<List<ActivityComponentEntity>>
    @Query("SELECT * FROM activity_rules WHERE packageName = :packageName")
    fun observeRules(packageName: String): Flow<List<ActivityRuleEntity>>
    @Query("SELECT * FROM activity_visits WHERE packageName = :packageName ORDER BY firstEnteredAtMillis")
    fun observeVisits(packageName: String): Flow<List<ActivityVisitEntity>>
    @Query("SELECT * FROM app_activity_configs WHERE packageName = :packageName")
    fun observeConfig(packageName: String): Flow<AppActivityConfigEntity?>
    @Query("SELECT * FROM app_activity_configs")
    suspend fun getConfigs(): List<AppActivityConfigEntity>
    @Query("SELECT * FROM app_activity_configs WHERE packageName = :packageName")
    suspend fun getConfig(packageName: String): AppActivityConfigEntity?
    @Query("SELECT * FROM activity_components WHERE packageName = :packageName AND className = :className")
    suspend fun getComponent(packageName: String, className: String): ActivityComponentEntity?
    @Query("SELECT * FROM activity_visits WHERE packageName = :packageName AND activityName = :activityName")
    suspend fun getVisit(packageName: String, activityName: String): ActivityVisitEntity?
    @Query("SELECT * FROM activity_components WHERE packageName = :packageName")
    suspend fun getComponents(packageName: String): List<ActivityComponentEntity>
    @Query("SELECT * FROM activity_rules WHERE packageName = :packageName")
    suspend fun getRules(packageName: String): List<ActivityRuleEntity>

    @Query("""
        SELECT r.packageName, r.activityName, r.mode,
               COALESCE(cfg.rulesEnabled, 1) AS rulesEnabled,
               CASE WHEN c.isPresent = 1 AND c.isEnabled = 1 AND c.isDeclared = 1
                    AND a.isInstalled = 1 AND cfg.scanError IS NULL THEN 1 ELSE 0 END AS isValid
        FROM activity_rules r
        LEFT JOIN activity_components c ON c.packageName = r.packageName AND c.className = r.activityName
        LEFT JOIN app_activity_configs cfg ON cfg.packageName = r.packageName
        LEFT JOIN installed_apps a ON a.packageName = r.packageName
        WHERE r.mode != 'FollowApp'
    """)
    fun observeRuntimeRules(): Flow<List<ActivityRuntimeRule>>

    @Upsert suspend fun upsertComponents(components: List<ActivityComponentEntity>)
    @Upsert suspend fun upsertComponent(component: ActivityComponentEntity)
    @Upsert suspend fun upsertRule(rule: ActivityRuleEntity)
    @Upsert suspend fun upsertConfig(config: AppActivityConfigEntity)
    @Upsert suspend fun upsertVisit(visit: ActivityVisitEntity)
    @Query("UPDATE activity_components SET isPresent = 0 WHERE packageName = :packageName")
    suspend fun markMissing(packageName: String)

    @Transaction
    suspend fun replaceComponents(packageName: String, components: List<ActivityComponentEntity>, config: AppActivityConfigEntity) {
        markMissing(packageName)
        upsertComponents(components)
        upsertConfig(config)
    }
}
