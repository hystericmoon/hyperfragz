package com.hosni.hyperfragz.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hosni.hyperfragz.R

/** "Night Vitrine" tokens. Keep in sync with :root vars in assets/skin.css. */
object V {
    val Ground = Color(0xFF0E1311)
    val Surface = Color(0xFF1A1A1A)
    val Hairline = Color(0xFF333333)
    val ChipBorder = Color(0xFF666666)
    val Text = Color(0xFFF6F7F2)
    val Text2 = Color(0xFFB3B3B3)
    val Text3 = Color(0xFF8C8C8C)
    val Selected = Color(0xFFDFDBCA)
    val OnSelected = Color(0xFF0E1311)
    val Primary = Color(0xFFCFA53B)
    val OnPrimary = Color(0xFF0E1311)

    // Derived from the design PNGs (design/): not separate brand tokens.
    val Black = Color(0xFF000000)          // image wells, hero
    val Raised = Color(0xFF151A18)         // "How it unfolds" band
    val Track = Color(0xFF262A28)          // empty part of bars
    val Olive = Color(0xFF6B6345)          // non-leading bar segments
    val Dim = Color(0xFF353937)            // minor bar segments
    val Heart = Color(0xFFD9C07C)          // middle notes, between cream and gold

    val Radius = RoundedCornerShape(4.dp)
    val SheetRadius = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun geist(weight: Int) = Font(
    R.font.geist,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Geist = FontFamily(geist(400), geist(500), geist(600))
val InstrumentSerif = FontFamily(
    Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.instrument_serif_regular, FontWeight.Normal, FontStyle.Normal),
)

/** Instrument Serif italic: perfume names and titles. */
val SerifTitle = TextStyle(fontFamily = InstrumentSerif, fontStyle = FontStyle.Italic, fontSize = 24.sp, lineHeight = 28.sp, color = V.Text)
val SerifRow = SerifTitle.copy(fontSize = 20.sp, lineHeight = 24.sp)

// No colour here: components (chips, buttons) supply LocalContentColor.
private val base = TextStyle(fontFamily = Geist)

private val typography = Typography(
    bodyLarge = base.copy(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = base.copy(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = base.copy(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = base.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight(500)),
    labelMedium = base.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight(500)),
    labelSmall = base.copy(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight(500), letterSpacing = 0.6.sp),
    titleLarge = SerifTitle.copy(color = Color.Unspecified),
    titleMedium = SerifRow.copy(color = Color.Unspecified),
)

@Composable
fun VitrineTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = V.Primary,
            onPrimary = V.OnPrimary,
            secondary = V.Selected,
            onSecondary = V.OnSelected,
            background = V.Ground,
            onBackground = V.Text,
            surface = V.Surface,
            onSurface = V.Text,
            surfaceVariant = V.Surface,
            onSurfaceVariant = V.Text2,
            surfaceContainerLow = V.Surface,
            surfaceContainer = V.Surface,
            surfaceContainerHigh = V.Surface,
            outline = V.ChipBorder,
            outlineVariant = V.Hairline,
        ),
        typography = typography,
        shapes = Shapes(
            extraSmall = V.Radius, small = V.Radius, medium = V.Radius, large = V.Radius,
            extraLarge = RoundedCornerShape(12.dp),
        ),
        content = { CompositionLocalProvider(LocalContentColor provides V.Text, content = content) },
    )
}
