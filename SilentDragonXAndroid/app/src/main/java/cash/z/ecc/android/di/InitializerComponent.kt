package cash.z.ecc.android.di

import cash.z.ecc.android.sdk.Initializer

class InitializerComponent {

    lateinit var initializer: Initializer
        private set

    fun createInitializer(config: Initializer.Config) {
        initializer = Initializer.newBlocking(DependenciesHolder.provideAppContext(), config)
    }

}