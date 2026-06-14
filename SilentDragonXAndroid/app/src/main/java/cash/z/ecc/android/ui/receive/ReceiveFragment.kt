package cash.z.ecc.android.ui.receive

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import cash.z.ecc.android.di.DependenciesHolder
import cash.z.ecc.android.ui.MainActivity
import cash.z.ecc.android.ui.compose.DragonXTheme
import cash.z.ecc.android.ui.compose.ReceiveScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Modern Compose receive screen: shows the wallet's shielded (zs…) address with a QR code and
 * copy/share actions. Replaces the old tab-based receive UI. The address comes from the proven
 * synchronizer (getAddress()).
 */
class ReceiveFragment : Fragment() {

    private val address = MutableStateFlow<String?>(null)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val addr by address.collectAsState()
            DragonXTheme {
                ReceiveScreen(
                    address = addr,
                    onBack = { (activity as? MainActivity)?.navController?.popBackStack() },
                    onCopy = { addr?.let { (activity as? MainActivity)?.copyText(it, "DragonX 地址") } },
                    onShare = { addr?.let { (activity as? MainActivity)?.shareText(it) } },
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            address.value = runCatching { DependenciesHolder.synchronizer.getAddress() }.getOrNull()
        }
    }
}
