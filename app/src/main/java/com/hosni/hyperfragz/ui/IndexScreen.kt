package com.hosni.hyperfragz.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hosni.hyperfragz.ActiveFilter
import com.hosni.hyperfragz.Detail
import com.hosni.hyperfragz.Hit
import com.hosni.hyperfragz.SiteController
import kotlinx.coroutines.delay

/** design/1 · Results and design/3 · No results. */
@Composable
fun IndexScreen(site: SiteController, onOpenFilters: () -> Unit, onOpenSaved: () -> Unit) {
    val state = site.state
    val active = state.activeFilters
    val noResults = state.total == 0 && (active.isNotEmpty() || state.query.isNotEmpty())

    Box(
        Modifier
            .fillMaxSize()
            .background(V.Ground)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
        ) {
            IndexHeader(site, onOpenSaved)
            UpdateBanner()
            if (active.isNotEmpty()) AppliedChips(site, active)
            if (!noResults) ResultsRow(site)
            Hairline(Modifier.padding(top = if (noResults) 17.dp else 0.dp))
            if (noResults) NoResults(site, active) else ResultsGrid(site)
        }

        if (!noResults) {
            val n = active.size
            PrimaryButton(
                text = if (n == 0) "Filter & sort" else "Filter & sort · $n",
                onClick = onOpenFilters,
                leading = {
                    TuneIcon()
                    Spacer(Modifier.width(10.dp))
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
                    .semantics { contentDescription = if (n == 0) "Filter and sort" else "Filter and sort, $n applied" },
            )
        }
    }
}

@Composable
private fun IndexHeader(site: SiteController, onOpenSaved: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 14.dp)
    ) {
        Text("The Index", style = serif(34), modifier = Modifier
            .weight(1f)
            .semantics { heading() })
        SavedButton(site.favorites.items.size, onOpenSaved)
        // "FRAGRANTICA" doubles as the overflow menu (Open on Fragrantica).
        var menu by remember { mutableStateOf(false) }
        Box {
            Text(
                "FRAGRANTICA",
                style = sans(12, V.Text2, 500).copy(letterSpacing = 1.2.sp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable(onClickLabel = "More options") { menu = true }
                    .semantics { role = Role.Button }
                    .padding(horizontal = 12.dp, vertical = 16.dp),
            )
            SiteMenu(menu, onDismiss = { menu = false }, site)
        }
    }
    SearchField(site)
}

@Composable
fun SiteMenu(expanded: Boolean, onDismiss: () -> Unit, site: SiteController) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, containerColor = V.Surface) {
        DropdownMenuItem(
            text = { Text("Open on Fragrantica", style = sans(15)) },
            onClick = { onDismiss(); site.openWeb() },
            modifier = Modifier.heightIn(min = 48.dp),
        )
        UpdateMenuItem(onDismiss) { site.message = it }
        if (site.canSetLinks && site.stack.isEmpty()) DropdownMenuItem(
            text = { Text("Open Fragrantica links here", style = sans(15)) },
            onClick = { onDismiss(); site.openLinkSettings() },
            modifier = Modifier.heightIn(min = 48.dp),
        )
        DropdownMenuItem(
            text = { Text("Report a problem", style = sans(15)) },
            onClick = { onDismiss(); site.reportProblem() },
            modifier = Modifier.heightIn(min = 48.dp),
        )
    }
}

