package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.*
import com.example.ui.theme.*
import com.example.viewmodel.CubeEditorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    vm: CubeEditorViewModel,
    onBack: () -> Unit
) {
    var cacheText by remember { mutableStateOf(vm.cacheSizeMbText()) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ObsidianSurface),
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_button")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Studio Settings", style = MaterialTheme.typography.titleMedium) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                SettingSectionCard("Appearance & Language") {
                    SettingSwitchRow(
                        title = "Pure AMOLED Black",
                        subtitle = "Battery bachane ke liye #000000 background",
                        checked = settings.amoledBlack,
                        onCheckedChange = { v -> vm.updateSettings { it.copy(amoledBlack = v) } }
                    )
                    HorizontalDivider(color = SurfaceBorder)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("UI Language", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppLanguage.entries.forEach { lang ->
                                FilterChip(
                                    selected = settings.language == lang,
                                    onClick = { vm.updateSettings { it.copy(language = lang) } },
                                    label = { Text(lang.label) }
                                )
                            }
                        }
                    }
                }
            }

            item {
                SettingSectionCard("Engine & Defaults") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Preview Quality", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PreviewQuality.entries.forEach { q ->
                                FilterChip(
                                    selected = settings.previewQuality == q,
                                    onClick = { vm.updateSettings { it.copy(previewQuality = q) } },
                                    label = { Text(q.label) }
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = SurfaceBorder)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Default Export Res", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                ResolutionPreset.entries.forEach { r ->
                                    FilterChip(
                                        selected = settings.defaultResolution == r,
                                        onClick = { vm.updateSettings { it.copy(defaultResolution = r) } },
                                        label = { Text(r.label) }
                                    )
                                }
                            }
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Default FPS", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(30, 60).forEach { f ->
                                    FilterChip(
                                        selected = settings.defaultFps == f,
                                        onClick = { vm.updateSettings { it.copy(defaultFps = f) } },
                                        label = { Text("$f") }
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = SurfaceBorder)
                    SettingSwitchRow(
                        title = "Snap to Beat",
                        subtitle = "Lyrics & keyframes automatically nearest beat par snap honge",
                        checked = settings.snapToBeat,
                        onCheckedChange = { v -> vm.updateSettings { it.copy(snapToBeat = v) } }
                    )
                    HorizontalDivider(color = SurfaceBorder)
                    SettingSwitchRow(
                        title = "Haptic Feedback",
                        subtitle = "Face snap aur keyframe par light vibration tick",
                        checked = settings.hapticsEnabled,
                        onCheckedChange = { v -> vm.updateSettings { it.copy(hapticsEnabled = v) } }
                    )
                }
            }

            item {
                SettingSectionCard("Storage & Offline Privacy") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Temp & Export Cache", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                            Text("Current usage: $cacheText", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        OutlinedButton(
                            onClick = {
                                vm.clearCacheStorage()
                                cacheText = vm.cacheSizeMbText()
                            },
                            modifier = Modifier.testTag("clear_cache_button")
                        ) {
                            Text("Clear Cache")
                        }
                    }
                    HorizontalDivider(color = SurfaceBorder)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Rounded.VerifiedUser, contentDescription = null, tint = EmeraldSuccess)
                        Text(
                            "100% On-Device Privacy: Koi server upload, ads, ya tracking nahi hai. Saare renders aur MP4 exports aapke phone par hi bante hain.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    HorizontalDivider(color = SurfaceBorder)
                    Button(
                        onClick = { vm.copyDebugInfoToClipboard() },
                        colors = ButtonDefaults.buttonColors(containerColor = ElevatedSurface),
                        modifier = Modifier.fillMaxWidth().testTag("copy_debug_button")
                    ) {
                        Icon(Icons.Rounded.BugReport, contentDescription = null, tint = CyberCyan)
                        Spacer(Modifier.width(8.dp))
                        Text("Copy Debug Diagnostics", color = TextPrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = ObsidianSurface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SurfaceBorder, RoundedCornerShape(18.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = CyberCyan)
            content()
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
