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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.asd.firewall.ui.theme.*
import com.asd.firewall.ui.viewmodel.FirewallRule
import com.asd.firewall.ui.viewmodel.FirewallViewModel

@Composable
fun RulesScreen(vm: FirewallViewModel) {
    val rules by vm.rules.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    if (showAddDialog) {
        AddRuleDialog(
            onDismiss = { showAddDialog = false },
            onAdd     = { json ->
                vm.addRule(json)
                showAddDialog = false
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().background(BgDark)
    ) {
        // Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgPanel)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${rules.size} Rules", color = TextMuted, style = MaterialTheme.typography.labelLarge)
            FloatingActionButton(
                onClick           = { showAddDialog = true },
                containerColor    = AccentCyan,
                contentColor      = BgDark,
                modifier          = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Rule", modifier = Modifier.size(18.dp))
            }
        }

        LazyColumn(
            modifier         = Modifier.fillMaxSize(),
            contentPadding   = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(rules, key = { it.id }) { rule ->
                RuleCard(rule = rule, onDelete = { vm.deleteRule(rule.id) })
            }
        }
    }
}

@Composable
private fun RuleCard(rule: FirewallRule, onDelete: () -> Unit) {
    val actionColor = if (rule.action == "ALLOW") AccentGreen else AccentRed

    Surface(
        shape  = RoundedCornerShape(12.dp),
        color  = BgPanel,
        border = BorderStroke(1.dp, actionColor.copy(alpha = 0.3f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Action badge
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = actionColor.copy(alpha = 0.15f),
            ) {
                Text(
                    text     = rule.action,
                    color    = actionColor,
                    style    = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(rule.description.ifBlank { "Rule #${rule.id}" }, color = TextMain, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(2.dp))
                Text(
                    text  = buildString {
                        append(rule.proto)
                        if (rule.dstPort > 0) append(" :${rule.dstPort}")
                        append(" • ${rule.hitCount} hits")
                    },
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = AccentRed.copy(alpha = 0.7f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddRuleDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var action   by remember { mutableStateOf("BLOCK") }
    var proto    by remember { mutableStateOf("ANY") }
    var dstPort  by remember { mutableStateOf("") }
    var srcIp    by remember { mutableStateOf("") }
    var desc     by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = BgPanel,
        titleContentColor = TextBright,
        textContentColor  = TextMain,
        title = { Text("Add Firewall Rule", color = AccentCyan) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Action selector
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("BLOCK", "ALLOW").forEach { opt ->
                        val selected = action == opt
                        val color = if (opt == "ALLOW") AccentGreen else AccentRed
                        FilterChip(
                            selected = selected,
                            onClick  = { action = opt },
                            label    = { Text(opt, color = if (selected) BgDark else color) },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = color,
                                containerColor         = color.copy(alpha = 0.1f),
                            )
                        )
                    }
                }
                // Protocol selector
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ANY", "TCP", "UDP", "ICMP").forEach { opt ->
                        FilterChip(
                            selected = proto == opt,
                            onClick  = { proto = opt },
                            label    = { Text(opt, style = MaterialTheme.typography.labelMedium) },
                        )
                    }
                }
                OutlinedTextField(
                    value         = srcIp,
                    onValueChange = { srcIp = it },
                    label         = { Text("Source IP (blank = any)", color = TextMuted) },
                    colors        = aegisTextFieldColors(),
                    singleLine    = true,
                )
                OutlinedTextField(
                    value         = dstPort,
                    onValueChange = { dstPort = it },
                    label         = { Text("Destination Port (0 = any)", color = TextMuted) },
                    colors        = aegisTextFieldColors(),
                    singleLine    = true,
                )
                OutlinedTextField(
                    value         = desc,
                    onValueChange = { desc = it },
                    label         = { Text("Description", color = TextMuted) },
                    colors        = aegisTextFieldColors(),
                    singleLine    = true,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val port = dstPort.toIntOrNull() ?: 0
                    val json = buildString {
                        append("{\"action\":\"$action\",\"proto\":\"$proto\"")
                        if (srcIp.isNotBlank()) append(",\"src_ip\":\"$srcIp\"")
                        append(",\"dst_port\":$port")
                        append(",\"description\":\"${desc.replace("\"","\\\"")}\"}") }
                    onAdd(json)
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = BgDark)
            ) { Text("Add Rule") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
        }
    )
}

@Composable
fun aegisTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = AccentCyan,
    unfocusedBorderColor = BorderColor,
    cursorColor          = AccentCyan,
    focusedTextColor     = TextMain,
    unfocusedTextColor   = TextMain,
    focusedLabelColor    = AccentCyan,
)
