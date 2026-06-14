package cash.z.ecc.android.ui.setup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import cash.z.ecc.android.databinding.FragmentBackupBinding
import cash.z.ecc.android.di.DependenciesHolder
import cash.z.ecc.android.ext.Const
import cash.z.ecc.android.feedback.Report
import cash.z.ecc.android.ui.base.BaseFragment
import cash.z.ecc.android.ui.compose.BackupScreen
import cash.z.ecc.android.ui.compose.DragonXTheme
import cash.z.ecc.kotlin.mnemonic.Mnemonics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Backup screen (Compose): shows the 24-word seed phrase in a numbered grid. For private-key
 * restored wallets there is no seed phrase, so an explanatory message is shown instead.
 */
class BackupFragment : BaseFragment<FragmentBackupBinding>() {
    override val screen = Report.Screen.BACKUP

    private val walletSetup: WalletSetupViewModel by activityViewModels()
    private val wordsState = MutableStateFlow<List<String>>(emptyList())
    private val birthdayState = MutableStateFlow("—")

    override fun inflate(inflater: LayoutInflater): FragmentBackupBinding =
        FragmentBackupBinding.inflate(inflater)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val words by wordsState.collectAsState()
            val birthday by birthdayState.collectAsState()
            DragonXTheme {
                BackupScreen(
                    words = words,
                    birthday = birthday,
                    onCopy = { mainActivity?.copyText(words.joinToString(" "), "助记词") },
                    onDone = { mainActivity?.navController?.popBackStack() },
                    onBack = { mainActivity?.navController?.popBackStack() },
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            wordsState.value = loadWords()
            birthdayState.value = (walletSetup.loadBirthdayHeight()?.value ?: 0L).toString()
        }
    }

    private suspend fun loadWords(): List<String> = withContext(Dispatchers.IO) {
        val seedPhrase = DependenciesHolder.lockBox.getCharsUtf8(Const.Backup.SEED_PHRASE)
            ?: return@withContext emptyList()
        runCatching { Mnemonics().toWordList(seedPhrase).map { String(it) } }.getOrDefault(emptyList())
    }
}
