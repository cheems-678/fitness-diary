package cn.wangguanwen.fitnessdiary.ui

import kotlin.math.abs
import kotlin.math.sqrt

internal data class CurvePoint(val x: Double, val y: Double)

internal data class CurveSegment(val start: CurvePoint, val firstControl: CurvePoint, val secondControl: CurvePoint, val end: CurvePoint) {
    fun at(t: Double): CurvePoint {
        require(t in 0.0..1.0)
        val u = 1 - t
        fun coordinate(a: Double, b: Double, c: Double, d: Double) = u * u * u * a + 3 * u * u * t * b + 3 * u * t * t * c + t * t * t * d
        return CurvePoint(coordinate(start.x, firstControl.x, secondControl.x, end.x), coordinate(start.y, firstControl.y, secondControl.y, end.y))
    }
}

/** Shape-preserving Hermite interpolation; x is the real date, not an equally-spaced index. */
internal fun smoothSegments(points: List<CurvePoint>): List<CurveSegment> {
    require(points.all { it.x.isFinite() && it.y.isFinite() })
    require(points.zipWithNext().all { (left, right) -> right.x > left.x }) { "日期必须严格递增" }
    if (points.size < 2) return emptyList()
    val h = points.zipWithNext().map { (left, right) -> right.x - left.x }
    val slopes = points.zipWithNext().mapIndexed { index, (left, right) -> (right.y - left.y) / h[index] }
    val derivatives = DoubleArray(points.size)
    if (points.size == 2) { derivatives[0] = slopes[0]; derivatives[1] = slopes[0] }
    else {
        fun endpoint(firstH: Double, secondH: Double, firstSlope: Double, secondSlope: Double): Double {
            val slope = ((2 * firstH + secondH) * firstSlope - firstH * secondSlope) / (firstH + secondH)
            return if (slope * firstSlope <= 0) 0.0 else slope.coerceIn(-3 * abs(firstSlope), 3 * abs(firstSlope))
        }
        derivatives[0] = endpoint(h[0], h[1], slopes[0], slopes[1])
        derivatives[points.lastIndex] = endpoint(h.last(), h[h.lastIndex - 1], slopes.last(), slopes[slopes.lastIndex - 1])
        for (index in 1 until points.lastIndex) {
            if (slopes[index - 1] * slopes[index] <= 0) derivatives[index] = 0.0
            else {
                val firstWeight = 2 * h[index] + h[index - 1]
                val secondWeight = h[index] + 2 * h[index - 1]
                derivatives[index] = (firstWeight + secondWeight) / (firstWeight / slopes[index - 1] + secondWeight / slopes[index])
            }
        }
    }
    // Fritsch–Carlson bound: controls stay inside each interval's observed range.
    for (index in slopes.indices) {
        val slope = slopes[index]
        if (slope == 0.0) { derivatives[index] = 0.0; derivatives[index + 1] = 0.0 }
        else {
            val alpha = derivatives[index] / slope
            val beta = derivatives[index + 1] / slope
            val norm = sqrt(alpha * alpha + beta * beta)
            if (norm > 3) {
                derivatives[index] = 3 * alpha * slope / norm
                derivatives[index + 1] = 3 * beta * slope / norm
            }
        }
    }
    return h.indices.map { index ->
        CurveSegment(points[index], CurvePoint(points[index].x + h[index] / 3, points[index].y + derivatives[index] * h[index] / 3),
            CurvePoint(points[index + 1].x - h[index] / 3, points[index + 1].y - derivatives[index + 1] * h[index] / 3), points[index + 1])
    }
}
