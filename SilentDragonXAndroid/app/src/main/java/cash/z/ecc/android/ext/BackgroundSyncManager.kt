package cash.z.ecc.android.ext

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import cash.z.ecc.android.di.DependenciesHolder
import cash.z.ecc.android.service.SyncForegroundService
import cash.z.ecc.android.util.twig

/**
 * Enables/disables the [SyncForegroundService] (long-term background sync) and remembers the choice.
 *
 * Stored as a "disabled" flag so the feature defaults to ON (a missing pref reads as false ->
 * enabled): keeping the foreground service alive is what stops aggressive OEMs from killing the
 * process mid-write and corrupting the block DB, so it is the safer default for this wallet.
 */
object BackgroundSyncManager {

    private const val PREF_DISABLED = "dragonx_background_sync_disabled"

    fun isEnabled(): Boolean =
        !runCatching { DependenciesHolder.prefs.getBoolean(PREF_DISABLED) }.getOrDefault(false)

    /** User-facing toggle: persist the choice and start/stop the service accordingly. */
    fun setEnabled(context: Context, enabled: Boolean) {
        setEnabledInternal(context, enabled)
        if (enabled) start(context) else stop(context)
    }

    /** Persist the flag only (used by the service's "stop" action to avoid restarting itself). */
    fun setEnabledInternal(context: Context, enabled: Boolean) {
        runCatching { DependenciesHolder.prefs.setBoolean(PREF_DISABLED, !enabled) }
    }

    /** Start the foreground service if the user hasn't turned background sync off. */
    fun startIfEnabled(context: Context) {
        if (isEnabled()) start(context)
    }

    private fun start(context: Context) {
        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, SyncForegroundService::class.java)
            )
        }.onFailure { twig("BackgroundSyncManager: failed to start service: $it") }
    }

    private fun stop(context: Context) {
        runCatching {
            context.stopService(Intent(context, SyncForegroundService::class.java))
        }.onFailure { twig("BackgroundSyncManager: failed to stop service: $it") }
    }
}
