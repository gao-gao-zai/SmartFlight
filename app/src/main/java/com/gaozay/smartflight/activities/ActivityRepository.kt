package com.gaozay.smartflight.activities

import com.gaozay.smartflight.data.local.dao.ActivityDao
import com.gaozay.smartflight.data.local.entity.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActivityRepository @Inject constructor(private val dao: ActivityDao, private val source: ActivitySource) {
    private val mutex = Mutex()
    fun observeDetails(packageName: String): Flow<ActivityDetails> = combine(
        dao.observeComponents(packageName), dao.observeRules(packageName), dao.observeVisits(packageName), dao.observeConfig(packageName),
    ) { components, rules, visits, config -> ActivityDetails(components, rules, visits, config) }
    fun observeRuntimeRules(): Flow<List<ActivityRuntimeRule>> = dao.observeRuntimeRules()
    fun observeSummaries(): Flow<Map<String, ActivityRuleSummary>> = observeRuntimeRules().map { rules ->
        rules.groupBy { it.packageName }.mapValues { (pkg, entries) ->
            ActivityRuleSummary(pkg, entries.size, entries.count { it.isValid }, entries.first().rulesEnabled)
        }
    }
    suspend fun isTracked(packageName: String): Boolean = dao.getConfig(packageName) != null

    suspend fun refreshTrackedPackages() {
        for (config in dao.getConfigs()) {
            try { refreshActivities(config.packageName) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Failed scans persist their error and retain the previous list. */ }
        }
    }

    suspend fun refreshActivities(packageName: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val config = dao.getConfig(packageName) ?: AppActivityConfigEntity(packageName)
            try {
                val scan = source.scanPackage(packageName)
                if (scan == null) {
                    dao.replaceComponents(packageName, emptyList(), config.copy(needsReview = true, scanError = null))
                } else {
                    val currentNames = scan.components.filter { it.isEnabled }.map { it.canonicalName }.toSet()
                    val invalidRules = dao.getRules(packageName).any { it.mode != ActivityRuleMode.FollowApp.name && it.activityName !in currentNames }
                    dao.replaceComponents(packageName, scan.components, config.copy(
                        lastScannedAtMillis = System.currentTimeMillis(), versionCode = scan.versionCode,
                        scanError = null, needsReview = config.needsReview || invalidRules,
                    ))
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                dao.upsertConfig(config.copy(scanError = error.message ?: error.javaClass.simpleName))
                throw error
            }
        }
    }

    suspend fun setRulesEnabled(packageName: String, enabled: Boolean) = mutex.withLock {
        dao.upsertConfig((dao.getConfig(packageName) ?: AppActivityConfigEntity(packageName)).copy(rulesEnabled = enabled))
    }
    suspend fun acknowledgeReview(packageName: String) = mutex.withLock {
        dao.getConfig(packageName)?.let { dao.upsertConfig(it.copy(needsReview = false)) }
    }
    suspend fun saveRule(packageName: String, className: String, mode: ActivityRuleMode, note: String) {
        if (mode != ActivityRuleMode.FollowApp && dao.getComponent(packageName, className)?.isDeclared == false) refreshActivities(packageName)
        mutex.withLock {
        val name = normalizeActivityName(packageName, className) ?: error("Invalid activity name")
        val component = dao.getComponent(packageName, name)
        if (mode != ActivityRuleMode.FollowApp && component?.isDeclared == false) throw UnverifiedActivityException()
        val canonical = component?.canonicalName ?: name
        // Following the app removes the override, while keeping notes and all recognition history.
        dao.upsertRule(ActivityRuleEntity(packageName, canonical, mode.name, note.trim().take(500)))
        }
    }
    suspend fun recordVisit(packageName: String, activityName: String, timestamp: Long, source: String, sessionId: String?) = mutex.withLock {
        val config = dao.getConfig(packageName) ?: return@withLock
        val name = normalizeActivityName(packageName, activityName) ?: return@withLock
        val existingComponent = dao.getComponent(packageName, name)
        val canonical = existingComponent?.canonicalName ?: name
        if (existingComponent == null) {
            // A system resume event is evidence of entry, but declaration verification is still required for a rule.
            dao.upsertComponent(ActivityComponentEntity(packageName, name, isEnabled = true, isExported = false,
                isDeclared = false, versionCode = config.versionCode, scannedAtMillis = timestamp))
        }
        if (existingComponent != null && !existingComponent.isDeclared && !existingComponent.isPresent) {
            dao.upsertComponent(existingComponent.copy(isPresent = true, isEnabled = true))
        }
        val old = dao.getVisit(packageName, canonical)
        if (old != null && timestamp < old.lastEnteredAtMillis) return@withLock
        val effectiveSession = sessionId ?: old?.sessionId
        dao.upsertVisit(ActivityVisitEntity(packageName, canonical,
            firstEnteredAtMillis = if (old != null && old.sessionId == effectiveSession) old.firstEnteredAtMillis else timestamp,
            lastEnteredAtMillis = timestamp, source = source, sessionId = effectiveSession))
    }
}

class UnverifiedActivityException : IllegalStateException("Activity declaration not verified")
