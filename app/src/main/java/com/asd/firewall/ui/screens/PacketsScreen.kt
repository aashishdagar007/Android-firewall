package com.asd.firewall.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asd.firewall.ui.theme.*
import com.asd.firewall.ui.viewmodel.FirewallViewModel
import com.asd.firewall.ui.viewmodel.PacketEntry

@Composable
fun PacketsScreen(vm: FirewallViewModel) {
    val packets by vm.packets.collectAsState()
    var protoFilter by remember { mutableStateOf("ALL") }
    var verdictFilter by remember { mutableStateOf("ALL") }

    val filtered = remember(packets, protoFilter, verdictFilter) {
        packets.filter { p ->
            (protoFilter == "ALL" || p.proto == protoFilter) &&
            (verdictFilter == "ALL" || p.verdict == verdictFilter)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(BgDark)) {
        // Header
        Column(
            modifier = Modifier.fillMaxWidth().background(BgPanel).padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Live Packet Feed", color = AccentCyan, style = MaterialTheme.typography.labelLarge)
                Surface(shape = RoundedCornerShape(20.dp), color = AccentCyan.copy(alpha = 0.15f)) {
                    Text(
                        "${filtered.size} packets",
                        color = AccentCyan, style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            // Filter chips row
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("ALL", "TCP", "UDP", "ICMP").forEach { proto ->
                    val sel = protoFilter == proto
                    FilterChip(
                        selected = sel, onClick = { protoFilter = proto },
                        label = { Text(proto, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentBlue.copy(alpha = 0.25f),
                            selectedLabelColor = AccentBlue,
                        )
                    )
                }
                Spacer(Modifier.width(4.dp))
                listOf("ALL" to TextMuted, "ALLOW" to AccentGreen, "BLOCK" to AccentRed).forEach { (verdict, color) ->
                    val sel = verdictFilter == verdict
                    FilterChip(
                        selected = sel, onClick = { verdictFilter = verdict },
                        label = { Text(verdict, style = MaterialTheme.typography.labelSmall, color = if (sel) color else TextMuted) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = color.copy(alpha = 0.15f),
                        )
                    )
                }
            }
        }

        // Column headers
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            HeaderCell("TIME",    0.20f)
            HeaderCell("PROTO",   0.10f)
            HeaderCell("SRC",     0.22f)
            HeaderCell("DST",     0.22f)
            HeaderCell("PORT",    0.12f)
            HeaderCell("VERDICT", 0.14f)
        }
        HorizontalDivider(color = BorderColor, thickness = 1.dp)

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(filtered, key = { it.seq }) { packet ->
                AnimatedVisibility(
                    visible = true,
                    enter   = fadeIn() + slideInVertically { -it / 2 },
                ) {
                    Column {
                        PacketRow(packet)
                        HorizontalDivider(color = BorderLight, thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.HeaderCell(text: String, weight: Float) {
    Text(text, color = TextMuted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(weight))
}

@Composable
private fun PacketRow(packet: PacketEntry) {
    val verdictColor = if (packet.verdict == "ALLOW") AccentGreen else AccentRed
    val bgColor      = if (packet.verdict == "BLOCK") AccentRed.copy(alpha = 0.04f) else Color.Transparent
    val protoColor = when (packet.proto) {
        "TCP"  -> AccentBlue
        "UDP"  -> AccentPurple
        "ICMP" -> AccentOrange
        else   -> TextMuted
    }
    val time = packet.timestamp.takeLast(8).take(8)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(time,              color = TextMuted,  style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(0.20f))
        Text(packet.proto,     color = protoColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.10f))
        Text(packet.srcIp,     color = TextMain,   style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(0.22f))
        Text(packet.dstIp,     color = TextMuted,  style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(0.22f))
        Text(":${packet.dstPort}", color = TextMuted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(0.12f))
        Surface(
            shape    = RoundedCornerShape(4.dp),
            color    = verdictColor.copy(alpha = 0.15f),
            modifier = Modifier.weight(0.14f)
        ) {
            Text(
                packet.verdict.take(3), color = verdictColor,
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }
    }
}
