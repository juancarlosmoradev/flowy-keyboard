package dev.flowy.keyboard

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.inputmethodservice.InputMethodService
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import kotlin.math.abs

class FlowyInputMethodService : InputMethodService() {
    private var lastSelectionStart = -1
    private var lastSelectionEnd = -1
    private var keyboardRoot: FrameLayout? = null
    private var keyPreviewPopup: PopupWindow? = null
    private var symbolPopup: PopupWindow? = null
    private var candidateView: LinearLayout? = null
    private var composingWord = ""
    private var pendingTranscript: String? = null
    private var transcriptDeliveryId = 0
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        if (!restarting) {
            lastSelectionStart = -1
            lastSelectionEnd = -1
            composingWord = ""
            showCandidates(emptyList())
        }
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(
            oldSelStart,
            oldSelEnd,
            newSelStart,
            newSelEnd,
            candidatesStart,
            candidatesEnd
        )
        if (newSelStart >= 0 && newSelEnd >= 0) {
            lastSelectionStart = newSelStart
            lastSelectionEnd = newSelEnd
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (lastSelectionStart >= 0 && lastSelectionEnd >= 0) {
            currentInputConnection?.setSelection(lastSelectionStart, lastSelectionEnd)
        }
    }

    private inner class FlowyKeyButton(context: Context) : Button(context) {
        var centerIcon: Drawable? = null
        var isBackspaceKey = false
        private var deleteHeld = false
        private var deleteRepeatTask: Runnable? = null

        fun startContinuousDelete(view: View) {
            stopContinuousDelete()
            deleteHeld = true
            handleKey("⌫")
            val repeat = object : Runnable {
                override fun run() {
                    if (!deleteHeld) return
                    handleKey("⌫")
                    mainHandler.postDelayed(this, 75L)
                }
            }
            deleteRepeatTask = repeat
            mainHandler.postDelayed(repeat, 350L)
        }

        fun stopContinuousDelete() {
            deleteHeld = false
            deleteRepeatTask?.let(mainHandler::removeCallbacks)
            deleteRepeatTask = null
        }

        override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
            if (isBackspaceKey && keyCode in setOf(
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER,
                    KeyEvent.KEYCODE_SPACE
                )
            ) {
                if (event.repeatCount == 0) startContinuousDelete(this)
                return true
            }
            return super.onKeyDown(keyCode, event)
        }

