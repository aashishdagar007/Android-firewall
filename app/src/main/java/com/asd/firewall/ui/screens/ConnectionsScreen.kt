package com.asd.firewall.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.asd.firewall.ui.theme.*
import com.asd.firewall.ui.viewmodel.ConnectionEntry
import com.asd.firewall.ui.viewmodel.FirewallViewModel
import kotlin.math.roundToInt

@Composable
fun ConnectionsScreen(vm: FirewallViewModel) {
    val connections by vm.connections.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(BgDark)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().background(BgPanel).padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Connection Tracking", color = TextMuted, style = MaterialTheme.typography.labelLarge)
            Text("${connections.size} active", color = AccentBlue, style = MaterialTheme.typography.labelMedium)
        }

        if (connections.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No active connections", color = TextMuted, style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(connections) { conn ->
                    ConnectionCard(conn)
                }
            }
        }
    }
}

@Composable
private fun ConnectionCard(conn: ConnectionEntry) {
    val stateColor = if (conn.state == "ESTABLISHED") AccentGreen else AccentBlue
    val protoColor = when (conn.proto) {
        "TCP"  -> AccentBlue
        "UDP"  -> AccentPurple
        "ICMP" -> AccentOrange
        else   -> TextMuted
    }

    Surface(
        shape  = RoundedCornerShape(12.dp),
        color  = BgPanel,
        border = BorderStroke(1.dp, stateColor.copy(alpha = 0.25f)),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Flow 5-tuple
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(4.dp), color = protoColor.copy(alpha = 0.15f)) {
                            Text(conn.proto, color = protoColor, style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("${conn.srcIp}:${conn.srcPort}", color = TextMain, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("→  ${conn.dstIp}:${conn.dstPort}", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                }
                // State + age
                Column(horizontalAlignment = Alignment.End) {
                    Surface(shape = RoundedCornerShape(6.dp), color = stateColor.copy(alpha = 0.15f)) {
                        Text(conn.state, color = stateColor, style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("${conn.ageSec.roundToInt()}s ago", color = TextMuted, style = MaterialTheme.typography.labelMedium)
                }
            }
            // Traffic stats
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("↓ ${formatBytes(conn.bytesIn)}", color = AccentGreen, style = MaterialTheme.typography.labelMedium)
                Text("↑ ${formatBytes(conn.bytesOut)}", color = AccentBlue, style = MaterialTheme.typography.labelMedium)
                Text("Σ ${formatBytes(conn.bytesIn + conn.bytesOut)}", color = TextMuted, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
