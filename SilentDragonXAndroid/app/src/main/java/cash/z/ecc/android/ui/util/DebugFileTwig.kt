package cash.z.ecc.android.ui.util

import cash.z.ecc.android.ZcashWalletApp
import cash.z.ecc.android.util.TroubleshootingTwig
import okio.appendingSink
import okio.buffer
import java.io.File

class DebugFileTwig(fileName: String = "developer_log.txt") : TroubleshootingTwig(formatter = spiffy(6)) {
    val file = File("${ZcashWalletApp.instance.filesDir}/logs", fileName)

    override fun twig(logMessage: String, priority: Int) {
        super.twig(logMessage, priority)
        appendToFile(formatter(logMessage))
    }

    private fun appendToFile(message: String) {
        file.appendingSink().buffer().use {
            it.writeUtf8("$message\n")
        }
    }
}
