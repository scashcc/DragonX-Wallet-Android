package cash.z.ecc.android.ext

import android.app.ActivityManager
import android.app.Dialog
import android.content.Context
import android.text.Html
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.getSystemService
import cash.z.ecc.android.R
import cash.z.ecc.android.feedback.Report
import cash.z.ecc.android.ui.MainActivity
import cash.z.ecc.android.ui.scan.ScanFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.*
import java.net.InetSocketAddress
import java.net.Socket

fun Context.showClearDataConfirmation(onDismiss: () -> Unit = {}, onCancel: () -> Unit = {}): Dialog {
    return MaterialAlertDialogBuilder(this)
        .setTitle(R.string.dialog_nuke_wallet_title)
        .setMessage(R.string.dialog_nuke_wallet_message)
        .setCancelable(false)
        .setPositiveButton(R.string.dialog_nuke_wallet_button_positive) { dialog, _ ->
            dialog.dismiss()
            onDismiss()
            onCancel()
        }
        .setNegativeButton(R.string.dialog_nuke_wallet_button_negative) { dialog, _ ->
            dialog.dismiss()
            onDismiss()
            getSystemService<ActivityManager>()?.clearApplicationUserData()
        }
        .show()
}

fun Context.showUninitializedError(error: Throwable? = null, onDismiss: () -> Unit = {}): Dialog {
    return MaterialAlertDialogBuilder(this)
        .setTitle(R.string.dialog_error_uninitialized_title)
        .setMessage(R.string.dialog_error_uninitialized_message)
        .setCancelable(false)
        .setPositiveButton(getString(R.string.dialog_error_uninitialized_button_positive)) { dialog, _ ->
            dialog.dismiss()
            onDismiss()
            if (error != null) throw error
        }
        .setNegativeButton(getString(R.string.dialog_error_uninitialized_button_negative)) { dialog, _ ->
            showClearDataConfirmation(
                onDismiss,
                onCancel = {
                    // do not let the user back into the app because we cannot recover from this case
                    showUninitializedError(error, onDismiss)
                }
            )
        }
        .show()
}

fun Context.showInvalidSeedPhraseError(error: Throwable? = null, onDismiss: () -> Unit = {}): Dialog {
    return MaterialAlertDialogBuilder(this)
        .setTitle(R.string.dialog_error_invalid_seed_phrase_title)
        .setMessage(getString(R.string.dialog_error_invalid_seed_phrase_message, error?.message ?: ""))
        .setCancelable(false)
        .setPositiveButton(getString(R.string.dialog_error_invalid_seed_phrase_button_positive)) { dialog, _ ->
            dialog.dismiss()
            onDismiss()
        }
        .show()
}

fun Context.showScanFailure(error: Throwable?, onCancel: () -> Unit = {}, onDismiss: () -> Unit = {}): Dialog {
    val message = if (error == null) {
        "Unknown error"
    } else {
        "${error.message}${if (error.cause != null) "\n\nCaused by: ${error.cause}" else ""}"
    }
    return MaterialAlertDialogBuilder(this)
        .setTitle(R.string.dialog_error_scan_failure_title)
        .setMessage(message)
        .setCancelable(true)
        .setPositiveButton(R.string.dialog_error_scan_failure_button_positive) { d, _ ->
            d.dismiss()
            onDismiss()
        }
        .setNegativeButton(R.string.dialog_error_scan_failure_button_negative) { d, _ ->
            d.dismiss()
            onCancel()
            onDismiss()
        }
        .show()
}

fun Context.showCriticalMessage(@StringRes titleResId: Int, @StringRes messageResId: Int, onDismiss: () -> Unit = {}): Dialog {
    return showCriticalMessage(titleResId.toAppString(), messageResId.toAppString(), onDismiss)
}

