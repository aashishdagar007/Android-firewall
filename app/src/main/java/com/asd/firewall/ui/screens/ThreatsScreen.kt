package com.asd.firewall.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.asd.firewall.ui.theme.*
import com.asd.firewall.ui.viewmodel.FirewallViewModel
import com.asd.firewall.ui.viewmodel.ThreatEntry

@Composable
fun ThreatsScreen(vm: FirewallViewModel) {
    val threats by vm.threats.collectAsState()
    var banDialogIp by remember { mutableStateOf<String?>(null) }

    // Manual ban dialog
    banDialogIp?.let { ip ->
        AlertDialog(
            onDismissRequest = { banDialogIp = null },
            containerColor   = BgPanel,
            title  = { Text("Ban $ip", color = AccentRed) },
            text   = { Text("This IP will be immediately banned for 5 minutes.", color = TextMain) },
            confirmButton = {
                Button(
                    onClick = { vm.banIp(ip, "Manual ban"); banDialogIp = null },
                    colors  = ButtonDefaults.buttonColors(containerColor = AccentRed, contentColor = TextBright)
                ) { Text("Ban") }
            },
            dismissButton = {
                TextButton(onClick = { banDialogIp = null }) { Text("Cancel", color = TextMuted) }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(BgDark)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().background(BgPanel).padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text("Threat Intelligence", color = TextMuted, style = MaterialTheme.typography.labelLarge)
            Surface(shape = RoundedCornerShape(20.dp), color = AccentRed.copy(alpha = 0.15f)) {
                Text(
                    "${threats.size} banned",
                    color    = AccentRed,
                    style    = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }
        }

        if (threats.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("No active threats", color = AccentGreen, style = MaterialTheme.typography.headlineMedium)
                    Text("The network is clean", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(threats, key = { it.ip }) { threat ->
                    ThreatCard(threat = threat, onUnban = { vm.unbanIp(threat.ip) })
                }
            }
        }
    }
}

@Composable
private fun ThreatCard(threat: ThreatEntry, onUnban: () -> Unit) {
    Surface(
        shape  = RoundedCornerShape(12.dp),
        color  = BgPanel,
        border = BorderStroke(1.dp, AccentRed.copy(alpha = 0.4f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Block, contentDescription = null, tint = AccentRed, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(threat.ip, color = AccentRed, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(threat.reason, color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                Text("Banned ${threat.banCount}x", color = TextMuted, style = MaterialTheme.typography.labelMedium)
            }
            TextButton(
                onClick = onUnban,
                colors  = ButtonDefaults.textButtonColors(contentColor = AccentGreen)
            ) {
                Text("Unban")
            }
        }
    }
}
