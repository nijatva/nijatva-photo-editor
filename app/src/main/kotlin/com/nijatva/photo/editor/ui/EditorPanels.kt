package com.nijatva.photo.editor.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.nijatva.photo.editor.R
import com.nijatva.photo.editor.edit.AdvancedOps
import com.nijatva.photo.editor.edit.CropRect
import com.nijatva.photo.editor.edit.DEFAULT_CURVE
import com.nijatva.photo.editor.edit.EditState
import com.nijatva.photo.editor.edit.ExifSummary
import com.nijatva.photo.editor.edit.Exporter
import com.nijatva.photo.editor.edit.FilterPreset
import com.nijatva.photo.editor.edit.HSL_CENTERS
import com.nijatva.photo.editor.edit.HslShift
import com.nijatva.photo.editor.edit.ImageLoader
import com.nijatva.photo.editor.edit.LocalAdjustLayer
import com.nijatva.photo.editor.edit.MaskKind
import com.nijatva.photo.editor.edit.MarkupMode
import com.nijatva.photo.editor.edit.Pipeline
import com.nijatva.photo.editor.edit.StrokeLayer
import com.nijatva.photo.editor.edit.TextLayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.roundToInt

// Panels, overlays and mask tools for the editor.

// ------------------------------------------------------------------ panels

@Composable
fun ValueSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    onBegin: () -> Unit,
    onEnd: () -> Unit,
    defaultValue: Float = 0f
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    onBegin()
                    onChange(defaultValue)
                    onEnd()
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = TextSecondary, fontSize = 11.sp)
            Text(
                text = (value * 100f).roundToInt().toString(),
                color = if (value == 0f) TextSecondary else NijatvaTeal,
                fontSize = 11.sp
            )
        }
        Slider(
            value = value,
            onValueChange = { v ->
                onBegin()
                onChange(v)
            },
            onValueChangeFinished = { onEnd() },
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = NijatvaTeal,
                activeTrackColor = NijatvaTeal,
                inactiveTrackColor = Color(0xFF2A2A2E)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
        )
    }
}

