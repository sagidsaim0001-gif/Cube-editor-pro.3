package com.example.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.ui.theme.*
import com.example.viewmodel.CubeEditorViewModel
import com.example.viewmodel.ExportUiState
import com.example.viewmodel.formatTimeMs

@Composable
fun LyricsToolSheet(
    project: CubeProject,
    timeMs: Long,
    isPlaying: Boolean,
    playbackSpeed: Float,
    selectedLyricId: String?,
    vm: CubeEditorViewModel
) {
    val context = LocalContext.current
    var pasteText by remember { mutableStateOf("") }
    var tapSyncMode by remember { mutableStateOf(false) }
    var tapCursorIdx by remember { mutableIntStateOf(0) }

    val sortedLyrics = remember(project.lyrics) { project.lyrics.sortedBy { it.startMs } }
    val selectedLyric = sortedLyrics.firstOrNull { it.id == selectedLyricId } ?: sortedLyrics.firstOrNull()

    val txtFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val content = runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() }.getOrNull()
            if (!content.isNullOrBlank()) {
                vm.importLyricsText(content, replaceExisting = true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 460.dp)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Lyrics & Cube Sync", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(
                    onClick = { vm.autoRotateLyrics() },
                    label = { Text("Auto Rotate") },
                    leadingIcon = { Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("auto_rotate_lyrics_button")
                )
                AssistChip(
                    onClick = { tapSyncMode = !tapSyncMode; tapCursorIdx = 0 },
                    label = { Text(if (tapSyncMode) "Exit Sync" else "Tap Sync") }
                )
            }
        }

        if (tapSyncMode && sortedLyrics.isNotEmpty()) {
            val curLine = sortedLyrics.getOrElse(tapCursorIdx) { sortedLyrics.last() }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ElevatedSurface,
                modifier = Modifier.fillMaxWidth().border(1.dp, CyberCyan, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Tap Sync (${tapCursorIdx + 1}/${sortedLyrics.size})", style = MaterialTheme.typography.labelSmall, color = CyberCyan)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = playbackSpeed == 0.75f, onClick = { vm.setPlaybackSpeed(if (playbackSpeed == 0.75f) 1.0f else 0.75f) }, label = { Text("0.75x") })
                            TextButton(onClick = { vm.togglePlayPause() }) { Text(if (isPlaying) "Pause" else "Play") }
                        }
                    }
                    Text(curLine.text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Button(
                        onClick = {
                            vm.tapToSyncLine(curLine.id)
                            if (tapCursorIdx < sortedLyrics.lastIndex) tapCursorIdx++ else tapSyncMode = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                        modifier = Modifier.fillMaxWidth().height(56.dp).testTag("tap_sync_beat_button")
                    ) {
                        Text("TAP ON LINE START (${formatTimeMs(timeMs)})", color = VoidBlack, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Paste / Import Box
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = pasteText,
                onValueChange = { pasteText = it },
                placeholder = { Text("Paste lyrics, .LRC [00:12.40], or .SRT…") },
                maxLines = 3,
                modifier = Modifier.weight(1f).testTag("lyrics_paste_input")
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    onClick = {
                        vm.importLyricsText(pasteText, replaceExisting = sortedLyrics.isEmpty())
                        pasteText = ""
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                    modifier = Modifier.testTag("import_lyrics_button")
                ) { Text("Add") }
                OutlinedButton(
                    onClick = { txtFilePicker.launch(arrayOf("text/*", "*/*")) },
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) { Text(".LRC") }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.autoTimingFromBeats() }) {
                Text("Beat Auto-Time")
            }
            if (selectedLyric != null) {
                OutlinedButton(onClick = { vm.splitLyricAtPlayhead(selectedLyric.id) }) {
                    Text("Split @ Playhead")
                }
            }
        }

        // Line List + Per-Lyric Cube Controls
        sortedLyrics.forEach { line ->
            val sel = line.id == selectedLyric?.id
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (sel) ElectricViolet.copy(alpha = 0.22f) else ElevatedSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, if (sel) CyberCyan else SurfaceBorder, RoundedCornerShape(10.dp))
                    .clickable {
                        vm.selectLyric(line.id)
                        vm.seekTo(line.startMs)
                    }
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(line.text, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text("${formatTimeMs(line.startMs)} • ${line.targetFace.label}", style = MaterialTheme.typography.labelSmall, color = CyberCyan)
                        IconButton(onClick = { vm.deleteLyricLine(line.id) }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Rounded.Close, "Delete lyric", tint = TextMuted, modifier = Modifier.size(16.dp))
                        }
                    }

                    if (sel) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FaceSide.entries.take(4).forEach { f ->
                                FilterChip(
                                    selected = line.targetFace == f,
                                    onClick = { vm.updateLyricLine(line.id) { it.copy(targetFace = f) } },
                                    label = { Text(f.shortLabel) }
                                )
                            }
                            TextButton(onClick = { vm.updateLyricLine(line.id) { it.copy(startMs = (it.startMs - 50L).coerceAtLeast(0L)) } }) { Text("-50ms") }
                            TextButton(onClick = { vm.updateLyricLine(line.id) { it.copy(startMs = it.startMs + 50L) } }) { Text("+50ms") }
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(RotationDirection.entries) { dir ->
                                FilterChip(
                                    selected = line.direction == dir,
                                    onClick = { vm.updateLyricLine(line.id) { it.copy(direction = dir) } },
                                    label = { Text(dir.label) }
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Spins: ${line.extraSpins}  •  Zoom: ${"%.2f".format(line.zoomPeak)}x", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Row {
                                TextButton(onClick = { vm.updateLyricLine(line.id) { it.copy(extraSpins = (it.extraSpins + 1) % 3) } }) { Text("+Spin") }
                                TextButton(onClick = { vm.applySettingsToAllLyrics(line) }) { Text("Apply All", color = CyberCyan) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun KeyframeAndMotionSheet(
    project: CubeProject,
    timeMs: Long,
    selectedKeyframeId: String?,
    showMotionPresetsFirst: Boolean,
    vm: CubeEditorViewModel
) {
    var presetDuration by remember { mutableFloatStateOf(700f) }
    var presetIntensity by remember { mutableFloatStateOf(1.0f) }
    val selectedKf = project.keyframes.firstOrNull { it.id == selectedKeyframeId }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 460.dp)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(if (showMotionPresetsFirst) "Motion Presets (12)" else "Keyframes (${project.keyframes.size})", style = MaterialTheme.typography.titleMedium)
            Button(
                onClick = { vm.recordKeyframeAtPlayhead() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD600)),
                modifier = Modifier.testTag("record_keyframe_button")
            ) {
                Icon(Icons.Rounded.Diamond, null, tint = VoidBlack, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Record @ ${formatTimeMs(timeMs)}", color = VoidBlack, fontWeight = FontWeight.Bold)
            }
        }

        // Motion Presets Grid
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Dur ${presetDuration.toInt()}ms", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(82.dp))
            Slider(value = presetDuration, onValueChange = { presetDuration = it }, valueRange = 250f..2000f, modifier = Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Power ${"%.1f".format(presetIntensity)}x", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(82.dp))
            Slider(value = presetIntensity, onValueChange = { presetIntensity = it }, valueRange = 0.5f..2.0f, modifier = Modifier.weight(1f))
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(MotionPresetType.entries) { preset ->
                AssistChip(
                    onClick = { vm.applyMotionPreset(preset, presetDuration.toLong(), presetIntensity) },
                    label = { Text(preset.label) },
                    modifier = Modifier.testTag("motion_preset_${preset.name}")
                )
            }
        }

        if (selectedKf != null) {
            Surface(shape = RoundedCornerShape(12.dp), color = ElevatedSurface, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Selected ◆ @ ${formatTimeMs(selectedKf.timeMs)}", style = MaterialTheme.typography.titleSmall, color = CyberCyan)
                        Row {
                            FilterChip(
                                selected = selectedKf.hold,
                                onClick = { vm.updateKeyframe(selectedKf.id) { it.copy(hold = !it.hold) } },
                                label = { Text("Hold") }
                            )
                            IconButton(onClick = { vm.deleteKeyframe(selectedKf.id) }) {
                                Icon(Icons.Rounded.Delete, "Delete keyframe", tint = CrimsonDanger)
                            }
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(EasingType.entries) { e ->
                            FilterChip(
                                selected = selectedKf.easing == e,
                                onClick = { vm.updateKeyframe(selectedKf.id) { it.copy(easing = e) } },
                                label = { Text(e.label) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BackgroundAndEffectsSheet(
    project: CubeProject,
    vm: CubeEditorViewModel
) {
    val bgPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.setBackgroundImageUri(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 460.dp)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Background & Visual Effects", style = MaterialTheme.typography.titleMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = project.background.type == BackgroundType.BLACK,
                onClick = { vm.updateBackground { it.copy(type = BackgroundType.BLACK, primaryColorHex = "#000000") } },
                label = { Text("Pure Black") }
            )
            FilterChip(
                selected = project.background.type == BackgroundType.GRADIENT,
                onClick = { vm.updateBackground { it.copy(type = BackgroundType.GRADIENT, primaryColorHex = "#0B0B14", secondaryColorHex = "#251447") } },
                label = { Text("Violet Grad") }
            )
            FilterChip(
                selected = project.background.type == BackgroundType.IMAGE,
                onClick = { bgPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                label = { Text("Pick Image") }
            )
        }

        HorizontalDivider(color = SurfaceBorder)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Shutter ${project.effects.shutterAngleDeg.toInt()}°", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(88.dp))
            Slider(
                value = project.effects.shutterAngleDeg,
                onValueChange = { v -> vm.updateEffects { it.copy(shutterAngleDeg = v) } },
                valueRange = 0f..360f,
                modifier = Modifier.weight(1f)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Vignette ${(project.effects.vignetteStrength * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(88.dp))
            Slider(
                value = project.effects.vignetteStrength,
                onValueChange = { v -> vm.updateEffects { it.copy(vignetteStrength = v) } },
                valueRange = 0f..0.9f,
                modifier = Modifier.weight(1f)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = project.effects.beatPulseEnabled,
                onClick = { vm.updateEffects { it.copy(beatPulseEnabled = !it.beatPulseEnabled) } },
                label = { Text("Beat Pulse") }
            )
            FilterChip(
                selected = project.effects.glowEnabled,
                onClick = { vm.updateEffects { it.copy(glowEnabled = !it.glowEnabled) } },
                label = { Text("Studio Glow") }
            )
            FilterChip(
                selected = project.effects.chromaticAberrationEnabled,
                onClick = { vm.updateEffects { it.copy(chromaticAberrationEnabled = !it.chromaticAberrationEnabled) } },
                label = { Text("RGB Beat") }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = project.effects.filmGrainStrength > 0.05f,
                onClick = { vm.updateEffects { it.copy(filmGrainStrength = if (it.filmGrainStrength > 0.05f) 0f else 0.4f) } },
                label = { Text("Film Grain") }
            )
            FilterChip(
                selected = project.effects.shakeStrength > 0.05f,
                onClick = { vm.updateEffects { it.copy(shakeStrength = if (it.shakeStrength > 0.05f) 0f else 0.35f) } },
                label = { Text("Cam Shake") }
            )
            FilterChip(
                selected = project.karaoke.enabled,
                onClick = { vm.updateKaraoke { it.copy(enabled = !it.enabled) } },
                label = { Text("Karaoke Pill") }
            )
        }
    }
}

@Composable
fun CreditAndWatermarkSheet(
    project: CubeProject,
    vm: CubeEditorViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Credit & Outro Watermark", style = MaterialTheme.typography.titleMedium)
            Switch(checked = project.credit.enabled, onCheckedChange = { e -> vm.updateCredit { it.copy(enabled = e) } })
        }

        OutlinedTextField(
            value = project.credit.handle,
            onValueChange = { h -> vm.updateCredit { it.copy(handle = h) } },
            label = { Text("Creator Handle (e.g. @name)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = project.credit.subtitle,
            onValueChange = { s -> vm.updateCredit { it.copy(subtitle = s) } },
            label = { Text("Subtitle / Song Credit") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CreditCorner.entries.forEach { corner ->
                FilterChip(
                    selected = project.credit.corner == corner,
                    onClick = { vm.updateCredit { it.copy(corner = corner) } },
                    label = { Text(corner.label) }
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Scale to Center Outro", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = project.credit.outroCenterEnabled, onCheckedChange = { v -> vm.updateCredit { it.copy(outroCenterEnabled = v) } })
        }
    }
}

@Composable
fun ExportSheet(
    project: CubeProject,
    exportState: ExportUiState,
    vm: CubeEditorViewModel
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Export 3D MP4 Video", style = MaterialTheme.typography.titleMedium)

        when (exportState) {
            is ExportUiState.Exporting -> {
                val p = exportState.progress
                Surface(shape = RoundedCornerShape(14.dp), color = ElevatedSurface, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(p.stageLabel, style = MaterialTheme.typography.titleSmall, color = CyberCyan)
                        LinearProgressIndicator(
                            progress = { p.progressFraction.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = ElectricViolet
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${(p.progressFraction * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                            Text("ETA: ${p.etaSeconds}s", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                        OutlinedButton(onClick = { vm.cancelExport() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Cancel Export", color = CrimsonDanger)
                        }
                    }
                }
            }
            is ExportUiState.Success -> {
                Surface(shape = RoundedCornerShape(14.dp), color = EmeraldSuccess.copy(alpha = 0.16f), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("MP4 Ready & Saved to Movies/CubeEditor!", style = MaterialTheme.typography.titleSmall, color = EmeraldSuccess)
                        Text("${exportState.result.width}x${exportState.result.height} • ${formatTimeMs(exportState.result.durationMs)}", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    exportState.shareUri?.let { uri ->
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "video/mp4"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share Cube Video"))
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Share, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Share")
                            }
                            OutlinedButton(onClick = { vm.dismissExportState() }) {
                                Text("Done")
                            }
                        }
                    }
                }
            }
            is ExportUiState.Error -> {
                Surface(shape = RoundedCornerShape(14.dp), color = CrimsonDanger.copy(alpha = 0.18f), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Export Issue: ${exportState.message}", style = MaterialTheme.typography.bodySmall, color = CrimsonDanger)
                        Button(onClick = { vm.startExport(fastPreview = true) }) { Text("Retry Fast Preview") }
                    }
                }
            }
            ExportUiState.Idle -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ResolutionPreset.entries.forEach { r ->
                        FilterChip(
                            selected = project.exportConfig.resolution == r,
                            onClick = { vm.updateProjectSettings { it.copy(exportConfig = it.exportConfig.copy(resolution = r)) } },
                            label = { Text(r.label) }
                        )
                    }
                    listOf(30, 60).forEach { f ->
                        FilterChip(
                            selected = project.exportConfig.fps == f,
                            onClick = { vm.updateProjectSettings { it.copy(exportConfig = it.exportConfig.copy(fps = f)) } },
                            label = { Text("${f}fps") }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1 to "Blur Off", 4 to "4x Blur", 8 to "8x Blur").forEach { (sf, lbl) ->
                        FilterChip(
                            selected = project.exportConfig.motionBlurSubFrames == sf,
                            onClick = { vm.updateProjectSettings { it.copy(exportConfig = it.exportConfig.copy(motionBlurSubFrames = sf)) } },
                            label = { Text(lbl) }
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Include Synced Audio", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = project.exportConfig.includeAudio,
                        onCheckedChange = { inc -> vm.updateProjectSettings { it.copy(exportConfig = it.exportConfig.copy(includeAudio = inc)) } }
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { vm.startExport(fastPreview = true) },
                        modifier = Modifier.weight(1f).height(48.dp).testTag("fast_preview_export_button")
                    ) {
                        Text("Fast Preview")
                    }
                    Button(
                        onClick = { vm.startExport(fastPreview = false) },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                        modifier = Modifier.weight(1.3f).height(48.dp).testTag("start_mp4_export_button")
                    ) {
                        Icon(Icons.Rounded.Download, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Export MP4")
                    }
                }
            }
        }
    }
}
