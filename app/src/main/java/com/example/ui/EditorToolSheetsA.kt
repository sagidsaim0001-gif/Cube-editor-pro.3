package com.example.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.audio.DeviceSong
import com.example.data.FontRegistry
import com.example.engine.FaceTextureRenderer
import com.example.model.*
import com.example.ui.theme.*
import com.example.viewmodel.ColorApplyTarget
import com.example.viewmodel.CubeEditorViewModel
import com.example.viewmodel.formatTimeMs

@Composable
fun MusicPickerSheet(
    project: CubeProject,
    deviceSongs: List<DeviceSong>,
    previewingSongId: Long?,
    vm: CubeEditorViewModel
) {
    val context = LocalContext.current
    val audioPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    var hasPerm by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, audioPerm) == PackageManager.PERMISSION_GRANTED)
    }
    var search by remember { mutableStateOf("") }
    var sortByTitle by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPerm = granted
        if (granted) vm.loadDeviceSongs()
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importAudioFileUri(uri)
    }

    LaunchedEffect(hasPerm) {
        if (hasPerm) vm.loadDeviceSongs()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 460.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Music & Beat Sync", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { filePicker.launch(arrayOf("audio/*")) },
                    modifier = Modifier.testTag("import_audio_file_button")
                ) {
                    Icon(Icons.Rounded.AudioFile, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Files")
                }
            }
        }

        project.music?.let { m ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ElevatedSurface,
                modifier = Modifier.fillMaxWidth().border(1.dp, TrackMusicColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Active: ${m.title}", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Beats detected: ${m.beatMarkersMs.size}  •  Trim ${formatTimeMs(m.trimStartMs)}–${formatTimeMs(m.trimEndMs)}", style = MaterialTheme.typography.labelSmall, color = EmeraldSuccess)
                        }
                        IconButton(onClick = { vm.removeMusicTrack() }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Remove song", tint = CrimsonDanger)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Vol ${(m.volume * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(62.dp))
                        Slider(
                            value = m.volume,
                            onValueChange = { v -> vm.updateMusicTrack { it.copy(volume = v) } },
                            valueRange = 0f..1f,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Trim Start: ${formatTimeMs(m.trimStartMs)}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                        Text("Fade In: ${m.fadeInMs}ms  Out: ${m.fadeOutMs}ms", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                    Slider(
                        value = m.trimStartMs.toFloat(),
                        onValueChange = { v ->
                            val s = v.toLong().coerceAtMost((m.durationMs - 2000L).coerceAtLeast(0L))
                            vm.updateMusicTrack { it.copy(trimStartMs = s, trimEndMs = maxOf(it.trimEndMs, s + 2000L)) }
                        },
                        valueRange = 0f..(m.durationMs.coerceAtLeast(3000L)).toFloat()
                    )
                }
            }
        }

        if (!hasPerm) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = ElevatedSurface,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("On-Device Songs Access", style = MaterialTheme.typography.titleSmall)
                    Text("Phone ke local gaane list karne ke liye audio permission dein, ya upar 'Files' button se bina permission koi bhi audio pick karein.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { permLauncher.launch(audioPerm) }, colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet)) {
                            Text("Allow Audio")
                        }
                        OutlinedButton(onClick = {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                        }) {
                            Text("App Settings")
                        }
                    }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Search song or artist…") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                FilterChip(selected = sortByTitle, onClick = { sortByTitle = !sortByTitle }, label = { Text(if (sortByTitle) "A-Z" else "Recent") })
            }

            val filtered = remember(deviceSongs, search, sortByTitle) {
                deviceSongs
                    .filter { search.isBlank() || it.title.contains(search, true) || it.artist.contains(search, true) }
                    .let { list -> if (sortByTitle) list.sortedBy { it.title } else list }
            }

            if (filtered.isEmpty()) {
                Text("Device me koi matching audio nahi mila. Upar 'Files' se import karein.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f, fill = false)) {
                    items(filtered, key = { it.id }) { song ->
                        val isPrev = previewingSongId == song.id
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = ElevatedSurface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                IconButton(onClick = { vm.togglePreviewSong(song) }, modifier = Modifier.size(38.dp)) {
                                    Icon(if (isPrev) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = "Preview", tint = CyberCyan)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(song.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${song.artist} • ${formatTimeMs(song.durationMs)}", style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 1)
                                }
                                Button(
                                    onClick = { vm.attachDeviceSong(song) },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet)
                                ) {
                                    Text("Use", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TextAndFontsSheet(
    project: CubeProject,
    selectedCube: SceneCube?,
    selectedFace: FaceSide,
    selectedWordId: String?,
    fontsTabActive: Boolean,
    vm: CubeEditorViewModel
) {
    val face = selectedCube?.faces?.get(selectedFace) ?: CubeFace(side = selectedFace)
    var newWordText by remember { mutableStateOf("") }
    var targetCorner by remember { mutableStateOf(CornerAnchor.TOP_LEFT) }
    val selectedWord = face.textItems.firstOrNull { it.id == selectedWordId } ?: face.textItems.lastOrNull()

    val allFourCornersFilled = remember(face.textItems) {
        CornerAnchor.entries.all { c -> face.textItems.any { it.corner == c } }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 460.dp)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("${selectedFace.label} Face • ${if (fontsTabActive) "Fonts & Style" else "Text & Layout"}", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(
                    onClick = { vm.randomizeSelectedFaceColors() },
                    label = { Text("Palette") },
                    leadingIcon = { Icon(Icons.Rounded.Palette, null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("randomize_colors_button")
                )
            }
        }

        if (allFourCornersFilled) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = CyberCyan.copy(alpha = 0.14f),
                modifier = Modifier.fillMaxWidth().clickable { vm.openTool(com.example.viewmodel.EditorTool.ADD_BOX) }
            ) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.AddBox, contentDescription = null, tint = CyberCyan)
                    Text("4 corners full! Naya box attach karne ke liye Add Box tap karein →", style = MaterialTheme.typography.bodySmall, color = CyberCyan)
                }
            }
        }

        // Add Word + 4-Corner Picker
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = newWordText,
                onValueChange = { newWordText = it },
                placeholder = { Text("Type word (English/हिंदी/اردو)…") },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("face_word_input")
            )
            Button(
                onClick = {
                    vm.addWordToSelectedFace(newWordText, targetCorner, selectedWord?.fontId)
                    newWordText = ""
                },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                modifier = Modifier.height(52.dp).testTag("add_word_button")
            ) {
                Text("Add")
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Corner:", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            CornerAnchor.entries.forEach { corner ->
                FilterChip(
                    selected = targetCorner == corner,
                    onClick = { targetCorner = corner },
                    label = { Text(corner.label) }
                )
            }
        }

        // Layout Mode & Auto Fit Buttons
        Text("Layout & Auto Fit", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(FaceLayoutMode.entries) { mode ->
                FilterChip(
                    selected = face.layoutMode == mode,
                    onClick = { vm.setFaceLayoutMode(mode) },
                    label = { Text(mode.label) },
                    modifier = Modifier.testTag("layout_mode_${mode.name}")
                )
            }
        }

        // Word Chips + Corner Arrow Mover
        if (face.textItems.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(face.textItems, key = { it.id }) { item ->
                    val active = item.id == selectedWord?.id
                    FilterChip(
                        selected = active,
                        onClick = { vm.selectWord(item.id) },
                        label = { Text("${item.corner.label}: ${item.text}") }
                    )
                }
            }
        }

        if (selectedWord != null) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = ElevatedSurface,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Edit: ${selectedWord.text}", style = MaterialTheme.typography.titleSmall, color = CyberCyan)
                        Row {
                            IconButton(onClick = { vm.reorderWordInFace(selectedWord.id, -1) }) { Icon(Icons.Rounded.ArrowUpward, "Move up") }
                            IconButton(onClick = { vm.reorderWordInFace(selectedWord.id, 1) }) { Icon(Icons.Rounded.ArrowDownward, "Move down") }
                            IconButton(onClick = { vm.duplicateWord(selectedWord.id) }) { Icon(Icons.Rounded.ContentCopy, "Duplicate") }
                            IconButton(onClick = { vm.deleteWord(selectedWord.id) }) { Icon(Icons.Rounded.Delete, "Delete", tint = CrimsonDanger) }
                        }
                    }

                    // 4-Direction Arrow Mover between corners
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Move Corner:", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        IconButton(onClick = {
                            val next = when (selectedWord.corner) {
                                CornerAnchor.TOP_RIGHT -> CornerAnchor.TOP_LEFT
                                CornerAnchor.BOTTOM_RIGHT -> CornerAnchor.BOTTOM_LEFT
                                else -> selectedWord.corner
                            }
                            vm.moveWordCorner(selectedWord.id, next)
                        }) { Icon(Icons.Rounded.ArrowBack, "Left Corner") }
                        IconButton(onClick = {
                            val next = when (selectedWord.corner) {
                                CornerAnchor.BOTTOM_LEFT -> CornerAnchor.TOP_LEFT
                                CornerAnchor.BOTTOM_RIGHT -> CornerAnchor.TOP_RIGHT
                                else -> selectedWord.corner
                            }
                            vm.moveWordCorner(selectedWord.id, next)
                        }) { Icon(Icons.Rounded.ArrowUpward, "Top Corner") }
                        IconButton(onClick = {
                            val next = when (selectedWord.corner) {
                                CornerAnchor.TOP_LEFT -> CornerAnchor.BOTTOM_LEFT
                                CornerAnchor.TOP_RIGHT -> CornerAnchor.BOTTOM_RIGHT
                                else -> selectedWord.corner
                            }
                            vm.moveWordCorner(selectedWord.id, next)
                        }) { Icon(Icons.Rounded.ArrowDownward, "Bottom Corner") }
                        IconButton(onClick = {
                            val next = when (selectedWord.corner) {
                                CornerAnchor.TOP_LEFT -> CornerAnchor.TOP_RIGHT
                                CornerAnchor.BOTTOM_LEFT -> CornerAnchor.BOTTOM_RIGHT
                                else -> selectedWord.corner
                            }
                            vm.moveWordCorner(selectedWord.id, next)
                        }) { Icon(Icons.Rounded.ArrowForward, "Right Corner") }
                    }

                    // Font Picker
                    Text("Bundled Fonts (13)", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(FontRegistry.fonts, key = { it.id }) { font ->
                            FilterChip(
                                selected = selectedWord.fontId == font.id,
                                onClick = { vm.updateWord(selectedWord.id) { it.copy(fontId = font.id) } },
                                label = { Text(font.displayName) }
                            )
                        }
                    }

                    // Word Color Swatches
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(KineticPaletteHex) { hex ->
                            val c = Color(FaceTextureRenderer.parseColorSafe(hex))
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .border(if (selectedWord.colorHex.equals(hex, true)) 2.5.dp else 1.dp, Color.White, CircleShape)
                                    .clickable { vm.updateWord(selectedWord.id) { it.copy(colorHex = hex) } }
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Size", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(64.dp))
                        Slider(
                            value = selectedWord.fontSizePx,
                            onValueChange = { v -> vm.updateWord(selectedWord.id) { it.copy(fontSizePx = v) } },
                            valueRange = 28f..160f,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Squeeze", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(64.dp))
                        Slider(
                            value = selectedWord.horizontalScaleX,
                            onValueChange = { v -> vm.updateWord(selectedWord.id) { it.copy(horizontalScaleX = v) } },
                            valueRange = 0.5f..1.4f,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Rotate", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(64.dp))
                        Slider(
                            value = selectedWord.rotationDeg,
                            onValueChange = { v -> vm.updateWord(selectedWord.id) { it.copy(rotationDeg = v) } },
                            valueRange = -180f..180f,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedWord.uppercase,
                            onClick = { vm.updateWord(selectedWord.id) { it.copy(uppercase = !it.uppercase) } },
                            label = { Text("UPPERCASE") }
                        )
                        FilterChip(
                            selected = selectedWord.strokeWidth > 0f,
                            onClick = { vm.updateWord(selectedWord.id) { it.copy(strokeWidth = if (it.strokeWidth > 0f) 0f else 4f) } },
                            label = { Text("Outline") }
                        )
                        FilterChip(
                            selected = selectedWord.shadowEnabled,
                            onClick = { vm.updateWord(selectedWord.id) { it.copy(shadowEnabled = !it.shadowEnabled) } },
                            label = { Text("Shadow") }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ColorSheet(
    selectedCube: SceneCube?,
    selectedFace: FaceSide,
    vm: CubeEditorViewModel
) {
    val face = selectedCube?.faces?.get(selectedFace) ?: CubeFace(side = selectedFace)
    var target by remember { mutableStateOf(ColorApplyTarget.THIS_FACE) }
    var hexInput by remember(face.backgroundColorHex) { mutableStateOf(face.backgroundColorHex) }
    var transparent by remember(face.transparent) { mutableStateOf(face.transparent) }
    var borderWidth by remember(face.borderWidth) { mutableFloatStateOf(face.borderWidth) }

    val swatches = listOf("#090910", "#000000", "#18102D", "#0E1C2F", "#2D0F1E", "#12261E", "#6C5CE7", "#00D2FF", "#FF4081", "#FFD600")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Face Colors & Border", style = MaterialTheme.typography.titleMedium)

        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(ColorApplyTarget.entries) { t ->
                FilterChip(selected = target == t, onClick = { target = t }, label = { Text(t.label) })
            }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(swatches) { hex ->
                val col = Color(FaceTextureRenderer.parseColorSafe(hex))
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(col)
                        .border(if (hexInput.equals(hex, true)) 2.5.dp else 1.dp, Color.White, CircleShape)
                        .clickable {
                            hexInput = hex
                            transparent = false
                            vm.applyFaceStyle(target, hex, null, false, face.borderColorHex, borderWidth)
                        }
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = hexInput,
                onValueChange = { hexInput = it },
                label = { Text("Hex") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = transparent,
                onClick = {
                    transparent = !transparent
                    vm.applyFaceStyle(target, hexInput, null, transparent, face.borderColorHex, borderWidth)
                },
                label = { Text("Transparent") }
            )
            Button(
                onClick = { vm.applyFaceStyle(target, hexInput, null, transparent, face.borderColorHex, borderWidth) },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet)
            ) {
                Text("Apply")
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Border", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(64.dp))
            Slider(
                value = borderWidth,
                onValueChange = { v ->
                    borderWidth = v
                    vm.applyFaceStyle(target, hexInput, face.gradientColorHex, transparent, face.borderColorHex, v)
                },
                valueRange = 0f..10f,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun AddBoxSheet(
    project: CubeProject,
    selectedCube: SceneCube?,
    vm: CubeEditorViewModel
) {
    val active = selectedCube ?: project.cubes.firstOrNull()
    var hingeMode by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Add & Link 3D Boxes", style = MaterialTheme.typography.titleMedium)
        Text("Kis face par naya box lock karna hai?", style = MaterialTheme.typography.bodySmall, color = TextSecondary)

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Hinge Mode (Fold along edge)", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = hingeMode, onCheckedChange = { hingeMode = it })
        }

        if (active != null) {
            // 6-Face Attach Picker
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FaceSide.entries.forEach { side ->
                    val occupied = project.cubes.any { it.parentId == active.id && it.parentFace == side }
                    OutlinedButton(
                        onClick = { vm.addBoxAttached(active.id, side, hingeMode) },
                        enabled = !occupied,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f).testTag("attach_face_${side.name}")
                    ) {
                        Text(if (occupied) "${side.label}✓" else side.label, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
        }

        HorizontalDivider(color = SurfaceBorder)
        Text("Scene Cubes (${project.cubes.size})", style = MaterialTheme.typography.labelMedium, color = CyberCyan)

        project.cubes.forEach { cube ->
            val isSel = cube.id == active?.id
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isSel) ElectricViolet.copy(alpha = 0.2f) else ElevatedSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, if (isSel) ElectricViolet else SurfaceBorder, RoundedCornerShape(12.dp))
                    .clickable { vm.selectCube(cube.id) }
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(cube.name, style = MaterialTheme.typography.titleSmall)
                        val rel = if (cube.parentId != null) "Attached to ${cube.parentFace?.label ?: "?"}${if (cube.hingeMode) " (Hinge)" else ""}" else "Root Cube"
                        Text(rel, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                    Row {
                        IconButton(onClick = { vm.toggleCubeTopBottom(cube.id) }) {
                            Icon(Icons.Rounded.Layers, contentDescription = "Toggle Top/Bottom", tint = if (cube.showTopBottom) CyberCyan else TextMuted)
                        }
                        IconButton(onClick = { vm.toggleCubeVisible(cube.id) }) {
                            Icon(if (cube.visible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff, contentDescription = "Visibility")
                        }
                        IconButton(onClick = { vm.toggleCubeLocked(cube.id) }) {
                            Icon(if (cube.locked) Icons.Rounded.Lock else Icons.Rounded.LockOpen, contentDescription = "Lock")
                        }
                        if (cube.parentId != null) {
                            IconButton(onClick = { vm.unlinkCube(cube.id) }) {
                                Icon(Icons.Rounded.LinkOff, contentDescription = "Unlink", tint = AmberWarning)
                            }
                        }
                        IconButton(onClick = { vm.duplicateCube(cube.id, includeChildren = false) }) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = "Duplicate")
                        }
                        if (project.cubes.size > 1) {
                            IconButton(onClick = { vm.deleteCube(cube.id, deleteChildren = false) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = CrimsonDanger)
                            }
                        }
                    }
                }
            }
        }
    }
}
