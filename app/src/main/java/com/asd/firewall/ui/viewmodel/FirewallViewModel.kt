package com.asd.firewall.ui.viewmodel

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.asd.firewall.AppPolicyManager
import com.asd.firewall.FirewallEngine
import com.asd.firewall.db.FirewallDatabase
import com.asd.firewall.db.PacketLogEntity
import com.asd.firewall.db.StatsSnapshotEntity
import com.asd.firewall.db.PerAppLogEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * FirewallViewModel — bridges the JNI engine to the Compose UI.
 *
 * Polls the JNI layer every second for live data (stats, packets, etc.)
 * and exposes it as StateFlows consumed by the Composable screens.
 * Also persists blocked-packet logs and stats snapshots in Room.
 */
class FirewallViewModel(application: Application) : AndroidViewModel(application) {

    // ── Room database (lazy — opened on first access) ─────────────
    private val db by lazy { FirewallDatabase.getInstance(application) }
    private val packetLogDao by lazy { db.packetLogDao() }
    private val statsSnapshotDao by lazy { db.statsSnapshotDao() }
    private val perAppLogDao by lazy { db.perAppLogDao() }

    // ── Observable state flows ───────────────────────────────────

    private val _stats       = MutableStateFlow(LiveStats())
    val stats: StateFlow<LiveStats> = _stats.asStateFlow()

    private val _packets     = MutableStateFlow<List<PacketEntry>>(emptyList())
    val packets: StateFlow<List<PacketEntry>> = _packets.asStateFlow()

    private val _rules       = MutableStateFlow<List<FirewallRule>>(emptyList())
    val rules: StateFlow<List<FirewallRule>> = _rules.asStateFlow()

    private val _threats     = MutableStateFlow<List<ThreatEntry>>(emptyList())
    val threats: StateFlow<List<ThreatEntry>> = _threats.asStateFlow()

    private val _anomalies   = MutableStateFlow<List<AnomalyEntry>>(emptyList())
    val anomalies: StateFlow<List<AnomalyEntry>> = _anomalies.asStateFlow()

    private val _connections = MutableStateFlow<List<ConnectionEntry>>(emptyList())
    val connections: StateFlow<List<ConnectionEntry>> = _connections.asStateFlow()

    private val _ledger      = MutableStateFlow<List<LedgerEntry>>(emptyList())
    val ledger: StateFlow<List<LedgerEntry>> = _ledger.asStateFlow()

    private val _perAppStats = MutableStateFlow<List<PerAppStatEntry>>(emptyList())
    val perAppStats: StateFlow<List<PerAppStatEntry>> = _perAppStats.asStateFlow()

    private val _stealthMode = MutableStateFlow(false)
    val stealthMode: StateFlow<Boolean> = _stealthMode.asStateFlow()

    private val _rateLimit   = MutableStateFlow(1000)
    val rateLimit: StateFlow<Int> = _rateLimit.asStateFlow()

    // Stats history for the sparkline chart (last 60 readings)
    private val _statsHistory = MutableStateFlow<List<Long>>(emptyList())
    val statsHistory: StateFlow<List<Long>> = _statsHistory.asStateFlow()

    // ── New: Security Score (0–100) ──────────────────────────────
    private val _securityScore = MutableStateFlow(100)
    val securityScore: StateFlow<Int> = _securityScore.asStateFlow()

    // ── New: VPN Uptime tracking ──────────────────────────────────
    private val _vpnStartTime = MutableStateFlow(0L)
    val vpnStartTime: StateFlow<Long> = _vpnStartTime.asStateFlow()

    // ── New: Installed app list for per-app firewall ──────────────
    data class InstalledApp(
        val packageName: String,
        val label: String,
        val isSystemApp: Boolean,
    )
    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val installedApps: StateFlow<List<InstalledApp>> = _installedApps.asStateFlow()

    // Delegate policies directly from AppPolicyManager
    val appPolicies: StateFlow<Map<String, String>> = AppPolicyManager.policies

    // ── New: Total blocked / threats badge counts ─────────────────
    val threatBadge: StateFlow<Int> get() = _threatBadgeCount
    private val _threatBadgeCount = MutableStateFlow(0)

    val anomalyBadge: StateFlow<Int> get() = _anomalyBadgeCount
    private val _anomalyBadgeCount = MutableStateFlow(0)

