package cash.z.ecc.android.ext

import cash.z.ecc.android.BuildConfig

object Const {
    /**
     * Named objects for Dependency Injection.
     */
    object Name {
        /** application data other than cryptographic keys */
        const val APP_PREFS = "const.name.app_prefs"
        const val BEFORE_SYNCHRONIZER = "const.name.before_synchronizer"
        const val SYNCHRONIZER = "const.name.synchronizer"
    }

    /**
     * App preference key names.
     */
    object Pref {
        const val FIRST_USE_VIEW_TX = "const.pref.first_use_view_tx"
        const val EASTER_EGG_TRIGGERED_SHIELDING = "const.pref.easter_egg_shielding"
        const val FEEDBACK_ENABLED = "const.pref.feedback_enabled"
        const val SERVER_HOST = "const.pref.server_host"
        const val SERVER_PORT = "const.pref.server_port"
        const val STREET_MODE = "const.pref.street_mode"

        // DragonX multi-wallet: number of wallets and the active wallet index.
        const val WALLET_COUNT = "const.pref.wallet_count"
        const val WALLET_ACTIVE = "const.pref.wallet_active"
    }

    /**
     * Constants used for wallet backup.
     */
    object Backup {
        const val SEED = "cash.z.ecc.android.SEED"
        const val SEED_PHRASE = "cash.z.ecc.android.SEED_PHRASE"
        const val HAS_SEED = "cash.z.ecc.android.HAS_SEED"
        const val HAS_SEED_PHRASE = "cash.z.ecc.android.HAS_SEED_PHRASE"
        const val HAS_BACKUP = "cash.z.ecc.android.HAS_BACKUP"

        // Config
        const val VIEWING_KEY = "cash.z.ecc.android.VIEWING_KEY"
        const val PUBLIC_KEY = "cash.z.ecc.android.PUBLIC_KEY"
        const val BIRTHDAY_HEIGHT = "cash.z.ecc.android.BIRTHDAY_HEIGHT"

        // DragonX private-key restore: when a wallet is restored from a Sapling spending key
        // (secret-extended-key-main…) there is no seed, so the spending key is stored directly here
        // and used for spending. See [cash.z.ecc.android.ext.Keys].
        const val SPENDING_KEY = "cash.z.ecc.android.SPENDING_KEY"
    }

    /**
     * Default values to use application-wide. Ideally, this set of values should remain very short.
     */
    object Default {
        object Server {
            // The first entry is the default. hk.dragonx.cc is the known-good DragonX
            // node (TLS on 443); the lite*.dragonx.is servers are selectable alternates.
            val serverList = listOf(
                "hk.dragonx.cc",
                "lite.dragonx.is",
                "lite1.dragonx.is",
                "lite2.dragonx.is",
                "lite3.dragonx.is",
                "lite4.dragonx.is",
                "lite5.dragonx.is"
            )

            val HOST = serverList.first()
            const val PORT = 443
        }
    }
}