@Composable
fun AdjustPanel(
    state: EditState,
    onLive: (EditState) -> Unit,
    onBegin: () -> Unit,
    onEnd: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        ValueSlider(stringResource(R.string.exposure), state.exposure, -1f..1f,
            { onLive(state.copy(exposure = it)) }, onBegin, onEnd)
        ValueSlider(stringResource(R.string.contrast), state.contrast, -1f..1f,
            { onLive(state.copy(contrast = it)) }, onBegin, onEnd)
        ValueSlider(stringResource(R.string.highlights), state.highlights, -1f..1f,
            { onLive(state.copy(highlights = it)) }, onBegin, onEnd)
        ValueSlider(stringResource(R.string.shadows), state.shadows, -1f..1f,
            { onLive(state.copy(shadows = it)) }, onBegin, onEnd)
        ValueSlider(stringResource(R.string.saturation), state.saturation, -1f..1f,
            { onLive(state.copy(saturation = it)) }, onBegin, onEnd)
        ValueSlider(stringResource(R.string.vibrance), state.vibrance, -1f..1f,
            { onLive(state.copy(vibrance = it)) }, onBegin, onEnd)
        ValueSlider(stringResource(R.string.warmth), state.warmth, -1f..1f,
            { onLive(state.copy(warmth = it)) }, onBegin, onEnd)
        ValueSlider(stringResource(R.string.tint), state.tint, -1f..1f,
            { onLive(state.copy(tint = it)) }, onBegin, onEnd)
        TextButton(onClick = { onLive(EditState()) }) {
            Text(stringResource(R.string.reset), color = TextSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
fun DetailPanel(
    state: EditState,
    onLive: (EditState) -> Unit,
    onBegin: () -> Unit,
    onEnd: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        ValueSlider(stringResource(R.string.clarity), state.clarity, -1f..1f,
            { onLive(state.copy(clarity = it)) }, onBegin, onEnd)
        ValueSlider(stringResource(R.string.sharpen), state.sharpen, 0f..1f,
            { onLive(state.copy(sharpen = it)) }, onBegin, onEnd)
        ValueSlider(stringResource(R.string.vignette), state.vignette, -1f..1f,
            { onLive(state.copy(vignette = it)) }, onBegin, onEnd)
        ValueSlider(stringResource(R.string.grain), state.grain, 0f..1f,
            { onLive(state.copy(grain = it)) }, onBegin, onEnd)
        Text(
            text = stringResource(R.string.markup_hint),
            color = Color(0xFF55555A),
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
fun CropPanel(
    state: EditState,
    onApply: (EditState) -> Unit,
    onLive: (EditState) -> Unit,
    onBegin: () -> Unit,
    onEnd: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(stringResource(R.string.rotate_left), false) {
                onApply(state.copy(rotation = (state.rotation + 270) % 360))
            }
            Chip(stringResource(R.string.rotate_right), false) {
                onApply(state.copy(rotation = (state.rotation + 90) % 360))
            }
            Chip(stringResource(R.string.flip_h), state.flipH) {
                onApply(state.copy(flipH = !state.flipH))
            }
            Chip(stringResource(R.string.flip_v), state.flipV) {
                onApply(state.copy(flipV = !state.flipV))
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Chip(stringResource(R.string.aspect_free), state.crop == CropRect()) {
                onApply(state.copy(crop = CropRect()))
            }
            Chip("1:1", false) { onApply(state.copy(crop = centered(1f, state.crop))) }
            Chip("4:5", false) { onApply(state.copy(crop = centered(0.8f, state.crop))) }
            Chip("16:9", false) { onApply(state.copy(crop = centered(16f / 9f, state.crop))) }
            Chip("3:2", false) { onApply(state.copy(crop = centered(1.5f, state.crop))) }
            Chip(stringResource(R.string.reset), false) { onApply(EditState()) }
        }
        Spacer(Modifier.height(8.dp))
        ValueSlider(stringResource(R.string.straighten), state.straighten, -0.45f..0.45f,
            { onLive(state.copy(straighten = it)) }, onBegin, onEnd)
        Text(
            text = stringResource(R.string.crop_hint),
            color = Color(0xFF55555A),
            fontSize = 10.sp
        )
    }
}

fun centered(aspect: Float, current: CropRect): CropRect {
    val w = current.width
    val h = current.height
    val target = aspect
    var nw = w
    var nh = w / target
    if (nh > h) {
        nh = h
        nw = h * target
    }
    val cx = (current.l + current.r) / 2f
    val cy = (current.t + current.b) / 2f
    val l = (cx - nw / 2f).coerceIn(0f, 1f - nw)
    val t = (cy - nh / 2f).coerceIn(0f, 1f - nh)
    return CropRect(l, t, l + nw, t + nh)
}

@Composable
fun CurvePanel(
    curve: List<Float>,
    channel: Int,
    onChannel: (Int) -> Unit,
    onLive: (List<Float>) -> Unit,
    onBegin: () -> Unit,
    onEnd: () -> Unit,
    onReset: () -> Unit
) {
    val latest = rememberUpdatedState(curve)
    var dragIdx by remember { mutableStateOf(-1) }
    val curveTint = when (channel) {
        1 -> Color(0xFFFF5252)
        2 -> Color(0xFF69F0AE)
        3 -> Color(0xFF448AFF)
        else -> NijatvaTeal
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Chip("Master", channel == 0) { onChannel(0) }
            Chip("Red", channel == 1) { onChannel(1) }
            Chip("Green", channel == 2) { onChannel(2) }
            Chip("Blue", channel == 3) { onChannel(3) }
        }
        Spacer(Modifier.height(6.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(Color(0xFF0A0A0A), RoundedCornerShape(10.dp))
                .border(1.dp, Color(0xFF262629), RoundedCornerShape(10.dp))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { pos ->
                            onBegin()
                            val pts = latest.value
                            val px = pos.x / size.width
                            dragIdx = ((px * (pts.size - 1)).roundToInt()).coerceIn(0, pts.size - 1)
                        },
                        onDrag = { change, drag ->
                            val pts = latest.value
                            if (dragIdx in pts.indices) {
                                val next = pts.toMutableList()
                                next[dragIdx] =
                                    (next[dragIdx] - drag.y / size.height).coerceIn(0f, 1f)
                                onLive(next)
                            }
                            change.consume()
                        },
                        onDragEnd = { dragIdx = -1; onEnd() },
                        onDragCancel = { dragIdx = -1; onEnd() }
                    )
                }
        ) {
            val grid = Color(0xFF2A2A2E)
            for (i in 1..3) {
                drawLine(grid, Offset(size.width * i / 4f, 0f), Offset(size.width * i / 4f, size.height), 1f)
                drawLine(grid, Offset(0f, size.height * i / 4f), Offset(size.width, size.height * i / 4f), 1f)
            }
            val lut = AdvancedOps.buildCurveLut(curve) ?: IntArray(256) { it }
            val path = Path()
            for (i in 0 until 256 step 2) {
                val x = size.width * i / 255f
                val y = size.height * (1f - lut[i] / 255f)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, curveTint, style = Stroke(2f))
            curve.forEachIndexed { i, v ->
                val x = size.width * i / (curve.size - 1).toFloat()
                val y = size.height * (1f - v)
                drawCircle(Color.White, 7f, Offset(x, y))
                drawCircle(curveTint, 4f, Offset(x, y))
            }
        }
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = onReset) {
            Text(stringResource(R.string.curve_reset), color = TextSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
fun HslPanel(
    hsl: List<HslShift>,
    channel: Int,
    onChannel: (Int) -> Unit,
    onLive: (List<HslShift>) -> Unit,
    onBegin: () -> Unit,
    onEnd: () -> Unit
) {
    val current = hsl.getOrElse(channel) { HslShift() }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            HSL_CENTERS.forEachIndexed { i, pair ->
                Chip(pair.first, channel == i) { onChannel(i) }
            }
        }
        Spacer(Modifier.height(8.dp))
        ValueSlider(stringResource(R.string.hue), current.hue, -1f..1f, { v ->
            onLive(hsl.toMutableList().also { it[channel] = current.copy(hue = v) })
        }, onBegin, onEnd)
        ValueSlider(stringResource(R.string.saturation), current.sat, -1f..1f, { v ->
            onLive(hsl.toMutableList().also { it[channel] = current.copy(sat = v) })
        }, onBegin, onEnd)
        ValueSlider(stringResource(R.string.lum), current.lum, -1f..1f, { v ->
            onLive(hsl.toMutableList().also { it[channel] = current.copy(lum = v) })
        }, onBegin, onEnd)
    }
}

@Composable
fun LocalPanel(
    masks: List<LocalAdjustLayer>,
    selectedId: Long?,
    tool: MaskKind,
    showMask: Boolean,
    size: Float,
    onSelect: (Long) -> Unit,
    onTool: (MaskKind) -> Unit,
    onToggleMask: () -> Unit,
    onSize: (Float) -> Unit,
    onEdit: ((LocalAdjustLayer) -> LocalAdjustLayer) -> Unit,
    onBegin: () -> Unit,
    onFinish: () -> Unit,
    onDelete: () -> Unit
) {
    val selected = masks.firstOrNull { it.id == selectedId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Chip(stringResource(R.string.mask_brush), tool == MaskKind.BRUSH) { onTool(MaskKind.BRUSH) }
            Chip(stringResource(R.string.mask_gradient), tool == MaskKind.LINEAR) { onTool(MaskKind.LINEAR) }
            Chip(stringResource(R.string.mask_radial), tool == MaskKind.RADIAL) { onTool(MaskKind.RADIAL) }
            Chip(stringResource(R.string.mask_light), tool == MaskKind.LIGHT) { onTool(MaskKind.LIGHT) }
            Chip(stringResource(R.string.mask_color), tool == MaskKind.COLOR) { onTool(MaskKind.COLOR) }
            Chip(stringResource(R.string.mask_erase), tool == MaskKind.ERASE) { onTool(MaskKind.ERASE) }
        }
        Spacer(Modifier.height(8.dp))

        if (masks.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                masks.forEachIndexed { index, mask ->
                    Chip("M" + (index + 1), mask.id == selectedId) { onSelect(mask.id) }
                }
                Chip(stringResource(R.string.show_mask), showMask) { onToggleMask() }
                if (selected != null) {
                    Chip(stringResource(R.string.delete_mask), false) { onDelete() }
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        Text(
            text = stringResource(R.string.mask_hint),
            color = Color(0xFF6E6E73),
            fontSize = 10.sp
        )
        Spacer(Modifier.height(4.dp))

        if (selected == null) {
            ValueSlider(stringResource(R.string.size), size, 0.004f..0.08f, { onSize(it) }, {}, {})
        } else {
            ValueSlider(stringResource(R.string.exposure), selected.exposure, -1f..1f,
                { v -> onEdit { it.copy(exposure = v) } }, onBegin, onFinish)
            ValueSlider(stringResource(R.string.contrast), selected.contrast, -1f..1f,
                { v -> onEdit { it.copy(contrast = v) } }, onBegin, onFinish)
            ValueSlider(stringResource(R.string.highlights), selected.highlights, -1f..1f,
                { v -> onEdit { it.copy(highlights = v) } }, onBegin, onFinish)
            ValueSlider(stringResource(R.string.shadows), selected.shadows, -1f..1f,
                { v -> onEdit { it.copy(shadows = v) } }, onBegin, onFinish)
            ValueSlider(stringResource(R.string.saturation), selected.saturation, -1f..1f,
                { v -> onEdit { it.copy(saturation = v) } }, onBegin, onFinish)
            ValueSlider(stringResource(R.string.warmth), selected.warmth, -1f..1f,
                { v -> onEdit { it.copy(warmth = v) } }, onBegin, onFinish)

            if (selected.kind == MaskKind.LIGHT) {
                val centre = (selected.lo + selected.hi) / 2f
                ValueSlider(stringResource(R.string.spread), (selected.hi - selected.lo) / 2f, 0.02f..0.5f,
                    { v ->
                        onEdit {
                            it.copy(
                                lo = (centre - v).coerceIn(0f, 1f),
                                hi = (centre + v).coerceIn(0f, 1f)
                            )
                        }
                    }, onBegin, onFinish)
            }
            if (selected.kind == MaskKind.COLOR) {
                ValueSlider(stringResource(R.string.spread), selected.tolerance, 5f..90f,
                    { v -> onEdit { it.copy(tolerance = v) } }, onBegin, onFinish)
            }
            if (selected.kind == MaskKind.BRUSH || selected.kind == MaskKind.ERASE) {
                ValueSlider(stringResource(R.string.size), size, 0.004f..0.08f, { onSize(it) }, {}, {})
            }
        }
    }
}

@Composable
fun LocalMaskLayer(
    tool: MaskKind,
    liveStroke: List<Pair<Float, Float>>?,
    onStroke: (List<Pair<Float, Float>>?) -> Unit,
    onCreateBrush: (List<Pair<Float, Float>>) -> Unit,
    onCreateGradient: (Float, Float, Float, Float) -> Unit,
    onCreateRadial: (Float, Float, Float) -> Unit,
    onTap: (Float, Float) -> Unit
) {
    val strokeCb = rememberUpdatedState(onStroke)
    val brushCb = rememberUpdatedState(onCreateBrush)
    val gradCb = rememberUpdatedState(onCreateGradient)
    val radCb = rememberUpdatedState(onCreateRadial)
    val tapCb = rememberUpdatedState(onTap)

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(tool) {
                if (tool == MaskKind.LIGHT || tool == MaskKind.COLOR) {
                    detectTapGestures(onTap = { pos ->
                        tapCb.value(
                            (pos.x / size.width).coerceIn(0f, 1f),
                            (pos.y / size.height).coerceIn(0f, 1f)
                        )
                    })
                } else {
                    var points = mutableListOf<Pair<Float, Float>>()
                    detectDragGestures(
                        onDragStart = { pos ->
                            points = mutableListOf(normalize(pos, size))
                            strokeCb.value(points.toList())
                        },
                        onDrag = { change, _ ->
                            points.add(normalize(change.position, size))
                            strokeCb.value(points.toList())
                            change.consume()
                        },
                        onDragEnd = {
                            val first = points.firstOrNull()
                            val last = points.lastOrNull()
                            when (tool) {
                                MaskKind.BRUSH, MaskKind.ERASE ->
                                    if (points.size > 1) brushCb.value(points.toList())
                                MaskKind.LINEAR ->
                                    if (first != null && last != null) {
                                        gradCb.value(first.first, first.second, last.first, last.second)
                                    }
                                MaskKind.RADIAL ->
                                    if (first != null && last != null) {
                                        val dx = last.first - first.first
                                        val dy = last.second - first.second
                                        radCb.value(
                                            first.first,
                                            first.second,
                                            sqrt(dx * dx + dy * dy).coerceAtLeast(0.05f)
                                        )
                                    }
                                else -> Unit
                            }
                            strokeCb.value(null)
                            points = mutableListOf()
                        },
                        onDragCancel = {
                            strokeCb.value(null)
                            points = mutableListOf()
                        }
                    )
                }
            }
    ) {
        val pts = liveStroke
        if (pts != null && pts.isNotEmpty()) {
            when (tool) {
                MaskKind.BRUSH, MaskKind.ERASE -> {
                    if (pts.size > 1) {
                        val path = Path()
                        path.moveTo(pts[0].first * size.width, pts[0].second * size.height)
                        for (i in 1 until pts.size) {
                            path.lineTo(pts[i].first * size.width, pts[i].second * size.height)
                        }
                        drawPath(
                            path,
                            Color.White.copy(alpha = 0.5f),
                            style = Stroke(18f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                }
                MaskKind.LINEAR -> {
                    if (pts.size > 1) {
                        drawLine(
                            Color.White,
                            Offset(pts[0].first * size.width, pts[0].second * size.height),
                            Offset(pts.last().first * size.width, pts.last().second * size.height),
                            2f
                        )
                    }
                }
                MaskKind.RADIAL -> {
                    if (pts.size > 1) {
                        val dx = (pts.last().first - pts[0].first) * size.width
                        val dy = (pts.last().second - pts[0].second) * size.height
                        drawCircle(
                            Color.White,
                            radius = sqrt(dx * dx + dy * dy),
                            center = Offset(pts[0].first * size.width, pts[0].second * size.height),
                            style = Stroke(2f)
                        )
                    }
                }
                else -> Unit
            }
        }
    }
}

fun lumAt(bmp: Bitmap?, nx: Float, ny: Float): Float {
    val b = bmp ?: return 0.5f
    val x = (nx * b.width).toInt().coerceIn(0, b.width - 1)
    val y = (ny * b.height).toInt().coerceIn(0, b.height - 1)
    val c = b.getPixel(x, y)
    return (0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) +
        0.114f * (c and 0xFF)) / 255f
}

fun hueAt(bmp: Bitmap?, nx: Float, ny: Float): Float {
    val b = bmp ?: return 0f
    val x = (nx * b.width).toInt().coerceIn(0, b.width - 1)
    val y = (ny * b.height).toInt().coerceIn(0, b.height - 1)
    val c = b.getPixel(x, y)
    val r = (c shr 16) and 0xFF
    val g = (c shr 8) and 0xFF
    val bl = c and 0xFF
    val mx = maxOf(r, g, bl)
    val mn = minOf(r, g, bl)
    val delta = (mx - mn).toFloat()
    if (delta < 8f) return 0f
    var hue = when (mx) {
        r -> 60f * (((g - bl) / delta) % 6f)
        g -> 60f * (((bl - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }
    if (hue < 0f) hue += 360f
    return hue
}

@Composable
fun MarkupPanel(
    tool: MarkupMode?,
    color: Int,
    size: Float,
    hasLayers: Boolean,
    onTool: (MarkupMode?) -> Unit,
    onColor: (Int) -> Unit,
    onSize: (Float) -> Unit,
    onAddText: () -> Unit,
    onClear: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Chip(stringResource(R.string.tool_none), tool == null) { onTool(null) }
            Chip(stringResource(R.string.tool_pen), tool == MarkupMode.PEN) { onTool(MarkupMode.PEN) }
            Chip(stringResource(R.string.tool_highlight), tool == MarkupMode.HIGHLIGHT) { onTool(MarkupMode.HIGHLIGHT) }
            Chip(stringResource(R.string.tool_pixelate), tool == MarkupMode.PIXELATE) { onTool(MarkupMode.PIXELATE) }
            Chip(stringResource(R.string.tool_blur), tool == MarkupMode.BLUR) { onTool(MarkupMode.BLUR) }
            Chip(stringResource(R.string.tool_blackout), tool == MarkupMode.BLACKOUT) { onTool(MarkupMode.BLACKOUT) }
            Chip(stringResource(R.string.tool_text), tool == MarkupMode.TEXT) { onTool(MarkupMode.TEXT) }
            Chip(stringResource(R.string.tool_heal), tool == MarkupMode.HEAL) { onTool(MarkupMode.HEAL) }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BRUSH_COLORS.forEach { c ->
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .background(Color(c), CircleShape)
                        .border(
                            if (c == color) 2.dp else 1.dp,
                            if (c == color) NijatvaTeal else Color(0xFF3A3A3E),
                            CircleShape
                        )
                        .clickable { onColor(c) }
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        ValueSlider(stringResource(R.string.size), size, 0.004f..0.08f,
            { onSize(it) }, {}, {})
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(stringResource(R.string.add_text), false) { onAddText() }
            Chip(stringResource(R.string.clear_markup), false) { onClear() }
        }
    }
}

@Composable
fun FilterPanel(state: EditState, onPick: (FilterPreset) -> Unit) {
    val presets = listOf(
        FilterPreset.NONE to stringResource(R.string.filter_none),
        FilterPreset.MONO to stringResource(R.string.filter_mono),
        FilterPreset.NOIR to stringResource(R.string.filter_noir),
        FilterPreset.WARM to stringResource(R.string.filter_warm),
        FilterPreset.COOL to stringResource(R.string.filter_cool),
        FilterPreset.VIVID to stringResource(R.string.filter_vivid),
        FilterPreset.FADE to stringResource(R.string.filter_fade),
        FilterPreset.SEPIA to stringResource(R.string.filter_sepia),
        FilterPreset.CHROME to stringResource(R.string.filter_chrome),
        FilterPreset.VINTAGE to stringResource(R.string.filter_vintage),
        FilterPreset.MATTE to stringResource(R.string.filter_matte),
        FilterPreset.SUNSET to stringResource(R.string.filter_sunset),
        FilterPreset.FOREST to stringResource(R.string.filter_forest)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        presets.forEach { (preset, label) ->
            Chip(label, state.filter == preset) { onPick(preset) }
        }
    }
}

@Composable
fun ExportPanel(
    format: String,
    quality: Float,
    stripExif: Boolean,
    saving: Boolean,
    scaleF: Float,
    estimate: String,
    onFormat: (String) -> Unit,
    onQuality: (Float) -> Unit,
    onStrip: (Boolean) -> Unit,
    onScale: (Float) -> Unit,
    onSave: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("JPEG", format == "JPEG") { onFormat("JPEG") }
            Chip("PNG", format == "PNG") { onFormat("PNG") }
            Chip("WEBP", format == "WEBP") { onFormat("WEBP") }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Chip("100%", scaleF == 1f) { onScale(1f) }
            Chip("75%", scaleF == 0.75f) { onScale(0.75f) }
            Chip("50%", scaleF == 0.5f) { onScale(0.5f) }
            if (estimate.isNotEmpty()) {
                Text(
                    text = estimate,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 6.dp, top = 8.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(stringResource(R.string.quality), color = TextSecondary, fontSize = 11.sp)
            Text(quality.roundToInt().toString(), color = NijatvaTeal, fontSize = 11.sp)
        }
        Slider(
            value = quality,
            onValueChange = onQuality,
            valueRange = 10f..100f,
            colors = SliderDefaults.colors(
                thumbColor = NijatvaTeal,
                activeTrackColor = NijatvaTeal,
                inactiveTrackColor = Color(0xFF2A2A2E)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.strip_exif), color = TextSecondary, fontSize = 11.sp)
            Switch(
                checked = stripExif,
                onCheckedChange = onStrip,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Black,
                    checkedTrackColor = NijatvaTeal
                )
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .background(NijatvaTeal, RoundedCornerShape(14.dp))
                .pointerInput(saving) {
                    detectTapGestures(onTap = { if (!saving) onSave() })
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(if (saving) R.string.saving else R.string.save),
                color = Color.Black,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// --------------------------------------------------------------- overlays

@Composable
fun MarkupLayer(
    tool: MarkupMode,
    color: Int,
    strokeWidth: Float,
    liveStroke: List<Pair<Float, Float>>?,
    onStroke: (List<Pair<Float, Float>>?) -> Unit,
    onCommitStroke: (List<Pair<Float, Float>>) -> Unit,
    onTap: (Float, Float) -> Unit
) {
    val strokeCb = rememberUpdatedState(onStroke)
    val commitCb = rememberUpdatedState(onCommitStroke)
    val tapCb = rememberUpdatedState(onTap)

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(tool, color, strokeWidth) {
                if (tool == MarkupMode.TEXT) {
                    detectTapGestures(onTap = { pos ->
                        tapCb.value(
                            (pos.x / size.width).coerceIn(0f, 1f),
                            (pos.y / size.height).coerceIn(0f, 1f)
                        )
                    })
                } else {
                    var points = mutableListOf<Pair<Float, Float>>()
                    detectDragGestures(
                        onDragStart = { pos ->
                            points = mutableListOf(normalize(pos, size))
                            strokeCb.value(points.toList())
                        },
                        onDrag = { change, _ ->
                            points.add(normalize(change.position, size))
                            strokeCb.value(points.toList())
                            change.consume()
                        },
                        onDragEnd = {
                            if (points.size > 1) commitCb.value(points.toList())
                            strokeCb.value(null)
                            points = mutableListOf()
                        },
                        onDragCancel = {
                            strokeCb.value(null)
                            points = mutableListOf()
                        }
                    )
                }
            }
    ) {
        val pts = liveStroke
        if (pts != null && pts.size > 1) {
            val path = Path()
            path.moveTo(pts[0].first * size.width, pts[0].second * size.height)
            for (i in 1 until pts.size) {
                path.lineTo(pts[i].first * size.width, pts[i].second * size.height)
            }
            val paintColor = when (tool) {
                MarkupMode.HIGHLIGHT -> Color(color).copy(alpha = 0.35f)
                MarkupMode.BLACKOUT -> Color.Black
                MarkupMode.PIXELATE, MarkupMode.BLUR -> Color.White.copy(alpha = 0.45f)
                else -> Color(color)
            }
            drawPath(
                path = path,
                color = paintColor,
                style = Stroke(
                    width = (strokeWidth * minOf(size.width, size.height)).coerceAtLeast(2f),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

fun normalize(pos: Offset, size: IntSize): Pair<Float, Float> = Pair(
    (pos.x / size.width.toFloat()).coerceIn(0f, 1f),
    (pos.y / size.height.toFloat()).coerceIn(0f, 1f)
)

@Composable
fun CropOverlay(
    crop: CropRect,
    onChange: (CropRect) -> Unit,
    onStart: () -> Unit,
    onCommit: () -> Unit
) {
    val latest = rememberUpdatedState(crop)
    var working by remember { mutableStateOf<CropRect?>(null) }
    var corner by remember { mutableStateOf(-1) }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { pos ->
                        onStart()
                        val c = latest.value
                        val x = pos.x / size.width
                        val y = pos.y / size.height
                        val n = 0.10f
                        corner = when {
                            abs(x - c.l) < n && abs(y - c.t) < n -> 0
                            abs(x - c.r) < n && abs(y - c.t) < n -> 1
                            abs(x - c.l) < n && abs(y - c.b) < n -> 2
                            abs(x - c.r) < n && abs(y - c.b) < n -> 3
                            else -> -1
                        }
                        working = c
                    },
                    onDrag = { change, drag ->
                        val c = working
                        if (c != null) {
                            val dx = drag.x / size.width
                            val dy = drag.y / size.height
                            var l = c.l
                            var t = c.t
                            var r = c.r
                            var b = c.b
                            when (corner) {
                                0 -> {
                                    l = (c.l + dx).coerceIn(0f, c.r - 0.08f)
                                    t = (c.t + dy).coerceIn(0f, c.b - 0.08f)
                                }
                                1 -> {
                                    r = (c.r + dx).coerceIn(c.l + 0.08f, 1f)
                                    t = (c.t + dy).coerceIn(0f, c.b - 0.08f)
                                }
                                2 -> {
                                    l = (c.l + dx).coerceIn(0f, c.r - 0.08f)
                                    b = (c.b + dy).coerceIn(c.t + 0.08f, 1f)
                                }
                                3 -> {
                                    r = (c.r + dx).coerceIn(c.l + 0.08f, 1f)
                                    b = (c.b + dy).coerceIn(c.t + 0.08f, 1f)
                                }
                                else -> {
                                    val ww = c.width
                                    val hh = c.height
                                    l = (c.l + dx).coerceIn(0f, 1f - ww)
                                    t = (c.t + dy).coerceIn(0f, 1f - hh)
                                    r = l + ww
                                    b = t + hh
                                }
                            }
                            val next = CropRect(l, t, r, b)
                            working = next
                            onChange(next)
                        }
                        change.consume()
                    },
                    onDragEnd = {
                        working = null
                        onCommit()
                    },
                    onDragCancel = {
                        working = null
                        onCommit()
                    }
                )
            }
    ) {
        val c = working ?: crop
        val l = c.l * size.width
        val t = c.t * size.height
        val r = c.r * size.width
        val b = c.b * size.height
        val dim = Color.Black.copy(alpha = 0.6f)

        drawRect(dim, Offset.Zero, Size(size.width, t.coerceAtLeast(0f)))
        drawRect(dim, Offset(0f, b), Size(size.width, (size.height - b).coerceAtLeast(0f)))
        drawRect(dim, Offset(0f, t), Size(l.coerceAtLeast(0f), (b - t).coerceAtLeast(0f)))
        drawRect(dim, Offset(r, t), Size((size.width - r).coerceAtLeast(0f), (b - t).coerceAtLeast(0f)))

        val grid = Color.White.copy(alpha = 0.28f)
        for (i in 1..2) {
            drawLine(
                grid,
                Offset(l + (r - l) * i / 3f, t),
                Offset(l + (r - l) * i / 3f, b),
                1f
            )
            drawLine(
                grid,
                Offset(l, t + (b - t) * i / 3f),
                Offset(r, t + (b - t) * i / 3f),
                1f
            )
        }
        drawRect(Color.White, Offset(l, t), Size(r - l, b - t), style = Stroke(2f))
        listOf(Offset(l, t), Offset(r, t), Offset(l, b), Offset(r, b)).forEach { point ->
            drawCircle(Color.White, 11f, point)
            drawCircle(NijatvaTeal, 7f, point)
        }
    }
}

@Composable
fun HistogramOverlay(bitmap: Bitmap, modifier: Modifier = Modifier) {
    val data = remember(bitmap) { AdvancedOps.histogram(bitmap, 64) }
    Canvas(
        modifier = modifier
            .size(width = 118.dp, height = 50.dp)
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
    ) {
        val peak = (data[0].maxOrNull() ?: 1).coerceAtLeast(1).toFloat()
        val colors = listOf(Color(0xFFFF5252), Color(0xFF69F0AE), Color(0xFF448AFF))
        for (ch in 0 until 3) {
            val arr = data[ch]
            val path = Path()
            path.moveTo(0f, size.height)
            for (i in arr.indices) {
                val x = size.width * i / (arr.size - 1).toFloat()
                val y = size.height - (arr[i] / peak) * size.height * 0.92f
                path.lineTo(x, y)
            }
            path.lineTo(size.width, size.height)
            path.close()
            drawPath(path, colors[ch].copy(alpha = 0.5f))
        }
    }
}