    // ── New: Last blocked IP for marquee ─────────────────────────
    private val _lastBlockedEntry = MutableStateFlow<PacketEntry?>(null)
    val lastBlockedEntry: StateFlow<PacketEntry?> = _lastBlockedEntry.asStateFlow()

    private var pollJob: Job? = null
    private var lastTotal: Long = 0
    private val serviceStartMs = SystemClock.elapsedRealtime()

    // ── Lifecycle ────────────────────────────────────────────────

    init {
        startPolling()
        loadInstalledApps()
        _vpnStartTime.value = System.currentTimeMillis()
    }

    override fun onCleared() {
        stopPolling()
        super.onCleared()
    }

    // ── Polling ──────────────────────────────────────────────────

    private fun startPolling() {
        pollJob = viewModelScope.launch(Dispatchers.IO) {
            var tick = 0
            while (isActive) {
                // Every second: update stats and packets (high frequency)
                refreshStats()
                if (tick % 1 == 0) refreshPackets()

                // Every 3 seconds: update rules, threats, connections, per-app stats
                if (tick % 3 == 0) {
                    refreshRules()
                    refreshThreats()
                    refreshConnections()
                    refreshPerAppStats()
                }

                // Every 10 seconds: update anomalies and ledger (lower frequency)
                if (tick % 10 == 0) {
                    refreshAnomalies()
                    refreshLedger()
                    refreshStealthMode()
                    refreshRateLimit()
                }

                // Every 60 seconds: write a stats snapshot to the Room DB
                if (tick % 60 == 0) {
                    writeStatsSnapshot()
                }

                // Recompute security score every 5 seconds
                if (tick % 5 == 0) {
                    recomputeSecurityScore()
                }

                tick++
                delay(1000)
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    // ── Refresh methods ──────────────────────────────────────────

    private suspend fun refreshStats() {
        val s = JsonParsers.parseStats(FirewallEngine.getStats())
        _stats.value = s

        // Track history for sparkline
        val delta = s.total - lastTotal
        lastTotal = s.total
        val current = _statsHistory.value.toMutableList()
        current.add(delta)
        if (current.size > 60) current.removeAt(0)
        _statsHistory.value = current
    }

    private suspend fun refreshPackets() {
        val pkts = JsonParsers.parsePackets(FirewallEngine.getPackets(100))
        _packets.value = pkts
        // Track most recent blocked packet for the marquee
        val lastBlocked = pkts.firstOrNull { it.verdict == "BLOCK" }
        if (lastBlocked != null) _lastBlockedEntry.value = lastBlocked
    }

    private suspend fun refreshRules() {
        _rules.value = JsonParsers.parseRules(FirewallEngine.getRules())
    }

    private suspend fun refreshThreats() {
        val t = JsonParsers.parseThreats(FirewallEngine.getThreats())
        _threats.value = t
        _threatBadgeCount.value = t.size
    }

    private suspend fun refreshAnomalies() {
        val a = JsonParsers.parseAnomalies(FirewallEngine.getAnomalies())
        _anomalies.value = a
        _anomalyBadgeCount.value = a.count { it.hitCount > 0 }
    }

    private suspend fun refreshConnections() {
        _connections.value = JsonParsers.parseConnections(FirewallEngine.getConnections())
    }

    private suspend fun refreshLedger() {
        _ledger.value = JsonParsers.parseLedger(FirewallEngine.getLedger(50))
    }

    private suspend fun refreshPerAppStats() {
        _perAppStats.value = JsonParsers.parsePerAppStats(FirewallEngine.getPerAppStats())
    }

    private fun refreshStealthMode() {
        _stealthMode.value = FirewallEngine.getStealthMode()
    }

    private fun refreshRateLimit() {
        _rateLimit.value = FirewallEngine.getRateLimit()
    }

    /** Write a point-in-time stats snapshot to Room for historical sparkline */
    private suspend fun writeStatsSnapshot() {
        val s = _stats.value
        val timestamp = System.currentTimeMillis()
        val snapshot = StatsSnapshotEntity(
            timestampMs    = timestamp,
            totalPackets   = s.total,
            blockedPackets = s.blocked,
            allowedPackets = s.allowed,
            bytesTotal     = s.bytesTotal,
            tcpCount       = s.tcp,
            udpCount       = s.udp,
            icmpCount      = s.icmp,
        )
        try { statsSnapshotDao.insert(snapshot) } catch (_: Exception) {}

        // Persist per-app stats
        val perApp = _perAppStats.value
        if (perApp.isNotEmpty()) {
            val appEntities = perApp.map { p ->
                PerAppLogEntity(
                    timestampMs    = timestamp,
                    packageName    = p.packageName,
                    bytesIn        = p.bytesIn,
                    bytesOut       = p.bytesOut,
                    packetsBlocked = p.packetsBlocked
                )
            }
            try { perAppLogDao.insertAll(appEntities) } catch (_: Exception) {}
        }
    }

    /** Persist newly blocked packets to the Room DB */
    private suspend fun persistBlockedPackets(packets: List<PacketEntry>) {
        val blocked = packets.filter { it.verdict == "BLOCK" }
        if (blocked.isEmpty()) return
        val entities = blocked.map { p ->
            PacketLogEntity(
                timestampMs      = System.currentTimeMillis(),
                srcIp            = p.srcIp,
                dstIp            = p.dstIp,
                srcPort          = p.srcPort,
                dstPort          = p.dstPort,
                proto            = p.proto,
                verdict          = p.verdict,
                processName      = p.process,
                sizeBytes        = p.size,
                ruleDescription  = "Auto-blocked",
            )
        }
        try { packetLogDao.insertAll(entities) } catch (_: Exception) {}
    }

    /** Expose recent packet log history from Room (for ReportScreen) */
    suspend fun getRecentBlockedFromDb(limit: Int = 200): List<PacketLogEntity> =
        try { packetLogDao.getRecentBlocked(limit) } catch (_: Exception) { emptyList() }

    /** Count of blocked packets stored in DB */
    suspend fun getDbBlockedCount(): Long =
        try { packetLogDao.count() } catch (_: Exception) { 0L }

    /**
     * Security Score formula (0–100):
     *   - Starts at 100
     *   - -2 per active banned threat (max -40)
     *   - -1 per anomaly triggered (max -20)
     *   - -10 if block rate > 30% (something is very wrong)
     *   - +5 if stealth mode is on
     */
    private fun recomputeSecurityScore() {
        val threatPenalty  = (_threats.value.size * 2).coerceAtMost(40)
        val anomalyPenalty = (_anomalies.value.count { it.hitCount > 0 }).coerceAtMost(20)
        val highBlockPenalty = if (_stats.value.blockRate > 0.3f) 10 else 0
        val stealthBonus = if (_stealthMode.value) 5 else 0
        val score = (100 - threatPenalty - anomalyPenalty - highBlockPenalty + stealthBonus)
            .coerceIn(0, 100)
        _securityScore.value = score
    }

    // ── App list loading ─────────────────────────────────────────

    fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = getApplication<Application>().packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { it.packageName != getApplication<Application>().packageName }
                .map { info ->
                    InstalledApp(
                        packageName = info.packageName,
                        label       = pm.getApplicationLabel(info).toString(),
                        isSystemApp = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    )
                }
                .sortedBy { it.label.lowercase() }
            _installedApps.value = apps
        }
    }

    // ── Actions (called from UI) ─────────────────────────────────

    fun addRule(jsonBody: String) {
        viewModelScope.launch(Dispatchers.IO) {
            FirewallEngine.addRule(jsonBody)
            delay(200)
            refreshRules()
        }
    }

    fun deleteRule(ruleId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            FirewallEngine.deleteRule(ruleId)
            delay(200)
            refreshRules()
        }
    }

