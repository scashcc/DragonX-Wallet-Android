package cash.z.ecc.android.ui.setup

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import cash.z.ecc.android.ZcashWalletApp
import cash.z.ecc.android.di.DependenciesHolder
import cash.z.ecc.android.ext.Const
import cash.z.ecc.android.ext.failWith
import cash.z.ecc.android.feedback.Feedback
import cash.z.ecc.android.feedback.Report
import cash.z.ecc.android.lockbox.LockBox
import cash.z.ecc.android.sdk.Initializer
import cash.z.ecc.android.sdk.exception.InitializerException
import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.android.sdk.model.LightWalletEndpoint
import cash.z.ecc.android.sdk.model.ZcashNetwork
import cash.z.ecc.android.sdk.tool.DerivationTool
import cash.z.ecc.android.sdk.type.UnifiedViewingKey
import cash.z.ecc.android.ui.setup.WalletSetupViewModel.WalletSetupState.*
import cash.z.ecc.android.util.twig
import cash.z.ecc.kotlin.mnemonic.Mnemonics
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

class WalletSetupViewModel : ViewModel() {

    private val mnemonics: Mnemonics = DependenciesHolder.mnemonics

    private val lockBox: LockBox = DependenciesHolder.lockBox

    private val prefs: LockBox = DependenciesHolder.prefs

    private val feedback: Feedback = DependenciesHolder.feedback

    enum class WalletSetupState {
        SEED_WITH_BACKUP, SEED_WITHOUT_BACKUP, NO_SEED
    }

    fun checkSeed(): Flow<WalletSetupState> = flow {
        when {
            lockBox.getBoolean(Const.Backup.HAS_BACKUP) -> emit(SEED_WITH_BACKUP)
            lockBox.getBoolean(Const.Backup.HAS_SEED) -> emit(SEED_WITHOUT_BACKUP)
            else -> emit(NO_SEED)
        }
    }

    /**
     * Throw an exception if the seed phrase is bad.
     */
    fun validatePhrase(seedPhrase: String) {
        mnemonics.validate(seedPhrase.toCharArray())
    }

    fun loadBirthdayHeight(): BlockHeight? {
        val h: Int? = lockBox[Const.Backup.BIRTHDAY_HEIGHT]
        twig("Loaded birthday with key ${Const.Backup.BIRTHDAY_HEIGHT} and found $h")
        h?.let {
            return BlockHeight.new(ZcashWalletApp.instance.defaultNetwork, it.toLong())
        }
        return null
    }

    suspend fun newWallet() {
        val network = ZcashWalletApp.instance.defaultNetwork
        twig("Initializing new ${network.networkName} wallet")
        with(mnemonics) {
            storeWallet(nextMnemonic(nextEntropy()), network, loadNearestBirthday(network))
        }
        openStoredWallet()
    }

    suspend fun importWallet(seedPhrase: String, birthdayHeight: BlockHeight?) {
        val network = ZcashWalletApp.instance.defaultNetwork
        twig("Importing ${network.networkName} wallet. Requested birthday: $birthdayHeight")
        storeWallet(
            seedPhrase.toCharArray(),
            network,
            birthdayHeight ?: loadNearestBirthday(network)
        )
        openStoredWallet()
    }

    suspend fun openStoredWallet() {
        DependenciesHolder.initializerComponent.createInitializer(loadConfig())
    }

    /**
     * Build a config object by loading in the viewingKey, birthday and server info which is already
     * known by this point.
     */
    private suspend fun loadConfig(): Initializer.Config {

        twig("Loading config variables")
        var overwriteVks = false
        val network = ZcashWalletApp.instance.defaultNetwork
        val vk =
            loadUnifiedViewingKey() ?: onMissingViewingKey(network).also { overwriteVks = true }
        val birthdayHeight = loadBirthdayHeight() ?: onMissingBirthday(network)
        val savedHost = prefs[Const.Pref.SERVER_HOST] ?: Const.Default.Server.HOST
        val port = prefs[Const.Pref.SERVER_PORT] ?: Const.Default.Server.PORT

        // Check if the preferred server is reachable; if not, try others
        // Run on IO dispatcher since findReachableServer does blocking Socket I/O
        val host = withContext(IO) { findReachableServer(savedHost, port) }

        Log.d("SilentDragon", "host: $host")

        twig("Done loading config variables")
        return Initializer.Config {
            it.importWallet(vk, birthdayHeight, network, LightWalletEndpoint(host, port, true))
            it.setOverwriteKeys(overwriteVks)
        }
    }

    /**
     * Check if the preferred server is reachable via TCP. If not, try each server
     * in the list until one responds. Returns the first reachable server, or falls
     * back to the preferred server if none respond.
     */
    private fun findReachableServer(preferredHost: String, port: Int): String {
        // Try preferred server first
        if (isServerReachable(preferredHost, port)) {
            return preferredHost
        }
        twig("Preferred server $preferredHost is unreachable, trying alternatives...")

        // Try each server in the list
        for (server in Const.Default.Server.serverList) {
            if (server != preferredHost && isServerReachable(server, port)) {
                twig("Found reachable server: $server")
                // Save working server to preferences for next time
                prefs[Const.Pref.SERVER_HOST] = server
                return server
            }
        }

        twig("WARNING: No servers responded, using preferred: $preferredHost")
        return preferredHost
    }

