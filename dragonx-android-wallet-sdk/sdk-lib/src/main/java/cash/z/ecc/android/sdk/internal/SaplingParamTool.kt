package cash.z.ecc.android.sdk.internal

import android.content.Context
import cash.z.ecc.android.sdk.exception.TransactionEncoderException
import cash.z.ecc.android.sdk.ext.ZcashSdk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class SaplingParamTool {

    companion object {

        private var appContext: Context? = null

        /**
         * Initialize with application context so params can be copied from bundled assets.
         */
        fun init(context: Context) {
            appContext = context.applicationContext
        }

        /**
         * Checks the given directory for the output and spending params and copies them from
         * bundled assets if they're missing.
         *
         * @param destinationDir the directory where the params should be stored.
         */
        suspend fun ensureParams(destinationDir: String) {
            var hadError = false
            arrayOf(
                ZcashSdk.SPEND_PARAM_FILE_NAME,
                ZcashSdk.OUTPUT_PARAM_FILE_NAME
            ).forEach { paramFileName ->
                if (!File(destinationDir, paramFileName).existsSuspend()) {
                    twig("WARNING: $paramFileName not found at location: $destinationDir")
                    hadError = true
                }
            }
            if (hadError) {
                try {
                    Bush.trunk.twigTask("copying bundled sapling params") {
                        copyBundledParams(destinationDir)
                    }
                } catch (e: Throwable) {
                    twig("failed to copy bundled params due to: $e")
                    throw TransactionEncoderException.MissingParamsException
                }
            }
        }

        /**
         * Copy the sapling params from bundled assets into the given directory.
         *
         * @param destinationDir the directory where the params will be stored.
         */
        private suspend fun copyBundledParams(destinationDir: String) {
            val context = appContext
                ?: throw IllegalStateException("SaplingParamTool not initialized. Call init(context) first.")

            arrayOf(
                ZcashSdk.SPEND_PARAM_FILE_NAME,
                ZcashSdk.OUTPUT_PARAM_FILE_NAME
            ).forEach { paramFileName ->
                val destFile = File(destinationDir, paramFileName)
                if (!destFile.existsSuspend()) {
                    if (destFile.parentFile?.existsSuspend() != true) {
                        destFile.parentFile?.mkdirsSuspend()
                    }
                    withContext(Dispatchers.IO) {
                        context.assets.open("params/$paramFileName").use { input ->
                            destFile.outputStream().use { output ->
                                twig("copying bundled $paramFileName to $destFile")
                                input.copyTo(output)
                            }
                        }
                    }
                }
            }
        }

        suspend fun clear(destinationDir: String) {
            if (validate(destinationDir)) {
                arrayOf(
                    ZcashSdk.SPEND_PARAM_FILE_NAME,
                    ZcashSdk.OUTPUT_PARAM_FILE_NAME
                ).forEach { paramFileName ->
                    val file = File(destinationDir, paramFileName)
                    if (file.deleteRecursivelySuspend()) {
                        twig("Files deleted successfully")
                    } else {
                        twig("Error: Files not able to be deleted!")
                    }
                }
            }
        }

        suspend fun validate(destinationDir: String): Boolean {
            return arrayOf(
                ZcashSdk.SPEND_PARAM_FILE_NAME,
                ZcashSdk.OUTPUT_PARAM_FILE_NAME
            ).all { paramFileName ->
                File(destinationDir, paramFileName).existsSuspend()
            }.also {
                println("Param files${if (!it) "did not" else ""} both exist!")
            }
        }

    }
}

suspend fun File.existsSuspend() = withContext(Dispatchers.IO) { exists() }
suspend fun File.mkdirsSuspend() = withContext(Dispatchers.IO) { mkdirs() }
suspend fun File.deleteRecursivelySuspend() = withContext(Dispatchers.IO) { deleteRecursively() }
