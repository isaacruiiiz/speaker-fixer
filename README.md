# M17 Tuner

App Android (Pixel 9a, Android 10+) para que el Monster M17 suene mejor por Bluetooth.

## Qué hace sola
Cada mejora tiene su propio interruptor:

| Mejora | Qué hace |
|---|---|
| **Más graves** | Sube 60–250 Hz (0–12 dB) |
| **Quitar sonido de lata** | Baja 1–5 kHz (0–10 dB) |
| **Limitador anti-distorsión** | Evita que sature con los graves subidos |
| **Volumen del móvil al conectar** | Pone el volumen multimedia al % elegido al conectar el M17 |

- **Botón grande A/B**: apaga o enciende todas las mejoras a la vez.
- **"Mantén pulsado para oír el original"**: mientras lo pulsas suena sin mejoras; al soltar vuelven.
- La **notificación** también tiene el botón A/B, para comparar sin abrir la app.
- Solo actúa cuando detecta un altavoz Bluetooth con "M17" en el nombre (se puede cambiar).
- Se inicia sola al encender el móvil (se puede desactivar).

## Lo que no puede hacer (Android no lo permite sin root)
Desactivar "Llamadas telefónicas", la descarga de hardware A2DP, LE Audio, el volumen absoluto y la frecuencia de muestreo. La app incluye una lista de control con botones que abren esos ajustes.

## Modo global y modo por app
- **Global** (por defecto): el efecto se aplica a toda la salida de audio.
- **Por app**: úsalo si en modo global no notas ningún cambio. El efecto se engancha a Spotify, YouTube Music, etc. cuando anuncian su sesión de audio. Si no notas nada, pausa y vuelve a dar a play.

## Cómo instalarla

### Opción A: Android Studio (en el Mac)
1. Abre Android Studio y elige **File → Open** con esta carpeta. Espera a que termine el *Gradle sync*.
2. En el Pixel, activa *Depuración USB* en Opciones de desarrollador y conéctalo por cable.
3. Pulsa ▶ **Run**.

También puedes ir a **Build → Build APK(s)** y pasar el APK al móvil.

### Opción B: desde el móvil (GitHub Releases)
Cada vez que se sube código, GitHub Actions compila la app y la publica en **Releases → latest**.
1. Abre el repo en el móvil y entra en **Releases → "M17 Tuner (última versión)"**.
2. Descarga **M17Tuner.apk** y ábrelo (tendrás que permitir "instalar apps desconocidas").

## Primer uso
1. Abre la app y acepta las notificaciones.
2. Conecta el M17. Debe aparecer "✓ Altavoz detectado".
3. Pon música y prueba el botón A/B.
4. Ajusta las intensidades a tu gusto. Un buen punto de partida: graves 6 dB, lata 4 dB.
