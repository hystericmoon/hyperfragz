package com.hosni.hyperfragz.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.hosni.hyperfragz.Count
import com.hosni.hyperfragz.Detail
import com.hosni.hyperfragz.Opinion
import com.hosni.hyperfragz.Related
import com.hosni.hyperfragz.Review
import com.hosni.hyperfragz.SiteController
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val PAD = 20.dp
private val GAP_SECTION = 40.dp

/** design/4 · Fragrance detail. Sections whose data is missing are left out. */
@Composable
fun DetailScreen(site: SiteController, d: Detail) {
    val list = rememberLazyListState()
    Box(
        Modifier
            .fillMaxSize()
            .background(V.Ground)
    ) {
        LazyColumn(state = list, contentPadding = PaddingValues(bottom = 40.dp), modifier = Modifier.fillMaxSize()) {
            item(key = "hero") {
                Box {
                    Hero(d)
                    TopButtons(site)
                }
            }
            item(key = "title") { TitleBlock(d) }
            // While the page is still loading, each missing section shows a
            // placeholder in its place; at the end, absent sections just vanish.
            val loading = !d.complete
            d.rating?.let { r -> item(key = "rating") { RatingBlock(r, loading) } } ?: placeholder(loading, "rating") { Skeleton(null, bars = 2) }
            d.accords?.let { a -> item(key = "accords") { Accords(a) } } ?: placeholder(loading, "accords") { Skeleton("What it smells like", bars = 5) }
            d.notes?.let { n -> item(key = "notes") { Unfolds(n) } } ?: placeholder(loading, "notes") { Skeleton("How it unfolds", bars = 3, raised = true) }
            if (d.longevity != null || d.sillage != null) item(key = "skin") { OnSkin(d) } else placeholder(loading, "skin") { Skeleton("On skin", bars = 2, tall = true) }
            if (d.pros != null || d.cons != null) item(key = "say") { PeopleSay(d) } else placeholder(loading, "say") { Skeleton("What people say", bars = 4) }
            d.wear?.let { w -> item(key = "wear") { BestWorn(w) } } ?: placeholder(loading, "wear") { Skeleton("Best worn", bars = 6) }
            d.perfumers?.let { p -> item(key = "created") { CreatedBy(p) } }
            d.similar?.let { s -> item(key = "similar") { Carousel("Reminds people of", "Scents members say smell alike, with how many agree.", s, withVotes = true) { site.openDetail(Detail.preview(it)) } } }
                ?: placeholder(loading, "similar") { Skeleton("Reminds people of", cards = true) }
            d.alsoLike?.let { s -> item(key = "also") { Carousel("If you like this", null, s, withVotes = false) { site.openDetail(Detail.preview(it)) } } }
                ?: placeholder(loading, "also") { Skeleton("If you like this", cards = true) }
            d.reviews?.takeIf { it.items.isNotEmpty() || !loading }?.let { r -> reviewItems(site, r) }
                ?: placeholder(loading, "reviews") { Skeleton("Reviews", bars = 4) }
        }
        // Once the hero is gone, give the transparent status bar a ground backing.
        val scrolled by remember { derivedStateOf { list.firstVisibleItemIndex > 0 } }
        if (scrolled) Box(
            Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(V.Ground)
        )
    }
}

// ------------------------------------------------------------------ hero + title

@Composable
private fun Hero(d: Detail) {
    PerfumeImage(
        d.image,
        Modifier
            .fillMaxWidth()
            .height(420.dp),
        padding = 48.dp,
        outline = 120.dp,
    )
}

@Composable
private fun TopButtons(site: SiteController) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        RoundButton(onClick = { site.back() }, description = "Back") {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = V.Text, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.weight(1f))
        Box {
            RoundButton(onClick = { menu = true }, description = "More options") {
                Icon(Icons.Default.MoreVert, null, tint = V.Text, modifier = Modifier.size(18.dp))
            }
            SiteMenu(menu, onDismiss = { menu = false }, site)
        }
    }
}

