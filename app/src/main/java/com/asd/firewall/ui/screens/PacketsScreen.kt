package com.asd.firewall.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        // Header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgPanel)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text  = "Live Packet Feed",
                color = AccentCyan,
                style = MaterialTheme.typography.labelLarge
            )
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = AccentCyan.copy(alpha = 0.15f),
            ) {
                Text(
                    text     = "${packets.size} packets",
                    color    = AccentCyan,
                    style    = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }

        // Column headers
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            HeaderCell("TIME",    0.20f)
            HeaderCell("PROTO",   0.10f)
            HeaderCell("SRC",     0.22f)
            HeaderCell("DST",     0.22f)
            HeaderCell("PORT",    0.12f)
            HeaderCell("VERDICT", 0.14f)
        }
        Divider(color = BorderColor, thickness = 1.dp)

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(packets, key = { it.seq }) { packet ->
                PacketRow(packet)
                Divider(color = BorderLight, thickness = 0.5.dp)
            }
        }
    }
}

@Composable
private fun RowScope.HeaderCell(text: String, weight: Float) {
    Text(
        text     = text,
        color    = TextMuted,
        style    = MaterialTheme.typography.labelMedium,
        modifier = Modifier.weight(weight)
    )
}

@Composable
private fun PacketRow(packet: PacketEntry) {
    val verdictColor = if (packet.verdict == "ALLOW") AccentGreen else AccentRed
    val protoColor = when (packet.proto) {
        "TCP"  -> AccentBlue
        "UDP"  -> AccentPurple
        "ICMP" -> AccentOrange
        else   -> TextMuted
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Timestamp — show only HH:MM:SS
        val time = packet.timestamp.takeLast(8).take(8).let {
            if (it.length == 8) it else packet.timestamp
        }
        Text(time,           color = TextMuted,    style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(0.20f))
        Text(packet.proto,   color = protoColor,   style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.10f))
        Text(packet.srcIp,   color = TextMain,     style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(0.22f))
        Text(packet.dstIp,   color = TextMuted,    style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(0.22f))
        Text(":${packet.dstPort}", color = TextMuted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(0.12f))
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = verdictColor.copy(alpha = 0.15f),
            modifier = Modifier.weight(0.14f)
        ) {
            Text(
                text     = packet.verdict.take(3),
                color    = verdictColor,
                style    = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }
    }
}
