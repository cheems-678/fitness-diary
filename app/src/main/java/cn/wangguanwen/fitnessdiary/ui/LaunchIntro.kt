package cn.wangguanwen.fitnessdiary.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.wangguanwen.fitnessdiary.R

private const val IntroDurationMs = 3_000

@Composable
internal fun LaunchIntro(onFinish: () -> Unit) {
    val elapsed = remember { Animatable(0f) }
    val finish by rememberUpdatedState(onFinish)
    LaunchedEffect(Unit) {
        elapsed.animateTo(IntroDurationMs.toFloat(), tween(IntroDurationMs, easing = LinearEasing))
        finish()
    }
    BackHandler { finish() }

    Box(
        Modifier.fillMaxSize()
            .testTag("launch-intro")
            .graphicsLayer { alpha = 1f - phase(elapsed.value, 2_600, IntroDurationMs) }
            .clipToBounds()
            .background(Paper)
            .clickable(
                indication = null,
                interactionSource = null,
                role = Role.Button,
                onClickLabel = "跳过启动动画",
                onClick = { finish() },
            ),
    ) {
        Image(
            painter = painterResource(R.drawable.bg_trend),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                alpha = phase(elapsed.value, 0, 800)
                scaleX = 1.045f - .045f * phase(elapsed.value, 0, 2_600)
                scaleY = scaleX
            },
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to Paper,
            .18f to Paper.copy(alpha = .95f),
            .40f to Color.Transparent,
            .75f to Color.Transparent,
            .92f to Paper.copy(alpha = .97f),
            1f to Paper,
        )))

        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(.6f))
            Text(
                "健身日记",
                modifier = Modifier.graphicsLayer {
                    val reveal = phase(elapsed.value, 400, 1_100)
                    alpha = reveal
                    translationY = 10.dp.toPx() * (1f - reveal)
                },
                color = Ink,
                fontFamily = Handwriting,
                fontSize = 44.sp,
                lineHeight = 52.sp,
                maxLines = 1,
            )
            Spacer(Modifier.weight(4f))
            IntroCurve { phase(elapsed.value, 900, 2_300) }
            Spacer(Modifier.height(10.dp))
            Text("轻触跳过", color = Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun IntroCurve(progress: () -> Float) {
    Box(Modifier.size(width = 168.dp, height = 46.dp).drawWithCache {
        fun point(x: Float, y: Float) = Offset(size.width * x, size.height * y)
        val start = point(.03f, .22f)
        val middle = point(.49f, .62f)
        val turn = point(.76f, .40f)
        val end = point(.97f, .79f)
        val line = Path().apply {
            moveTo(start.x, start.y)
            cubicTo(size.width * .21f, start.y, size.width * .26f, middle.y, middle.x, middle.y)
            cubicTo(size.width * .68f, middle.y, size.width * .67f, turn.y, turn.x, turn.y)
            cubicTo(size.width * .86f, turn.y, size.width * .84f, end.y, end.x, end.y)
        }
        val measure = PathMeasure().apply { setPath(line, false) }
        val revealed = Path()
        val paperStroke = Stroke(6.dp.toPx(), cap = StrokeCap.Round)
        val inkStroke = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round)
        onDrawBehind {
            val fraction = progress()
            if (fraction > 0f) {
                val distance = measure.length * fraction
                revealed.reset()
                if (measure.getSegment(0f, distance, revealed)) {
                    drawPath(revealed, Paper, style = paperStroke)
                    drawPath(revealed, Ink, style = inkStroke)
                    drawCircle(Ink, 2.8.dp.toPx(), start)
                    drawCircle(Ink, 3.2.dp.toPx(), measure.getPosition(distance))
                }
            }
        }
    })
}

private fun phase(elapsedMs: Float, startMs: Int, endMs: Int): Float =
    FastOutSlowInEasing.transform(((elapsedMs - startMs) / (endMs - startMs)).coerceIn(0f, 1f))
