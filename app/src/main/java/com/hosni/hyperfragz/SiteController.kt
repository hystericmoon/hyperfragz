package com.hosni.hyperfragz

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject
import java.io.ByteArrayInputStream

const val HOME_URL = "https://www.fragrantica.com/search/"
private const val ORIGIN = "https://www.fragrantica.com"
private const val TAG = "Vitrine"

/**
 * Owns the hidden WebView (the data source) and talks to assets/bridge.js and
 * assets/extract.js. The UI is native; the page is only shown in the
 * "Open on Fragrantica" fallback ([showWeb]).
 */
class SiteController(private val context: Context) {

    /** Last state of the search page (kept while a perfume page is loaded). */
    var state by mutableStateOf(PageState())
        private set
    /** Kind of page currently loaded in the WebView: search | perfume | other. */
    var page by mutableStateOf("loading")
        private set
    /** Detail screens on top of the index; empty = index. */
    val stack = mutableStateListOf<Detail>()
    var showWeb by mutableStateOf(false)
        private set
    /** One-shot message for a snackbar (e.g. voting needs a login). */
    var message by mutableStateOf<String?>(null)
    var canGoBack by mutableStateOf(false)
        private set
    var loading by mutableStateOf(true)
        private set

    val favorites = Favorites(context)

    /** assets/adhosts.txt; must be loaded before the WebView starts requesting. */
    private val adEntries = asset("adhosts.txt").lineSequence()
        .map { it.substringBefore('#').trim().lowercase() }
        .filter { it.isNotEmpty() }
        .toList()
    private val adHosts: Set<String> = adEntries.filterNot { '/' in it }.toSet()
    private val adPrefixes: List<String> = adEntries.filter { '/' in it && !it.startsWith("*/") }
    private val adPaths: List<String> = adEntries.filter { it.startsWith("*/") }.map { it.substring(1) }

    private fun isAd(host: String, path: String): Boolean {
        if (isAdHost(host)) return true
        val h = host.lowercase()
        val p = path.lowercase()
        if (adPrefixes.any { (h + p).startsWith(it) }) return true
        val site = h == "fragrantica.com" || h.endsWith(".fragrantica.com")
        return !site && adPaths.any { p.startsWith(it) }
    }

    /**
     * Replaced if Android kills its page process (memory pressure on long
     * sessions); the UI keys its AndroidView on this, so it swaps in the new one.
     */
    var webView: WebView by mutableStateOf(createWebView(HOME_URL))
        private set

    /** Where the hidden page should be for what's on screen. */
    private fun expectedUrl(): String =
        stack.lastOrNull()?.url ?: state.url.takeIf { it.startsWith("https://www.fragrantica.com/search") } ?: HOME_URL

    private fun recreateWebView() {
        val old = webView
        webView = createWebView(expectedUrl())
        old.post { old.destroy() }
    }

    private fun isAdHost(host: String): Boolean {
        var h = host.lowercase()
        while (true) {
            if (h in adHosts) return true
            val dot = h.indexOf('.')
            if (dot < 0) return false
            h = h.substring(dot + 1)
        }
    }

    // ------------------------------------------------------------ native → page

    fun setQuery(q: String) = call("setQuery", q)
    fun setSort(value: String) {
        state = state.copy(sortSelected = value)
        call("setSort", value)
    }

    fun toggle(panel: Panel, item: FacetItem, on: Boolean = !item.checked) {
        // Optimistic: flip locally so the chip responds at once; bridge confirms.
        state = state.copy(panels = state.panels.map { p ->
            if (p.title != panel.title) p
            else p.copy(items = p.items.mapIndexed { i, it ->
                when {
                    it.value == item.value -> it.copy(checked = on)
                    p.type == "radio" && on -> it.copy(checked = false)
                    p.type == "radio" && !on && i == 0 -> it.copy(checked = true)
                    p.type == "rating" && on -> it.copy(checked = false)
                    else -> it
                }
            })
        })
        if (!on) state = state.copy(refinements = state.refinements?.filterNot { it.panel == panel.title && it.value == item.value })
        call("applyFacet", panel.title, item.value, on)
    }

    fun setRange(panel: Panel, from: Int?, to: Int?) = call("setRange", panel.title, from, to)
    fun facetSearch(panel: Panel, q: String) = call("facetSearch", panel.title, q)
    fun showMore(panel: Panel, more: Boolean = true) = call("showMore", panel.title, more)
    fun clearAll() = call("clearAll")

