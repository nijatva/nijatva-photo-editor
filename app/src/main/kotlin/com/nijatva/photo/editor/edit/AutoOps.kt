package com.nijatva.photo.editor.edit

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt

/**
 * The three "make it look right" buttons, done with plain maths — no model, no
 * network, nothing downloaded:
 *
 *  * tone      — percentile stretch + highlight/shadow recovery from the histogram
 *  * white balance — grey-world assumption on the mid-tones
 *  * straighten — dominant near-horizontal edge angle from a Sobel pass
 */
object AutoOps {

    data class Suggestion(
        val exposure: Float = 0f,
        val contrast: Float = 0f,
        val highlights: Float = 0f,
        val shadows: Float = 0f,
        val warmth: Float = 0f,
        val straighten: Float = 0f
    )

    private const val MAX_EDGE = 480

    /** Runs on a small copy, so it stays instant even on a 50 MP source. */
    fun analyse(source: Bitmap): Suggestion {
        val sample = downscale(source)
        val w = sample.width
        val h = sample.height
        val px = IntArray(w * h)
        sample.getPixels(px, 0, w, 0, 0, w, h)

        val histogram = IntArray(256)
        var sumR = 0.0
        var sumG = 0.0
        var sumB = 0.0
        var midCount = 0

        for (c in px) {
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val lum = ((r * 299 + g * 587 + b * 114) / 1000).coerceIn(0, 255)
            histogram[lum]++
            // grey-world on the mid-tones only: ignore crushed blacks and blown whites
            if (lum in 40..215) {
                sumR += r
                sumG += g
                sumB += b
                midCount++
            }
        }

        val total = px.size.coerceAtLeast(1)
        val p01 = percentile(histogram, total, 0.01)
        val p05 = percentile(histogram, total, 0.05)
        val p50 = percentile(histogram, total, 0.50)
        val p95 = percentile(histogram, total, 0.95)
        val p99 = percentile(histogram, total, 0.99)

        // tone: pull the median towards the middle, open up a flat histogram
        val exposure = ((0.46f - p50 / 255f) * 1.5f).coerceIn(-0.45f, 0.45f)
        val spread = (p95 - p05) / 255f
        val contrast = ((0.82f - spread) * 0.9f).coerceIn(-0.25f, 0.40f)
        val highlights = if (p99 > 246) -0.25f else 0f
        val shadows = if (p01 < 10) 0.22f else 0f

        // white balance: if the mid-tones lean blue, warm them up and vice versa
        var warmth = 0f
        if (midCount > 0) {
            val avgR = sumR / midCount
            val avgG = sumG / midCount
            val avgB = sumB / midCount
            val rb = ((avgR - avgB) / 255.0).toFloat() // positive = already warm
            warmth = (-rb * 1.8f).coerceIn(-0.45f, 0.45f)
            if (avgG <= 0.0) warmth = 0f
        }

        return Suggestion(
            exposure = exposure,
            contrast = contrast,
            highlights = highlights,
            shadows = shadows,
            warmth = warmth,
            straighten = dominantTilt(px, w, h)
        )
    }

    /** Angle in degrees to feed `EditState.straighten`, 0 when nothing is level-ish. */
    private fun dominantTilt(px: IntArray, w: Int, h: Int): Float {
        if (w < 32 || h < 32) return 0f
        val grey = FloatArray(w * h)
        for (i in px.indices) {
            val c = px[i]
            grey[i] = ((c shr 16 and 0xFF) * 0.299f + (c shr 8 and 0xFF) * 0.587f + (c and 0xFF) * 0.114f)
        }
        val bins = FloatArray(31) // -15..15 degrees, 1 degree each
        var weight = 0f
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                val gx = grey[i + 1] - grey[i - 1]
                val gy = grey[i + w] - grey[i - w]
                val mag = abs(gx) + abs(gy)
                if (mag < 40f) continue // flat area, no edge to vote with
                if (abs(gy) < abs(gx) * 1.6f) continue // too vertical to be a horizon
                val angle = Math.toDegrees(atan2(gy.toDouble(), gx.toDouble())).toFloat()
                var tilt = when {
                    angle > 90f -> angle - 180f
                    angle < -90f -> angle + 180f
                    else -> angle
                }
                if (tilt > 15f || tilt < -15f) continue
                val bucket = (tilt + 15f).roundToInt().coerceIn(0, 30)
                bins[bucket] += mag
                weight += mag
            }
        }
        if (weight < 4000f) return 0f // not enough structure; leave it alone
        var best = 0
        for (i in bins.indices) if (bins[i] > bins[best]) best = i
        val peak = bins[best]
        // only trust the peak when it clearly beats the rest
        if (peak < weight * 0.18f) return 0f
        return -(best - 15).toFloat()
    }

    private fun percentile(histogram: IntArray, total: Int, fraction: Double): Int {
        val target = (total * fraction).toInt().coerceAtLeast(1)
        var seen = 0
        for (i in histogram.indices) {
            seen += histogram[i]
            if (seen >= target) return i
        }
        return 255
    }

    private fun downscale(source: Bitmap): Bitmap {
        val longest = maxOf(source.width, source.height)
        if (longest <= MAX_EDGE) return source
        val scale = MAX_EDGE.toFloat() / longest
        val w = (source.width * scale).toInt().coerceAtLeast(1)
        val h = (source.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, w, h, true)
    }
}
