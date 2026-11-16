package com.isaacruiz.m17tuner

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

@SuppressLint("UseSwitchCompatOrMaterialCode", "SetTextI18n")
class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var root: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var abButton: Button
    private lateinit var serviceButton: Button
    private val stateListener: () -> Unit = { refreshStatus() }

    private val accent = Color.parseColor("#39D98A")
    private val warn = Color.parseColor("#FF8A3D")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        val scroll = ScrollView(this).apply { fitsSystemWindows = true }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp, 16.dp, 20.dp, 32.dp)
        }
        scroll.addView(root, MATCH_PARENT, WRAP_CONTENT)
        setContentView(scroll)

        buildUi()

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        if (!EngineState.running) EnhancerService.start(this)
    }

    override fun onResume() {
        super.onResume()
        EngineState.addListener(stateListener)
        refreshStatus()
    }

    override fun onPause() {
        EngineState.removeListener(stateListener)
        super.onPause()
    }

    // ---------------------------------------------------------------------

    private fun buildUi() {
        root.addView(TextView(this).apply {
            text = "M17 Tuner"
            textSize = 28f
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(note("Mejoras de sonido para el Monster M17 con el Pixel. Cada mejora se activa por separado y el botón grande las apaga todas para comparar."))

        // --- Estado ---
        statusText = TextView(this).apply {
            textSize = 14f
            setPadding(14.dp, 12.dp, 14.dp, 12.dp)
            background = card()
        }
        root.addView(statusText, lp(top = 12))

        // --- A/B ---
        abButton = Button(this).apply {
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            minHeight = 72.dp
            setOnClickListener { prefs.master = !prefs.master; refreshStatus() }
        }
        root.addView(abButton, lp(top = 16))

        val holdButton = Button(this).apply {
            text = "Mantén pulsado para oír el ORIGINAL"
            var before = true
            setOnTouchListener { v, ev ->
                when (ev.action) {
                    MotionEvent.ACTION_DOWN -> { before = prefs.master; prefs.master = false; refreshStatus() }
                    MotionEvent.ACTION_UP -> { prefs.master = before; refreshStatus(); v.performClick() }
                    MotionEvent.ACTION_CANCEL -> { prefs.master = before; refreshStatus() }
                }
                true
            }
        }
        root.addView(holdButton, lp(top = 6))

        // --- Mejoras automáticas ---
        header("Mejoras automáticas")

        toggleWithSlider(
            title = "Más graves",
            desc = "Refuerza la zona de 60–250 Hz, que es lo que más se echa en falta en un altavoz pequeño.",
            isOn = { prefs.bassOn }, setOn = { prefs.bassOn = it },
            min = 0, max = 12, value = { prefs.bassDb }, setValue = { prefs.bassDb = it },
            unit = "dB",
        )
        toggleWithSlider(
            title = "Quitar sonido de lata",
            desc = "Recorta los medios-agudos (1–5 kHz), que es donde suena 'enlatado' y chillón.",
            isOn = { prefs.tinOn }, setOn = { prefs.tinOn = it },
            min = 0, max = 10, value = { prefs.tinDb }, setValue = { prefs.tinDb = it },
            unit = "dB",
        )
        toggleWithSlider(
            title = "Limitador anti-distorsión",
            desc = "Evita que el altavoz sature con los graves subidos y permite más volumen limpio.",
            isOn = { prefs.limiterOn }, setOn = { prefs.limiterOn = it },
        )
        toggleWithSlider(
            title = "Volumen del móvil al conectar",
            desc = "Pone el volumen multimedia del móvil al nivel elegido cuando se conecta el altavoz (recomendado 100 % y regular con el altavoz). Al apagarlo vuelve al volumen anterior.",
            isOn = { prefs.volumeOn }, setOn = { prefs.volumeOn = it },
            min = 10, max = 100, value = { prefs.volumePct }, setValue = { prefs.volumePct = it },
            unit = "%",
        )

        // --- Ajustes ---
        header("Ajustes")
        simpleSwitch("Aplicar solo con el altavoz conectado", { prefs.onlySpeaker }, { prefs.onlySpeaker = it })
        root.addView(note("Nombre del altavoz a detectar (parte del nombre Bluetooth):"), lp(top = 6))
        root.addView(EditText(this).apply {
            setText(prefs.deviceFilter)
            isSingleLine = true
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { prefs.deviceFilter = s?.toString().orEmpty() }
            })
        }, lp())
        simpleSwitch(
            "Modo por app (usar si en modo global no notas cambio)",
            { prefs.perAppMode }, { prefs.perAppMode = it },
        )
        root.addView(note("En modo por app el efecto se engancha a Spotify, YouTube Music, etc. cuando empiezan a sonar. Si no notas nada, pausa y vuelve a dar a play."))
        simpleSwitch("Iniciar al encender el móvil", { prefs.autoStart }, { prefs.autoStart = it })

        serviceButton = Button(this).apply {
            setOnClickListener {
                if (EngineState.running) EnhancerService.stop(this@MainActivity)
                else EnhancerService.start(this@MainActivity)
                postDelayed({ refreshStatus() }, 300)
            }
        }
        root.addView(serviceButton, lp(top = 8))

        // --- Manual ---
        header("Ajustes que tienes que hacer a mano")
        root.addView(note("Android no deja que una app normal (sin root) cambie las opciones de desarrollador ni los perfiles Bluetooth. Esta lista es para que no se te olvide ninguno; marca los que ya tengas."))

        manualItem("chk_calls", "Ajustes Bluetooth → ⚙️ del M17 → desactivar \"Llamadas telefónicas\"")
        manualItem("chk_offload", "Opciones de desarrollador → \"Inhabilitar descarga de hardware A2DP\" (reiniciar después)")
        manualItem("chk_le", "Opciones de desarrollador → \"Inhabilitar Bluetooth LE Audio\"")
        manualItem("chk_absvol", "Opciones de desarrollador → \"Inhabilitar volumen absoluto\" (reiniciar después)")
        manualItem("chk_rate", "Opciones de desarrollador → Frecuencia de muestreo 44,1 kHz y 16 bits por muestra (con el altavoz conectado)")

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(Button(this).apply {
            text = "Bluetooth"
            setOnClickListener { openSettings(Settings.ACTION_BLUETOOTH_SETTINGS) }
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        row.addView(Button(this).apply {
            text = "Opciones de desarrollador"
            setOnClickListener { openSettings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS) }
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1.6f))
        root.addView(row, lp(top = 8))

        header("Más calidad todavía")
        root.addView(note("Si el altavoz tiene entrada AUX, un adaptador USB-C a jack 3,5 mm (con DAC) evita el Bluetooth por completo. En Spotify pon la calidad en \"Muy alta\"."))
    }

    private fun refreshStatus() {
        if (!::statusText.isInitialized) return
        val running = EngineState.running
        val outs = EngineState.bluetoothOutputs
        val sb = StringBuilder()
        sb.append(if (running) "● Servicio en marcha" else "○ Servicio parado").append('\n')
        sb.append(
            if (EngineState.speakerConnected) "✓ Altavoz detectado"
            else "✗ Altavoz \"${prefs.deviceFilter}\" no conectado"
        ).append('\n')
        if (outs.isNotEmpty()) sb.append("Salida Bluetooth: ").append(outs.joinToString()).append('\n')
        if (running) sb.append("Efecto: ").append(EngineState.attachedTo).append('\n')
        EngineState.lastError?.let { sb.append("⚠ ").append(it).append('\n') }
        statusText.text = sb.toString().trimEnd()

        val on = prefs.master
        abButton.text = if (on) "MEJORAS ACTIVADAS\n(toca para oír el original)" else "SONIDO ORIGINAL\n(toca para activar las mejoras)"
        abButton.background = GradientDrawable().apply {
            cornerRadius = 14.dp.toFloat()
            setColor(if (on) accent else warn)
        }
        abButton.setTextColor(Color.parseColor("#101114"))

        serviceButton.text = if (running) "Detener servicio" else "Iniciar servicio"
    }

    // --- Constructores de vistas ------------------------------------------

    private fun header(text: String) {
        root.addView(TextView(this).apply {
            this.text = text
            textSize = 19f
            setTypeface(typeface, Typeface.BOLD)
        }, lp(top = 28))
    }

    private fun note(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13.5f
        alpha = 0.75f
    }

    private fun simpleSwitch(title: String, get: () -> Boolean, set: (Boolean) -> Unit) {
        root.addView(Switch(this).apply {
            text = title
            textSize = 15f
            isChecked = get()
            setOnCheckedChangeListener { _, c -> set(c) }
        }, lp(top = 10))
    }

    private fun toggleWithSlider(
        title: String,
        desc: String,
        isOn: () -> Boolean,
        setOn: (Boolean) -> Unit,
        min: Int = 0,
        max: Int = 0,
        value: (() -> Int)? = null,
        setValue: ((Int) -> Unit)? = null,
        unit: String = "",
    ) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14.dp, 10.dp, 14.dp, 12.dp)
            background = card()
        }
        val sw = Switch(this).apply {
            text = title
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            isChecked = isOn()
        }
        box.addView(sw, MATCH_PARENT, WRAP_CONTENT)
        box.addView(note(desc), lp(top = 2))

        if (value != null && setValue != null) {
            val label = TextView(this).apply { textSize = 14f }
            fun show(v: Int) { label.text = "Intensidad: $v $unit" }
            val bar = SeekBar(this).apply {
                this.min = min
                this.max = max
                progress = value()
                isEnabled = isOn()
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                        show(p)
                        if (fromUser) setValue(p)
                    }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {}
                })
            }
            show(value())
            box.addView(label, lp(top = 6))
            box.addView(bar, lp())
            sw.setOnCheckedChangeListener { _, c -> setOn(c); bar.isEnabled = c }
        } else {
            sw.setOnCheckedChangeListener { _, c -> setOn(c) }
        }
        root.addView(box, lp(top = 10))
    }

    private fun manualItem(key: String, text: String) {
        root.addView(CheckBox(this).apply {
            this.text = text
            textSize = 14f
            isChecked = prefs.checked(key)
            setOnCheckedChangeListener { _, c -> prefs.setChecked(key, c) }
        }, lp(top = 4))
    }

    private fun openSettings(action: String) {
        try {
            startActivity(Intent(action))
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Activa antes las opciones de desarrollador: Ajustes → Información del teléfono → pulsa 7 veces en Número de compilación",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun card() = GradientDrawable().apply {
        cornerRadius = 14.dp.toFloat()
        setColor(Color.argb(28, 128, 128, 128))
    }

    private fun lp(top: Int = 0) = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
        topMargin = top.dp
        gravity = Gravity.START
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}
