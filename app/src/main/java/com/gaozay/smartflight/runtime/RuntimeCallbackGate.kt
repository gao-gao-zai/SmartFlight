package com.gaozay.smartflight.runtime

/** A late callback can only call its own listener, never a replacement listener. */
internal class RuntimeCallbackGate {
    class Registration(val listener: () -> Unit)
    @Volatile private var current: Registration? = null
    fun register(listener: () -> Unit): Registration = Registration(listener).also { current = it }
    fun clear() { current = null }
    fun dispatch(registration: Registration) {
        if (current === registration) registration.listener()
    }
}
