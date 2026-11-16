package com.nijatva.photo.editor.edit

import android.graphics.ColorMatrix

/** Normalised crop window (0..1) inside the transformed frame. */
data class CropRect(val l: Float = 0f, val t: Float = 0f, val r: Float = 1f, val b: Float = 1f) {
    val width: Float get() = (r - l).coerceAtLeast(0.02f)
    val height: Float get() = (b - t).coerceAtLeast(0.02f)
}

enum class MarkupMode { PEN, HIGHLIGHT, PIXELATE, BLUR, BLACKOUT, TEXT }

sealed interface Layer {
    val id: Long
}

/** A freehand stroke in normalised image coordinates. */
data class StrokeLayer(
    override val id: Long,
    val points: List<Pair<Float, Float>>,
    val mode: MarkupMode,
    val color: Int,
    val width: Float
) : Layer

data class TextLayer(
    override val id: Long,
    val text: String,
    val x: Float,
    val y: Float,
    val size: Float,
    val color: Int
) : Layer

/** A painted mask with its own local grade (Lightroom-style brush). */
data class LocalAdjustLayer(
    override val id: Long,
    val points: List<Pair<Float, Float>>,
    val width: Float,
    val exposure: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f
) : Layer {
    val isNoop: Boolean
        get() = exposure == 0f && contrast == 0f && saturation == 0f && warmth == 0f

    fun matrix(): ColorMatrix {
        val out = ColorMatrix()
        val e = 1f + exposure * 0.9f
        out.postConcat(
            ColorMatrix(
                floatArrayOf(
                    e, 0f, 0f, 0f, 0f,
                    0f, e, 0f, 0f, 0f,
                    0f, 0f, e, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
        val c = 1f + contrast
        val t = (1f - c) * 127.5f
        out.postConcat(
            ColorMatrix(
                floatArrayOf(
                    c, 0f, 0f, 0f, t,
                    0f, c, 0f, 0f, t,
                    0f, 0f, c, 0f, t,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
        if (saturation != 0f) {
            val sat = ColorMatrix()
            sat.setSaturation(1f + saturation)
            out.postConcat(sat)
        }
        if (warmth != 0f) {
            val w = warmth * 45f
            out.postConcat(
                ColorMatrix(
                    floatArrayOf(
                        1f, 0f, 0f, 0f, w,
                        0f, 1f, 0f, 0f, 0f,
                        0f, 0f, 1f, 0f, -w,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
        return out
    }
}

/** Selective hue / saturation / luminance for one colour range. */
data class HslShift(val hue: Float = 0f, val sat: Float = 0f, val lum: Float = 0f) {
    val isNeutral: Boolean get() = hue == 0f && sat == 0f && lum == 0f
}

val HSL_CENTERS = listOf(
    "Red" to 0f,
    "Orange" to 30f,
    "Yellow" to 60f,
    "Green" to 120f,
    "Aqua" to 180f,
    "Blue" to 240f,
    "Purple" to 285f,
    "Magenta" to 320f
)

val DEFAULT_CURVE = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)

data class EditState(
    val rotation: Int = 0,
    val flipH: Boolean = false,
    val flipV: Boolean = false,
    val straighten: Float = 0f,
    val crop: CropRect = CropRect(),
    val exposure: Float = 0f,
    val contrast: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val saturation: Float = 0f,
    val vibrance: Float = 0f,
    val warmth: Float = 0f,
    val tint: Float = 0f,
    val clarity: Float = 0f,
    val sharpen: Float = 0f,
    val grain: Float = 0f,
    val vignette: Float = 0f,
    val curve: List<Float> = DEFAULT_CURVE,
    val hsl: List<HslShift> = List(HSL_CENTERS.size) { HslShift() },
    val filter: FilterPreset = FilterPreset.NONE,
    val layers: List<Layer> = emptyList(),
    val localAdjusts: List<LocalAdjustLayer> = emptyList()
) {
    val isDefault: Boolean get() = this == EditState()

    val hasLocalEdits: Boolean get() = localAdjusts.isNotEmpty()

    val hasGeometry: Boolean
        get() = rotation != 0 || flipH || flipV || straighten != 0f || crop != CropRect()

    val needsPixelWork: Boolean
        get() = curve != DEFAULT_CURVE ||
            hsl.any { !it.isNeutral } ||
            highlights != 0f || shadows != 0f || vibrance != 0f ||
            clarity != 0f || sharpen != 0f || grain != 0f || vignette != 0f

    /** Linear part of the grade, applied with a ColorMatrix (fast path). */
    fun colorMatrix(): ColorMatrix {
        val out = ColorMatrix()
        filter.matrix()?.let { out.postConcat(it) }

        val e = 1f + exposure * 0.9f
        out.postConcat(
            ColorMatrix(
                floatArrayOf(
                    e, 0f, 0f, 0f, 0f,
                    0f, e, 0f, 0f, 0f,
                    0f, 0f, e, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )

        val c = 1f + contrast
        val t = (1f - c) * 127.5f
        out.postConcat(
            ColorMatrix(
                floatArrayOf(
                    c, 0f, 0f, 0f, t,
                    0f, c, 0f, 0f, t,
                    0f, 0f, c, 0f, t,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )

        if (saturation != 0f) {
            val s = ColorMatrix()
            s.setSaturation(1f + saturation)
            out.postConcat(s)
        }

        if (warmth != 0f || tint != 0f) {
            val w = warmth * 45f
            val ti = tint * 35f
            out.postConcat(
                ColorMatrix(
                    floatArrayOf(
                        1f, 0f, 0f, 0f, w + ti * 0.4f,
                        0f, 1f, 0f, 0f, -ti,
                        0f, 0f, 1f, 0f, -w + ti * 0.4f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
        return out
    }
}

enum class FilterPreset {
    NONE, MONO, NOIR, WARM, COOL, VIVID, FADE, SEPIA, CHROME, VINTAGE, MATTE, SUNSET, FOREST;

    fun matrix(): ColorMatrix? = when (this) {
        NONE -> null
        MONO -> ColorMatrix().apply { setSaturation(0f) }
        NOIR -> {
            val m = ColorMatrix(
                floatArrayOf(
                    1.55f, 0f, 0f, 0f, -42f,
                    0f, 1.55f, 0f, 0f, -42f,
                    0f, 0f, 1.55f, 0f, -42f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            val g = ColorMatrix()
            g.setSaturation(0f)
            m.postConcat(g)
            m
        }
        WARM -> ColorMatrix(
            floatArrayOf(
                1.12f, 0f, 0f, 0f, 12f,
                0f, 1.04f, 0f, 0f, 2f,
                0f, 0f, 0.92f, 0f, -8f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        COOL -> ColorMatrix(
            floatArrayOf(
                0.92f, 0f, 0f, 0f, -6f,
                0f, 1.02f, 0f, 0f, 2f,
                0f, 0f, 1.14f, 0f, 12f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        VIVID -> {
            val m = ColorMatrix(
                floatArrayOf(
                    1.14f, 0f, 0f, 0f, -14f,
                    0f, 1.14f, 0f, 0f, -14f,
                    0f, 0f, 1.14f, 0f, -14f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            val s = ColorMatrix()
            s.setSaturation(1.35f)
            m.postConcat(s)
            m
        }
        FADE -> {
            val m = ColorMatrix(
                floatArrayOf(
                    0.88f, 0f, 0f, 0f, 22f,
                    0f, 0.88f, 0f, 0f, 22f,
                    0f, 0f, 0.9f, 0f, 20f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            val s = ColorMatrix()
            s.setSaturation(0.85f)
            m.postConcat(s)
            m
        }
        SEPIA -> ColorMatrix(
            floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        CHROME -> {
            val m = ColorMatrix(
                floatArrayOf(
                    1.18f, 0f, 0f, 0f, -18f,
                    0f, 1.18f, 0f, 0f, -18f,
                    0f, 0f, 1.22f, 0f, -16f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            val sat = ColorMatrix()
            sat.setSaturation(1.25f)
            m.postConcat(sat)
            m
        }
        VINTAGE -> {
            val m = ColorMatrix(
                floatArrayOf(
                    0.95f, 0f, 0f, 0f, 24f,
                    0f, 0.9f, 0f, 0f, 18f,
                    0f, 0f, 0.82f, 0f, 8f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            val sat = ColorMatrix()
            sat.setSaturation(0.72f)
            m.postConcat(sat)
            m
        }
        MATTE -> {
            val m = ColorMatrix(
                floatArrayOf(
                    0.82f, 0f, 0f, 0f, 34f,
                    0f, 0.82f, 0f, 0f, 34f,
                    0f, 0f, 0.84f, 0f, 34f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            val sat = ColorMatrix()
            sat.setSaturation(0.8f)
            m.postConcat(sat)
            m
        }
        SUNSET -> {
            val m = ColorMatrix(
                floatArrayOf(
                    1.16f, 0f, 0f, 0f, 22f,
                    0f, 1.02f, 0f, 0f, 4f,
                    0f, 0f, 0.84f, 0f, -12f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            val sat = ColorMatrix()
            sat.setSaturation(1.15f)
            m.postConcat(sat)
            m
        }
        FOREST -> {
            val m = ColorMatrix(
                floatArrayOf(
                    0.9f, 0f, 0f, 0f, -6f,
                    0f, 1.12f, 0f, 0f, 6f,
                    0f, 0f, 0.94f, 0f, -4f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            val sat = ColorMatrix()
            sat.setSaturation(1.1f)
            m.postConcat(sat)
            m
        }
    }
}
