package com.nijatva.photo.editor.edit

import android.graphics.Bitmap
import android.graphics.ColorMatrixColorFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Typeface
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Everything a ColorMatrix cannot express: tone curve, selective HSL,
 * highlights/shadows, vibrance, local contrast, sharpening, vignette, grain —
 * plus the markup layer stack (pen, highlighter, pixelate, blur, blackout, text).
 */
object AdvancedOps {

    // ---------------------------------------------------------------- colour

    fun pixelPass(src: Bitmap, state: EditState): Bitmap {
        val w = src.width
        val h = src.height
        if (w < 2 || h < 2) return src

        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)

        if (state.clarity != 0f || state.sharpen != 0f) {
            unsharp(px, w, h, state.clarity, state.sharpen)
        }

        val lut = buildCurveLut(state.curve)
        val hsl = state.hsl
        val anyHsl = hsl.any { !it.isNeutral }
        val invDiag = 1f / sqrt((w * w + h * h).toFloat())

        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                val c = px[i]
                var r = ((c shr 16) and 0xFF).toFloat()
                var g = ((c shr 8) and 0xFF).toFloat()
                var b = (c and 0xFF).toFloat()

                if (lut != null) {
                    r = lut[r.toInt()].toFloat()
                    g = lut[g.toInt()].toFloat()
                    b = lut[b.toInt()].toFloat()
                }

                val lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255f

                if (state.shadows != 0f) {
                    val f = state.shadows * (1f - lum) * (1f - lum) * 110f
                    r += f; g += f; b += f
                }
                if (state.highlights != 0f) {
                    val f = state.highlights * lum * lum * 110f
                    r += f; g += f; b += f
                }

                if (state.vibrance != 0f) {
                    val mx = max(r, max(g, b))
                    val mn = min(r, min(g, b))
                    val sat = if (mx <= 0f) 0f else (mx - mn) / 255f
                    val amt = state.vibrance * (1f - sat) * 1.1f
                    val avg = (r + g + b) / 3f
                    r += (r - avg) * amt
                    g += (g - avg) * amt
                    b += (b - avg) * amt
                }

                if (anyHsl) {
                    val mx = max(r, max(g, b))
                    val mn = min(r, min(g, b))
                    val delta = mx - mn
                    if (delta > 1f) {
                        var hue = when {
                            mx == r -> 60f * (((g - b) / delta) % 6f)
                            mx == g -> 60f * (((b - r) / delta) + 2f)
                            else -> 60f * (((r - g) / delta) + 4f)
                        }
                        if (hue < 0f) hue += 360f
                        var sat = if (mx <= 0f) 0f else delta / mx
                        var v = mx / 255f

                        var dHue = 0f
                        var sScale = 1f
                        var vScale = 1f
                        var wSum = 0f
                        for (ci in hsl.indices) {
                            val shift = hsl[ci]
                            if (shift.isNeutral) continue
                            val weight = bandWeight(hue, HSL_CENTERS[ci].second)
                            if (weight <= 0f) continue
                            wSum += weight
                            dHue += weight * shift.hue * 30f
                            sScale += weight * shift.sat
                            vScale += weight * shift.lum
                        }
                        if (wSum > 0f) {
                            hue = (hue + dHue / wSum + 360f) % 360f
                            sat = (sat * sScale).coerceIn(0f, 1f)
                            v = (v * vScale).coerceIn(0f, 1f)
                            val rgb = hsvToRgb(hue, sat, v)
                            r = rgb[0]; g = rgb[1]; b = rgb[2]
                        }
                    }
                }

                if (state.vignette != 0f) {
                    val dx = (x - w / 2f) * invDiag * 2f
                    val dy = (y - h / 2f) * invDiag * 2f
                    val d = (dx * dx + dy * dy).coerceIn(0f, 1f)
                    val f = 1f + state.vignette * d * 0.95f
                    r *= f; g *= f; b *= f
                }

                if (state.grain != 0f) {
                    val n = (((x * 73856093) xor (y * 19349663)) and 0xFF) - 128
                    val f = n * state.grain * 0.7f
                    r += f; g += f; b += f
                }

