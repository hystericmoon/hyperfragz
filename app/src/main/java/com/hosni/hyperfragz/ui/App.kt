package com.hosni.hyperfragz.ui

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.hosni.hyperfragz.SiteController

/**
 * The WebView is always composed, full size, underneath: it's the data source
 * and must keep a real viewport for the site's lazy loading. Native screens sit
 * on top, opaque. In fallback mode the WebView is shown with a slim native bar.
 */
@Composable
fun App(site: SiteController) {
    var refine by rememberSaveable { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }

    BackHandler(enabled = site.showWeb || site.stack.isNotEmpty()) { site.back() }
    LaunchedEffect(site.message) {
        val m = site.message ?: return@LaunchedEffect
        snack.showSnackbar(m)
        site.message = null
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(V.Ground)
    ) {
        Column(Modifier.fillMaxSize()) {
            if (site.showWeb) WebBar(site)
            // Keyed on the instance: a recreated WebView (after its page process
            // died) replaces the old view.
            key(site.webView) {
                AndroidView(
                    factory = { site.webView.also { (it.parent as? ViewGroup)?.removeView(it) } },
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (site.showWeb) Modifier.navigationBarsPadding() else Modifier),
                )
            }
        }

        if (!site.showWeb) {
            val top = site.stack.lastOrNull()
            if (top == null) IndexScreen(site, onOpenFilters = { refine = true })
            else DetailScreen(site, top)
            if (refine && top == null) RefineSheet(site, onDismiss = { refine = false })
        }

        SnackbarHost(
            snack,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp),
        ) { Snackbar(it, containerColor = V.Surface, contentColor = V.Text) }
    }
}

@Composable
private fun WebBar(site: SiteController) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(V.Ground)
            .statusBarsPadding()
            .heightIn(min = 56.dp)
            .padding(start = 20.dp, end = 4.dp)
    ) {
        Text("fragrantica.com", style = sans(15, V.Text2), modifier = Modifier.weight(1f))
        IconButton(onClick = { site.closeWeb() }) {
            Icon(Icons.Default.Close, contentDescription = "Back to HyperFragz", tint = V.Text)
        }
    }
}