@Composable
private fun SearchField(site: SiteController) {
    val pageQuery = site.state.query
    var text by rememberSaveable { mutableStateOf(pageQuery) }
    var focused by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    LaunchedEffect(pageQuery) { if (!focused && pageQuery != text) text = pageQuery }
    LaunchedEffect(text) {
        if (text == site.state.query) return@LaunchedEffect
        delay(300)
        site.setQuery(text)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .padding(top = 11.dp)
            .fillMaxWidth()
            .height(50.dp)
            .clip(V.Radius)
            .background(V.Surface)
            .border(1.dp, V.Hairline, V.Radius)
            .padding(start = 16.dp, end = 4.dp),
    ) {
        Icon(Icons.Default.Search, null, tint = V.Text2, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (text.isEmpty()) Text("Perfume, brand or note", style = sans(17, V.Text3))
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = sans(17),
                cursorBrush = SolidColor(V.Primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused }
                    .semantics { contentDescription = "Search perfumes" },
            )
        }
        if (text.isNotEmpty()) {
            IconButton(onClick = { text = "" }) {
                Icon(Icons.Default.Close, contentDescription = "Clear search", tint = V.Text2, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun AppliedChips(site: SiteController, active: List<ActiveFilter>) {
    val row = androidx.compose.foundation.lazy.rememberLazyListState()
    // Chips arrive one by one from the page; always show the row from its start.
    LaunchedEffect(active.map { it.label }) { row.scrollToItem(0) }
    LazyRow(
        state = row,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
        modifier = Modifier.padding(top = 2.dp),
    ) {
        items(active, key = { it.panelTitle + "|" + it.label }) { f ->
            AppliedChip(chipLabel(f), onRemove = { site.remove(f) })
        }
    }
}

/** "Gender: female" → "Female"; ranges/menus keep their own wording. */
fun chipLabel(f: ActiveFilter): String {
    val l = f.label
    return when {
        f.ref?.kind == "refinementList" || f.item != null && f.panel?.type == "list" -> l.capitalised()
        f.ref?.kind == "numericMenu" && f.panelTitle.equals("Minimum votes", true) -> l.substringAfter(": ") + " votes"
        else -> l.substringAfter(": ").capitalised()
    }
}

@Composable
private fun ResultsRow(site: SiteController) {
    val state = site.state
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 6.dp)
    ) {
        Text(
            when (val t = state.total) {
                null -> "Searching…"
                1 -> "1 result"
                else -> "${fmt(t)} results"
            },
            style = sans(13, V.Text2),
            modifier = Modifier
                .weight(1f)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        SortMenu(site)
    }
}

@Composable
fun SortMenu(site: SiteController, labelStyle: androidx.compose.ui.text.TextStyle = sans(14, V.Text, 500)) {
    val state = site.state
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clickable(onClickLabel = "Change sort order") { open = true }
                .semantics { role = Role.Button; contentDescription = "Sort: ${state.sortLabel ?: "default"}" }
                .padding(horizontal = 12.dp),
        ) {
            Text(state.sortLabel?.let(::shortSort) ?: "Sort", style = labelStyle)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.KeyboardArrowDown, null, tint = labelStyle.color, modifier = Modifier.size(14.dp))
        }
        DropdownMenu(open, onDismissRequest = { open = false }, containerColor = V.Surface) {
            state.sortOptions.forEach { o ->
                val sel = o.value == state.sortSelected
                DropdownMenuItem(
                    text = { Text(shortSort(o.label), style = sans(15, if (sel) V.Primary else V.Text, if (sel) 500 else 400)) },
                    onClick = { open = false; site.setSort(o.value) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics { if (sel) contentDescription = "${shortSort(o.label)}, selected" },
                )
            }
        }
    }
}

/** "The longest lasting (longevity)" → "Longest lasting". */
fun shortSort(label: String) = label.substringBefore(" (").removePrefix("The ").capitalised()

@Composable
private fun ResultsGrid(site: SiteController) {
    val state = site.state
    val hits = state.hitList
    val grid = rememberLazyGridState()
    // A new result set (different first card) starts at the top; loading more,
    // or coming back from a perfume, keeps the position.
    var firstUrl by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(hits.firstOrNull()?.url) {
        val f = hits.firstOrNull()?.url ?: return@LaunchedEffect
        if (firstUrl != null && firstUrl != f) grid.scrollToItem(0)
        firstUrl = f
    }

    // Infinite list: ask the hidden page for its next 30 near the end. The
    // effect restarts when the list changes; a request the page couldn't act
    // on (e.g. mid-search) is retried a few times.
    val nearEnd by remember {
        derivedStateOf {
            val last = grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= grid.layoutInfo.totalItemsCount - 6
        }
    }
    LaunchedEffect(nearEnd, hits.firstOrNull()?.url, hits.size) {
        val total = state.total ?: return@LaunchedEffect
        if (!nearEnd || hits.isEmpty() || hits.size >= total) return@LaunchedEffect
        repeat(4) {
            site.loadMore()
            delay(3000)
        }
    }

    if (hits.isEmpty()) {
        LoadingGrid()
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = grid,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(23.dp),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 17.dp, bottom = 120.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(hits, key = { _, h -> h.url }) { _, h -> HitCard(h) { site.openDetail(Detail.preview(h)) } }
        if (state.total != null && hits.size < state.total) {
            item(span = { GridItemSpan(2) }) {
                Text("Loading more…", style = sans(13, V.Text3), modifier = Modifier.padding(vertical = 12.dp))
            }
        }
    }
}

@Composable
fun HitCard(h: Hit, onClick: () -> Unit) {
    Column(
        Modifier
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = listOf(h.name, h.brand, h.meta).filter { it.isNotEmpty() }.joinToString(", ") }
    ) {
        PerfumeImage(
            h.thumb,
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp)),
            padding = 14.dp,
        )
        Text(h.name, style = serif(22, line = 1.05f), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
        Text(h.brand, style = sans(14, V.Text2), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
        if (h.meta.isNotEmpty()) Text(h.meta, style = sans(13, V.Text3), modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun LoadingGrid() {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 17.dp)) {
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 23.dp)) {
                repeat(2) {
                    Column(Modifier.weight(1f)) {
                        PerfumeImage(null, Modifier.fillMaxWidth().aspectRatio(1f))
                        Box(Modifier.padding(top = 12.dp).height(18.dp).fillMaxWidth(0.7f).background(V.Track))
                        Box(Modifier.padding(top = 8.dp).height(12.dp).fillMaxWidth(0.5f).background(V.Track))
                    }
                }
            }
        }
    }
}

