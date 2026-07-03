package com.asd.firewall

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.asd.firewall.receivers.NotificationActionReceiver
import com.asd.firewall.ui.MainActivity
import java.util.concurrent.atomic.AtomicInteger

/**
 * ThreatNotificationManager — Posts distinct alert notifications when new IPs are auto-banned.
 *
 * Uses a dedicated high-priority notification channel (distinct from the VPN foreground channel)
 * so that threat alerts are heads-up notifications that appear even on lock screen.
 *
 * Usage:
 *   ThreatNotificationManager.init(context)
 *   ThreatNotificationManager.notifyThreatBanned("192.168.1.1", "SYN Flood / Port Scan")
 */
object ThreatNotificationManager {

    private const val THREAT_CHANNEL_ID   = "aegis_threat_alerts"
    private const val THREAT_CHANNEL_NAME = "Threat Alerts"
    private const val BASE_NOTIF_ID       = 9000  // IDs 9000–9999 reserved for threats

    private val notifCounter = AtomicInteger(0)
    private var initialized  = false

    /** Call once on service start */
    fun init(context: Context) {
        if (initialized) return
        createChannel(context)
        initialized = true
    }

    /**
     * Show a heads-up notification for a newly banned IP.
     * @param ip     The IP address that was auto-banned
     * @param reason The ban reason (e.g. "SYN Flood / Port Scan", "Rate Limit Exceeded")
     */
    fun notifyThreatBanned(context: Context, ip: String, reason: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Deep-link into ThreatsScreen
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "threats")
        }
        val pi = PendingIntent.getActivity(
            context,
            notifCounter.get(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Use rolling IDs 9000–9099 so at most 100 threat notifications stack
        val notifId = BASE_NOTIF_ID + (notifCounter.getAndIncrement() % 100)

        // Action to Unban IP
        val unbanIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_UNBAN_IP
            putExtra(NotificationActionReceiver.EXTRA_IP, ip)
            putExtra(NotificationActionReceiver.EXTRA_NOTIF_ID, notifId)
        }
        val unbanPi = PendingIntent.getBroadcast(
            context,
            notifId,
            unbanIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, THREAT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("🚨 Threat Blocked!")
            .setContentText("$ip auto-banned: $reason")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        "IP Address: $ip\n" +
                        "Reason: $reason\n" +
                        "Tap to view Threat Details →"
                    )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setColor(0xFFFF4444.toInt()) // Danger red
            .addAction(
                R.drawable.ic_shield,
                "View Threats",
                pi
            )
            .addAction(
                0, // No icon
                "Unban IP",
                unbanPi
            )
            .build()

        nm.notify(notifId, notif)
    }

    /**
     * Post a summary notification when multiple bans happen in quick succession
     * (reduces notification spam during active attacks).
     */
    fun notifyBatchThreats(context: Context, count: Int, topIp: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("navigate_to", "threats")
        }
        val pi = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, THREAT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("🚨 $count Threats Blocked!")
            .setContentText("Latest: $topIp • Tap for details")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setColor(0xFFFF4444.toInt())
            .build()

        nm.notify(BASE_NOTIF_ID, notif)
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                THREAT_CHANNEL_ID,
                THREAT_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH  // Heads-up alerts
            ).apply {
                description = "Real-time alerts when hostile IPs are auto-banned by Aegis XII"
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
                lightColor = 0xFFFF4444.toInt()
            }
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }
}
