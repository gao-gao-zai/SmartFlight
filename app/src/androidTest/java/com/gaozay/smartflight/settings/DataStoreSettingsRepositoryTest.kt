package com.gaozay.smartflight.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataStoreSettingsRepositoryTest {
    @Test
    fun concurrentTransformsUseLatestPersistedSettingsWithoutLosingOtherFields() = runBlocking {
        val repository = DataStoreSettingsRepository(InstrumentationRegistry.getInstrumentation().targetContext)
        val original = repository.settings.first()
        try {
            repository.updateSettings { it.copy(screenOffDelaySeconds = 0, appExitDelaySeconds = 0) }
            coroutineScope {
                repeat(100) { index ->
                    launch(Dispatchers.Default) {
                        repository.updateSettings { current ->
                            if (index % 2 == 0) {
                                current.copy(screenOffDelaySeconds = current.screenOffDelaySeconds + 1)
                            } else {
                                current.copy(appExitDelaySeconds = current.appExitDelaySeconds + 1)
                            }
                        }
                    }
                }
            }
            assertEquals(
                original.copy(screenOffDelaySeconds = 50, appExitDelaySeconds = 50),
                repository.settings.first(),
            )
        } finally {
            repository.updateSettings { original }
        }
    }
}
