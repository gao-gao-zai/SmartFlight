package com.gaozay.smartflight.di

import android.content.Context
import androidx.room.Room
import com.gaozay.smartflight.apps.InstalledAppRepository
import com.gaozay.smartflight.apps.RoomInstalledAppRepository
import com.gaozay.smartflight.data.local.SmartFlightDatabase
import com.gaozay.smartflight.data.local.dao.ExecutionLogDao
import com.gaozay.smartflight.data.local.dao.InstalledAppDao
import com.gaozay.smartflight.logs.ExecutionLogRepository
import com.gaozay.smartflight.logs.RoomExecutionLogRepository
import com.gaozay.smartflight.permission.AdbBootstrapRepository
import com.gaozay.smartflight.permission.AccessRepository
import com.gaozay.smartflight.permission.DataStoreAdbBootstrapRepository
import com.gaozay.smartflight.permission.DataStoreRootAccessProbeRepository
import com.gaozay.smartflight.permission.DefaultAccessRepository
import com.gaozay.smartflight.permission.RootAccessProbeRepository
import com.gaozay.smartflight.runtime.DataStoreRuntimeStatusRepository
import com.gaozay.smartflight.runtime.ForegroundAppDetector
import com.gaozay.smartflight.runtime.ForegroundAppSource
import com.gaozay.smartflight.runtime.RuntimePromptNotifier
import com.gaozay.smartflight.runtime.RuntimeStatusRepository
import com.gaozay.smartflight.runtime.ToastRuntimePromptNotifier
import com.gaozay.smartflight.settings.DataStoreSettingsRepository
import com.gaozay.smartflight.settings.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideSmartFlightDatabase(
        @ApplicationContext context: Context,
    ): SmartFlightDatabase = Room.databaseBuilder(
        context,
        SmartFlightDatabase::class.java,
        "smartflight.db",
    ).addMigrations(SmartFlightDatabase.MIGRATION_1_2).build()

    @Provides
    fun provideInstalledAppDao(database: SmartFlightDatabase): InstalledAppDao =
        database.installedAppDao()

    @Provides
    fun provideExecutionLogDao(database: SmartFlightDatabase): ExecutionLogDao =
        database.executionLogDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindInstalledAppRepository(
        repository: RoomInstalledAppRepository,
    ): InstalledAppRepository

    @Binds
    @Singleton
    abstract fun bindExecutionLogRepository(
        repository: RoomExecutionLogRepository,
    ): ExecutionLogRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        repository: DataStoreSettingsRepository,
    ): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindRuntimeStatusRepository(
        repository: DataStoreRuntimeStatusRepository,
    ): RuntimeStatusRepository

    @Binds
    @Singleton
    abstract fun bindForegroundAppSource(
        detector: ForegroundAppDetector,
    ): ForegroundAppSource

    @Binds
    @Singleton
    abstract fun bindRuntimePromptNotifier(
        notifier: ToastRuntimePromptNotifier,
    ): RuntimePromptNotifier

    @Binds
    @Singleton
    abstract fun bindAccessRepository(
        repository: DefaultAccessRepository,
    ): AccessRepository

    @Binds
    @Singleton
    abstract fun bindAdbBootstrapRepository(
        repository: DataStoreAdbBootstrapRepository,
    ): AdbBootstrapRepository

    @Binds
    @Singleton
    abstract fun bindRootAccessProbeRepository(
        repository: DataStoreRootAccessProbeRepository,
    ): RootAccessProbeRepository
}
