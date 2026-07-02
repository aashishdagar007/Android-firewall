package com.asd.firewall.ui.viewmodel

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.asd.firewall.AppPolicyManager
import com.asd.firewall.FirewallEngine
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
 */
class FirewallViewModel(application: Application) : AndroidViewModel(application) {

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

                // Every 3 seconds: update rules, threats, connections
                if (tick % 3 == 0) {
                    refreshRules()
                    refreshThreats()
                    refreshConnections()
                }

                // Every 10 seconds: update anomalies and ledger (lower frequency)
                if (tick % 10 == 0) {
                    refreshAnomalies()
                    refreshLedger()
                    refreshStealthMode()
                    refreshRateLimit()
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

    private fun refreshStealthMode() {
        _stealthMode.value = FirewallEngine.getStealthMode()
    }

    private fun refreshRateLimit() {
        _rateLimit.value = FirewallEngine.getRateLimit()
    }

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
        }
    }
}
