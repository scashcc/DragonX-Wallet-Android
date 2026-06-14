package cash.z.ecc.android.ui.node

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
import cash.z.ecc.android.ext.Const
import cash.z.ecc.android.ui.MainActivity
import cash.z.ecc.android.ui.compose.DragonXTheme
import cash.z.ecc.android.ui.compose.NodeScreen
import cash.z.ecc.android.ui.compose.NodeStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Full-screen Compose node picker (replaces the old dialog). Probes every default server's latency
 * in parallel (TCP connect time) and lets the user switch without restarting — it writes the chosen
 * host to prefs and rebuilds the synchronizer (same logic the old dialog used).
 */
class NodeFragment : Fragment() {

    private val nodesState = MutableStateFlow<List<NodeStatus>>(emptyList())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val nodes by nodesState.collectAsState()
            DragonXTheme {
                NodeScreen(
                    nodes = nodes,
                    onBack = { (activity as? MainActivity)?.navController?.popBackStack() },
                    onSelect = ::selectNode,
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        probeAll()
    }

    private fun currentHost(): String =
        DependenciesHolder.prefs[Const.Pref.SERVER_HOST] ?: Const.Default.Server.HOST

    private fun probeAll() {
        val cur = currentHost()
        val servers = Const.Default.Server.serverList
        nodesState.value = servers.map { NodeStatus(it, null, it == cur) }
        val port = Const.Default.Server.PORT
        servers.forEach { host ->
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val ms = measureLatency(host, port)
                nodesState.update { list -> list.map { if (it.host == host) it.copy(latencyMs = ms) else it } }
            }
        }
    }

    private fun measureLatency(host: String, port: Int): Long {
        return try {
            val start = System.currentTimeMillis()
            Socket().use { it.connect(InetSocketAddress(host, port), 5000) }
            System.currentTimeMillis() - start
        } catch (e: Exception) {
            -1L
        }
    }

    private fun selectNode(host: String) {
        val prefs = DependenciesHolder.prefs
        prefs[Const.Pref.SERVER_HOST] = host
        prefs[Const.Pref.SERVER_PORT] = Const.Default.Server.PORT
        (activity as? MainActivity)?.let { act ->
            try {
                DependenciesHolder.resetSynchronizer()
                act.startSync(isRestart = true)
            } catch (e: Exception) {
                android.util.Log.e("DRGXNODE", "Error switching node", e)
            }
            act.showSnackbar("已切换到 $host")
            act.navController?.popBackStack()
        }
    }
}
