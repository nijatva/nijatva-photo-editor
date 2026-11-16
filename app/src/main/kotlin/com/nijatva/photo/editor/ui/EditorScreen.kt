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
import com.nijatva.photo.editor.edit.MarkupMode
import com.nijatva.photo.editor.edit.Pipeline
import com.nijatva.photo.editor.edit.StrokeLayer
import com.nijatva.photo.editor.edit.TextLayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class EditorTab { CROP, ADJUST, DETAIL, CURVES, HSL, LOCAL, MARKUP, FILTERS, EXPORT }

private val BRUSH_COLORS = listOf(
    0xFFFF3B30.toInt(),
    0xFF00E0B2.toInt(),
    0xFF3B82F6.toInt(),
    0xFFFFD60A.toInt(),
    0xFFFFFFFF.toInt(),
    0xFF000000.toInt()
)

@Composable
fun EditorScreen(uri: Uri, onBack: () -> Unit) {
    val context = LocalContext.current
    val resolver = context.contentResolver
    val scope = rememberCoroutineScope()

    val history = remember { EditHistory() }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var rendered by remember { mutableStateOf<Bitmap?>(null) }
    var tab by remember { mutableStateOf(EditorTab.ADJUST) }
    var comparing by remember { mutableStateOf(false) }
    var showExif by remember { mutableStateOf(false) }
    var exifRows by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var format by remember { mutableStateOf("JPEG") }
    var quality by remember { mutableStateOf(92f) }
    var stripExif by remember { mutableStateOf(true) }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var dragStart by remember { mutableStateOf<EditState?>(null) }
    var tool by remember { mutableStateOf<MarkupMode?>(null) }
    var brushColor by remember { mutableStateOf(BRUSH_COLORS[0]) }
    var brushSize by remember { mutableStateOf(0.02f) }
    var hslChannel by remember { mutableStateOf(0) }
    var showHistogram by remember { mutableStateOf(false) }
    var liveStroke by remember { mutableStateOf<List<Pair<Float, Float>>?>(null) }
    var textAt by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var textValue by remember { mutableStateOf("") }
    var splitMode by remember { mutableStateOf(false) }
    var splitX by remember { mutableStateOf(0.5f) }
    var outScale by remember { mutableStateOf(1f) }
    var localExposure by remember { mutableStateOf(0f) }
    var localSaturation by remember { mutableStateOf(0f) }
    var localWarmth by remember { mutableStateOf(0f) }
    var estimate by remember { mutableStateOf("") }

    val state = history.state
    val previewState = if (tab == EditorTab.CROP) state.copy(crop = CropRect()) else state

    LaunchedEffect(uri) {
        preview = withContext(Dispatchers.IO) { ImageLoader.loadPreview(resolver, uri, 1400) }
    }

    LaunchedEffect(preview, previewState) {
        val source = preview ?: return@LaunchedEffect
        delay(45)
        rendered = withContext(Dispatchers.Default) { Pipeline.render(source, previewState) }
    }

    LaunchedEffect(rendered, format, quality, outScale) {
        val bmp = rendered ?: return@LaunchedEffect
        delay(120)
        estimate = withContext(Dispatchers.Default) {
            Exporter.estimateSize(bmp, format, quality.roundToInt(), outScale)
        }
    }

    LaunchedEffect(message) {
        if (message != null) {
            delay(3400)
            message = null
        }
    }

    fun beginDrag() {
        if (dragStart == null) dragStart = history.state
    }

    fun endDrag() {
        dragStart?.let { history.commit(it) }
        dragStart = null
    }

    val doSave: () -> Unit = {
        saving = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                val full = ImageLoader.loadFull(resolver, uri)
                if (full == null) null
                else {
                    val out = Pipeline.render(full, history.state)
                    Exporter.save(context, out, format, quality.roundToInt(), stripExif, uri, outScale)
                }
            }
            saving = false
            message = if (result != null) {
                context.getString(R.string.saved, result.displayPath)
            } else {
                context.getString(R.string.save_failed)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) doSave() else message = context.getString(R.string.need_permission)
    }

    val onSaveClick: () -> Unit = {
        val needsPermission = Build.VERSION.SDK_INT < 29 &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            doSave()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .safeDrawingPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .background(AmoledBlack)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TopAction(stringResource(R.string.back)) { onBack() }
            Spacer(Modifier.weight(1f))
            TopAction(stringResource(R.string.undo), history.canUndo) { history.undo() }
            TopAction(stringResource(R.string.redo), history.canRedo) { history.redo() }
            Box(
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectTapGestures(onPress = {
                            comparing = true
                            tryAwaitRelease()
                            comparing = false
                        })
                    }
                    .padding(horizontal = 7.dp, vertical = 8.dp)
            ) {
                Text(
                    stringResource(R.string.original),
                    color = if (comparing) NijatvaTeal else TextSecondary,
                    fontSize = 11.sp
                )
            }
            TopAction(stringResource(R.string.histogram), showHistogram) {
                showHistogram = !showHistogram
            }
            TopAction(stringResource(R.string.split), splitMode) {
                splitMode = !splitMode
                if (splitMode) {
                    comparing = false
                    scale = 1f
                    offset = Offset.Zero
                }
            }
            TopAction(stringResource(R.string.exif_info)) {
                exifRows = ExifSummary.read(resolver, uri)
                showExif = true
            }
            Box(
                modifier = Modifier
                    .background(NijatvaTeal, RoundedCornerShape(50))
                    .padding(horizontal = 11.dp, vertical = 6.dp)
                    .pointerInput(Unit) { detectTapGestures(onTap = { tab = EditorTab.EXPORT }) }
            ) {
                Text(
                    stringResource(R.string.export),
                    color = Color.Black,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            val shown = if (comparing) preview else rendered
            if (shown != null) {
                val ratio = shown.width.toFloat() / shown.height.toFloat()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .aspectRatio(ratio)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        }
                        .pointerInput(tool, tab, splitMode) {
                            if (tool == null && tab != EditorTab.CROP &&
                                tab != EditorTab.LOCAL && !splitMode
                            ) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(1f, 8f)
                                    offset = if (scale <= 1.01f) Offset.Zero else offset + pan
                                }
                            }
                        }
                        .pointerInput(tool, tab, splitMode) {
                            if (tool == null && tab != EditorTab.CROP &&
                                tab != EditorTab.LOCAL && !splitMode
                            ) {
                                detectTapGestures(onDoubleTap = {
                                    scale = 1f
                                    offset = Offset.Zero
                                })
                            }
                        }
                ) {
                    Image(
                        bitmap = shown.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )

                    val original = preview
                    if (splitMode && original != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .drawWithContent {
                                    clipRect(right = size.width * splitX) {
                                        this@drawWithContent.drawContent()
                                    }
                                }
                        ) {
                            Image(
                                bitmap = original.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectDragGestures { change, _ ->
                                        splitX = (change.position.x / size.width)
                                            .coerceIn(0.03f, 0.97f)
                                        change.consume()
                                    }
                                }
                        ) {
                            drawLine(
                                Color.White,
                                Offset(size.width * splitX, 0f),
                                Offset(size.width * splitX, size.height),
                                3f
                            )
                            drawCircle(
                                NijatvaTeal,
                                16f,
                                Offset(size.width * splitX, size.height / 2f)
                            )
                        }
                    }

                    val markUp = tab == EditorTab.MARKUP && tool != null
                    val localMode = tab == EditorTab.LOCAL
                    if (markUp || localMode) {
                        MarkupLayer(
                            tool = if (localMode) MarkupMode.PEN else tool!!,
                            color = if (localMode) 0xFFFFFFFF.toInt() else brushColor,
                            strokeWidth = brushSize,
                            liveStroke = liveStroke,
                            onStroke = { liveStroke = it },
                            onCommitStroke = { points ->
                                if (points.size > 1) {
                                    if (localMode) {
                                        val layer = LocalAdjustLayer(
                                            id = System.nanoTime(),
                                            points = points,
                                            width = brushSize,
                                            exposure = localExposure,
                                            saturation = localSaturation,
                                            warmth = localWarmth
                                        )
                                        history.apply(
                                            history.state.copy(
                                                localAdjusts = history.state.localAdjusts + layer
                                            )
                                        )
                                    } else {
                                        val layer = StrokeLayer(
                                            id = System.nanoTime(),
                                            points = points,
                                            mode = tool!!,
                                            color = brushColor,
                                            width = brushSize
                                        )
                                        history.apply(
                                            history.state.copy(
                                                layers = history.state.layers + layer
                                            )
                                        )
                                    }
                                }
                                liveStroke = null
                            },
                            onTap = { nx, ny -> if (markUp) textAt = nx to ny }
                        )
                    }

                    if (tab == EditorTab.CROP) {
                        CropOverlay(
                            crop = state.crop,
                            onChange = { rect -> history.update(history.state.copy(crop = rect)) },
                            onStart = { beginDrag() },
                            onCommit = { endDrag() }
                        )
                    }
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = NijatvaTeal, strokeWidth = 2.dp)
                    Spacer(Modifier.height(14.dp))
                    Text(stringResource(R.string.loading), color = TextSecondary, fontSize = 13.sp)
                }
            }

            if (showHistogram && rendered != null) {
                HistogramOverlay(
                    bitmap = rendered!!,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface1, RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .padding(vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                TabLabel(stringResource(R.string.tab_crop), tab == EditorTab.CROP) { tab = EditorTab.CROP }
                TabLabel(stringResource(R.string.tab_adjust), tab == EditorTab.ADJUST) { tab = EditorTab.ADJUST }
                TabLabel(stringResource(R.string.tab_detail), tab == EditorTab.DETAIL) { tab = EditorTab.DETAIL }
                TabLabel(stringResource(R.string.tab_curves), tab == EditorTab.CURVES) { tab = EditorTab.CURVES }
                TabLabel(stringResource(R.string.tab_hsl), tab == EditorTab.HSL) { tab = EditorTab.HSL }
                TabLabel(stringResource(R.string.tab_local), tab == EditorTab.LOCAL) { tab = EditorTab.LOCAL }
                TabLabel(stringResource(R.string.tab_markup), tab == EditorTab.MARKUP) { tab = EditorTab.MARKUP }
                TabLabel(stringResource(R.string.tab_filters), tab == EditorTab.FILTERS) { tab = EditorTab.FILTERS }
                TabLabel(stringResource(R.string.tab_export), tab == EditorTab.EXPORT) { tab = EditorTab.EXPORT }
            }
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(214.dp)
                    .padding(horizontal = 14.dp)
            ) {
                when (tab) {
                    EditorTab.CROP -> CropPanel(
                        state = state,
                        onApply = { history.apply(it) },
                        onLive = { history.update(it) },
                        onBegin = { beginDrag() },
                        onEnd = { endDrag() }
                    )

                    EditorTab.ADJUST -> AdjustPanel(
                        state = state,
                        onLive = { history.update(it) },
                        onBegin = { beginDrag() },
                        onEnd = { endDrag() }
                    )

                    EditorTab.DETAIL -> DetailPanel(
                        state = state,
                        onLive = { history.update(it) },
                        onBegin = { beginDrag() },
                        onEnd = { endDrag() }
                    )

                    EditorTab.CURVES -> CurvePanel(
                        curve = state.curve,
                        onLive = { history.update(state.copy(curve = it)) },
                        onBegin = { beginDrag() },
                        onEnd = { endDrag() },
                        onReset = { history.apply(state.copy(curve = DEFAULT_CURVE)) }
                    )

                    EditorTab.HSL -> HslPanel(
                        hsl = state.hsl,
                        channel = hslChannel,
                        onChannel = { hslChannel = it },
                        onLive = { history.update(state.copy(hsl = it)) },
                        onBegin = { beginDrag() },
                        onEnd = { endDrag() }
                    )

                    EditorTab.LOCAL -> LocalPanel(
                        exposure = localExposure,
                        saturation = localSaturation,
                        warmth = localWarmth,
                        size = brushSize,
                        hasMasks = state.localAdjusts.isNotEmpty(),
                        onExposure = { localExposure = it },
                        onSaturation = { localSaturation = it },
                        onWarmth = { localWarmth = it },
                        onSize = { brushSize = it },
                        onClear = {
                            if (state.localAdjusts.isNotEmpty()) {
                                history.apply(state.copy(localAdjusts = emptyList()))
                            }
                        }
                    )

                    EditorTab.MARKUP -> MarkupPanel(
                        tool = tool,
                        color = brushColor,
                        size = brushSize,
                        hasLayers = state.layers.isNotEmpty(),
                        onTool = { tool = it },
                        onColor = { brushColor = it },
                        onSize = { brushSize = it },
                        onAddText = { textValue = ""; textAt = 0.5f to 0.5f },
                        onClear = {
                            if (state.layers.isNotEmpty()) {
                                history.apply(state.copy(layers = emptyList()))
                            }
                        }
                    )

                    EditorTab.FILTERS -> FilterPanel(state) { preset ->
                        history.apply(state.copy(filter = preset))
                    }

                    EditorTab.EXPORT -> ExportPanel(
                        format = format,
                        quality = quality,
                        stripExif = stripExif,
                        saving = saving,
                        scaleF = outScale,
                        estimate = estimate,
                        onFormat = { format = it },
                        onQuality = { quality = it },
                        onStrip = { stripExif = it },
                        onScale = { outScale = it },
                        onSave = onSaveClick
                    )
                }
            }
        }
    }

    val pendingText = textAt
    if (pendingText != null) {
        AlertDialog(
            onDismissRequest = { textAt = null },
            containerColor = Surface2,
            title = { Text(stringResource(R.string.add_text), color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    placeholder = { Text(stringResource(R.string.text_hint), color = TextSecondary) },
                    singleLine = false
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (textValue.isNotBlank()) {
                        val layer = TextLayer(
                            id = System.nanoTime(),
                            text = textValue,
                            x = pendingText.first,
                            y = pendingText.second,
                            size = 0.06f,
                            color = brushColor
                        )
                        history.apply(history.state.copy(layers = history.state.layers + layer))
                    }
                    textAt = null
                }) { Text(stringResource(R.string.add), color = NijatvaTeal) }
            },
            dismissButton = {
                TextButton(onClick = { textAt = null }) {
                    Text(stringResource(R.string.cancel), color = TextSecondary)
                }
            }
        )
    }

    if (showExif) {
        AlertDialog(
            onDismissRequest = { showExif = false },
            containerColor = Surface2,
            title = { Text(stringResource(R.string.exif_info), color = TextPrimary) },
            confirmButton = {
                TextButton(onClick = { showExif = false }) {
                    Text(stringResource(R.string.close), color = NijatvaTeal)
                }
            },
            text = {
                Column(modifier = Modifier.height(300.dp)) {
                    if (exifRows.isEmpty()) {
                        Text(stringResource(R.string.no_exif), color = TextSecondary, fontSize = 13.sp)
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            exifRows.forEach { (k, v) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Text(
                                        k,
                                        color = TextSecondary,
                                        fontSize = 12.sp,
                                        modifier = Modifier.width(110.dp)
                                    )
                                    Text(v, color = TextPrimary, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        )
    }

    message?.let { text ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .background(Color(0xFF232326), RoundedCornerShape(14.dp))
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Text(text, color = TextPrimary, fontSize = 13.sp)
            }
        }
    }
}

// ------------------------------------------------------------------ chrome

@Composable
private fun TopAction(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .pointerInput(enabled) {
                if (enabled) detectTapGestures(onTap = { onClick() })
            }
            .padding(horizontal = 7.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            color = if (enabled) TextPrimary else Color(0xFF454549),
            fontSize = 11.sp
        )
    }
}

@Composable
private fun TabLabel(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            color = if (selected) NijatvaTeal else TextSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier
                .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) }
                .padding(horizontal = 9.dp, vertical = 6.dp)
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .size(width = 16.dp, height = 2.dp)
                .background(
                    if (selected) NijatvaTeal else Color.Transparent,
                    RoundedCornerShape(2.dp)
                )
        )
    }
}

