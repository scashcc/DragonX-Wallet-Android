package cash.z.ecc.android.ui

import android.Manifest
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Vibrator
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager.Authenticators.*
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.*
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavDirections
import androidx.navigation.Navigator
import androidx.navigation.findNavController
import cash.z.ecc.android.R
import cash.z.ecc.android.ZcashWalletApp
import cash.z.ecc.android.databinding.DialogFirstUseMessageBinding
import cash.z.ecc.android.di.DependenciesHolder
import cash.z.ecc.android.ext.*
import cash.z.ecc.android.feedback.Feedback
import cash.z.ecc.android.feedback.FeedbackCoordinator
import cash.z.ecc.android.feedback.LaunchMetric
import cash.z.ecc.android.feedback.Report
import cash.z.ecc.android.feedback.Report.Error.NonFatal.Reorg
import cash.z.ecc.android.feedback.Report.NonUserAction.FEEDBACK_STOPPED
import cash.z.ecc.android.feedback.Report.NonUserAction.SYNC_START
import cash.z.ecc.android.feedback.Report.Tap.COPY_ADDRESS
import cash.z.ecc.android.feedback.Report.Tap.COPY_TRANSPARENT_ADDRESS
import cash.z.ecc.android.sdk.Initializer
import cash.z.ecc.android.sdk.SdkSynchronizer
import cash.z.ecc.android.sdk.db.entity.ConfirmedTransaction
import cash.z.ecc.android.sdk.exception.CompactBlockProcessorException
import cash.z.ecc.android.sdk.ext.BatchMetrics
import cash.z.ecc.android.sdk.ext.ZcashSdk
import cash.z.ecc.android.sdk.ext.toAbbreviatedAddress
import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.android.ui.history.HistoryViewModel
import cash.z.ecc.android.ui.util.MemoUtil
import cash.z.ecc.android.util.twig
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity(R.layout.main_activity) {

    val mainViewModel: MainViewModel by viewModels()

    val feedback: Feedback = DependenciesHolder.feedback

    val feedbackCoordinator: FeedbackCoordinator = DependenciesHolder.feedbackCoordinator

    val clipboard: ClipboardManager = DependenciesHolder.clipboardManager

    val historyViewModel: HistoryViewModel by viewModels()

    private var syncStarted = false

    private val mediaPlayer: MediaPlayer = MediaPlayer()
    private var snackbar: Snackbar? = null
    private var dialog: Dialog? = null
    private var ignoreScanFailure: Boolean = false

    var navController: NavController? = null
    private val navInitListeners: MutableList<() -> Unit> = mutableListOf()

    private val hasCameraPermission
        get() = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

    val latestHeight: BlockHeight?
        get() = DependenciesHolder.synchronizer.latestHeight

    override fun onCreate(savedInstanceState: Bundle?) {
        lifecycleScope.launch {
            feedback.start()
        }
        super.onCreate(savedInstanceState)

        initNavigation()
        initLoadScreen()

        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        setWindowFlag(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS, false)
        setWindowFlag(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION, false)
    }

    override fun onResume() {
        super.onResume()
        // keep track of app launch metrics
        // (how long does it take the app to open when it is not already in the foreground)
        ZcashWalletApp.instance.let { app ->
            if (!app.creationMeasured) {
                app.creationMeasured = true
                feedback.report(LaunchMetric())
            }
        }
    }

    override fun onDestroy() {
        lifecycleScope.launch {
            feedback.report(FEEDBACK_STOPPED)
            feedback.stop()
        }
        super.onDestroy()
    }

    private fun setWindowFlag(bits: Int, on: Boolean) {
        val win = window
        val winParams = win.attributes
        if (on) {
            winParams.flags = winParams.flags or bits
        } else {
            winParams.flags = winParams.flags and bits.inv()
        }
        win.attributes = winParams
    }

    private fun initNavigation() {
        navController = findNavController(R.id.nav_host_fragment)
        navController!!.addOnDestinationChangedListener { _, _, _ ->
            // hide the keyboard anytime we change destinations
            getSystemService<InputMethodManager>()?.hideSoftInputFromWindow(
                this@MainActivity.window.decorView.rootView.windowToken,
                InputMethodManager.HIDE_NOT_ALWAYS
            )
        }

        for (listener in navInitListeners) {
            listener()
        }
        navInitListeners.clear()
    }

    private fun initLoadScreen() {
        lifecycleScope.launchWhenResumed {
            mainViewModel.loadingMessage.collect { message ->
                onLoadingMessage(message)
            }
        }
    }

    private fun onLoadingMessage(message: String?) {
        twig("Applying loading message: $message")
        // TODO: replace with view binding
        findViewById<View>(R.id.container_loading).goneIf(message == null)
        findViewById<TextView>(R.id.text_message).text = message
    }

    fun popBackTo(@IdRes destination: Int, inclusive: Boolean = false) {
        navController?.popBackStack(destination, inclusive)
    }

    fun safeNavigate(navDirections: NavDirections) =
        safeNavigate(navDirections.actionId, navDirections.arguments, null)

    fun safeNavigate(
        @IdRes destination: Int,
        args: Bundle? = null,
        extras: Navigator.Extras? = null
    ) {
        if (navController == null) {
            navInitListeners.add {
                try {
                    navController?.navigate(destination, args, null, extras)
                } catch (t: Throwable) {
                    twig(
                        "WARNING: during callback, did not navigate to destination: R.id.${
                            resources.getResourceEntryName(
                                destination
                            )
                        } due to: $t"
                    )
                }
            }
        } else {
            try {
                navController?.navigate(destination, args, null, extras)
            } catch (t: Throwable) {
                twig(
                    "WARNING: did not immediately navigate to destination: R.id.${
                        resources.getResourceEntryName(
                            destination
                        )
                    } due to: $t"
                )
            }
        }
    }

    fun startSync(isRestart: Boolean = false) {
        twig("MainActivity.startSync")
        DependenciesHolder.synchronizer.let { synchronizer ->
            // Always (re)wire the callbacks to THIS Activity instance. After a configuration
            // change (rotation, dark-mode toggle, etc.) the Activity is recreated while the
            // process-singleton synchronizer keeps running, so the previous instance's handlers
            // would be stale and point at a dead Activity.
            synchronizer.onProcessorErrorHandler = ::onProcessorError
            synchronizer.onChainErrorHandler = ::onChainError
            synchronizer.onCriticalErrorHandler = ::onCriticalError
            (synchronizer as SdkSynchronizer).processor.onScanMetricCompleteListener =
                ::onScanMetricComplete

            if (synchronizer.isStarted && !isRestart) {
                // The synchronizer is already running. This happens when the Activity is recreated
                // by a configuration change DURING a long sync: `syncStarted` is a fresh-instance
                // field (false again) but the synchronizer singleton is still started. Calling
                // start() again throws SynchronizerException.FalseStart and crashed the whole app —
                // during the slow tail of a heavy scan this looked like "stuck near 90%, then the
                // wallet dies and restarts over and over, never finishing". Just re-bind instead.
                twig("Synchronizer already started; re-binding to recreated Activity (no restart)")
                syncStarted = true
                mainViewModel.setSyncReady(true)
            } else if (!syncStarted || isRestart) {
                syncStarted = true
                mainViewModel.setLoading(true)
                feedback.report(SYNC_START)
                try {
                    synchronizer.start(lifecycleScope)
                } catch (e: cash.z.ecc.android.sdk.exception.SynchronizerException.FalseStart) {
                    // Defensive: a race left it already started. Never crash here — just continue.
                    twig("start() found an already-started synchronizer; continuing without restart")
                }
                mainViewModel.setSyncReady(true)

                // When restarting (e.g. after server switch), the HomeFragment's
                // flow is still bound to the old synchronizer.  Signal it to rebind.
                if (isRestart) {
                    mainViewModel.syncRestarted.value = true
                }
            } else {
                twig("Ignoring request to start sync because sync has already been started!")
            }
        }
        mainViewModel.setLoading(false)
        // Keep syncing in the background (unless the user turned it off): a foreground service pins
        // the process so the OS can't kill it mid-write (which corrupts the block DB).
        cash.z.ecc.android.ext.BackgroundSyncManager.startIfEnabled(this)
        twig("MainActivity.startSync COMPLETE")
    }

    private fun onScanMetricComplete(batchMetrics: BatchMetrics, isComplete: Boolean) {
        val reportingThreshold = 100
        if (isComplete) {
            if (batchMetrics.cumulativeItems > reportingThreshold) {
                val network = DependenciesHolder.synchronizer.network.networkName
                reportAction(
                    Report.Performance.ScanRate(
                        network,
                        batchMetrics.cumulativeItems.toInt(),
                        batchMetrics.cumulativeTime,
                        batchMetrics.cumulativeIps
                    )
                )
            }
        }
    }

    private fun onCriticalError(error: Throwable?): Boolean {
        val errorMessage = error?.message
            ?: error?.cause?.message
            ?: error?.toString()
            ?: "A critical error has occurred but no details were provided. Please report and consider submitting logs to help track this one down."
        showCriticalMessage(
            title = "Unrecoverable Error",
            message = errorMessage,
        ) {
            throw error ?: RuntimeException("A critical error occurred but it was null")
        }
        return false
    }

    fun reportScreen(screen: Report.Screen?) = reportAction(screen)

    fun reportTap(tap: Report.Tap?) = reportAction(tap)

    fun reportFunnel(step: Feedback.Funnel?) = reportAction(step)

    private fun reportAction(action: Feedback.Action?) {
        action?.let { feedback.report(it) }
    }

    fun setLoading(isLoading: Boolean, message: String? = null) {
        mainViewModel.setLoading(isLoading, message)
    }

    fun authenticate(
        description: String,
        title: String = getString(R.string.biometric_prompt_title),
        block: () -> Unit
    ) {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                twig("Authentication success with type: ${if (result.authenticationType == AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL) "DEVICE_CREDENTIAL" else if (result.authenticationType == AUTHENTICATION_RESULT_TYPE_BIOMETRIC) "BIOMETRIC" else "UNKNOWN"}  object: ${result.cryptoObject}")
                block()
                twig("Done authentication block")
                // we probably only need to do this if the type is DEVICE_CREDENTIAL
                // but it doesn't hurt to hide the keyboard every time
                hideKeyboard()
            }

            override fun onAuthenticationFailed() {
                twig("Authentication failed!!!!")
                showMessage("Authentication failed :(")
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                twig("Authentication Error")
                fun doNothing(message: String, interruptUser: Boolean = true) {
                    if (interruptUser) {
                        showSnackbar(message)
                    } else {
                        showMessage(message, true)
                    }
                }
                when (errorCode) {
                    ERROR_HW_NOT_PRESENT, ERROR_HW_UNAVAILABLE,
                    ERROR_NO_BIOMETRICS, ERROR_NO_DEVICE_CREDENTIAL -> {
                        twig("Warning: bypassing authentication because $errString [$errorCode]")
                        showMessage(
                            "Please enable screen lock on this device to add security here!",
                            true
                        )
                        block()
                    }
                    ERROR_LOCKOUT -> doNothing("Too many attempts. Try again in 30s.")
                    ERROR_LOCKOUT_PERMANENT -> doNothing("Whoa. Waaaay too many attempts!")
                    ERROR_CANCELED -> doNothing("I just can't right now. Please try again.")
                    ERROR_NEGATIVE_BUTTON -> doNothing("Authentication cancelled", false)
                    ERROR_USER_CANCELED -> doNothing("Cancelled", false)
                    ERROR_NO_SPACE -> doNothing("Not enough storage space!")
                    ERROR_TIMEOUT -> doNothing("Oops. It timed out.")
                    ERROR_UNABLE_TO_PROCESS -> doNothing(".")
                    ERROR_VENDOR -> doNothing("We got some weird error and you should report this.")
                    else -> {
                        twig("Warning: unrecognized authentication error $errorCode")
                        doNothing("Authentication failed with error code $errorCode")
                    }
                }
            }
        }

        BiometricPrompt(this, ContextCompat.getMainExecutor(this), callback).apply {
            authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setConfirmationRequired(false)
                    .setDescription(description)
                    .setAllowedAuthenticators(BIOMETRIC_STRONG or BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
                    .build()
            )
        }
    }

    fun playSound(fileName: String) {
        mediaPlayer.apply {
            if (isPlaying) stop()
            try {
                reset()
                assets.openFd(fileName).let { afd ->
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                }
                prepare()
                start()
            } catch (t: Throwable) {
                Log.e("SDK_ERROR", "ERROR: unable to play sound due to $t")
            }
        }
    }

    // TODO: spruce this up with API 26 stuff
    fun vibrateSuccess() = vibrate(0, 200, 200, 100, 100, 800)

    fun vibrate(initialDelay: Long, vararg durations: Long) {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (vibrator.hasVibrator()) {
            vibrator.vibrate(longArrayOf(initialDelay, *durations), -1)
        }
    }

    fun copyAddress(view: View? = null) {
        reportTap(COPY_ADDRESS)
        lifecycleScope.launch {
            copyText(DependenciesHolder.synchronizer.getAddress(), "Address")
        }
    }

    fun copyTransparentAddress(view: View? = null) {
        reportTap(COPY_TRANSPARENT_ADDRESS)
        lifecycleScope.launch {
            copyText(DependenciesHolder.synchronizer.getTransparentAddress(), "T-Address")
        }
    }

    fun copyText(textToCopy: String, label: String = "ECC Wallet Text") {
        clipboard.setPrimaryClip(
            ClipData.newPlainText(label, textToCopy)
        )
        showMessage("$label copied!")
        vibrate(0, 50)
    }

    fun shareText(textToShare: String) {
        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, textToShare)
        }

        val shareIntent = Intent.createChooser(sendIntent, null)
        startActivity(shareIntent)
    }

    suspend fun isValidAddress(address: String): Boolean {
        try {
            return !DependenciesHolder.synchronizer.validateAddress(address).isNotValid
        } catch (t: Throwable) {
        }
        return false
    }

    fun preventBackPress(fragment: Fragment) {
        onFragmentBackPressed(fragment) {}
    }

    fun onFragmentBackPressed(fragment: Fragment, block: () -> Unit) {
        onBackPressedDispatcher.addCallback(
            fragment,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    block()
                }
            }
        )
    }

    private fun showMessage(message: String, linger: Boolean = false) {
        twig("toast: $message")
        Toast.makeText(this, message, if (linger) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }

    fun showSnackbar(
        message: String,
        actionLabel: String = getString(android.R.string.ok),
        action: () -> Unit = {}
    ): Snackbar {
        return if (snackbar == null) {
            val view = findViewById<View>(R.id.main_activity_container)
            val snacks = Snackbar
                .make(view, "$message", Snackbar.LENGTH_INDEFINITE)
                .setAction(actionLabel) { action() }

            val snackBarView = snacks.view as ViewGroup
            val navigationBarHeight = resources.getDimensionPixelSize(
                resources.getIdentifier(
                    "navigation_bar_height",
                    "dimen",
                    "android"
                )
            )
            val params = snackBarView.getChildAt(0).layoutParams as ViewGroup.MarginLayoutParams
            params.setMargins(
                params.leftMargin,
                params.topMargin,
                params.rightMargin,
                navigationBarHeight
            )

            snackBarView.getChildAt(0).setLayoutParams(params)
            snacks
        } else {
            snackbar!!.setText(message).setAction(actionLabel) { action() }
        }.also {
            if (!it.isShownOrQueued) it.show()
        }
    }

    fun showKeyboard(focusedView: View) {
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(focusedView, InputMethodManager.SHOW_FORCED)
    }

    fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(findViewById<View>(android.R.id.content).windowToken, 0)
    }

    /**
     * @param popUpToInclusive the destination to remove from the stack before opening the camera.
     * This only takes effect in the common case where the permission is granted.
     */
    fun maybeOpenScan(popUpToInclusive: Int? = null) {
        if (hasCameraPermission) {
            openCamera(popUpToInclusive)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(arrayOf(Manifest.permission.CAMERA), 101)
            } else {
                onNoCamera()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                openCamera()
            } else {
                onNoCamera()
            }
        }
    }

    private fun openCamera(popUpToInclusive: Int? = null) {
        navController?.navigate(popUpToInclusive ?: R.id.action_global_nav_scan)
    }

    private fun onNoCamera() {
        showSnackbar(getString(R.string.camera_permission_denied))
    }

    // TODO: clean up this error handling
    private var ignoredErrors = 0
    private var reorgCount = 0
    private fun onProcessorError(error: Throwable?): Boolean {
        // A corrupt block database ("database disk image is malformed") cannot be fixed by
        // retrying or reopening the wallet -- previously the only fix was a full reinstall. Detect
        // it and automatically wipe the (re-downloadable) block data and resync. The seed/keys live
        // in separate secure storage and are NOT touched, so the user keeps their wallet.
        if (isDatabaseCorruption(error)) {
            twig("Detected block-database corruption; clearing block data and resyncing. error=$error")
            clearBlockDataAndRestart()
            // Return true (rather than false) so the SDK keeps the loop alive briefly instead of
            // escalating to the "Unrecoverable Error" critical handler -- our recovery coroutine
            // stops the synchronizer and restarts the app from under it.
            return true
        }
        // A "FOREIGN KEY constraint failed" while scanning is a data-table inconsistency (usually a
        // small reorg near the tip). A full wipe would needlessly resync from the birthday, so we
        // instead quick-rewind ~1 week and re-scan: it fixes the recent inconsistency while keeping
        // older history/funds. Falls back to the manual repair dialog if it just happened.
        if (isForeignKeyConstraintError(error)) {
            twig("Detected FK-constraint scan error; quick-rewinding ~1 week to recover. error=$error")
            autoFkRewind()
            return true
        }
        var notified = false
        when (error) {
            is CompactBlockProcessorException.Uninitialized -> {
                if (dialog == null) {
                    notified = true
                    runOnUiThread {
                        dialog = showUninitializedError(error) {
                            dialog = null
                        }
                    }
                }
            }
            is CompactBlockProcessorException.FailedScan -> {
                // Auto-recover instead of the scary "Scan Failure / Ignore / Retry" dialog: a quick
                // rewind clears recent scan inconsistencies; persistent failures escalate to a resync
                // (both capped against loops). The user just sees a brief "repairing" snackbar.
                twig("FailedScan; auto quick-rewinding (no dialog).")
                autoFkRewind()
                return true
            }
            is CompactBlockProcessorException.FailedReorgRepair -> {
                // Recover automatically (clear block data + resync; keys/addresses preserved) instead
                // of popping the scary "Incompatible Block Data / Clear & Resync?" dialog. A counter
                // inside clearBlockDataAndRestart caps this so a deterministic error can't loop.
                twig("FailedReorgRepair; auto clearing block data and resyncing (no dialog).")
                clearBlockDataAndRestart()
                return true
            }
        }
        // Transient node/gRPC connectivity errors (DEADLINE_EXCEEDED, UNAVAILABLE): do NOT pop a
        // blocking "Select a Server" dialog. That old dialog was non-cancelable and its only other
        // button ("Exit") called exitProcess(0) — so a brief node hiccup (e.g. during 合并零钱) forced
        // the user to either pick a node or have the app killed, which looked like a crash. The block
        // processor already auto-reconnects on its own; here we just show a throttled, non-blocking
        // hint and keep retrying. Users can switch nodes anytime via 我的 → 选择节点 (Compose picker).
        if (!notified && error is io.grpc.StatusRuntimeException) {
            val code = (error as io.grpc.StatusRuntimeException).status.code
            if (code == io.grpc.Status.Code.DEADLINE_EXCEEDED || code == io.grpc.Status.Code.UNAVAILABLE) {
                notified = true
                val now = System.currentTimeMillis()
                if (now - lastNodeErrorNoticeMs > 20_000L) {
                    lastNodeErrorNoticeMs = now
                    runOnUiThread {
                        showSnackbar("节点连接不稳定，正在自动重连…如长时间不动可到 我的→选择节点 手动切换")
                    }
                }
                return true
            }
        }
        if (!notified) {
            ignoredErrors++
            if (ignoredErrors >= ZcashSdk.RETRIES) {
                if (dialog == null) {
                    notified = true
                    runOnUiThread {
                        dialog = showCriticalProcessorError(error) {
                            dialog = null
                        }
                    }
                }
            }
        }
        twig("MainActivity has received an error${if (notified) " and notified the user" else ""} and reported it to bugsnag and mixpanel.")
        feedback.report(error)
        return true
    }

    private fun onChainError(errorHeight: BlockHeight, rewindHeight: BlockHeight) {
        reorgCount++
        twig("Chain reorg detected (#$reorgCount): error at $errorHeight, rewinding to $rewindHeight")
        feedback.report(Reorg(errorHeight, rewindHeight))
    }

    @Volatile
    private var recoveryInProgress = false

    // Throttle the "node unstable" snackbar so repeated gRPC errors don't spam it.
    @Volatile
    private var lastNodeErrorNoticeMs = 0L

    /**
     * Walk the cause chain looking for a SQLite corruption signature. The block databases can
     * become corrupt if the app process is killed mid-write; once that happens, every scan attempt
     * throws "database disk image is malformed" and the wallet can no longer sync.
     */
    private fun isDatabaseCorruption(error: Throwable?): Boolean {
        var t: Throwable? = error
        var depth = 0
        while (t != null && depth < 12) {
            if (t is android.database.sqlite.SQLiteDatabaseCorruptException) return true
            val msg = (t.message ?: "").lowercase(java.util.Locale.US)
            if (msg.contains("malformed") ||
                msg.contains("sqlite_corrupt") ||
                msg.contains("database disk image") ||
                msg.contains("file is not a database") ||
                msg.contains("(code 11")
            ) {
                return true
            }
            t = t.cause
            depth++
        }
        return false
    }

    /**
     * Delete the local block databases (cache + data) and restart the app to resync from the
     * wallet birthday. The seed/keys live in separate secure storage and are NOT touched, so this
     * does NOT require the user to re-enter their seed words or reinstall the app.
     *
     * A short cool-down guards against a restart loop: if we just did this, show the manual repair
     * dialog instead of silently restarting again.
     */
    private fun clearBlockDataAndRestart() {
        if (recoveryInProgress) return
        recoveryInProgress = true

        val recoveryPrefs = getSharedPreferences("dragonx_recovery", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val last = recoveryPrefs.getLong("last_auto_recovery_ms", 0L)
        if (now - last < 120_000L) {
            twig("Auto-recovery ran <2min ago; not looping. Letting the scanner keep retrying.")
            recoveryInProgress = false
            runOnUiThread { showSnackbar("正在修复同步数据，请稍候…（若长时间不动可到 我的→重扫钱包）") }
            return
        }
        // Cap automatic resyncs within a 30-min window so a deterministic error can't loop forever.
        val windowStart = recoveryPrefs.getLong("recovery_window_start_ms", 0L)
        var recoveryCount = recoveryPrefs.getInt("recovery_count", 0)
        if (now - windowStart > 1_800_000L) {
            recoveryCount = 0
            recoveryPrefs.edit().putLong("recovery_window_start_ms", now).apply()
        }
        if (recoveryCount >= 3) {
            twig("Auto-recovery cap reached; not resyncing again. Hinting manual repair.")
            recoveryInProgress = false
            runOnUiThread { showSnackbar("自动修复多次仍未完成，请到 我的→重扫钱包→清除并重新同步 手动处理，或检查节点/网络") }
            return
        }
        recoveryPrefs.edit().putInt("recovery_count", recoveryCount + 1).apply()
        recoveryPrefs.edit().putLong("last_auto_recovery_ms", now).apply()

        runOnUiThread {
            ignoreScanFailure = true
            dialog?.dismiss()
            dialog = null
            setLoading(true, "区块数据损坏，正在清除并重新同步…")
            showSnackbar("检测到区块数据损坏，正在自动修复：清除区块数据并重新同步（助记词不受影响，无需重装）")
        }

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { DependenciesHolder.synchronizer.stop() }
                    .onFailure { twig("recovery: synchronizer.stop failed: $it") }
                runCatching {
                    Initializer.erase(
                        applicationContext,
                        ZcashWalletApp.instance.defaultNetwork
                    )
                }.onFailure { twig("recovery: erase failed: $it") }
            }
            restartApp()
        }
    }

    @Volatile
    private var fkRewindInProgress = false

    private fun isForeignKeyConstraintError(error: Throwable?): Boolean {
        var t: Throwable? = error
        var depth = 0
        while (t != null && depth < 12) {
            val msg = (t.message ?: "").lowercase(java.util.Locale.US)
            if (msg.contains("foreign key constraint") || msg.contains("constraint failed")) return true
            t = t.cause
            depth++
        }
        return false
    }

    /**
     * Recover from a "FOREIGN KEY constraint failed" scan error by quick-rewinding ~1 week on the
     * running synchronizer (same mechanism as manual Quick Rescan); the scanner then re-scans from
     * the rewound height, dropping the inconsistent recent rows while keeping older history/funds.
     * Guarded against re-entry; a cooldown falls back to the manual repair dialog if it keeps firing.
     */
    private fun autoFkRewind() {
        if (fkRewindInProgress) return

        val recoveryPrefs = getSharedPreferences("dragonx_recovery", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - recoveryPrefs.getLong("last_fk_rewind_ms", 0L) < 120_000L) {
            twig("FK rewind ran <2min ago; not looping. Showing manual repair dialog.")
            clearBlockDataAndRestart()
            return
        }
        recoveryPrefs.edit().putLong("last_fk_rewind_ms", now).apply()
        fkRewindInProgress = true
        runOnUiThread { showSnackbar("检测到数据不一致，正在快速回退重扫修复（保留历史与余额）…") }

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val sync = DependenciesHolder.synchronizer
                    val tip = sync.latestHeight?.value ?: sync.networkHeight.value?.value
                    val activation = sync.network.saplingActivationHeight.value
                    if (tip != null) {
                        val target = (tip - 8064L).coerceAtLeast(activation)
                        sync.rewindToNearestHeight(BlockHeight.new(sync.network, target), true)
                    }
                }
            } catch (t: Throwable) {
                twig("auto FK rewind failed: $t")
                clearBlockDataAndRestart()
            } finally {
                fkRewindInProgress = false
            }
        }
    }

    /**
     * Relaunch the app process cleanly. Used after clearing block data so the SDK rebuilds its
     * databases from scratch (from the stored birthday) on the next launch.
     */
    fun restartApp() {
        twig("Restarting app to complete block-data recovery")
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val component = launchIntent?.component
        if (component != null) {
            startActivity(Intent.makeRestartActivityTask(component))
        }
        Runtime.getRuntime().exit(0)
    }

    // TODO: maybe move this quick helper code somewhere general or throttle the dialogs differently (like with a flow and stream operators, instead)

    private val throttles = mutableMapOf<String, () -> Any>()
    private val noWork = {}
    private fun throttle(key: String, delay: Long, block: () -> Any) {
        // if the key exists, just add the block to run later and exit
        if (throttles.containsKey(key)) {
            throttles[key] = block
            return
        }
        block()

        // after doing the work, check back in later and if another request came in, throttle it, otherwise exit
        throttles[key] = noWork
        findViewById<View>(android.R.id.content).postDelayed(
            {
                throttles[key]?.let { pendingWork ->
                    throttles.remove(key)
                    if (pendingWork !== noWork) throttle(key, delay, pendingWork)
                }
            },
            delay
        )
    }

    /* Memo functions that might possibly get moved to MemoUtils */

    suspend fun getSender(transaction: ConfirmedTransaction?): String {
        if (transaction == null) return getString(R.string.unknown)
        return MemoUtil.findAddressInMemo(transaction, ::isValidAddress)?.toAbbreviatedAddress()
            ?: getString(R.string.unknown)
    }

    suspend fun String?.validateAddress(): String? {
        if (this == null) return null
        return if (isValidAddress(this)) this else null
    }

    fun showFirstUseWarning(
        prefKey: String,
        @StringRes titleResId: Int = R.string.blank,
        @StringRes msgResId: Int = R.string.blank,
        @StringRes positiveResId: Int = android.R.string.ok,
        @StringRes negativeResId: Int = android.R.string.cancel,
        action: MainActivity.() -> Unit = {}
    ) {
        historyViewModel.prefs.getBoolean(prefKey).let { doNotWarnAgain ->
            if (doNotWarnAgain) {
                action()
                return@showFirstUseWarning
            }
        }

        val dialogViewBinding = DialogFirstUseMessageBinding.inflate(layoutInflater)

        fun savePref() {
            dialogViewBinding.dialogFirstUseCheckbox.isChecked.let { wasChecked ->
                historyViewModel.prefs.setBoolean(prefKey, wasChecked)
            }
        }

        dialogViewBinding.dialogMessage.setText(msgResId)
        if (dialog != null) dialog?.dismiss()
        // TODO: This should be moved to a DialogFragment, otherwise unmanaged dialogs go away during Activity configuration changes
        dialog = MaterialAlertDialogBuilder(this)
            .setTitle(titleResId)
            .setView(dialogViewBinding.root)
            .setCancelable(false)
            .setPositiveButton(positiveResId) { d, _ ->
                d.dismiss()
                dialog = null
                savePref()
                action()
            }
            .setNegativeButton(negativeResId) { d, _ ->
                d.dismiss()
                dialog = null
                savePref()
            }
            .show()
    }

    fun onLaunchUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (t: Throwable) {
            showMessage(getString(R.string.error_launch_url))
            twig("Warning: failed to open browser due to $t")
        }
    }
}