    fun remove(filter: ActiveFilter) {
        val ref = filter.ref
        when {
            ref != null -> {
                state = state.copy(refinements = state.refinements?.minus(ref))
                call("removeRefinement", ref.kind, ref.attribute, ref.value)
            }
            filter.panel == null -> Unit
            filter.item == null -> setRange(filter.panel, null, null)
            else -> toggle(filter.panel, filter.item, on = false)
        }
    }

    fun loadMore() = call("loadMore")

    // ------------------------------------------------------------ navigation

    fun openDetail(preview: Detail) {
        if (stack.isEmpty()) call("saveScroll") // so Back can restore the loaded results
        stack.add(preview)
        openedAt = android.os.SystemClock.elapsedRealtime()
        markNav()
        webView.loadUrl(preview.url)
    }

    /**
     * A fragrantica.com link from outside the app: perfume pages open on top of
     * whatever is showing, search links replace the index results.
     */
    fun openLink(link: String) {
        val uri = android.net.Uri.parse(link.trim())
        val path = uri.path.orEmpty()
        val url = uri.buildUpon().scheme("https").authority("www.fragrantica.com").fragment(null).build().toString()
        if (showWeb) closeWeb()
        when {
            path.startsWith("/perfume/") ->
                if (stack.lastOrNull()?.let { samePage(it.url, url) } != true) openDetail(Detail.preview(Hit(url, "", "", null, null, null)))
            path.startsWith("/search") -> {
                stack.clear()
                markNav()
                webView.loadUrl(url)
            }
            else -> message = "HyperFragz opens Fragrantica perfume and search links."
        }
    }

    /** Handles Back; false when there's nothing left to go back to (exit). */
    fun back(): Boolean {
        if (showWeb) { closeWeb(); return true }
        if (stack.isEmpty()) return false
        stack.removeAt(stack.lastIndex)
        markNav()
        when {
            hasSiteHistory(webView) -> webView.goBack()
            stack.isEmpty() -> webView.loadUrl(HOME_URL)
            else -> webView.loadUrl(stack.last().url)
        }
        return true
    }

    fun openWeb() {
        webView.settings.blockNetworkImage = false
        showWeb = true
        setHidden(false)
    }

    /** html.hf-hidden (skin.css) turns the page's animations off while nobody sees it. */
    private fun setHidden(hidden: Boolean) =
        webView.evaluateJavascript("document.documentElement.classList.toggle('hf-hidden',$hidden)", null)

    /** App in the background: stop the hidden page's timers and rendering. */
    fun pause() {
        webView.onPause()
        webView.pauseTimers()
    }

    fun resume() {
        webView.resumeTimers()
        webView.onResume()
    }

    /** Leaving the fallback: make the native stack match wherever the user ended up. */
    fun closeWeb() {
        showWeb = false
        webView.settings.blockNetworkImage = true
        setHidden(true)
        val url = webView.url ?: return
        when {
            "/perfume/" in url && stack.lastOrNull()?.let { samePage(it.url, url) } != true ->
                stack.add(Detail.preview(Hit(url, "", "", null, null, null)))
            "/search" in url -> stack.clear()
        }
    }

    fun sortReviews(kind: String) =
        webView.evaluateJavascript("window.HyperExtract&&HyperExtract.sortReviews(${JSONObject.quote(kind)})", null)

    fun helpful(reviewId: String) =
        webView.evaluateJavascript("window.HyperExtract&&HyperExtract.helpful(${JSONObject.quote(reviewId)})", null)

    private fun samePage(a: String, b: String) =
        runCatching { android.net.Uri.parse(a).path == android.net.Uri.parse(b).path }.getOrDefault(false)

    /**
     * The hidden page must match the screen: search page under the index, the
     * top perfume under a detail screen. History quirks or site redirects can
     * leave it elsewhere, which made search/filters silently do nothing.
     */
    private var lastNavAt = 0L
    private var lastReported: PageState? = null
    private val recheck = Runnable { lastReported?.let(::resyncIfDrifted) }
    private fun markNav() {
        lastNavAt = android.os.SystemClock.elapsedRealtime()
        // Pages may report only during the grace period; check again after it.
        webView.removeCallbacks(recheck)
        webView.postDelayed(recheck, 4500)
    }

