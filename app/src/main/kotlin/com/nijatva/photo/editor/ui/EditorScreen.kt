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
import com.nijatva.photo.editor.edit.AutoOps
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

enum class EditorTab { CROP, ADJUST, DETAIL, CURVES, HSL, LOCAL, MARKUP, FILTERS, EXPORT }

val BRUSH_COLORS = listOf(
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
    var looks by remember { mutableStateOf(PresetStore.load(context)) }
    var selectedLook by remember { mutableStateOf<String?>(null) }
    var showSaveLook by remember { mutableStateOf(false) }
    var lookName by remember { mutableStateOf("") }
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
    var curveChannel by remember { mutableStateOf(0) }
    var showHistogram by remember { mutableStateOf(false) }
    var liveStroke by remember { mutableStateOf<List<Pair<Float, Float>>?>(null) }
    var textAt by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var textValue by remember { mutableStateOf("") }
    var splitMode by remember { mutableStateOf(false) }
    var splitX by remember { mutableStateOf(0.5f) }
    var outScale by remember { mutableStateOf(1f) }
    var maxEdge by remember { mutableStateOf(0) }
    var localExposure by remember { mutableStateOf(0f) }
    var localSaturation by remember { mutableStateOf(0f) }
    var localWarmth by remember { mutableStateOf(0f) }
    var maskTool by remember { mutableStateOf(MaskKind.BRUSH) }
    var selectedMask by remember { mutableStateOf<Long?>(null) }
    var showMask by remember { mutableStateOf(false) }
    var estimate by remember { mutableStateOf("") }

    val state = history.state
    val previewState = if (tab == EditorTab.CROP) state.copy(crop = CropRect()) else state

    LaunchedEffect(uri) {
        preview = withContext(Dispatchers.IO) { ImageLoader.loadPreview(resolver, uri, 1400) }
    }

    LaunchedEffect(preview, previewState, showMask, selectedMask) {
        val source = preview ?: return@LaunchedEffect
        delay(45)
        rendered = withContext(Dispatchers.Default) {
            val base = Pipeline.render(source, previewState)
            val mask = if (showMask) {
                previewState.localAdjusts.firstOrNull { it.id == selectedMask }
            } else {
                null
            }
            if (mask != null) AdvancedOps.maskOverlay(base, mask) else base
        }
    }

    LaunchedEffect(rendered, format, quality, outScale, maxEdge) {
        val bmp = rendered ?: return@LaunchedEffect
        delay(120)
        estimate = withContext(Dispatchers.Default) {
            Exporter.estimateSize(bmp, format, quality.roundToInt(), outScale, maxEdge)
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
                    Exporter.save(context, out, format, quality.roundToInt(), stripExif, uri, outScale, maxEdge)
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
                    if (markUp) {
                        MarkupLayer(
                            tool = tool!!,
                            color = brushColor,
                            strokeWidth = brushSize,
                            liveStroke = liveStroke,
                            onStroke = { liveStroke = it },
                            onCommitStroke = { points ->
                                if (points.size > 1) {
                                    val layer = StrokeLayer(
                                        id = System.nanoTime(),
                                        points = points,
                                        mode = tool!!,
                                        color = brushColor,
                                        width = brushSize
                                    )
                                    history.apply(
                                        history.state.copy(layers = history.state.layers + layer)
                                    )
                                }
                                liveStroke = null
                            },
                            onTap = { nx, ny -> textAt = nx to ny }
                        )
                    }

                    if (localMode) {
                        val addMask: (LocalAdjustLayer) -> Unit = { mask ->
                            history.apply(
                                history.state.copy(
                                    localAdjusts = history.state.localAdjusts + mask
                                )
                            )
                            selectedMask = mask.id
                            showMask = true
                        }
                        LocalMaskLayer(
                            tool = maskTool,
                            liveStroke = liveStroke,
                            onStroke = { liveStroke = it },
                            onCreateBrush = { points ->
                                addMask(
                                    LocalAdjustLayer(
                                        id = System.nanoTime(),
                                        kind = maskTool,
                                        points = points,
                                        width = brushSize,
                                        exposure = localExposure,
                                        saturation = localSaturation,
                                        warmth = localWarmth
                                    )
                                )
                            },
                            onCreateGradient = { x1, y1, x2, y2 ->
                                addMask(
                                    LocalAdjustLayer(
                                        id = System.nanoTime(),
                                        kind = MaskKind.LINEAR,
                                        x1 = x1, y1 = y1, x2 = x2, y2 = y2,
                                        exposure = localExposure,
                                        saturation = localSaturation,
                                        warmth = localWarmth
                                    )
                                )
                            },
                            onCreateRadial = { cx, cy, radius ->
                                addMask(
                                    LocalAdjustLayer(
                                        id = System.nanoTime(),
                                        kind = MaskKind.RADIAL,
                                        cx = cx, cy = cy, radius = radius,
                                        exposure = localExposure,
                                        saturation = localSaturation,
                                        warmth = localWarmth
                                    )
                                )
                            },
                            onTap = { nx, ny ->
                                if (maskTool == MaskKind.COLOR) {
                                    val hue = hueAt(shown, nx, ny)
                                    addMask(
                                        LocalAdjustLayer(
                                            id = System.nanoTime(),
                                            kind = MaskKind.COLOR,
                                            targetHue = hue,
                                            exposure = localExposure,
                                            saturation = localSaturation,
                                            warmth = localWarmth
                                        )
                                    )
                                } else {
                                    val lum = lumAt(shown, nx, ny)
                                    addMask(
                                        LocalAdjustLayer(
                                            id = System.nanoTime(),
                                            kind = MaskKind.LIGHT,
                                            lo = (lum - 0.14f).coerceIn(0f, 1f),
                                            hi = (lum + 0.14f).coerceIn(0f, 1f),
                                            exposure = localExposure,
                                            saturation = localSaturation,
                                            warmth = localWarmth
                                        )
                                    )
                                }
                            }
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
                        onEnd = { endDrag() },
                        onAutoStraighten = {
                            preview?.let { bmp ->
                                val tilt = AutoOps.analyse(bmp).straighten
                                history.apply(state.copy(straighten = tilt))
                            }
                        }
                    )

                    EditorTab.ADJUST -> AdjustPanel(
                        state = state,
                        onLive = { history.update(it) },
                        onBegin = { beginDrag() },
                        onEnd = { endDrag() },
                        onAuto = {
                            preview?.let { bmp ->
                                val s = AutoOps.analyse(bmp)
                                history.apply(
                                    state.copy(
                                        exposure = s.exposure,
                                        contrast = s.contrast,
                                        highlights = s.highlights,
                                        shadows = s.shadows,
                                        warmth = s.warmth
                                    )
                                )
                            }
                        }
                    )

                    EditorTab.DETAIL -> DetailPanel(
                        state = state,
                        onLive = { history.update(it) },
                        onBegin = { beginDrag() },
                        onEnd = { endDrag() }
                    )

                    EditorTab.CURVES -> CurvePanel(
                        curve = when (curveChannel) {
                            1 -> state.curveR
                            2 -> state.curveG
                            3 -> state.curveB
                            else -> state.curve
                        },
                        channel = curveChannel,
                        onChannel = { curveChannel = it },
                        onLive = { next ->
                            history.update(
                                when (curveChannel) {
                                    1 -> state.copy(curveR = next)
                                    2 -> state.copy(curveG = next)
                                    3 -> state.copy(curveB = next)
                                    else -> state.copy(curve = next)
                                }
                            )
                        },
                        onBegin = { beginDrag() },
                        onEnd = { endDrag() },
                        onReset = {
                            history.apply(
                                state.copy(
                                    curve = DEFAULT_CURVE,
                                    curveR = DEFAULT_CURVE,
                                    curveG = DEFAULT_CURVE,
                                    curveB = DEFAULT_CURVE
                                )
                            )
                        }
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
                        masks = state.localAdjusts,
                        selectedId = selectedMask,
                        tool = maskTool,
                        showMask = showMask,
                        size = brushSize,
                        onSelect = { selectedMask = it },
                        onTool = { maskTool = it },
                        onToggleMask = { showMask = !showMask },
                        onSize = { brushSize = it },
                        onEdit = { transform ->
                            val id = selectedMask
                            if (id == null) return@LocalPanel
                            history.update(
                                state.copy(
                                    localAdjusts = state.localAdjusts.map {
                                        if (it.id == id) transform(it) else it
                                    }
                                )
                            )
                        },
                        onBegin = { beginDrag() },
                        onFinish = { endDrag() },
                        onDelete = {
                            val id = selectedMask
                            if (id != null) {
                                history.apply(
                                    state.copy(localAdjusts = state.localAdjusts.filterNot { it.id == id })
                                )
                                selectedMask = null
                                showMask = false
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

                    EditorTab.FILTERS -> FilterPanel(
                        state = state,
                        lookNames = looks.map { it.name },
                        selectedLook = selectedLook,
                        onPick = { preset -> history.apply(state.copy(filter = preset)) },
                        onSaveLook = {
                            lookName = ""
                            showSaveLook = true
                        },
                        onApplyLook = { name ->
                            val preset = looks.firstOrNull { it.name == name }
                            if (preset != null) {
                                history.apply(PresetStore.apply(preset.json, state))
                                selectedLook = name
                            }
                        },
                        onDeleteLook = { name ->
                            looks = PresetStore.delete(context, name)
                            if (selectedLook == name) selectedLook = null
                        }
                    )

                    EditorTab.EXPORT -> ExportPanel(
                        format = format,
                        quality = quality,
                        stripExif = stripExif,
                        saving = saving,
                        scaleF = outScale,
                        maxEdge = maxEdge,
                        estimate = estimate,
                        onFormat = { format = it },
                        onMaxEdge = { maxEdge = it },
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

    if (showSaveLook) {
        AlertDialog(
            onDismissRequest = { showSaveLook = false },
            containerColor = Surface2,
            title = { Text(stringResource(R.string.preset_name), color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = lookName,
                    onValueChange = { lookName = it },
                    placeholder = {
                        Text(stringResource(R.string.preset_name_hint), color = TextSecondary)
                    },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (lookName.isNotBlank()) {
                        looks = PresetStore.upsert(context, lookName, state)
                        selectedLook = lookName.trim()
                    }
                    showSaveLook = false
                }) { Text(stringResource(R.string.save_look), color = NijatvaTeal) }
            },
            dismissButton = {
                TextButton(onClick = { showSaveLook = false }) {
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
fun TopAction(label: String, enabled: Boolean = true, onClick: () -> Unit) {
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
fun TabLabel(label: String, selected: Boolean, onClick: () -> Unit) {
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