    fun banIp(ip: String, reason: String = "Manual ban") {
        viewModelScope.launch(Dispatchers.IO) {
            FirewallEngine.banIp(ip, reason)
            delay(200)
            refreshThreats()
        }
    }

    fun unbanIp(ip: String) {
        viewModelScope.launch(Dispatchers.IO) {
            FirewallEngine.unbanIp(ip)
            delay(200)
            refreshThreats()
        }
    }

    fun setStealthMode(enabled: Boolean) {
        FirewallEngine.setStealthMode(enabled)
        _stealthMode.value = enabled
        recomputeSecurityScore()
    }

    fun setRateLimit(pps: Int) {
        FirewallEngine.setRateLimit(pps)
        _rateLimit.value = pps
    }

    fun setDefaultPolicy(policy: String) {
        viewModelScope.launch(Dispatchers.IO) {
            FirewallEngine.setDefaultPolicy(policy)
            AppPolicyManager.setDefaultPolicyGlobal(policy)
        }
    }

    fun setAppPolicy(packageName: String, policy: String) {
        AppPolicyManager.setPolicy(packageName, policy)
    }

    fun refreshAll() {
        viewModelScope.launch(Dispatchers.IO) {
            refreshStats()
            refreshPackets()
            refreshRules()
            refreshThreats()
            refreshConnections()
            refreshAnomalies()
            refreshLedger()
            refreshPerAppStats()
        }
    }

