package com.asd.firewall.ui.screens

import android.graphics.drawable.Drawable
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.asd.firewall.AppPolicyManager
import com.asd.firewall.ui.theme.*
import com.asd.firewall.ui.viewmodel.FirewallViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppFirewallScreen(vm: FirewallViewModel) {
    val apps      by vm.installedApps.collectAsState()
    val policies  by vm.appPolicies.collectAsState()
    var query     by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(false) }

    val filtered = remember(apps, query, showSystem) {
        apps.filter { app ->
            (showSystem || !app.isSystemApp) &&
            (query.isBlank() || app.label.contains(query, ignoreCase = true) ||
             app.packageName.contains(query, ignoreCase = true))
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(BgDark)) {
        // ── Header ───────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgPanel)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Per-App Firewall", color = TextMain, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${filtered.size} apps", color = TextMuted, style = MaterialTheme.typography.labelMedium)
                }
                // System apps toggle
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("System", color = TextMuted, style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(6.dp))
                    Switch(
                        checked = showSystem,
                        onCheckedChange = { showSystem = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor       = AccentCyan,
                            checkedTrackColor       = AccentCyan.copy(alpha = 0.3f),
                            uncheckedThumbColor     = TextMuted,
                            uncheckedTrackColor     = BorderColor,
                        )
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Search bar
            OutlinedTextField(
                value         = query,
                onValueChange = { query = it },
                placeholder   = { Text("Search apps...", color = TextDim) },
                leadingIcon   = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },
                trailingIcon  = if (query.isNotBlank()) {{
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = TextMuted)
                    }
                }} else null,
                modifier   = Modifier.fillMaxWidth(),
                colors     = aegisTextFieldColors(),
                shape      = RoundedCornerShape(12.dp),
                singleLine = true,
            )
        }

        // ── Policy legend ────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PolicyLegendChip("ALLOW",   AccentGreen)
            PolicyLegendChip("MONITOR", AccentGold)
            PolicyLegendChip("BLOCK",   AccentRed)
        }

        // ── App list ─────────────────────────────────────────────
        if (apps.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentCyan, modifier = Modifier.size(40.dp))
            }
        } else if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No apps match \"$query\"", color = TextMuted, style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filtered, key = { it.packageName }) { app ->
                    val policy = policies[app.packageName] ?: "ALLOW"
                    AppPolicyCard(
                        appLabel    = app.label,
                        packageName = app.packageName,
                        isSystem    = app.isSystemApp,
                        policy      = policy,
                        onPolicy    = { vm.setAppPolicy(app.packageName, it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PolicyLegendChip(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(Modifier.width(4.dp))
        Text(label, color = color, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun AppPolicyCard(
    appLabel: String,
    packageName: String,
    isSystem: Boolean,
    policy: String,
    onPolicy: (String) -> Unit,
) {
    val borderColor = when (policy) {
        "BLOCK"   -> AccentRed
        "MONITOR" -> AccentGold
        else      -> BorderColor
    }

    Surface(
        shape  = RoundedCornerShape(12.dp),
        color  = BgPanel,
        border = BorderStroke(1.dp, borderColor.copy(alpha = 0.4f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App icon placeholder (letter avatar)
            Surface(
                shape  = RoundedCornerShape(10.dp),
                color  = borderColor.copy(alpha = 0.12f),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        appLabel.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                        color = borderColor,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            // App info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        appLabel,
                        color = TextMain,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isSystem) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = AccentBlue.copy(alpha = 0.15f)
                        ) {
                            Text(
                                "SYS",
                                color = AccentBlue,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Text(
                    packageName,
                    color = TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(8.dp))

            // Three-way toggle: ALLOW | MONITOR | BLOCK
            ThreeWayPolicyToggle(current = policy, onSelect = onPolicy)
        }
    }
}

@Composable
private fun ThreeWayPolicyToggle(current: String, onSelect: (String) -> Unit) {
    val options = listOf("ALLOW" to AccentGreen, "MON" to AccentGold, "BLOCK" to AccentRed)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(BgDark),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEach { (label, color) ->
            val policyKey = if (label == "MON") "MONITOR" else label
            val selected = current == policyKey
            val bgColor by animateColorAsState(
                if (selected) color.copy(alpha = 0.2f) else Color.Transparent, label = "bg"
            )
            val textColor by animateColorAsState(
                if (selected) color else TextDim, label = "txt"
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(bgColor)
                    .clickable { onSelect(policyKey) }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = textColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}
