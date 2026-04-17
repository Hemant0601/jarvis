package com.jarvis

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.jarvis.ui.JarvisApp
import com.jarvis.ui.theme.JarvisTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val previousCrash = CrashReporter.readAndClear(applicationContext)
        setContent {
            JarvisTheme {
                var showCrash by remember { mutableStateOf(previousCrash != null) }
                JarvisApp(startInCapture = intent?.action == "com.jarvis.action.CAPTURE")
                if (showCrash && previousCrash != null) {
                    AlertDialog(
                        onDismissRequest = { showCrash = false },
                        confirmButton = {
                            TextButton(onClick = { showCrash = false }) { Text("Dismiss") }
                        },
                        title = { Text("Jarvis crashed last run") },
                        text = {
                            Text(
                                text = previousCrash,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                            )
                        },
                    )
                }
            }
        }
    }
}
