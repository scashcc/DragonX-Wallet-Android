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
import cash.z.ecc.android.sdk.db.entity.isFailedEncoding
import cash.z.ecc.android.sdk.db.entity.isFailedSubmit
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
        var rounds = 0
        binding.buttonStart.isEnabled = false
        binding.progressConsolidate.visibility = View.VISIBLE
        binding.textStatus.text = getString(R.string.consolidate_running, 0)

        try {
            viewModel.consolidate().collect { tx ->
                if (tx.isFailedEncoding() || tx.isFailedSubmit()) {
                    twig("consolidation round failed: ${tx.errorMessage}")
                } else {
                    rounds++
                    binding.textStatus.text = getString(R.string.consolidate_running, rounds)
                }
            }
            binding.textStatus.text = if (rounds == 0) {
                getString(R.string.consolidate_none)
            } else {
                getString(R.string.consolidate_done, rounds)
            }
        } catch (t: Throwable) {
            twig("consolidation failed: $t")
            binding.textStatus.text =
                getString(R.string.consolidate_error, t.message ?: t.toString())
        } finally {
            running = false
            binding.buttonStart.isEnabled = true
            binding.progressConsolidate.visibility = View.GONE
        }
    }
}
