package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CubeProject
import com.example.ui.theme.*
import com.example.viewmodel.formatTimeMs
import kotlin.math.abs
import kotlin.math.roundToLong

@Composable
fun MultiTrackTimeline(
    project: CubeProject,
    timeMs: Long,
    selectedCubeId: String?,
    selectedLyricId: String?,
    selectedKeyframeId: String?,
    snapToBeat: Boolean,
    onSeek: (Long) -> Unit,
    onSelectCube: (String) -> Unit,
    onSelectLyric: (String) -> Unit,
    onSelectKeyframe: (String) -> Unit,
    onToggleBeatSnap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalDurationMs = project.computedDurationMs().coerceAtLeast(5000L)
    var pxPerSec by remember { mutableFloatStateOf(54f) }

    val beats = project.music?.beatMarkersMs.orEmpty()
    val peaks = project.music?.waveformPeaks.orEmpty()
    val activeKeyframes = remember(project.keyframes, selectedCubeId) {
        project.keyframes.filter { selectedCubeId == null || it.cubeId == selectedCubeId }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ObsidianSurface)
            .border(width = 1.dp, color = SurfaceBorder)
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = formatTimeMs(timeMs),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = CyberCyan
                )
                Text(
                    text = "/ ${formatTimeMs(totalDurationMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (project.cubes.size > 1) {
                    val activeCube = project.cubes.firstOrNull { it.id == selectedCubeId }
                    Text(
                        text = activeCube?.name ?: "All Cubes",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricVioletLight,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(ElectricViolet.copy(alpha = 0.16f))
                            .clickable {
                                val idx = project.cubes.indexOfFirst { it.id == selectedCubeId }
                                val next = project.cubes[(idx + 1) % project.cubes.size]
                                onSelectCube(next.id)
                            }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Text(
                    text = if (snapToBeat) "Beat Snap ON" else "Beat Snap OFF",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (snapToBeat) AmberWarning else TextMuted,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (snapToBeat) AmberWarning.copy(alpha = 0.14f) else ElevatedSurface)
                        .clickable(onClick = onToggleBeatSnap)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                        .testTag("timeline_beat_snap_toggle")
                )
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(136.dp)
                .testTag("timeline_scrub_area")
                .pointerInput(totalDurationMs, pxPerSec) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        if (zoom != 1f) {
                            pxPerSec = (pxPerSec * zoom).coerceIn(24f, 180f)
                        }
                        if (pan.x != 0f) {
                            val dMs = (-pan.x / pxPerSec * 1000f).roundToLong()
                            onSeek((timeMs + dMs).coerceIn(0L, totalDurationMs))
                        }
                    }
                }
                .pointerInput(totalDurationMs, pxPerSec, selectedLyricId, activeKeyframes) {
                    detectTapGestures { offset ->
                        val centerPx = size.width * 0.5f
                        val tappedMs = (timeMs + ((offset.x - centerPx) / pxPerSec * 1000f).roundToLong())
                            .coerceIn(0L, totalDurationMs)

                        val cubeTrackTop = 20f * density
                        val cubeTrackBottom = 54f * density
                        val lyricTrackBottom = 92f * density

                        when {
                            offset.y in cubeTrackTop..cubeTrackBottom -> {
                                val hitKf = activeKeyframes.minByOrNull { abs(it.timeMs - tappedMs) }
                                if (hitKf != null && abs(hitKf.timeMs - tappedMs) < 350L) {
                                    onSelectKeyframe(hitKf.id)
                                    onSeek(hitKf.timeMs)
                                    return@detectTapGestures
                                }
                            }
                            offset.y in cubeTrackBottom..lyricTrackBottom -> {
                                val hitLyric = project.lyrics.firstOrNull { tappedMs in it.startMs..it.endMs }
                                if (hitLyric != null) {
                                    onSelectLyric(hitLyric.id)
                                    onSeek(hitLyric.startMs)
                                    return@detectTapGestures
                                }
                            }
                        }
                        onSeek(tappedMs)
                    }
                }
        ) {
            val widthPx = constraints.maxWidth.toFloat()
            val centerX = widthPx * 0.5f
            val densityLocal = LocalDensity.current

            Column(
                modifier = Modifier
                    .width(56.dp)
                    .fillMaxHeight()
                    .background(VoidBlack.copy(alpha = 0.75f))
                    .padding(start = 6.dp, top = 22.dp, bottom = 6.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                TimelineTrackBadge(Icons.Rounded.ViewInAr, "Cube", TrackCubeColor)
                TimelineTrackBadge(Icons.Rounded.Subtitles, "Lyric", TrackLyricColor)
                TimelineTrackBadge(Icons.Rounded.GraphicEq, "Music", TrackMusicColor)
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val h = size.height
                val w = size.width
                val cx = w * 0.5f

                fun msToX(ms: Long): Float = cx + ((ms - timeMs) / 1000f) * pxPerSec

                val stepMs = if (pxPerSec >= 80f) 500L else 1000L
                val minVisMs = (timeMs - (cx / pxPerSec * 1000f).toLong()).coerceAtLeast(0L)
                val maxVisMs = (timeMs + ((w - cx) / pxPerSec * 1000f).toLong()).coerceAtMost(totalDurationMs)
                var tick = (minVisMs / stepMs) * stepMs
                while (tick <= maxVisMs) {
                    val x = msToX(tick)
                    val isMajor = tick % 2000L == 0L
                    drawLine(
                        color = if (isMajor) TextSecondary else SurfaceBorder,
                        start = Offset(x, 0f),
                        end = Offset(x, if (isMajor) 12.dp.toPx() else 7.dp.toPx()),
                        strokeWidth = 1.5f
                    )
                    tick += stepMs
                }

                beats.forEach { beatMs ->
                    if (beatMs in minVisMs..maxVisMs) {
                        val bx = msToX(beatMs)
                        drawLine(
                            color = AmberWarning.copy(alpha = 0.24f),
                            start = Offset(bx, 16.dp.toPx()),
                            end = Offset(bx, h - 4.dp.toPx()),
                            strokeWidth = 1.5f
                        )
                    }
                }

                // Track 1: Cube keyframe track
                val t1Top = 20.dp.toPx()
                val t1H = 28.dp.toPx()
                val leftEdge = msToX(0L).coerceAtLeast(0f)
                val rightEdge = msToX(totalDurationMs).coerceAtMost(w)
                if (rightEdge > leftEdge) {
                    drawRoundRect(
                        color = TrackCubeColor.copy(alpha = 0.16f),
                        topLeft = Offset(leftEdge, t1Top),
                        size = Size(rightEdge - leftEdge, t1H),
                        cornerRadius = CornerRadius(6.dp.toPx())
                    )
                }
                activeKeyframes.forEach { kf ->
                    if (kf.timeMs in (minVisMs - 500L)..(maxVisMs + 500L)) {
                        val kx = msToX(kf.timeMs)
                        val ky = t1Top + t1H * 0.5f
                        val r = if (kf.id == selectedKeyframeId) 7.dp.toPx() else 5.5.dp.toPx()
                        val diamond = Path().apply {
                            moveTo(kx, ky - r)
                            lineTo(kx + r, ky)
                            lineTo(kx, ky + r)
                            lineTo(kx - r, ky)
                            close()
                        }
                        drawPath(
                            diamond,
                            color = if (kf.id == selectedKeyframeId) CyberCyan else if (kf.isGeneratedFromLyric) TrackCubeColor else AmberWarning
                        )
                    }
                }

                // Track 2: Lyrics blocks
                val t2Top = 54.dp.toPx()
                val t2H = 30.dp.toPx()
                project.lyrics.forEach { lyric ->
                    val x1 = msToX(lyric.startMs)
                    val x2 = msToX(lyric.endMs)
                    if (x2 >= 0f && x1 <= w) {
                        val selected = lyric.id == selectedLyricId
                        drawRoundRect(
                            color = if (selected) TrackLyricColor.copy(alpha = 0.55f) else TrackLyricColor.copy(alpha = 0.24f),
                            topLeft = Offset(x1, t2Top),
                            size = Size((x2 - x1).coerceAtLeast(8.dp.toPx()), t2H),
                            cornerRadius = CornerRadius(6.dp.toPx())
                        )
                    }
                }

                // Track 3: Audio waveform
                val t3Top = 90.dp.toPx()
                val t3H = 34.dp.toPx()
                if (project.music != null && peaks.isNotEmpty()) {
                    val mStart = project.music.timelineOffsetMs
                    val mDur = (project.music.trimEndMs - project.music.trimStartMs).coerceAtLeast(1000L)
                    val xStart = msToX(mStart)
                    val totalBarW = (mDur / 1000f) * pxPerSec
                    drawRoundRect(
                        color = TrackMusicColor.copy(alpha = 0.14f),
                        topLeft = Offset(xStart, t3Top),
                        size = Size(totalBarW, t3H),
                        cornerRadius = CornerRadius(6.dp.toPx())
                    )
                    val step = (peaks.size / 140).coerceAtLeast(1)
                    for (i in peaks.indices step step) {
                        val frac = i.toFloat() / peaks.size.coerceAtLeast(1)
                        val bx = xStart + frac * totalBarW
                        if (bx in 0f..w) {
                            val amp = (peaks[i].coerceIn(0.08f, 1f)) * (t3H * 0.42f)
                            val midY = t3Top + t3H * 0.5f
                            drawLine(
                                color = TrackMusicColor.copy(alpha = 0.85f),
                                start = Offset(bx, midY - amp),
                                end = Offset(bx, midY + amp),
                                strokeWidth = 2.dp.toPx()
                            )
                        }
                    }
                }

                // Fixed center playhead
                drawLine(
                    color = TextPrimary,
                    start = Offset(cx, 0f),
                    end = Offset(cx, h),
                    strokeWidth = 2.dp.toPx()
                )
                drawCircle(
                    color = CyberCyan,
                    radius = 4.5.dp.toPx(),
                    center = Offset(cx, 4.5.dp.toPx())
                )
            }

            project.lyrics.forEach { lyric ->
                val x1 = centerX + ((lyric.startMs - timeMs) / 1000f) * pxPerSec
                val x2 = centerX + ((lyric.endMs - timeMs) / 1000f) * pxPerSec
                if (x2 > 60f && x1 < widthPx - 20f && (x2 - x1) > 40f) {
                    Box(
                        modifier = Modifier
                            .offset(
                                x = with(densityLocal) { x1.coerceAtLeast(60f).toDp() },
                                y = 58.dp
                            )
                            .widthIn(max = with(densityLocal) { (x2 - x1.coerceAtLeast(60f)).coerceAtLeast(24f).toDp() })
                            .padding(horizontal = 4.dp)
                    ) {
                        Text(
                            text = lyric.text,
                            fontSize = 10.sp,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineTrackBadge(
    icon: ImageVector,
    label: String,
    tint: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(12.dp))
        Text(label, fontSize = 10.sp, color = TextSecondary, maxLines = 1)
    }
}
