package cash.z.ecc.android

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraXConfig
import cash.z.ecc.android.di.DependenciesHolder
import cash.z.ecc.android.sdk.Initializer
import cash.z.ecc.android.ext.tryWithWarning
import cash.z.ecc.android.feedback.FeedbackCoordinator
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.sdk.model.ZcashNetwork
import cash.z.ecc.android.util.twig
import kotlinx.coroutines.*

class ZcashWalletApp : Application(), CameraXConfig.Provider {

    private val coordinator: FeedbackCoordinator
        get() = DependenciesHolder.feedbackCoordinator

    lateinit var defaultNetwork: ZcashNetwork

    var creationTime: Long = 0
        private set

    var creationMeasured: Boolean = false

    /** The amount of transparent funds that need to accumulate before autoshielding is triggered */
    val autoshieldThreshold: Long = Zatoshi.ZATOSHI_PER_ZEC // 1 ZEC

    /**
     * Intentionally private Scope for use with launching Feedback jobs. The feedback object has the
     * longest scope in the app because it needs to be around early in order to measure launch times
     * and stick around late in order to catch crashes. We intentionally don't expose this because
     * application objects can have odd lifecycles, given that there is no clear onDestroy moment in
     * many cases.
     */
    private var feedbackScope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /**
     * Process-lifetime scope that OWNS the block-sync engine.
     *
     * The [cash.z.ecc.android.sdk.Synchronizer] is a process singleton, but it used to be started
     * with the Activity's `lifecycleScope`. `Synchronizer.start(parentScope)` parents the whole sync
     * job (download → validate → scan loop) to that scope, and `lifecycleScope` is cancelled in the
     * Activity's `onDestroy` — which fires when the app is swiped out of Recents (and on some OEM
     * task kills) even while a foreground service keeps the PROCESS alive. The result: the foreground
     * service held an empty, syncing-nothing process, "long-term background sync" silently stopped,
     * and an in-progress 合并零钱 (which relies on the background scanner to advance block height and
     * confirm its batches) stalled forever waiting for confirmations that could never arrive.
     *
     * Parenting sync to this process-lifetime scope instead means it keeps running for as long as the
     * process lives and only stops when explicitly told — `synchronizer.stop()` is still called on
     * wipe / server-switch / corruption-recovery, and each of those cancels the synchronizer's own
     * child scope without touching this one (so it can safely parent the next synchronizer instance).
     */
    val syncScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)

        // Setting a global reference to the application object is icky; we should try to refactor
        // this away if possible.  Doing this in attachBaseContext instead of onCreate()
        // to avoid any lifecycle issues, as certain components can run before Application.onCreate()
        // (like ContentProvider initialization), but attachBaseContext will still run before that.
        instance = this
    }

    override fun onCreate() {
        super.onCreate()

        // Register this before the uncaught exception handler, because we want to make sure the
        // exception handler also doesn't do disk IO.  Since StrictMode only applies for debug builds,
        // we'll also see the crashes during development right away and won't miss them if they aren't
        // reported by the crash reporting.
        if (BuildConfig.DEBUG) {
            StrictModeHelper.enableStrictMode()
        }
        // Persistent sync "diary". Twigs are SILENT by default, so release builds previously logged
        // nothing at all — a slow/"stuck" sync was undiagnosable (no PC needed: the log survives to a
        // file that can be pulled later via:
        //   adb pull /sdcard/Android/data/dragonx.android/files/dragonx-sync.log
        // We always plant a FileTwig (rotating, size-capped) plus a logcat mirror for live debugging.
        // This logs the block download/validate/scan steps so we can SEE which batch a slow scan is on
        // and how long each batch takes. File IO is best-effort and can never crash/stall the engine.
        runCatching {
            val logFile = java.io.File(getExternalFilesDir(null) ?: filesDir, "dragonx-sync.log")
            val fileTwig = cash.z.ecc.android.sdk.internal.FileTwig(logFile)
            // SDK engine logs (the scan loop lives here — this is what reveals a stuck/slow batch).
            cash.z.ecc.android.sdk.internal.Twig.plant(
                cash.z.ecc.android.sdk.internal.TroubleshootingTwig(
                    printer = { android.util.Log.i("DRGXSYNC", it); Unit }
                ) + fileTwig
            )
            // App-level logs (processor errors, auto-recovery, uncaught exceptions) -> same file.
            cash.z.ecc.android.util.Twig.plant(
                cash.z.ecc.android.util.TroubleshootingTwig(
                    printer = { android.util.Log.i("DRGXWALLET", it); fileTwig.twig(it, 0); Unit }
                )
            )
        }

        // Setup handler for uncaught exceptions.
        Thread.getDefaultUncaughtExceptionHandler()?.let {
            Thread.setDefaultUncaughtExceptionHandler(ExceptionReporter(it))
        }
        creationTime = System.currentTimeMillis()

        defaultNetwork = ZcashNetwork.from(resources.getInteger(R.integer.zcash_network_id))
        feedbackScope.launch {
            coordinator.feedback.start()
        }
    }

    override fun getCameraXConfig(): CameraXConfig {
        return Camera2Config.defaultConfig()
    }

    companion object {
        lateinit var instance: ZcashWalletApp
    }

    /**
     * @param feedbackCoordinator inject a provider so that if a crash happens before configuration
     * is complete, we can lazily initialize all the feedback objects at this moment so that we
     * don't have to add any time to startup.
     */
    inner class ExceptionReporter(private val ogHandler: Thread.UncaughtExceptionHandler) :
        Thread.UncaughtExceptionHandler {
        override fun uncaughtException(t: Thread?, e: Throwable?) {
            // Log the FULL stack trace (not just the message) into the sync log, so a user-reported
            // UI crash — e.g. tapping "备份助记词" — can be pinpointed to an exact file:line straight
            // from their exported log (我的 → 诊断 → 导出/分享日志), without a PC or adb. Wrapped in
            // runCatching so diagnostic logging can never itself disturb crash handling. The marker
            // line makes it trivial to find at the tail of the log.
            runCatching {
                twig("===== FATAL UNCAUGHT EXCEPTION on thread '${t?.name}' (${e?.javaClass?.name}) =====")
                twig("Uncaught Exception:\n${e?.stackTraceToString() ?: "$e"}")
                // Also persist to a dedicated, never-flooded crash file. The sync log keeps growing
                // (and rotates at 2MB) once the user reopens the app, which can bury/evict the crash;
                // this file keeps the last crash intact for the export feature to attach.
                val crashFile = java.io.File(getExternalFilesDir(null) ?: filesDir, "dragonx-crash.log")
                val stamp = java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())
                crashFile.appendText(
                    "===== CRASH $stamp  v${BuildConfig.VERSION_NAME}  thread='${t?.name}' =====\n" +
                        "${e?.stackTraceToString() ?: e}\n\n"
                )
            }

            // A corrupt block/data database ("database disk image is malformed") can be thrown from
            // any path that touches it (the transactions paging query, the scanner, etc.). It cannot
            // be fixed by retrying; the cure is to wipe the (re-downloadable) block data and resync.
            // The seed/keys live in separate secure storage and are NOT touched. We do this from the
            // global handler so corruption is recovered no matter which thread/coroutine hit it
            // (MainActivity only covers the scan loop). A short cooldown prevents a restart loop.
            if (isBlockDbCorruption(e) && recoverFromBlockDbCorruption()) {
                return // recoverFromBlockDbCorruption() restarts the process; never reached normally
            }

            // Things can get pretty crazy during a fatal exception
            // so be cautious here to avoid freezing the app
            tryWithWarning("Unable to report fatal crash") {
                // note: these are the only reported crashes that set isFatal=true
                coordinator.feedback.report(e, true)
            }
            tryWithWarning("Unable to flush the feedback coordinator") {
                coordinator.flush()
            }

            try {
                // can do this if necessary but first verify that we need it
                runBlocking {
                    coordinator.await()
                    coordinator.feedback.stop()
                }
            } catch (t: Throwable) {
                twig("WARNING: failed to wait for the feedback observers to complete.")
            } finally {
                // it's important that this always runs so we use the finally clause here
                // rather than another tryWithWarning block
                ogHandler.uncaughtException(t, e)
                Thread.sleep(2000L)
            }
        }

        private fun isBlockDbCorruption(error: Throwable?): Boolean {
            var c: Throwable? = error
            var depth = 0
            while (c != null && depth < 15) {
                if (c is android.database.sqlite.SQLiteDatabaseCorruptException) return true
                val msg = (c.message ?: "").lowercase(java.util.Locale.US)
                if (msg.contains("malformed") ||
                    msg.contains("sqlite_corrupt") ||
                    msg.contains("database disk image") ||
                    msg.contains("file is not a database") ||
                    msg.contains("(code 11")
                ) {
                    return true
                }
                c = c.cause
                depth++
            }
            return false
        }

        /** Erase the (re-downloadable) block data and restart. @return true if recovery started. */
        private fun recoverFromBlockDbCorruption(): Boolean {
            val app = this@ZcashWalletApp
            return try {
                val prefs = app.getSharedPreferences("dragonx_recovery", Context.MODE_PRIVATE)
                val now = System.currentTimeMillis()
                if (now - prefs.getLong("last_auto_recovery_ms", 0L) < 120_000L) {
                    twig("Global DB-corruption recovery skipped (cooldown); letting it crash normally.")
                    return false
                }
                prefs.edit().putLong("last_auto_recovery_ms", now).apply()
                twig("Global handler: block DB corrupt -> erasing block data and restarting (keys kept).")
                // erase() is a suspend fun; the uncaught handler is not a coroutine, so block on it.
                runCatching { runBlocking { Initializer.erase(app, app.defaultNetwork) } }
                    .onFailure { twig("Global recovery: erase failed: $it") }
                app.packageManager.getLaunchIntentForPackage(app.packageName)?.component?.let {
                    app.startActivity(Intent.makeRestartActivityTask(it))
                }
                Runtime.getRuntime().exit(0)
                true
            } catch (t: Throwable) {
                twig("Global recovery failed: $t")
                false
            }
        }
    }
}