    /**
     * Generate a structured Markdown security report aggregating live stats, threats,
     * anomalies, and per-app data. Returns the report as a String for sharing.
     */
    fun generateSecurityReport(): String {
        val s = _stats.value
        val threats = _threats.value
        val anomalies = _anomalies.value.filter { it.hitCount > 0 }
        val perApp = _perAppStats.value.take(10)
        val rules = _rules.value

        val sb = StringBuilder()
        val timestamp = java.text.SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()
        ).format(java.util.Date())

        sb.appendLine("# 🛡️ Aegis XII — Security Report")
        sb.appendLine("Generated: $timestamp")
        sb.appendLine()
        sb.appendLine("## 📊 Traffic Summary")
        sb.appendLine("| Metric | Value |")
        sb.appendLine("|--------|-------|")
        sb.appendLine("| Total Packets | ${s.total} |")
        sb.appendLine("| Allowed | ${s.allowed} (${String.format("%.1f", s.allowRate * 100)}%) |")
        sb.appendLine("| Blocked | ${s.blocked} (${String.format("%.1f", s.blockRate * 100)}%) |")
        sb.appendLine("| TCP | ${s.tcp} |")
        sb.appendLine("| UDP | ${s.udp} |")
        sb.appendLine("| ICMP | ${s.icmp} |")
        sb.appendLine("| Data Processed | ${formatBytes(s.bytesTotal)} |")
        sb.appendLine("| Security Score | ${_securityScore.value}/100 |")
        sb.appendLine()

        sb.appendLine("## 🚨 Active Threats (${threats.size} banned IPs)")
        if (threats.isEmpty()) {
            sb.appendLine("No active threats.")
        } else {
            sb.appendLine("| IP | Reason | Ban Count |")
            sb.appendLine("|----|--------|-----------|")
            threats.take(20).forEach { t ->
                sb.appendLine("| ${t.ip} | ${t.reason} | ${t.banCount} |")
            }
            if (threats.size > 20) sb.appendLine("*... and ${threats.size - 20} more*")
        }
        sb.appendLine()

        sb.appendLine("## ⚠️ Anomaly Detections (${anomalies.size} triggered)")
        if (anomalies.isEmpty()) {
            sb.appendLine("No anomalies detected.")
        } else {
            sb.appendLine("| Anomaly | Hit Count |")
            sb.appendLine("|---------|-----------|")
            anomalies.forEach { a ->
                sb.appendLine("| ${a.name} | ${a.hitCount} |")
            }
        }
        sb.appendLine()

        sb.appendLine("## 📱 Top Apps by Traffic")
        if (perApp.isEmpty()) {
            sb.appendLine("No per-app data available yet.")
        } else {
            sb.appendLine("| App | Data In | Data Out | Blocked Pkts |")
            sb.appendLine("|-----|---------|----------|-------------|")
            perApp.forEach { p ->
                sb.appendLine("| ${p.packageName.substringAfterLast('.')} | ${formatBytes(p.bytesIn)} | ${formatBytes(p.bytesOut)} | ${p.packetsBlocked} |")
            }
        }
        sb.appendLine()

        sb.appendLine("## 📋 Active Rules (${rules.size})")
        if (rules.isEmpty()) {
            sb.appendLine("No custom rules configured.")
        } else {
            sb.appendLine("| # | Action | Proto | Port | Description |")
            sb.appendLine("|---|--------|-------|------|-------------|")
            rules.take(15).forEach { r ->
                val port = if (r.dstPort == 0) "*" else r.dstPort.toString()
                sb.appendLine("| ${r.id} | ${r.action} | ${r.proto} | $port | ${r.description} |")
            }
        }
        sb.appendLine()
        sb.appendLine("---")
        sb.appendLine("*Report generated by Aegis XII Android Firewall v2.0.0*")

        return sb.toString()
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576     -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1_024         -> "%.1f KB".format(bytes / 1_024.0)
        else                   -> "$bytes B"
    }
}
