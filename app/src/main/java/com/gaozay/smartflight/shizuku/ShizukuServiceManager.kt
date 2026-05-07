package com.gaozay.smartflight.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import com.gaozay.smartflight.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import rikka.shizuku.Shizuku
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class ShizukuServiceManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @Volatile
    private var commandService: IShizukuCommandService? = null

    suspend fun getOrBindService(): IShizukuCommandService? {
        val existing = commandService
        if (existing != null) {
            return existing
        }
        if (!Shizuku.pingBinder()) return null

        val args = Shizuku.UserServiceArgs(
            ComponentName(context.packageName, ShizukuCommandService::class.java.name),
        )
            .daemon(false)
            .tag("smartflight-shizuku-command")
            .version(BuildConfig.VERSION_CODE)
            .processNameSuffix("smartflight_shizuku")

        return suspendCancellableCoroutine { continuation ->
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    val binder = IShizukuCommandService.Stub.asInterface(service)
                    commandService = binder
                    continuation.resume(binder)
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    commandService = null
                }
            }

            runCatching {
                Shizuku.bindUserService(args, connection)
            }.onFailure {
                continuation.resume(null)
            }
        }
    }
}
