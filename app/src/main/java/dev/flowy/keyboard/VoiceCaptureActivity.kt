package dev.flowy.keyboard

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.ResultReceiver
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

class VoiceCaptureActivity : Activity() {
    private lateinit var statusView: TextView
    private lateinit var actionButton: Button
    private var resultReceiver: ResultReceiver? = null

    private val worker = Executors.newSingleThreadExecutor()
    private val cancelRequested = AtomicBoolean(false)
    private val stopRequested = AtomicBoolean(false)
    @Volatile private var activeConnection: HttpURLConnection? = null
    @Volatile private var activeRecorder: AudioRecord? = null
    @Volatile private var operationInProgress = false

    private val modelFile: File
        get() = File(filesDir, MODEL_FILE_NAME)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        resultReceiver = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_RESULT_RECEIVER, ResultReceiver::class.java)
        } else {
            intent.getParcelableExtra(EXTRA_RESULT_RECEIVER)
        }
        createContent()
        renderReadyState()
    }

    private fun createContent() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(getColor(R.color.flowy_surface))
        }
        statusView = TextView(this).apply {
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(getColor(R.color.flowy_text))
        }
        actionButton = Button(this).apply {
            textSize = 18f
            isSingleLine = true
            setOnClickListener { onPrimaryAction() }
        }
        val cancelButton = Button(this).apply {
            text = "Cancelar"
            textSize = 18f
            setOnClickListener { cancelAndFinish() }
        }
        layout.addView(statusView)
        layout.addView(actionButton)
        layout.addView(cancelButton)
        setContentView(layout)
    }

    private fun renderReadyState() {
        if (modelFile.isFile && modelFile.length() > MIN_MODEL_BYTES) {
            statusView.text = "Modelo Whisper local listo. La transcripción se procesa en este dispositivo."
            actionButton.text = "Iniciar dictado"
        } else {
            statusView.text = "Descarga una vez el modelo local de español. Requiere cerca de 32 MB."
            actionButton.text = "Descargar modelo local"
        }
    }

    private fun onPrimaryAction() {
        if (operationInProgress) return
        if (!modelFile.isFile || modelFile.length() <= MIN_MODEL_BYTES) {
            downloadModel()
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO)
        } else {
            startRecordingAndTranscription()
        }
    }

    private fun downloadModel() {
        if (operationInProgress) return
        operationInProgress = true
        cancelRequested.set(false)
        actionButton.isEnabled = false
        statusView.text = "Conectando para descargar el modelo Whisper local…"
        worker.execute {
            var connection: HttpURLConnection? = null
            val temporaryFile = File(filesDir, "$MODEL_FILE_NAME.part")
            try {
                connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20_000
                    readTimeout = 60_000
                    instanceFollowRedirects = true
                    setRequestProperty("Accept-Encoding", "identity")
                    connect()
                }
                activeConnection = connection
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw IllegalStateException("HTTP ${connection.responseCode}")
                }

                val totalBytes = connection.contentLength
                val digest = MessageDigest.getInstance("SHA-256")
                var downloaded = 0L
                var lastProgressAt = 0L
                connection.inputStream.use { input ->
                    FileOutputStream(temporaryFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (!cancelRequested.get()) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                            downloaded += count
                            val now = System.currentTimeMillis()
                            if (now - lastProgressAt >= 300) {
                                lastProgressAt = now
                                val percent = if (totalBytes > 0) {
                                    (downloaded * 100 / totalBytes).toInt().coerceIn(0, 100)
                                } else {
                                    -1
                                }
                                runOnUiThread {
                                    if (!isFinishing && !cancelRequested.get()) {
                                        statusView.text = if (percent >= 0) {
                                            "Descargando modelo local: $percent%"
                                        } else {
                                            "Descargados ${downloaded / (1024 * 1024)} MB…"
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (cancelRequested.get()) {
                    temporaryFile.delete()
                    return@execute
                }
                if (temporaryFile.length() <= MIN_MODEL_BYTES) {
                    throw IllegalStateException("Modelo incompleto")
                }
                val actualHash = digest.digest().joinToString("") {
                    "%02x".format(it.toInt() and 0xff)
                }
                if (!actualHash.equals(MODEL_SHA256, ignoreCase = true)) {
                    throw IllegalStateException("La verificación del modelo falló")
                }
                if (modelFile.exists()) modelFile.delete()
                if (!temporaryFile.renameTo(modelFile)) {
                    throw IllegalStateException("No se pudo guardar el modelo")
                }
                runOnUiThread {
                    operationInProgress = false
                    if (!isFinishing && !cancelRequested.get()) {
                        actionButton.isEnabled = true
                        renderReadyState()
                    }
                }
            } catch (error: Exception) {
                temporaryFile.delete()
                runOnUiThread {
                    operationInProgress = false
                    if (!isFinishing && !cancelRequested.get()) {
                        actionButton.isEnabled = true
                        actionButton.text = "Reintentar descarga"
                        statusView.text = "Falló la descarga o verificación del modelo. Revisa la conexión y reintenta."
                    }
                }
            } finally {
                activeConnection = null
                connection?.disconnect()
            }
        }
    }

    private fun startRecordingAndTranscription() {
        if (operationInProgress) return
        operationInProgress = true
        cancelRequested.set(false)
        stopRequested.set(false)
        actionButton.text = "Detener y transcribir"
        statusView.text = "Listo para grabar. Habla después de iniciar."
        actionButton.setOnClickListener {
            stopRequested.set(true)
            actionButton.isEnabled = false
            statusView.text = "Terminando la grabación…"
        }

        worker.execute {
            var recorder: AudioRecord? = null
            try {
                val minimumBuffer = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                if (minimumBuffer <= 0) throw IllegalStateException("AudioRecord no disponible")
                recorder = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    max(minimumBuffer * 2, SAMPLE_RATE)
                )
                if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                    throw IllegalStateException("No se pudo iniciar el micrófono")
                }
                activeRecorder = recorder
                recorder.startRecording()
                runOnUiThread {
                    if (!isFinishing && !cancelRequested.get()) {
                        statusView.text = "Escuchando… toca Detener y transcribir cuando termines."
                    }
                }

                val samples = ArrayList<Short>(MAX_SAMPLES)
                val buffer = ShortArray(READ_BUFFER_SAMPLES)
                while (!cancelRequested.get() && !stopRequested.get() && samples.size < MAX_SAMPLES) {
                    val count = recorder.read(buffer, 0, buffer.size)
                    if (count < 0) throw IllegalStateException("Error leyendo el micrófono: $count")
                    for (index in 0 until count) {
                        if (samples.size >= MAX_SAMPLES) break
                        samples.add(buffer[index])
                    }
                }
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    recorder.stop()
                }
                recorder.release()
                recorder = null
                activeRecorder = null

                if (cancelRequested.get()) return@execute
                if (samples.size < MIN_SAMPLES) {
                    throw IllegalStateException("Grabación demasiado corta")
                }

                runOnUiThread {
                    if (!isFinishing && !cancelRequested.get()) {
                        statusView.text = "Transcribiendo con Whisper en el dispositivo…"
                        actionButton.text = "Procesando…"
                    }
                }
                val audio = FloatArray(samples.size) { index -> samples[index] / 32768.0f }
                val native = WhisperNative()
                val context = native.initialize(modelFile.absolutePath)
                if (context == 0L) throw IllegalStateException("No se pudo cargar el modelo")
                val transcript = try {
                    native.transcribe(context, audio).trim()
                } finally {
                    native.release(context)
                }
                runOnUiThread {
                    operationInProgress = false
                    if (isFinishing || cancelRequested.get()) return@runOnUiThread
                    if (transcript.isNotBlank()) {
                        resultReceiver?.send(RESULT_TRANSCRIPT, Bundle().apply {
                            putString(EXTRA_TRANSCRIPT, transcript)
                        })
                        finish()
                    } else {
                        statusView.text = "No se reconoció voz. Pulsa para intentarlo de nuevo."
                        actionButton.text = "Iniciar dictado"
                        actionButton.isEnabled = true
                        actionButton.setOnClickListener { onPrimaryAction() }
                    }
                }
            } catch (error: SecurityException) {
                recorder?.release()
                activeRecorder = null
                runOnUiThread {
                    operationInProgress = false
                    if (!isFinishing && !cancelRequested.get()) {
                        statusView.text = "No se pudo acceder al micrófono. Revisa el permiso."
                        renderReadyState()
                        actionButton.isEnabled = true
                    }
                }
            } catch (error: Exception) {
                try {
                    if (recorder?.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
                } catch (_: IllegalStateException) {
                }
                recorder?.release()
                activeRecorder = null
                runOnUiThread {
                    operationInProgress = false
                    if (!isFinishing && !cancelRequested.get()) {
                        statusView.text = when (error.message) {
                            "Grabación demasiado corta" -> "No se detectó suficiente voz. Inténtalo de nuevo."
                            else -> "Falló la grabación o la transcripción local. Inténtalo de nuevo."
                        }
                        actionButton.isEnabled = true
                        actionButton.text = "Iniciar dictado"
                        actionButton.setOnClickListener { onPrimaryAction() }
                    }
                }
            }
        }
    }

    private fun cancelAndFinish() {
        cancelRequested.set(true)
        stopRequested.set(true)
        activeConnection?.disconnect()
        finish()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_AUDIO) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                startRecordingAndTranscription()
            } else {
                statusView.text = "Permiso de micrófono denegado."
                renderReadyState()
            }
        }
    }

    override fun onDestroy() {
        cancelRequested.set(true)
        stopRequested.set(true)
        activeConnection?.disconnect()
        worker.shutdownNow()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_RESULT_RECEIVER = "dev.flowy.keyboard.RESULT_RECEIVER"
        const val EXTRA_TRANSCRIPT = "dev.flowy.keyboard.TRANSCRIPT"
        const val RESULT_TRANSCRIPT = 10
        private const val REQUEST_AUDIO = 41
        private const val SAMPLE_RATE = 16_000
        private const val READ_BUFFER_SAMPLES = 4_096
        private const val MAX_RECORDING_SECONDS = 30
        private const val MAX_SAMPLES = SAMPLE_RATE * MAX_RECORDING_SECONDS
        private const val MIN_SAMPLES = SAMPLE_RATE / 2
        private const val MODEL_FILE_NAME = "whisper-tiny-q5_1-es.ggml"
        private const val MIN_MODEL_BYTES = 30L * 1024L * 1024L
        private const val MODEL_SHA256 = "818710568da3ca15689e31a743197b520007872ff9576237bda97bd1b469c3d7"
        private const val MODEL_URL =
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/c521a4b02f422512d734391fdf08bb08c0862f68/ggml-tiny-q5_1.bin"
    }
}
