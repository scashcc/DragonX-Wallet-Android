package cash.z.ecc.android.ui.setup

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import cash.z.ecc.android.R
import cash.z.ecc.android.ZcashWalletApp
import cash.z.ecc.android.databinding.FragmentRestoreBinding
import cash.z.ecc.android.ext.showConfirmation
import cash.z.ecc.android.ext.showInvalidSeedPhraseError
import cash.z.ecc.android.ext.showSharedLibraryCriticalError
import cash.z.ecc.android.feedback.Report
import cash.z.ecc.android.feedback.Report.Funnel.Restore
import cash.z.ecc.android.feedback.Report.Tap.*
import cash.z.ecc.android.sdk.model.BlockHeight
import cash.z.ecc.android.ui.base.BaseFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tylersuehr.chips.Chip
import kotlinx.coroutines.launch

/**
 * Restore-from-seed screen.
 *
 * DragonX: the 24 seed words are entered in a fixed 4-column × 6-row numbered grid instead of the
 * old chips input. Each cell is an independent field, so any single word can be corrected in place
 * — fixing the old "delete one word and everything after it shifts, forcing a full re-type"
 * problem. Each cell autocompletes against the BIP-39 word list.
 */
class RestoreFragment : BaseFragment<FragmentRestoreBinding>(), View.OnKeyListener {
    override val screen = Report.Screen.RESTORE

    private val walletSetup: WalletSetupViewModel by activityViewModels()

    private val wordCells = ArrayList<AutoCompleteTextView>(SEED_WORD_COUNT)
    private var reportedStart = false
    private var reportedComplete = false

    override fun inflate(inflater: LayoutInflater): FragmentRestoreBinding =
        FragmentRestoreBinding.inflate(inflater)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        buildSeedGrid()

