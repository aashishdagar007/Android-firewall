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
import com.asd.firewall.ui.viewmodel.AnomalyEntry
import com.asd.firewall.ui.viewmodel.FirewallViewModel

@Composable
fun AnomaliesScreen(vm: FirewallViewModel) {
    val anomalies by vm.anomalies.collectAsState()
    val maxHits = anomalies.maxOfOrNull { it.hitCount }?.coerceAtLeast(1) ?: 1
    val activeCount = anomalies.count { it.hitCount > 0 }

    Column(modifier = Modifier.fillMaxSize().background(BgDark)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().background(BgPanel).padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Anomaly Detection", color = TextMain, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("20 vectors monitored", color = TextMuted, style = MaterialTheme.typography.bodySmall)
            }
            Surface(shape = RoundedCornerShape(20.dp), color = AccentOrange.copy(alpha = 0.15f)) {
                Text(
                    "$activeCount triggered",
                    color = AccentOrange, style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }
        }

        // Severity legend
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            listOf("Low" to AccentGold, "Medium" to AccentOrange, "High" to AccentRed).forEach { (label, color) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(color, androidx.compose.foundation.shape.CircleShape))
                    Spacer(Modifier.width(4.dp))
                    Text(label, color = color, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(anomalies) { anomaly ->
                AnimatedVisibility(visible = true, enter = fadeIn() + expandVertically()) {
                    AnomalyRow(anomaly = anomaly, maxHits = maxHits)
                }
            }
        }
    }
}

@Composable
private fun AnomalyRow(anomaly: AnomalyEntry, maxHits: Int) {
    val fraction = (anomaly.hitCount.toFloat() / maxHits.toFloat()).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(fraction, tween(600, easing = EaseOutCubic), label = "bar")

    val (color, severity) = when {
        anomaly.hitCount == 0   -> TextMuted to "Clean"
        anomaly.hitCount < 10   -> AccentGold   to "Low"
        anomaly.hitCount < 100  -> AccentOrange  to "Medium"
        else                     -> AccentRed     to "High"
    }

    Surface(
        shape  = RoundedCornerShape(10.dp),
        color  = BgPanel,
        border = BorderStroke(1.dp, if (anomaly.hitCount > 0) color.copy(alpha = 0.3f) else BorderColor),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        anomaly.name.removePrefix("Anomaly: "),
                        color = if (anomaly.hitCount > 0) TextMain else TextMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (anomaly.hitCount > 0) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    if (anomaly.hitCount > 0) {
                        Text(severity, color = color, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (anomaly.hitCount > 0) color.copy(alpha = 0.15f) else Color.Transparent
                ) {
                    Text(
                        if (anomaly.hitCount == 0) "—" else "${anomaly.hitCount}",
                        color = color,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            if (anomaly.hitCount > 0) {
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier.fillMaxWidth().height(4.dp)
                        .background(BorderColor, RoundedCornerShape(2.dp))
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(animatedFraction).height(4.dp)
                            .background(
                                brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                    listOf(color.copy(alpha = 0.7f), color)
                                ),
                                shape = RoundedCornerShape(2.dp)
                            )
                    )
                }
            }
        }
    }
}
