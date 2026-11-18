package com.reco1l.verktex

import com.reco1l.verktex.data.Mat2
import kotlin.math.PI
import kotlin.math.tan
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Test

class Mat2TransformTest {

    @Test
    fun inPlaceTransformMatchesTheAllocatingOne() {
        val random = Random(42)
        val out = FloatArray(4)

        repeat(200) {
            val matrix = Mat2(
                a = random.nextFloat() * 4f - 2f,
                b = random.nextFloat() * 4f - 2f,
                c = random.nextFloat() * 4f - 2f,
                d = random.nextFloat() * 4f - 2f,
                tx = random.nextFloat() * 200f - 100f,
                ty = random.nextFloat() * 200f - 100f,
            )
            val x = random.nextFloat() * 1000f - 500f
            val y = random.nextFloat() * 1000f - 500f

            val expected = matrix.transform(x, y)
            matrix.transform(x, y, out, offset = 2)

            assertEquals(expected.x, out[2], 0f)
            assertEquals(expected.y, out[3], 0f)
        }
    }

    @Test
    fun inPlaceTransformDefaultsToTheStartOfTheArray() {
        val out = floatArrayOf(-1f, -1f, -1f)

        Mat2(tx = 5f, ty = 7f).transform(1f, 2f, out)

        assertEquals(6f, out[0], 0f)
        assertEquals(9f, out[1], 0f)
        assertEquals(-1f, out[2], 0f)
    }

    @Test
    fun skewXMovesPointsHorizontallyByTheirHeight() {
        val point = Mat2().apply { skew(30f, 0f) }.transform(0f, 10f)

        assertEquals(tan(PI / 6).toFloat() * 10f, point.x, 1e-4f)
        assertEquals(10f, point.y, 1e-4f)
    }

    @Test
    fun skewYMovesPointsVerticallyByTheirWidth() {
        val point = Mat2().apply { skew(0f, -30f) }.transform(10f, 0f)

        assertEquals(10f, point.x, 1e-4f)
        assertEquals(-tan(PI / 6).toFloat() * 10f, point.y, 1e-4f)
    }

    @Test
    fun skewIsAppliedAfterTheExistingTransformations() {
        // Translated first, so the skew moves the translation too.
        val point = Mat2().apply {
            translate(0f, 10f)
            skew(45f, 0f)
        }.transform(0f, 0f)

        assertEquals(10f, point.x, 1e-4f)
        assertEquals(10f, point.y, 1e-4f)
    }

    @Test
    fun skewIsInvertible() {
        val matrix = Mat2().apply {
            scale(2f, 3f)
            skew(-15f, 20f)
            rotate(30f)
            translate(5f, -7f)
        }
        val inverse = matrix.copy().apply { invert() }

        val point = matrix.transform(12f, 34f)
        val back = inverse.transform(point.x, point.y)

        assertEquals(12f, back.x, 1e-3f)
        assertEquals(34f, back.y, 1e-3f)
    }
}
