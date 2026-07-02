package com.asd.firewall.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.asd.firewall.AegisVpnService
import com.asd.firewall.ui.Screen
import com.asd.firewall.ui.theme.*
import com.asd.firewall.ui.viewmodel.FirewallViewModel
import com.asd.firewall.ui.viewmodel.LiveStats
import java.util.concurrent.TimeUnit
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun DashboardScreen(vm: FirewallViewModel, navController: NavController) {
    val stats       by vm.stats.collectAsState()
    val history     by vm.statsHistory.collectAsState()
    val score       by vm.securityScore.collectAsState()
    val stealthMode by vm.stealthMode.collectAsState()
    val lastBlocked by vm.lastBlockedEntry.collectAsState()
    val vpnRunning  by AegisVpnService.isRunning.collectAsState()
    val startTime   by vm.vpnStartTime.collectAsState()

    val uptimeMs  = System.currentTimeMillis() - startTime
    val uptimeStr = buildString {
        val h = TimeUnit.MILLISECONDS.toHours(uptimeMs)
        val m = TimeUnit.MILLISECONDS.toMinutes(uptimeMs) % 60
        val s = TimeUnit.MILLISECONDS.toSeconds(uptimeMs) % 60
        if (h > 0) append("${h}h ")
        append("${m}m ${s}s")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── Hero Status Card ─────────────────────────────────────
        HeroStatusCard(
            vpnRunning  = vpnRunning,
            score       = score,
            uptimeStr   = uptimeStr,
            pps         = history.lastOrNull() ?: 0L,
            stealthMode = stealthMode,
        )

        // ── Alert Marquee ────────────────────────────────────────
        if (lastBlocked != null) {
            Surface(
                shape  = RoundedCornerShape(10.dp),
                color  = AccentRed.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, AccentRed.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Block, null, tint = AccentRed, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Last blocked: ${lastBlocked!!.srcIp} → ${lastBlocked!!.dstIp}:${lastBlocked!!.dstPort} (${lastBlocked!!.proto})",
                        color  = AccentRed,
                        style  = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                }
            }
        }

        // ── KPI Cards Row ────────────────────────────────────────
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KpiCard(Modifier.weight(1f), "TOTAL",   stats.total.formatCompact(),   AccentCyan)
            KpiCard(Modifier.weight(1f), "ALLOWED", stats.allowed.formatCompact(), AccentGreen)
            KpiCard(Modifier.weight(1f), "BLOCKED", stats.blocked.formatCompact(), AccentRed)
        }

        // ── Second row of KPIs ────────────────────────────────────
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KpiCard(Modifier.weight(1f), "TCP",  stats.tcp.formatCompact(),  AccentBlue)
            KpiCard(Modifier.weight(1f), "UDP",  stats.udp.formatCompact(),  AccentPurple)
            KpiCard(Modifier.weight(1f), "ICMP", stats.icmp.formatCompact(), AccentOrange)
        }

        // ── Traffic chart + Donut ─────────────────────────────────
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Sparkline
            AegisPanel(title = "Traffic Rate (60s)", modifier = Modifier.weight(1.5f)) {
                if (history.isNotEmpty()) {
                    SparklineChart(
                        data     = history.map { it.toFloat() },
                        color    = AccentCyan,
                        modifier = Modifier.fillMaxWidth().height(80.dp).padding(top = 8.dp)
                    )
                } else {
                    Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                        Text("Collecting...", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // Donut chart
            AegisPanel(title = "Allow/Block", modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(80.dp).padding(top = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    DonutChart(
                        allowRate = stats.allowRate,
                        blockRate = stats.blockRate,
                        modifier  = Modifier.size(70.dp)
                    )
                }
            }
        }

        // ── Quick Actions ─────────────────────────────────────────
        AegisPanel(title = "Quick Actions") {
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickChip(
                    label   = if (stealthMode) "Stealth ON" else "Stealth",
                    icon    = Icons.Default.VisibilityOff,
                    color   = AccentPurple,
                    active  = stealthMode,
                    onClick = { vm.setStealthMode(!stealthMode) },
                    modifier = Modifier.weight(1f)
                )
                QuickChip(
                    label   = "Map",
                    icon    = Icons.Default.Map,
                    color   = AccentRed,
                    active  = false,
                    onClick = { navController.navigate(Screen.ThreatMap.route) { launchSingleTop = true } },
                    modifier = Modifier.weight(1f)
                )
                QuickChip(
                    label   = "Apps",
                    icon    = Icons.Default.Apps,
                    color   = AccentGreen,
                    active  = false,
                    onClick = { navController.navigate(Screen.AppFirewall.route) { launchSingleTop = true } },
                    modifier = Modifier.weight(1f)
                )
                QuickChip(
                    label   = "Report",
                    icon    = Icons.Default.Assessment,
                    color   = AccentBlue,
                    active  = false,
                    onClick = { navController.navigate(Screen.Report.route) { launchSingleTop = true } },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ── Data processed + Block rate ───────────────────────────
        AegisPanel(title = "Protection Status") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "${(stats.blockRate * 100).toInt()}%",
                        color = if (stats.blockRate > 0.1f) AccentRed else AccentGreen,
                        style = MaterialTheme.typography.displayMedium.copy(fontSize = 36.sp)
                    )
                    Text("Block Rate", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatBytes(stats.bytesTotal), color = AccentCyan, style = MaterialTheme.typography.headlineMedium)
                    Text("Data Processed", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress         = { stats.blockRate.coerceIn(0f, 1f) },
                modifier         = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color            = AccentRed,
                trackColor       = BgPanel,
            )
        }

        // ── Quick Navigation ──────────────────────────────────────
        AegisPanel(title = "Quick Access") {
            val quickItems = listOf(
                Triple(Screen.Threats.route,     "Threats",     AccentRed),
                Triple(Screen.Anomalies.route,   "Anomalies",   AccentOrange),
                Triple(Screen.Connections.route, "Connections", AccentBlue),
                Triple(Screen.Ledger.route,      "Ledger",      AccentPurple),
            )
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                quickItems.forEach { (route, label, color) ->
                    OutlinedButton(
                        onClick = { navController.navigate(route) { launchSingleTop = true } },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
                        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
                        contentPadding = PaddingValues(6.dp)
                    ) {
                        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
                    }
                }
            }
        }
    }
}