@Composable
private fun RoundButton(onClick: () -> Unit, description: String, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clickable(onClick = onClick)
            .semantics { role = Role.Button; contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0xCC161A18)),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}

@Composable
private fun TitleBlock(d: Detail) {
    Column(Modifier.padding(start = PAD, end = PAD, top = 30.dp)) {
        if (d.brand.isNotEmpty()) Text(d.brand, style = sans(15, V.Text2))
        Text(d.name.ifEmpty { "…" }, style = serif(54, line = 1.0f), modifier = Modifier
            .padding(top = 6.dp)
            .semantics { heading() })
        d.familyLine?.let { Text(it, style = sans(15, V.Text2), modifier = Modifier.padding(top = 10.dp)) }
        Hairline(Modifier.padding(top = 24.dp))
    }
}

@Composable
private fun RatingBlock(r: com.hosni.hyperfragz.Rating, loading: Boolean) {
    Column(Modifier.padding(horizontal = PAD)) {
        val pos = r.positivePct
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontFamily = InstrumentSerif, fontSize = serif(31).fontSize, color = V.Primary)) {
                    append("%.2f".format(Locale.US, r.value))
                }
                append(" out of 5")
                r.votes?.let { append(" from ${fmt(it)} votes.") } ?: append(".")
                pos?.let { append(" $it% love or like it.") }
            },
            style = sans(16, V.Text2, line = 24),
            modifier = Modifier.padding(top = 20.dp),
        )
        // The love…hate split loads a little later; hold its space meanwhile.
        if (r.breakdown == null && loading) {
            Box(
                Modifier
                    .padding(top = 14.dp)
                    .fillMaxWidth()
                    .height(5.dp)
                    .background(V.Track)
            )
            Spacer(Modifier.height(10.dp + 16.dp))
        }
        r.breakdown?.let { b ->
            val colors = listOf(V.Primary, Color(0xFF9E8340), V.Olive.copy(alpha = 0.85f), V.Dim, Color(0xFF2A2E2C))
            SegmentBar(
                b.map { it.count.toFloat() },
                b.indices.map { colors.getOrElse(it) { V.Dim } },
                Modifier
                    .padding(top = 14.dp)
                    .fillMaxWidth()
                    .height(5.dp),
            )
            Row(Modifier.padding(top = 10.dp)) {
                Text("Love", style = sans(12, V.Text3), modifier = Modifier.weight(1f))
                Text("Hate", style = sans(12, V.Text3))
            }
        }
        Hairline(Modifier.padding(top = 20.dp))
    }
}

// ------------------------------------------------------------------ accords

@Composable
private fun Accords(all: List<com.hosni.hyperfragz.Accord>) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val shown = if (expanded) all else all.take(5)
    Column(Modifier.padding(horizontal = PAD)) {
        SectionTitle("What it smells like", Modifier.padding(top = 26.dp, bottom = 12.dp))
        shown.forEachIndexed { i, a ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .height(28.dp)
                    .semantics(mergeDescendants = true) { contentDescription = "${a.label.capitalised()}, ${a.width.roundToInt()}%" },
            ) {
                Text(a.label.capitalised(), style = sans(14, V.Text, 500), modifier = Modifier.width(116.dp), maxLines = 1)
                Box(Modifier.weight(1f)) {
                    Box(
                        Modifier
                            .fillMaxWidth((a.width / 100f).coerceIn(0.04f, 1f))
                            .height(9.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(accordColor(i))
                    )
                }
            }
        }
        if (all.size > 5) TextLink(if (expanded) "Show fewer accords" else "Show ${all.size - 5} more accords", onClick = { expanded = !expanded })
        else Spacer(Modifier.height(20.dp))
        Spacer(Modifier.height(16.dp))
    }
}

/** Gold fading to olive by rank, as in the design. */
private fun accordColor(rank: Int): Color {
    val t = (rank / 6f).coerceAtMost(1f)
    fun mix(a: Float, b: Float) = a + (b - a) * t
    val g = V.Primary; val o = V.Olive
    return Color(mix(g.red, o.red), mix(g.green, o.green), mix(g.blue, o.blue))
}

