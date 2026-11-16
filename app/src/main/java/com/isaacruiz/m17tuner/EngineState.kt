package com.isaacruiz.m17tuner

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArrayList

/** Estado en vivo del servicio, para que la pantalla lo muestre. */
object EngineState {
    @Volatile var running = false
    @Volatile var bluetoothOutputs: List<String> = emptyList()
    @Volatile var speakerConnected = false
    @Volatile var effectsActive = false
    @Volatile var attachedTo: String = ""
    @Volatile var lastError: String? = null

    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun addListener(l: () -> Unit) { listeners.add(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }

    fun changed() {
        main.post { listeners.forEach { it() } }
    }
}
