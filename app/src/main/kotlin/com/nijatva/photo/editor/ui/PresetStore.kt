package com.nijatva.photo.editor.ui

import android.content.Context
import com.nijatva.photo.editor.edit.DEFAULT_CURVE
import com.nijatva.photo.editor.edit.EditState
import com.nijatva.photo.editor.edit.FilterPreset
import com.nijatva.photo.editor.edit.HslShift
import org.json.JSONArray
import org.json.JSONObject

/** A saved look: the colour grade only — geometry, markup and masks stay out of it. */
data class LookPreset(val name: String, val json: String)

/**
 * Presets live in SharedPreferences as one JSON array. No database, no files,
 * nothing leaves the device — the same promise the rest of the app keeps.
 */
object PresetStore {

    private const val PREFS = "nijatva_presets"
    private const val KEY = "looks"
    private const val MAX = 40

    fun load(context: Context): List<LookPreset> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val name = o.optString("name")
                val json = o.optJSONObject("state")?.toString()
                if (name.isBlank() || json == null) null else LookPreset(name, json)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(context: Context, presets: List<LookPreset>) {
        val arr = JSONArray()
        presets.take(MAX).forEach { p ->
            arr.put(
                JSONObject().apply {
                    put("name", p.name)
                    put("state", JSONObject(p.json))
                }
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, arr.toString())
            .apply()
    }

    /** Adds or replaces a preset with the same name, newest first. */
    fun upsert(context: Context, name: String, state: EditState): List<LookPreset> {
        val next = listOf(LookPreset(name.trim(), encode(state))) +
            load(context).filter { !it.name.equals(name.trim(), ignoreCase = true) }
        save(context, next)
        return load(context)
    }

    fun delete(context: Context, name: String): List<LookPreset> {
        save(context, load(context).filter { it.name != name })
        return load(context)
    }

    fun encode(state: EditState): String = JSONObject().apply {
        put("exposure", state.exposure.toDouble())
        put("contrast", state.contrast.toDouble())
        put("highlights", state.highlights.toDouble())
        put("shadows", state.shadows.toDouble())
        put("saturation", state.saturation.toDouble())
        put("vibrance", state.vibrance.toDouble())
        put("warmth", state.warmth.toDouble())
        put("tint", state.tint.toDouble())
        put("clarity", state.clarity.toDouble())
        put("sharpen", state.sharpen.toDouble())
        put("grain", state.grain.toDouble())
        put("vignette", state.vignette.toDouble())
        put("filter", state.filter.name)
        put("curve", JSONArray(state.curve))
        put("curveR", JSONArray(state.curveR))
        put("curveG", JSONArray(state.curveG))
        put("curveB", JSONArray(state.curveB))
        put(
            "hsl",
            JSONArray().apply {
                state.hsl.forEach { h ->
                    put(
                        JSONObject().apply {
                            put("h", h.hue.toDouble())
                            put("s", h.sat.toDouble())
                            put("l", h.lum.toDouble())
                        }
                    )
                }
            }
        )
    }.toString()

    /** Applies a stored look on top of the current state, keeping geometry and layers. */
    fun apply(json: String, base: EditState): EditState {
        val o = try {
            JSONObject(json)
        } catch (e: Exception) {
            return base
        }
        fun f(key: String, fallback: Float) = o.optDouble(key, fallback.toDouble()).toFloat()
        fun curve(key: String, fallback: List<Float>): List<Float> {
            val arr = o.optJSONArray(key) ?: return fallback
            if (arr.length() < 2) return fallback
            return (0 until arr.length()).map { arr.optDouble(it, 0.0).toFloat() }
        }
        val hsl = o.optJSONArray("hsl")?.let { arr ->
            (0 until arr.length()).map { i ->
                val h = arr.optJSONObject(i)
                HslShift(
                    hue = h?.optDouble("h", 0.0)?.toFloat() ?: 0f,
                    sat = h?.optDouble("s", 0.0)?.toFloat() ?: 0f,
                    lum = h?.optDouble("l", 0.0)?.toFloat() ?: 0f
                )
            }
        } ?: base.hsl
        val filter = FilterPreset.entries.firstOrNull { it.name == o.optString("filter") }
            ?: FilterPreset.NONE
        return base.copy(
            exposure = f("exposure", base.exposure),
            contrast = f("contrast", base.contrast),
            highlights = f("highlights", base.highlights),
            shadows = f("shadows", base.shadows),
            saturation = f("saturation", base.saturation),
            vibrance = f("vibrance", base.vibrance),
            warmth = f("warmth", base.warmth),
            tint = f("tint", base.tint),
            clarity = f("clarity", base.clarity),
            sharpen = f("sharpen", base.sharpen),
            grain = f("grain", base.grain),
            vignette = f("vignette", base.vignette),
            filter = filter,
            curve = curve("curve", DEFAULT_CURVE),
            curveR = curve("curveR", DEFAULT_CURVE),
            curveG = curve("curveG", DEFAULT_CURVE),
            curveB = curve("curveB", DEFAULT_CURVE),
            hsl = hsl
        )
    }
}
