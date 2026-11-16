package com.example.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ProjectTemplate
import com.example.model.*
import com.example.ui.theme.*
import com.example.viewmodel.formatTimeMs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    drafts: List<DraftSummary>,
    templates: List<ProjectTemplate>,
    settings: AppSettings,
    onOpenProject: (String) -> Unit,
    onCreateProject: (String, AspectRatioPreset, ResolutionPreset, Int, BackgroundType, String) -> Unit,
    onUseTemplate: (ProjectTemplate) -> Unit,
    onRenameDraft: (String, String) -> Unit,
    onDuplicateDraft: (String) -> Unit,
    onDeleteDraft: (String) -> Unit,
    onImportCubeProj: (Uri) -> Unit,
    onOpenSettings: () -> Unit
) {
    var showNewDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<DraftSummary?>(null) }
    var deleteTarget by remember { mutableStateOf<DraftSummary?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImportCubeProj(uri)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Brush.linearGradient(listOf(ElectricViolet, CyberCyan))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.ViewInAr, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text("Cube Editor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("3D Kinetic Lyric Studio", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { importLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.testTag("import_cubeproj_button")
                    ) {
                        Icon(Icons.Rounded.FileOpen, contentDescription = "Import .cubeproj", tint = TextSecondary)
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = TextPrimary)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                // Hero New Project CTA
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(ElectricViolet, Color(0xFF4834D4), Color(0xFF1E1E36))
                            )
                        )
                        .clickable { showNewDialog = true }
                        .padding(20.dp)
                        .testTag("new_project_button")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                color = Color.White.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    "NEW 3D PROJECT",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            Text(
                                "Naya Cube Project Shuru Karein",
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "9:16 Reels, 1080p, Multi-Cube & Beat Sync Ready",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White,
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Add, contentDescription = "Create New Project", tint = VoidBlack, modifier = Modifier.size(28.dp))
                            }
                        }
                    }
                }
            }

            // Templates Row
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Starter Templates", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text("1-tap load", style = MaterialTheme.typography.labelSmall, color = CyberCyan)
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(templates, key = { it.id }) { tpl ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = ElevatedSurface,
                                modifier = Modifier
                                    .width(210.dp)
                                    .border(1.dp, SurfaceBorder, RoundedCornerShape(16.dp))
                                    .clickable { onUseTemplate(tpl) }
                                    .testTag("template_${tpl.id}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = ElectricViolet.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            tpl.badge,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = CyberCyan,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                    Text(tpl.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(tpl.subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }

            // Recent Drafts
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Recent Drafts (${drafts.size})", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                }
            }

            if (drafts.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = ObsidianSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, SurfaceBorder, RoundedCornerShape(18.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.Widgets, contentDescription = null, tint = TextMuted, modifier = Modifier.size(44.dp))
                            Text("Abhi koi draft nahi hai", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Text(
                                "Upar 'Naya Cube Project' ya Starter Template par tap karein aur apna pehla 3D lyric video banayein.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            } else {
                items(drafts, key = { it.id }) { draft ->
                    DraftCard(
                        draft = draft,
                        onOpen = { onOpenProject(draft.id) },
                        onRename = { renameTarget = draft },
                        onDuplicate = { onDuplicateDraft(draft.id) },
                        onDelete = { deleteTarget = draft }
                    )
                }
            }
        }
    }

    if (showNewDialog) {
        NewProjectDialog(
            defaultResolution = settings.defaultResolution,
            defaultFps = settings.defaultFps,
            onDismiss = { showNewDialog = false },
            onCreate = { name, aspect, res, fps, bgType, bgColor ->
                showNewDialog = false
                onCreateProject(name, aspect, res, fps, bgType, bgColor)
            }
        )
    }

    renameTarget?.let { target ->
        var text by remember(target.id) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename Project") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text("Project Name") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRenameDraft(target.id, text)
                    renameTarget = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Draft Delete Karein?") },
            text = { Text("\"${target.name}\" permanently delete ho jayega.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteDraft(target.id)
                    deleteTarget = null
                }) { Text("Delete", color = CrimsonDanger) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun DraftCard(
    draft: DraftSummary,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val thumbBmp = remember(draft.thumbnailPath, draft.updatedAtMs) {
        draft.thumbnailPath?.let { path -> runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull() }
    }
    val dateStr = remember(draft.updatedAtMs) {
        SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(draft.updatedAtMs))
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ObsidianSurface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SurfaceBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen)
            .testTag("draft_card_${draft.id}")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .height(84.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(VoidBlack),
                contentAlignment = Alignment.Center
            ) {
                if (thumbBmp != null) {
                    Image(
                        bitmap = thumbBmp,
                        contentDescription = draft.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Rounded.ViewInAr, contentDescription = null, tint = ElectricViolet)
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(draft.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "$dateStr  •  ${formatTimeMs(draft.durationMs)}  •  ${draft.cubeCount} Cube",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (draft.hasMusic) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = EmeraldSuccess, modifier = Modifier.size(14.dp))
                            Text(
                                draft.musicTitle ?: "Audio",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldSuccess,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (draft.lyricCount > 0) {
                        Text("${draft.lyricCount} lines", style = MaterialTheme.typography.labelSmall, color = CyberCyan)
                    }
                }
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Options", tint = TextSecondary)
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menuExpanded = false; onRename() })
                    DropdownMenuItem(text = { Text("Duplicate") }, leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) }, onClick = { menuExpanded = false; onDuplicate() })
                    DropdownMenuItem(text = { Text("Delete", color = CrimsonDanger) }, leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = CrimsonDanger) }, onClick = { menuExpanded = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun NewProjectDialog(
    defaultResolution: ResolutionPreset,
    defaultFps: Int,
    onDismiss: () -> Unit,
    onCreate: (String, AspectRatioPreset, ResolutionPreset, Int, BackgroundType, String) -> Unit
) {
    var name by remember { mutableStateOf("My Cube Lyric") }
    var aspect by remember { mutableStateOf(AspectRatioPreset.RATIO_9_16) }
    var resolution by remember { mutableStateOf(defaultResolution) }
    var fps by remember { mutableStateOf(defaultFps) }
    var bgType by remember { mutableStateOf(BackgroundType.BLACK) }
    var bgColor by remember { mutableStateOf("#000000") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        title = { Text("New 3D Cube Project") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Project Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("new_project_name_input")
                )

                Text("Aspect Ratio", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AspectRatioPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = aspect == preset,
                            onClick = { aspect = preset },
                            label = { Text(preset.label) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Resolution", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ResolutionPreset.entries.forEach { r ->
                                FilterChip(selected = resolution == r, onClick = { resolution = r }, label = { Text(r.label) })
                            }
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("FPS", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(30, 60).forEach { f ->
                                FilterChip(selected = fps == f, onClick = { fps = f }, label = { Text("${f}") })
                            }
                        }
                    }
                }

                Text("Background", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = bgType == BackgroundType.BLACK, onClick = { bgType = BackgroundType.BLACK; bgColor = "#000000" }, label = { Text("Black") })
                    FilterChip(selected = bgType == BackgroundType.GRADIENT, onClick = { bgType = BackgroundType.GRADIENT; bgColor = "#0B0B12" }, label = { Text("Gradient") })
                    FilterChip(selected = bgType == BackgroundType.SOLID, onClick = { bgType = BackgroundType.SOLID; bgColor = "#121024" }, label = { Text("Dark Navy") })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name, aspect, resolution, fps, bgType, bgColor) },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                modifier = Modifier.testTag("confirm_create_project")
            ) {
                Text("Create & Open")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
