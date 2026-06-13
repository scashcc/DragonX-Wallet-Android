package cash.z.ecc.android.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import cash.z.ecc.android.R
import cash.z.ecc.android.ZcashWalletApp
import cash.z.ecc.android.databinding.FragmentExportKeysBinding
import cash.z.ecc.android.ext.Const
import cash.z.ecc.android.lockbox.LockBox
import cash.z.ecc.android.sdk.tool.DerivationTool
import cash.z.ecc.android.ui.base.BaseFragment
import cash.z.ecc.android.ui.setup.WalletSetupViewModel
import cash.z.ecc.android.util.twig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lets the user view and copy the secrets needed to move funds into another wallet
 * (e.g. the desktop silentdragonxlite-cli): the 24-word seed phrase, the wallet birthday
 * height, and the Sapling extended spending key (private key). Reachable from the Profile
 * screen behind the same biometric gate as the seed backup screen.
 */
class ExportKeysFragment : BaseFragment<FragmentExportKeysBinding>() {

    private val walletSetup: WalletSetupViewModel by activityViewModels()

    private var seedPhrase: String = ""
    private var spendingKey: String = ""
    private var shieldedAddress: String = ""

    override fun inflate(inflater: LayoutInflater): FragmentExportKeysBinding =
        FragmentExportKeysBinding.inflate(inflater)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonCopySeed.setOnClickListener {
            mainActivity?.copyText(seedPhrase, getString(R.string.export_keys_label_seed))
        }
        binding.buttonCopySpendingKey.setOnClickListener {
            mainActivity?.copyText(spendingKey, getString(R.string.export_keys_label_spending_key))
        }
        binding.buttonCopyAddress.setOnClickListener {
            mainActivity?.copyText(shieldedAddress, getString(R.string.export_keys_label_address))
        }
        binding.buttonDone.setOnClickListener {
            mainActivity?.navController?.popBackStack()
        }

        loadKeys()
    }

    private fun loadKeys() = lifecycleScope.launch {
        val notFound = getString(R.string.export_keys_not_found)
        val network = ZcashWalletApp.instance.defaultNetwork
        var birthdayText = notFound

        // All secure-storage reads and key derivation happen off the main thread.
        withContext(Dispatchers.IO) {
            val lockBox = LockBox(ZcashWalletApp.instance)
            lockBox.getCharsUtf8(Const.Backup.SEED_PHRASE)?.let { seedPhrase = String(it) }
            val seedBytes = lockBox.getBytes(Const.Backup.SEED)
            if (seedBytes != null) {
                try {
                    spendingKey = DerivationTool.deriveSpendingKeys(seedBytes, network)[0]
                    shieldedAddress = DerivationTool.deriveShieldedAddress(seedBytes, network)
                } catch (t: Throwable) {
                    twig("ExportKeys: failed to derive keys due to $t")
                }
            }
            walletSetup.loadBirthdayHeight()?.value?.let { birthdayText = it.toString() }
        }

        binding.textSeedPhrase.text = seedPhrase.ifEmpty { notFound }
        binding.textSpendingKey.text = spendingKey.ifEmpty { notFound }
        binding.textAddress.text = shieldedAddress.ifEmpty { notFound }
        binding.textBirthday.text = birthdayText
    }
}
