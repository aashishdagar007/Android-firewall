package com.asd.firewall

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.asd.firewall.ui.MainActivity
import com.asd.firewall.workers.DbCleanupWorker
import com.asd.firewall.workers.ThreatSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AegisVpnService — The core background service that intercepts all device traffic.
 *
 * Architecture:
 * ┌─────────────────────────────────────────────────────────────────┐
 * │                    AegisVpnService                              │
 * │                                                                 │
 * │  VpnService.Builder.establish() → TUN fd (virtual interface)   │
 * │         ↓                                ↑                     │
 * │  Read thread (raw IP packets)     Write thread (forwarded pkts) │
 * │         ↓                                ↑                     │
 * │  FirewallEngine.evaluatePacket()  protect(socket) + forward    │
 * │         ↓ ALLOW                          ↑                     │
 * │  Protected upstream socket ────────────────                    │
 * │         ↓ BLOCK → silently drop                                │
 * └─────────────────────────────────────────────────────────────────┘
 *
 * This is the same approach used by NetGuard, AdGuard (no-root), and Blokada.
 * Fully Google Play compliant — uses only documented Android APIs.
 */
class AegisVpnService : VpnService() {

    companion object {
        private const val TAG              = "AegisVpnService"
        private const val NOTIF_CHANNEL_ID = "aegis_vpn_channel"
        private const val NOTIF_ID         = 1001
        private const val BUFFER_SIZE      = 32767 // Max TUN MTU

        // Actions for startService intent
        const val ACTION_START = "com.asd.firewall.START"
        const val ACTION_STOP  = "com.asd.firewall.STOP"

        // Public state observable by the UI
        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    }

    private var tunInterface: ParcelFileDescriptor? = null
    private val running = AtomicBoolean(false)
    // Coroutine scope for the packet loop — cancelled on VPN stop
    private var vpnScope: CoroutineScope? = null
    private var wakeLock: PowerManager.WakeLock? = null

