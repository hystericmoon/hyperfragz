package com.hosni.hyperfragz.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import java.text.NumberFormat
import kotlin.math.roundToInt

// ------------------------------------------------------------------ type (from design/)

val Sans = TextStyle(fontFamily = Geist, color = V.Text)
fun sans(size: Int, color: Color = V.Text, weight: Int = 400, line: Int = 0) =
    Sans.copy(fontSize = size.sp, color = color, fontWeight = FontWeight(weight), lineHeight = if (line > 0) line.sp else (size * 1.35f).sp)
fun serif(size: Int, color: Color = V.Text, line: Float = 1.12f) =
    SerifTitle.copy(fontSize = size.sp, lineHeight = (size * line).sp, color = color)

/** Upper-case section label (GENDER, NOTES …). */
val Overline = sans(11, V.Text2, 500).copy(letterSpacing = 1.1.sp)

// ------------------------------------------------------------------ numbers

fun fmt(n: Int): String = NumberFormat.getIntegerInstance().format(n)

/** 959 · 1.4k · 14.4k · 2.9K-style (lower-case k, like the design). */
fun compact(n: Int): String = when {
    n >= 1_000_000 -> trim1(n / 1_000_000.0) + "M"
    n >= 1_000 -> trim1(n / 1_000.0) + "k"
    else -> n.toString()
}
private fun trim1(d: Double) = ("%.1f".format(d)).removeSuffix(".0")

private val NUMBER_WORDS = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten")
fun numberWord(n: Int) = NUMBER_WORDS.getOrNull(n) ?: n.toString()

// ------------------------------------------------------------------ building blocks

/** Black image well; bottle outline while loading or when there's no image. */
@Composable
fun PerfumeImage(url: String?, modifier: Modifier = Modifier, padding: Dp = 12.dp, outline: Dp = 40.dp) {
    Box(modifier.background(V.Black), contentAlignment = Alignment.Center) {
        if (url == null) BottleOutline(outline)
        else SubcomposeAsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            loading = { BottleOutline(outline) },
            error = { BottleOutline(outline) },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }
}

@Composable
fun BottleOutline(size: Dp) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size * 0.75f, size)) {
            val stroke = Stroke(width = 1.2.dp.toPx())
            val c = V.Olive
            val w = this.size.width
            val h = this.size.height
            drawRoundRect(c, Offset(w * 0.32f, 0f), Size(w * 0.36f, h * 0.14f), CornerRadius(2.dp.toPx()), style = stroke)
            drawRect(c, Offset(w * 0.42f, h * 0.14f), Size(w * 0.16f, h * 0.08f), style = stroke)
            drawRoundRect(c, Offset(0f, h * 0.22f), Size(w, h * 0.78f), CornerRadius(5.dp.toPx()), style = stroke)
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, size: Int = 26) {
    Text(text, style = serif(size), modifier = modifier.semantics { heading() })
}

@Composable
fun Hairline(modifier: Modifier = Modifier) = HorizontalDivider(modifier, thickness = 1.dp, color = V.Hairline)

/** Thin bar: [fraction] 0..1 of [color] on a dark track. */
@Composable
fun MeterBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 3.dp, track: Color = V.Track) {
    Canvas(modifier.height(height)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        drawRoundRect(color, size = Size(size.width * fraction.coerceIn(0f, 1f), size.height), cornerRadius = r)
    }
}

/** Segments proportional to [weights], separated by [gap]. */
@Composable
fun SegmentBar(weights: List<Float>, colors: List<Color>, modifier: Modifier, gap: Dp = 2.dp, radius: Dp = 1.dp) {
    Canvas(modifier) {
        val total = weights.sum().takeIf { it > 0f } ?: return@Canvas
        val g = gap.toPx()
        val usable = size.width - g * (weights.size - 1)
        var x = 0f
        weights.forEachIndexed { i, w ->
            val segW = usable * (w / total)
            if (segW > 0.5f) drawRoundRect(colors[i], Offset(x, 0f), Size(segW, size.height), CornerRadius(radius.toPx()))
            x += segW + g
        }
    }
}

/** Underlined text link ("Show all 8 pros"). */
@Composable
fun TextLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = V.Text2) {
    Text(
        text,
        style = sans(14, color).copy(textDecoration = TextDecoration.Underline),
        modifier = modifier
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .semantics { role = Role.Button }
            .padding(vertical = 14.dp),
    )
}

/** Outlined, full-ish button (Clear all, Read all …). */
@Composable
fun OutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, height: Dp = 52.dp) {
    Box(
        modifier
            .heightIn(min = height)
            .clip(V.Radius)
            .border(BorderStroke(1.dp, V.Hairline), V.Radius)
            .clickable(onClick = onClick)
            .semantics { role = Role.Button }
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = sans(15, V.Text, 500)) }
}

/** Gold, filled. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, leading: (@Composable () -> Unit)? = null) {
    Row(
        modifier
            .heightIn(min = 52.dp)
            .clip(V.Radius)
            .background(V.Primary)
            .clickable(onClick = onClick)
            .semantics { role = Role.Button }
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        leading?.invoke()
        Text(text, style = sans(16, V.OnPrimary, 500))
    }
}

/**
 * Filter chip from the Refine sheet: outlined, or cream-filled when selected.
 * Selection is also exposed to TalkBack as a state description (not colour only).
 */
@Composable
fun SelectChip(label: String, selected: Boolean, onClick: () -> Unit, description: String = label) {
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .semantics(mergeDescendants = true) {
                role = Role.Checkbox
                stateDescription = if (selected) "Selected" else "Not selected"
                contentDescription = description
            }
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .height(40.dp)
                .clip(V.Radius)
                .then(if (selected) Modifier.background(V.Selected) else Modifier.border(1.dp, V.Hairline, V.Radius))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = sans(15, if (selected) V.OnSelected else V.Text, 500), maxLines = 1)
        }
    }
}

/** Applied-filter chip with ×, cream-filled (index header). */
@Composable
fun AppliedChip(label: String, onRemove: () -> Unit) {
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "Remove $label"
            }
            .clickable(onClick = onRemove),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .height(34.dp)
                .clip(V.Radius)
                .background(V.Selected)
                .padding(start = 14.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = sans(15, V.OnSelected, 500), maxLines = 1)
            Box(Modifier.size(8.dp))
            Icon(Icons.Default.Close, null, tint = V.OnSelected, modifier = Modifier.size(14.dp))
        }
    }
}

/** Outlined tag (review vote tags). */
@Composable
fun Tag(text: String) {
    Box(
        Modifier
            .heightIn(min = 28.dp)
            .border(1.dp, V.Hairline, RoundedCornerShape(3.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = sans(12, V.Text2)) }
}

fun pct(part: Int, total: Int): Int = if (total <= 0) 0 else (part * 100f / total).roundToInt()

fun String.capitalised() = replaceFirstChar { it.uppercase() }
