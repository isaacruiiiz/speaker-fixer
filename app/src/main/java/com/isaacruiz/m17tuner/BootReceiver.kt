package com.isaacruiz.m17tuner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Arranca el servicio al encender el móvil (o al actualizar la app) si está activado. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return
        if (Prefs(context).autoStart) {
            runCatching { EnhancerService.start(context) }
        }
    }
}
