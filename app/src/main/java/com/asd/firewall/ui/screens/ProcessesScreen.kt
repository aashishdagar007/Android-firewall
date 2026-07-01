package com.asd.firewall.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.asd.firewall.ui.theme.*
import com.asd.firewall.ui.viewmodel.FirewallViewModel

@Composable
fun ProcessesScreen(vm: FirewallViewModel) {
    // Processes are derived from the packets ring buffer — group by process name
    val packets by vm.packets.collectAsState()

    // Aggregate traffic by process name
    val processMap = remember(packets) {
        packets.groupBy { it.process.ifBlank { "unknown" } }
            .mapValues { (_, pkts) ->
                val totalBytes = pkts.sumOf { it.size.toLong() }
                val blocked    = pkts.count { it.verdict == "BLOCK" }
                Triple(pkts.size, totalBytes, blocked)
            }
            .entries
            .sortedByDescending { it.value.second }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(BgDark)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().background(BgPanel).padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("App Traffic Attribution", color = TextMuted, style = MaterialTheme.typography.labelLarge)
            Text("${processMap.size} apps", color = AccentCyan, style = MaterialTheme.typography.labelMedium)
        }

        if (processMap.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No traffic data yet", color = TextMuted, style = MaterialTheme.typography.bodyLarge)
                    Text("Start the VPN to capture app traffic", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            val maxBytes = processMap.maxOfOrNull { it.value.second } ?: 1L
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(processMap) { (pkg, data) ->
                    val (count, bytes, blocked) = data
                    val barFraction = (bytes.toFloat() / maxBytes.toFloat()).coerceIn(0f, 1f)
                    val hasBlocked = blocked > 0

                    Surface(
                        shape  = RoundedCornerShape(12.dp),
                        color  = BgPanel,
                        border = BorderStroke(1.dp, if (hasBlocked) AccentRed.copy(alpha = 0.3f) else BorderColor),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // App name
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text  = pkg.substringAfterLast('.'),
                                        color = TextMain,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text  = pkg,
                                        color = TextMuted,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                // Stats column
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(formatBytes(bytes), color = AccentCyan, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                    if (blocked > 0) {
                                        Text("$blocked blocked", color = AccentRed, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            // Traffic bar
                            Box(
                                modifier = Modifier.fillMaxWidth().height(4.dp)
                                    .background(BorderColor, RoundedCornerShape(2.dp))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(barFraction)
                                        .height(4.dp)
                                        .background(
                                            if (hasBlocked) AccentRed else AccentCyan,
                                            RoundedCornerShape(2.dp)
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
