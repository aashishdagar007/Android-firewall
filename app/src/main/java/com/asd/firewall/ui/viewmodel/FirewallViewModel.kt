package com.asd.firewall.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asd.firewall.FirewallEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * FirewallViewModel — bridges the JNI engine to the Compose UI.
 *
 * Polls the JNI layer every second for live data (stats, packets, etc.)
 * and exposes it as StateFlows consumed by the Composable screens.
 *
 * This matches the polling pattern used by the desktop dashboard
 * (which polls the REST API every second via JavaScript).
 */
class FirewallViewModel : ViewModel() {

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

    private var pollJob: Job? = null
    private var lastTotal: Long = 0

    // ── Lifecycle ────────────────────────────────────────────────

    init {
        startPolling()
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
        _packets.value = JsonParsers.parsePackets(FirewallEngine.getPackets(100))
    }

    private suspend fun refreshRules() {
        _rules.value = JsonParsers.parseRules(FirewallEngine.getRules())
    }

    private suspend fun refreshThreats() {
        _threats.value = JsonParsers.parseThreats(FirewallEngine.getThreats())
    }

    private suspend fun refreshAnomalies() {
        _anomalies.value = JsonParsers.parseAnomalies(FirewallEngine.getAnomalies())
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
    }

    fun setRateLimit(pps: Int) {
        FirewallEngine.setRateLimit(pps)
        _rateLimit.value = pps
    }

    fun setDefaultPolicy(policy: String) {
        viewModelScope.launch(Dispatchers.IO) {
            FirewallEngine.setDefaultPolicy(policy)
        }
    }
}
