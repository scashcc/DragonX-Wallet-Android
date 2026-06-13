package cash.z.ecc.android.ui.receive

import androidx.lifecycle.ViewModel
import cash.z.ecc.android.di.DependenciesHolder
import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.util.twig

class ReceiveViewModel : ViewModel() {

    private val synchronizer: Synchronizer = DependenciesHolder.synchronizer

    suspend fun getAddress(): String = synchronizer.getAddress()

    override fun onCleared() {
        super.onCleared()
        twig("ReceiveViewModel cleared!")
    }
}
