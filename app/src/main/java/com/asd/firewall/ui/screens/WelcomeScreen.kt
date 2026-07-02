package com.asd.firewall.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asd.firewall.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── Data for onboarding pages ────────────────────────────────────

private data class OnboardPage(
    val icon: ImageVector,
    val color: Color,
    val title: String,
    val subtitle: String,
    val feature1: String,
    val feature2: String,
    val feature3: String,
)

private val pages = listOf(
    OnboardPage(
        icon      = Icons.Default.Shield,
        color     = AccentCyan,
        title     = "Zero-Root Protection",
        subtitle  = "Military-grade firewall without compromising your device",
        feature1  = "No root required — 100% Play compliant",
        feature2  = "VPN-based TUN packet interception",
        feature3  = "Persistent background protection like Spotify",
    ),
    OnboardPage(
        icon      = Icons.Default.Search,
        color     = AccentPurple,
        title     = "Deep Packet Inspection",
        subtitle  = "Our C++ DPI engine analyses every byte that leaves your device",
        feature1  = "Protocol-aware: TCP, UDP, ICMP, DNS",
        feature2  = "Port-scan detection and rate limiting",
        feature3  = "Anomaly scoring across 20+ threat vectors",
    ),
    OnboardPage(
        icon      = Icons.Default.Lock,
        color     = AccentGold,
        title     = "Tamper-Proof Ledger",
        subtitle  = "Every firewall event is SHA-256 chained — impossible to forge",
        feature1  = "Cryptographic event chain like a blockchain",
        feature2  = "Detects log tampering attempts instantly",
        feature3  = "Export verifiable security reports",
    ),
    OnboardPage(
        icon      = Icons.Default.Apps,
        color     = AccentGreen,
        title     = "Per-App Firewall",
        subtitle  = "Total control over which apps can reach the internet",
        feature1  = "Allow / Block / Monitor per installed app",
        feature2  = "Real-time traffic attribution by package",
        feature3  = "One-tap to silence any suspicious app",
    ),
)

// ── Welcome Screen ───────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WelcomeScreen(onFinished: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == pages.lastIndex

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        // Subtle animated radial gradient background
        val infiniteTransition = rememberInfiniteTransition(label = "bg")
        val glowAlpha by infiniteTransition.animateFloat(
            initialValue = 0.04f, targetValue = 0.10f,
            animationSpec = infiniteRepeatable(tween(3000), RepeatMode.Reverse),
            label = "glow"
        )
        val currentPage = pages[pagerState.currentPage]
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    colors = listOf(currentPage.color.copy(alpha = glowAlpha), BgDark),
                    radius = 900f
                )
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {

            // Skip button
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.TopEnd) {
                if (!isLastPage) {
                    TextButton(onClick = onFinished) {
                        Text("Skip", color = TextMuted, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            // Pager
            HorizontalPager(
                state    = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                OnboardPageContent(page = pages[page])
            }

            // Page indicators
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 24.dp)
            ) {
                repeat(pages.size) { i ->
                    val isSelected = pagerState.currentPage == i
                    val width by animateDpAsState(if (isSelected) 24.dp else 8.dp, label = "dot")
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(width)
                            .clip(CircleShape)
                            .background(if (isSelected) currentPage.color else TextDim)
                    )
                }
            }

            // CTA button
            AnimatedContent(targetState = isLastPage, label = "cta") { last ->
                Button(
                    onClick = {
                        if (last) onFinished()
                        else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    },
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(56.dp),
                    shape  = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (last) AccentGreen else AccentCyan,
                        contentColor   = BgDark,
                    ),
                    elevation = ButtonDefaults.buttonElevation(8.dp)
                ) {
                    Text(
                        if (last) "Enable Protection →" else "Next →",
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardPageContent(page: OnboardPage) {
    val infiniteTransition = rememberInfiniteTransition(label = "icon")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.95f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1400, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "pulse"
    )
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f, targetValue = 0.45f,
        animationSpec = infiniteRepeatable(tween(1600, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "ring"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Animated icon with concentric rings
        Box(
            modifier = Modifier.size(160.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Outer ring
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .drawBehind {
                        drawCircle(color = page.color.copy(alpha = ringAlpha * 0.5f), radius = size.minDimension / 2)
                    }
            )
            // Inner ring
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .drawBehind {
                        drawCircle(color = page.color.copy(alpha = ringAlpha), radius = size.minDimension / 2)
                    }
            )
            // Icon surface
            Surface(
                modifier  = Modifier.size(80.dp).scale(pulse),
                shape     = CircleShape,
                color     = page.color.copy(alpha = 0.15f),
                border    = BorderStroke(2.dp, page.color.copy(alpha = 0.6f)),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector        = page.icon,
                        contentDescription = null,
                        tint               = page.color,
                        modifier           = Modifier.size(40.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(40.dp))

        Text(
            text      = page.title,
            color     = TextBright,
            style     = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            textAlign  = TextAlign.Center,
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text      = page.subtitle,
            color     = TextMuted,
            style     = MaterialTheme.typography.bodyLarge,
            textAlign  = TextAlign.Center,
            lineHeight = 24.sp,
        )

        Spacer(Modifier.height(32.dp))

        // Feature bullets
        listOf(page.feature1, page.feature2, page.feature3).forEach { feature ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = page.color.copy(alpha = 0.15f),
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector        = Icons.Default.Check,
                            contentDescription = null,
                            tint               = page.color,
                            modifier           = Modifier.size(14.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(feature, color = TextMain, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
