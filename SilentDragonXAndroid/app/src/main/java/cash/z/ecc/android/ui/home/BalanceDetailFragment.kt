package cash.z.ecc.android.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import cash.z.ecc.android.R
import cash.z.ecc.android.ZcashWalletApp
import cash.z.ecc.android.databinding.FragmentBalanceDetailBinding
import cash.z.ecc.android.ext.goneIf
import cash.z.ecc.android.ext.onClickNavBack
import cash.z.ecc.android.ext.toAppColor
import cash.z.ecc.android.ext.toSplitColorSpan
import cash.z.ecc.android.feedback.Report.Tap.RECEIVE_BACK
import cash.z.ecc.android.sdk.ext.convertZatoshiToZecString
import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.android.ui.base.BaseFragment
import cash.z.ecc.android.ui.home.BalanceDetailViewModel.StatusModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class BalanceDetailFragment : BaseFragment<FragmentBalanceDetailBinding>() {

    private val viewModel: BalanceDetailViewModel by viewModels()
    private var lastSignal: BlockHeight? = null

    override fun inflate(inflater: LayoutInflater): FragmentBalanceDetailBinding =
        FragmentBalanceDetailBinding.inflate(inflater)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.balances.onEach { onBalanceUpdated(it) }.launchIn(this)
                viewModel.statuses.onEach { onStatusUpdated(it) }.launchIn(this)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.hitAreaExit.onClickNavBack() { tapped(RECEIVE_BACK) }
        binding.textShieldedHushTitle.text = "SHIELDED ${getString(R.string.symbol)}"
    }

    private fun onBalanceUpdated(balanceModel: BalanceDetailViewModel.BalanceModel) {
        balanceModel.apply {
            if (balanceModel.hasData()) {
                setBalances(paddedShielded, paddedTransparent, paddedTotal)
            } else {
                setBalances(" --", " --", " --")
            }
        }
    }

    private fun onStatusUpdated(status: StatusModel) {
        binding.textStatus.text = status.toStatus()
        if (status.missingBlocks > 100) {
            binding.textBlockHeightPrefix.text = "Processing "
            binding.textBlockHeight.text = String.format(
                "%,d",
                status.info.lastScannedHeight?.value ?: 0
            ) + " of " + String.format("%,d", status.info.networkBlockHeight?.value ?: 0)
        } else {
            status.info.lastScannedHeight.let { height ->
                if (height == null) {
                    binding.textBlockHeightPrefix.text = "Processing..."
                    binding.textBlockHeight.text = ""
                } else {
                    binding.textBlockHeightPrefix.text = "Balances as of block "
                    binding.textBlockHeight.text =
                        String.format("%,d", status.info.lastScannedHeight?.value ?: 0)
                    sendNewBlockSignal(status.info.lastScannedHeight)
                }
            }
        }
    }

    private fun sendNewBlockSignal(currentHeight: BlockHeight?) {
        // prevent a flood of signals while scanning blocks
        if (lastSignal != null && (currentHeight?.value ?: 0) > lastSignal!!.value) {
            mainActivity?.vibrate(0, 100, 100, 300)
            Toast.makeText(mainActivity, "New block!", Toast.LENGTH_SHORT).show()
        }
        lastSignal = currentHeight
    }

    fun setBalances(shielded: String, transparent: String, total: String) {
        binding.textShieldAmount.text = shielded.colorize()
    }

    private fun String.colorize(): CharSequence {
        val dotIndex = indexOf('.')
        return if (dotIndex < 0 || length < (dotIndex + 4)) {
            this
        } else {
            toSplitColorSpan(R.color.text_light, R.color.zcashWhite_24, indexOf('.') + 4)
        }
    }

    private fun StatusModel.toStatus(): String {
        fun String.plural(count: Int) = if (count > 1) "${this}s" else this

        if (viewModel.latestBalance?.hasData() == false) {
            return "Balance info is not yet available"
        }

        var status = ""
        if (hasUnmined) {
            val count = pendingUnmined.count()
            status += "Balance excludes $count unconfirmed ${"transaction".plural(count)}. "
        }

        status += when {
            hasPendingTransparentBalance && hasPendingShieldedBalance -> {
                "Awaiting ${pendingShieldedBalance.convertZatoshiToZecString(8)} ${
                    ZcashWalletApp.instance.getString(
                        R.string.symbol
                    )
                } in shielded funds and {pendingTransparentBalance.convertZatoshiToZecString(8)} ${
                    ZcashWalletApp.instance.getString(
                        R.string.symbol
                    )
                } in transparent funds"
            }
            hasPendingShieldedBalance -> {
                "Awaiting ${pendingShieldedBalance.convertZatoshiToZecString(8)} ${
                    ZcashWalletApp.instance.getString(
                        R.string.symbol
                    )
                } in shielded funds"
            }
            hasPendingTransparentBalance -> {
                "Awaiting ${pendingTransparentBalance.convertZatoshiToZecString(8)} ${
                    ZcashWalletApp.instance.getString(
                        R.string.symbol
                    )
                } in transparent funds"
            }
            else -> ""
        }

        pendingUnconfirmed.count().takeUnless { it == 0 }?.let { count ->
            if (status.contains("Awaiting")) status += " and "
            status += "$count outbound ${"transaction".plural(count)}"
            remainingConfirmations().firstOrNull()?.let { remaining ->
                status += " with $remaining ${"confirmation".plural(remaining.toInt())} remaining"
            }
        }

        return if (status.isEmpty()) "All funds are available!" else status
    }
}
