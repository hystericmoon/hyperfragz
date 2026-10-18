package com.hosni.hyperfragz.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hosni.hyperfragz.FacetItem
import com.hosni.hyperfragz.Panel
import com.hosni.hyperfragz.SiteController
import kotlinx.coroutines.delay

/** Site panel title → label in the sheet. */
private val OPEN = listOf("Gender" to "Gender", "Notes" to "Notes", "Groups" to "Scent family")
private val ROWS = listOf(
    "Designers" to "Designer",
    "Minimum votes" to "Minimum votes",
    "Rating" to "Rating",
    "Year" to "Year",
    "Perfumers" to "Perfumer",
)
private val MORE = listOf(
    "Countries" to "Countries",
    "Collections" to "Collections",
    "Parent company" to "Parent company",
    "Industries" to "Industries",
    "Specialty Designers" to "Specialty designers",
    "Popularity" to "Popularity",
)
private const val MORE_PAGE = "__more"

/** design/2 · Filter sheet. Rows with › drill into a sub-page within the sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefineSheet(site: SiteController, onDismiss: () -> Unit) {
    val state = site.state
    var page by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = page != null) { page = if (page != MORE_PAGE && MORE.any { it.first == page }) MORE_PAGE else null }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = V.SheetRadius,
        containerColor = V.Ground,
        contentColor = V.Text,
        scrimColor = V.Black.copy(alpha = 0.6f),
        // Scrolling the content must not drag the sheet: leftover scroll used to
        // tug it down and spring back (the "jiggle"). Close with ×, scrim or Back.
        sheetGesturesEnabled = false,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(V.Primary.copy(alpha = 0.45f))
            )
        },
    ) {
        // Fixed height from the screen, not a fraction of the sheet's own
        // constraints (which shift as the sheet moves and fed back into it).
        val height = LocalConfiguration.current.screenHeightDp.dp * 0.9f
        Column(Modifier.height(height)) {
            val current = page
            val title = when (current) {
                null -> "Refine"
                MORE_PAGE -> "More filters"
                else -> (ROWS + MORE).firstOrNull { it.first == current }?.second ?: current
            }
            SheetHeader(
                title = title,
                onBack = if (current != null) ({ page = if (current != MORE_PAGE && MORE.any { it.first == current }) MORE_PAGE else null }) else null,
                onClose = onDismiss,
            )
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                when (current) {
                    null -> MainPage(site, onOpen = { page = it })
                    MORE_PAGE -> MORE.forEach { (t, label) -> state.panel(t)?.let { p -> DrillRow(label, p.summary, set = p.summary != "Any") { page = t } } }
                    else -> state.panel(current)?.let { PanelPage(site, it, (ROWS + MORE).first { r -> r.first == current }.second) }
                }
                Spacer(Modifier.height(16.dp))
            }
            Footer(site, onDismiss)
        }
    }
}

@Composable
private fun SheetHeader(title: String, onBack: (() -> Unit)?, onClose: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (onBack == null) 20.dp else 4.dp, end = 4.dp, top = 2.dp, bottom = 4.dp)
    ) {
        if (onBack != null) IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Refine", tint = V.Text)
        }
        Text(title, style = serif(28), modifier = Modifier
            .weight(1f)
            .semantics { heading() })
        IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close filters", tint = V.Text) }
    }
}

@Composable
private fun MainPage(site: SiteController, onOpen: (String) -> Unit) {
    val state = site.state
    // Sort
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
    ) {
        Text("Sort", style = sans(16, V.Text, 500), modifier = Modifier.weight(1f))
        SortMenu(site, labelStyle = sans(15, V.Text2))
    }
    OPEN.forEach { (t, label) ->
        state.panel(t)?.let { p ->
            Hairline()
            Text(label.uppercase(), style = Overline, modifier = Modifier
                .padding(top = 8.dp, bottom = 20.dp)
                .semantics { heading() })
            if (p.searchable && t == "Notes") {
                FacetSearch(site, p, label)
                Spacer(Modifier.height(4.dp))
            }
            Chips(site, p)
            Spacer(Modifier.height(6.dp))
        }
    }
    Hairline()
    ROWS.forEach { (t, label) -> state.panel(t)?.let { p -> DrillRow(label, p.summary, set = p.summary != "Any") { onOpen(t) } } }
    val moreCount = state.activeFilters.count { f -> MORE.any { it.first.equals(f.panelTitle, true) } }
    DrillRow("More filters", if (moreCount == 0) "Any" else "$moreCount applied", set = moreCount > 0) { onOpen(MORE_PAGE) }
}

@Composable
private fun DrillRow(label: String, value: String, set: Boolean, onClick: () -> Unit) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clickable(onClick = onClick)
                .semantics(mergeDescendants = true) { role = Role.Button; contentDescription = "$label, $value" },
        ) {
            Text(label, style = sans(16, V.Text, 500), modifier = Modifier.weight(1f))
            Text(
                value.capitalised(),
                style = sans(15, if (set) V.Primary else V.Text2),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp).weight(1f, fill = false),
            )
            Spacer(Modifier.width(6.dp))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = V.Text2, modifier = Modifier.size(16.dp))
        }
        Hairline()
    }
}

@Composable
private fun PanelPage(site: SiteController, p: Panel, label: String) {
    Spacer(Modifier.height(8.dp))
    when (p.type) {
        "range" -> RangeEditor(site, p)
        else -> {
            if (p.searchable) {
                FacetSearch(site, p, label)
                Spacer(Modifier.height(8.dp))
            }
            Chips(site, p)
            if (p.canShowMore) TextLink(if (p.showingMore) "Show fewer" else "Show more", onClick = { site.showMore(p, !p.showingMore) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(site: SiteController, p: Panel) {
    if (p.items.isEmpty()) {
        Text("No matches", style = sans(14, V.Text3), modifier = Modifier.padding(vertical = 12.dp))
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        p.items.forEach { item -> SelectChip(chipText(p, item), item.checked, onClick = { site.toggle(p, item) }, description = chipDescription(p, item)) }
    }
}

private fun chipText(p: Panel, item: FacetItem) = when (p.type) {
    "rating" -> item.label.replace(" & up", "★ & up")
    else -> item.label.capitalised()
}

private fun chipDescription(p: Panel, item: FacetItem) =
    chipText(p, item).replace("★", " stars") + (item.count?.let { ", ${fmt(it)} perfumes" } ?: "")

@Composable
private fun FacetSearch(site: SiteController, p: Panel, label: String) {
    var q by rememberSaveable(p.title) { mutableStateOf(p.query) }
    var lastPageQuery by remember(p.title) { mutableStateOf(p.query) }
    LaunchedEffect(p.query) {
        if (p.query.isEmpty() && lastPageQuery.isNotEmpty()) q = ""
        lastPageQuery = p.query
    }
    LaunchedEffect(q) {
        if (q == p.query) return@LaunchedEffect
        delay(250)
        site.facetSearch(p, q)
    }
    Field(q, { q = it }, "Search ${label.lowercase()}", leadingSearch = true)
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    leadingSearch: Boolean = false,
    keyboard: KeyboardOptions = KeyboardOptions.Default,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(46.dp)
            .clip(V.Radius)
            .background(V.Surface)
            .border(1.dp, V.Hairline, V.Radius)
            .padding(horizontal = 14.dp),
    ) {
        if (leadingSearch) {
            Icon(Icons.Default.Search, null, tint = V.Text2, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(10.dp))
        }
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = sans(16, V.Text3))
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = sans(16),
                cursorBrush = SolidColor(V.Primary),
                keyboardOptions = keyboard,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = placeholder },
            )
        }
    }
}

@Composable
private fun RangeEditor(site: SiteController, p: Panel) {
    val r = p.range
    var from by rememberSaveable(r?.from) { mutableStateOf(r?.from?.toString() ?: "") }
    var to by rememberSaveable(r?.to) { mutableStateOf(r?.to?.toString() ?: "") }
    val focus = LocalFocusManager.current
    val num = KeyboardOptions(keyboardType = KeyboardType.Number)
    Text("FROM – TO", style = Overline, modifier = Modifier.padding(bottom = 8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Field(from, { from = it.filter(Char::isDigit).take(4) }, r?.min?.toString() ?: "From", keyboard = num, modifier = Modifier.weight(1f))
        Field(to, { to = it.filter(Char::isDigit).take(4) }, r?.max?.toString() ?: "To", keyboard = num, modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(12.dp))
    OutlineButton("Apply", onClick = {
        focus.clearFocus()
        site.setRange(p, from.toIntOrNull(), to.toIntOrNull())
    }, modifier = Modifier.fillMaxWidth(), height = 48.dp)
}

@Composable
private fun Footer(site: SiteController, onDismiss: () -> Unit) {
    val state = site.state
    Hairline()
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        OutlineButton("Clear all", onClick = { site.clearAll() })
        PrimaryButton(
            text = when (val t = state.total) {
                null -> "Show results"
                1 -> "Show 1 result"
                else -> "Show ${fmt(t)} results"
            },
            onClick = onDismiss,
            modifier = Modifier
                .weight(1f)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}
