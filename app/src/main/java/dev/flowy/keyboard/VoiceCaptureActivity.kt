package dev.flowy.keyboard

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.ResultReceiver
import android.speech.ModelDownloadListener
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
    private var modelActionButton: Button? = null
    private var modelDownloadRequested = false
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
        modelActionButton = Button(this).apply {
            text = "Descargar modelo local"
            visibility = android.view.View.GONE
            setOnClickListener { requestOrCheckModel() }
        }
        layout.addView(modelActionButton)
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
            checkSpanishSupport()
        } catch (_: UnsupportedOperationException) {
            statusView?.text = "Este dispositivo no tiene reconocimiento de voz local."
        } catch (_: SecurityException) {
            statusView?.text = "No se pudo acceder al micrófono."
        }
    }

    private fun checkSpanishSupport() {
        if (finished) return
        if (Build.VERSION.SDK_INT < 33) {
            startListening()
            return
        }
        modelActionButton?.visibility = android.view.View.GONE
        statusView?.text = "Revisando español disponible en el dispositivo…"
        try {
            speechRecognizer?.checkRecognitionSupport(
                recognitionIntent(),
                mainExecutor,
                object : RecognitionSupportCallback {
                    override fun onSupportResult(recognitionSupport: android.speech.RecognitionSupport) {
                        val installed = recognitionSupport.installedOnDeviceLanguages.any(::isSpanish)
                        val downloadable = recognitionSupport.supportedOnDeviceLanguages.any(::isSpanish)
                        if (installed) {
                            modelDownloadRequested = false
                            startListening()
                        } else if (downloadable) {
                            modelDownloadRequested = false
                            statusView?.text = "Falta el modelo local de español. Puedes descargarlo ahora."
                            modelActionButton?.apply {
                                text = "Descargar español sin conexión"
                                visibility = android.view.View.VISIBLE
                            }
                        } else {
                            statusView?.text = "El servicio de voz no ofrece español local. Revisa los idiomas sin conexión en los Ajustes de Android."
                        }
                    }

                    override fun onError(error: Int) {
                        statusView?.text = "No se pudo confirmar el modelo local. Revisa los idiomas sin conexión en Ajustes de Android."
                    }
                }
            )
        } catch (_: SecurityException) {
            statusView?.text = "No se pudo comprobar el modelo local de español."
        }
    }

    private fun isSpanish(languageTag: String) =
        Locale.forLanguageTag(languageTag).language == "es"

    private fun requestOrCheckModel() {
        if (Build.VERSION.SDK_INT < 33 || finished) return
        if (modelDownloadRequested) {
            checkSpanishSupport()
            return
        }
        try {
            modelActionButton?.isEnabled = false
            statusView?.text = "Solicitando la descarga del modelo local de español…"
            if (Build.VERSION.SDK_INT >= 34) {
                speechRecognizer?.triggerModelDownload(
                    recognitionIntent(),
                    mainExecutor,
                    object : ModelDownloadListener {
                        override fun onProgress(completedPercent: Int) {
                            statusView?.text = "Descargando modelo local: $completedPercent%"
                        }

                        override fun onSuccess() {
                            modelDownloadRequested = false
                            statusView?.text = "Modelo descargado. Comprobando…"
                            modelActionButton?.isEnabled = true
                            checkSpanishSupport()
                        }

                        override fun onScheduled() {
                            modelDownloadRequested = true
                            statusView?.text = "Android dejó la descarga programada. Cuando termine, pulsa Revisar modelo."
                            modelActionButton?.apply {
                                text = "Revisar modelo"
                                isEnabled = true
                                visibility = android.view.View.VISIBLE
                            }
                        }

                        override fun onError(error: Int) {
                            modelDownloadRequested = false
                            statusView?.text = "Android no pudo descargar el modelo. Revisa la conexión e inténtalo de nuevo."
                            modelActionButton?.apply {
                                text = "Reintentar descarga"
                                isEnabled = true
                                visibility = android.view.View.VISIBLE
                            }
                        }
                    }
                )
            } else {
                speechRecognizer?.triggerModelDownload(recognitionIntent())
                modelDownloadRequested = true
                statusView?.text = "Solicitud enviada a Android. Cuando termine, pulsa Revisar modelo."
                modelActionButton?.apply {
                    text = "Revisar modelo"
                    isEnabled = true
                    visibility = android.view.View.VISIBLE
                }
            }
        } catch (_: RuntimeException) {
            modelDownloadRequested = false
            statusView?.text = "No se pudo solicitar la descarga. Revisa los idiomas sin conexión en Ajustes de Android."
            modelActionButton?.apply {
                text = "Reintentar descarga"
                isEnabled = true
                visibility = android.view.View.VISIBLE
            }
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
