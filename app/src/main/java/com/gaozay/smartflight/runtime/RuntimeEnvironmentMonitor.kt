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
import android.provider.Settings
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
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

    @Volatile
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    @Volatile
    private var radioReceiver: BroadcastReceiver? = null
    @Volatile
    private var changeListener: (() -> Unit)? = null

    suspend fun refreshSnapshot() {
        applySnapshot()
    }

    fun register(onNetworkChanged: () -> Unit) {
        changeListener = onNetworkChanged
        if (networkCallback == null) {
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    notifyChanged(this)
                }

                override fun onLost(network: Network) {
                    notifyChanged(this)
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    notifyChanged(this)
                }
            }
            networkCallback = callback
            connectivityManager.registerDefaultNetworkCallback(callback)
        }
        if (radioReceiver == null) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (radioReceiver === this) {
                        changeListener?.invoke()
                    }
                }
            }
            radioReceiver = receiver
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

    fun unregister() {
        changeListener = null
        val previousCallback = networkCallback
        val previousReceiver = radioReceiver
        networkCallback = null
        radioReceiver = null
        previousCallback?.let { runCatching { connectivityManager.unregisterNetworkCallback(it) } }
        previousReceiver?.let { runCatching { context.unregisterReceiver(it) } }
    }

    // Callbacks only enqueue work. Updating here would erase the coordinator's
    // previous snapshot before it can check for an unexpected external change.
    private fun notifyChanged(callback: ConnectivityManager.NetworkCallback) {
        if (networkCallback === callback) {
            changeListener?.invoke()
        }
    }

    private suspend fun applySnapshot() {
        val airplaneModeEnabled = Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.AIRPLANE_MODE_ON,
            0,
        ) != 0
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
                isAirplaneModeEnabled = airplaneModeEnabled,
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
