package com.gaozay.smartflight

import com.gaozay.smartflight.apps.AppTypeFilter
import com.gaozay.smartflight.apps.InstalledAppRepository
import com.gaozay.smartflight.data.local.entity.InstalledAppEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AppsManagementControllerTest {
    @Test
    fun clearAdvancedFiltersRestoresDefaultValues() = runTest {
        val controller = AppsManagementController(FakeInstalledAppRepository())

        controller.updateAppTypeFilter(AppTypeFilter.System)
        controller.clearAppAdvancedFilters()

        assertEquals(AppTypeFilter.User, controller.appFilterStateFlow().first().typeFilter)
    }

    @Test
    fun refreshInstalledAppsUpdatesSuccessSummaryAndScanningFlag() = runTest {
        val controller = AppsManagementController(FakeInstalledAppRepository(refreshCount = 7))

        controller.refreshInstalledApps()

        assertFalse(controller.appScanning.value)
        assertEquals("上次扫描发现 7 个用户应用", controller.appLastScanSummary.value)
    }

    @Test
    fun refreshInstalledAppsUpdatesFailureSummaryAndScanningFlag() = runTest {
        val controller = AppsManagementController(
            FakeInstalledAppRepository(refreshFailure = IllegalStateException("boom")),
        )

        controller.refreshInstalledApps()

        assertFalse(controller.appScanning.value)
        assertEquals("扫描失败：boom", controller.appLastScanSummary.value)
    }

    private class FakeInstalledAppRepository(
        private val refreshCount: Int = 0,
        private val refreshFailure: Throwable? = null,
    ) : InstalledAppRepository {
        override fun observeApps(): Flow<List<InstalledAppEntity>> = MutableStateFlow(emptyList())
        override fun observeAppCount(): Flow<Int> = MutableStateFlow(0)
        override suspend fun getApp(packageName: String): InstalledAppEntity? = null
        override suspend fun refreshInstalledApps(): Int {
            refreshFailure?.let { throw it }
            return refreshCount
        }
        override suspend fun upsertApps(apps: List<InstalledAppEntity>) = Unit
        override suspend fun setManualOnline(packageName: String) = Unit
        override suspend fun setManualOffline(packageName: String) = Unit
        override suspend fun resetToDefault(packageName: String) = Unit
    }
}
