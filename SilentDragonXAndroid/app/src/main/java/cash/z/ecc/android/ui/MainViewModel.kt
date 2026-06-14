package cash.z.ecc.android.ui

import androidx.lifecycle.ViewModel
import cash.z.ecc.android.util.twig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel : ViewModel() {
    // Start with no full-screen overlay: the Compose home shows its own sync state (\u8FDE\u63A5\u4E2D/\u626B\u63CF\u4E2D\u2026),
    // so a bare "Loading..." no longer lingers over a heavy wallet's slow startup.
    private val _loadingMessage = MutableStateFlow<String?>(null)
    private val _syncReady = MutableStateFlow(false)
    val syncRestarted = MutableStateFlow(false)
    val loadingMessage: StateFlow<String?> get() = _loadingMessage
    val isLoading get() = loadingMessage.value != null

    /**
     * A flow of booleans representing whether or not the synchronizer has been started. This is
     * useful for views that want to monitor the status of the wallet but don't want to access the
     * synchronizer before it is ready to be used. This is also helpful for race conditions where
     * the status of the synchronizer is needed before it is created.
     */
    val syncReady = _syncReady.asStateFlow()

    fun setLoading(isLoading: Boolean = false, message: String? = null) {
        twig("MainViewModel.setLoading: $isLoading")
        _loadingMessage.value = if (!isLoading) {
            null
        } else {
            message ?: "\u23F3 Loading..."
        }
    }

    fun setSyncReady(isReady: Boolean) {
        twig("MainViewModel.setSyncReady: $isReady")
        _syncReady.value = isReady
    }
}
