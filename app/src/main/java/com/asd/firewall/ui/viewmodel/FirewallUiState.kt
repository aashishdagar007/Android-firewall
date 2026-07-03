package com.asd.firewall.ui.viewmodel

import com.google.gson.JsonParser
import com.google.gson.JsonObject
import com.google.gson.JsonArray

// ── UI State Data Classes ────────────────────────────────────────
// These are the Kotlin data models for everything shown in the UI.
// They are populated by parsing the JSON strings returned by the JNI layer.

data class LiveStats(
    val total: Long       = 0,
    val allowed: Long     = 0,
    val blocked: Long     = 0,
    val tcp: Long         = 0,
    val udp: Long         = 0,
    val icmp: Long        = 0,
    val bytesTotal: Long  = 0,
) {
    val blockRate: Float get() = if (total > 0) blocked.toFloat() / total.toFloat() else 0f
    val allowRate: Float get() = if (total > 0) allowed.toFloat() / total.toFloat() else 0f
}

data class PacketEntry(
    val seq: Long,
    val timestamp: String,
    val srcIp: String,
    val dstIp: String,
    val srcPort: Int,
    val dstPort: Int,
    val proto: String,
    val verdict: String,
    val process: String,
    val size: Int,
)

data class FirewallRule(
    val id: Int,
    val action: String,
    val proto: String,
    val srcIp: Long,
    val dstIp: Long,
    val srcPort: Int,
    val dstPort: Int,
    val description: String,
    val hitCount: Long,
)

data class ProcessEntry(
    val packageName: String,
    val bytesIn: Long  = 0,
    val bytesOut: Long = 0,
)

/** Live per-app bandwidth stats from the C++ JNI engine */
data class PerAppStatEntry(
    val packageName: String,
    val bytesIn: Long,
    val bytesOut: Long,
    val packetsTotal: Long,
    val packetsBlocked: Long,
) {
    val bytesTotal: Long get() = bytesIn + bytesOut
    val blockRate: Float get() = if (packetsTotal > 0) packetsBlocked.toFloat() / packetsTotal else 0f
}

data class ThreatEntry(
    val ip: String,
    val reason: String,
    val banCount: Int,
)

data class AnomalyEntry(
    val name: String,
    val hitCount: Int,
)

data class ConnectionEntry(
    val srcIp: String,
    val dstIp: String,
    val srcPort: Int,
    val dstPort: Int,
    val proto: String,
    val state: String,
    val bytesIn: Long,
    val bytesOut: Long,
    val ageSec: Double,
)

data class LedgerEntry(
    val seq: Long,
    val timestamp: String,
    val event: String,
    val hash: String,
)

// ── JSON Parsers ─────────────────────────────────────────────────

object JsonParsers {

    fun parseStats(json: String): LiveStats = try {
        val obj = JsonParser.parseString(json).asJsonObject
        LiveStats(
            total      = obj.get("total")?.asLong ?: 0,
            allowed    = obj.get("allowed")?.asLong ?: 0,
            blocked    = obj.get("blocked")?.asLong ?: 0,
            tcp        = obj.get("tcp")?.asLong ?: 0,
            udp        = obj.get("udp")?.asLong ?: 0,
            icmp       = obj.get("icmp")?.asLong ?: 0,
            bytesTotal = obj.get("bytes_total")?.asLong ?: 0,
        )
    } catch (e: Exception) { LiveStats() }

    fun parsePackets(json: String): List<PacketEntry> = try {
        val arr = JsonParser.parseString(json).asJsonArray
        arr.map { el ->
            val o = el.asJsonObject
            PacketEntry(
                seq       = o.get("seq")?.asLong ?: 0,
                timestamp = o.get("timestamp")?.asString ?: "",
                srcIp     = o.get("src_ip")?.asString ?: "",
                dstIp     = o.get("dst_ip")?.asString ?: "",
                srcPort   = o.get("src_port")?.asInt ?: 0,
                dstPort   = o.get("dst_port")?.asInt ?: 0,
                proto     = o.get("proto")?.asString ?: "ANY",
                verdict   = o.get("verdict")?.asString ?: "ALLOW",
                process   = o.get("process")?.asString ?: "",
                size      = o.get("size")?.asInt ?: 0,
            )
        }
    } catch (e: Exception) { emptyList() }

