package com.asd.firewall

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AppPolicyManager — persists per-app firewall policies.
 *
 * Policy values:
 *   "ALLOW"   → traffic allowed (default for all apps)
 *   "BLOCK"   → all traffic from this app is dropped
 *   "MONITOR" → traffic passes but is flagged/logged
 */
object AppPolicyManager {

    private const val PREFS_NAME = "aegis_app_policies"
    private const val KEY_PREFIX  = "policy_"
    private const val KEY_ONBOARDING_DONE = "onboarding_done"
    private const val KEY_AUTO_START      = "auto_start"
    private const val KEY_DOH_ENABLED     = "doh_enabled"
    private const val KEY_DEFAULT_POLICY  = "default_policy_global"

    private lateinit var prefs: SharedPreferences

    private val _policies = MutableStateFlow<Map<String, String>>(emptyMap())
    val policies: StateFlow<Map<String, String>> = _policies.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadAll()
    }

    private fun loadAll() {
        val map = mutableMapOf<String, String>()
        prefs.all.forEach { (key, value) ->
            if (key.startsWith(KEY_PREFIX) && value is String) {
                map[key.removePrefix(KEY_PREFIX)] = value
            }
        }
        _policies.value = map
    }

    fun setPolicy(packageName: String, policy: String) {
        prefs.edit().putString(KEY_PREFIX + packageName, policy).apply()
        val updated = _policies.value.toMutableMap()
        updated[packageName] = policy
        _policies.value = updated

        // Propagate to C++ engine as a rule
        pushPolicyToEngine(packageName, policy)
    }

    fun getPolicy(packageName: String): String =
        _policies.value[packageName] ?: "ALLOW"

    private fun pushPolicyToEngine(packageName: String, policy: String) {
        if (!FirewallEngine.isInitialized()) return
        when (policy) {
            "BLOCK"   -> FirewallEngine.addRule(
                """{"action":"BLOCK","proto":"ANY","dst_port":0,"description":"App block: $packageName","app":"$packageName"}"""
            )
            "ALLOW"   -> FirewallEngine.addRule(
                """{"action":"ALLOW","proto":"ANY","dst_port":0,"description":"App allow: $packageName","app":"$packageName"}"""
            )
            // MONITOR = allow + extra logging (handled in evaluatePacket)
        }
    }

    // ── Preference helpers ────────────────────────────────────────

    fun isOnboardingDone(): Boolean = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
    fun setOnboardingDone() = prefs.edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()

    fun isAutoStartEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_START, true)
    fun setAutoStart(enabled: Boolean) = prefs.edit().putBoolean(KEY_AUTO_START, enabled).apply()

    fun isDohEnabled(): Boolean = prefs.getBoolean(KEY_DOH_ENABLED, false)
    fun setDohEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_DOH_ENABLED, enabled).apply()

    fun getDefaultPolicyGlobal(): String = prefs.getString(KEY_DEFAULT_POLICY, "ALLOW") ?: "ALLOW"
    fun setDefaultPolicyGlobal(policy: String) =
        prefs.edit().putString(KEY_DEFAULT_POLICY, policy).apply()
}
