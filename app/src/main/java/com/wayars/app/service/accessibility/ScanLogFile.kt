package com.wayars.app.service.accessibility

import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes every [ScanDiagnostics.record] event to a plain-text file for the
 * whole duration of one Active session, so a live log that scrolls too fast
 * to read no longer needs to be caught by eye — turn Active on, reproduce
 * the issue, turn Active off, then share the finished file.
 *
 * Exactly one file per Active session: [start] creates and opens it the
 * instant Active turns on, [stop] closes it the instant Active turns off.
 * [lastCompletedLogFile] only ever points at a FINISHED (closed) file, so a
 * share action built on top of it never offers a file that's still being
 * written to mid-session.
 */
object ScanLogFile {

    private var appContext: Context? = null

    /** Call once, e.g. from Application.onCreate(). */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private var writer: BufferedWriter? = null
    private var currentFile: File? = null

    private val _lastCompletedLogFile = MutableStateFlow<File?>(null)
    val lastCompletedLogFile: StateFlow<File?> = _lastCompletedLogFile

    private val fileNameFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    private val lineTimeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    /** Old session files beyond this count are deleted when a new session starts. */
    private const val MAX_KEPT_FILES = 10

    @Synchronized
    fun start() {
        val context = appContext ?: return
        stopInternal() // defensive: in case start() is ever called twice without a stop()

        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "wayars_logs")
        if (!dir.exists()) dir.mkdirs()
        cleanupOldFiles(dir)

        val file = File(dir, "scan_${fileNameFormat.format(Date())}.log")
        try {
            val w = BufferedWriter(FileWriter(file, true))
            w.write("=== WayArs scan log started ${Date()} (SDK ${Build.VERSION.SDK_INT}) ===")
            w.newLine()
            w.flush()
            writer = w
            currentFile = file
        } catch (_: Exception) {
            // Storage unavailable — scanning itself must never be blocked by this.
        }
    }

    @Synchronized
    fun append(line: String) {
        val w = writer ?: return
        try {
            w.write("${lineTimeFormat.format(Date())}  $line")
            w.newLine()
            w.flush()
        } catch (_: Exception) {
            // A failed diagnostic write must never crash scanning.
        }
    }

    @Synchronized
    fun stop() {
        stopInternal()
    }

    private fun stopInternal() {
        val w = writer
        val file = currentFile
        writer = null
        currentFile = null
        if (w != null) {
            try {
                w.write("=== WayArs scan log stopped ${Date()} ===")
                w.newLine()
                w.close()
            } catch (_: Exception) {
                // Ignore — nothing more we can do at this point.
            }
            if (file != null && file.exists() && file.length() > 0) {
                _lastCompletedLogFile.value = file
            }
        }
    }

    private fun cleanupOldFiles(dir: File) {
        val files = dir.listFiles { f -> f.isFile && f.name.startsWith("scan_") } ?: return
        val sorted = files.sortedBy { it.lastModified() } // oldest first
        val excess = sorted.size - (MAX_KEPT_FILES - 1) // leave room for the file about to be created
        if (excess > 0) sorted.take(excess).forEach { it.delete() }
    }
}