        override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
            if (isBackspaceKey && keyCode in setOf(
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER,
                    KeyEvent.KEYCODE_SPACE
                )
            ) {
                stopContinuousDelete()
                return true
            }
            return super.onKeyUp(keyCode, event)
        }
        var symbolHint: String = ""
        private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFA4A8B0.toInt()
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            centerIcon?.let { icon ->
                val size = dp(22)
                val left = (width - size) / 2
                val top = (height - size) / 2
                icon.setBounds(left, top, left + size, top + size)
                icon.draw(canvas)
            }
            if (symbolHint.isNotEmpty()) {
                hintPaint.textSize = dp(8).toFloat()
                canvas.drawText(symbolHint, width - dp(6).toFloat(), dp(11).toFloat(), hintPaint)
            }
        }
    }

    override fun onCreateInputView(): View {
        val root = FrameLayout(this).apply {
            background = getDrawable(R.drawable.keyboard_background)
            clipChildren = false
            clipToPadding = false
        }
        keyboardRoot = root
        val keyboard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(9), dp(10), dp(10))
        }
        val suggestions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), 0, dp(8), 0)
            visibility = View.GONE
            candidateView = this
        }
        keyboard.addView(suggestions, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(36)
        ))
        val rows = listOf(
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
            listOf("a", "s", "d", "f", "g", "h", "j", "k", "l", "ñ"),
            listOf("z", "x", "c", "v", "b", "n", "m", ",", ".", "⌫")
        )
        var firstKey: View? = null
        rows.forEach { labels ->
            val row = createRow()
            labels.forEach { label ->
                val button = createKeyButton(label)
                if (firstKey == null) firstKey = button
                row.addView(button, keyLayoutParams())
            }
            keyboard.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        val actionRow = createRow()
        listOf(
            "Clear all" to 1.35f,
            "Space" to 4.2f,
            "Voz" to 1.35f,
            "↵" to 1.35f
        ).forEach { (label, weight) ->
            val button = createKeyButton(label)
            button.setBackgroundResource(R.drawable.action_key_background)
            if (label == "Clear all") {
                button.text = ""
                button.centerIcon = getDrawable(R.drawable.ic_delete_sweep)
                button.invalidate()
                button.contentDescription = "Borrar todo el texto"
            }
            if (label == "Voz") {
                button.text = ""
                button.centerIcon = getDrawable(R.drawable.ic_mic)
                button.invalidate()
                button.contentDescription = "Dictar por voz"
                button.setOnClickListener { startVoiceCapture() }
            }
            if (label == "↵") {
                button.setBackgroundResource(R.drawable.primary_key_background)
                button.text = ""
                button.centerIcon = getDrawable(R.drawable.ic_keyboard_return)
                button.invalidate()
                button.setTextColor(getColor(R.color.flowy_surface))
                button.contentDescription = "Enter" 
            }
            actionRow.addView(button, keyLayoutParams(weight))
        }
        keyboard.addView(actionRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        root.addView(keyboard, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        ))
        root.post { firstKey?.requestFocus() }
        return root
    }

    private fun startVoiceCapture() {
        val targetConnection = currentInputConnection
        if (targetConnection == null) {
            Toast.makeText(this, "No hay un campo de texto activo.", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, VoiceCaptureActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(VoiceCaptureActivity.EXTRA_RESULT_RECEIVER, object : ResultReceiver(Handler(Looper.getMainLooper())) {
                override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                    if (resultCode == VoiceCaptureActivity.RESULT_TRANSCRIPT) {
                        val transcript = resultData?.getString(VoiceCaptureActivity.EXTRA_TRANSCRIPT)
                        if (!transcript.isNullOrBlank()) {
                            deliverTranscriptWhenEditorResumes(transcript, targetConnection)
                        }
                    }
                }
            })
            putExtra(VoiceCaptureActivity.EXTRA_START_IMMEDIATELY, true)
        }
        try {
            startActivity(intent)
        } catch (error: RuntimeException) {
            android.util.Log.e("FlowyVoice", "No se pudo abrir el dictado", error)
            Toast.makeText(this, "No se pudo abrir el dictado.", Toast.LENGTH_LONG).show()
        }
    }

    private fun deliverTranscriptWhenEditorResumes(
        transcript: String,
        originalConnection: android.view.inputmethod.InputConnection
    ) {
        pendingTranscript = transcript
        val deliveryId = ++transcriptDeliveryId
        requestShowSelf(0)
        tryCommitTranscript(deliveryId, transcript, originalConnection, attempt = 0)
    }

    private fun tryCommitTranscript(
        deliveryId: Int,
        transcript: String,
        originalConnection: android.view.inputmethod.InputConnection,
        attempt: Int
    ) {
        Handler(Looper.getMainLooper()).postDelayed({
            if (deliveryId != transcriptDeliveryId || pendingTranscript != transcript) return@postDelayed

            val activeConnection = currentInputConnection
            val activeCommitted = runCatching {
                activeConnection?.commitText(transcript, 1) == true
            }.getOrDefault(false)
            if (activeCommitted) {
                pendingTranscript = null
                return@postDelayed
            }

            if (attempt < 8) {
                tryCommitTranscript(deliveryId, transcript, originalConnection, attempt + 1)
                return@postDelayed
            }

            val fallbackCommitted = if (activeConnection !== originalConnection) {
                runCatching { originalConnection.commitText(transcript, 1) }.getOrDefault(false)
            } else {
                false
            }
            pendingTranscript = null
            if (!fallbackCommitted) {
                Toast.makeText(
                    this,
                    "Reconocí el dictado, pero Android no reactivó el campo de texto. Toca el campo y vuelve a insertar.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }, if (attempt == 0) 250L else 150L)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun createRow() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
    }

    private fun keyLayoutParams(weight: Float = 1f) =
        LinearLayout.LayoutParams(0, dp(54), weight).apply {
            setMargins(dp(3), dp(4), dp(3), dp(4))
        }

    private fun createKeyButton(label: String) = FlowyKeyButton(this).apply {
        val alternates = LONG_PRESS_ALTERNATES[label].orEmpty()
        text = label
        textSize = if (label.length == 1) 19f else 14f
        isAllCaps = false
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.CENTER
        includeFontPadding = false
        setPadding(dp(2), dp(3), dp(2), dp(3))
        setTextColor(getColor(R.color.flowy_text))
        background = getDrawable(R.drawable.key_background)
        minWidth = 0
        minHeight = dp(48)
        isFocusable = true
        stateListAnimator = null
        contentDescription = if (label == "⌫") "Borrar carácter"
            else if (alternates.isEmpty()) label
            else "$label, mantén presionado para ver ${alternates.size} alternativas"
        symbolHint = alternates.take(2).joinToString(" ")
        if (label == "⌫") {
            text = ""
            isBackspaceKey = true
            centerIcon = getDrawable(R.drawable.ic_backspace)
        }
        setOnClickListener {
            if (label == "Clear all") clearAllText() else handleKey(label)
        }

        var downX = 0f
        var downY = 0f
        var longPressTriggered = false
        var longPressTask: Runnable? = null
        val touchSlop = ViewConfiguration.get(this@FlowyInputMethodService).scaledTouchSlop

        isLongClickable = false
        setOnTouchListener { view, event ->
            if (label == "⌫") {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        dismissSymbolPopup()
                        performKeyHaptic(view)
                        view.isPressed = true
                        startContinuousDelete(view)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (event.x < 0 || event.y < 0 || event.x >= view.width || event.y >= view.height) {
                            stopContinuousDelete()
                            view.isPressed = false
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        stopContinuousDelete()
                        view.isPressed = false
                    }
                }
                return@setOnTouchListener true
            }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dismissSymbolPopup()
                    performKeyHaptic(view)
                    downX = event.x
                    downY = event.y
                    longPressTriggered = false
                    if (label.length == 1 && label != "⌫") showKeyPreview(view, label)
                    if (alternates.isNotEmpty()) {
                        val task = Runnable {
                            if (view.isPressed) {
                                longPressTriggered = true
                                dismissKeyPreview()
                                performKeyHaptic(view)
                                showSymbolPopup(view, alternates)
                                view.isPressed = false
                            }
                        }
                        longPressTask = task
                        mainHandler.postDelayed(task, 230L)
                    }
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop) {
                        longPressTask?.let(mainHandler::removeCallbacks)
                        dismissKeyPreview()
                    }
                    false
                }
                MotionEvent.ACTION_UP -> {
                    longPressTask?.let(mainHandler::removeCallbacks)
                    dismissKeyPreview()
                    if (longPressTriggered) {
                        longPressTriggered = false
                        true
                    } else false
                }
                MotionEvent.ACTION_CANCEL -> {
                    longPressTask?.let(mainHandler::removeCallbacks)
                    dismissKeyPreview()
                    val consumed = longPressTriggered
                    longPressTriggered = false
                    consumed
                }
                else -> false
            }
        }
    }

    private fun performKeyHaptic(view: View) {
        val feedback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            HapticFeedbackConstants.KEYBOARD_TAP
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        }
        view.performHapticFeedback(feedback)
    }

    private fun popupWindow(content: View, width: Int, height: Int, touchable: Boolean): PopupWindow {
        return PopupWindow(content, width, height, false).apply {
            isFocusable = false
            isTouchable = touchable
            isOutsideTouchable = false
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            elevation = dp(18).toFloat()
        }
    }

    private fun popupXOffset(anchor: View, popupWidth: Int): Int {
        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        val screenWidth = resources.displayMetrics.widthPixels
        val desiredLeft = (location[0] + anchor.width / 2 - popupWidth / 2)
            .coerceIn(dp(4), (screenWidth - popupWidth - dp(4)).coerceAtLeast(dp(4)))
        return desiredLeft - location[0]
    }

    private fun showKeyPreview(anchor: View, label: String) {
        dismissKeyPreview()
        val height = dp(58)
        val preview = TextView(this).apply {
            text = label
            textSize = 27f
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(getColor(R.color.flowy_text))
            background = getDrawable(R.drawable.key_preview_background)
        }
        val popup = popupWindow(preview, dp(56), height, touchable = false)
        keyPreviewPopup = popup
        popup.showAsDropDown(anchor, popupXOffset(anchor, dp(56)), -anchor.height - height - dp(5), Gravity.START)
        preview.alpha = 0f
        preview.animate().alpha(1f).setDuration(65).start()
    }

    private fun showSymbolPopup(anchor: View, symbols: List<String>) {
        dismissKeyPreview()
        dismissSymbolPopup()
        val itemWidth = dp(43)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(5), dp(5), dp(5))
            background = getDrawable(R.drawable.symbol_popup_background)
        }
        symbols.forEach { symbol ->
            val option = TextView(this).apply {
                text = symbol
                textSize = 20f
                gravity = Gravity.CENTER
                includeFontPadding = false
                setTextColor(getColor(R.color.flowy_text))
                background = getDrawable(R.drawable.key_background)
                isClickable = true
                contentDescription = "Insertar $symbol"
                setOnClickListener {
                    performKeyHaptic(this)
                    finishComposition()
                    currentInputConnection?.commitText(symbol, 1)
                    dismissSymbolPopup()
                }
            }
            panel.addView(option, LinearLayout.LayoutParams(itemWidth, dp(46)).apply {
                setMargins(dp(2), 0, dp(2), 0)
            })
        }
        val popupWidth = (itemWidth + dp(4)) * symbols.size + dp(10)
        val popupHeight = dp(56)
        val popup = popupWindow(panel, popupWidth, popupHeight, touchable = true)
        symbolPopup = popup
        popup.showAsDropDown(
            anchor,
            popupXOffset(anchor, popupWidth),
            -anchor.height - popupHeight - dp(6),
            Gravity.START
        )
        panel.alpha = 0f
        panel.animate().alpha(1f).setDuration(55).start()
    }

    private fun dismissSymbolPopup() {
        symbolPopup?.dismiss()
        symbolPopup = null
    }

    private fun dismissKeyPreview() {
        keyPreviewPopup?.dismiss()
        keyPreviewPopup = null
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        dismissKeyPreview()
        dismissSymbolPopup()
        finishComposition()
        super.onFinishInputView(finishingInput)
    }

    private fun updateComposingWord(word: String) {
        composingWord = word
        val connection = currentInputConnection ?: return
        if (word.isEmpty()) {
            connection.setComposingText("", 1)
            connection.finishComposingText()
            showCandidates(emptyList())
            return
        }
        connection.setComposingText(word, 1)
        showCandidates(makeSuggestions(word))
    }

    private fun makeSuggestions(word: String): List<String> {
        if (word.isBlank()) return emptyList()
        val prefix = word.lowercase(Locale("es", "CL"))
        val local = COMMON_WORDS.asSequence()
            .filter { it.startsWith(prefix) && !it.equals(prefix, ignoreCase = true) }
            .take(5)
            .toList()
        return (listOf(word) + local)
            .distinctBy { it.lowercase(Locale.ROOT) }
            .take(3)
    }

    private fun showCandidates(words: List<String>) {
        val strip = candidateView ?: return
        strip.removeAllViews()
        if (words.isEmpty()) {
            strip.visibility = View.GONE
            keyboardRoot?.requestLayout()
            return
        }
        strip.visibility = View.VISIBLE
        words.take(3).forEachIndexed { index, word ->
            val candidate = TextView(this).apply {
                text = word
                textSize = 14f
                gravity = Gravity.CENTER
                isSingleLine = true
                setTextColor(getColor(if (index == 1) R.color.flowy_accent else R.color.flowy_text))
                setOnClickListener { commitCandidate(word) }
            }
            strip.addView(candidate, LinearLayout.LayoutParams(0, dp(34), 1f))
        }
        keyboardRoot?.requestLayout()
    }

    private fun commitCandidate(word: String) {
        val connection = currentInputConnection ?: return
        connection.commitText("$word ", 1)
        composingWord = ""
        showCandidates(emptyList())
    }

    private fun finishComposition() {
        if (composingWord.isNotEmpty()) currentInputConnection?.finishComposingText()
        composingWord = ""
        showCandidates(emptyList())
    }

    private fun handleKey(label: String) {
        val connection = currentInputConnection ?: return
        when (label) {
            "⌫" -> {
                if (composingWord.isNotEmpty()) updateComposingWord(composingWord.dropLast(1))
                else connection.deleteSurroundingText(1, 0)
            }
            "Space" -> {
                finishComposition()
                connection.commitText(" ", 1)
            }
            "↵" -> {
                finishComposition()
                connection.performEditorAction(
                    currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
                        ?: EditorInfo.IME_ACTION_DONE
                )
            }
            "Voz" -> {
                finishComposition()
                startVoiceCapture()
            }
            else -> if (label.length == 1 && label[0].isLetter()) {
                updateComposingWord(composingWord + label)
            } else {
                finishComposition()
                connection.commitText(label, 1)
            }
        }
    }

    private companion object {
        val COMMON_WORDS = listOf("ahora", "algo", "algunos", "alguien", "allí", "aquí", "así", "aunque", "además", "antes", "año", "años", "abajo", "bien", "bueno", "buenos", "cada", "casi", "casa", "caso", "como", "con", "cómo", "cosas", "cuando", "cuál", "cualquier", "cuanto", "cuánto", "curso", "dado", "dar", "de", "debe", "decir", "dejar", "desde", "día", "días", "donde", "dos", "durante", "e", "el", "ella", "ellos", "en", "entre", "era", "eres", "es", "esa", "esas", "ese", "eso", "esta", "estaba", "están", "este", "esto", "estos", "fin", "forma", "fue", "fuera", "fueron", "gente", "gracias", "gran", "grande", "ha", "haber", "había", "hace", "hacer", "hacia", "han", "hasta", "hay", "he", "hecho", "hola", "hoy", "iba", "igual", "incluso", "ir", "la", "las", "le", "les", "lo", "los", "luego", "lugar", "más", "menos", "mi", "mientras", "mismo", "mis", "mucha", "mucho", "muy", "nada", "nadie", "necesario", "ni", "no", "nos", "nosotros", "nuestra", "nuestro", "nunca", "o", "otra", "otro", "para", "parece", "parte", "pasar", "pero", "poco", "podemos", "poder", "podrá", "podrían", "podría", "puede", "pueden", "pues", "que", "qué", "quien", "quién", "quiero", "quizá", "saber", "será", "ser", "si", "sí", "sido", "siempre", "sin", "sobre", "soy", "su", "sus", "tal", "también", "tan", "tanto", "te", "tener", "tengo", "tenemos", "tiempo", "tiene", "toda", "todas", "todo", "todos", "tomar", "trabajo", "tras", "tu", "tú", "un", "una", "unas", "uno", "unos", "usted", "ustedes", "va", "vamos", "van", "varias", "veces", "ver", "verdad", "vez", "vida", "ya", "yo")
        val LONG_PRESS_ALTERNATES = mapOf(
            "q" to listOf("1", "!", "¡", "¿"), "w" to listOf("2", "@", "€"),
            "e" to listOf("3", "é", "è", "ê", "ë"), "r" to listOf("4", "#", "£"),
            "t" to listOf("5", "$", "¢"), "y" to listOf("6", "ü", "ÿ"),
            "u" to listOf("7", "ú", "ù", "û", "ü"), "i" to listOf("8", "í", "ì", "î", "ï"),
            "o" to listOf("9", "ó", "ò", "ô", "ö"), "p" to listOf("0", "°", "·"),
            "a" to listOf("@", "á", "à", "â", "ä", "æ"), "s" to listOf("#", "ß", "§"),
            "d" to listOf("$", "€", "£", "¥"), "f" to listOf("%", "‰", "ƒ"),
            "g" to listOf("&", "§", "¢"), "h" to listOf("-", "_", "–", "—"),
            "j" to listOf("+", "=", "±"), "k" to listOf("(", "[", "{"),
            "l" to listOf(")", "]", "}"), "ñ" to listOf("!", "¡", "?", "¿"),
            "z" to listOf("?", "¿", "~"), "x" to listOf(":", ";", "×"),
            "c" to listOf(";", ":", "ç", "©"), "v" to listOf("/", "\\", "|"),
            "b" to listOf("\"", "'", "“", "”"), "n" to listOf("'", "’", "‘", "´"),
            "m" to listOf("\\", "~", "≈"), "," to listOf("<", "«", "≤"),
            "." to listOf(">", "…", "≥", "?", "!")
        )
    }


    private fun clearAllText() {
        val connection = currentInputConnection ?: return
        val extracted = connection.getExtractedText(ExtractedTextRequest(), 0)
        finishComposition()
        if (extracted != null) {
            val start = extracted.startOffset.coerceAtLeast(0)
            val end = start + extracted.text.length
            if (connection.setSelection(start, end)) {
                connection.commitText("", 1)
                connection.setSelection(start, start)
            }
            return
        }
        if (connection.performContextMenuAction(android.R.id.selectAll)) {
            connection.commitText("", 1)
            connection.setSelection(0, 0)
        }
    }
}