/** design/3 · No results. */
@Composable
private fun NoResults(site: SiteController, active: List<ActiveFilter>) {
    val state = site.state
    val count = active.size + if (state.query.isNotEmpty()) 1 else 0
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp)
    ) {
        Spacer(Modifier.height(66.dp))
        Text(
            when (count) {
                1 -> "Nothing matches that."
                2 -> "Nothing matches both."
                else -> "Nothing matches all ${numberWord(count)}."
            },
            style = serif(34, line = 1.1f),
            modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite },
        )
        Text("Loosen one filter to see what’s close.", style = sans(15, V.Text2), modifier = Modifier.padding(top = 10.dp))
        Spacer(Modifier.height(30.dp))
        Hairline()
        if (state.query.isNotEmpty()) RemoveRow("“${state.query}”", null) { site.setQuery("") }
        active.forEach { f -> RemoveRow(chipLabel(f), f.ref?.without) { site.remove(f) } }
        Spacer(Modifier.height(28.dp))
        OutlineButton("Clear all filters", onClick = { site.clearAll() }, height = 48.dp)
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun RemoveRow(label: String, without: Int?, onRemove: () -> Unit) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(onClick = onRemove)
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = "Remove $label" + (without?.let { ", ${fmt(it)} results" } ?: "")
                },
        ) {
            Text(
                buildAnnotatedString {
                    append("Remove ")
                    withStyle(SpanStyle(fontFamily = InstrumentSerif, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, fontSize = 19.sp)) { append(label) }
                },
                style = sans(15),
                modifier = Modifier.weight(1f),
            )
            if (without != null) Text("${fmt(without)} results", style = sans(13, V.Primary))
        }
        Hairline()
    }
}

/** Sliders icon from the design's Filter & sort button. */
@Composable
private fun TuneIcon() {
    androidx.compose.foundation.Canvas(Modifier.size(18.dp)) {
        val c = V.OnPrimary
        val sw = 1.6.dp.toPx()
        val ys = listOf(0.25f, 0.5f, 0.75f)
        val knobs = listOf(0.65f, 0.3f, 0.55f)
        ys.forEachIndexed { i, y ->
            drawLine(c, androidx.compose.ui.geometry.Offset(0f, size.height * y), androidx.compose.ui.geometry.Offset(size.width, size.height * y), sw)
            drawCircle(V.Primary, radius = 3.dp.toPx(), center = androidx.compose.ui.geometry.Offset(size.width * knobs[i], size.height * y))
            drawCircle(c, radius = 3.dp.toPx(), center = androidx.compose.ui.geometry.Offset(size.width * knobs[i], size.height * y), style = androidx.compose.ui.graphics.drawscope.Stroke(sw))
        }
    }
}

