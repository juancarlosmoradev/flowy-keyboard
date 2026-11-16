package dev.flowy.keyboard

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.ResultReceiver
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.RecognitionSupportCallback
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

class VoiceCaptureActivity : Activity() {
    private var speechRecognizer: SpeechRecognizer? = null
    private var statusView: TextView? = null
    private var resultReceiver: ResultReceiver? = null
    private var finished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        resultReceiver = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_RESULT_RECEIVER, ResultReceiver::class.java)
        } else {
            intent.getParcelableExtra(EXTRA_RESULT_RECEIVER)
        }
        showStatus("Preparando dictado local…")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            showStatus("El dictado local requiere Android 12 o posterior.")
            return
        }
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            showStatus("Este dispositivo no tiene reconocimiento de voz local.")
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO)
        } else {
            prepareRecognizer()
        }
    }

    private fun showStatus(message: String) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }
        statusView = TextView(this).apply {
            text = message
            textSize = 22f
            gravity = Gravity.CENTER
        }
        layout.addView(statusView)
        layout.addView(Button(this).apply {
            text = "Cancelar"
            setOnClickListener { finish() }
        })
        setContentView(layout)
    }

    private fun recognitionIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-CL")
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es-CL")
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }

    private fun prepareRecognizer() {
        if (finished) return
        try {
            speechRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this).also {
                it.setRecognitionListener(listener)
            }
            if (Build.VERSION.SDK_INT >= 33) {
                statusView?.text = "Revisando español disponible en el dispositivo…"
                speechRecognizer?.checkRecognitionSupport(
                    recognitionIntent(),
                    mainExecutor,
                    object : RecognitionSupportCallback {
                        override fun onSupportResult(recognitionSupport: android.speech.RecognitionSupport) {
                            val hasSpanish = recognitionSupport.installedOnDeviceLanguages.any {
                                Locale.forLanguageTag(it).language == "es"
                            }
                            if (hasSpanish) startListening()
                            else statusView?.text = "No hay un modelo local de español instalado."
                        }

                        override fun onError(error: Int) {
                            statusView?.text = "No se pudo confirmar el modelo local de español."
                        }
                    }
                )
            } else {
                startListening()
            }
        } catch (_: UnsupportedOperationException) {
            statusView?.text = "Este dispositivo no tiene reconocimiento de voz local."
        } catch (_: SecurityException) {
            statusView?.text = "No se pudo acceder al micrófono."
        }
    }

    private fun startListening() {
        if (finished) return
        statusView?.text = "Escuchando… habla ahora."
        try {
            speechRecognizer?.startListening(recognitionIntent())
        } catch (_: SecurityException) {
            statusView?.text = "No se pudo acceder al micrófono."
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            statusView?.text = "Escuchando… habla ahora."
        }

        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            statusView?.text = "Procesando en el dispositivo…"
        }

        override fun onError(error: Int) {
            if (!finished) {
                statusView?.text = when (error) {
                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "El modelo local no reconoce español (Chile)."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Falta permiso para usar el micrófono."
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No se detectó voz. Cierra y vuelve a intentarlo."
                    else -> "Falló el dictado local. Cierra y vuelve a intentarlo."
                }
            }
        }

        override fun onResults(results: Bundle?) {
            val transcript = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
            if (!transcript.isNullOrEmpty()) {
                finished = true
                resultReceiver?.send(RESULT_TRANSCRIPT, Bundle().apply {
                    putString(EXTRA_TRANSCRIPT, transcript)
                })
                finish()
            } else {
                statusView?.text = "No se reconoció texto. Cierra y vuelve a intentarlo."
            }
        }

        override fun onPartialResults(partialResults: Bundle?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_AUDIO) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                prepareRecognizer()
            } else {
                statusView?.text = "Permiso de micrófono denegado."
            }
        }
    }

    override fun onDestroy() {
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_RESULT_RECEIVER = "dev.flowy.keyboard.RESULT_RECEIVER"
        const val EXTRA_TRANSCRIPT = "dev.flowy.keyboard.TRANSCRIPT"
        const val RESULT_TRANSCRIPT = 10
        private const val REQUEST_AUDIO = 41
    }
}
