package cash.z.ecc.android.ext

import cash.z.ecc.android.util.twig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Lightweight "is there a newer build?" check. Reads the latest GitHub release of the build repo and
 * extracts the version from the universal APK asset name (DragonX-Wallet-vX.Y.Z-universal.apk), which
 * the CI names deterministically. This is REMINDER-ONLY: nothing is downloaded or installed
 * automatically — the UI just shows current vs latest and offers links the user can tap to update.
 *
 * Network failures are swallowed (returns latestVersion = null); the footer then falls back to a
 * "tap to check" / "open releases page" affordance, so a blocked api.github.com never breaks the
 * settings screen.
 */
object UpdateChecker {

    /** Always-usable human page (works even when the API call fails). */
    const val RELEASES_PAGE = "https://github.com/scashcc/DragonX-Wallet-Android/releases/latest"

    private const val API =
        "https://api.github.com/repos/scashcc/DragonX-Wallet-Android/releases/latest"

    private val APK_NAME = Regex("DragonX-Wallet-v(.+?)-universal\\.apk")

    data class Result(
        val currentVersion: String,
        val latestVersion: String?,   // null when the check failed / hasn't completed
        val downloadUrl: String?,     // direct universal-APK link, when available
        val pageUrl: String,          // release page (always usable)
        val hasUpdate: Boolean,
    )

    suspend fun check(currentVersion: String): Result = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(API).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "DragonX-Wallet-Android")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val obj = JSONObject(body)
            val pageUrl = obj.optString("html_url", RELEASES_PAGE).ifBlank { RELEASES_PAGE }

            var latest: String? = null
            var downloadUrl: String? = null
            obj.optJSONArray("assets")?.let { assets ->
                for (idx in 0 until assets.length()) {
                    val asset = assets.getJSONObject(idx)
                    val match = APK_NAME.find(asset.optString("name"))
                    if (match != null) {
                        latest = match.groupValues[1]
                        downloadUrl = asset.optString("browser_download_url").ifBlank { null }
                        break
                    }
                }
            }

            val newer = latest != null && isNewer(latest!!, currentVersion)
            Result(currentVersion, latest, downloadUrl, pageUrl, newer)
        } catch (t: Throwable) {
            twig("UpdateChecker failed: $t")
            Result(currentVersion, null, null, RELEASES_PAGE, false)
        }
    }

    /** Numeric, dot-separated semver comparison (1.5.10 > 1.5.9). Non-numeric parts count as 0. */
    private fun isNewer(latest: String, current: String): Boolean {
        val l = latest.trim().split(".")
        val c = current.trim().split(".")
        for (i in 0 until maxOf(l.size, c.size)) {
            val lv = l.getOrNull(i)?.toIntOrNull() ?: 0
            val cv = c.getOrNull(i)?.toIntOrNull() ?: 0
            if (lv != cv) return lv > cv
        }
        return false
    }
}
