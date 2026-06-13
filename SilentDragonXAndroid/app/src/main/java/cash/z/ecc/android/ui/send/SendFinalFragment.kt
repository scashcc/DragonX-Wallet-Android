package cash.z.ecc.android.ui.send

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import cash.z.ecc.android.R
import cash.z.ecc.android.databinding.FragmentSendFinalBinding
import cash.z.ecc.android.ext.WalletZecFormmatter
import cash.z.ecc.android.ext.goneIf
import cash.z.ecc.android.feedback.Report
import cash.z.ecc.android.feedback.Report.Tap.SEND_FINAL_CLOSE
import cash.z.ecc.android.sdk.SdkSynchronizer
import cash.z.ecc.android.sdk.db.entity.*
import cash.z.ecc.android.sdk.ext.toAbbreviatedAddress
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.ui.base.BaseFragment
import cash.z.ecc.android.util.twig
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class SendFinalFragment : BaseFragment<FragmentSendFinalBinding>() {
    override val screen = Report.Screen.SEND_FINAL

    private val sendViewModel: SendViewModel by activityViewModels()

    // Set true once the send reaches a terminal state (success/failure/cancelled), so the
    // timeout safety net knows whether it still needs to surface a failure.
    private var reachedTerminal = false

    override fun inflate(inflater: LayoutInflater): FragmentSendFinalBinding =
        FragmentSendFinalBinding.inflate(inflater)

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.buttonPrimary.setOnClickListener {
            onReturnToSend()
        }
        binding.backButtonHitArea.setOnClickListener {
            onExit().also { tapped(SEND_FINAL_CLOSE) }
        }
        binding.textConfirmation.text =
            "${getString(R.string.send_final_sending)} ${WalletZecFormmatter.toZecStringFull(sendViewModel.zatoshiAmount)} ${getString(R.string.symbol)}\n${getString(R.string.send_final_to)}\n${sendViewModel.toAddress.toAbbreviatedAddress()}"
        mainActivity?.preventBackPress(this)

        // Safety net: a shielded send should resolve (success or failure) well within this window.
        // If the pending-tx flow never reaches a terminal state — e.g. the node rejects the tx for a
        // bad anchor but the rejection isn't surfaced — don't trap the user on an endless spinner.
        reachedTerminal = false
        viewLifecycleOwner.lifecycleScope.launch {
            delay(SEND_TIMEOUT_MS)
            if (!reachedTerminal && isResumed) {
                twig("SendFinal: no terminal state after ${SEND_TIMEOUT_MS}ms; surfacing failure instead of spinning")
                updateUi(timeoutUiModel())
            }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mainActivity?.apply {
            sendViewModel.send().onEach {
                onPendingTxUpdated(it)
            }.launchIn((sendViewModel.synchronizer as SdkSynchronizer).coroutineScope)
        }
    }

    private fun onPendingTxUpdated(tx: PendingTransaction?) {
        if (tx == null || !isResumed) return // TODO: maybe log this

        try {
            if (tx.isSubmitSuccess() || tx.isFailure() || tx.isCancelled()) {
                reachedTerminal = true
            }
            tx.toUiModel().let { model ->
                updateUi(model)
            }

            // only hold onto the view model if the transaction failed so that the user can retry
            if (tx.isSubmitSuccess()) {
                sendViewModel.reset()
                // celebrate
                mainActivity?.vibrate(0, 100, 100, 200, 200, 400)
            }
        } catch (t: Throwable) {
            val message = "ERROR: error while handling pending transaction update! $t"
            twig(message)
            mainActivity?.feedback?.report(Report.Error.NonFatal.TxUpdateFailed(t))
            mainActivity?.feedback?.report(t)
        }
    }

    private fun onExit() {
        sendViewModel.reset()
        mainActivity?.safeNavigate(R.id.action_nav_send_final_to_nav_home)
    }

    private fun onCancel(tx: PendingTransaction) {
        sendViewModel.cancel(tx.id)
    }

    private fun onReturnToSend() {
        mainActivity?.safeNavigate(R.id.action_nav_send_final_to_nav_send)
    }

    private fun onSeeDetails() {
        sendViewModel.reset()
        mainActivity?.safeNavigate(R.id.action_nav_send_final_to_nav_history)
    }

    private fun updateUi(model: UiModel) {
        binding.apply {
            backButton.goneIf(!model.showCloseIcon)
            backButtonHitArea.goneIf(!model.showCloseIcon)

            textConfirmation.text = model.title
            lottieSending.goneIf(!model.showProgress)
            if (!model.showProgress) lottieSending.pauseAnimation() else lottieSending.playAnimation()
            errorMessage.text = model.errorMessage
            buttonPrimary.apply {
                text = model.primaryButtonText
                setOnClickListener { model.primaryAction() }
            }
            buttonMoreInfo.apply {
                goneIf(!model.showSecondaryButton)
                text = getString(R.string.send_more_info)
                setOnClickListener {
                    binding.textMoreInfo.text = model.errorDescription
                    text = getString(R.string.done)
                    setOnClickListener { onExit() }
                }
            }
        }
    }

    private fun PendingTransaction.toUiModel() = UiModel().also { model ->
        when {
            isCancelled() -> {
                model.title = getString(R.string.send_final_result_cancelled)
                model.primaryButtonText = getString(R.string.send_final_button_primary_back)
                model.primaryAction = { onReturnToSend() }
            }
            isSubmitSuccess() -> {
                model.title = getString(R.string.send_final_button_primary_sent)
                model.primaryButtonText = getString(R.string.send_final_button_primary_details)
                model.primaryAction = { onSeeDetails() }
            }
            isFailure() -> {
                model.title = getString(R.string.send_final_button_primary_failed)
                model.errorMessage = if (isFailedEncoding()) getString(R.string.send_final_error_encoding) else getString(
                    R.string.send_final_error_submitting
                )
                model.errorDescription = errorMessage.toString()
                model.primaryButtonText = getString(R.string.send_final_button_primary_retry)
                model.primaryAction = { onReturnToSend() }
                model.showSecondaryButton = true
            }
            else -> {
                model.title = "${getString(R.string.send_final_sending)} ${WalletZecFormmatter.toZecStringFull(
                    Zatoshi(value))} ${getString(R.string.symbol)} ${getString(R.string.send_final_to)}\n${toAddress.toAbbreviatedAddress()}"
                model.showProgress = true
                if (isCreating()) {
                    model.showCloseIcon = false
                    model.primaryButtonText = getString(R.string.send_final_button_primary_cancel)
                    model.primaryAction = { onCancel(this) }
                } else {
                    model.primaryButtonText = getString(R.string.send_final_button_primary_details)
                    model.primaryAction = { onSeeDetails() }
                }
            }
        }
    }

    private fun timeoutUiModel() = UiModel().also { model ->
        model.title = getString(R.string.send_final_button_primary_failed)
        model.errorMessage = getString(R.string.send_final_error_submitting)
        model.errorDescription =
            "交易未在预期时间内完成，可能被网络拒绝（例如承诺树 anchor 不合法）。请到交易记录查看是否已发出；若未发出请重试。\n" +
                "The transaction did not complete in time and may have been rejected by the network. " +
                "Check your transaction history; if it did not send, try again."
        model.primaryButtonText = getString(R.string.send_final_button_primary_retry)
        model.primaryAction = { onReturnToSend() }
        model.showSecondaryButton = true
        model.showCloseIcon = true
        model.showProgress = false
    }

    companion object {
        // A send (encode proof + submit + observe result) resolves in seconds normally; 90s is a
        // generous ceiling after which we surface failure rather than spin forever.
        private const val SEND_TIMEOUT_MS = 90_000L
    }

    // fields are ordered, as they appear, top-to-bottom in the UI because that makes it easier to reason about each screen state
    data class UiModel(
        var showCloseIcon: Boolean = true,
        var title: String = "",
        var errorDescription: String = "",
        var showProgress: Boolean = false,
        var errorMessage: String = "",
        var primaryButtonText: String = "See Details",
        var primaryAction: () -> Unit = {},
        var showSecondaryButton: Boolean = false,
    )
}
