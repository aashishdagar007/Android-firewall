package com.asd.firewall.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asd.firewall.AppPolicyManager
import com.asd.firewall.ui.theme.*
import com.asd.firewall.ui.viewmodel.FirewallViewModel

@Composable
fun SettingsScreen(vm: FirewallViewModel) {
    val stealthMode by vm.stealthMode.collectAsState()
    val rateLimit   by vm.rateLimit.collectAsState()

    var autoStart      by remember { mutableStateOf(AppPolicyManager.isAutoStartEnabled()) }
    var dohEnabled     by remember { mutableStateOf(AppPolicyManager.isDohEnabled()) }
    var defaultPolicy  by remember { mutableStateOf(AppPolicyManager.getDefaultPolicyGlobal()) }
    var showPolicyDialog by remember { mutableStateOf(false) }
    var rateLimitFloat   by remember { mutableFloatStateOf(rateLimit.toFloat()) }

    if (showPolicyDialog) {
        DefaultPolicyDialog(
            current   = defaultPolicy,
            onSelect  = { policy ->
                defaultPolicy = policy
                vm.setDefaultPolicy(policy)
                showPolicyDialog = false
            },
            onDismiss = { showPolicyDialog = false }
        )
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
                .background(BgPanel)
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Column {
                Text("Settings", color = TextBright, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Text("Configure your firewall protection", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(Modifier.height(12.dp))

        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ── Protection Section ────────────────────────────────
            SettingsSection("🛡️  Protection") {
                // Stealth Mode
                SettingsToggleRow(
                    icon      = Icons.Default.VisibilityOff,
                    iconColor = AccentPurple,
                    title     = "Stealth Mode",
                    subtitle  = "Hide firewall from adversarial traffic probes",
                    checked   = stealthMode,
                    onToggle  = { vm.setStealthMode(it) }
                )
                SettingsDivider()

                // Default Policy
                SettingsClickRow(
                    icon      = Icons.Default.Policy,
                    iconColor = AccentBlue,
                    title     = "Default Policy",
                    subtitle  = "What happens to traffic that doesn't match any rule",
                    value     = defaultPolicy,
                    valueColor = if (defaultPolicy == "ALLOW") AccentGreen else AccentRed,
                    onClick   = { showPolicyDialog = true }
                )
                SettingsDivider()

                // Rate Limit Slider
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = AccentOrange.copy(alpha = 0.12f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Speed, null, tint = AccentOrange, modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Rate Limit", color = TextMain, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                Text("Max packets per second", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AccentOrange.copy(alpha = 0.15f)
                        ) {
                            Text(
                                "${rateLimitFloat.toInt()} PPS",
                                color    = AccentOrange,
                                style    = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Slider(
                        value         = rateLimitFloat,
                        onValueChange = { rateLimitFloat = it },
                        onValueChangeFinished = { vm.setRateLimit(rateLimitFloat.toInt()) },
                        valueRange    = 100f..10000f,
                        steps         = 99,
                        colors        = SliderDefaults.colors(
                            thumbColor        = AccentOrange,
                            activeTrackColor  = AccentOrange,
                            inactiveTrackColor = BorderColor
                        )
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("100", color = TextDim, style = MaterialTheme.typography.labelSmall)
                        Text("10 000", color = TextDim, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // ── System Section ────────────────────────────────────
            SettingsSection("⚙️  System") {
                SettingsToggleRow(
                    icon      = Icons.Default.Autorenew,
                    iconColor = AccentGreen,
                    title     = "Auto-Start on Boot",
                    subtitle  = "Restart protection automatically after device reboot",
                    checked   = autoStart,
                    onToggle  = { autoStart = it; AppPolicyManager.setAutoStart(it) }
                )
                SettingsDivider()
                SettingsToggleRow(
                    icon      = Icons.Default.Dns,
                    iconColor = AccentTeal,
                    title     = "DNS over HTTPS (DoH)",
                    subtitle  = "Encrypt DNS queries to prevent ISP snooping",
                    checked   = dohEnabled,
                    onToggle  = { dohEnabled = it; AppPolicyManager.setDohEnabled(it) }
                )
            }

            // ── Presets ───────────────────────────────────────────
            SettingsSection("🚫  Block Presets") {
                listOf(
                    Triple(Icons.Default.Block, AccentRed, "Block Ad Networks" to "Block common advertising domains on port 80/443"),
                    Triple(Icons.Default.Policy, AccentOrange, "Block Telemetry" to "Prevent Microsoft, Google, Apple telemetry endpoints"),
                    Triple(Icons.Default.CloudOff, AccentPurple, "Block P2P Traffic" to "Block BitTorrent and P2P protocols (port 6881–6889)"),
                ).forEach { (icon, color, info) ->
                    val (title, subtitle) = info
                    SettingsActionRow(
                        icon      = icon,
                        iconColor = color,
                        title     = title,
                        subtitle  = subtitle,
                        onClick   = {
                            val port = when {
                                "Ad" in title -> 0
                                "Telemetry" in title -> 443
                                "P2P" in title -> 6881
                                else -> 0
                            }
                            vm.addRule("""{"action":"BLOCK","proto":"ANY","dst_port":$port,"description":"$title"}""")
                        }
                    )
                    if (info != ("Block P2P Traffic" to "Block BitTorrent and P2P protocols (port 6881–6889)"))
                        SettingsDivider()
                }
            }

            // ── About ─────────────────────────────────────────────
            SettingsSection("ℹ️  About AEGIS XII") {
                AboutRow("Version",         "2.0.0")
                SettingsDivider()
                AboutRow("Engine",          "C++ DPI / NFQ / BVUDP")
                SettingsDivider()
                AboutRow("Min SDK",         "Android 8.0 (Oreo)")
                SettingsDivider()
                AboutRow("Approach",        "VPN Service (no root)")
                SettingsDivider()
                AboutRow("Ledger",          "SHA-256 chain")
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── Section container ────────────────────────────────────────────

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            title,
            color    = TextMuted,
            style    = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            letterSpacing = 0.5.sp
        )
        Surface(
            shape  = RoundedCornerShape(16.dp),
            color  = BgPanel,
            border = BorderStroke(1.dp, BorderColor),
        ) {
            Column { content() }
        }
    }
}

// ── Row types ────────────────────────────────────────────────────

@Composable
private fun SettingsToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape    = RoundedCornerShape(8.dp),
            color    = iconColor.copy(alpha = 0.12f),
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextMain, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextMuted, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor   = AccentCyan,
                checkedTrackColor   = AccentCyan.copy(alpha = 0.3f),
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = BorderColor,
            )
        )
    }
}

@Composable
private fun SettingsClickRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    value: String,
    valueColor: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape    = RoundedCornerShape(8.dp),
            color    = iconColor.copy(alpha = 0.12f),
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextMain, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextMuted, style = MaterialTheme.typography.bodySmall)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, color = valueColor, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.ChevronRight, null, tint = TextDim, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun SettingsActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    var applied by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { applied = true; onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape    = RoundedCornerShape(8.dp),
            color    = iconColor.copy(alpha = 0.12f),
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextMain, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextMuted, style = MaterialTheme.typography.bodySmall)
        }
        AnimatedContent(targetState = applied, label = "apply") { done ->
            if (done) {
                Icon(Icons.Default.Check, null, tint = AccentGreen, modifier = Modifier.size(20.dp))
            } else {
                Icon(Icons.Default.Add, null, tint = iconColor, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextMuted, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = TextMain, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 64.dp),
        color    = BorderLight,
        thickness = 0.5.dp
    )
}

// ── Default Policy Dialog ────────────────────────────────────────

@Composable
private fun DefaultPolicyDialog(
    current: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = BgPanel,
        title = { Text("Default Policy", color = AccentCyan) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Traffic that doesn't match any rule will be handled by this default policy.",
                    color = TextMuted, style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(4.dp))
                listOf("ALLOW" to AccentGreen, "BLOCK" to AccentRed).forEach { (opt, color) ->
                    val selected = current == opt
                    Surface(
                        shape  = RoundedCornerShape(12.dp),
                        color  = if (selected) color.copy(alpha = 0.15f) else BgDark,
                        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) color else BorderColor),
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(opt) }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (opt == "ALLOW") Icons.Default.CheckCircle else Icons.Default.Cancel,
                                contentDescription = null,
                                tint = color,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(opt, color = color, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                Text(
                                    if (opt == "ALLOW") "All unmatched traffic is forwarded (recommended)" else "All unmatched traffic is dropped (lockdown mode)",
                                    color = TextMuted, style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
        }
    )
}
