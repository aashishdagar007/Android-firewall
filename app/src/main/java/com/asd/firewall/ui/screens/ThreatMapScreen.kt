package com.asd.firewall.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asd.firewall.ui.theme.*
import com.asd.firewall.ui.viewmodel.FirewallViewModel
import com.asd.firewall.ui.viewmodel.ThreatEntry
import kotlin.math.*
import kotlin.random.Random

// ── Geo simulation ───────────────────────────────────────────────
// In a real build, IP → lat/lon comes from a bundled GeoLite2 DB.
// Here we simulate realistic world map pings based on threat IP regions.

private data class MapPing(
    val x: Float,    // 0f-1f, normalised longitude
    val y: Float,    // 0f-1f, normalised latitude
    val ip: String,
    val country: String,
    val reason: String,
    val birthMs: Long = System.currentTimeMillis(),
)

private val CountryCoordinates = mapOf(
    "United States"    to (0.22f to 0.38f),
    "China"            to (0.75f to 0.35f),
    "Russia"           to (0.65f to 0.25f),
    "Germany"          to (0.50f to 0.28f),
    "Brazil"           to (0.31f to 0.60f),
    "India"            to (0.68f to 0.45f),
    "Japan"            to (0.82f to 0.35f),
    "United Kingdom"   to (0.46f to 0.26f),
    "South Korea"      to (0.80f to 0.34f),
    "Netherlands"      to (0.50f to 0.27f),
    "Ukraine"          to (0.57f to 0.28f),
    "Iran"             to (0.62f to 0.38f),
    "North Korea"      to (0.80f to 0.32f),
    "Nigeria"          to (0.50f to 0.52f),
    "Indonesia"        to (0.77f to 0.57f),
)

private fun ipToCountry(ip: String): String {
    val first = ip.split(".").firstOrNull()?.toIntOrNull() ?: 100
    return when {
        first in 1..20      -> "United States"
        first in 21..40     -> "China"
        first in 41..60     -> "Russia"
        first in 61..80     -> "Germany"
        first in 81..100    -> "India"
        first in 101..120   -> "Japan"
        first in 121..140   -> "Brazil"
        first in 141..160   -> "United Kingdom"
        first in 161..180   -> "Ukraine"
        first in 181..200   -> "Iran"
        first in 201..220   -> "South Korea"
        else                -> "Netherlands"
    }
}

// ── Threat Map Screen ─────────────────────────────────────────────

