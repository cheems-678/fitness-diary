package cn.wangguanwen.fitnessdiary.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.wangguanwen.fitnessdiary.data.DiaryEntry
import cn.wangguanwen.fitnessdiary.data.WeightFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.max

@Composable
fun WeightChart(entries: List<DiaryEntry>) {
    var selected by remember(entries) { mutableStateOf(entries.lastOrNull()) }
    val color = Ink
    val density = LocalDensity.current
    val textSize = with(density) { 11.sp.toPx() }
    val curve = remember(entries) { smoothSegments(entries.map { CurvePoint(LocalDate.parse(it.date).toEpochDay().toDouble(), it.weightHundredths!! / 100.0) }) }
    Column(Modifier.fillMaxWidth().background(Paper.copy(alpha = .88f), SketchShape).sketchBorder().padding(16.dp)) {
        Text(selected?.let { "${it.date}   ${WeightFormat.display(it.weightHundredths!!)} kg" }
            ?: "这个时段还没有体重记录", color = color, fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(184.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("记录体重后，这里会出现变化曲线", color = Muted, fontSize = 12.sp)
            }
        } else {
            val minWeight = entries.minOf { it.weightHundredths!! } / 100.0
            val maxWeight = entries.maxOf { it.weightHundredths!! } / 100.0
            val margin = max(.5, (maxWeight - minWeight) * .15)
            val low = max(0.0, minWeight - margin)
            val high = maxWeight + margin
            val firstDay = LocalDate.parse(entries.first().date).toEpochDay()
            val lastDay = LocalDate.parse(entries.last().date).toEpochDay()
            val labels = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = android.graphics.Color.rgb(85, 80, 71); this.textSize = textSize }
            val description = entries.joinToString("；") { "${it.date} ${WeightFormat.display(it.weightHundredths!!)} 公斤" }
            Canvas(Modifier.fillMaxWidth().height(184.dp).semantics { contentDescription = "体重变化曲线：$description" }
                .pointerInput(entries) {
                    detectTapGestures { touch ->
                        val left = 49.dp.toPx()
                        val right = size.width - 12.dp.toPx()
                        selected = entries.minByOrNull { entry ->
                            val ratio = if (firstDay == lastDay) .5f else (LocalDate.parse(entry.date).toEpochDay() - firstDay).toFloat() / (lastDay - firstDay)
                            abs(touch.x - (left + ratio * (right - left)))
                        }
                    }
                }) {
                val left = 49.dp.toPx(); val right = size.width - 12.dp.toPx()
                val top = 10.dp.toPx(); val bottom = size.height - 30.dp.toPx()
                val gridColor = Pencil.copy(alpha = .3f)
                labels.textAlign = Paint.Align.LEFT
                for (step in 0..3) {
                    val y = top + (bottom - top) * step / 3
                    drawLine(gridColor, Offset(left, y), Offset(right, y), strokeWidth = .7.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())))
                    val value = high - (high - low) * step / 3
                    drawContext.canvas.nativeCanvas.drawText(String.format(java.util.Locale.ROOT, "%.2f", value), 0f, y + textSize / 3, labels)
                }
                fun screen(point: CurvePoint): Offset {
                    val ratio = if (firstDay == lastDay) .5f else ((point.x - firstDay) / (lastDay - firstDay)).toFloat()
                    return Offset(left + ratio * (right - left), bottom - ((point.y - low) / (high - low) * (bottom - top)).toFloat())
                }
                val points = entries.map { screen(CurvePoint(LocalDate.parse(it.date).toEpochDay().toDouble(), it.weightHundredths!! / 100.0)) }
                if (points.size > 1) {
                    val line = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        curve.forEach { segment ->
                            val first = screen(segment.firstControl); val second = screen(segment.secondControl); val end = screen(segment.end)
                            cubicTo(first.x, first.y, second.x, second.y, end.x, end.y)
                        }
                    }
                    val fill = Path().apply { addPath(line); lineTo(points.last().x, bottom); lineTo(points.first().x, bottom); close() }
                    drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = .10f), color.copy(alpha = .01f)), top, bottom))
                    drawPath(line, Paper, style = Stroke(width = 5.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawPath(line, color, style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                points.forEachIndexed { index, point ->
                    if (entries[index] == selected) {
                        drawLine(color.copy(alpha = .35f), Offset(point.x, top), Offset(point.x, bottom), .8.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
                        drawCircle(Paper, 8.dp.toPx(), point)
                    }
                    drawCircle(Paper, 4.dp.toPx(), point)
                    drawCircle(color, if (entries[index] == selected) 4.dp.toPx() else 3.dp.toPx(), point, style = Stroke(1.6.dp.toPx()))
                    if (entries[index] == selected) drawCircle(color, 1.6.dp.toPx(), point)
                }
                val dateFormat = DateTimeFormatter.ofPattern("MM/dd")
                if (firstDay == lastDay) {
                    labels.textAlign = Paint.Align.CENTER
                    drawContext.canvas.nativeCanvas.drawText(LocalDate.parse(entries.first().date).format(dateFormat), (left + right) / 2, size.height - 4.dp.toPx(), labels)
                } else {
                    labels.textAlign = Paint.Align.LEFT
                    drawContext.canvas.nativeCanvas.drawText(LocalDate.ofEpochDay(firstDay).format(dateFormat), left, size.height - 4.dp.toPx(), labels)
                    labels.textAlign = Paint.Align.RIGHT
                    drawContext.canvas.nativeCanvas.drawText(LocalDate.ofEpochDay(lastDay).format(dateFormat), right, size.height - 4.dp.toPx(), labels)
                }
            }
            Text("圆点为实际记录 · 点击查看数值", fontSize = 11.sp, color = Muted, modifier = Modifier.padding(top = 6.dp))
        }
    }
}
