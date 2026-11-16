package dev.flowy.keyboard

import android.inputmethodservice.InputMethodService
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
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
        root.post { root.findFocus()?.let { } ?: grid.getChildAt(0)?.requestFocus() }
        return root
    }

    private fun handleKey(label: String) {
        val connection = currentInputConnection ?: return
        when (label) {
            "⌫" -> connection.deleteSurroundingText(1, 0)
            "Space" -> connection.commitText(" ", 1)
            "↵" -> connection.performEditorAction(currentInputEditorInfo?.imeOptions ?: 0)
            else -> connection.commitText(label, 1)
        }
    }
}
