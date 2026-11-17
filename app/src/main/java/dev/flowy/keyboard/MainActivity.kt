package dev.flowy.keyboard

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(13, 16, 28)
        window.navigationBarColor = Color.rgb(13, 16, 28)

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(22), dp(24), dp(28))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(21, 25, 42), Color.rgb(11, 14, 24))
            )
        }
        val brandRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        brandRow.addView(TextView(this).apply {
            text = "F"
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(13, 20, 32))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(117, 241, 220), Color.rgb(130, 169, 255))
            ).apply { cornerRadius = dp(16).toFloat() }
        }, LinearLayout.LayoutParams(dp(42), dp(42)))
        val brandText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        brandText.addView(TextView(this).apply {
            text = "FLOWY"
            textSize = 14f
            letterSpacing = 0.18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(245, 247, 255))
        })
        brandText.addView(TextView(this).apply {
            text = "TECLADO PERSONAL"
            textSize = 9f
            letterSpacing = 0.14f
            setTextColor(Color.rgb(139, 151, 176))
        })
        brandRow.addView(brandText)
        page.addView(brandRow)

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(64), 0, dp(34))
        }
        hero.addView(TextView(this).apply {
            text = "Tu voz.\nTus palabras."
            textSize = 34f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            letterSpacing = -0.02f
            setTextColor(Color.rgb(247, 248, 255))
        })
        hero.addView(TextView(this).apply {
            text = "Escribe con Flowy o dicta sin salir de tu espacio."
            textSize = 15f
            setTextColor(Color.rgb(157, 168, 190))
            setPadding(0, dp(12), 0, 0)
        })
        page.addView(hero)

        page.addView(TextView(this).apply {
            text = "CAMPO DE PRUEBA"
            textSize = 10f
            letterSpacing = 0.16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(112, 226, 208))
            setPadding(dp(2), 0, 0, dp(10))
        })

        val inputCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(17), dp(15), dp(17), dp(14))
            background = GradientDrawable().apply {
                setColor(Color.rgb(27, 33, 51))
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), Color.rgb(56, 68, 91))
            }
        }
        inputCard.addView(TextView(this).apply {
            text = "PRUÉBALO AQUÍ"
            textSize = 9f
            letterSpacing = 0.14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(141, 154, 180))
        })
        inputCard.addView(EditText(this).apply {
            hint = "Escribe algo aquí"
            textSize = 20f
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_DONE
            setTextColor(Color.rgb(247, 248, 255))
            setHintTextColor(Color.rgb(111, 124, 149))
            background = null
            setPadding(0, dp(13), 0, dp(5))
            contentDescription = "Campo de prueba de Flowy"
        })
        page.addView(inputCard, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        page.addView(TextView(this).apply {
            text = "Toca el campo para activar el teclado. Mantén una tecla para ver sus alternativas."
            textSize = 12f
            setTextColor(Color.rgb(130, 143, 168))
            setPadding(dp(3), dp(14), dp(3), 0)
        })
        setContentView(page)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
