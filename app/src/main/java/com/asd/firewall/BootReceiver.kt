package com.asd.firewall

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log

/**
 * BootReceiver — Auto-starts the Aegis XII firewall service on device boot.
 *
 * This is the same mechanism used by Spotify to resume playback after reboot.
 * Declared in AndroidManifest.xml with RECEIVE_BOOT_COMPLETED permission.
 *
 * The service only restarts if the user had previously enabled protection
 * (checked via SharedPreferences to respect user intent).
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG     = "AegisBootReceiver"
        private const val PREFS   = "aegis_prefs"
        private const val KEY_ENABLED = "vpn_enabled_on_boot"

        /** Call this when the user starts the VPN to persist their preference */
        fun setEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ENABLED, enabled)
                .apply()
        }

        fun isEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, false)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON" &&
            action != "com.htc.intent.action.QUICKBOOT_POWERON") {
            return
        }

        Log.i(TAG, "Boot completed — checking if firewall should auto-start")

        // Only auto-start if the user had previously enabled protection
        if (!isEnabled(context)) {
            Log.i(TAG, "Auto-start disabled by user preference — skipping")
            return
        }

        Log.i(TAG, "Auto-starting Aegis XII firewall service...")

        val serviceIntent = Intent(context, AegisVpnService::class.java).apply {
            action = AegisVpnService.ACTION_START
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
