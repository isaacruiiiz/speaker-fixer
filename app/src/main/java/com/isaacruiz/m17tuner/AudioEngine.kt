package com.isaacruiz.m17tuner

import android.media.audiofx.DynamicsProcessing

/**
 * Aplica un ecualizador de 9 bandas + limitador con DynamicsProcessing.
 * Sesión 0 = mezcla global de salida. Otras sesiones = una app concreta.
 */
class AudioEngine {

    private val effects = LinkedHashMap<Int, DynamicsProcessing>()
    var lastError: String? = null
        private set

    val sessions: Set<Int> get() = effects.keys

    fun attach(session: Int): Boolean {
        if (effects.containsKey(session)) return true
        return try {
            val dp = DynamicsProcessing(Int.MAX_VALUE, session, buildConfig())
            effects[session] = dp
            lastError = null
            true
        } catch (e: Throwable) {
            lastError = "No se pudo crear el efecto (sesión $session): ${e.message ?: e.javaClass.simpleName}"
            false
        }
    }

    fun detach(session: Int) {
        effects.remove(session)?.let { runCatching { it.release() } }
    }

    fun releaseAll() {
        effects.values.forEach { runCatching { it.release() } }
        effects.clear()
    }

    fun apply(s: Snapshot, active: Boolean) {
        val dead = mutableListOf<Int>()
        for ((session, dp) in effects) {
            if (!applyTo(dp, s, active)) dead.add(session)
        }
        // Si otra app tomó el control del efecto, lo recreamos.
        for (session in dead) {
            detach(session)
            if (attach(session)) effects[session]?.let { applyTo(it, s, active) }
        }
    }

    private fun applyTo(dp: DynamicsProcessing, s: Snapshot, active: Boolean): Boolean {
        return try {
            if (!dp.hasControl()) return false
            val gains = bandGains(s)
            for (i in CUTOFFS.indices) {
                dp.setPreEqBandAllChannelsTo(i, DynamicsProcessing.EqBand(true, CUTOFFS[i], gains[i]))
            }
            // Bajamos la entrada para dejar margen a los graves y evitar saturar.
            val maxBoost = (gains.maxOrNull() ?: 0f).coerceAtLeast(0f)
            val preGain = if (s.limiterOn) -maxBoost * 0.5f else -maxBoost
            dp.setInputGainAllChannelsTo(preGain)
            dp.setLimiterAllChannelsTo(limiter(s.limiterOn))
            dp.setEnabled(active)
            true
        } catch (e: Throwable) {
            lastError = "Error aplicando el efecto: ${e.message ?: e.javaClass.simpleName}"
            false
        }
    }

    companion object {
        /** Frecuencia superior de cada banda, en Hz. */
        val CUTOFFS = floatArrayOf(60f, 120f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 20000f)

        /** Ganancia (dB) por banda según los interruptores activos. */
        fun bandGains(s: Snapshot): FloatArray {
            val g = FloatArray(CUTOFFS.size)
            if (s.bassOn) {
                val b = s.bassDb.toFloat()
                g[0] += b          // < 60 Hz
                g[1] += b          // 60–120 Hz
                g[2] += b * 0.5f   // 120–250 Hz
                g[3] += b * 0.15f  // 250–500 Hz
            }
            if (s.tinOn) {
                val t = s.tinDb.toFloat()
                g[4] -= t * 0.3f   // 500 Hz–1 kHz
                g[5] -= t * 0.8f   // 1–2 kHz
                g[6] -= t          // 2–4 kHz (la zona del "sonido de lata")
                g[7] -= t * 0.4f   // 4–8 kHz
            }
            return g
        }

        private fun limiter(enabled: Boolean) = DynamicsProcessing.Limiter(
            true,      // inUse
            enabled,   // enabled
            0,         // linkGroup
            1f,        // attack ms
            60f,       // release ms
            10f,       // ratio
            -1f,       // threshold dB
            0f,        // postGain dB
        )

        private fun buildConfig(): DynamicsProcessing.Config {
            val builder = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                2,                 // canales
                true, CUTOFFS.size, // pre-EQ
                false, 0,          // compresor multibanda
                false, 0,          // post-EQ
                true,              // limitador
            )
            val eq = DynamicsProcessing.Eq(true, true, CUTOFFS.size)
            for (i in CUTOFFS.indices) {
                eq.setBand(i, DynamicsProcessing.EqBand(true, CUTOFFS[i], 0f))
            }
            builder.setPreEqAllChannelsTo(eq)
            builder.setLimiterAllChannelsTo(limiter(true))
            return builder.build()
        }
    }
}
