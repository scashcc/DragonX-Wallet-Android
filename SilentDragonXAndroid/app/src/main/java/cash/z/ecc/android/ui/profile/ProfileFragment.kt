package cash.z.ecc.android.ui.profile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.core.content.FileProvider.getUriForFile
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewModelScope
import cash.z.ecc.android.BuildConfig
import cash.z.ecc.android.R
import cash.z.ecc.android.ZcashWalletApp
import cash.z.ecc.android.databinding.FragmentProfileBinding
import cash.z.ecc.android.ext.*
import cash.z.ecc.android.feedback.FeedbackFile
import cash.z.ecc.android.feedback.Report
import cash.z.ecc.android.feedback.Report.Funnel.UserFeedback
import cash.z.ecc.android.feedback.Report.Tap.*
import cash.z.ecc.android.sdk.SdkSynchronizer
import cash.z.ecc.android.sdk.ext.toAbbreviatedAddress
import cash.z.ecc.android.ui.MainActivity
import cash.z.ecc.android.ui.base.BaseFragment
import cash.z.ecc.android.ui.util.DebugFileTwig
import cash.z.ecc.android.util.Bush
import cash.z.ecc.android.util.twig
import kotlinx.coroutines.launch
import java.io.File

class ProfileFragment : BaseFragment<FragmentProfileBinding>() {
    override val screen = Report.Screen.PROFILE

    private val viewModel: ProfileViewModel by viewModels()

    override fun inflate(inflater: LayoutInflater): FragmentProfileBinding =
        FragmentProfileBinding.inflate(inflater)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.hitAreaSettings.onClickNavTo(R.id.action_nav_profile_to_nav_settings)
        binding.hitAreaExit.onClickNavBack() { tapped(PROFILE_CLOSE) }
        binding.buttonBackup.setOnClickListener {
            tapped(PROFILE_BACKUP)
            mainActivity?.let { main ->
                main.authenticate(
                    getString(R.string.biometric_backup_phrase_description),
                    getString(R.string.biometric_backup_phrase_title)
                ) {
                    main.safeNavigate(R.id.action_nav_profile_to_nav_backup)
                }
            }
        }
        binding.buttonExportKeys.setOnClickListener {
            mainActivity?.let { main ->
                main.authenticate(
                    getString(R.string.biometric_backup_phrase_description),
                    getString(R.string.biometric_backup_phrase_title)
                ) {
                    main.safeNavigate(R.id.action_nav_profile_to_nav_export_keys)
                }
            }
        }
        binding.buttonConsolidate.setOnClickListener {
            // Consolidation is a self-send (funds stay with the user), so no biometric gate is
            // needed here; the consolidation screen has its own confirmation step.
            mainActivity?.safeNavigate(R.id.action_nav_profile_to_nav_consolidate)
        }
        binding.buttonRescan.setOnClickListener {
            tapped(PROFILE_RESCAN)
            onRescanWallet()
        }

        // Website
        binding.websiteButton.setOnClickListener {
            openWebsiteLink()
        }

        // Telegram
        binding.telegramButton.setOnClickListener {
            openTelegramLink()
        }

        // SilentDragon Gitea
        binding.textBannerMessage.setOnClickListener {
            openGiteaLink()
        }

        // Fakebook
        binding.fakebookButton.setOnClickListener {
            openFakebookLink()
        }

        // Twatter
        binding.twatterButton.setOnClickListener {
            openTwatterLink()
        }

        // Add build version
        binding.textVersion.text = BuildConfig.VERSION_NAME

