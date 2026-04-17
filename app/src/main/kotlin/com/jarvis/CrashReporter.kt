package com.jarvis

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Date

/**
 * Writes any uncaught exception to `filesDir/last-crash.txt` before letting the
 * process die. MainActivity reads that file on next launch and shows the trace in
 * a dialog so the user can screenshot it without `adb`.
 */
object CrashReporter {

    private const val FILE = "last-crash.txt"
    private const val TAG = "JarvisCrash"

    fun install(context: Context) {
        val appFiles = context.filesDir
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, err ->
            runCatching { write(appFiles, thread, err) }
            Log.e(TAG, "Uncaught exception on ${thread.name}", err)
            prev?.uncaughtException(thread, err)
        }
    }

    fun readAndClear(context: Context): String? {
        val f = File(context.filesDir, FILE)
        if (!f.exists()) return null
        val text = runCatching { f.readText() }.getOrNull()
        runCatching { f.delete() }
        return text
    }

    fun appendNonFatal(context: Context, label: String, err: Throwable) {
        runCatching {
            val f = File(context.filesDir, FILE)
            val sw = StringWriter().also { err.printStackTrace(PrintWriter(it)) }
            f.appendText(
                buildString {
                    append("=== ").append(Date()).append(" | NON-FATAL: ").append(label).append(" ===\n")
                    append(sw.toString()).append("\n")
                }
            )
        }
    }

    private fun write(dir: File, thread: Thread, err: Throwable) {
        dir.mkdirs()
        val f = File(dir, FILE)
        val sw = StringWriter()
        err.printStackTrace(PrintWriter(sw))
        f.writeText(
            buildString {
                append("Jarvis crash @ ").append(Date()).append("\n")
                append("Thread: ").append(thread.name).append("\n\n")
                append(sw.toString())
            }
        )
    }
}
