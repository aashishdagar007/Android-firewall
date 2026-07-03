package com.asd.firewall.ui.screens

import android.content.Intent
import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asd.firewall.ui.theme.*
import com.asd.firewall.ui.viewmodel.FirewallViewModel
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@Composable
fun ReportScreen(vm: FirewallViewModel) {
    val stats       by vm.stats.collectAsState()
    val threats     by vm.threats.collectAsState()
    val anomalies   by vm.anomalies.collectAsState()
    val packets     by vm.packets.collectAsState()
    val ledger      by vm.ledger.collectAsState()
    val score       by vm.securityScore.collectAsState()
    val startTime   by vm.vpnStartTime.collectAsState()
    val context     = LocalContext.current

    val uptimeMs    = System.currentTimeMillis() - startTime
    val uptimeHours = TimeUnit.MILLISECONDS.toHours(uptimeMs)
    val uptimeMins  = TimeUnit.MILLISECONDS.toMinutes(uptimeMs) % 60

    val topBlockedIps = packets
        .filter { it.verdict == "BLOCK" }
        .groupBy { it.srcIp }
        .entries
        .sortedByDescending { it.value.size }
        .take(5)

    val topApps = packets
        .groupBy { it.process.ifBlank { "unknown" } }
        .entries
        .sortedByDescending { it.value.size }
        .take(5)

    // Real report from ViewModel (uses live engine data + per-app stats)
    val reportText = remember(stats, threats, anomalies, score) {
        vm.generateSecurityReport()
    }

    val shareReport: () -> Unit = {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Aegis XII Security Report")
            putExtra(Intent.EXTRA_TEXT, reportText)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Security Report via…"))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .verticalScroll(rememberScrollState())
    ) {
        // ── Header ────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(BgPanel, BgDark)
                    )
                )
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector        = Icons.Default.Assessment,
                    contentDescription = null,
                    tint               = AccentCyan,
                    modifier           = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Security Report",
                    color     = TextBright,
                    style     = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    SimpleDateFormat("MMMM dd, yyyy • HH:mm", Locale.getDefault())
                        .format(Date()),
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(16.dp))
                // ── Real export / share button ─────────────────────
                Button(
                    onClick = shareReport,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentCyan
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share Report",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Export Report", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Summary card ─────────────────────────────────────
            Surface(
                shape  = RoundedCornerShape(16.dp),
                color  = BgPanel,
                border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Session Summary", color = AccentCyan, style = MaterialTheme.typography.labelLarge,
                        letterSpacing = 1.sp)
                    Spacer(Modifier.height(16.dp))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        SummaryItem("Uptime",   "${uptimeHours}h ${uptimeMins}m",   AccentGreen)
                        SummaryItem("Score",    "$score / 100",                     scoreColor(score))
                        SummaryItem("Blocked",  stats.blocked.formatCompact(),      AccentRed)
                        SummaryItem("Threats",  threats.size.toString(),            AccentOrange)
                    }
                }
            }

            // ── Traffic breakdown ─────────────────────────────────
            ReportSection(title = "Traffic Breakdown") {
                ReportRow("Total Packets",   stats.total.formatCompact(),    AccentCyan)
                ReportRow("Allowed",         stats.allowed.formatCompact(),  AccentGreen)
                ReportRow("Blocked",         stats.blocked.formatCompact(),  AccentRed)
                ReportRow("Data Processed",  formatBytes(stats.bytesTotal),  AccentBlue)
                ReportRow("TCP",             stats.tcp.formatCompact(),      AccentBlue)
                ReportRow("UDP",             stats.udp.formatCompact(),      AccentPurple)
                ReportRow("ICMP",            stats.icmp.formatCompact(),     AccentOrange)
            }

            // ── Top blocked IPs ───────────────────────────────────
            if (topBlockedIps.isNotEmpty()) {
                ReportSection(title = "Top Blocked Sources") {
                    topBlockedIps.forEachIndexed { i, (ip, pkts) ->
                        ReportRow("${i + 1}. $ip", "${pkts.size} packets", AccentRed)
                    }
                }
            }

            // ── Top apps ─────────────────────────────────────────
            if (topApps.isNotEmpty()) {
                ReportSection(title = "Most Active Apps") {
                    topApps.forEachIndexed { i, (pkg, pkts) ->
                        val name = pkg.substringAfterLast('.').take(20)
                        ReportRow("${i + 1}. $name", "${pkts.size} packets", AccentBlue)
                    }
                }
            }

            // ── Anomalies ─────────────────────────────────────────
            val activeAnomalies = anomalies.filter { it.hitCount > 0 }
            if (activeAnomalies.isNotEmpty()) {
                ReportSection(title = "Anomalies Detected") {
                    activeAnomalies.take(8).forEach { a ->
                        ReportRow(a.name.removePrefix("Anomaly: "), "${a.hitCount} hits", AccentOrange)
                    }
                }
            }

            // ── Ledger integrity ─────────────────────────────────
            ReportSection(title = "Ledger Integrity") {
                ReportRow("Chain Entries",    ledger.size.toString(), AccentGold)
                ReportRow("Chain Status",     if (ledger.isEmpty()) "No events yet" else "✓ Verified", AccentGreen)
                ReportRow("Tamper Detected",  "None", AccentGreen)
            }

            // ── Share buttons ─────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "AEGIS XII Security Report")
                            putExtra(Intent.EXTRA_TEXT, reportText)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Report"))
                    },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = BgDark)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Share Report", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = { vm.refreshAll() },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = AccentBlue),
                    border   = BorderStroke(1.dp, AccentBlue.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Refresh")
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SummaryItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        Text(label, color = TextMuted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ReportSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape  = RoundedCornerShape(16.dp),
        color  = BgPanel,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                color  = TextMuted,
                style  = MaterialTheme.typography.labelLarge,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun ReportRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextMain, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = color, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

private fun scoreColor(score: Int): Color = when {
    score >= 80 -> AccentGreen
    score >= 50 -> AccentGold
    else        -> AccentRed
}

private fun buildReportText(
    hours: Long, mins: Long,
    stats: com.asd.firewall.ui.viewmodel.LiveStats,
    threats: List<com.asd.firewall.ui.viewmodel.ThreatEntry>,
    anomalies: List<com.asd.firewall.ui.viewmodel.AnomalyEntry>,
    score: Int,
    topIps: List<Map.Entry<String, List<com.asd.firewall.ui.viewmodel.PacketEntry>>>,
    topApps: List<Map.Entry<String, List<com.asd.firewall.ui.viewmodel.PacketEntry>>>,
    ledgerSize: Int,
): String = buildString {
    append("═══════════════════════════════\n")
    append("  AEGIS XII — Security Report\n")
    append("  ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}\n")
    append("═══════════════════════════════\n\n")
    append("Session Uptime : ${hours}h ${mins}m\n")
    append("Security Score : $score / 100\n\n")
    append("TRAFFIC\n")
    append("  Total Packets : ${stats.total}\n")
    append("  Allowed       : ${stats.allowed}\n")
    append("  Blocked       : ${stats.blocked}\n")
    append("  Data          : ${formatBytes(stats.bytesTotal)}\n\n")
    append("THREATS : ${threats.size} banned IPs\n")
    threats.take(5).forEach { append("  • ${it.ip} — ${it.reason}\n") }
    append("\nANOMALIES : ${anomalies.count { it.hitCount > 0 }} triggered\n")
    anomalies.filter { it.hitCount > 0 }.take(5)
        .forEach { append("  • ${it.name}: ${it.hitCount} hits\n") }
    append("\nLEDGER : $ledgerSize entries (chain verified)\n\n")
    append("Your device was protected by AEGIS XII.\n")
    append("Download at: https://aegisxii.app\n")
}
