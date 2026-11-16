package dev.flowy.keyboard

import android.inputmethodservice.InputMethodService
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout

class FlowyInputMethodService : InputMethodService() {
    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(12, 8, 12, 8)
        }
        val grid = GridLayout(this).apply {
            columnCount = 10
            rowCount = 3
            alignmentMode = GridLayout.ALIGN_BOUNDS
        }
        val keys = listOf(
            *"qwertyuiop".map(Char::toString).toTypedArray(),
            *"asdfghjkl".map(Char::toString).toTypedArray(),
            *"zxcvbnm".map(Char::toString).toTypedArray(),
            "⌫", "Space", "↵"
        )
        keys.forEach { label ->
            val button = Button(this).apply {
                text = label
                isFocusable = true
                setOnClickListener { handleKey(label) }
            }
            grid.addView(button, GridLayout.LayoutParams().apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(2, 2, 2, 2)
            })
        }
        root.addView(grid)
        root.addView(Button(this).apply {
            text = "Clear all"
            contentDescription = "Clear all text"
            setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_menu_delete, 0, 0, 0)
            isFocusable = true
            setOnClickListener { clearAllText() }
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        root.post { root.findFocus()?.let { } ?: grid.getChildAt(0)?.requestFocus() }
        return root
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
            else -> connection.commitText(label, 1)
        }
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
