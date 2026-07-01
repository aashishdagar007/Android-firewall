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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asd.firewall.ui.theme.*
import com.asd.firewall.ui.viewmodel.FirewallViewModel
import com.asd.firewall.ui.viewmodel.LedgerEntry

@Composable
fun LedgerScreen(vm: FirewallViewModel) {
    val entries by vm.ledger.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(BgDark)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().background(BgPanel).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Tamper-Proof Chain Ledger", color = TextMuted, style = MaterialTheme.typography.labelLarge)
                Text("SHA-256 cryptographic chain", color = AccentPurple, style = MaterialTheme.typography.labelMedium)
            }
            Icon(Icons.Default.Lock, contentDescription = null, tint = AccentPurple, modifier = Modifier.size(20.dp))
        }

        if (entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Book, contentDescription = null, tint = TextMuted, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("No ledger entries yet", color = TextMuted, style = MaterialTheme.typography.bodyLarge)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(entries, key = { it.seq }) { entry ->
                    LedgerEntryCard(entry)
                }
            }
        }
    }
}

@Composable
private fun LedgerEntryCard(entry: LedgerEntry) {
    Surface(
        shape  = RoundedCornerShape(10.dp),
        color  = BgPanel,
        border = BorderStroke(1.dp, AccentPurple.copy(alpha = 0.2f)),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text  = "#${entry.seq}",
                    color = AccentPurple,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text  = entry.timestamp,
                    color = TextMuted,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text  = entry.event,
                color = TextMain,
                style = MaterialTheme.typography.bodyMedium
            )
            if (entry.hash.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text  = entry.hash.take(32) + "…",
                    color = TextMuted.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                )
            }
        }
    }
}