    private fun resyncIfDrifted(p: PageState) {
        if (showWeb || p.page == "loading") return
        // Right after a navigation the old page can still report; not a drift.
        if (android.os.SystemClock.elapsedRealtime() - lastNavAt < 4000) return
        val top = stack.lastOrNull()
        val drifted = when {
            top == null -> p.page != "search"
            else -> p.page == "search"
        }
        if (drifted && !samePage(p.url, expectedUrl())) {
            markNav()
            webView.loadUrl(expectedUrl())
        }
    }

    private var openedAt = 0L
    private val debuggable = context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0

    private fun applyDetail(d: Detail) {
        if (debuggable) Log.d(TAG, "detail +${android.os.SystemClock.elapsedRealtime() - openedAt}ms complete=${d.complete} " +
            listOfNotNull(d.rating?.let { "rating" + if (it.breakdown != null) "+" else "" }, d.accords?.let { "accords" }, d.notes?.let { "notes" },
                d.longevity?.let { "skin" }, d.pros?.let { "say" }, d.wear?.let { "wear" }, d.similar?.let { "similar" },
                d.alsoLike?.let { "also" }, d.reviews?.takeIf { it.items.isNotEmpty() }?.let { "reviews" }).joinToString(","))
        val i = stack.indexOfLast { samePage(it.url, d.url) }
        if (i < 0) return
        val old = stack[i]
        if (old.complete && !d.complete) return // a late partial must not undo a full read
        // Sections stream in, and the site briefly swaps some out while it
        // re-renders them: keep whatever we already had for anything missing now.
        stack[i] = d.mergedOver(old)
        favorites.refresh(stack[i])
    }

    /** True when there is an earlier *site* page to return to (skips about:blank). */
    private fun hasSiteHistory(view: WebView): Boolean {
        val list = view.copyBackForwardList()
        val i = list.currentIndex
        return i > 0 && list.getItemAtIndex(i - 1)?.url?.startsWith("http") == true
    }

    private fun call(fn: String, vararg args: Any?) {
        val js = args.joinToString(",") { a ->
            when (a) {
                null -> "null"
                is String -> JSONObject.quote(a)
                else -> a.toString()
            }
        }
        webView.evaluateJavascript("window.VitrineBridge&&VitrineBridge.$fn($js)", null)
    }

    // ------------------------------------------------------------ page → native

    private inner class NativeBridge {
        @JavascriptInterface
        fun onState(json: String) {
            val parsed = runCatching { PageState.parse(json) }
                .onFailure { Log.w(TAG, "bad state", it) }
                .getOrNull() ?: return
            webView.post {
                lastReported = parsed
                resyncIfDrifted(parsed)
                page = parsed.page
                // While the search page reloads (e.g. Back from a perfume) it first
                // reports an empty state; keep the results already on screen.
                val reloading = parsed.total == null && parsed.hitList.isEmpty() && state.hitList.isNotEmpty()
                if (parsed.isSearch && !reloading) state = parsed
            }
        }

        @JavascriptInterface
        fun onDetail(json: String) {
            val o = runCatching { JSONObject(json) }.getOrNull() ?: return
            val data = o.optJSONObject("data") ?: return
            when (o.optString("kind")) {
                "detail", "detail-partial" -> {
                    val d = runCatching { Detail.parse(data, complete = o.optString("kind") == "detail") }
                        .onFailure { Log.w(TAG, "bad detail", it) }
                        .getOrNull() ?: return
                    webView.post { applyDetail(d) }
                }
                "helpful" -> if (data.optBoolean("login")) webView.post {
                    message = "Voting needs a Fragrantica account. Log in via Open on Fragrantica."
                }
            }
        }
    }

    // ------------------------------------------------------------ setup

    private fun asset(name: String) = context.assets.open(name).bufferedReader().use { it.readText() }

    /** CSS goes in first, as a <style> on <html>, then moved to <head> once it exists. */
    private fun documentStartScript(): String {
        val css = JSONObject.quote(asset("skin.css"))
        return """
            (function(){
              document.documentElement.classList.add('hf-hidden');
              var s=document.createElement('style');s.id='vitrine-skin';s.textContent=$css;
              document.documentElement.appendChild(s);
              document.addEventListener('DOMContentLoaded',function(){if(document.head)document.head.appendChild(s);});
            })();
        """.trimIndent() + "\n" + asset("extract.js") + "\n" + asset("bridge.js")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(url: String): WebView = WebView(context).apply {
        setBackgroundColor(Color.parseColor("#0E1311"))
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = true
        // Hidden data source: the native UI loads the images it shows.
        settings.blockNetworkImage = true
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            androidx.webkit.WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, false)
        }