    fun parseRules(json: String): List<FirewallRule> = try {
        val arr = JsonParser.parseString(json).asJsonArray
        arr.map { el ->
            val o = el.asJsonObject
            FirewallRule(
                id          = o.get("id")?.asInt ?: 0,
                action      = o.get("action")?.asString ?: "BLOCK",
                proto       = o.get("proto")?.asString ?: "ANY",
                srcIp       = o.get("src_ip")?.asLong ?: 0,
                dstIp       = o.get("dst_ip")?.asLong ?: 0,
                srcPort     = o.get("src_port")?.asInt ?: 0,
                dstPort     = o.get("dst_port")?.asInt ?: 0,
                description = o.get("description")?.asString ?: "",
                hitCount    = o.get("hit_count")?.asLong ?: 0,
            )
        }
    } catch (e: Exception) { emptyList() }

    fun parseThreats(json: String): List<ThreatEntry> = try {
        val arr = JsonParser.parseString(json).asJsonArray
        arr.map { el ->
            val o = el.asJsonObject
            ThreatEntry(
                ip       = o.get("ip")?.asString ?: "",
                reason   = o.get("reason")?.asString ?: "",
                banCount = o.get("ban_count")?.asInt ?: 0,
            )
        }
    } catch (e: Exception) { emptyList() }

    fun parseAnomalies(json: String): List<AnomalyEntry> = try {
        val arr = JsonParser.parseString(json).asJsonArray
        arr.map { el ->
            val o = el.asJsonObject
            AnomalyEntry(
                name     = o.get("name")?.asString ?: "",
                hitCount = o.get("hit_count")?.asInt ?: 0,
            )
        }
    } catch (e: Exception) { emptyList() }

    fun parseConnections(json: String): List<ConnectionEntry> = try {
        val arr = JsonParser.parseString(json).asJsonArray
        arr.map { el ->
            val o = el.asJsonObject
            ConnectionEntry(
                srcIp    = o.get("src_ip")?.asString ?: "",
                dstIp    = o.get("dst_ip")?.asString ?: "",
                srcPort  = o.get("src_port")?.asInt ?: 0,
                dstPort  = o.get("dst_port")?.asInt ?: 0,
                proto    = o.get("proto")?.asString ?: "",
                state    = o.get("state")?.asString ?: "",
                bytesIn  = o.get("bytes_in")?.asLong ?: 0,
                bytesOut = o.get("bytes_out")?.asLong ?: 0,
                ageSec   = o.get("age_sec")?.asDouble ?: 0.0,
            )
        }
    } catch (e: Exception) { emptyList() }

    fun parseLedger(json: String): List<LedgerEntry> = try {
        val arr = JsonParser.parseString(json).asJsonArray
        arr.mapIndexed { i, el ->
            try {
                val o = el.asJsonObject
                LedgerEntry(
                    seq       = o.get("seq")?.asLong ?: i.toLong(),
                    timestamp = o.get("ts")?.asString ?: o.get("timestamp")?.asString ?: "",
                    event     = o.get("event")?.asString ?: o.toString(),
                    hash      = o.get("hash")?.asString ?: "",
                )
            } catch (e: Exception) {
                LedgerEntry(seq = i.toLong(), timestamp = "", event = el.toString(), hash = "")
            }
        }
    } catch (e: Exception) { emptyList() }
}

fun parsePerAppStats(json: String): List<PerAppStatEntry> = try {
    val arr = JsonParser.parseString(json).asJsonArray
    arr.map { el ->
        val o = el.asJsonObject
        PerAppStatEntry(
            packageName     = o.get("pkg")?.asString ?: "",
            bytesIn         = o.get("bytes_in")?.asLong ?: 0,
            bytesOut        = o.get("bytes_out")?.asLong ?: 0,
            packetsTotal    = o.get("packets_total")?.asLong ?: 0,
            packetsBlocked  = o.get("packets_blocked")?.asLong ?: 0,
        )
    }.filter { it.packageName.isNotEmpty() }
        .sortedByDescending { it.bytesTotal }
} catch (e: Exception) { emptyList() }
