package cash.z.ecc.android.di

import android.content.ClipboardManager
import android.content.Context
import cash.z.ecc.android.ZcashWalletApp
import cash.z.ecc.android.ext.Const
import cash.z.ecc.android.feedback.*
import cash.z.ecc.android.lockbox.LockBox
import cash.z.ecc.android.sdk.Initializer
import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.android.sdk.model.LightWalletEndpoint
import cash.z.ecc.android.sdk.type.UnifiedViewingKey
import cash.z.ecc.android.ui.util.DebugFileTwig
import cash.z.ecc.android.util.SilentTwig
import cash.z.ecc.android.util.Twig
import cash.z.ecc.kotlin.mnemonic.Mnemonics

object DependenciesHolder {

    fun provideAppContext(): Context = ZcashWalletApp.instance

    val initializerComponent by lazy { InitializerComponent() }

    private var _synchronizer: Synchronizer? = null
    val synchronizer: Synchronizer
        get() {
            if (_synchronizer == null) {
                _synchronizer = Synchronizer.newBlocking(initializerComponent.initializer)
            }
            return _synchronizer!!
        }

    /**
     * Stop the current synchronizer and reinitialize with the server
     * currently saved in preferences, so a new synchronizer will be
     * created on next access pointing to the new server.
     */
    fun resetSynchronizer() {
        _synchronizer?.let {
            try {
                it.stop()
            } catch (e: Exception) {
                // Ignore errors during teardown
            }
            _synchronizer = null
        }
        // Rebuild the initializer with the new server from prefs
        val host = prefs[Const.Pref.SERVER_HOST] ?: Const.Default.Server.HOST
        val port = prefs[Const.Pref.SERVER_PORT] ?: Const.Default.Server.PORT
        val network = ZcashWalletApp.instance.defaultNetwork
        val extfvk = lockBox.getCharsUtf8(Const.Backup.VIEWING_KEY)
        val extpub = lockBox.getCharsUtf8(Const.Backup.PUBLIC_KEY)
        // Load the stored birthday so the processor uses the correct scan start height
        val birthdayHeight: BlockHeight? = lockBox.get<Int>(Const.Backup.BIRTHDAY_HEIGHT)?.let {
            BlockHeight.new(network, it.toLong())
        }
        if (extfvk != null && extpub != null) {
            val vk = UnifiedViewingKey(extfvk = String(extfvk), extpub = String(extpub))
            initializerComponent.createInitializer(Initializer.Config {
                it.importWallet(vk, birthdayHeight, network, LightWalletEndpoint(host, port, true))
            })
        }
    }

    val clipboardManager by lazy { provideAppContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }

    val lockBox by lazy { LockBox(provideAppContext()) }

    val prefs by lazy { LockBox(provideAppContext()) }

    val feedback by lazy { Feedback() }

    val feedbackCoordinator by lazy {
        lockBox.getBoolean(Const.Pref.FEEDBACK_ENABLED).let { isEnabled ->
            // observe nothing unless feedback is enabled
            Twig.plant(if (isEnabled) DebugFileTwig() else SilentTwig())
            FeedbackCoordinator(feedback)
        }
    }

    val mnemonics by lazy { Mnemonics() }
}