// ------------------------------------------------------------------ panels

@Composable
private fun ValueSlider(
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
private fun AdjustPanel(
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
private fun DetailPanel(
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
private fun CropPanel(
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

private fun centered(aspect: Float, current: CropRect): CropRect {
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
private fun CurvePanel(
    curve: List<Float>,
    onLive: (List<Float>) -> Unit,
    onBegin: () -> Unit,
    onEnd: () -> Unit,
    onReset: () -> Unit
) {
    val latest = rememberUpdatedState(curve)
    var dragIdx by remember { mutableStateOf(-1) }

    Column(modifier = Modifier.fillMaxSize()) {
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
            drawPath(path, NijatvaTeal, style = Stroke(2f))
            curve.forEachIndexed { i, v ->
                val x = size.width * i / (curve.size - 1).toFloat()
                val y = size.height * (1f - v)
                drawCircle(Color.White, 7f, Offset(x, y))
                drawCircle(NijatvaTeal, 4f, Offset(x, y))
            }
        }
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = onReset) {
            Text(stringResource(R.string.curve_reset), color = TextSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun HslPanel(
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
private fun LocalPanel(
    exposure: Float,
    saturation: Float,
    warmth: Float,
    size: Float,
    hasMasks: Boolean,
    onExposure: (Float) -> Unit,
    onSaturation: (Float) -> Unit,
    onWarmth: (Float) -> Unit,
    onSize: (Float) -> Unit,
    onClear: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.local_hint),
            color = Color(0xFF6E6E73),
            fontSize = 10.sp
        )
        Spacer(Modifier.height(4.dp))
        ValueSlider(stringResource(R.string.exposure), exposure, -1f..1f,
            { onExposure(it) }, {}, {})
        ValueSlider(stringResource(R.string.saturation), saturation, -1f..1f,
            { onSaturation(it) }, {}, {})
        ValueSlider(stringResource(R.string.warmth), warmth, -1f..1f,
            { onWarmth(it) }, {}, {})
        ValueSlider(stringResource(R.string.size), size, 0.004f..0.08f,
            { onSize(it) }, {}, {})
        Chip(stringResource(R.string.clear_masks), !hasMasks) { onClear() }
    }
}

@Composable
private fun MarkupPanel(
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
private fun FilterPanel(state: EditState, onPick: (FilterPreset) -> Unit) {
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
private fun ExportPanel(
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
private fun MarkupLayer(
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

private fun normalize(pos: Offset, size: IntSize): Pair<Float, Float> = Pair(
    (pos.x / size.width.toFloat()).coerceIn(0f, 1f),
    (pos.y / size.height.toFloat()).coerceIn(0f, 1f)
)

@Composable
private fun CropOverlay(
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
private fun HistogramOverlay(bitmap: Bitmap, modifier: Modifier = Modifier) {
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