// ------------------------------------------------------------------ how it unfolds

@Composable
private fun Unfolds(n: com.hosni.hyperfragz.Notes) {
    val stages = buildList {
        if (n.top.isNotEmpty()) add(Triple("Opening, first minutes", n.top, V.Selected to 26))
        if (n.middle.isNotEmpty()) add(Triple("Heart, the next few hours", n.middle, V.Heart to 27))
        if (n.base.isNotEmpty()) add(Triple("Drydown, what stays", n.base, V.Primary to 30))
        if (isEmpty() && n.flat.isNotEmpty()) add(Triple("Notes", n.flat, V.Selected to 26))
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(V.Raised)
            .padding(horizontal = PAD, vertical = 36.dp)
    ) {
        SectionTitle("How it unfolds")
        Text("From the first spray to the last trace on skin.", style = sans(14, V.Text2), modifier = Modifier.padding(top = 8.dp, bottom = 22.dp))
        stages.forEachIndexed { i, (label, notes, look) ->
            val (color, size) = look
            Row(Modifier.height(IntrinsicSize.Min)) {
                // Timeline: dot, then a line down to the next stage's dot.
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(22.dp).fillMaxHeight()) {
                    Box(Modifier.padding(top = 4.dp).size(10.dp).clip(CircleShape).background(color))
                    if (i < stages.lastIndex) Box(
                        Modifier
                            .padding(top = 2.dp)
                            .width(1.dp)
                            .weight(1f)
                            .background(V.Olive.copy(alpha = 0.7f))
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.padding(bottom = if (i < stages.lastIndex) 26.dp else 0.dp)) {
                    Text(label, style = sans(13, V.Text2))
                    Text(listSentence(notes), style = serif(size, color, line = 1.15f), modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

/** "Pear, pink pepper, orange blossom" (first capital only). */
private fun listSentence(items: List<String>) =
    items.mapIndexed { i, s -> if (i == 0) s.capitalised() else s.lowercase() }.joinToString(", ")

// ------------------------------------------------------------------ on skin

@Composable
private fun OnSkin(d: Detail) {
    Column(Modifier.padding(horizontal = PAD)) {
        SectionTitle("On skin", Modifier.padding(top = 36.dp, bottom = 16.dp))
        d.longevity?.let { SkinMeter("Longevity", it, "Very weak", "Eternal") }
        d.sillage?.let { SkinMeter("Sillage", it, "Intimate", "Enormous") }
        Hairline(Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun SkinMeter(title: String, buckets: List<Count>, low: String, high: String) {
    val total = buckets.sumOf { it.count }.coerceAtLeast(1)
    val winner = buckets.maxByOrNull { it.count } ?: return
    val ranked = buckets.sortedByDescending { it.count }
    Column(Modifier.padding(bottom = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = sans(15, V.Text, 600), modifier = Modifier.weight(1f).semantics { heading() })
            Text(winner.label.capitalised(), style = sans(15, V.Primary))
        }
        SegmentBar(
            buckets.map { it.count.toFloat() },
            buckets.map {
                when {
                    it === winner -> V.Primary
                    it.count.toFloat() / total >= 0.12f -> V.Olive.copy(alpha = 0.85f)
                    else -> V.Dim
                }
            },
            Modifier
                .padding(top = 10.dp)
                .fillMaxWidth()
                .height(28.dp),
            gap = 2.dp,
        )
        Row(Modifier.padding(top = 8.dp)) {
            Text(low, style = sans(12, V.Text3), modifier = Modifier.weight(1f))
            Text(high, style = sans(12, V.Text3))
        }
        val a = ranked.getOrNull(0)
        val b = ranked.getOrNull(1)
        if (a != null) Text(
            "${pct(a.count, total)}% say ${a.label.lowercase()}" + (b?.let { " and ${pct(it.count, total)}% say ${it.label.lowercase()}" } ?: "") + ".",
            style = sans(14, V.Text2),
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

// ------------------------------------------------------------------ what people say

@Composable
private fun PeopleSay(d: Detail) {
    Column(Modifier.padding(horizontal = PAD)) {
        SectionTitle("What people say", Modifier.padding(top = 30.dp))
        Text(
            "Summarised by AI from member reviews, so it may be inaccurate. The bars show how many members agree.",
            style = sans(13, V.Text2, line = 20),
            modifier = Modifier.padding(top = 8.dp, bottom = 10.dp),
        )
        d.pros?.let { OpinionList("Pros", it, V.Primary, V.Primary, "pros") }
        d.cons?.let { OpinionList("Cons", it, V.Text, V.Selected, "cons") }
        Hairline(Modifier.padding(top = 16.dp))
    }
}

@Composable
private fun OpinionList(title: String, items: List<Opinion>, titleColor: Color, bar: Color, noun: String) {
    var all by rememberSaveable(title) { mutableStateOf(false) }
    Text(title, style = sans(15, titleColor, 500), modifier = Modifier
        .padding(top = 16.dp, bottom = 4.dp)
        .semantics { heading() })
    (if (all) items else items.take(4)).forEachIndexed { i, o ->
        if (i > 0) Hairline()
        Column(
            Modifier
                .padding(vertical = 12.dp)
                .semantics(mergeDescendants = true) { contentDescription = "${o.text}. ${o.pct}% of ${fmt(o.total)} agree." }
        ) {
            Text(o.text, style = sans(15, V.Text))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 9.dp)) {
                MeterBar(o.pct / 100f, bar, Modifier.weight(1f), height = 3.dp)
                Spacer(Modifier.width(12.dp))
                Text("${o.pct}% of ${fmt(o.total)} agree", style = sans(12, V.Text3))
            }
        }
    }
    if (items.size > 4) {
        Hairline()
        TextLink(if (all) "Show fewer $noun" else "Show all ${items.size} $noun", onClick = { all = !all })
    }
}

// ------------------------------------------------------------------ best worn

@Composable
private fun BestWorn(w: List<Count>) {
    val byLabel = w.associateBy { it.label.lowercase() }
    val day = byLabel["day"]?.count ?: 0
    val night = byLabel["night"]?.count ?: 0
    val seasons = w.filter { it.label.lowercase() in setOf("winter", "spring", "summer", "fall") }.sortedByDescending { it.count }
    val headline = buildString {
        append("Best worn")
        if (day + night > 0) append(if (night >= day) " at night" else " during the day")
        if (seasons.size >= 2) append(", in ${seasons[0].label.lowercase()} and ${seasons[1].label.lowercase()}")
        else if (seasons.isNotEmpty()) append(", in ${seasons[0].label.lowercase()}")
    }
    val rows = w.sortedByDescending { it.count }
    val max = rows.first().count.coerceAtLeast(1)
    Column(Modifier.padding(horizontal = PAD)) {
        SectionTitle(headline, Modifier.padding(top = 36.dp, bottom = 14.dp))
        rows.forEachIndexed { i, c ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .height(28.dp)
                    .semantics(mergeDescendants = true) { contentDescription = "${c.label.capitalised()}, ${fmt(c.count)} votes" },
            ) {
                Text(c.label.capitalised(), style = sans(14, V.Text, 500), modifier = Modifier.width(76.dp))
                MeterBar(c.count.toFloat() / max, if (i < 3) V.Primary else V.Olive, Modifier.weight(1f), height = 5.dp)
                Text(compact(c.count), style = sans(13, V.Text3), modifier = Modifier.width(56.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }
        }
        Hairline(Modifier.padding(top = 26.dp))
    }
}

// ------------------------------------------------------------------ created by

@Composable
private fun CreatedBy(names: String) {
    Column(Modifier.padding(horizontal = PAD)) {
        SectionTitle("Created by", Modifier.padding(top = 32.dp, bottom = 14.dp))
        Text(names, style = sans(15, V.Text, line = 24))
        Hairline(Modifier.padding(top = 28.dp))
    }
}

// ------------------------------------------------------------------ carousels

@Composable
private fun Carousel(title: String, subtitle: String?, items: List<Related>, withVotes: Boolean, onOpen: (Related) -> Unit) {
    // The design sets these two carousels in from the page margin (40 vs 20).
    Column {
        SectionTitle(title, Modifier.padding(start = 40.dp, end = PAD, top = 32.dp))
        subtitle?.let { Text(it, style = sans(14, V.Text2), modifier = Modifier.padding(start = 40.dp, end = PAD, top = 8.dp)) }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 40.dp),
            modifier = Modifier.padding(top = 16.dp),
        ) {
            items(items, key = { it.url }) { r -> RelatedCard(r, withVotes, onOpen) }
        }
        Hairline(Modifier.padding(start = PAD, end = PAD, top = 26.dp))
    }
}

@Composable
private fun RelatedCard(r: Related, withVotes: Boolean, onOpen: (Related) -> Unit) {
    val w = if (withVotes) 150.dp else 140.dp
    Column(
        Modifier
            .width(w)
            .clickable { onOpen(r) }
            .semantics(mergeDescendants = true) {
                contentDescription = listOf(r.name, r.brand).joinToString(", ") +
                    (if (withVotes) r.pct?.let { ", $it% agree from ${fmt(r.total)} votes" } ?: "" else "")
            }
    ) {
        PerfumeImage(
            r.image,
            Modifier
                .width(w)
                .height(if (withVotes) 120.dp else 140.dp)
                .clip(V.Radius),
            padding = 12.dp,
        )
        Text(r.name, style = serif(19, line = 1.1f), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
        Text(r.brand, style = sans(13, V.Text2), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        if (withVotes) r.pct?.let { p ->
            MeterBar(p / 100f, if (p >= 70) V.Primary else V.Text3, Modifier.padding(top = 10.dp).fillMaxWidth(), height = 3.dp)
            Text("$p% agree, ${fmt(r.total)} votes", style = sans(12, V.Text3), modifier = Modifier.padding(top = 6.dp))
        }
    }
}

// ------------------------------------------------------------------ loading placeholders

/** A placeholder item while [show] (the page is still loading). */
private fun LazyListScope.placeholder(show: Boolean, key: String, content: @Composable () -> Unit) {
    if (show) item(key = "skeleton-$key") { content() }
}

@Composable
private fun Skeleton(title: String?, bars: Int = 3, raised: Boolean = false, tall: Boolean = false, cards: Boolean = false) {
    val pulse = rememberInfiniteTransition(label = "skeleton")
    val alpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "alpha",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (raised) Modifier.background(V.Raised) else Modifier)
            .padding(horizontal = if (cards) 40.dp else PAD, vertical = if (raised) 36.dp else 0.dp)
            .semantics { contentDescription = "Loading ${title ?: "rating"}" }
    ) {
        if (title != null) Text(title, style = serif(26, V.Text3), modifier = Modifier.padding(top = if (raised) 0.dp else 32.dp, bottom = 16.dp))
        else Spacer(Modifier.height(20.dp))
        if (cards) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(3) {
                    Column(Modifier.width(150.dp)) {
                        Box(Modifier.size(150.dp, 120.dp).clip(V.Radius).background(V.Track.copy(alpha = alpha)))
                        Box(Modifier.padding(top = 10.dp).size(110.dp, 14.dp).background(V.Track.copy(alpha = alpha)))
                        Box(Modifier.padding(top = 6.dp).size(80.dp, 10.dp).background(V.Track.copy(alpha = alpha)))
                    }
                }
            }
        } else repeat(bars) { i ->
            Box(
                Modifier
                    .padding(vertical = if (tall) 10.dp else 7.dp)
                    .fillMaxWidth(if (tall) 1f else 1f - i * 0.12f)
                    .height(if (tall) 28.dp else 10.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(V.Track.copy(alpha = alpha))
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}

// ------------------------------------------------------------------ reviews

private val SORTS = listOf("newest" to "Newest", "positive" to "Most liked positive", "negative" to "Most liked negative")
private const val REVIEWS_SHOWN = 2

private fun LazyListScope.reviewItems(site: SiteController, r: com.hosni.hyperfragz.Reviews) {
    item(key = "reviews-head") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = PAD, end = 8.dp, top = 30.dp, bottom = 4.dp)
        ) {
            SectionTitle("Reviews", Modifier.weight(1f))
            var open by remember { mutableStateOf(false) }
            Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clickable(onClickLabel = "Change review order") { open = true }
                        .padding(horizontal = 12.dp)
                ) {
                    Text(SORTS.firstOrNull { it.first == r.sort }?.second ?: "Newest", style = sans(14, V.Text, 500))
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.KeyboardArrowDown, null, tint = V.Text, modifier = Modifier.size(14.dp))
                }
                DropdownMenu(open, onDismissRequest = { open = false }, containerColor = V.Surface) {
                    SORTS.forEach { (k, label) ->
                        DropdownMenuItem(
                            text = { Text(label, style = sans(15, if (k == r.sort) V.Primary else V.Text)) },
                            onClick = { open = false; site.sortReviews(k) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
            }
        }
    }
    item(key = "reviews-list-${r.sort}") {
        var all by rememberSaveable(r.sort) { mutableStateOf(false) }
        Column(Modifier.padding(horizontal = PAD)) {
            val shown = if (all) r.items else r.items.take(REVIEWS_SHOWN)
            shown.forEach { rv ->
                ReviewItem(site, rv)
                Hairline()
            }
            if (r.items.isEmpty()) Text("No reviews yet.", style = sans(14, V.Text3), modifier = Modifier.padding(vertical = 16.dp))
            Spacer(Modifier.height(24.dp))
            when {
                !all && r.items.size > REVIEWS_SHOWN -> OutlineButton(
                    "Read all ${r.total?.let { compact(it).uppercase() } ?: ""} reviews".replace("  ", " "),
                    onClick = { all = true },
                    modifier = Modifier.fillMaxWidth(),
                )
                all && (r.total ?: 0) > r.items.size -> OutlineButton(
                    "More reviews on Fragrantica",
                    onClick = { site.openWeb() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewItem(site: SiteController, rv: Review) {
    var open by rememberSaveable(rv.id) { mutableStateOf(false) }
    var overflows by remember(rv.id) { mutableStateOf(false) }
    Column(Modifier.padding(vertical = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E2220)),
                contentAlignment = Alignment.Center,
            ) { Text(rv.user.trim().take(1).uppercase().ifEmpty { "?" }, style = sans(15, V.Text, 500)) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(rv.user, style = sans(14, V.Text, 600), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(prettyDate(rv.date), style = sans(12, V.Text3))
            }
            rv.verdict?.let {
                Text(
                    it.capitalised(),
                    style = serif(20, when (it) { "love" -> V.Primary; "like" -> V.Text; else -> V.Text2 }),
                    modifier = Modifier.semantics { contentDescription = "Verdict: $it" },
                )
            }
        }
        if (rv.tags.isNotEmpty()) FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 12.dp),
        ) { rv.tags.forEach { Tag(it.capitalised()) } }
        Text(
            rv.text,
            style = sans(15, V.Text, line = 23),
            maxLines = if (open) Int.MAX_VALUE else 6,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!open) overflows = it.hasVisualOverflow },
            modifier = Modifier.padding(top = 14.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable(onClickLabel = "Mark review as helpful") { site.helpful(rv.id) }
                    .padding(end = 16.dp)
            ) {
                Icon(Icons.Default.ThumbUp, null, tint = V.Text2, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("Helpful", style = sans(13, V.Text2))
            }
            if (overflows || open) Text(
                if (open) "Show less" else "Read more",
                style = sans(13, V.Text2),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable { open = !open }
                    .semantics { role = Role.Button }
                    .padding(start = 8.dp, top = 15.dp),
            )
        }
    }
}

private val DATE_OUT = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

/** "2026-10-09" → "Oct 9, 2026"; anything else passes through. */
private fun prettyDate(s: String): String =
    runCatching { LocalDate.parse(s).format(DATE_OUT) }.getOrDefault(s)
