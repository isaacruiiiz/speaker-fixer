package com.isaacruiz.m17tuner

import android.content.Context
import android.content.SharedPreferences

/** Valores que necesita el motor de audio para aplicar el ecualizador. */
data class Snapshot(
    val bassOn: Boolean,
    val bassDb: Int,
    val tinOn: Boolean,
    val tinDb: Int,
    val limiterOn: Boolean,
)

/** Todos los ajustes de la app, guardados en SharedPreferences. */
class Prefs(context: Context) {
    val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun b(key: String, def: Boolean) = sp.getBoolean(key, def)
    private fun i(key: String, def: Int) = sp.getInt(key, def)
    private fun putB(key: String, v: Boolean) = sp.edit().putBoolean(key, v).apply()
    private fun putI(key: String, v: Int) = sp.edit().putInt(key, v).apply()

    /** Interruptor general A/B: true = mejoras, false = sonido original. */
    var master: Boolean
        get() = b(K_MASTER, true)
        set(v) = putB(K_MASTER, v)

    var bassOn: Boolean
        get() = b(K_BASS_ON, true)
        set(v) = putB(K_BASS_ON, v)
    var bassDb: Int
        get() = i(K_BASS_DB, 6)
        set(v) = putI(K_BASS_DB, v)

    var tinOn: Boolean
        get() = b(K_TIN_ON, true)
        set(v) = putB(K_TIN_ON, v)
    var tinDb: Int
        get() = i(K_TIN_DB, 4)
        set(v) = putI(K_TIN_DB, v)

    var limiterOn: Boolean
        get() = b(K_LIMITER_ON, true)
        set(v) = putB(K_LIMITER_ON, v)

    var volumeOn: Boolean
        get() = b(K_VOLUME_ON, false)
        set(v) = putB(K_VOLUME_ON, v)
    var volumePct: Int
        get() = i(K_VOLUME_PCT, 100)
        set(v) = putI(K_VOLUME_PCT, v)

    /** Aplicar solo cuando el altavoz está conectado (si no, en cualquier salida). */
    var onlySpeaker: Boolean
        get() = b(K_ONLY_SPEAKER, true)
        set(v) = putB(K_ONLY_SPEAKER, v)
    var deviceFilter: String
        get() = sp.getString(K_FILTER, "M17") ?: "M17"
        set(v) = sp.edit().putString(K_FILTER, v).apply()

    /** false = efecto global (toda la salida); true = por app (Spotify, YouTube Music...). */
    var perAppMode: Boolean
        get() = b(K_PER_APP, false)
        set(v) = putB(K_PER_APP, v)

    var autoStart: Boolean
        get() = b(K_AUTOSTART, true)
        set(v) = putB(K_AUTOSTART, v)

    fun checked(key: String) = b(key, false)
    fun setChecked(key: String, v: Boolean) = putB(key, v)

    fun snapshot() = Snapshot(bassOn, bassDb, tinOn, tinDb, limiterOn)

    companion object {
        const val FILE = "m17tuner"
        const val K_MASTER = "master"
        const val K_BASS_ON = "bass_on"
        const val K_BASS_DB = "bass_db"
        const val K_TIN_ON = "tin_on"
        const val K_TIN_DB = "tin_db"
        const val K_LIMITER_ON = "limiter_on"
        const val K_VOLUME_ON = "volume_on"
        const val K_VOLUME_PCT = "volume_pct"
        const val K_ONLY_SPEAKER = "only_speaker"
        const val K_FILTER = "device_filter"
        const val K_PER_APP = "per_app"
        const val K_AUTOSTART = "autostart"
    }
}
