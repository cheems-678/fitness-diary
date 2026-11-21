package cn.wangguanwen.fitnessdiary

import cn.wangguanwen.fitnessdiary.ui.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class SmoothCurveTest {
    @Test fun passesEveryMeasuredPointAndKeepsDateSpacing() {
        val points = listOf(CurvePoint(0.0, 65.25), CurvePoint(1.0, 65.40), CurvePoint(11.0, 64.8), CurvePoint(12.0, 65.1))
        val segments = smoothSegments(points)
        assertEquals(3, segments.size)
        segments.forEachIndexed { index, segment ->
            assertEquals(points[index], segment.at(0.0)); assertEquals(points[index + 1], segment.at(1.0))
            assertEquals((points[index].x + points[index + 1].x) / 2, segment.at(.5).x, 1e-9)
        }
        assertEquals(10.0, segments[1].end.x - segments[1].start.x, 0.0)
    }

    @Test fun alternatingPeaksNeverCreateUnmeasuredExtrema() {
        val points = listOf(CurvePoint(0.0, 70.0), CurvePoint(1.0, 65.0), CurvePoint(2.0, 69.9), CurvePoint(50.0, 65.1), CurvePoint(51.0, 68.0))
        assertBounded(points)
    }

    @Test fun risingAndFallingTrendsStayMonotone() {
        listOf(listOf(65.0, 65.01, 66.7, 66.71), listOf(70.0, 68.0, 67.99, 65.0)).forEach { weights ->
            val points = listOf(0.0, 1.0, 40.0, 41.0).zip(weights) { date, weight -> CurvePoint(date, weight) }
            assertBounded(points)
            val samples = smoothSegments(points).flatMap { segment -> (0..100).map { segment.at(it / 100.0).y } }
            assertTrue(samples.zipWithNext().all { (left, right) -> if (weights.last() > weights.first()) right >= left - 1e-9 else right <= left + 1e-9 })
        }
    }

    @Test fun flatSectionsAndJoinSlopesRemainStable() {
        val points = listOf(CurvePoint(0.0, 65.0), CurvePoint(3.0, 65.0), CurvePoint(8.0, 66.0), CurvePoint(9.0, 66.1))
        val segments = smoothSegments(points)
        (0..100).forEach { assertEquals(65.0, segments[0].at(it / 100.0).y, 1e-9) }
        segments.zipWithNext().forEach { (left, right) ->
            val leftSlope = (left.end.y - left.secondControl.y) / (left.end.x - left.secondControl.x)
            val rightSlope = (right.firstControl.y - right.start.y) / (right.firstControl.x - right.start.x)
            assertEquals(leftSlope, rightSlope, 1e-8)
        }
    }

    @Test fun zeroOneAndTwoRecordsHaveNaturalResults() {
        assertTrue(smoothSegments(emptyList()).isEmpty())
        assertTrue(smoothSegments(listOf(CurvePoint(1.0, 65.0))).isEmpty())
        val line = smoothSegments(listOf(CurvePoint(1.0, 65.0), CurvePoint(11.0, 66.0))).single()
        (0..10).forEach { index -> val t = index / 10.0; assertEquals(65 + t, line.at(t).y, 1e-9) }
    }

    @Test fun invalidDatesAndNumbersAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { smoothSegments(listOf(CurvePoint(1.0, 65.0), CurvePoint(1.0, 66.0))) }
        assertThrows(IllegalArgumentException::class.java) { smoothSegments(listOf(CurvePoint(2.0, 65.0), CurvePoint(1.0, 66.0))) }
        assertThrows(IllegalArgumentException::class.java) { smoothSegments(listOf(CurvePoint(1.0, Double.NaN))) }
    }

    private fun assertBounded(points: List<CurvePoint>) {
        smoothSegments(points).forEach { segment -> (0..100).forEach { sample ->
            val value = segment.at(sample / 100.0).y
            assertTrue("曲线超出相邻记录范围", value >= min(segment.start.y, segment.end.y) - 1e-8 && value <= max(segment.start.y, segment.end.y) + 1e-8)
        } }
    }
}