        /*
        onClick(binding.buttonLogs) {
            tapped(PROFILE_VIEW_USER_LOGS)
            onViewLogs()
        }
        binding.buttonLogs.setOnLongClickListener {
            tapped(PROFILE_VIEW_DEV_LOGS)
            onViewDevLogs()
            true
        }*/
    }

    private fun openFakebookLink() {
        getString(R.string.fakebook_url).takeUnless { it.isBlank() }?.let { url ->
            mainActivity?.onLaunchUrl(url)
        }
    }

    private fun openGiteaLink() {
        getString(R.string.gitea_url).takeUnless { it.isBlank() }?.let { url ->
            mainActivity?.onLaunchUrl(url)
        }
    }

    private fun openMastodonLink() {
        getString(R.string.mastodon_url).takeUnless { it.isBlank() }?.let { url ->
            mainActivity?.onLaunchUrl(url)
        }
    }

    private fun openMatrixLink() {
        getString(R.string.matrix_url).takeUnless { it.isBlank() }?.let { url ->
            mainActivity?.onLaunchUrl(url)
        }
    }

    private fun openPeerTubeLink() {
        getString(R.string.peertube_url).takeUnless { it.isBlank() }?.let { url ->
            mainActivity?.onLaunchUrl(url)
        }
    }

    private fun openTwatterLink() {
        getString(R.string.twatter_url).takeUnless { it.isBlank() }?.let { url ->
            mainActivity?.onLaunchUrl(url)
        }
    }

    private fun openTelegramLink() {
        getString(R.string.telegram_url).takeUnless { it.isBlank() }?.let { url ->
            mainActivity?.onLaunchUrl(url)
        }
    }

    private fun openWebsiteLink() {
        getString(R.string.website_url).takeUnless { it.isBlank() }?.let { url ->
            mainActivity?.onLaunchUrl(url)
        }
    }

    private fun openYoutubeLink() {
        getString(R.string.youtube_url).takeUnless { it.isBlank() }?.let { url ->
            mainActivity?.onLaunchUrl(url)
        }
    }

    override fun onResume() {
        super.onResume()
        resumedScope.launch {
            binding.textAddress.text = viewModel.getShieldedAddress().toAbbreviatedAddress(12, 12)
        }
    }

    // TODO: reduce these to one function
    private fun onFullRescan() {
        twig("TMP: onFullRescan: CALLED")
        (viewModel.synchronizer as SdkSynchronizer).coroutineScope.launch {
            try {
                twig("TMP: onFullRescan: START")
                viewModel.fullRescan()
                Toast.makeText(ZcashWalletApp.instance, "Performing full rescan!", Toast.LENGTH_LONG).show()
                mainActivity?.navController?.popBackStack()
            } catch (t: Throwable) {
                mainActivity?.showCriticalMessage(
                    "Full Rescan Failed",
                    "Unable to perform full rescan due to error:\n\n${t.message}"
                )
            }
        }
    }

    private fun onQuickRescan() {
        twig("TMP: onQuickRescan: CALLED")
        viewModel.viewModelScope.launch {
            try {
                twig("TMP: onQuickRescan: START")
                viewModel.quickRescan()
                Toast.makeText(ZcashWalletApp.instance, "Performing quick rescan!", Toast.LENGTH_LONG).show()
                mainActivity?.navController?.popBackStack()
            } catch (t: Throwable) {
                mainActivity?.showCriticalMessage("Quick Rescan Failed", "Unable to perform quick rescan due to error:\n\n${t.message}")
            }
        }
    }

    private fun onWipe() {
        mainActivity?.showConfirmation(
            "清除区块数据并重新同步？",
            "将删除本地区块数据并从头同步（用于修复同步卡住或区块库损坏）。\n\n" +
                "你的助记词和地址不受影响，无需重装，也不用重新输入助记词。\n\n" +
                "应用会自动重启并开始重新同步。是否继续？",
            "清除并重新同步"
        ) {
            viewModel.wipe()
            mainActivity?.restartApp()
        }
    }

    private fun onRescanWallet() {
        val quickDistance = viewModel.quickScanDistance()
        val fullDistance = viewModel.fullScanDistance()
        mainActivity?.showRescanWalletDialog(
            String.format("%,d", quickDistance),
            viewModel.blocksToMinutesString(quickDistance),
            String.format("%,d", fullDistance),
            viewModel.blocksToMinutesString(fullDistance),
            onFullRescan = ::onFullRescan,
            onQuickRescan = ::onQuickRescan,
            onWipe = ::onWipe
        )
    }

    private fun onViewLogs() {
        shareFile(userLogFile())
    }

    private fun onViewDevLogs() {
        developerLogFile().let {
            if (it == null) {
                mainActivity?.showSnackbar("Error: No developer log found!")
            } else {
                shareFile(it)
            }
        }
    }

    private fun shareFiles(vararg files: File?) {
        val uris = arrayListOf<Uri>().apply {
            files.filterNotNull().mapNotNull {
                getUriForFile(ZcashWalletApp.instance, "${BuildConfig.APPLICATION_ID}.fileprovider", it)
            }.forEach {
                add(it)
            }
        }
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            type = "text/*"
        }
        startActivity(Intent.createChooser(intent, getString(R.string.profile_share_log_title)))
    }

    fun shareFile(file: File?) {
        file ?: return
        val uri = getUriForFile(ZcashWalletApp.instance, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, uri)
            type = "text/plain"
        }
        startActivity(Intent.createChooser(intent, getString(R.string.profile_share_log_title)))
    }

    private fun userLogFile(): File? {
        return mainActivity?.feedbackCoordinator?.findObserver<FeedbackFile>()?.file
    }

    private fun developerLogFile(): File? {
        return Bush.trunk.find<DebugFileTwig>()?.file
    }
}
