package com.asd.firewall

import android.content.Context
import android.util.Log
import java.io.File

/**
 * FirewallEngine — Kotlin singleton wrapping the C++ JNI native library (libaegisjni.so).
 *
 * All native calls are declared as `external fun` and map directly to the
 * JNI functions in aegis_jni.cpp.
 *
 * Usage:
 *   FirewallEngine.init(context)
 *   val verdict = FirewallEngine.evaluatePacket(rawBytes, length, "com.example.app")
 *   val stats   = FirewallEngine.getStats()   // returns JSON string
 */
object FirewallEngine {

    private const val TAG = "FirewallEngine"
    @Volatile private var initialized = false

    // Path to rules.conf inside the app's private files dir (set during init)
    @Volatile private var configFilePath: String = ""

    /** Returns true if the native engine has been initialized and is ready. */
    fun isInitialized(): Boolean = initialized

    // ── Load the JNI shared library ─────────────────────────────
    init {
        System.loadLibrary("aegisjni")
    }

    /**
     * Initialize the engine with paths inside the app's private storage.
     * Must be called before any other method (called by AegisVpnService.onCreate).
     */
    fun init(context: Context) {
        if (initialized) return

        val filesDir = context.filesDir

        // Create required subdirectories
        File(filesDir, "logs").mkdirs()
        File(filesDir, "config").mkdirs()

        val logPath     = File(filesDir, "logs/firewall.log").absolutePath
        val configPath  = File(filesDir, "config/rules.conf").absolutePath
        val ledgerDir   = File(filesDir, "logs").absolutePath

        // Copy default rules.conf from assets if not already present
        val configFile = File(configPath)
        if (!configFile.exists()) {
            try {
                context.assets.open("rules.conf").use { input ->
                    configFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Log.i(TAG, "Default rules.conf copied from assets")
            } catch (e: Exception) {
                Log.w(TAG, "No default rules.conf in assets, starting with empty rules")
                // Create an empty file so the C++ parser doesn't error
                configFile.createNewFile()
            }
        }

        nativeCreate(logPath, configPath, ledgerDir)
        configFilePath = configPath  // Store for saveRules()
        initialized = true
        Log.i(TAG, "FirewallEngine initialized")
    }

    /**
     * Destroy the engine on service stop.
     */
    fun destroy() {
        if (!initialized) return
        nativeDestroy()
        initialized = false
        Log.i(TAG, "FirewallEngine destroyed")
    }

    /**
     * Evaluate a raw IPv4 packet from the TUN interface.
     * @return true = ALLOW (forward the packet), false = BLOCK (drop it)
     */
    fun evaluatePacket(rawPacket: ByteArray, length: Int, appName: String): Boolean {
        if (!initialized) return true  // Fail open if not ready
        return nativeEvaluatePacket(rawPacket, length, appName) == 1
    }

    // ── Data accessors (all return JSON strings) ────────────────

    /** Live counters: total, allowed, blocked, tcp, udp, icmp, bytes_total */
    fun getStats(): String = if (initialized) nativeGetStats() else "{}"

    /** Current rule chain as JSON array */
    fun getRules(): String = if (initialized) nativeGetRules() else "[]"

    /** Add a rule from a JSON body (same schema as the desktop REST API) */
    fun addRule(jsonBody: String): Boolean {
        val result = initialized && nativeAddRule(jsonBody)
        if (result) saveRules()
        return result
    }

    /** Delete a rule by its ID */
    fun deleteRule(ruleId: Int): Boolean {
        val result = initialized && nativeDeleteRule(ruleId)
        if (result) saveRules()
        return result
    }

    /**
     * Persist the current rule chain to rules.conf so it survives service restarts.
     * Called automatically after addRule() and deleteRule().
     * Format: one JSON object per line (the C++ ConfigParser.load() reads this).
     */
    private fun saveRules() {
        if (!initialized || configFilePath.isEmpty()) return
        try {
            val rulesJson = nativeGetRules() // JSON array
            // Convert JSON array to newline-delimited format that ConfigParser can read
            // Each rule is written as a simplified conf line:
            //   ACTION PROTO DST_PORT DESC
            // We write it as raw JSON per-line so future C++ can be extended to parse JSON
            val file = File(configFilePath)
            file.bufferedWriter().use { w ->
                // Write header comment
                w.write("# Aegis XII auto-saved rules — do not edit manually\n")
                // Parse and re-serialize each rule as a conf line
                val arr = com.google.gson.JsonParser.parseString(rulesJson).asJsonArray
                for (el in arr) {
                    val o = el.asJsonObject
                    val action  = o.get("action")?.asString ?: continue
                    val proto   = o.get("proto")?.asString ?: "ANY"
                    val dstPort = o.get("dst_port")?.asInt ?: 0
                    val desc    = o.get("description")?.asString ?: ""
                    val portStr = if (dstPort == 0) "*" else dstPort.toString()
                    w.write("$action $proto $portStr $desc\n")
                }
            }
            Log.i(TAG, "Rules saved to $configFilePath")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save rules: ${e.message}")
        }
    }

    /** Last N packets from the ring buffer as JSON array */
    fun getPackets(n: Int = 100): String = if (initialized) nativeGetPackets(n) else "[]"

    /** Anomaly hit counts as JSON array */
    fun getAnomalies(): String = if (initialized) nativeGetAnomalies() else "[]"

    /** Live connection tracking table as JSON array */
    fun getConnections(): String = if (initialized) nativeGetConnections() else "[]"

    /** Threat ban table as JSON array */
    fun getThreats(): String = if (initialized) nativeGetThreats() else "[]"

    /** Per-app bandwidth statistics as JSON array (pkg, bytes_in, bytes_out, packets_total, packets_blocked) */
    fun getPerAppStats(): String = if (initialized) nativeGetPerAppStats() else "[]"

    /** Manually ban an IP */
    fun banIp(ip: String, reason: String = "Manual ban"): Boolean =
        initialized && nativeBanIp(ip, reason)

    /** Manually unban an IP */
    fun unbanIp(ip: String): Boolean = initialized && nativeUnbanIp(ip)

    /** Enable/disable stealth mode */
    fun setStealthMode(enabled: Boolean) { if (initialized) nativeSetStealthMode(enabled) }

    /** Get stealth mode state */
    fun getStealthMode(): Boolean = initialized && nativeGetStealthMode()

    /** Last N ledger entries as JSON array */
    fun getLedger(n: Int = 50): String = if (initialized) nativeGetLedger(n) else "[]"

    /** Set default firewall policy: "ALLOW" or "BLOCK" */
    fun setDefaultPolicy(policy: String) { if (initialized) nativeSetDefaultPolicy(policy) }

    /** Get current rate limit (packets/sec) */
    fun getRateLimit(): Int = if (initialized) nativeGetRateLimit() else 1000

    /** Set rate limit (packets/sec) */
    fun setRateLimit(pps: Int) { if (initialized) nativeSetRateLimit(pps) }

    /**
     * Push UID → package name map to the C++ resolver.
     * JSON format: {"10234": "com.google.android.youtube", ...}
     */
    fun setUidPackageMap(jsonMap: String) { if (initialized) nativeSetUidPackageMap(jsonMap) }

    /** Resolve a UID to a package name */
    fun resolveUid(uid: Int): String = if (initialized) nativeResolveUid(uid) else "uid:$uid"

    // ── Native (JNI) declarations ────────────────────────────────

    @JvmStatic private external fun nativeCreate(logPath: String, configPath: String, ledgerDir: String)
    @JvmStatic private external fun nativeDestroy()
    @JvmStatic private external fun nativeEvaluatePacket(rawPacket: ByteArray, length: Int, appName: String): Int
    @JvmStatic private external fun nativeGetStats(): String
    @JvmStatic private external fun nativeGetRules(): String
    @JvmStatic private external fun nativeAddRule(jsonBody: String): Boolean
    @JvmStatic private external fun nativeDeleteRule(ruleId: Int): Boolean
    @JvmStatic private external fun nativeGetPackets(n: Int): String
    @JvmStatic private external fun nativeGetAnomalies(): String
    @JvmStatic private external fun nativeGetConnections(): String
    @JvmStatic private external fun nativeGetThreats(): String
    @JvmStatic private external fun nativeBanIp(ip: String, reason: String): Boolean
    @JvmStatic private external fun nativeUnbanIp(ip: String): Boolean
    @JvmStatic private external fun nativeSetStealthMode(enabled: Boolean)
    @JvmStatic private external fun nativeGetStealthMode(): Boolean
    @JvmStatic private external fun nativeGetLedger(n: Int): String
    @JvmStatic private external fun nativeSetDefaultPolicy(policy: String)
    @JvmStatic private external fun nativeGetRateLimit(): Int
    @JvmStatic private external fun nativeSetRateLimit(pps: Int)
    @JvmStatic private external fun nativeSetUidPackageMap(jsonMap: String)
    @JvmStatic private external fun nativeResolveUid(uid: Int): String
    @JvmStatic private external fun nativeGetPerAppStats(): String
}
