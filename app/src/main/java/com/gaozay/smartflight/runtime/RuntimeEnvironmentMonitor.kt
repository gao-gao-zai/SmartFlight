package com.gaozay.smartflight.runtime

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuntimeEnvironmentMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val runtimeStatusRepository: RuntimeStatusRepository,
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var radioReceiver: BroadcastReceiver? = null

    suspend fun refreshSnapshot() {
        applySnapshot(
            airplaneModeEnabled = null,
        )
    }

    fun register(scope: CoroutineScope) {
        if (networkCallback == null) {
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    scope.launch { applySnapshot() }
                }

                override fun onLost(network: Network) {
                    scope.launch { applySnapshot() }
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    scope.launch { applySnapshot() }
                }
            }.also { callback ->
                connectivityManager.registerDefaultNetworkCallback(callback)
            }
        }
        if (radioReceiver == null) {
            radioReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val airplaneModeEnabled = when (intent?.action) {
                        Intent.ACTION_AIRPLANE_MODE_CHANGED -> intent.getBooleanExtra("state", false)
                        else -> null
                    }
                    scope.launch {
                        applySnapshot(airplaneModeEnabled = airplaneModeEnabled)
                    }
                }
            }.also { receiver ->
                ContextCompat.registerReceiver(
                    context,
                    receiver,
                    IntentFilter().apply {
                        addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
                        addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                        addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
                    },
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )
            }
        }
    }

    fun unregister() {
        networkCallback?.let(connectivityManager::unregisterNetworkCallback)
        networkCallback = null
        radioReceiver?.let {
            runCatching { context.unregisterReceiver(it) }
        }
        radioReceiver = null
    }

    private suspend fun applySnapshot(
        airplaneModeEnabled: Boolean? = null,
    ) {
        val wifiConnected = isWifiConnected()
        val wifiEnabled = wifiManager.wifiState == WifiManager.WIFI_STATE_ENABLED
        val bluetoothReadable = canReadBluetoothState()
        val bluetoothEnabled = if (bluetoothReadable) {
            bluetoothAdapter?.state == BluetoothAdapter.STATE_ON
        } else {
            runtimeStatusRepository.snapshotState().isBluetoothEnabled
        }
        runtimeStatusRepository.updateSnapshot { snapshot ->
            snapshot.copy(
                isAirplaneModeEnabled = airplaneModeEnabled ?: snapshot.isAirplaneModeEnabled,
                isWifiConnected = wifiConnected,
                isWifiEnabled = wifiEnabled,
                isBluetoothEnabled = bluetoothEnabled,
                isBluetoothStateReadable = bluetoothReadable,
                updatedAtMillis = System.currentTimeMillis(),
            )
        }
    }

    private fun isWifiConnected(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun canReadBluetoothState(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
