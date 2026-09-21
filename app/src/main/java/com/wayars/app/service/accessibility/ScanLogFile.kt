package com.wayars.app.service.accessibility

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
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
 *  - Every actual file write happens on [ioScope] — a single-thread-
 *    equivalent background dispatcher, chosen specifically so writes stay
 *    strictly ORDERED (never interleaved/reordered) without needing to hold
 *    a lock on the calling thread. [append] used to do its file I/O
 *    synchronously, inline, on whatever thread called it — which in
 *    practice was the AccessibilityService's main thread, called on EVERY
 *    qualifying accessibility event system-wide. During ordinary phone use
 *    that's a near-continuous stream of open/write/close calls blocking the
 *    main thread, a real source of "app not responding". Dispatching the
 *    write instead keeps the caller (onAccessibilityEvent) non-blocking.
 *  - [MAX_FILE_SIZE_BYTES] bounds how large a single log file can grow
 *    during one long-running session — previously only the FILE COUNT was
 *    capped (via [cleanupOldFiles], which only prunes at the START of a new
 *    session), so one session left running for many hours/days (e.g. while
 *    the app kept scanning in the background) could grow one file
 *    indefinitely. [append] now rotates to a fresh file, pruning old ones
 *    the same way [start] does, once the current file crosses that size.
 *  - Every filesystem operation is wrapped in try/catch: a logging failure
 *    (storage unmounted, permission revoked, disk full, ...) must never
 *    crash the app or interrupt scanning.
 */
object ScanLogFile {

    private const val LOG_DIR_NAME = "wayars_logs"
    private const val FILE_PREFIX = "scan_"
    private const val FILE_SUFFIX = ".log"

    /** How many most-recent log files to keep; older ones are deleted on [start] and on rotation. */
    private const val MAX_KEPT_FILES = 10

    /** Rotate to a fresh file once the current one reaches this size. */
    private const val MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024 // 5 MB

    private val fileNameFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    private val lineTimeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    // limitedParallelism(1) on the IO dispatcher: real background threads
    // (unlike Dispatchers.Main), but only one at a time, so writes queued
    // one after another from any calling thread still land in the file in
    // the order they were queued.
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    /** The file currently being written to, or null if no session is active. */
    private var currentFile: File? = null

    /** The Context passed to [start], kept only so a mid-session rotation
     *  (triggered from [append], off the caller's thread) can re-derive the
     *  same log directory without needing the caller to pass one again. */
    private var currentContext: Context? = null

    /**
     * Points at the most recently FINISHED (i.e. [stop]-closed) log file,
     * or null if no session has completed yet. Only ever updated by [stop],
     * so a UI observing this never gets offered a file that a session is
     * still actively writing to. Used by DashboardScreen's "share log" action.
     */
    private val _lastCompletedLogFile = MutableStateFlow<File?>(null)
    val lastCompletedLogFile: StateFlow<File?> = _lastCompletedLogFile

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
    fun start(context: Context) {
        currentContext = context.applicationContext
        ioScope.launch { startOnIoThread(context.applicationContext) }
    }

    private fun startOnIoThread(context: Context) {
        try {
            val baseDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
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
     * Does nothing if [start] was never called, if [stop] has already been
     * called, or if the write itself fails for any reason. The actual write
     * (and the size check that may trigger a rotation) happens on
     * [ioScope], never on the calling thread.
     */
    fun append(text: String) {
        val timestamp = lineTimeFormat.format(Date()) // cheap; fine to format on the caller's thread
        ioScope.launch { appendOnIoThread(timestamp, text) }
    }

    private fun appendOnIoThread(timestamp: String, text: String) {
        val file = currentFile ?: return
        try {
            if (file.length() >= MAX_FILE_SIZE_BYTES) {
                rotateOnIoThread(file)
            }
            val target = currentFile ?: return
            FileWriter(target, true).use { writer ->
                writer.write("[$timestamp] $text")
                writer.write(System.lineSeparator())
            }
        } catch (_: Exception) {
            // A failed log write must never crash the AccessibilityService.
        }
    }

    /** Closes off [full] (marks it as completed, same as [stop] would) and
     *  opens a brand-new file to keep writing to, pruning old ones exactly
     *  like [start] does. */
    private fun rotateOnIoThread(full: File) {
        try {
            FileWriter(full, true).use { writer ->
                writer.write("=== WayArs scan log rotated (size limit) ${Date()} ===")
                writer.write(System.lineSeparator())
            }
            if (full.length() > 0) {
                _lastCompletedLogFile.value = full
            }
        } catch (_: Exception) {
            // Ignore — still try to open the next file below.
        }
        val context = currentContext ?: return
        startOnIoThread(context)
    }

    /**
     * Ends the current logging session. After this call, [append] is a
     * no-op until [start] is called again.
     */
    fun stop() {
        ioScope.launch { stopOnIoThread() }
    }

    private fun stopOnIoThread() {
        val file = currentFile
        currentFile = null
        if (file != null) {
            try {
                FileWriter(file, true).use { writer ->
                    writer.write("=== WayArs scan log stopped ${Date()} ===")
                    writer.write(System.lineSeparator())
                }
                if (file.exists() && file.length() > 0) {
                    _lastCompletedLogFile.value = file
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
