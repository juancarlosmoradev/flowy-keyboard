package dev.flowy.keyboard

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.ResultReceiver
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
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
    private lateinit var detailView: TextView
    private lateinit var actionButton: Button
    private lateinit var visualizer: VoiceListeningVisual
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
        window.statusBarColor = Color.rgb(11, 14, 24)
        window.navigationBarColor = Color.rgb(11, 14, 24)
        @Suppress("DEPRECATION")
        resultReceiver = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_RESULT_RECEIVER, ResultReceiver::class.java)
        } else {
            intent.getParcelableExtra(EXTRA_RESULT_RECEIVER)
        }
        try {
            createContent()
            renderReadyState()
            if (intent.getBooleanExtra(EXTRA_START_IMMEDIATELY, false)) {
                if (modelFile.isFile && modelFile.length() > MIN_MODEL_BYTES) {
                    requestPermissionOrStart()
                } else {
                    downloadModel()
                }
            }
        } catch (error: RuntimeException) {
            Log.e("FlowyVoice", "No se pudo iniciar la pantalla de dictado", error)
            Toast.makeText(this, "No se pudo abrir el dictado. Intenta otra vez.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun createContent() {
        val root = FrameLayout(this).apply {
            background = getDrawable(R.drawable.voice_background)
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(26), dp(26), dp(26), dp(30))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = "●"
            textSize = 13f
            setTextColor(Color.rgb(106, 236, 215))
        })
        header.addView(TextView(this).apply {
            text = "  flowy voice"
            textSize = 11f
            letterSpacing = 0.2f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(194, 204, 226))
        })
        layout.addView(header, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(34)
        ))

        val visualArea = FrameLayout(this)
        visualizer = VoiceListeningVisual(this)
        visualArea.addView(visualizer, FrameLayout.LayoutParams(dp(300), dp(300), Gravity.CENTER))
        layout.addView(visualArea, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        statusView = TextView(this).apply {
            textSize = 24f
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(Color.rgb(247, 248, 255))
        }
        detailView = TextView(this).apply {
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(151, 164, 191))
        }
        layout.addView(statusView)
        layout.addView(detailView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })

        actionButton = Button(this).apply {
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            isSingleLine = true
            isAllCaps = false
            setTextColor(Color.rgb(20, 25, 38))
            background = getDrawable(R.drawable.voice_stop_background)
            stateListAnimator = null
            setOnClickListener { onPrimaryAction() }
        }
        val cancelButton = TextView(this).apply {
            text = "Cancelar"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(149, 161, 186))
            background = getDrawable(R.drawable.voice_cancel_background)
            setOnClickListener { cancelAndFinish() }
        }
        layout.addView(actionButton, LinearLayout.LayoutParams(dp(170), dp(52)).apply {
            topMargin = dp(28)
        })
        layout.addView(cancelButton, LinearLayout.LayoutParams(dp(130), dp(44)).apply {
            topMargin = dp(8)
        })
        root.addView(layout, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
        setContentView(root)
    }

    private fun renderReadyState() {
        visualizer.setMode(VoiceListeningVisual.Mode.READY)
        statusView.setTextColor(Color.rgb(247, 248, 255))
        detailView.text = ""
        if (modelFile.isFile && modelFile.length() > MIN_MODEL_BYTES) {
            statusView.text = "Voz lista"
            actionButton.text = "Empezar"
        } else {
            statusView.text = "Preparando tu voz"
            detailView.text = "El modelo se descarga una vez"
            actionButton.text = "Continuar"
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
            visualizer.setMode(VoiceListeningVisual.Mode.READY)
            statusView.text = "Permiso de micrófono"
            detailView.text = "Necesario para escuchar tu voz"
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
        visualizer.setMode(VoiceListeningVisual.Mode.PREPARING)
        statusView.text = "Preparando voz"
        detailView.text = ""
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
                                        statusView.text = "Preparando voz"
                                        detailView.text = if (percent >= 0) "$percent%" else "Descargando modelo local"
                                        visualizer.progress = if (percent >= 0) percent / 100f else null
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
                        visualizer.setMode(VoiceListeningVisual.Mode.READY)
                        statusView.text = "No se pudo preparar la voz"
                        detailView.text = "Revisa tu conexión e inténtalo de nuevo"
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
        visualizer.setMode(VoiceListeningVisual.Mode.LISTENING)
        visualizer.progress = null
        visualizer.audioLevel = 0.08f
        statusView.setTextColor(Color.rgb(247, 248, 255))
        actionButton.text = "Detener"
        actionButton.isEnabled = true
        statusView.text = "Te escucho"
        detailView.text = "Haz una pausa para terminar"
        actionButton.setOnClickListener {
            stopRequested.set(true)
            actionButton.isEnabled = false
            visualizer.setMode(VoiceListeningVisual.Mode.PROCESSING)
            statusView.text = "Transcribiendo"
            detailView.text = ""
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
                    if (now - lastLevelUpdate >= 80 && count > 0) {
                        val rms = sqrt(squareSumForBuffer(buffer, count)) / 32768.0
                        val responsiveLevel = sqrt((rms * 8.0).coerceIn(0.0, 1.0)).toFloat()
                        runOnUiThread {
                            if (!isFinishing && !cancelRequested.get() && !speechFinished) {
                                visualizer.audioLevel = responsiveLevel.coerceIn(0.02f, 1f)
                                if (!speechStarted) detailView.text = ""
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
                        visualizer.setMode(VoiceListeningVisual.Mode.PROCESSING)
                        statusView.text = "Transcribiendo"
                        detailView.text = ""
                        actionButton.text = ""
                        actionButton.isEnabled = false
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
                        statusView.setTextColor(Color.rgb(124, 239, 218))
                        statusView.text = "Listo"
                        detailView.text = "Insertando en tu texto"
                        val receiver = resultReceiver
                        if (receiver == null) {
                            statusView.text = "No se pudo insertar"
                            detailView.text = "Vuelve a tocar el micrófono"
                            actionButton.text = "Cerrar"
                            actionButton.isEnabled = true
                            actionButton.setOnClickListener { finish() }
                        } else {
                            receiver.send(RESULT_TRANSCRIPT, Bundle().apply {
                                putString(EXTRA_TRANSCRIPT, transcript)
                            })
                            finish()
                        }
                    } else {
                        visualizer.setMode(VoiceListeningVisual.Mode.READY)
                        statusView.text = "No te entendí"
                        detailView.text = "Acércate al micrófono e inténtalo de nuevo"
                        actionButton.text = "Intentar de nuevo"
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
                        visualizer.setMode(VoiceListeningVisual.Mode.READY)
                        statusView.text = "Micrófono no disponible"
                        detailView.text = "Revisa el permiso del dispositivo"
                        actionButton.text = "Intentar de nuevo"
                        actionButton.isEnabled = true
                        actionButton.setOnClickListener { onPrimaryAction() }
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
                        visualizer.setMode(VoiceListeningVisual.Mode.READY)
                        statusView.text = when (error.message) {
                            "Grabación demasiado corta", "No se detectó voz" -> "No te escuché"
                            "No se recibió audio del micrófono" -> "Micrófono no disponible"
                            else -> "No se pudo transcribir"
                        }
                        detailView.text = "Toca para intentarlo otra vez"
                        actionButton.isEnabled = true
                        actionButton.text = "Intentar de nuevo"
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
                statusView.text = "Micrófono no disponible"
                detailView.text = "Activa el permiso para dictar"
                visualizer.setMode(VoiceListeningVisual.Mode.READY)
                actionButton.text = "Intentar de nuevo"
                actionButton.setOnClickListener { onPrimaryAction() }
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

private class VoiceListeningVisual(context: android.content.Context) : View(context) {
    enum class Mode { READY, LISTENING, PROCESSING, PREPARING }

    var mode: Mode = Mode.READY
        private set
    var audioLevel: Float = 0.08f
        set(value) {
            field = value.coerceIn(0f, 1f)
            postInvalidateOnAnimation()
        }
    var progress: Float? = null
        set(value) {
            field = value?.coerceIn(0f, 1f)
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val particleCount = 104
    private val startTime = System.currentTimeMillis()

    init {
        contentDescription = "Visualizador animado del dictado de voz"
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setMode(value: Mode) {
        mode = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = minOf(width, height) / 300f
        if (scale <= 0f) return
        val elapsed = (System.currentTimeMillis() - startTime) / 1000f
        val speed = when (mode) {
            Mode.LISTENING -> 1.0f
            Mode.PROCESSING -> 0.58f
            Mode.PREPARING -> 0.42f
            Mode.READY -> 0.28f
        }
        val pulse = (kotlin.math.sin((elapsed * speed * 2.0f).toDouble()).toFloat() + 1f) / 2f
        val level = if (mode == Mode.LISTENING) audioLevel else 0.16f + pulse * 0.12f

        canvas.save()
        canvas.translate(width / 2f, height / 2f)
        canvas.scale(scale, scale)

        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(
            0f, 0f, 145f,
            intArrayOf(Color.argb(62, 0, 245, 255), Color.argb(42, 255, 43, 214), Color.TRANSPARENT),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(0f, 0f, 145f, paint)
        paint.shader = null

        for (ring in 0..3) {
            val drift = (kotlin.math.sin((elapsed * speed + ring * 0.92f).toDouble()).toFloat() + 1f) / 2f
            val radius = 69f + ring * 14f + drift * (7f + level * 15f)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = if (ring == 0) 1.5f else 1f
            paint.color = when (ring % 3) {
                0 -> Color.argb((34 + drift * 50).toInt(), 0, 245, 255)
                1 -> Color.argb((26 + drift * 42).toInt(), 255, 43, 214)
                else -> Color.argb((22 + drift * 32).toInt(), 132, 72, 255)
            }
            canvas.drawCircle(0f, 0f, radius, paint)
        }

        val barCount = 72
        for (bar in 0 until barCount) {
            val angle = (Math.PI * 2.0 * bar / barCount).toFloat()
            val wave = (kotlin.math.sin((elapsed * speed * 3.3f + bar * 0.47f).toDouble()).toFloat() + 1f) / 2f
            val length = 4f + wave * (7f + level * 34f)
            val innerRadius = 56f + wave * level * 3f
            val outerRadius = innerRadius + length
            val x1 = kotlin.math.cos(angle.toDouble()).toFloat() * innerRadius
            val y1 = kotlin.math.sin(angle.toDouble()).toFloat() * innerRadius
            val x2 = kotlin.math.cos(angle.toDouble()).toFloat() * outerRadius
            val y2 = kotlin.math.sin(angle.toDouble()).toFloat() * outerRadius
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = if (bar % 4 == 0) 2.8f else 1.8f
            paint.color = when (bar % 3) {
                0 -> Color.argb(165, 105, 237, 216)
                1 -> Color.argb(132, 126, 168, 255)
                else -> Color.argb(105, 190, 132, 255)
            }
            canvas.drawLine(x1, y1, x2, y2, paint)
        }

        for (particle in 0 until particleCount) {
            val baseAngle = particle * (Math.PI * 2.0 / particleCount) +
                kotlin.math.sin(particle * 9.7).toDouble() * 0.11
            val orbit = 72f + (particle % 12) * 7.1f +
                kotlin.math.sin((elapsed * speed * 0.9f + particle * 1.73f).toDouble()).toFloat() *
                (3f + level * 16f)
            val angle = baseAngle + elapsed * speed * (if (particle % 2 == 0) 0.17f else -0.13f)
            val x = kotlin.math.cos(angle).toFloat() * orbit
            val y = kotlin.math.sin(angle).toFloat() * orbit
            val twinkle = (kotlin.math.sin((elapsed * speed * 2f + particle * 0.81f).toDouble()).toFloat() + 1f) / 2f
            val radius = 0.8f + (particle % 5) * 0.36f
            paint.style = Paint.Style.FILL
            paint.color = if (particle % 3 == 0) Color.argb((125 + twinkle * 130).toInt(), 0, 245, 255)
                else Color.argb((95 + twinkle * 145).toInt(), 255, 43, 214)
            canvas.drawCircle(x, y, radius, paint)
            if (particle % 7 == 0) {
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeWidth = 0.8f
                paint.color = Color.argb((35 + twinkle * 95).toInt(), 180, 235, 255)
                canvas.drawLine(x - radius * 2.6f, y + radius * 2.6f, x + radius * 2.6f, y - radius * 2.6f, paint)
            }
        }

        val coreRadius = 47f + pulse * (3f + level * 9f)
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(
            -16f, -18f, coreRadius * 1.8f,
            intArrayOf(Color.rgb(0, 245, 255), Color.rgb(255, 43, 214), Color.rgb(92, 46, 255)),
            floatArrayOf(0f, 0.52f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(0f, 0f, coreRadius, paint)
        paint.shader = null
        paint.color = Color.argb(45, 255, 255, 255)
        canvas.drawOval(RectF(-coreRadius * 0.56f, -coreRadius * 0.63f, coreRadius * 0.08f, -coreRadius * 0.34f), paint)

        progress?.let { value ->
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            paint.strokeCap = Paint.Cap.ROUND
            paint.color = Color.argb(45, 215, 226, 255)
            canvas.drawCircle(0f, 0f, 125f, paint)
            paint.color = Color.rgb(115, 237, 218)
            canvas.drawArc(RectF(-125f, -125f, 125f, 125f), -90f, value * 360f, false, paint)
        }

        canvas.restore()
        postInvalidateOnAnimation()
    }
}