        addJavascriptInterface(NativeBridge(), "VitrineNative")

        val startScript = documentStartScript()
        val hasDocStart = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        if (hasDocStart) {
            WebViewCompat.addDocumentStartJavaScript(this, startScript, setOf(ORIGIN))
        }

        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                val host = uri.host ?: return false
                if (host == "fragrantica.com" || host.endsWith(".fragrantica.com")) return false
                if (!request.isForMainFrame) return false
                // Off-site links the user taps open in the real browser; scripted
                // off-site redirects (no gesture) are simply not followed.
                if (!request.hasGesture()) return true
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                return true
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val host = request.url.host ?: return null
                if (isAd(host, request.url.path.orEmpty())) {
                    return WebResourceResponse("text/plain", "utf-8", 204, "No Content", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                }
                // Bundled fonts for skin.css, served same-origin so @font-face needs no CORS.
                val path = request.url.path ?: return null
                if (request.url.host != "www.fragrantica.com" || !path.startsWith("/__vitrine/fonts/")) return null
                val res = when (path.substringAfterLast('/')) {
                    "instrument_serif_italic.ttf" -> R.font.instrument_serif_italic
                    "geist.ttf" -> R.font.geist
                    else -> return null
                }
                return WebResourceResponse("font/ttf", null, context.resources.openRawResource(res)).apply {
                    responseHeaders = mapOf("Cache-Control" to "max-age=31536000")
                }
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                loading = true
                markNav()
                if (!hasDocStart) view.evaluateJavascript(startScript, null)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                loading = false
                if (showWeb) setHidden(false) // a page opened inside Open on Fragrantica
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                canGoBack = hasSiteHistory(view)
            }

            // Android killed (or crashed) the page process: without this the
            // app would keep a dead WebView and every action would do nothing.
            override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
                Log.w(TAG, "page process gone (crashed=${detail.didCrash()}); recreating")
                if (view === webView) view.post { recreateWebView() }
                return true
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                if (m.message().contains("Vitrine", ignoreCase = true) || m.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                    Log.d(TAG, "${m.messageLevel()} ${m.message()} @${m.sourceId()}:${m.lineNumber()}")
                }
                return true
            }
        }

        // DevTools over adb only in debug builds.
        val debuggable = context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        WebView.setWebContentsDebuggingEnabled(debuggable)
        // Keep the page process alive like a visible one while the app is in use;
        // in the background Android may reclaim it (onRenderProcessGone recreates it).
        setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true)
        loadUrl(url)
    }

    fun openHome() = webView.loadUrl(HOME_URL)

    /** Android 12+: where the user lets fragrantica.com links open in HyperFragz. */
    val canSetLinks = android.os.Build.VERSION.SDK_INT >= 31

    fun openLinkSettings() {
        if (!canSetLinks) return
        runCatching {
            context.startActivity(
                Intent(android.provider.Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** A pre-filled GitHub issue: app and Android version, the page, and which sections came up empty. */
    fun reportProblem() {
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        val top = stack.lastOrNull()
        val pageUrl = top?.url ?: state.url
        val missing = top?.takeIf { it.complete }?.missingSections().orEmpty()
        val title = if (top != null && top.name.isNotEmpty()) "Problem on ${top.name}" else "Problem in HyperFragz"
        val body = buildString {
            appendLine("**What went wrong?**")
            appendLine()
            appendLine()
            appendLine("---")
            appendLine("- App: HyperFragz $version")
            appendLine("- Android: ${android.os.Build.VERSION.RELEASE} (${android.os.Build.MODEL})")
            appendLine("- Page: $pageUrl")
            if (missing.isNotEmpty()) appendLine("- Empty sections: ${missing.joinToString()}")
        }
        val uri = android.net.Uri.parse("https://github.com/hystericmoon/hyperfragz/issues/new").buildUpon()
            .appendQueryParameter("title", title)
            .appendQueryParameter("body", body)
            .build()
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
