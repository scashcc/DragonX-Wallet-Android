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
        if (BuildConfig.DEBUG) {
            cash.z.ecc.android.sdk.internal.Twig.plant(
                cash.z.ecc.android.sdk.internal.TroubleshootingTwig(
                    printer = { android.util.Log.d("@TWIG", it); Unit }
                )
            )
            cash.z.ecc.android.util.Twig.enabled(true)
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
            twig("Uncaught Exception: $e caused by: ${e?.cause}")

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
                runCatching { Initializer.erase(app, app.defaultNetwork) }
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