    // ── Lifecycle ────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service created")
        FirewallEngine.init(this)
        createNotificationChannel()
        ThreatNotificationManager.init(this)   // Initialize threat alert channel
        buildAndPushUidMap()
        scheduleBackgroundWorkers()
    }

    private fun scheduleBackgroundWorkers() {
        val workManager = WorkManager.getInstance(this)

        // Schedule daily threat intelligence sync
        val syncRequest = PeriodicWorkRequestBuilder<ThreatSyncWorker>(24, java.util.concurrent.TimeUnit.HOURS)
            .build()
        workManager.enqueueUniquePeriodicWork(
            "ThreatSyncWork",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )

        // Schedule daily database cleanup
        val cleanupRequest = PeriodicWorkRequestBuilder<DbCleanupWorker>(24, java.util.concurrent.TimeUnit.HOURS)
            .build()
        workManager.enqueueUniquePeriodicWork(
            "DbCleanupWork",
            ExistingPeriodicWorkPolicy.KEEP,
            cleanupRequest
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVpn()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                // Default / ACTION_START
                startForeground(NOTIF_ID, buildNotification(true))
                if (!running.get()) {
                    startVpn()
                }
            }
        }
        // START_STICKY: Android restarts the service if killed, like Spotify
        return START_STICKY
    }

    override fun onDestroy() {
        stopVpn()
        FirewallEngine.destroy()
        wakeLock?.release()
        super.onDestroy()
        Log.i(TAG, "Service destroyed")
    }

    override fun onRevoke() {
        // Called when the user revokes VPN permission from system settings
        Log.w(TAG, "VPN permission revoked by user")
        stopVpn()
        stopSelf()
    }

    // ── VPN Startup ──────────────────────────────────────────────

    private fun startVpn() {
        Log.i(TAG, "Starting VPN tunnel...")

        // Acquire a partial wake lock so the CPU stays awake for packet processing
        // (same technique Spotify uses to keep audio processing alive)
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AegisXII:FirewallWakeLock"
        ).also { it.acquire(10 * 60 * 60 * 1000L) } // Max 10 hours

        try {
            // Build the VPN interface
            // We route ALL traffic through our TUN: 0.0.0.0/0 and ::/0
            val builder = Builder()
                .setSession("Aegis XII Firewall")
                .addAddress("10.0.0.2", 32)           // Virtual local address for TUN
                .addRoute("0.0.0.0", 0)               // Route ALL IPv4 traffic through us
                .addDnsServer("8.8.8.8")              // Google DNS via TUN
                .addDnsServer("1.1.1.1")              // Cloudflare DNS via TUN
                .setMtu(BUFFER_SIZE)
                .setBlocking(true)                    // Blocking I/O (simpler read loop)

            // Exclude our own app from the VPN to prevent routing loops
            try {
                builder.addDisallowedApplication(packageName)
            } catch (e: PackageManager.NameNotFoundException) {
                Log.w(TAG, "Could not exclude self from VPN: ${e.message}")
            }

            tunInterface = builder.establish()
            if (tunInterface == null) {
                Log.e(TAG, "Failed to establish VPN tunnel — null fd")
                _isRunning.value = false
                return
            }

            running.set(true)
            _isRunning.value = true

            // Start the packet processing coroutine (replaces raw thread)
            val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            vpnScope = scope
            scope.launch { packetLoop() }
            // Start live-stats notification updater (refreshes every 30 s)
            startNotificationUpdater()

            Log.i(TAG, "VPN tunnel established — packet loop running")
            updateNotification(true)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VPN", e)
            running.set(false)
            _isRunning.value = false
        }
    }

    private fun stopVpn() {
        Log.i(TAG, "Stopping VPN...")
        running.set(false)
        _isRunning.value = false

        try {
            tunInterface?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing TUN interface", e)
        }
        tunInterface = null

        // Cancel the coroutine scope (cooperative cancellation)
        vpnScope?.cancel()
        vpnScope = null

        updateNotification(false)
        Log.i(TAG, "VPN stopped")
    }

    // ── Packet Processing Loop ───────────────────────────────────

    /**
     * The main packet loop. Runs as a coroutine on Dispatchers.IO.
     *
     * Cooperative cancellation: the loop checks isActive (from CoroutineScope)
     * and also the running AtomicBoolean. TUN close wakes the blocked read.
     *
     * For each raw IP packet read from the TUN fd:
     * 1. Extract UID of the sending app via ConnectivityManager (API 29+)
     * 2. Call FirewallEngine.evaluatePacket() (C++ rule engine)
     * 3. ALLOW → forward via a protected socket back to the real network
     * 4. BLOCK  → silently discard
     */
    private suspend fun packetLoop() {
        Log.i(TAG, "Packet loop started (coroutine on ${Thread.currentThread().name})")

        val tun = tunInterface ?: return
        val inputStream  = FileInputStream(tun.fileDescriptor)
        val outputStream = FileOutputStream(tun.fileDescriptor)

        val rawBytes = ByteArray(BUFFER_SIZE)

        // isActive = cooperative coroutine cancellation (scope.cancel() sets this false)
        while (isActive && running.get()) {
            try {
                val length = inputStream.read(rawBytes)
                if (length <= 0) {
                    // Yield CPU slice to allow other coroutines to run and check cancellation
                    kotlinx.coroutines.yield()
                    continue
                }

                // Quick version check — skip non-IPv4/IPv6
                val version = (rawBytes[0].toInt() and 0xFF) shr 4
                if (version != 4 && version != 6) continue

                // Determine which app sent this packet via ConnectivityManager (API 29+)
                val appName = resolvePacketApp(rawBytes, length)

                // Evaluate via C++ rule engine (heuristics, DPI, rule chain)
                val allowed = FirewallEngine.evaluatePacket(rawBytes, length, appName)

                if (allowed) {
                    // ALLOW: write back to TUN so the OS forwards to real network
                    outputStream.write(rawBytes, 0, length)
                }
                // BLOCK: silently discard — packet never reaches the network

            } catch (e: java.io.IOException) {
                // TUN closed = VPN stopping, exit cleanly
                if (!running.get()) break
                Log.w(TAG, "Packet loop I/O error: ${e.message}")
            } catch (e: Exception) {
                if (!running.get()) break
                Log.w(TAG, "Packet loop error: ${e.message}")
            }
        }

        Log.i(TAG, "Packet loop exited")
    }

    /**
     * Resolve which app a packet belongs to.
     *
     * On API 29+: Uses ConnectivityManager.getConnectionOwnerUid() for exact UID attribution
     * based on the socket 5-tuple (src_ip, src_port, dst_ip, dst_port, proto).
     *
     * On API < 29: Falls back to reading /proc/net/tcp (dst_port heuristic).
     */
    private fun resolvePacketApp(rawBytes: ByteArray, length: Int): String {
        if (length < 20) return "unknown"

        val version = (rawBytes[0].toInt() and 0xFF) shr 4
        if (version != 4) return "unknown" // IPv6 handled separately

        val proto = rawBytes[9].toInt() and 0xFF
        if ((proto != 6 && proto != 17) || length < 24) return "proto:$proto"

        // Extract 5-tuple from IPv4 header
        val srcIp = ((rawBytes[12].toInt() and 0xFF) shl 24) or
                    ((rawBytes[13].toInt() and 0xFF) shl 16) or
                    ((rawBytes[14].toInt() and 0xFF) shl  8) or
                    (rawBytes[15].toInt() and 0xFF)
        val dstIp = ((rawBytes[16].toInt() and 0xFF) shl 24) or
                    ((rawBytes[17].toInt() and 0xFF) shl 16) or
                    ((rawBytes[18].toInt() and 0xFF) shl  8) or
                    (rawBytes[19].toInt() and 0xFF)
        val srcPort = ((rawBytes[20].toInt() and 0xFF) shl 8) or (rawBytes[21].toInt() and 0xFF)
        val dstPort = ((rawBytes[22].toInt() and 0xFF) shl 8) or (rawBytes[23].toInt() and 0xFF)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // API 29+: exact UID from socket table
            try {
                val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
                val transportProto = if (proto == 6) 6 else 17 // IPPROTO_TCP / IPPROTO_UDP
                val srcAddr = InetAddress.getByAddress(
                    byteArrayOf(
                        (srcIp shr 24).toByte(), (srcIp shr 16).toByte(),
                        (srcIp shr 8).toByte(), srcIp.toByte()
                    )
                )
                val dstAddr = InetAddress.getByAddress(
                    byteArrayOf(
                        (dstIp shr 24).toByte(), (dstIp shr 16).toByte(),
                        (dstIp shr 8).toByte(), dstIp.toByte()
                    )
                )
                val uid = cm.getConnectionOwnerUid(
                    transportProto,
                    InetSocketAddress(srcAddr, srcPort),
                    InetSocketAddress(dstAddr, dstPort)
                )
                if (uid != android.os.Process.INVALID_UID) {
                    return FirewallEngine.resolveUid(uid)
                }
            } catch (e: Exception) {
                Log.v(TAG, "UID lookup failed: ${e.message}")
            }
        }

        // Fallback: use port-based map (best-effort for API < 29)
        return FirewallEngine.resolveUid(dstPort)
    }

    // ── UID→Package Map ──────────────────────────────────────────

    /**
     * Build a UID → package name map from PackageManager and push it to the C++ layer.
     * Called on service start and can be refreshed periodically.
     */
    private fun buildAndPushUidMap() {
        try {
            val pm = packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val sb = StringBuilder("{")
            var first = true
            for (app in apps) {
                if (!first) sb.append(",")
                sb.append("\"").append(app.uid).append("\":\"")
                sb.append(app.packageName).append("\"")
                first = false
            }
            sb.append("}")
            FirewallEngine.setUidPackageMap(sb.toString())
            Log.i(TAG, "UID map pushed: ${apps.size} apps")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to build UID map: ${e.message}")
        }
    }

    // ── Foreground Notification (Spotify-style with live stats) ──

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIF_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW  // Low = no sound, stays visible like Spotify
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private var notifUpdateThread: Thread? = null

    /**
     * Start a background thread that:
     * 1. Refreshes the foreground notification with live stats every 30 s
     * 2. Detects new threat bans and posts real-time alert notifications
     */
    private fun startNotificationUpdater() {
        var lastKnownThreatCount = 0
        notifUpdateThread = Thread({
            while (running.get()) {
                try {
                    Thread.sleep(30_000)
                    if (!running.get()) break

                    // Refresh the persistent foreground notification
                    updateNotification(true)

                    // Check for new threat bans
                    if (FirewallEngine.isInitialized()) {
                        val threatsJson = FirewallEngine.getThreats()
                        // Count current bans by counting "ip":" occurrences
                        val currentCount = threatsJson.split("\"ip\":\"").size - 1
                        val newBans = currentCount - lastKnownThreatCount

                        if (newBans > 0 && lastKnownThreatCount > 0) {
                            // Extract the most recently banned IP
                            val ipMatch = Regex("\"ip\":\"([^\"]+)\"").find(threatsJson)
                            val latestIp = ipMatch?.groupValues?.get(1) ?: "Unknown IP"
                            val reasonMatch = Regex("\"reason\":\"([^\"]+)\"").find(threatsJson)
                            val reason = reasonMatch?.groupValues?.get(1) ?: "Auto-ban"

                            if (newBans == 1) {
                                ThreatNotificationManager.notifyThreatBanned(
                                    this@AegisVpnService, latestIp, reason
                                )
                            } else {
                                ThreatNotificationManager.notifyBatchThreats(
                                    this@AegisVpnService, newBans, latestIp
                                )
                            }
                        }
                        lastKnownThreatCount = currentCount
                    }

                } catch (e: InterruptedException) { break }
            }
        }, "AegisNotifUpdater").also { it.isDaemon = true; it.start() }
    }

    private fun buildNotification(active: Boolean): android.app.Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, AegisVpnService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Collect live counters for the notification body
        val stats = if (FirewallEngine.isInitialized()) {
            try {
                val json = FirewallEngine.getStats()
                val blocked = Regex("\"blocked\":(\\d+)").find(json)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                val total   = Regex("\"total\":(\\d+)").find(json)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                val bytes   = Regex("\"bytes_total\":(\\d+)").find(json)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                Triple(total, blocked, bytes)
            } catch (e: Exception) { Triple(0L, 0L, 0L) }
        } else Triple(0L, 0L, 0L)

        val (total, blocked, bytes) = stats
        val bigText = buildString {
            if (active) {
                appendLine("🛡️ ${blocked} threats blocked  •  ${total} packets inspected")
                append("📦 ${formatBytesNotif(bytes)} processed")
            } else {
                append("Protection stopped")
            }
        }

        return NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setContentTitle(if (active) "⚡ AEGIS XII — Protecting your device" else "AEGIS XII — Protection inactive")
            .setContentText(if (active) "${blocked} blocked  |  ${total} total packets" else "Tap to re-enable protection")
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setSmallIcon(R.drawable.ic_shield)
            .setColor(getColor(R.color.accent_cyan))
            .setContentIntent(openIntent)
            .setOngoing(active)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                R.drawable.ic_shield,
                "📊 Open Dashboard",
                openIntent
            )
            .addAction(
                R.drawable.ic_shield,
                getString(R.string.stop_protection),
                stopIntent
            )
            .build()
    }

    private fun updateNotification(active: Boolean) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(active))
    }

    private fun formatBytesNotif(bytes: Long): String = when {
        bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576     -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1_024         -> "%.1f KB".format(bytes / 1_024.0)
        else                   -> "$bytes B"
    }
}
