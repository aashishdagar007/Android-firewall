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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.asd.firewall.ui.Screen
import com.asd.firewall.ui.theme.*
import com.asd.firewall.ui.viewmodel.FirewallViewModel
import com.asd.firewall.ui.viewmodel.LiveStats

@Composable
fun DashboardScreen(vm: FirewallViewModel, navController: NavController) {
    val stats by vm.stats.collectAsState()
    val history by vm.statsHistory.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── KPI Cards Row ─────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            KpiCard(
                modifier = Modifier.weight(1f),
                label    = "TOTAL",
                value    = stats.total.formatCompact(),
                color    = AccentCyan,
            )
            KpiCard(
                modifier = Modifier.weight(1f),
                label    = "ALLOWED",
                value    = stats.allowed.formatCompact(),
                color    = AccentGreen,
            )
            KpiCard(
                modifier = Modifier.weight(1f),
                label    = "BLOCKED",
                value    = stats.blocked.formatCompact(),
                color    = AccentRed,
            )
        }

        // ── Second row of KPIs ───────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            KpiCard(modifier = Modifier.weight(1f), label = "TCP",  value = stats.tcp.formatCompact(),  color = AccentBlue)
            KpiCard(modifier = Modifier.weight(1f), label = "UDP",  value = stats.udp.formatCompact(),  color = AccentPurple)
            KpiCard(modifier = Modifier.weight(1f), label = "ICMP", value = stats.icmp.formatCompact(), color = AccentOrange)
        }

        // ── Traffic Sparkline ────────────────────────────────────
        AegisPanel(title = "Traffic Rate (60s)") {
            if (history.isNotEmpty()) {
                SparklineChart(
                    data     = history.map { it.toFloat() },
                    color    = AccentCyan,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .padding(top = 8.dp)
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Collecting data...", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // ── Block Rate Gauge ─────────────────────────────────────
        AegisPanel(title = "Protection Status") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text  = "${(stats.blockRate * 100).toInt()}%",
                        color = if (stats.blockRate > 0.1f) AccentRed else AccentGreen,
                        style = MaterialTheme.typography.displayMedium.copy(fontSize = 36.sp)
                    )
                    Text("Block Rate", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text  = formatBytes(stats.bytesTotal),
                        color = AccentCyan,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text("Data Processed", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                }
            }
            // Thin progress bar
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress         = { stats.blockRate.coerceIn(0f, 1f) },
                modifier         = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color            = AccentRed,
                trackColor       = BgPanel,
            )
        }

        // ── Quick Navigation ─────────────────────────────────────
        AegisPanel(title = "Quick Access") {
            val quickItems = listOf(
                Triple(Screen.Threats.route,     "Threats",     AccentRed),
                Triple(Screen.Anomalies.route,   "Anomalies",  AccentOrange),
                Triple(Screen.Connections.route, "Connections", AccentBlue),
                Triple(Screen.Ledger.route,      "Ledger",      AccentPurple),
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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

// ── Reusable components ──────────────────────────────────────────

@Composable
fun KpiCard(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier  = modifier,
        shape     = RoundedCornerShape(12.dp),
        color     = BgPanel,
        border    = BorderStroke(1.dp, color.copy(alpha = 0.25f)),
        tonalElevation = 0.dp,
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

@Composable
fun SparklineChart(
    data: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val max = data.maxOrNull()?.coerceAtLeast(1f) ?: 1f
    Canvas(modifier = modifier) {
        if (data.size < 2) return@Canvas
        val w = size.width
        val h = size.height
        val step = w / (data.size - 1)
        val path = Path()
        data.forEachIndexed { i, v ->
            val x = i * step
            val y = h - (v / max) * h * 0.9f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = color,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 2.dp.toPx(),
                cap   = StrokeCap.Round,
                join  = StrokeJoin.Round,
            )
        )
        // Fill gradient under the line
        val fillPath = Path().also { it.addPath(path) }
        fillPath.lineTo((data.size - 1) * step, h)
        fillPath.lineTo(0f, h)
        fillPath.close()
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(color.copy(alpha = 0.25f), Color.Transparent)
            )
        )
    }
}

// ── Helpers ──────────────────────────────────────────────────────
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