fun Context.showCriticalMessage(title: String, message: String, onDismiss: () -> Unit = {}): Dialog {
    Log.d("SilentDragon", "showCriticalMessage called: $message")

    var delimiter = ":"
    val splitError = message.split(delimiter)
    var pluckedError = splitError[0]

    if(pluckedError == "UNAVAILABLE" || pluckedError == "DEADLINE_EXCEEDED"){
        return showServerPickerDialog(onServerSelected = { host ->
            // Do NOT call onDismiss() here — it throws the original error
        })
    }

    return MaterialAlertDialogBuilder(this)
        .setTitle(title)
        .setMessage(message)
        .setCancelable(false)
        .setPositiveButton(android.R.string.ok) { d, _ ->
            d.dismiss()
            onDismiss()
        }
        .show()
}

fun Context.showCriticalProcessorError(error: Throwable?, onRetry: () -> Unit = {}): Dialog {
    return MaterialAlertDialogBuilder(this)
        .setTitle(R.string.dialog_error_processor_critical_title)
        .setMessage(error?.message ?: getString(R.string.dialog_error_processor_critical_message))
        .setCancelable(false)
        .setPositiveButton(R.string.dialog_error_processor_critical_button_positive) { d, _ ->
            d.dismiss()
            onRetry()
        }
        .setNegativeButton(R.string.dialog_error_processor_critical_button_negative) { dialog, _ ->
            dialog.dismiss()
            throw error ?: RuntimeException("Critical error while processing blocks and the user chose to exit.")
        }
        .show()
}

fun Context.showUpdateServerCriticalError(userFacingMessage: String, onConfirm: () -> Unit = {}): Dialog {
    return MaterialAlertDialogBuilder(this)
        .setTitle(R.string.dialog_error_change_server_title)
        .setMessage(userFacingMessage)
        .setCancelable(false)
        .setPositiveButton(R.string.dialog_error_change_server_button_positive) { d, _ ->
            d.dismiss()
            onConfirm()
        }
        .show()
}

fun Context.showUpdateServerDialog(positiveResId: Int = R.string.dialog_modify_server_button_positive, onCancel: () -> Unit = {}, onUpdate: () -> Unit = {}): Dialog {
    return MaterialAlertDialogBuilder(this)
        .setTitle(R.string.dialog_modify_server_title)
        .setMessage(R.string.dialog_modify_server_message)
        .setCancelable(false)
        .setPositiveButton(positiveResId) { dialog, _ ->
            dialog.dismiss()
            onUpdate()
        }
        .setNegativeButton(R.string.dialog_modify_server_button_negative) { dialog, _ ->
            dialog.dismiss()
            onCancel()
        }
        .show()
}

fun Context.showRescanWalletDialog(quickDistance: String, quickEstimate: String, fullDistance: String, fullEstimate: String, onWipe: () -> Unit = {}, onFullRescan: () -> Unit = {}, onQuickRescan: () -> Unit = {}): Dialog {
    return MaterialAlertDialogBuilder(this)
        .setTitle(R.string.dialog_rescan_wallet_title)
        .setMessage(Html.fromHtml(getString(R.string.dialog_rescan_wallet_message, quickDistance, quickEstimate, fullDistance, fullEstimate)))
        .setCancelable(true)
        .setPositiveButton(R.string.dialog_rescan_wallet_button_positive) { dialog, _ ->
            dialog.dismiss()
            onQuickRescan()
        }
        .setNeutralButton(R.string.dialog_rescan_wallet_button_neutral) { dialog, _ ->
            dialog.dismiss()
            onWipe()
        }
        .setNegativeButton(R.string.dialog_rescan_wallet_button_negative) { dialog, _ ->
            dialog.dismiss()
            onFullRescan()
        }
        .show()
}

fun Context.showConfirmation(title: String, message: String, positiveButton: String, negativeButton: String = "Cancel", onPositive: () -> Unit = {}): Dialog {
    return MaterialAlertDialogBuilder(this)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton(positiveButton) { dialog, _ ->
            dialog.dismiss()
            onPositive()
        }
        .setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
        }
        .show()
}

