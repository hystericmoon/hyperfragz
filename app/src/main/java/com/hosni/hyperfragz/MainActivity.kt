package com.hosni.hyperfragz

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.hosni.hyperfragz.ui.App
import com.hosni.hyperfragz.ui.LocalUpdater
import com.hosni.hyperfragz.ui.VitrineTheme

private val LINK = Regex("""https?://(www\.)?fragrantica\.com/\S+""", RegexOption.IGNORE_CASE)

class MainActivity : ComponentActivity() {

    private lateinit var site: SiteController
    private lateinit var updater: Updater

    override fun onCreate(savedInstanceState: Bundle?) {
        val ground = Color.parseColor("#0E1311")
        // Transparent status bar: the detail hero runs underneath it, as in design/.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(ground),
        )
        super.onCreate(savedInstanceState)
        site = SiteController(this)
        updater = Updater(this)
        // Release builds look for a new GitHub release on each launch.
        if (savedInstanceState == null && !updater.isDebug) updater.checkAsync()
        setContent { VitrineTheme { CompositionLocalProvider(LocalUpdater provides updater) { App(site) } } }
        openFromIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openFromIntent(intent)
    }

    /** Fragrantica links (tapped or shared) open here; debug builds also take test extras. */
    private fun openFromIntent(intent: Intent?) {
        intent ?: return
        val link = when (intent.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let { LINK.find(it)?.value }
                ?: run { site.message = "That isn't a Fragrantica link."; null }
            else -> null
        }
        if (link != null) {
            site.openLink(link)
            return
        }
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        // Test hooks: `am start … --es open <perfume url>`; simulate Android killing the page process.
        if (intent.getBooleanExtra("crashPage", false)) { site.webView.loadUrl("chrome://crash"); return }
        intent.getStringExtra("open")?.let(site::openLink)
    }

    override fun onResume() {
        super.onResume()
        site.resume()
    }

    override fun onPause() {
        site.pause()
        super.onPause()
    }

    override fun onDestroy() {
        site.webView.destroy()
        super.onDestroy()
    }
}
