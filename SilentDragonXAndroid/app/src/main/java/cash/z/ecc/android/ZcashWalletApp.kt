package cash.z.ecc.android

import android.app.Application
import android.content.Context
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraXConfig
import cash.z.ecc.android.di.DependenciesHolder
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
    }
}