        binding.buttonDone.setOnClickListener {
            onDone().also { tapped(RESTORE_DONE) }
        }
        binding.buttonSuccess.setOnClickListener {
            onEnterWallet().also { tapped(RESTORE_SUCCESS) }
        }
        binding.buttonClear.setOnClickListener {
            onClearSeedWords().also { tapped(RESTORE_CLEAR) }
        }
        updateDoneViews()
    }

    /**
     * Build the 24-cell numbered grid: 6 rows of 4 equal-width cells. Each cell stores its word
     * independently, so editing one never affects the others.
     */
    private fun buildSeedGrid() {
        val context = requireContext()
        val inflater = LayoutInflater.from(context)
        val words = resources.getStringArray(R.array.word_list)
        val suggestions = ArrayAdapter(context, android.R.layout.simple_list_item_1, words)

        wordCells.clear()
        binding.seedGrid.removeAllViews()
        var index = 0
        for (row in 0 until SEED_ROWS) {
            val rowView = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            for (col in 0 until SEED_COLS) {
                val cell = inflater.inflate(R.layout.item_seed_word, rowView, false)
                cell.layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
                val number = cell.findViewById<TextView>(R.id.text_number)
                val input = cell.findViewById<AutoCompleteTextView>(R.id.input_word)
                number.text = (index + 1).toString()
                input.setAdapter(suggestions)
                input.imeOptions =
                    if (index == SEED_WORD_COUNT - 1) EditorInfo.IME_ACTION_DONE
                    else EditorInfo.IME_ACTION_NEXT
                input.addTextChangedListener { updateDoneViews() }
                wordCells.add(input)
                rowView.addView(cell)
                index++
            }
            binding.seedGrid.addView(rowView)
        }
    }

    private fun collectSeedPhrase(): String =
        wordCells.joinToString(" ") { it.text.toString().trim().lowercase() }.trim()

    private fun filledWordCount(): Int =
        wordCells.count { it.text.toString().trim().isNotEmpty() }

    private fun onClearSeedWords() {
        mainActivity?.showConfirmation(
            "清除全部 Clear all words",
            "确定清除所有已输入的助记词、重新输入吗？Clear all entered words and start over?",
            "清除 Clear",
            onPositive = {
                wordCells.forEach { it.setText("") }
                wordCells.firstOrNull()?.requestFocus()
                updateDoneViews()
            }
        )
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        mainActivity?.onFragmentBackPressed(this) {
            tapped(RESTORE_BACK)
            if (filledWordCount() == 0) {
                onExit()
            } else {
                MaterialAlertDialogBuilder(requireContext())
                    .setMessage("确定退出？为安全起见，已输入的助记词会被清除。Entered words will be cleared.")
                    .setTitle("退出？Abort?")
                    .setPositiveButton("继续输入 Stay") { dialog, _ ->
                        mainActivity?.reportFunnel(Restore.Stay)
                        dialog.dismiss()
                    }
                    .setNegativeButton("退出 Exit") { dialog, _ ->
                        dialog.dismiss()
                        onExit()
                    }
                    .show()
            }
        }
    }

    private fun onExit() {
        mainActivity?.reportFunnel(Restore.Exit)
        mainActivity?.hideKeyboard()
        mainActivity?.navController?.popBackStack()
    }

    private fun onEnterWallet() {
        mainActivity?.reportFunnel(Restore.Success)
        mainActivity?.safeNavigate(R.id.action_nav_restore_to_nav_home)
    }

    private fun onDone() {
        mainActivity?.reportFunnel(Restore.Done)
        mainActivity?.hideKeyboard()
        val activation = ZcashWalletApp.instance.defaultNetwork.saplingActivationHeight
        val seedPhrase = collectSeedPhrase()
        val birthday = binding.inputBirthdate.text.toString().let { s ->
            if (s.isEmpty()) activation.value else (s.toLongOrNull() ?: activation.value)
        }.coerceAtLeast(activation.value)

        try {
            walletSetup.validatePhrase(seedPhrase)
            importWallet(
                seedPhrase,
                BlockHeight.new(ZcashWalletApp.instance.defaultNetwork, birthday)
            )
        } catch (t: Throwable) {
            mainActivity?.showInvalidSeedPhraseError(t)
        }
    }

    private fun importWallet(seedPhrase: String, birthday: BlockHeight?) {
        mainActivity?.reportFunnel(Restore.ImportStarted)
        mainActivity?.hideKeyboard()
        mainActivity?.apply {
            lifecycleScope.launch {
                try {
                    walletSetup.importWallet(seedPhrase, birthday)
                    mainActivity?.startSync()
                    // bugfix: if the user proceeds before the synchronizer is created the app will crash!
                    binding.buttonSuccess.isEnabled = true
                    mainActivity?.reportFunnel(Restore.ImportCompleted)
                    playSound("sound_receive_small.mp3")
                    vibrateSuccess()
                } catch (e: UnsatisfiedLinkError) {
                    mainActivity?.showSharedLibraryCriticalError(e)
                }
            }
        }

        binding.groupDone.visibility = View.GONE
        binding.groupStart.visibility = View.GONE
        binding.groupSuccess.visibility = View.VISIBLE
        binding.buttonSuccess.isEnabled = false
    }

    private fun updateDoneViews() {
        val count = filledWordCount()
        if (count >= 1 && !reportedStart) {
            reportedStart = true
            mainActivity?.reportFunnel(Restore.SeedWordsStarted)
        }
        val isDone = count >= SEED_WORD_COUNT
        if (isDone && !reportedComplete) {
            reportedComplete = true
            mainActivity?.reportFunnel(Restore.SeedWordsCompleted)
        }
        binding.buttonDone.isEnabled = isDone
    }

    override fun onKey(v: View?, keyCode: Int, event: KeyEvent?): Boolean = false

    companion object {
        private const val SEED_WORD_COUNT = 24
        private const val SEED_COLS = 4
        private const val SEED_ROWS = 6
    }
}

class SeedWordChip(val word: String, var index: Int = -1) : Chip() {
    override fun getSubtitle(): String? = null // "subtitle for $word"
    override fun getAvatarDrawable(): Drawable? = null
    override fun getId() = index
    override fun getTitle() = word
    override fun getAvatarUri() = null
}
