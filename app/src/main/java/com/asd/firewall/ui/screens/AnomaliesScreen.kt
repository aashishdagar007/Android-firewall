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
import androidx.compose.ui.unit.dp
import com.asd.firewall.ui.theme.*
import com.asd.firewall.ui.viewmodel.AnomalyEntry
import com.asd.firewall.ui.viewmodel.FirewallViewModel

@Composable
fun AnomaliesScreen(vm: FirewallViewModel) {
    val anomalies by vm.anomalies.collectAsState()
    val maxHits = anomalies.maxOfOrNull { it.hitCount }?.coerceAtLeast(1) ?: 1

    Column(modifier = Modifier.fillMaxSize().background(BgDark)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(BgPanel).padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Anomaly Detection", color = TextMuted, style = MaterialTheme.typography.labelLarge)
            Text("${anomalies.count { it.hitCount > 0 }} triggered", color = AccentOrange, style = MaterialTheme.typography.labelMedium)
        }

        LazyColumn(
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(anomalies) { anomaly ->
                AnomalyRow(anomaly = anomaly, maxHits = maxHits)
            }
        }
    }
}

@Composable
private fun AnomalyRow(anomaly: AnomalyEntry, maxHits: Int) {
    val fraction = (anomaly.hitCount.toFloat() / maxHits.toFloat()).coerceIn(0f, 1f)
    val color = when {
        anomaly.hitCount == 0    -> TextMuted
        anomaly.hitCount < 10   -> AccentGold
        anomaly.hitCount < 100  -> AccentOrange
        else                    -> AccentRed
    }

    Surface(
        shape  = RoundedCornerShape(10.dp),
        color  = BgPanel,
        border = BorderStroke(1.dp, if (anomaly.hitCount > 0) color.copy(alpha = 0.3f) else BorderColor),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text      = anomaly.name.removePrefix("Anomaly: "),
                    color     = if (anomaly.hitCount > 0) TextMain else TextMuted,
                    style     = MaterialTheme.typography.bodyMedium,
                    modifier  = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text  = if (anomaly.hitCount == 0) "—" else anomaly.hitCount.toString(),
                    color = color,
                    style = MaterialTheme.typography.labelLarge
                )
            }
            if (anomaly.hitCount > 0) {
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier.fillMaxWidth().height(3.dp)
                        .background(BorderColor, RoundedCornerShape(1.5.dp))
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(fraction).height(3.dp)
                            .background(color, RoundedCornerShape(1.5.dp))
                    )
                }
            }
        }
    }
}
