package com.example.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.theme.*
import kotlinx.coroutines.launch

private data class OnboardPage(
    val icon: ImageVector,
    val accent: Color,
    val badge: String,
    val title: String,
    val subtitle: String
)

@Composable
fun OnboardingScreen(
    onFinish: () -> Unit
) {
    val pages = listOf(
        OnboardPage(
            icon = Icons.Rounded.ViewInAr,
            accent = ElectricViolet,
            badge = "STEP 01",
            title = "3D Kinetic Cubes Banao",
            subtitle = "Har cube face par bold packed words rakho — 4 corners, Free Drag, ya 1-tap Poster Auto Fit ke saath. Multi-cube chains bhi attach kar sakte ho."
        ),
        OnboardPage(
            icon = Icons.Rounded.MusicNote,
            accent = CyberCyan,
            badge = "STEP 02",
            title = "Beat & Lyrics Par Sync Karo",
            subtitle = "Apne phone se song pick karo, Tap-to-Sync se har line mark karo aur Auto Rotate dabao — cube har beat par 3D motion blur ke saath ghumega."
        ),
        OnboardPage(
            icon = Icons.Rounded.MovieCreation,
            accent = EmeraldSuccess,
            badge = "STEP 03",
            title = "1080p MP4 Export Karo",
            subtitle = "100% offline deterministic render engine — crisp audio-video sync, sub-frame motion blur, credit outro aur direct Reels / Shorts share."
        )
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(VoidBlack, Color(0xFF121024), VoidBlack)
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.ViewInAr, contentDescription = null, tint = ElectricViolet)
                Text("CUBE EDITOR", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            }
            TextButton(
                onClick = onFinish,
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .testTag("skip_onboarding_button")
            ) {
                Text("Skip", color = TextSecondary)
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 56.dp, bottom = 92.dp)
        ) { index ->
            val p = pages[index]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(136.dp)
                        .clip(RoundedCornerShape(34.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(p.accent.copy(alpha = 0.28f), ObsidianSurface)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = p.icon,
                        contentDescription = p.title,
                        tint = p.accent,
                        modifier = Modifier.size(64.dp)
                    )
                }
                Spacer(Modifier.height(28.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = p.accent.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = p.badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = p.accent,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = p.title,
                    style = MaterialTheme.typography.displaySmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = p.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(pages.size) { i ->
                    val active = pagerState.currentPage == i
                    val color by animateColorAsState(if (active) ElectricViolet else SurfaceBorder, label = "dot")
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(if (active) 24.dp else 8.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }

            Button(
                onClick = {
                    if (pagerState.currentPage < pages.lastIndex) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    } else {
                        onFinish()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("onboarding_next_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet)
            ) {
                Text(
                    text = if (pagerState.currentPage < pages.lastIndex) "Aage Badho" else "Studio Start Karein",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
            }
        }
    }
}