                px[i] = (0xFF shl 24) or
                    (clamp255(r) shl 16) or
                    (clamp255(g) shl 8) or
                    clamp255(b)
            }
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    private fun clamp255(v: Float): Int = when {
        v <= 0f -> 0
        v >= 255f -> 255
        else -> v.toInt()
    }

    private fun bandWeight(hue: Float, center: Float): Float {
        var d = abs(hue - center)
        if (d > 180f) d = 360f - d
        return (1f - d / 45f).coerceIn(0f, 1f)
    }

    private fun hsvToRgb(h: Float, s: Float, v: Float): FloatArray {
        val c = v * s
        val x = c * (1f - abs(((h / 60f) % 2f) - 1f))
        val m = v - c
        val r1: Float
        val g1: Float
        val b1: Float
        when {
            h < 60f -> { r1 = c; g1 = x; b1 = 0f }
            h < 120f -> { r1 = x; g1 = c; b1 = 0f }
            h < 180f -> { r1 = 0f; g1 = c; b1 = x }
            h < 240f -> { r1 = 0f; g1 = x; b1 = c }
            h < 300f -> { r1 = x; g1 = 0f; b1 = c }
            else -> { r1 = c; g1 = 0f; b1 = x }
        }
        return floatArrayOf((r1 + m) * 255f, (g1 + m) * 255f, (b1 + m) * 255f)
    }

    fun buildCurveLut(points: List<Float>): IntArray? {
        if (points == DEFAULT_CURVE || points.size < 2) return null
        val lut = IntArray(256)
        val n = points.size
        for (i in 0 until 256) {
            val t = i / 255f * (n - 1)
            val idx = t.toInt().coerceIn(0, n - 2)
            val f = t - idx
            val p0 = points[(idx - 1).coerceAtLeast(0)]
            val p1 = points[idx]
            val p2 = points[idx + 1]
            val p3 = points[(idx + 2).coerceAtMost(n - 1)]
            val v = catmullRom(p0, p1, p2, p3, f)
            lut[i] = (v.coerceIn(0f, 1f) * 255f).toInt()
        }
        return lut
    }

    private fun catmullRom(p0: Float, p1: Float, p2: Float, p3: Float, t: Float): Float {
        val t2 = t * t
        val t3 = t2 * t
        return 0.5f * (
            (2f * p1) +
                (-p0 + p2) * t +
                (2f * p0 - 5f * p1 + 4f * p2 - p3) * t2 +
                (-p0 + 3f * p1 - 3f * p2 + p3) * t3
            )
    }

    // ------------------------------------------------------------- unsharp

    private fun unsharp(px: IntArray, w: Int, h: Int, clarity: Float, sharpen: Float) {
        if (clarity != 0f) {
            val radius = max(3, min(w, h) / 70)
            blend(px, boxBlur(px, w, h, radius), clarity * 1.5f)
        }
        if (sharpen != 0f) {
            blend(px, boxBlur(px, w, h, 1), sharpen * 2.0f)
        }
    }

    private fun blend(px: IntArray, blur: IntArray, amount: Float) {
        for (i in px.indices) {
            val c = px[i]
            val b = blur[i]
            val r = ((c shr 16) and 0xFF) + (((c shr 16) and 0xFF) - ((b shr 16) and 0xFF)) * amount
            val g = ((c shr 8) and 0xFF) + (((c shr 8) and 0xFF) - ((b shr 8) and 0xFF)) * amount
            val bl = (c and 0xFF) + ((c and 0xFF) - (b and 0xFF)) * amount
            px[i] = (0xFF shl 24) or (clamp255(r) shl 16) or (clamp255(g) shl 8) or clamp255(bl)
        }
    }

    /** Separable box blur on packed ARGB pixels. */
    private fun boxBlur(src: IntArray, w: Int, h: Int, radius: Int): IntArray {
        val tmp = IntArray(w * h)
        val out = IntArray(w * h)
        val span = radius * 2 + 1

        for (y in 0 until h) {
            var sr = 0
            var sg = 0
            var sb = 0
            val row = y * w
            for (i in -radius..radius) {
                val xx = i.coerceIn(0, w - 1)
                val c = src[row + xx]
                sr += (c shr 16) and 0xFF
                sg += (c shr 8) and 0xFF
                sb += c and 0xFF
            }
            for (x in 0 until w) {
                tmp[row + x] = (0xFF shl 24) or
                    ((sr / span) shl 16) or ((sg / span) shl 8) or (sb / span)
                val addX = (x + radius + 1).coerceAtMost(w - 1)
                val remX = (x - radius).coerceAtLeast(0)
                val add = src[row + addX]
                val rem = src[row + remX]
                sr += ((add shr 16) and 0xFF) - ((rem shr 16) and 0xFF)
                sg += ((add shr 8) and 0xFF) - ((rem shr 8) and 0xFF)
                sb += (add and 0xFF) - (rem and 0xFF)
            }
        }

        for (x in 0 until w) {
            var sr = 0
            var sg = 0
            var sb = 0
            for (i in -radius..radius) {
                val yy = i.coerceIn(0, h - 1)
                val c = tmp[yy * w + x]
                sr += (c shr 16) and 0xFF
                sg += (c shr 8) and 0xFF
                sb += c and 0xFF
            }
            for (y in 0 until h) {
                out[y * w + x] = (0xFF shl 24) or
                    ((sr / span) shl 16) or ((sg / span) shl 8) or (sb / span)
                val addY = (y + radius + 1).coerceAtMost(h - 1)
                val remY = (y - radius).coerceAtLeast(0)
                val add = tmp[addY * w + x]
                val rem = tmp[remY * w + x]
                sr += ((add shr 16) and 0xFF) - ((rem shr 16) and 0xFF)
                sg += ((add shr 8) and 0xFF) - ((rem shr 8) and 0xFF)
                sb += (add and 0xFF) - (rem and 0xFF)
            }
        }
        return out
    }

    // -------------------------------------------------------------- layers

    fun drawLayers(src: Bitmap, layers: List<Layer>): Bitmap {
        if (layers.isEmpty()) return src
        val out = src.copy(Bitmap.Config.ARGB_8888, true) ?: return src
        val canvas = Canvas(out)
        val w = out.width.toFloat()
        val h = out.height.toFloat()

        for (layer in layers) {
            when (layer) {
                is StrokeLayer -> drawStroke(canvas, out, layer, w, h)
                is TextLayer -> drawText(canvas, layer, w, h)
                is LocalAdjustLayer -> Unit // applied earlier by applyLocal()
            }
        }
        return out
    }

    private fun buildPath(layer: StrokeLayer, w: Float, h: Float): Path {
        val path = Path()
        val pts = layer.points
        if (pts.isEmpty()) return path
        path.moveTo(pts[0].first * w, pts[0].second * h)
        if (pts.size == 1) {
            path.lineTo(pts[0].first * w + 0.5f, pts[0].second * h + 0.5f)
        } else {
            for (i in 1 until pts.size) {
                path.lineTo(pts[i].first * w, pts[i].second * h)
            }
        }
        return path
    }

    private fun strokeWidthPx(layer: StrokeLayer, w: Float, h: Float): Float =
        (layer.width * min(w, h)).coerceAtLeast(2f)

    private fun drawStroke(canvas: Canvas, src: Bitmap, layer: StrokeLayer, w: Float, h: Float) {
        val path = buildPath(layer, w, h)
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeWidthPx(layer, w, h)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        when (layer.mode) {
            MarkupMode.PEN -> {
                strokePaint.color = layer.color
                canvas.drawPath(path, strokePaint)
            }
            MarkupMode.HIGHLIGHT -> {
                strokePaint.color = Color.argb(
                    90,
                    Color.red(layer.color),
                    Color.green(layer.color),
                    Color.blue(layer.color)
                )
                strokePaint.strokeWidth = strokeWidthPx(layer, w, h) * 2.2f
                canvas.drawPath(path, strokePaint)
            }
            MarkupMode.BLACKOUT -> {
                strokePaint.color = Color.BLACK
                canvas.drawPath(path, strokePaint)
            }
            MarkupMode.PIXELATE, MarkupMode.BLUR -> {
                val outline = Path()
                strokePaint.strokeWidth = strokeWidthPx(layer, w, h) * 1.6f
                if (!strokePaint.getFillPath(path, outline)) return
                val bounds = RectF()
                outline.computeBounds(bounds, true)
                val rect = Rect(
                    bounds.left.toInt().coerceIn(0, src.width - 1),
                    bounds.top.toInt().coerceIn(0, src.height - 1),
                    bounds.right.toInt().coerceIn(1, src.width),
                    bounds.bottom.toInt().coerceIn(1, src.height)
                )
                if (rect.width() < 3 || rect.height() < 3) return
                val patch = Bitmap.createBitmap(src, rect.left, rect.top, rect.width(), rect.height())
                val effect = if (layer.mode == MarkupMode.PIXELATE) pixelate(patch) else soften(patch)
                canvas.save()
                canvas.clipPath(outline)
                canvas.drawBitmap(
                    effect,
                    Rect(0, 0, effect.width, effect.height),
                    RectF(rect),
                    Paint(Paint.FILTER_BITMAP_FLAG)
                )
                canvas.restore()
            }
            MarkupMode.TEXT -> Unit
        }
    }

    private fun pixelate(src: Bitmap): Bitmap {
        val block = max(6, min(src.width, src.height) / 14)
        val sw = max(1, src.width / block)
        val sh = max(1, src.height / block)
        val small = Bitmap.createScaledBitmap(src, sw, sh, false)
        return Bitmap.createScaledBitmap(small, src.width, src.height, false)
    }

    private fun soften(src: Bitmap): Bitmap {
        val f = 12
        val sw = max(1, src.width / f)
        val sh = max(1, src.height / f)
        val small = Bitmap.createScaledBitmap(src, sw, sh, true)
        return Bitmap.createScaledBitmap(small, src.width, src.height, true)
    }

    private fun drawText(canvas: Canvas, layer: TextLayer, w: Float, h: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = layer.color
            textSize = (layer.size * h).coerceAtLeast(10f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(4f, 0f, 2f, Color.argb(140, 0, 0, 0))
        }
        val lines = layer.text.split("\n")
        val lineHeight = paint.textSize * 1.2f
        var baseline = layer.y * h
        for (line in lines) {
            canvas.drawText(line, layer.x * w, baseline, paint)
            baseline += lineHeight
        }
    }

    // -------------------------------------------------------- local masks

    /**
     * Applies each painted mask's own grade only where the user brushed.
     * Mask is feathered, then blended per pixel: out = base + (graded - base) * mask.
     */
    fun applyLocal(src: Bitmap, layers: List<LocalAdjustLayer>): Bitmap {
        var out = src
        for (layer in layers) {
            if (layer.isNoop || layer.points.size < 2) continue
            out = applyOneLocal(out, layer)
        }
        return out
    }

    private fun applyOneLocal(src: Bitmap, layer: LocalAdjustLayer): Bitmap {
        val w = src.width
        val h = src.height
        if (w < 4 || h < 4) return src

        // 1. mask (white stroke on transparent), then feather by down/up scaling
        val maskRaw = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val maskCanvas = Canvas(maskRaw)
        val brush = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = (layer.width * min(w, h)).coerceAtLeast(2f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val path = Path()
        path.moveTo(layer.points[0].first * w, layer.points[0].second * h)
        for (i in 1 until layer.points.size) {
            path.lineTo(layer.points[i].first * w, layer.points[i].second * h)
        }
        maskCanvas.drawPath(path, brush)

        val small = Bitmap.createScaledBitmap(maskRaw, max(1, w / 12), max(1, h / 12), true)
        val mask = Bitmap.createScaledBitmap(small, w, h, true)

        // 2. graded copy of the whole frame
        val graded = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val gradedCanvas = Canvas(graded)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        paint.colorFilter = ColorMatrixColorFilter(layer.matrix())
        gradedCanvas.drawBitmap(src, 0f, 0f, paint)

        // 3. blend through the mask
        val basePx = IntArray(w * h)
        val gradedPx = IntArray(w * h)
        val maskPx = IntArray(w * h)
        src.getPixels(basePx, 0, w, 0, 0, w, h)
        graded.getPixels(gradedPx, 0, w, 0, 0, w, h)
        mask.getPixels(maskPx, 0, w, 0, 0, w, h)

        for (i in basePx.indices) {
            val a = (maskPx[i] ushr 24) and 0xFF
            if (a == 0) continue
            val f = a / 255f
            val b = basePx[i]
            val g = gradedPx[i]
            val r = ((b shr 16) and 0xFF) + (((g shr 16) and 0xFF) - ((b shr 16) and 0xFF)) * f
            val gg = ((b shr 8) and 0xFF) + (((g shr 8) and 0xFF) - ((b shr 8) and 0xFF)) * f
            val bb = (b and 0xFF) + ((g and 0xFF) - (b and 0xFF)) * f
            basePx[i] = (0xFF shl 24) or (clamp255(r) shl 16) or (clamp255(gg) shl 8) or clamp255(bb)
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(basePx, 0, w, 0, 0, w, h)
        return out
    }

    // ----------------------------------------------------------- histogram

    fun histogram(src: Bitmap, bins: Int = 64): Array<IntArray> {
        val r = IntArray(bins)
        val g = IntArray(bins)
        val b = IntArray(bins)
        val w = src.width
        val h = src.height
        val stepX = max(1, w / 220)
        val stepY = max(1, h / 220)
        val scale = bins / 256f
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val c = src.getPixel(x, y)
                r[(((c shr 16) and 0xFF) * scale).toInt().coerceIn(0, bins - 1)]++
                g[(((c shr 8) and 0xFF) * scale).toInt().coerceIn(0, bins - 1)]++
                b[((c and 0xFF) * scale).toInt().coerceIn(0, bins - 1)]++
                x += stepX
            }
            y += stepY
        }
        return arrayOf(r, g, b)
    }
}
