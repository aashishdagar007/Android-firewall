package com.asd.firewall.receivers

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.asd.firewall.FirewallEngine
import com.asd.firewall.ThreatNotificationManager

/**
 * NotificationActionReceiver — Handles actions triggered directly from threat notifications.
 * Allows the user to unban/trust an IP without having to open the app.
 */
class NotificationActionReceiver : BroadcastReceiver() {
    
    companion object {
        const val ACTION_UNBAN_IP = "com.asd.firewall.UNBAN_IP"
        const val EXTRA_IP = "extra_ip"
        const val EXTRA_NOTIF_ID = "extra_notif_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_UNBAN_IP) {
            val ip = intent.getStringExtra(EXTRA_IP) ?: return
            val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)
            
            Log.i("NotificationAction", "User requested to unban IP: $ip from notification")
            
            if (FirewallEngine.isInitialized()) {
                // Find and delete the block rule associated with this IP.
                // In a production app, the C++ engine would have an unbanIp(ip) method.
                // We'll simulate finding it in the rules and deleting it.
                val rulesJson = FirewallEngine.getRules()
                val rulesList = com.asd.firewall.ui.viewmodel.JsonParsers.parseRules(rulesJson)
                
                val rule = rulesList.find { it.description.contains(ip) || it.action == "BLOCK" }
                if (rule != null) {
                    FirewallEngine.deleteRule(rule.id)
                    Toast.makeText(context, "Unbanned IP: $ip", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "IP $ip is no longer banned", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "Firewall engine offline. Cannot unban.", Toast.LENGTH_SHORT).show()
            }
            
            // Dismiss the notification since we've handled it
            if (notifId != -1) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(notifId)
            }
        }
    }
}
