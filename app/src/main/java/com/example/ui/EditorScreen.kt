package com.example.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.DeviceSong
import com.example.engine.FaceTextureCache
import com.example.engine.SceneRenderer
import com.example.model.*
import com.example.ui.theme.*
import com.example.viewmodel.AxisLock
import com.example.viewmodel.CubeEditorViewModel
import com.example.viewmodel.EditorTool
import com.example.viewmodel.ExportUiState
import kotlin.math.abs

@Composable
fun EditorScreen(
    project: CubeProject?,
    bgBitmap: Bitmap?,
    faceTextureCache: FaceTextureCache,
    timeMs: Long,
    isPlaying: Boolean,
    isLooping: Boolean,
    playbackSpeed: Float,
    canUndo: Boolean,
    canRedo: Boolean,
    selectedCubeId: String?,
    selectedFace: FaceSide,
    selectedWordId: String?,
    selectedLyricId: String?,
    selectedKeyframeId: String?,
    activeTool: EditorTool?,
    axisLock: AxisLock,
    showSafeGuides: Boolean,
    settings: AppSettings,
    deviceSongs: List<DeviceSong>,
    previewingSongId: Long?,
    exportState: ExportUiState,
    toastMessage: String?,
    vm: CubeEditorViewModel,
    onBackHome: () -> Unit
) {
    val context = LocalContext.current
    val copy = remember(settings.language) { copyForLanguage(settings.language) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(toastMessage) {
        if (!toastMessage.isNullOrBlank()) {
            snackbarHostState.showSnackbar(toastMessage)
            vm.clearToast()
        }
    }

    BackHandler {
        if (activeTool != null) {
            vm.openTool(null)
        } else {
            vm.saveAndCloseEditor()
            onBackHome()
        }
    }

    if (project == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(VoidBlack),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = ElectricViolet)
        }
        return
    }

    val selectedCube = remember(project.cubes, selectedCubeId) {
        project.cubes.firstOrNull { it.id == selectedCubeId } ?: project.cubes.firstOrNull()
    }
    var showRenameDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = if (settings.amoledBlack) AmoledBlack else VoidBlack,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            EditorTopBar(
                projectName = project.name,
                aspectLabel = project.aspectRatio.label,
                canUndo = canUndo,
                canRedo = canRedo,
                showSafeGuides = showSafeGuides,
                exportLabel = copy.exportBtn,
                onBack = {
                    vm.saveAndCloseEditor()
                    onBackHome()
                },
                onRename = { showRenameDialog = true },
                onUndo = vm::undo,
                onRedo = vm::redo,
                onToggleGuides = vm::toggleSafeGuides,
                onExport = { vm.openTool(EditorTool.EXPORT) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 3D Stage Viewport
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                val ratio = project.aspectRatio.widthRatio.toFloat() / project.aspectRatio.heightRatio.toFloat()
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(6.dp)
                        .aspectRatio(ratio, matchHeightConstraintsFirst = true)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, SurfaceBorder, RoundedCornerShape(14.dp))
                        .testTag("editor_3d_stage")
                        .pointerInput(axisLock) {
                            detectTransformGestures { _, pan, zoom, rotation ->
                                if (pan.x != 0f || pan.y != 0f) {
                                    val sensitivity = 0.45f
                                    val dxDeg = when (axisLock) {
                                        AxisLock.FREE, AxisLock.X -> pan.y * sensitivity
                                        else -> 0f
                                    }
                                    val dyDeg = when (axisLock) {
                                        AxisLock.FREE, AxisLock.Y -> pan.x * sensitivity
                                        else -> 0f
                                    }
                                    val dzDeg = if (axisLock == AxisLock.Z) pan.x * sensitivity else 0f
                                    vm.rotateSelectedCubeBy(dxDeg, dyDeg, dzDeg)
                                }
                                if (zoom != 1f || abs(rotation) > 0.1f) {
                                    if (abs(rotation) > 0.25f) {
                                        vm.rotateSelectedCubeBy(0f, 0f, rotation)
                                    }
                                    if (zoom != 1f) {
                                        vm.scaleAndMoveSelectedCube(zoom, 0f, 0f)
                                    }
                                }
                            }
                        }
                        .pointerInput(selectedFace) {
                            detectTapGestures(
                                onDoubleTap = {
                                    val nextIdx = (FaceSide.FOUR_SIDES.indexOf(selectedFace) + 1) % FaceSide.FOUR_SIDES.size
                                    val nextFace = FaceSide.FOUR_SIDES[nextIdx]
                                    vm.snapSelectedCubeToFace(nextFace)
                                    vm.triggerHaptic()
                                }
                            )
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawIntoCanvas { composeCanvas ->
                            SceneRenderer.renderFrame(
                                context = context,
                                canvas = composeCanvas.nativeCanvas,
                                width = size.width.toInt().coerceAtLeast(1),
                                height = size.height.toInt().coerceAtLeast(1),
                                project = project,
                                timeMs = timeMs,
                                faceTextureCache = faceTextureCache,
                                bgBitmap = bgBitmap,
                                selectedCubeId = selectedCubeId,
                                selectedFace = selectedFace,
                                selectedWordId = selectedWordId,
                                showSafeGuides = showSafeGuides,
                                textureSizePx = settings.previewQuality.textureSizePx,
                                isExport = false
                            )
                        }
                    }

                    if (!settings.editorCoachDismissed) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(10.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(ObsidianSurface.copy(alpha = 0.88f))
                                .border(1.dp, CyberCyan.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .clickable { vm.dismissEditorCoach() }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                "Drag: Rotate • Pinch: Zoom/Twist • Double-Tap: Snap Face (Tap to hide)",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextPrimary
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(VoidBlack.copy(alpha = 0.74f))
                            .border(1.dp, SurfaceBorder, RoundedCornerShape(20.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AxisLock.entries.forEach { lock ->
                            val selected = axisLock == lock
                            Text(
                                text = lock.label,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) CyberCyan else TextSecondary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { vm.setAxisLock(lock) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                        VerticalDivider(modifier = Modifier.height(16.dp), color = SurfaceBorder)
                        FaceSide.FOUR_SIDES.forEach { face ->
                            val selected = selectedFace == face
                            Text(
                                text = face.label.take(1),
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) ElectricVioletLight else TextMuted,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        vm.snapSelectedCubeToFace(face)
                                        vm.triggerHaptic()
                                    }
                                    .padding(horizontal = 7.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            TransportControlStrip(
                isPlaying = isPlaying,
                isLooping = isLooping,
                playbackSpeed = playbackSpeed,
                onPrevLyric = vm::jumpPrevLyric,
                onNextLyric = vm::jumpNextLyric,
                onPlayPause = vm::togglePlayPause,
                onToggleLoop = vm::toggleLoop,
                onCycleSpeed = vm::cyclePlaybackSpeed,
                onAddKeyframe = {
                    vm.recordKeyframeAtPlayhead()
                    vm.triggerHaptic()
                }
            )

            if (activeTool == null) {
                MultiTrackTimeline(
                    project = project,
                    timeMs = timeMs,
                    selectedCubeId = selectedCubeId,
                    selectedLyricId = selectedLyricId,
                    selectedKeyframeId = selectedKeyframeId,
                    snapToBeat = settings.snapToBeat,
                    onSeek = vm::seekTo,
                    onSelectCube = vm::selectCube,
                    onSelectLyric = { id ->
                        vm.selectLyric(id)
                        vm.openTool(EditorTool.LYRICS)
                    },
                    onSelectKeyframe = { kfId ->
                        vm.selectKeyframe(kfId)
                        vm.openTool(EditorTool.KEYFRAME)
                    },
                    onToggleBeatSnap = {
                        vm.updateSettings { it.copy(snapToBeat = !it.snapToBeat) }
                    }
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 240.dp, max = 330.dp)
                        .background(ObsidianSurface)
                        .border(1.dp, SurfaceBorder)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ElevatedSurface)
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                activeTool.label,
                                style = MaterialTheme.typography.titleMedium,
                                color = CyberCyan
                            )
                            IconButton(
                                onClick = { vm.openTool(null) },
                                modifier = Modifier.size(36.dp).testTag("close_tool_sheet_btn")
                            ) {
                                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Close Tool", tint = TextPrimary)
                            }
                        }

                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            when (activeTool) {
                                EditorTool.MUSIC -> MusicPickerSheet(project, deviceSongs, previewingSongId, vm)
                                EditorTool.TEXT -> TextAndFontsSheet(project, selectedCube, selectedFace, selectedWordId, fontsTabActive = false, vm = vm)
                                EditorTool.FONTS -> TextAndFontsSheet(project, selectedCube, selectedFace, selectedWordId, fontsTabActive = true, vm = vm)
                                EditorTool.COLOR -> ColorSheet(selectedCube, selectedFace, vm)
                                EditorTool.ADD_BOX -> AddBoxSheet(project, selectedCube, vm)
                                EditorTool.LYRICS -> LyricsToolSheet(project, timeMs, isPlaying, playbackSpeed, selectedLyricId, vm)
                                EditorTool.KEYFRAME -> KeyframeAndMotionSheet(project, timeMs, selectedKeyframeId, showMotionPresetsFirst = false, vm = vm)
                                EditorTool.MOTION -> KeyframeAndMotionSheet(project, timeMs, selectedKeyframeId, showMotionPresetsFirst = true, vm = vm)
                                EditorTool.BACKGROUND -> BackgroundAndEffectsSheet(project, vm)
                                EditorTool.CREDIT -> CreditAndWatermarkSheet(project, vm)
                                EditorTool.EXPORT -> ExportSheet(project, exportState, vm)
                            }
                        }
                    }
                }
            }

            EditorBottomToolDock(
                activeTool = activeTool,
                copy = copy,
                onSelectTool = vm::openTool
            )
        }
    }

    if (showRenameDialog) {
        var newName by remember(project.name) { mutableStateOf(project.name) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            containerColor = ElevatedSurface,
            title = { Text("Rename Project", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    label = { Text("Name") }
                )
            },
            confirmButton = {
                Button(onClick = {
                    val clean = newName.trim()
                    if (clean.isNotEmpty()) {
                        vm.updateProjectSettings { it.copy(name = clean) }
                    }
                    showRenameDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun EditorTopBar(
    projectName: String,
    aspectLabel: String,
    canUndo: Boolean,
    canRedo: Boolean,
    showSafeGuides: Boolean,
    exportLabel: String,
    onBack: () -> Unit,
    onRename: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onToggleGuides: () -> Unit,
    onExport: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ObsidianSurface)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.testTag("editor_back_btn")) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = TextPrimary)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onRename)
                .padding(horizontal = 4.dp)
        ) {
            Text(
                projectName,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(aspectLabel, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }

        IconButton(onClick = onUndo, enabled = canUndo, modifier = Modifier.testTag("editor_undo_btn")) {
            Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = "Undo", tint = if (canUndo) TextPrimary else TextMuted)
        }
        IconButton(onClick = onRedo, enabled = canRedo, modifier = Modifier.testTag("editor_redo_btn")) {
            Icon(Icons.AutoMirrored.Rounded.Redo, contentDescription = "Redo", tint = if (canRedo) TextPrimary else TextMuted)
        }
        IconButton(onClick = onToggleGuides, modifier = Modifier.testTag("editor_guides_btn")) {
            Icon(
                Icons.Rounded.GridOn,
                contentDescription = "Safe Area Guides",
                tint = if (showSafeGuides) CyberCyan else TextSecondary
            )
        }

        Button(
            onClick = onExport,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
            modifier = Modifier
                .height(40.dp)
                .testTag("editor_export_btn")
        ) {
            Icon(Icons.Rounded.Publish, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(exportLabel, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun TransportControlStrip(
    isPlaying: Boolean,
    isLooping: Boolean,
    playbackSpeed: Float,
    onPrevLyric: () -> Unit,
    onNextLyric: () -> Unit,
    onPlayPause: () -> Unit,
    onToggleLoop: () -> Unit,
    onCycleSpeed: () -> Unit,
    onAddKeyframe: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(VoidBlack)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevLyric, modifier = Modifier.testTag("transport_prev_lyric")) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous Lyric", tint = TextPrimary)
            }
            IconButton(onClick = onNextLyric, modifier = Modifier.testTag("transport_next_lyric")) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "Next Lyric", tint = TextPrimary)
            }
        }

        FilledIconButton(
            onClick = onPlayPause,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = ElectricViolet),
            modifier = Modifier
                .size(48.dp)
                .testTag("transport_play_pause_btn")
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Color.White
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            TextButton(onClick = onCycleSpeed, modifier = Modifier.testTag("transport_speed_btn")) {
                Text("${playbackSpeed}x", color = CyberCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onToggleLoop, modifier = Modifier.testTag("transport_loop_btn")) {
                Icon(Icons.Rounded.Repeat, contentDescription = "Loop", tint = if (isLooping) CyberCyan else TextMuted)
            }
            IconButton(onClick = onAddKeyframe, modifier = Modifier.testTag("transport_add_kf_btn")) {
                Icon(Icons.Rounded.Diamond, contentDescription = "Add Keyframe", tint = AmberWarning)
            }
        }
    }
}

