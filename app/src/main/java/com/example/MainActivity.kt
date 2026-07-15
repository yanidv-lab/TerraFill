package com.example

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.example.navigation.NavGraph
import com.example.ui.GameViewModel
import com.example.ui.theme.MyApplicationTheme
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Entry Activity for TerraFill.
 * Initializes edge-to-edge drawings, sets up the theme, and launches the NavGraph.
 */
class MainActivity : ComponentActivity() {
    private var gameViewModel: GameViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Global crash handler — shows a readable error screen instead of silently closing.
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val crashMsg = "Thread: ${thread.name}\n\n$sw"
                Log.e("TERRA_CRASH", crashMsg)
                startActivity(
                    Intent(this, CrashActivity::class.java).apply {
                        putExtra(CrashActivity.EXTRA_STACK_TRACE, crashMsg)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }
                )
                // Kill the broken process so the system starts a fresh one to host the
                // crash screen. Without this, a main-thread crash leaves the app frozen
                // and the crash screen never appears.
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(10)
            } catch (e: Exception) {
                Log.e("TERRA_CRASH", "Failed to launch crash screen", e)
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val navController = rememberNavController()
                val vm: GameViewModel = viewModel()
                gameViewModel = vm

                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    NavGraph(
                        navController = navController,
                        viewModel = vm,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        gameViewModel?.pauseAudio()
    }

    override fun onResume() {
        super.onResume()
        gameViewModel?.resumeAudio()
    }
}
