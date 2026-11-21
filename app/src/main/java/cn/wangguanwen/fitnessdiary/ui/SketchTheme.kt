package cn.wangguanwen.fitnessdiary.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.*
import cn.wangguanwen.fitnessdiary.R

internal val Paper = Color(0xFFF5F0E6)
internal val Ink = Color(0xFF282520)
internal val Muted = Color(0xFF555047)
internal val Pencil = Color(0xFF777067)
internal val Handwriting = FontFamily(Font(R.font.wenkai_regular))

/** A stable pencil outline: it never changes with recomposition or chart values. */
private fun paperPath(size: Size, density: Float, inset: Float = 0f): Path {
    val x = inset; val y = inset; val w = size.width - inset; val h = size.height - inset
    val r = minOf(17f * density, (w - x) / 4, (h - y) / 4)
    val wobble = minOf(.7f * density, r / 8)
    return Path().apply {
        moveTo(x + r, y + wobble)
        cubicTo(w * .32f, y - wobble, w * .67f, y + wobble * 2, w - r, y)
        quadraticTo(w, y, w - wobble, y + r)
        cubicTo(w + wobble, h * .33f, w - wobble * 2, h * .68f, w, h - r)
        quadraticTo(w, h, w - r, h - wobble)
        cubicTo(w * .67f, h + wobble, w * .34f, h - wobble * 2, x + r, h)
        quadraticTo(x, h, x + wobble, h - r)
        cubicTo(x - wobble, h * .67f, x + wobble * 2, h * .34f, x, y + r)
        quadraticTo(x, y, x + r, y + wobble)
        close()
    }
}

internal object SketchShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(paperPath(size, density.density, density.density))
}

internal fun Modifier.sketchBorder() = drawWithCache {
    val first = paperPath(size, density, 1.4.dp.toPx())
    val second = paperPath(size, density, 3.2.dp.toPx())
    onDrawWithContent {
        drawContent()
        drawPath(first, Ink.copy(alpha = .58f), style = Stroke(1.dp.toPx()))
        translate(.4.dp.toPx(), -.3.dp.toPx()) {
            drawPath(second, Pencil.copy(alpha = .24f), style = Stroke(.55.dp.toPx()))
        }
    }
}

@Composable
internal fun SketchCard(
    modifier: Modifier = Modifier,
    opacity: Float = .66f,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (onClick == null) Surface(modifier.sketchBorder(), shape = SketchShape, color = Paper.copy(alpha = opacity), contentColor = Ink) { Column(content = content) }
    else Surface(onClick = onClick, modifier = modifier.sketchBorder(), shape = SketchShape, color = Paper.copy(alpha = opacity), contentColor = Ink) { Column(content = content) }
}

@Composable
internal fun sketchFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Paper.copy(alpha = .74f), unfocusedContainerColor = Paper.copy(alpha = .66f),
    errorContainerColor = Paper.copy(alpha = .74f), focusedBorderColor = Ink, unfocusedBorderColor = Pencil,
)

@Composable
internal fun SketchTheme(content: @Composable () -> Unit) {
    val base = Typography()
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Ink, onPrimary = Paper, primaryContainer = Ink, onPrimaryContainer = Paper,
            secondary = Ink, onSecondary = Paper, secondaryContainer = Color(0xFFE1D9CC), onSecondaryContainer = Ink,
            tertiary = Pencil, background = Paper, onBackground = Ink,
            surface = Paper, onSurface = Ink, surfaceVariant = Paper.copy(alpha = .8f), onSurfaceVariant = Muted,
            surfaceContainer = Paper, surfaceContainerHigh = Paper, surfaceContainerHighest = Color(0xFFE7E0D4),
            outline = Pencil, outlineVariant = Pencil.copy(alpha = .4f), surfaceTint = Color.Transparent,
            error = Ink, onError = Paper, errorContainer = Color(0xFFE1D9CC), onErrorContainer = Ink,
        ),
        typography = base.copy(
            headlineSmall = base.headlineSmall.copy(fontFamily = Handwriting),
            headlineMedium = base.headlineMedium.copy(fontFamily = Handwriting),
            titleLarge = base.titleLarge.copy(fontFamily = Handwriting),
            titleMedium = base.titleMedium.copy(fontFamily = Handwriting),
            labelLarge = base.labelLarge.copy(fontFamily = Handwriting, fontSize = 16.sp),
            labelMedium = base.labelMedium.copy(fontFamily = Handwriting, fontSize = 14.sp),
            labelSmall = base.labelSmall.copy(fontFamily = Handwriting, fontSize = 13.sp),
        ),
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(24.dp)),
        content = content,
    )
}

@Composable
internal fun SketchBackground(scene: Int) {
    Crossfade(targetState = scene, animationSpec = tween(360, easing = FastOutSlowInEasing), label = "纸页背景淡化") { page ->
        Image(
            painter = painterResource(when (page) { 0 -> R.drawable.bg_trend; 1 -> R.drawable.bg_diary; else -> R.drawable.bg_settings }),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = BiasAlignment(if (page == 2) .1f else 0f, 0f),
            modifier = Modifier.fillMaxSize(),
        )
    }
    // The drawing stays sharp; only the text near the system bars gets a light paper wash.
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Paper.copy(alpha = .16f), Color.Transparent, Color.Transparent, Paper.copy(alpha = .18f)))))
    Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(Paper.copy(alpha = .84f)))
}
