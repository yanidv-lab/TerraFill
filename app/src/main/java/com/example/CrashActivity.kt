package com.example

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * The screen shown after a crash. Launched automatically by the
 * UncaughtExceptionHandler in MainActivity. Uses plain Android Views (no Compose)
 * so it is immune to any Compose-related crash that triggered it.
 *
 * Debug builds show the full stack trace, which is what makes a bug reportable
 * during development. Release builds show an apology instead: a player has no use
 * for a Java stack trace, and the crash is already on its way to Play's Android
 * Vitals with a far better trace than anyone could screenshot.
 */
class CrashActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val showDetails = BuildConfig.DEBUG
        val stackTrace = intent.getStringExtra(EXTRA_STACK_TRACE)
            ?: "No crash information available."

        // Build a simple full-screen layout in code — no XML needed.
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 64, 32, 32)
            setBackgroundColor(0xFF1A0010.toInt())
        }

        root.addView(TextView(this).apply {
            text = if (showDetails) "⚠ APP CRASHED" else "⚠ SOMETHING WENT WRONG"
            textSize = 22f
            setTextColor(0xFFFF4466.toInt())
            setPadding(0, 0, 0, 16)
            typeface = android.graphics.Typeface.MONOSPACE
        })

        root.addView(TextView(this).apply {
            text = if (showDetails) {
                "Error details (screenshot this):"
            } else {
                "TerraFill hit an unexpected problem and had to stop.\n\n" +
                    "Your progress is saved. Reopen the app to keep playing.\n\n" +
                    "The fault has been reported automatically - sorry about that."
            }
            textSize = 12f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 0, 0, 8)
            typeface = android.graphics.Typeface.MONOSPACE
        })

        if (showDetails) {
            val scrollView = ScrollView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
                )
            }

            scrollView.addView(TextView(this).apply {
                text = stackTrace
                textSize = 10f
                setTextColor(0xFFE0E0E0.toInt())
                typeface = android.graphics.Typeface.MONOSPACE
                setPadding(8, 8, 8, 8)
                setBackgroundColor(0xFF2A0020.toInt())
            })

            root.addView(scrollView)
        } else {
            // Push the button to the bottom of the screen without the trace pane.
            root.addView(android.view.View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
                )
            })
        }

        root.addView(Button(this).apply {
            text = "CLOSE APP"
            setBackgroundColor(0xFFFF4466.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 16 }
            setOnClickListener {
                finishAffinity()
            }
        })

        setContentView(root)
    }

    companion object {
        const val EXTRA_STACK_TRACE = "stack_trace"

        fun createIntent(source: Activity, stackTrace: String): Intent =
            Intent(source, CrashActivity::class.java).apply {
                putExtra(EXTRA_STACK_TRACE, stackTrace)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
    }
}