// ── Hero Status Card with animated shield pulse ───────────────────

@Composable
private fun HeroStatusCard(
    vpnRunning: Boolean,
    score: Int,
    uptimeStr: String,
    pps: Long,
    stealthMode: Boolean,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shield")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = if (vpnRunning) 1.08f else 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "scale"
    )
    val ring1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.15f, targetValue = if (vpnRunning) 0.40f else 0.05f,
        animationSpec = infiniteRepeatable(tween(1600, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "r1"
    )
    val ring2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.08f, targetValue = if (vpnRunning) 0.25f else 0.03f,
        animationSpec = infiniteRepeatable(tween(2000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "r2"
    )

    val shieldColor = when {
        !vpnRunning    -> TextMuted
        stealthMode    -> AccentPurple
        score >= 80    -> AccentGreen
        score >= 50    -> AccentGold
        else           -> AccentRed
    }

    val scoreColor = when {
        score >= 80 -> AccentGreen
        score >= 50 -> AccentGold
        else        -> AccentRed
    }

    Surface(
        shape  = RoundedCornerShape(20.dp),
        color  = BgPanel,
        border = BorderStroke(1.dp, shieldColor.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Animated shield
            Box(
                modifier = Modifier.size(80.dp),
                contentAlignment = Alignment.Center
            ) {
                // Ring 2
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .drawBehind {
                            drawCircle(shieldColor.copy(alpha = ring2Alpha), radius = size.minDimension / 2)
                        }
                )
                // Ring 1
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .drawBehind {
                            drawCircle(shieldColor.copy(alpha = ring1Alpha), radius = size.minDimension / 2)
                        }
                )
                // Icon
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = shieldColor,
                    modifier = Modifier
                        .size((40 * pulseScale).dp)
                )
            }

            Spacer(Modifier.width(16.dp))

            // Status text
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (vpnRunning) "Protected" else "Inactive",
                    color = shieldColor,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                )
                if (vpnRunning) {
                    Text("↑ ${pps} pkt/s", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                    Text("⏱ $uptimeStr", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                    if (stealthMode) {
                        Spacer(Modifier.height(2.dp))
                        Surface(shape = RoundedCornerShape(4.dp), color = AccentPurple.copy(alpha = 0.15f)) {
                            Text("STEALTH", color = AccentPurple, style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                } else {
                    Text("VPN is not active", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                }
            }

            // Security Score Arc
            if (vpnRunning) {
                Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                    SecurityScoreArc(score = score, color = scoreColor)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "$score",
                            color = scoreColor,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            lineHeight = 20.sp
                        )
                        Text("/ 100", color = TextDim, style = MaterialTheme.typography.labelSmall, lineHeight = 12.sp)
                    }
                }
            }
        }
    }
}

// ── Security Score Radial Arc ─────────────────────────────────────

