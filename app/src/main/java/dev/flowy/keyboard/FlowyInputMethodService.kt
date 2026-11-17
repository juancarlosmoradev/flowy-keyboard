package dev.flowy.keyboard

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast

class FlowyInputMethodService : InputMethodService() {
    private var lastSelectionStart = -1
    private var lastSelectionEnd = -1

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
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(getColor(R.color.flowy_surface))
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
            root.addView(row)
        }
        val actionRow = createRow()
        listOf(
            "Clear all" to 1.5f,
            "Space" to 4f,
            "Voz" to 1.5f,
            "↵" to 1.5f
        ).forEach { (label, weight) ->
            val button = createKeyButton(label)
            if (label == "Clear all") {
                button.text = ""
                button.contentDescription = "Borrar todo el texto"
                button.compoundDrawableTintList = android.content.res.ColorStateList.valueOf(
                    getColor(R.color.flowy_text)
                )
                button.setCompoundDrawablesWithIntrinsicBounds(
                    android.R.drawable.ic_menu_delete,
                    0,
                    0,
                    0
                )
            }
            if (label == "Voz") {
                button.text = ""
                button.contentDescription = "Dictar por voz"
                button.compoundDrawableTintList = android.content.res.ColorStateList.valueOf(
                    getColor(R.color.flowy_text)
                )
                button.setCompoundDrawablesWithIntrinsicBounds(
                    android.R.drawable.ic_btn_speak_now,
                    0,
                    0,
                    0
                )
                button.setOnClickListener { startVoiceCapture() }
            }
            actionRow.addView(button, keyLayoutParams(weight))
        }
        root.addView(actionRow)
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
                            val committed = runCatching {
                                targetConnection.commitText(transcript, 1)
                            }.getOrDefault(false)
                            if (!committed) {
                                Toast.makeText(
                                    this@FlowyInputMethodService,
                                    "El campo de texto rechazó la transcripción.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }
            })
        }
        startActivity(intent)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun createRow() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
    }

    private fun keyLayoutParams(weight: Float = 1f) =
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight).apply {
            setMargins(dp(3), dp(3), dp(3), dp(3))
        }

    private fun createKeyButton(label: String) = Button(this).apply {
        val alternate = LONG_PRESS_ALTERNATES[label]
        text = label
        textSize = if (label.length == 1) 18f else 16f
        isAllCaps = false
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.CENTER
        includeFontPadding = false
        setPadding(0, dp(4), 0, dp(4))
        setTextColor(getColor(R.color.flowy_text))
        background = getDrawable(R.drawable.key_background)
        minWidth = 0
        minHeight = (56 * resources.displayMetrics.density).toInt()
        isFocusable = true
        contentDescription = if (alternate == null) label else "$label, mantén presionado para $alternate"
        setOnClickListener {
            if (label == "Clear all") clearAllText() else handleKey(label)
        }
        isLongClickable = alternate != null
        setOnLongClickListener {
            val symbol = alternate ?: return@setOnLongClickListener false
            currentInputConnection?.commitText(symbol, 1)
            true
        }
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
            "q" to "1", "w" to "2", "e" to "3", "r" to "4", "t" to "5",
            "y" to "6", "u" to "7", "i" to "8", "o" to "9", "p" to "0",
            "a" to "@", "s" to "#", "d" to "$", "f" to "%", "g" to "&",
            "h" to "-", "j" to "+", "k" to "(", "l" to ")", "ñ" to "!",
            "z" to "?", "x" to ":", "c" to ";", "v" to "/", "b" to "\"",
            "n" to "'", "m" to "\\", "," to "<", "." to ">"
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
