package com.gaozay.smartflight.runtime

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.util.Log
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

interface RuntimeEnvironmentSource {
    suspend fun refreshSnapshot()
    fun register(onNetworkChanged: () -> Unit)
    fun unregister()
}

@Singleton
class RuntimeEnvironmentMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val runtimeStatusRepository: RuntimeStatusRepository,
) : RuntimeEnvironmentSource {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    @Volatile
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    @Volatile
    private var radioReceiver: BroadcastReceiver? = null
    private val callbackGate = RuntimeCallbackGate()
    private var mobileDataObserver: ContentObserver? = null

    override suspend fun refreshSnapshot() {
        applySnapshot()
    }

    @Synchronized
    override fun register(onNetworkChanged: () -> Unit) {
        unregister()
        val registration = callbackGate.register(onNetworkChanged)
        if (networkCallback == null) {
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    callbackGate.dispatch(registration)
                }

                override fun onLost(network: Network) {
                    callbackGate.dispatch(registration)
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    callbackGate.dispatch(registration)
                }
            }
            networkCallback = callback
            try {
                connectivityManager.registerDefaultNetworkCallback(callback)
            } catch (error: RuntimeException) {
                unregister()
                throw error
            }
        }
        if (radioReceiver == null) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    callbackGate.dispatch(registration)
                }
            }
            radioReceiver = receiver
            try {
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
            } catch (error: RuntimeException) {
                unregister()
                throw error
            }
        }

        // Mobile data can change while Wi-Fi remains the default network.
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { callbackGate.dispatch(registration) }
        }
        mobileDataObserver = observer
        try {
            context.contentResolver.registerContentObserver(Settings.Global.getUriFor("mobile_data"), false, observer)
        } catch (error: RuntimeException) {
            mobileDataObserver = null
            Log.w("SmartFlightRuntime", "Mobile-data state observer unavailable", error)
        }
    }

    @Synchronized
    override fun unregister() {
        callbackGate.clear()
        mobileDataObserver?.let { runCatching { context.contentResolver.unregisterContentObserver(it) } }
        mobileDataObserver = null
        val previousCallback = networkCallback
        val previousReceiver = radioReceiver
        networkCallback = null
        radioReceiver = null
        previousCallback?.let { runCatching { connectivityManager.unregisterNetworkCallback(it) } }
        previousReceiver?.let { runCatching { context.unregisterReceiver(it) } }
    }

    // Callbacks enqueue work; only the coordinator refreshes the snapshot.
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
