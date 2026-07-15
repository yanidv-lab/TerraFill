package com.example

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * A simple crash-reporter screen that displays the full stack trace whenever
 * the app crashes. Launched automatically by the UncaughtExceptionHandler in
 * MainActivity. Uses plain Android Views (no Compose) so it is immune to any
 * Compose-related crash that triggered it.
 */
class CrashActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val stackTrace = intent.getStringExtra(EXTRA_STACK_TRACE)
            ?: "No crash information available."

        // Build a simple full-screen layout in code — no XML needed.
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 64, 32, 32)
            setBackgroundColor(0xFF1A0010.toInt())
        }

        root.addView(TextView(this).apply {
            text = "⚠ APP CRASHED"
            textSize = 22f
            setTextColor(0xFFFF4466.toInt())
            setPadding(0, 0, 0, 16)
            typeface = android.graphics.Typeface.MONOSPACE
        })

        root.addView(TextView(this).apply {
            text = "Error details (screenshot this):"
            textSize = 12f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 0, 0, 8)
            typeface = android.graphics.Typeface.MONOSPACE
        })

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
