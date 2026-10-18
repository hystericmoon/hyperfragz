package com.hosni.hyperfragz

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private const val REPO = "hystericmoon/hyperfragz"
private const val TAG = "Updater"

/**
 * Updates from GitHub Releases: the latest release's tag (v1.2.3) is compared
 * with this build's versionName, and its .apk asset is downloaded and handed
 * to the system installer (Android never installs sideloaded apps silently).
 */
class Updater(private val context: Context) {

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val version: String, val apkUrl: String, val size: Long) : State
        data class Downloading(val version: String, val progress: Float) : State
        data class Failed(val message: String) : State
    }

    var state by mutableStateOf<State>(State.Idle)
        private set

    /** Outlives the screens: leaving the index must not cancel a download. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun checkAsync(onDone: (State) -> Unit = {}) { scope.launch { check(); onDone(state) } }
    fun installAsync(update: State.Available) { scope.launch { install(update) } }

    /** Hidden after the user closes the banner, until the next launch. */
    var dismissed by mutableStateOf(false)

    val currentVersion: String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"

    /** Debug builds have another package id; a release APK wouldn't replace them. */
    val isDebug = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    suspend fun check() {
        if (state is State.Checking || state is State.Downloading) return
        state = State.Checking
        state = runCatching { withContext(Dispatchers.IO) { latest() } }
            .onFailure { Log.w(TAG, "check failed", it) }
            .getOrElse { State.Failed("Couldn't reach GitHub") }
    }

    private fun latest(): State {
        val conn = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        val json = try {
            if (conn.responseCode == 404) return State.UpToDate // no release published yet
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
        val o = JSONObject(json)
        val version = o.optString("tag_name").removePrefix("v")
        if (!isNewer(version, currentVersion)) return State.UpToDate
        val assets = o.optJSONArray("assets")
        for (i in 0 until (assets?.length() ?: 0)) {
            val a = assets!!.getJSONObject(i)
            if (a.optString("name").endsWith(".apk")) {
                return State.Available(version, a.optString("browser_download_url"), a.optLong("size"))
            }
        }
        return State.UpToDate // a release without an APK is nothing to install
    }

    /** Downloads the APK, then opens the system install prompt. */
    suspend fun install(update: State.Available) {
        // Android 8+: the user allows "Install unknown apps" for HyperFragz once.
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        state = State.Downloading(update.version, 0f)
        val file = runCatching { withContext(Dispatchers.IO) { download(update) } }
            .onFailure { Log.w(TAG, "download failed", it) }
            .getOrNull()
        if (file == null) {
            state = State.Failed("Download failed. Try again.")
            return
        }
        state = update // if the prompt is cancelled, the button is still there
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private suspend fun download(update: State.Available): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // older downloads
        val out = File(dir, "HyperFragz-${update.version}.apk")
        val conn = URL(update.apkUrl).openConnection() as HttpURLConnection // follows GitHub's redirect
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        try {
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: update.size
            conn.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var lastShown = 0f
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        val p = if (total > 0) done.toFloat() / total else 0f
                        if (p - lastShown >= 0.02f) {
                            lastShown = p
                            withContext(Dispatchers.Main) { state = State.Downloading(update.version, p) }
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        return out
    }

    companion object {
        /** "1.10.0" > "1.9.2"; missing parts count as 0. */
        fun isNewer(candidate: String, current: String): Boolean {
            val a = candidate.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
            val b = current.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}
