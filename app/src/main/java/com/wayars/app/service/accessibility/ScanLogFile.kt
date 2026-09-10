package com.wayars.app.service.accessibility

import android.content.Context
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes scan-related log lines to a plain-text file while the
 * AccessibilityService is actively scanning the screen.
 *
 * Lifecycle:
 *  - [start] creates a brand-new file `scan_yyyyMMdd_HHmmss.log` inside
 *    `<externalFilesDir>/wayars_logs/` and remembers it as the "current" file.
 *  - [append] appends one timestamped line to that file, if one exists.
 *  - [stop] forgets the current file (no more lines are written until the
 *    next [start]).
 *
 * Design notes:
 *  - No stream is kept open between calls: each [append] opens the file in
 *    append mode, writes one line, and closes it again. This keeps the
 *    object simple and crash-safe (nothing to leak or forget to flush) at
 *    the cost of a per-line file open, which is fine for the volume of
 *    lines a screen scan produces.
 *  - Every public method is `@Synchronized`, so concurrent calls from the
 *    AccessibilityService's callback thread, a coroutine, or the UI thread
 *    can never interleave and corrupt the file.
 *  - Every filesystem operation is wrapped in try/catch: a logging failure
 *    (storage unmounted, permission revoked, disk full, ...) must never
 *    crash the app or interrupt scanning.
 */
object ScanLogFile {

    private const val LOG_DIR_NAME = "wayars_logs"
    private const val FILE_PREFIX = "scan_"
    private const val FILE_SUFFIX = ".log"

    /** How many most-recent log files to keep; older ones are deleted on [start]. */
    private const val MAX_KEPT_FILES = 10

    private val fileNameFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    private val lineTimeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    /** The file currently being written to, or null if no session is active. */
    private var currentFile: File? = null

    /**
     * Starts a new logging session: creates `wayars_logs/` if needed, prunes
     * old files down to [MAX_KEPT_FILES], and opens a new timestamped file
     * as the current log target.
     *
     * Safe to call multiple times in a row (e.g. Active toggled on twice) —
     * each call simply starts a fresh file. Safe to call with a `context`
     * whose external storage is unavailable: on any failure, [currentFile]
     * is left `null` and subsequent [append] calls are silently no-ops.
     */
    @Synchronized
    fun start(context: Context) {
        try {
            val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
            val logDir = File(baseDir, LOG_DIR_NAME)
            if (!logDir.exists()) {
                logDir.mkdirs()
            }

            cleanupOldFiles(logDir)

            val fileName = "$FILE_PREFIX${fileNameFormat.format(Date())}$FILE_SUFFIX"
            val newFile = File(logDir, fileName)

            // Touch the file now so it exists even if the first append() is
            // delayed or never comes, and so any failure surfaces here
            // rather than silently inside the first append().
            FileWriter(newFile, true).use { writer ->
                writer.write("=== WayArs scan log started ${Date()} ===")
                writer.write(System.lineSeparator())
            }

            currentFile = newFile
        } catch (_: Exception) {
            // Storage unavailable, permission denied, etc. Scanning itself
            // must never be blocked by a logging failure.
            currentFile = null
        }
    }

    /**
     * Appends one timestamped line to the current log file.
     *
     * Does nothing (no exception) if [start] was never called, if [stop]
     * has already been called, or if the write itself fails for any reason.
     */
    @Synchronized
    fun append(text: String) {
        val file = currentFile ?: return
        try {
            FileWriter(file, true).use { writer ->
                writer.write("[${lineTimeFormat.format(Date())}] $text")
                writer.write(System.lineSeparator())
            }
        } catch (_: Exception) {
            // A failed log write must never crash the AccessibilityService.
        }
    }

    /**
     * Ends the current logging session. After this call, [append] is a
     * no-op until [start] is called again.
     */
    @Synchronized
    fun stop() {
        val file = currentFile
        currentFile = null
        if (file != null) {
            try {
                FileWriter(file, true).use { writer ->
                    writer.write("=== WayArs scan log stopped ${Date()} ===")
                    writer.write(System.lineSeparator())
                }
            } catch (_: Exception) {
                // Ignore — nothing more can be done at this point.
            }
        }
    }

    /**
     * Deletes the oldest files in [dir] so that, once the new file about to
     * be created is added, at most [MAX_KEPT_FILES] remain.
     */
    private fun cleanupOldFiles(dir: File) {
        try {
            val files = dir.listFiles { f ->
                f.isFile && f.name.startsWith(FILE_PREFIX) && f.name.endsWith(FILE_SUFFIX)
            } ?: return

            if (files.size < MAX_KEPT_FILES) return

            val oldestFirst = files.sortedBy { it.lastModified() }
            val excess = oldestFirst.size - (MAX_KEPT_FILES - 1) // leave room for the new file
            if (excess > 0) {
                oldestFirst.take(excess).forEach { it.delete() }
            }
        } catch (_: Exception) {
            // Cleanup is best-effort; a failure here must not block start().
        }
    }
}
