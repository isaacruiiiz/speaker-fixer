package com.isaacruiz.m17tuner

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/**
 * Servicio en primer plano que mantiene los efectos vivos mientras escuchas música,
 * detecta cuándo se conecta el altavoz y permite el cambio A/B desde la notificación.
 */
class EnhancerService : Service() {

    private lateinit var prefs: Prefs
    private lateinit var audio: AudioManager
    private val engine = AudioEngine()
    private val handler = Handler(Looper.getMainLooper())

    private var speakerConnected = false
    private var savedVolume: Int? = null
    private var currentPerAppMode: Boolean? = null

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == Prefs.K_PER_APP || key == Prefs.K_FILTER) {
            setupMode()
            refreshDevices()
        }
        applyAll()
    }

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            refreshDevices(); applyAll()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            refreshDevices(); applyAll()
        }
    }

    /** Apps de música que anuncian su sesión de audio (Spotify, YouTube Music, etc.). */
    private val sessionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!prefs.perAppMode) return
            val session = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, AudioManager.ERROR)
            if (session <= 0) return
            when (intent.action) {
                AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> {
                    engine.attach(session)
                    applyAll()
                }
                AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> {
                    engine.detach(session)
                    applyAll()
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        audio = getSystemService(AudioManager::class.java)
        createChannel()
        goForeground()

        val filter = IntentFilter().apply {
            addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(sessionReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(sessionReceiver, filter)
        }
        audio.registerAudioDeviceCallback(deviceCallback, handler)
        prefs.sp.registerOnSharedPreferenceChangeListener(prefListener)

        EngineState.running = true
        setupMode()
        refreshDevices()
        applyAll()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> prefs.master = !prefs.master // el listener aplica el cambio
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        prefs.sp.unregisterOnSharedPreferenceChangeListener(prefListener)
        audio.unregisterAudioDeviceCallback(deviceCallback)
        runCatching { unregisterReceiver(sessionReceiver) }
        restoreVolume()
        engine.releaseAll()
        EngineState.running = false
        EngineState.effectsActive = false
        EngineState.attachedTo = ""
        EngineState.changed()
        super.onDestroy()
    }

    // ---------------------------------------------------------------------

    private fun setupMode() {
        val perApp = prefs.perAppMode
        if (perApp == currentPerAppMode) return
        currentPerAppMode = perApp
        engine.releaseAll()
        if (!perApp) engine.attach(0) // mezcla global
    }

    private fun refreshDevices() {
        val filter = prefs.deviceFilter.trim()
        val names = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.type in BT_TYPES }
            .map { it.productName?.toString().orEmpty() }
            .filter { it.isNotBlank() }
            .distinct()
        EngineState.bluetoothOutputs = names
        val connected = filter.isNotEmpty() && names.any { it.contains(filter, ignoreCase = true) }
        if (!connected && speakerConnected) {
            // Android guarda el volumen por dispositivo; al desconectar no tocamos nada.
            savedVolume = null
        }
        speakerConnected = connected
        EngineState.speakerConnected = connected
    }

    private fun applyAll() {
        val active = prefs.master && (!prefs.onlySpeaker || speakerConnected)
        engine.apply(prefs.snapshot(), active)
        applyVolume(active && prefs.volumeOn)

        EngineState.effectsActive = active && engine.sessions.isNotEmpty()
        EngineState.attachedTo = describeSessions()
        EngineState.lastError = engine.lastError
        EngineState.changed()
        updateNotification(active)
    }

    private fun applyVolume(wanted: Boolean) {
        if (wanted) {
            if (savedVolume != null) return // ya aplicado; respetamos si luego lo cambias a mano
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            savedVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            val target = (max * prefs.volumePct / 100f).toInt().coerceIn(1, max)
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        } else {
            restoreVolume()
        }
    }

    private fun restoreVolume() {
        val v = savedVolume ?: return
        savedVolume = null
        if (speakerConnected) runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0) }
    }

    private fun describeSessions(): String {
        val s = engine.sessions
        return when {
            s.isEmpty() && prefs.perAppMode -> "Esperando a que una app de música abra su sesión"
            s.isEmpty() -> "Sin efecto"
            s.contains(0) -> "Global (toda la salida de audio)"
            else -> "Por app: ${s.size} sesión(es)"
        }
    }

    // --- Notificación ----------------------------------------------------

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        val ch = NotificationChannel(CHANNEL, "Estado de M17 Tuner", NotificationManager.IMPORTANCE_LOW)
        ch.setShowBadge(false)
        nm.createNotificationChannel(ch)
    }

    private fun buildNotification(active: Boolean): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val toggle = PendingIntent.getService(
            this, 1, Intent(this, EnhancerService::class.java).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 2, Intent(this, EnhancerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val icon = Icon.createWithResource(this, R.drawable.ic_stat)
        val title = when {
            !prefs.master -> "Sonido ORIGINAL"
            active -> "Mejoras ACTIVADAS"
            else -> "Esperando al altavoz…"
        }
        val text = if (speakerConnected) "Altavoz conectado" else "Altavoz no conectado"
        val toggleLabel = if (prefs.master) "Oír original" else "Activar mejoras"

        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(icon, toggleLabel, toggle).build())
            .addAction(Notification.Action.Builder(icon, "Detener", stop).build())
            .build()
    }

    private fun goForeground() {
        val n = buildNotification(false)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun updateNotification(active: Boolean) {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(active))
    }

    companion object {
        const val ACTION_TOGGLE = "com.isaacruiz.m17tuner.TOGGLE"
        const val ACTION_STOP = "com.isaacruiz.m17tuner.STOP"
        private const val CHANNEL = "estado"
        private const val NOTIF_ID = 17

        private val BT_TYPES = setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
        )

        fun start(context: Context) {
            context.startForegroundService(Intent(context, EnhancerService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, EnhancerService::class.java))
        }
    }
}