/**
 * Error to show when the Rust libraries did not properly link. This problem can happen pretty often
 * during development when a build of the SDK failed to compile and resulted in an AAR file with no
 * shared libraries (*.so files) inside. In theory, this should never be seen by an end user but if
 * it does occur it is better to show a clean message explaining the situation. Nothing can be done
 * other than rebuilding the SDK or switching to a functional version.
 * As a developer, this error probably means that you need to comment out mavenLocal() as a repo.
 */
fun Context.showSharedLibraryCriticalError(e: Throwable): Dialog = showCriticalMessage(
    titleResId = R.string.dialog_error_critical_link_title,
    messageResId = R.string.dialog_error_critical_link_message,
    onDismiss = { throw e }
)

fun Context.showReorgRepairDialog(onDismiss: () -> Unit = {}): Dialog {
    return MaterialAlertDialogBuilder(this)
        .setTitle("Incompatible Block Data")
        .setMessage(
            "The wallet contains block data from an old or incompatible chain. " +
            "This prevents syncing.\n\n" +
            "Would you like to clear the old data and sync fresh from the network?\n" +
            "(Your wallet keys and addresses will be preserved)"
        )
        .setCancelable(false)
        .setPositiveButton("Clear & Resync") { dialog, _ ->
            dialog.dismiss()
            onDismiss()
            getSystemService<ActivityManager>()?.clearApplicationUserData()
        }
        .setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
            onDismiss()
        }
        .show()
}

fun Context.showServerPickerDialog(
    userInitiated: Boolean = false,
    onServerSelected: (String) -> Unit = {}
): Dialog {
    val servers = Const.Default.Server.serverList
    val port = Const.Default.Server.PORT
    val currentHost = cash.z.ecc.android.di.DependenciesHolder.prefs[Const.Pref.SERVER_HOST]
        ?: Const.Default.Server.HOST
    // Display names start as "Checking..." and get updated. "●" marks the currently-selected node.
    val displayItems = servers.map { host ->
        val marker = if (host == currentHost) "● " else ""
        "$marker$host — checking..."
    }.toMutableList()

    val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, displayItems)

    val listView = ListView(this).apply {
        this.adapter = adapter
        setPadding(32, 16, 32, 16)
    }

    val builder = MaterialAlertDialogBuilder(this)
        .setTitle(if (userInitiated) "选择节点 Choose node" else "Select a Server")
        .setMessage(
            if (userInitiated) "点击任意节点即可切换（● 为当前节点）。Tap a node to switch."
            else "The current server is unavailable. Choose a server to connect to:"
        )
        .setView(listView)
        // Always cancelable and never fatal: a transient node error must NOT trap the user or kill
        // the app. Dismissing without choosing just lets the sync loop keep auto-reconnecting.
        .setCancelable(true)
    builder.setNegativeButton("关闭 Close") { d, _ -> d.dismiss() }
    val dialog = builder.show()

    // Check each server's connectivity in the background
    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    servers.forEachIndexed { index, host ->
        scope.launch {
            val online = try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), 5000)
                    true
                }
            } catch (e: Exception) {
                false
            }
            withContext(Dispatchers.Main) {
                val marker = if (host == currentHost) "● " else ""
                displayItems[index] =
                    if (online) "$marker✓  $host" else "$marker✗  $host  (offline)"
                adapter.notifyDataSetChanged()
            }
        }
    }

    listView.setOnItemClickListener { _, _, position, _ ->
        val host = servers[position]
        // Save selected server to preferences
        val prefs = cash.z.ecc.android.di.DependenciesHolder.prefs
        prefs[Const.Pref.SERVER_HOST] = host
        prefs[Const.Pref.SERVER_PORT] = port
        dialog.dismiss()
        scope.cancel()
        onServerSelected(host)
        // Reconnect with the new server without restarting
        (this as? MainActivity)?.let { activity ->
            try {
                cash.z.ecc.android.di.DependenciesHolder.resetSynchronizer()
                activity.startSync(isRestart = true)
            } catch (e: Exception) {
                android.util.Log.e("SilentDragon", "Error reconnecting after server change", e)
            }
        }
    }

    return dialog
}
