package cash.z.ecc.android.ui.setup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import cash.z.ecc.android.databinding.FragmentBackupBinding
import cash.z.ecc.android.di.DependenciesHolder
import cash.z.ecc.android.ext.Const
import cash.z.ecc.android.feedback.Report
import cash.z.ecc.android.ui.base.BaseFragment
import cash.z.ecc.kotlin.mnemonic.Mnemonics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Backup screen (plain XML, see fragment_backup.xml). Shows the 24-word seed phrase numbered, the
 * wallet birthday height, and copy/done buttons. For private-key-restored wallets there is no seed
 * phrase, so an explanatory message is shown instead.
 *
 * This used to be a Compose ComposeView, but on some devices (Xiaomi / MIUI / Android 13) the
 * Compose runtime crashed in composeInitial (ArrayIndexOutOfBoundsException in SlotTable) the instant
 * this screen opened from the biometric gate. ExportKeysFragment reaches the same gate as plain XML
 * and never crashes, so the backup screen is plain XML now too.
 */
class BackupFragment : BaseFragment<FragmentBackupBinding>() {
    override val screen = Report.Screen.BACKUP

    private val walletSetup: WalletSetupViewModel by activityViewModels()
    private var words: List<String> = emptyList()

    override fun inflate(inflater: LayoutInflater): FragmentBackupBinding =
        FragmentBackupBinding.inflate(inflater)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.buttonCopySeed.setOnClickListener {
            if (words.isNotEmpty()) mainActivity?.copyText(words.joinToString(" "), "助记词")
        }
        binding.buttonDone.setOnClickListener {
            mainActivity?.navController?.popBackStack()
        }
        load()
    }

    private fun load() = lifecycleScope.launch {
        val loaded = loadWords()
        words = loaded
        binding.textSeedPhrase.text = if (loaded.isEmpty()) {
            "此钱包由私钥恢复，没有助记词。\n请到「我的 → 导出私钥」妥善保管你的私钥。"
        } else {
            loaded.mapIndexed { i, word -> String.format("%2d. %s", i + 1, word) }.joinToString("\n")
        }
        val birthday = withContext(Dispatchers.IO) {
            runCatching { walletSetup.loadBirthdayHeight()?.value }.getOrNull()
        }
        binding.textBirthday.text = (birthday ?: 0L).toString()
    }

    private suspend fun loadWords(): List<String> = withContext(Dispatchers.IO) {
        val seedPhrase = DependenciesHolder.lockBox.getCharsUtf8(Const.Backup.SEED_PHRASE)
            ?: return@withContext emptyList()
        runCatching { Mnemonics().toWordList(seedPhrase).map { String(it) } }.getOrDefault(emptyList())
    }
}
