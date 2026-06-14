package cash.z.ecc.android.ui.setup

import android.graphics.drawable.Drawable
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
import cash.z.ecc.android.R
import cash.z.ecc.android.ZcashWalletApp
import cash.z.ecc.android.databinding.FragmentRestoreBinding
import cash.z.ecc.android.ext.showSharedLibraryCriticalError
import cash.z.ecc.android.feedback.Report
import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.android.ui.base.BaseFragment
import cash.z.ecc.android.ui.compose.DragonXTheme
import cash.z.ecc.android.ui.compose.RestoreScreen
import com.tylersuehr.chips.Chip
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Restore-from-seed (Compose). The 24 words are entered in a 4×6 grid of independent numbered cells
 * (so any one word can be corrected in place), or pasted in one shot. The word collection +
 * validation + import are unchanged (delegated to [WalletSetupViewModel]).
 */
class RestoreFragment : BaseFragment<FragmentRestoreBinding>() {
    override val screen = Report.Screen.RESTORE

    private val walletSetup: WalletSetupViewModel by activityViewModels()
    private val busy = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    override fun inflate(inflater: LayoutInflater): FragmentRestoreBinding =
        FragmentRestoreBinding.inflate(inflater)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val isBusy by busy.collectAsState()
            val err by error.collectAsState()
            DragonXTheme {
                RestoreScreen(
                    busy = isBusy,
                    error = err,
                    onBack = { mainActivity?.navController?.popBackStack() },
                    onRestore = { words, birthdayStr -> onRestore(words, birthdayStr) },
                )
            }
        }
    }

    private fun onRestore(words: List<String>, birthdayStr: String) {
        val activation = ZcashWalletApp.instance.defaultNetwork.saplingActivationHeight
        // Identical collection to the original grid: trim + lowercase + space-join.
        val seedPhrase = words.joinToString(" ") { it.trim().lowercase() }.trim()
        val birthday = birthdayStr.let { s ->
            if (s.isEmpty()) activation.value else (s.toLongOrNull() ?: activation.value)
        }.coerceAtLeast(activation.value)

        error.value = null
        try {
            walletSetup.validatePhrase(seedPhrase)
        } catch (t: Throwable) {
            error.value = "助记词无效，请检查每个词的拼写与顺序。\n${t.message ?: ""}"
            return
        }

        busy.value = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                walletSetup.importWallet(
                    seedPhrase,
                    BlockHeight.new(ZcashWalletApp.instance.defaultNetwork, birthday)
                )
                mainActivity?.startSync()
                mainActivity?.safeNavigate(R.id.action_nav_restore_to_nav_home)
            } catch (e: UnsatisfiedLinkError) {
                busy.value = false
                mainActivity?.showSharedLibraryCriticalError(e)
            } catch (t: Throwable) {
                busy.value = false
                error.value = "恢复失败：${t.message ?: t.toString()}"
            }
        }
    }
}

/** Kept because SeedWordAdapter still imports it; removing it would break compilation. */
class SeedWordChip(val word: String, var index: Int = -1) : Chip() {
    override fun getSubtitle(): String? = null
    override fun getAvatarDrawable(): Drawable? = null
    override fun getId() = index
    override fun getTitle() = word
    override fun getAvatarUri() = null
}