    private fun isServerReachable(host: String, port: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 5000)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun loadUnifiedViewingKey(): UnifiedViewingKey? {
        val extfvk = lockBox.getCharsUtf8(Const.Backup.VIEWING_KEY)
        val extpub = lockBox.getCharsUtf8(Const.Backup.PUBLIC_KEY)
        return when {
            extfvk == null || extpub == null -> {
                if (extfvk == null) {
                    twig("Warning: Shielded key was missing")
                }
                if (extpub == null) {
                    twig("Warning: Transparent key was missing")
                }
                null
            }
            else -> UnifiedViewingKey(extfvk = String(extfvk), extpub = String(extpub))
        }
    }

    private suspend fun onMissingViewingKey(network: ZcashNetwork): UnifiedViewingKey {
        twig("Recover VK: Viewing key was missing")
        // add some temporary logic to help us troubleshoot this problem.
        ZcashWalletApp.instance.getSharedPreferences("SecurePreferences", Context.MODE_PRIVATE)
            .all.map { it.key }.joinToString().let { keyNames ->
                "${Const.Backup.VIEWING_KEY}, ${Const.Backup.PUBLIC_KEY}".let { missingKeys ->
                    // is there a typo or change in how the value is labelled?
                    // for troubleshooting purposes, let's see if we CAN derive the vk from the seed in these situations
                    var recoveryViewingKey: UnifiedViewingKey? = null
                    var ableToLoadSeed = false
                    try {
                        val seed = lockBox.getBytes(Const.Backup.SEED)!!
                        ableToLoadSeed = true
                        twig("Recover UVK: Seed found")
                        recoveryViewingKey =
                            DerivationTool.deriveUnifiedViewingKeys(seed, network)[0]
                        twig("Recover UVK: successfully derived UVK from seed")
                    } catch (t: Throwable) {
                        twig("Failed while trying to recover UVK due to: $t")
                    }

                    // this will happen during rare upgrade scenarios when the user migrates from a seed-only wallet to this vk-based version
                    // or during more common scenarios where the user migrates from a vk only wallet to a unified vk wallet
                    if (recoveryViewingKey != null) {
                        storeUnifiedViewingKey(recoveryViewingKey)
                        return recoveryViewingKey
                    } else {
                        feedback.report(
                            Report.Issue.MissingViewkey(
                                ableToLoadSeed,
                                missingKeys,
                                keyNames,
                                lockBox.getCharsUtf8(Const.Backup.VIEWING_KEY) != null
                            )
                        )
                    }
                    throw InitializerException.MissingViewingKeyException
                }
            }
    }

    private suspend fun onMissingBirthday(network: ZcashNetwork): BlockHeight =
        failWith(InitializerException.MissingBirthdayException) {
            twig("Recover Birthday: falling back to sapling birthday")
            loadNearestBirthday(network)
        }

    private suspend fun loadNearestBirthday(network: ZcashNetwork) =
        BlockHeight.ofLatestCheckpoint(
            ZcashWalletApp.instance,
            network,
        )

    //
    // Storage Helpers
    //

    /**
     * Entry point for all storage. Takes a seed phrase and stores all the parts so that we can
     * selectively use them, the next time the app is opened. Although we store everything, we
     * primarily only work with the viewing key and spending key. The seed is only accessed when
     * presenting backup information to the user.
     */
    private suspend fun storeWallet(
        seedPhraseChars: CharArray,
        network: ZcashNetwork,
        birthday: BlockHeight
    ) {
        check(!lockBox.getBoolean(Const.Backup.HAS_SEED)) {
            "Error! Cannot store a seed when one already exists! This would overwrite the" +
                    " existing seed and could lead to a loss of funds if the user has no backup!"
        }

        storeBirthday(birthday)

        mnemonics.toSeed(seedPhraseChars).let { bip39Seed ->
            DerivationTool.deriveUnifiedViewingKeys(bip39Seed, network)[0].let { viewingKey ->
                storeSeedPhrase(seedPhraseChars)
                storeSeed(bip39Seed)
                storeUnifiedViewingKey(viewingKey)
            }
        }
    }

    private suspend fun storeBirthday(birthday: BlockHeight) = withContext(IO) {
        twig("Storing birthday ${birthday.value} with and key ${Const.Backup.BIRTHDAY_HEIGHT}")
        lockBox[Const.Backup.BIRTHDAY_HEIGHT] = birthday.value
    }

    private suspend fun storeSeedPhrase(seedPhrase: CharArray) = withContext(IO) {
        twig("Storing seedphrase: ${seedPhrase.size}")
        lockBox[Const.Backup.SEED_PHRASE] = seedPhrase
        lockBox[Const.Backup.HAS_SEED_PHRASE] = true
    }

    private suspend fun storeSeed(bip39Seed: ByteArray) = withContext(IO) {
        twig("Storing seed: ${bip39Seed.size}")
        lockBox.setBytes(Const.Backup.SEED, bip39Seed)
        lockBox[Const.Backup.HAS_SEED] = true
    }

    private suspend fun storeUnifiedViewingKey(vk: UnifiedViewingKey) = withContext(IO) {
        twig("storeViewingKey vk: ${vk.extfvk.length}")
        lockBox[Const.Backup.VIEWING_KEY] = vk.extfvk
        lockBox[Const.Backup.PUBLIC_KEY] = vk.extpub
    }
}
