package cash.z.ecc.android.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import cash.z.ecc.android.databinding.FragmentAutoShieldInformationBinding
import cash.z.ecc.android.ext.requireApplicationContext
import cash.z.ecc.android.feedback.Report
import cash.z.ecc.android.preference.Preferences
import cash.z.ecc.android.preference.model.put
import cash.z.ecc.android.ui.base.BaseFragment

class AutoshieldingInformationFragment : BaseFragment<FragmentAutoShieldInformationBinding>() {
    override val screen = Report.Screen.AUTO_SHIELD_INFORMATION

    private val args: AutoshieldingInformationFragmentArgs by navArgs()

    override fun inflate(inflater: LayoutInflater): FragmentAutoShieldInformationBinding =
        FragmentAutoShieldInformationBinding.inflate(inflater)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        /*
         * Once the fragment is displayed, acknowledge it was presented to the user.  While it might
         * be better to have explicit user interaction (positive/negative button or back),
         * this implementation is simpler.  Hooking into the positive/negative button is easy, but
         * hooking into the back button from a Fragment ends up being gross.
         *
         * Always acknowledging is necessary, because the HomeFragment will otherwise almost immediately
         * re-launch this Fragment when it refreshes the UI (and therefore re-runs the
         * check as to whether the preference to display this fragment has been set).
         */
        acknowledge()

        binding.buttonAutoshieldDismiss.setOnClickListener {
            if (args.isStartAutoshield) {
                findNavController().navigate(AutoshieldingInformationFragmentDirections.actionNavAutoshieldingInfoToAutoshield())
            } else {
                findNavController().navigate(AutoshieldingInformationFragmentDirections.actionNavAutoshieldingInfoToHome())
            }
        }
        binding.buttonAutoshieldMoreInfo.setOnClickListener {
            try {
                findNavController().navigate(AutoshieldingInformationFragmentDirections.actionNavAutoshieldingInfoToBrowser())
            } catch (e: Exception) {
                // ActivityNotFoundException could happen on certain devices, like Android TV, Android Things, etc.

                // SecurityException shouldn't occur, but just in case we catch all exceptions to
                // prevent another package on the device from crashing us if that package tries to be malicious
                // by adding permissions or changing export status dynamically.

                // In the future, it might also be desirable to display a Toast or Snackbar indicating
                // that the browser couldn't be launched

                findNavController().navigate(AutoshieldingInformationFragmentDirections.actionNavAutoshieldingInfoToHome())
            }
        }
    }

    private fun acknowledge() {
        Preferences.isAcknowledgedAutoshieldingInformationPrompt.put(
            requireApplicationContext(),
            true
        )
    }
}