@Composable
private fun SecurityScoreArc(score: Int, color: Color) {
    val animatedScore by animateFloatAsState(
        targetValue = score.toFloat() / 100f,
        animationSpec = tween(800, easing = EaseOutCubic),
        label = "score"
    )
    Canvas(modifier = Modifier.size(64.dp)) {
        val strokeWidth = 5.dp.toPx()
        val padding     = strokeWidth / 2
        val arcSize     = Size(size.width - padding * 2, size.height - padding * 2)
        val topLeft     = Offset(padding, padding)
        val startAngle  = 135f
        val sweepFull   = 270f

        // Background track
        drawArc(
            color      = Color(0xFF1A2540),
            startAngle = startAngle,
            sweepAngle = sweepFull,
            useCenter  = false,
            topLeft    = topLeft,
            size       = arcSize,
            style      = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        // Filled arc
        drawArc(
            color      = color,
            startAngle = startAngle,
            sweepAngle = sweepFull * animatedScore,
            useCenter  = false,
            topLeft    = topLeft,
            size       = arcSize,
            style      = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}

// ── Donut Chart ───────────────────────────────────────────────────

@Composable
private fun DonutChart(
    allowRate: Float,
    blockRate: Float,
    modifier: Modifier = Modifier,
) {
    val animAllow by animateFloatAsState(allowRate.coerceIn(0f, 1f), tween(600), label = "allow")
    val animBlock by animateFloatAsState(blockRate.coerceIn(0f, 1f), tween(600), label = "block")

    Canvas(modifier = modifier) {
        val stroke = 10.dp.toPx()
        val pad    = stroke / 2
        val rect   = Size(size.width - pad * 2, size.height - pad * 2)
        val tl     = Offset(pad, pad)

        // ALLOW arc
        drawArc(AccentGreen,  -90f, 360f * animAllow, false, tl, rect, style = Stroke(stroke, cap = StrokeCap.Butt))
        // BLOCK arc (starts after ALLOW)
        drawArc(AccentRed,    -90f + 360f * animAllow, 360f * animBlock, false, tl, rect, style = Stroke(stroke, cap = StrokeCap.Butt))
        // Idle/other
        val idle = 1f - animAllow - animBlock
        if (idle > 0.01f)
            drawArc(Color(0xFF1A2540), -90f + 360f * (animAllow + animBlock), 360f * idle, false, tl, rect, style = Stroke(stroke, cap = StrokeCap.Butt))
    }
}

// ── Quick Chip ────────────────────────────────────────────────────

@Composable
private fun QuickChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape    = RoundedCornerShape(10.dp),
        color    = if (active) color.copy(alpha = 0.18f) else BgDark,
        border   = BorderStroke(1.dp, color.copy(alpha = if (active) 0.7f else 0.3f)),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(3.dp))
            Text(label, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── Reusable panel ────────────────────────────────────────────────

@Composable
fun AegisPanel(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier  = modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        color     = BgPanel,
        border    = BorderStroke(1.dp, BorderColor),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text  = title,
                color = TextMuted,
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.5.sp)
            )
            content()
        }
    }
}

// ── KPI Card ─────────────────────────────────────────────────────

@Composable
fun KpiCard(modifier: Modifier = Modifier, label: String, value: String, color: Color) {
    Surface(
        modifier  = modifier,
        shape     = RoundedCornerShape(12.dp),
        color     = BgPanel,
        border    = BorderStroke(1.dp, color.copy(alpha = 0.25f)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(label, color = TextMuted, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            Text(value, color = color, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
    }
}

// ── Sparkline Chart ───────────────────────────────────────────────

@Composable
fun SparklineChart(data: List<Float>, color: Color, modifier: Modifier = Modifier) {
    val max = data.maxOrNull()?.coerceAtLeast(1f) ?: 1f
    Canvas(modifier = modifier) {
        if (data.size < 2) return@Canvas
        val w    = size.width
        val h    = size.height
        val step = w / (data.size - 1)
        val path = Path()
        data.forEachIndexed { i, v ->
            val x = i * step
            val y = h - (v / max) * h * 0.9f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        val fillPath = Path().also { it.addPath(path) }
        fillPath.lineTo((data.size - 1) * step, h)
        fillPath.lineTo(0f, h)
        fillPath.close()
        drawPath(fillPath, brush = Brush.verticalGradient(listOf(color.copy(alpha = 0.25f), Color.Transparent)))
    }
}

// ── Helpers ───────────────────────────────────────────────────────

fun Long.formatCompact(): String = when {
    this >= 1_000_000 -> "${this / 1_000_000}M"
    this >= 1_000     -> "${this / 1_000}K"
    else              -> this.toString()
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576     -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024         -> "%.1f KB".format(bytes / 1_024.0)
    else                   -> "$bytes B"
}
