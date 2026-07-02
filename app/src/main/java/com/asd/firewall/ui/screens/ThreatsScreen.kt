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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.asd.firewall.ui.theme.*
import com.asd.firewall.ui.viewmodel.FirewallViewModel
import com.asd.firewall.ui.viewmodel.ThreatEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreatsScreen(vm: FirewallViewModel) {
    val threats by vm.threats.collectAsState()
    var banDialogIp by remember { mutableStateOf<String?>(null) }
    var manualBanIp by remember { mutableStateOf("") }
    var showManualBan by remember { mutableStateOf(false) }

    // Manual ban dialog
    banDialogIp?.let { ip ->
        AlertDialog(
            onDismissRequest = { banDialogIp = null },
            containerColor   = BgPanel,
            title  = { Text("Ban $ip", color = AccentRed) },
            text   = { Text("This IP will be immediately banned.", color = TextMain) },
            confirmButton = {
                Button(
                    onClick = { vm.banIp(ip, "Manual ban"); banDialogIp = null },
                    colors  = ButtonDefaults.buttonColors(containerColor = AccentRed, contentColor = TextBright)
                ) { Text("Ban Now") }
            },
            dismissButton = {
                TextButton(onClick = { banDialogIp = null }) { Text("Cancel", color = TextMuted) }
            }
        )
    }

    // Manual IP entry dialog
    if (showManualBan) {
        AlertDialog(
            onDismissRequest = { showManualBan = false },
            containerColor   = BgPanel,
            title  = { Text("Ban an IP", color = AccentRed) },
            text = {
                OutlinedTextField(
                    value         = manualBanIp,
                    onValueChange = { manualBanIp = it },
                    label         = { Text("IP Address", color = TextMuted) },
                    colors        = aegisTextFieldColors(),
                    singleLine    = true,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (manualBanIp.isNotBlank()) {
                            vm.banIp(manualBanIp.trim(), "Manual ban")
                            manualBanIp = ""
                            showManualBan = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed, contentColor = TextBright)
                ) { Text("Ban") }
            },
            dismissButton = {
                TextButton(onClick = { showManualBan = false }) { Text("Cancel", color = TextMuted) }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(BgDark)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().background(BgPanel).padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Threat Intelligence", color = TextMain, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Surface(shape = RoundedCornerShape(20.dp), color = AccentRed.copy(alpha = 0.15f)) {
                    Text(
                        "${threats.size} banned IPs",
                        color = AccentRed, style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }
            // Manual ban FAB
            FloatingActionButton(
                onClick        = { showManualBan = true },
                containerColor = AccentRed,
                contentColor   = TextBright,
                modifier       = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Ban IP", modifier = Modifier.size(20.dp))
            }
        }

        if (threats.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Shield, null, tint = AccentGreen, modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("No active threats", color = AccentGreen, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("The network is clean", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(threats, key = { it.ip }) { threat ->
                    AnimatedVisibility(
                        visible = true,
                        enter   = fadeIn() + slideInVertically { it / 2 },
                    ) {
                        ThreatCard(threat = threat, onUnban = { vm.unbanIp(threat.ip) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ThreatCard(threat: ThreatEntry, onUnban: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f, targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "g"
    )

    Surface(
        shape  = RoundedCornerShape(12.dp),
        color  = BgPanel,
        border = BorderStroke(1.dp, AccentRed.copy(alpha = glowAlpha + 0.1f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon with glow bg
            Surface(
                shape    = RoundedCornerShape(10.dp),
                color    = AccentRed.copy(alpha = 0.12f),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Block, null, tint = AccentRed, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(threat.ip, color = AccentRed, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(threat.reason, color = TextMain, style = MaterialTheme.typography.bodyMedium)
                Text("Banned ${threat.banCount}×", color = TextMuted, style = MaterialTheme.typography.labelMedium)
            }
            TextButton(
                onClick = onUnban,
                colors  = ButtonDefaults.textButtonColors(contentColor = AccentGreen)
            ) {
                Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Unban")
            }
        }
    }
}