@Composable
fun ThreatMapScreen(vm: FirewallViewModel) {
    val threats by vm.threats.collectAsState()
    val packets by vm.packets.collectAsState()

    // Build pings from active threats + recent blocked packets
    val pings = remember(threats, packets) {
        val list = mutableListOf<MapPing>()
        threats.take(20).forEach { threat ->
            val country = ipToCountry(threat.ip)
            val (baseX, baseY) = CountryCoordinates[country] ?: (0.5f to 0.5f)
            list.add(MapPing(
                x       = (baseX + Random.nextFloat() * 0.04f - 0.02f).coerceIn(0.05f, 0.95f),
                y       = (baseY + Random.nextFloat() * 0.03f - 0.015f).coerceIn(0.05f, 0.95f),
                ip      = threat.ip,
                country = country,
                reason  = threat.reason,
            ))
        }
        packets.filter { it.verdict == "BLOCK" }.take(15).forEach { pkt ->
            if (list.none { it.ip == pkt.srcIp }) {
                val country = ipToCountry(pkt.srcIp)
                val (baseX, baseY) = CountryCoordinates[country] ?: (0.5f to 0.5f)
                list.add(MapPing(
                    x       = (baseX + Random.nextFloat() * 0.05f - 0.025f).coerceIn(0.05f, 0.95f),
                    y       = (baseY + Random.nextFloat() * 0.04f - 0.02f).coerceIn(0.05f, 0.95f),
                    ip      = pkt.srcIp,
                    country = country,
                    reason  = "Packet blocked: ${pkt.proto} :${pkt.dstPort}",
                ))
            }
        }
        list
    }

    var selectedPing by remember { mutableStateOf<MapPing?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(BgDark)) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgPanel)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Live Threat Map", color = TextMain, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${pings.size} threat sources detected", color = AccentRed, style = MaterialTheme.typography.labelMedium)
            }
            Surface(shape = RoundedCornerShape(20.dp), color = AccentRed.copy(alpha = 0.15f)) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PulsingDot(color = AccentRed)
                    Spacer(Modifier.width(6.dp))
                    Text("LIVE", color = AccentRed, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Map
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF060C18))
        ) {
            WorldMapCanvas(
                pings         = pings,
                onPingTapped  = { selectedPing = it },
                modifier      = Modifier.fillMaxSize()
            )
        }

        // Selected ping detail
        AnimatedVisibility(
            visible = selectedPing != null,
            enter   = slideInVertically { it },
            exit    = slideOutVertically { it },
        ) {
            selectedPing?.let { ping ->
                Surface(
                    color  = BgPanel,
                    border = BorderStroke(1.dp, AccentRed.copy(alpha = 0.5f)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Block, contentDescription = null, tint = AccentRed, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(ping.ip, color = AccentRed, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            Text(ping.country, color = TextMain, style = MaterialTheme.typography.bodyMedium)
                            Text(ping.reason, color = TextMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { selectedPing = null }) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = TextMuted)
                        }
                    }
                }
            }
        }

        // Threat list
        if (pings.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(pings) { ping ->
                    Surface(
                        shape  = RoundedCornerShape(20.dp),
                        color  = AccentRed.copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, AccentRed.copy(alpha = 0.3f)),
                        modifier = Modifier.clickable { selectedPing = ping }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🌍", fontSize = 14.sp)
                            Spacer(Modifier.width(5.dp))
                            Column {
                                Text(ping.ip, color = AccentRed, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Text(ping.country, color = TextMuted, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("No active threats — network is clean", color = AccentGreen, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

// ── World Map drawn entirely with Canvas ─────────────────────────

@Composable
private fun WorldMapCanvas(
    pings: List<MapPing>,
    onPingTapped: (MapPing) -> Unit,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "map")
    val ringScale by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
        label = "ring"
    )
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
        label = "ralpha"
    )

    Canvas(
        modifier = modifier.pointerInput(pings) {
            detectTapGestures { offset ->
                val canvasW = size.width.toFloat()
                val canvasH = size.height.toFloat()
                pings.firstOrNull { ping ->
                    val px = ping.x * canvasW
                    val py = ping.y * canvasH
                    (offset.x - px).pow(2) + (offset.y - py).pow(2) < 900f
                }?.let(onPingTapped)
            }
        }
    ) {
        val w = size.width
        val h = size.height

        // ── Draw simplified world map as dot grid ─────────────────
        // Using a simplified continent outline approach — horizontal band approximation
        val dotRadius = 1.8.dp.toPx()
        val dotSpacingX = 9.dp.toPx()
        val dotSpacingY = 9.dp.toPx()

        // Land coverage map: simplified bitmask row by row
        val landRows: List<Pair<Float, List<ClosedFloatingPointRange<Float>>>> = listOf(
            0.18f to listOf(0.42f..0.58f),                                         // Northern Europe / Scandinavia
            0.22f to listOf(0.38f..0.62f, 0.70f..0.85f),                          // Europe + Russia top
            0.26f to listOf(0.14f..0.20f, 0.36f..0.65f, 0.66f..0.88f),           // N. America + Europe + Russia
            0.30f to listOf(0.12f..0.25f, 0.35f..0.68f, 0.67f..0.90f),           // N. America wide + Asia
            0.34f to listOf(0.10f..0.28f, 0.34f..0.67f, 0.66f..0.88f),           // N. America + Middle East + Asia
            0.38f to listOf(0.09f..0.27f, 0.36f..0.48f, 0.58f..0.68f, 0.70f..0.84f), // Americas + Africa + SE Asia
            0.42f to listOf(0.10f..0.25f, 0.42f..0.52f, 0.60f..0.70f, 0.73f..0.82f),
            0.46f to listOf(0.28f..0.35f, 0.44f..0.55f, 0.63f..0.72f, 0.74f..0.80f),
            0.50f to listOf(0.30f..0.36f, 0.44f..0.56f, 0.65f..0.74f),
            0.54f to listOf(0.30f..0.38f, 0.43f..0.56f),
            0.58f to listOf(0.31f..0.42f, 0.44f..0.50f),
            0.62f to listOf(0.30f..0.40f),
            0.66f to listOf(0.28f..0.36f),
        )

        for ((normY, bands) in landRows) {
            val py = normY * h
            var nx = dotSpacingX
            while (nx < w) {
                val normX = nx / w
                if (bands.any { normX in it }) {
                    drawCircle(
                        color  = Color(0xFF1A2840),
                        radius = dotRadius,
                        center = Offset(nx, py)
                    )
                }
                nx += dotSpacingX
            }
        }

        // ── Draw threat pings ─────────────────────────────────────
        pings.forEach { ping ->
            val px = ping.x * w
            val py = ping.y * h

            // Expanding ring (animated)
            drawCircle(
                color  = AccentRed.copy(alpha = ringAlpha * 0.6f),
                radius = 18.dp.toPx() * ringScale,
                center = Offset(px, py),
                style  = Stroke(width = 1.5.dp.toPx())
            )
            // Inner filled dot
            drawCircle(
                color  = AccentRed,
                radius = 4.dp.toPx(),
                center = Offset(px, py)
            )
            // White center
            drawCircle(
                color  = Color.White,
                radius = 1.5.dp.toPx(),
                center = Offset(px, py)
            )
        }
    }
}

@Composable
private fun PulsingDot(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "a"
    )
    Box(
        modifier = Modifier
            .size(8.dp)
            .background(color.copy(alpha = alpha), CircleShape)
    )
}
