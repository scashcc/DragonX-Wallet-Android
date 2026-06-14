package cash.z.ecc.android.sdk.internal

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A [Twig] that appends every log message to a file so the sync "diary" survives the app being
 * closed/killed and can be retrieved later (e.g. `adb pull` from the app's external files dir).
 *
 * Twigs are silent by default; the app turns this on by planting it, typically together with a
 * console twig, e.g. `Twig.plant(TroubleshootingTwig() + FileTwig(logFile))`.
 *
 * The file is size-capped with a single rotation (`name` -> `name.1`) so it can never grow without
 * bound. Writes are synchronized and best-effort: any IO failure is swallowed so logging can never
 * crash or stall the sync engine.
 *
 * @param logFile the file to append to. Its parent directory is created if missing.
 * @param maxBytes when the active file exceeds this, it is rotated to "<name>.1" and a fresh file starts.
 * @param minPriority only messages with priority >= this are written (matches TroubleshootingTwig).
 */
class FileTwig(
    private val logFile: File,
    private val maxBytes: Long = 2L * 1024 * 1024,
    private val minPriority: Int = 0
) : Twig {

    private val lock = Any()
    private val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    init {
        runCatching {
            logFile.parentFile?.mkdirs()
            appendLine("===== DragonX sync log opened ${dateFormat.format(Date())} (pull: adb pull ${logFile.absolutePath}) =====")
        }
    }

    override fun twig(logMessage: String, priority: Int) {
        if (priority < minPriority) return
        val tags = if (Bush.leaves.isEmpty()) "" else Bush.leaves.joinToString(",", "[", "] ")
        appendLine("${dateFormat.format(Date())} $tags$logMessage")
    }

    private fun appendLine(line: String) {
        synchronized(lock) {
            runCatching {
                if (logFile.exists() && logFile.length() > maxBytes) {
                    val rotated = File(logFile.parentFile, logFile.name + ".1")
                    if (rotated.exists()) rotated.delete()
                    logFile.renameTo(rotated)
                }
                logFile.appendText(line + "\n")
            }
        }
    }
}
