package cash.z.ecc.android.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import cash.z.ecc.android.R
import cash.z.ecc.android.databinding.FragmentConsolidateBinding
import cash.z.ecc.android.ext.showConfirmation
import cash.z.ecc.android.sdk.Synchronizer.Status.SYNCED
import cash.z.ecc.android.sdk.db.entity.PendingTransaction
import cash.z.ecc.android.sdk.db.entity.isMined
import cash.z.ecc.android.sdk.db.entity.isSubmitSuccess
import cash.z.ecc.android.sdk.exception.TransactionEncoderException
import cash.z.ecc.android.ui.base.BaseFragment
import cash.z.ecc.android.util.twig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * "合并零钱" (consolidate small notes). Wallets that receive many small payments (e.g. mining-pool
 * payouts) accumulate hundreds of tiny notes, which makes large sends too big to be mined. This
 * screen runs the fully-automatic sweep: it repeatedly merges the smallest notes back to the
 * wallet's own address, a batch at a time, until nothing is left worth consolidating. The number
 * of rounds adapts to how fragmented the wallet is.
 */
class ConsolidateFragment : BaseFragment<FragmentConsolidateBinding>() {

    private val viewModel: ProfileViewModel by viewModels()

    // True while a sweep is in progress; blocks re-entry and the Done button.
    private var running = false

    override fun inflate(inflater: LayoutInflater): FragmentConsolidateBinding =
        FragmentConsolidateBinding.inflate(inflater)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.buttonStart.setOnClickListener { onStartTapped() }
        binding.buttonDone.setOnClickListener {
            if (!running) mainActivity?.navController?.popBackStack()
        }
    }

    private fun onStartTapped() {
        if (running) return
        mainActivity?.showConfirmation(
            getString(R.string.consolidate_confirm_title),
            getString(R.string.consolidate_confirm_message),
            getString(R.string.consolidate_start)
        ) {
            startConsolidation()
        }
    }

    private fun startConsolidation() = viewLifecycleOwner.lifecycleScope.launch {
        // The wallet must be fully synced so the notes have valid witnesses at the anchor height.
        val synced = try {
            viewModel.synchronizer.status.first() == SYNCED
        } catch (t: Throwable) {
            false
        }
        if (!synced) {
            mainActivity?.showSnackbar(getString(R.string.consolidate_need_sync))
            return@launch
        }

        running = true
        // The sweep emits each batch when submitted and again when it confirms (or fails/expires),
        // so we key by tx id and recompute the tallies on every update. Progress is reported by
        // *confirmed* batches (real on-chain confirmations), never just "submitted to mempool".
        val seen = HashMap<Long, PendingTransaction>()
        binding.buttonStart.isEnabled = false
        binding.progressConsolidate.visibility = View.VISIBLE
        binding.textStatus.text = getString(R.string.consolidate_running, 0, 0)

        try {
            viewModel.consolidate().collect { tx ->
                seen[tx.id] = tx
                val confirmed = seen.values.count { it.isMined() }
                val submitted = seen.values.count { it.isMined() || it.isSubmitSuccess() }
                binding.textStatus.text = getString(R.string.consolidate_running, confirmed, submitted)
            }
            val confirmed = seen.values.count { it.isMined() }
            val submitted = seen.values.count { it.isMined() || it.isSubmitSuccess() }
            binding.textStatus.text = when {
                submitted == 0 -> getString(R.string.consolidate_none)
                confirmed < submitted ->
                    getString(R.string.consolidate_done_partial, confirmed, submitted - confirmed)
                else -> getString(R.string.consolidate_done, confirmed)
            }
        } catch (t: Throwable) {
            if (isNeedsRescan(t)) {
                twig("consolidation needs a full rescan: $t")
                binding.textStatus.text = getString(R.string.consolidate_needs_rescan)
                promptRescan()
            } else {
                twig("consolidation failed: $t")
                binding.textStatus.text =
                    getString(R.string.consolidate_error, t.message ?: t.toString())
            }
        } finally {
            running = false
            binding.buttonStart.isEnabled = true
            binding.progressConsolidate.visibility = View.GONE
        }
    }

    /**
     * Detects the SDK's "funds exist but witnesses are missing -> needs a full rescan" signal.
     * Checks both the exception type and a stable message marker, since coroutine flows may wrap
     * the original exception.
     */
    private fun isNeedsRescan(t: Throwable): Boolean {
        var cause: Throwable? = t
        while (cause != null) {
            if (cause is TransactionEncoderException.ConsolidationNeedsRescanException) return true
            if (cause.message?.contains("DRAGONX_NEEDS_RESCAN") == true) return true
            cause = cause.cause
        }
        return false
    }

    /**
     * Offers a one-tap full rescan. A rescan rebuilds continuous witnesses from the wallet's
     * birthday, which is the only real cure for the dust deadlock. Mnemonic/address are untouched.
     */
    private fun promptRescan() {
        mainActivity?.showConfirmation(
            getString(R.string.consolidate_rescan_title),
            getString(R.string.consolidate_rescan_message),
            getString(R.string.consolidate_rescan_confirm)
        ) {
            viewModel.wipe()
            mainActivity?.restartApp()
        }
    }
}
