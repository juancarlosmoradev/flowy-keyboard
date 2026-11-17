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
import kotlin.math.sqrt

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
        if (intent.getBooleanExtra(EXTRA_START_IMMEDIATELY, false)) {
            if (modelFile.isFile && modelFile.length() > MIN_MODEL_BYTES) {
                requestPermissionOrStart()
            } else {
                downloadModel()
            }
        }
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
        requestPermissionOrStart()
    }

    private fun requestPermissionOrStart() {
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
                        if (intent.getBooleanExtra(EXTRA_START_IMMEDIATELY, false)) {
                            requestPermissionOrStart()
                        } else {
                            renderReadyState()
                        }
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
        actionButton.text = "Terminar ahora"
        statusView.text = "Iniciando dictado…"
        actionButton.setOnClickListener {
            stopRequested.set(true)
            actionButton.isEnabled = false
            statusView.text = "Terminando y transcribiendo…"
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
                        statusView.text = "Habla ahora. El dictado termina al detectar una pausa."
                    }
                }

                val samples = ArrayList<Short>(MAX_SAMPLES)
                val buffer = ShortArray(READ_BUFFER_SAMPLES)
                var peakAmplitude = 0
                var noiseFloor = INITIAL_NOISE_RMS
                var speechStarted = false
                var speechFrameCount = 0
                var speechSampleCount = 0
                var trailingSilenceSamples = 0
                var speechFinished = false
                var lastLevelUpdate = 0L
                while (!cancelRequested.get() && !stopRequested.get() &&
                    samples.size < MAX_SAMPLES && !speechFinished
                ) {
                    val count = recorder.read(buffer, 0, buffer.size)
                    if (count < 0) throw IllegalStateException("Error leyendo el micrófono: $count")
                    var frameStart = 0
                    while (frameStart < count && samples.size < MAX_SAMPLES) {
                        val frameEnd = minOf(frameStart + VAD_FRAME_SAMPLES, count)
                        var squareSum = 0.0
                        var framePeak = 0
                        for (index in frameStart until frameEnd) {
                            val sample = buffer[index].toInt()
                            val magnitude = kotlin.math.abs(sample)
                            peakAmplitude = max(peakAmplitude, magnitude)
                            framePeak = max(framePeak, magnitude)
                            squareSum += sample.toDouble() * sample.toDouble()
                            samples.add(buffer[index])
                        }
                        val frameSamples = frameEnd - frameStart
                        val frameRms = sqrt(squareSum / frameSamples)
                        if (!speechStarted) {
                            noiseFloor = noiseFloor * NOISE_FLOOR_SMOOTHING +
                                frameRms * (1.0 - NOISE_FLOOR_SMOOTHING)
                            val speechThreshold = max(
                                MIN_VOICE_RMS,
                                minOf(noiseFloor, MAX_NOISE_REFERENCE_RMS) * START_NOISE_MULTIPLIER
                            )
                            if (frameRms >= speechThreshold && framePeak >= MIN_SIGNAL_PEAK) {
                                speechFrameCount++
                                if (speechFrameCount >= REQUIRED_SPEECH_FRAMES) {
                                    speechStarted = true
                                    runOnUiThread {
                                        if (!isFinishing && !cancelRequested.get()) {
                                            statusView.text = "Escuchando… guarda silencio cuando termines."
                                        }
                                    }
                                }
                            } else {
                                speechFrameCount = 0
                            }
                            if (!speechStarted && samples.size >= MAX_WAIT_FOR_SPEECH_SAMPLES) {
                                throw IllegalStateException("No se detectó voz")
                            }
                        } else {
                            speechSampleCount += frameSamples
                            val silenceThreshold = max(
                                MIN_SILENCE_RMS,
                                minOf(noiseFloor, MAX_NOISE_REFERENCE_RMS) * END_NOISE_MULTIPLIER
                            )
                            if (frameRms < silenceThreshold) {
                                trailingSilenceSamples += frameSamples
                            } else {
                                trailingSilenceSamples = 0
                            }
                            if (speechSampleCount >= MIN_SPEECH_SAMPLES &&
                                trailingSilenceSamples >= END_SILENCE_SAMPLES
                            ) {
                                speechFinished = true
                            }
                        }
                        frameStart = frameEnd
                    }

                    val now = System.currentTimeMillis()
                    if (now - lastLevelUpdate >= 400 && count > 0) {
                        val levelPercent = (sqrt(squareSumForBuffer(buffer, count)) / 32768.0 * 100.0).toInt()
                        runOnUiThread {
                            if (!isFinishing && !cancelRequested.get() && !speechFinished) {
                                statusView.text = if (speechStarted) {
                                    "Dictando… ${levelPercent}% de señal. Pausa para terminar."
                                } else {
                                    "Esperando voz… ${levelPercent}% de señal."
                                }
                            }
                        }
                        lastLevelUpdate = now
                    }
                }
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    recorder.stop()
                }
                recorder.release()
                recorder = null
                activeRecorder = null

                if (cancelRequested.get()) return@execute
                if (!speechStarted) throw IllegalStateException("No se detectó voz")
                if (samples.size < MIN_SAMPLES) {
                    throw IllegalStateException("Grabación demasiado corta")
                }
                if (peakAmplitude < MIN_SIGNAL_PEAK) {
                    throw IllegalStateException("No se recibió audio del micrófono")
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
                        statusView.text = "Transcripción lista. Insertando en el campo activo…\n\n$transcript"
                        val receiver = resultReceiver
                        if (receiver == null) {
                            statusView.text = "No se pudo devolver la transcripción al teclado.\n\n$transcript"
                        } else {
                            receiver.send(RESULT_TRANSCRIPT, Bundle().apply {
                                putString(EXTRA_TRANSCRIPT, transcript)
                            })
                            finish()
                        }
                    } else {
                        statusView.text =
                            "Audio recibido (pico $peakAmplitude), pero no se reconocieron palabras. Acércate al micrófono y prueba otra vez."
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
                            "Grabación demasiado corta" -> "La grabación fue muy corta. Habla al menos un segundo e inténtalo de nuevo."
                            "No se recibió audio del micrófono" -> "No está llegando audio. Revisa el permiso y el micrófono activo del teléfono."
                            "No se detectó voz" -> "No detecté voz. Toca el micrófono y habla cuando aparezca Esperando voz."
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

    private fun squareSumForBuffer(buffer: ShortArray, count: Int): Double {
        var sum = 0.0
        for (index in 0 until count) {
            val sample = buffer[index].toDouble()
            sum += sample * sample
        }
        return sum / count.coerceAtLeast(1)
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
        const val EXTRA_START_IMMEDIATELY = "dev.flowy.keyboard.START_IMMEDIATELY"
        const val RESULT_TRANSCRIPT = 10
        private const val REQUEST_AUDIO = 41
        private const val SAMPLE_RATE = 16_000
        private const val READ_BUFFER_SAMPLES = 4_096
        private const val VAD_FRAME_SAMPLES = SAMPLE_RATE / 50
        private const val MAX_RECORDING_SECONDS = 30
        private const val MAX_SAMPLES = SAMPLE_RATE * MAX_RECORDING_SECONDS
        private const val MAX_WAIT_FOR_SPEECH_SAMPLES = SAMPLE_RATE * 12
        private const val MIN_SAMPLES = SAMPLE_RATE / 2
        private const val MIN_SPEECH_SAMPLES = SAMPLE_RATE * 3 / 10
        private const val END_SILENCE_SAMPLES = SAMPLE_RATE
        private const val REQUIRED_SPEECH_FRAMES = 3
        private const val MIN_VOICE_RMS = 450.0
        private const val MIN_SILENCE_RMS = 280.0
        private const val INITIAL_NOISE_RMS = 100.0
        private const val MAX_NOISE_REFERENCE_RMS = 700.0
        private const val NOISE_FLOOR_SMOOTHING = 0.94
        private const val START_NOISE_MULTIPLIER = 1.6
        private const val END_NOISE_MULTIPLIER = 1.2
        private const val MIN_SIGNAL_PEAK = 80
        private const val MODEL_FILE_NAME = "whisper-tiny-q5_1-es.ggml"
        private const val MIN_MODEL_BYTES = 30L * 1024L * 1024L
        private const val MODEL_SHA256 = "818710568da3ca15689e31a743197b520007872ff9576237bda97bd1b469c3d7"
        private const val MODEL_URL =
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/c521a4b02f422512d734391fdf08bb08c0862f68/ggml-tiny-q5_1.bin"
    }
}