@Composable
private fun EditorBottomToolDock(
    activeTool: EditorTool?,
    copy: UiCopy,
    onSelectTool: (EditorTool) -> Unit
) {
    val items = remember(copy) {
        listOf(
            Triple(EditorTool.MUSIC, copy.music, Icons.Rounded.MusicNote),
            Triple(EditorTool.TEXT, copy.text, Icons.Rounded.Title),
            Triple(EditorTool.FONTS, copy.fonts, Icons.Rounded.FontDownload),
            Triple(EditorTool.COLOR, copy.color, Icons.Rounded.Palette),
            Triple(EditorTool.ADD_BOX, copy.addBox, Icons.Rounded.AddBox),
            Triple(EditorTool.LYRICS, copy.lyrics, Icons.Rounded.Subtitles),
            Triple(EditorTool.KEYFRAME, copy.keyframe, Icons.Rounded.Diamond),
            Triple(EditorTool.MOTION, copy.motion, Icons.Rounded.Animation),
            Triple(EditorTool.BACKGROUND, copy.bgFx, Icons.Rounded.AutoFixHigh),
            Triple(EditorTool.CREDIT, copy.credit, Icons.Rounded.AlternateEmail),
            Triple(EditorTool.EXPORT, copy.exportBtn, Icons.Rounded.Movie)
        )
    }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(ObsidianSurface)
            .padding(vertical = 6.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(items, key = { it.first.name }) { (tool, label, icon) ->
            val selected = activeTool == tool
            Column(
                modifier = Modifier
                    .widthIn(min = 62.dp)
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) ElectricViolet.copy(alpha = 0.24f) else Color.Transparent)
                    .border(
                        width = if (selected) 1.dp else 0.dp,
                        color = if (selected) ElectricViolet else Color.Transparent,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelectTool(tool) }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .testTag("tool_dock_${tool.name.lowercase()}"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (selected) CyberCyan else TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = label,
                    fontSize = 11.sp,
                    color = if (selected) CyberCyan else TextSecondary,
                    maxLines = 1
                )
            }
        }
    }
}
