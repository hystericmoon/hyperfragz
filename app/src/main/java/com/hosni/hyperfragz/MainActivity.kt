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

    /** Debug builds only: `am start … --es open <perfume url>` opens that page (for testing). */
    private fun openFromIntent(intent: Intent?) {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        // Test hook: simulate Android killing the hidden page's process.
        if (intent?.getBooleanExtra("crashPage", false) == true) { site.webView.loadUrl("chrome://crash"); return }
        val url = intent?.getStringExtra("open") ?: return
        if (url.startsWith("https://www.fragrantica.com/perfume/")) {
            site.openDetail(Detail.preview(Hit(url, "", "", null, null, null)))
        }
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
