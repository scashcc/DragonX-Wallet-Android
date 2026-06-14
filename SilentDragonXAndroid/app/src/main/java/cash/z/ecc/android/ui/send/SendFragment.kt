package cash.z.ecc.android.ui.send

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import cash.z.ecc.android.R
import cash.z.ecc.android.di.DependenciesHolder
import cash.z.ecc.android.ext.WalletZecFormmatter
import cash.z.ecc.android.sdk.db.entity.isFailedEncoding
import cash.z.ecc.android.sdk.db.entity.isFailedSubmit
import cash.z.ecc.android.sdk.db.entity.isSubmitSuccess
import cash.z.ecc.android.sdk.ext.ZcashSdk
import cash.z.ecc.android.sdk.ext.convertZecToZatoshi
import cash.z.ecc.android.sdk.ext.safelyConvertToBigDecimal
import cash.z.ecc.android.sdk.ext.toAbbreviatedAddress
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.ui.MainActivity
import cash.z.ecc.android.ui.compose.DragonXTheme
import cash.z.ecc.android.ui.compose.SendPhase
import cash.z.ecc.android.ui.compose.SendScreen
import cash.z.ecc.android.util.twig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Modern Compose send screen ("address-first"). Drives the proven SendViewModel: validate the
 * address/amount, confirm with the device biometric/credential, derive the spending key and submit
 * via the synchronizer, tracking the resulting PendingTransaction. "Submitted" is surfaced honestly
 * (it is not yet on-chain — confirmations are shown in History).
 */
class SendFragment : Fragment() {

    private val viewModel: SendViewModel by viewModels()
    private val phase = MutableStateFlow<SendPhase>(SendPhase.Editing)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val p by phase.collectAsState()
            val bal by DependenciesHolder.synchronizer.saplingBalances.collectAsState()
            val available = bal?.available
            val availableText = available?.let { WalletZecFormmatter.toZecStringShort(it) } ?: "0"
            val maxAmount = available?.let {
                WalletZecFormmatter.toZecStringFull(Zatoshi((it.value - ZcashSdk.MINERS_FEE.value).coerceAtLeast(0)))
            } ?: "0"
            DragonXTheme {
                SendScreen(
                    availableText = availableText,
                    maxAmount = maxAmount,
                    phase = p,
                    onBack = { (activity as? MainActivity)?.navController?.popBackStack() },
                    onSend = { address, amount, memo -> onSendTapped(address, amount, memo) },
                    onDone = { (activity as? MainActivity)?.navController?.popBackStack() },
                    onGoConsolidate = { (activity as? MainActivity)?.safeNavigate(R.id.action_nav_send_to_nav_consolidate) },
                )
            }
        }
    }

    private fun onSendTapped(address: String, amountStr: String, memo: String) =
        viewLifecycleOwner.lifecycleScope.launch {
            val amount = amountStr.safelyConvertToBigDecimal().convertZecToZatoshi()
            viewModel.toAddress = address
            viewModel.zatoshiAmount = amount
            viewModel.memo = memo

            val availableZatoshi = DependenciesHolder.synchronizer.saplingBalances.value?.available?.value
            val maxZatoshi = availableZatoshi?.let { (it - ZcashSdk.MINERS_FEE.value).coerceAtLeast(0) }

            val error = try {
                viewModel.validate(requireContext(), availableZatoshi, maxZatoshi).first()
            } catch (t: Throwable) {
                t.message ?: "校验失败"
            }
            if (error != null) {
                phase.value = SendPhase.Failed(error)
                return@launch
            }

            // Security gate: confirm with biometric / device credential before spending (parity with
            // the original send flow). authenticate() bypasses gracefully when no lock is set up.
            val confirmText = "确认发送 ${WalletZecFormmatter.toZecStringFull(amount)} DRGX 到 ${address.toAbbreviatedAddress()}"
            val act = activity as? MainActivity
            if (act != null) {
                act.authenticate(confirmText) {
                    viewLifecycleOwner.lifecycleScope.launch { runSend() }
                }
            } else {
                runSend()
            }
        }

    private suspend fun runSend() {
        phase.value = SendPhase.Sending
        try {
            // send() eagerly derives the spending key (runBlocking) before returning the flow, so do
            // it off the main thread to avoid jank.
            val flow = withContext(Dispatchers.IO) { viewModel.send() }
            flow.collect { tx ->
                when {
                    tx.isFailedEncoding() || tx.isFailedSubmit() -> {
                        val msg = tx.errorMessage
                        // With MAX_TX_SPENDS=6, a spend needing >6 notes can't be built and fails as
                        // InsufficientBalance before anything is submitted. Steer to consolidation.
                        if (msg?.contains("insufficient", ignoreCase = true) == true) {
                            phase.value = SendPhase.NeedConsolidate
                        } else {
                            phase.value = SendPhase.Failed(friendlyError(msg))
                        }
                    }
                    tx.isSubmitSuccess() ->
                        phase.value = SendPhase.Submitted
                }
            }
        } catch (t: Throwable) {
            twig("send failed: $t")
            phase.value = SendPhase.Failed(t.message ?: "发送失败")
        }
    }

    private fun friendlyError(message: String?): String {
        val m = message ?: "发送失败"
        return if (m.contains("insufficient", ignoreCase = true)) {
            "可用余额不足，或零钱太碎导致选不出足够的币——请先到「合并零钱」整理后再转账。"
        } else {
            m
        }
    }
}
