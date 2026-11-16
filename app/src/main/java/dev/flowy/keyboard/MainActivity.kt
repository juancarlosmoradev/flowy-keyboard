package dev.flowy.keyboard

import android.app.Activity
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        layout.addView(TextView(this).apply { text = "Flowy Keyboard test" })
        layout.addView(EditText(this).apply {
            hint = "Type here to test Flowy"
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setSingleLine(true)
        })
        setContentView(layout)
    }
}
