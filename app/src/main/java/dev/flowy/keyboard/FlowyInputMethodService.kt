package dev.flowy.keyboard

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.text.TextUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class FlowyInputMethodService : InputMethodService() {
    private var lastSelectionStart = -1
    private var lastSelectionEnd = -1
    private var keyboardRoot: FrameLayout? = null
    private var popupLayer: FrameLayout? = null
    private var keyPreviewView: TextView? = null
    private var symbolPanel: LinearLayout? = null
    private var symbolAnchor: View? = null
    private var pendingTranscript: String? = null
    private var transcriptDeliveryId = 0

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        if (!restarting) {
            lastSelectionStart = -1
            lastSelectionEnd = -1
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

    override fun onCreateInputView(): View {
        val root = FrameLayout(this).apply {
            background = getDrawable(R.drawable.keyboard_background)
            minimumHeight = dp(350)
            clipChildren = false
            clipToPadding = false
        }
        keyboardRoot = root
        val overlay = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
        }
        popupLayer = overlay
        val keyboard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(9), dp(10), dp(10))
        }
        val rows = listOf(
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
                button.setCompoundDrawablesWithIntrinsicBounds(
                    getDrawable(R.drawable.ic_delete_sweep), null, null, null
                )
                button.contentDescription = "Borrar todo el texto"
            }
            if (label == "⌫") {
                button.text = ""
                button.setCompoundDrawablesWithIntrinsicBounds(
                    getDrawable(R.drawable.ic_backspace), null, null, null
                )
                button.contentDescription = "Borrar carácter"
            }
            if (label == "Voz") {
                button.text = ""
                button.setCompoundDrawablesWithIntrinsicBounds(
                    getDrawable(R.drawable.ic_mic), null, null, null
                )
                button.contentDescription = "Dictar por voz"
                button.setOnClickListener { startVoiceCapture() }
            }
            if (label == "↵") {
                button.setBackgroundResource(R.drawable.primary_key_background)
                button.text = ""
                button.setCompoundDrawablesWithIntrinsicBounds(
                    getDrawable(R.drawable.ic_keyboard_return), null, null, null
                )
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
        root.addView(overlay, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            Gravity.TOP
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
        startActivity(intent)
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

    private fun createKeyButton(label: String) = Button(this).apply {
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
        contentDescription = if (alternates.isEmpty()) label else "$label, mantén presionado para ver ${alternates.size} alternativas"
        setOnClickListener {
            if (label == "Clear all") clearAllText() else handleKey(label)
        }
        isLongClickable = alternates.isNotEmpty()
        setOnLongClickListener {
            if (alternates.isEmpty()) return@setOnLongClickListener false
            showSymbolPopup(it, alternates)
            true
        }
        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dismissSymbolPopup()
                    performKeyHaptic(view)
                    if (label.length == 1 && label != "⌫") showKeyPreview(view, label)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dismissKeyPreview()
            }
            false
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

    private fun showKeyPreview(anchor: View, label: String) {
        dismissKeyPreview()
        val root = keyboardRoot ?: return
        val preview = TextView(this).apply {
            text = label
            textSize = 28f
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(getColor(R.color.flowy_text))
            background = getDrawable(R.drawable.key_preview_background)
            elevation = dp(14).toFloat()
            alpha = 0f
            scaleX = 0.72f
            scaleY = 0.72f
        }
        val popupWidth = dp(60)
        val popupHeight = dp(62)
        val position = overlayPosition(anchor, popupWidth, popupHeight, dp(5))
        val layer = popupLayer ?: return
        layer.addView(preview, FrameLayout.LayoutParams(popupWidth, popupHeight).apply {
            leftMargin = position.first
            topMargin = position.second
        })
        keyPreviewView = preview
        preview.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(120).start()
    }

    private fun showSymbolPopup(anchor: View, symbols: List<String>) {
        dismissKeyPreview()
        dismissSymbolPopup()
        val root = keyboardRoot ?: return
        val itemWidth = dp(44)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = getDrawable(R.drawable.symbol_popup_background)
            elevation = dp(12).toFloat()
            alpha = 0f
            scaleX = 0.94f
            scaleY = 0.94f
        }
        symbols.forEach { symbol ->
            val option = TextView(this).apply {
                text = symbol
                textSize = 20f
                gravity = Gravity.CENTER
                includeFontPadding = false
                setTextColor(getColor(R.color.flowy_text))
                background = getDrawable(R.drawable.key_background)
                isFocusable = true
                isFocusableInTouchMode = true
                contentDescription = symbol
                setOnClickListener {
                    performKeyHaptic(this)
                    currentInputConnection?.commitText(symbol, 1)
                    val anchorToRestore = symbolAnchor
                    dismissSymbolPopup()
                    anchorToRestore?.requestFocus()
                }
            }
            panel.addView(option, LinearLayout.LayoutParams(itemWidth, dp(48)).apply {
                setMargins(dp(2), 0, dp(2), 0)
            })
        }
        val popupWidth = (itemWidth + dp(4)) * symbols.size + dp(12)
        val popupHeight = dp(60)
        val position = overlayPosition(anchor, popupWidth, popupHeight, dp(7))
        val layer = popupLayer ?: return
        layer.addView(panel, FrameLayout.LayoutParams(popupWidth, popupHeight).apply {
            leftMargin = position.first
            topMargin = position.second
        })
        symbolPanel = panel
        symbolAnchor = anchor
        panel.getChildAt(0)?.requestFocus()
        panel.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(140).start()
    }

    private fun overlayPosition(anchor: View, width: Int, height: Int, gap: Int): Pair<Int, Int> {
        val root = keyboardRoot ?: return 0 to 0
        val rootLocation = IntArray(2)
        val anchorLocation = IntArray(2)
        root.getLocationInWindow(rootLocation)
        anchor.getLocationInWindow(anchorLocation)
        val centeredX = anchorLocation[0] - rootLocation[0] + anchor.width / 2 - width / 2
        val x = centeredX.coerceIn(dp(4), (root.width - width - dp(4)).coerceAtLeast(dp(4)))
        val aboveY = anchorLocation[1] - rootLocation[1] - height - gap
        val maxY = (root.height - height - dp(4)).coerceAtLeast(dp(4))
        val y = aboveY.coerceIn(dp(4), maxY)
        return x to y
    }

    private fun dismissSymbolPopup() {
        val panel = symbolPanel ?: return
        panel.isEnabled = false
        panel.animate().alpha(0f).scaleX(0.96f).scaleY(0.96f).setDuration(90)
            .withEndAction { (panel.parent as? ViewGroup)?.removeView(panel) }
            .start()
        symbolPanel = null
        symbolAnchor = null
    }

    private fun dismissKeyPreview() {
        val preview = keyPreviewView ?: return
        preview.animate().alpha(0f).scaleX(0.78f).scaleY(0.78f).setDuration(70)
            .withEndAction { (preview.parent as? ViewGroup)?.removeView(preview) }
            .start()
        keyPreviewView = null
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        dismissKeyPreview()
        dismissSymbolPopup()
        super.onFinishInputView(finishingInput)
    }

    private fun handleKey(label: String) {
        val connection = currentInputConnection ?: return
        when (label) {
            "⌫" -> connection.deleteSurroundingText(1, 0)
            "Space" -> connection.commitText(" ", 1)
            "↵" -> connection.performEditorAction(
                currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
                    ?: EditorInfo.IME_ACTION_DONE
            )
            "Voz" -> startVoiceCapture()
            else -> connection.commitText(label, 1)
        }
    }

    private companion object {
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
