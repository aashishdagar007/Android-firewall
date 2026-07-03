package com.asd.firewall.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import kotlinx.coroutines.flow.Flow

// ── Entity: Individual blocked packet log ───────────────────────────
@Entity(tableName = "packet_logs")
data class PacketLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs: Long,         // System.currentTimeMillis()
    val srcIp: String,
    val dstIp: String,
    val srcPort: Int,
    val dstPort: Int,
    val proto: String,             // "TCP", "UDP", "ICMP", "ANY"
    val verdict: String,           // "ALLOW" or "BLOCK"
    val processName: String,       // app package name or "unknown"
    val sizeBytes: Int,
    val ruleDescription: String,   // which rule triggered the verdict
)

// ── Entity: Periodic stats snapshot for sparkline history ───────────
@Entity(tableName = "stats_snapshots")
data class StatsSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs: Long,         // when this snapshot was captured
    val totalPackets: Long,
    val blockedPackets: Long,
    val allowedPackets: Long,
    val bytesTotal: Long,
    val tcpCount: Long,
    val udpCount: Long,
    val icmpCount: Long,
)

// ── Entity: Per-app bandwidth usage history ─────────────────────────
@Entity(tableName = "per_app_logs")
data class PerAppLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs: Long,
    val packageName: String,
    val bytesIn: Long,
    val bytesOut: Long,
    val packetsBlocked: Long
)

// ── DAO: Packet log operations ──────────────────────────────────────
@Dao
interface PacketLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: PacketLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<PacketLogEntity>)

    /** Recent N packets, newest first */
    @Query("SELECT * FROM packet_logs ORDER BY timestampMs DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 500): List<PacketLogEntity>

    /** Only blocked packets for threat analysis */
    @Query("SELECT * FROM packet_logs WHERE verdict = 'BLOCK' ORDER BY timestampMs DESC LIMIT :limit")
    suspend fun getRecentBlocked(limit: Int = 200): List<PacketLogEntity>

    /** Count of blocked packets in the last N minutes */
    @Query("SELECT COUNT(*) FROM packet_logs WHERE verdict = 'BLOCK' AND timestampMs > :sinceMs")
    suspend fun countBlockedSince(sinceMs: Long): Long

    /** Unique blocked process names for per-app threat summary */
    @Query("SELECT DISTINCT processName FROM packet_logs WHERE verdict = 'BLOCK' ORDER BY timestampMs DESC LIMIT 50")
    suspend fun getTopBlockedApps(): List<String>

    /** Purge entries older than N days to keep DB size manageable */
    @Query("DELETE FROM packet_logs WHERE timestampMs < :cutoffMs")
    suspend fun purgeOlderThan(cutoffMs: Long)

    /** Total row count */
    @Query("SELECT COUNT(*) FROM packet_logs")
    suspend fun count(): Long
}

// ── DAO: Stats snapshot operations ──────────────────────────────────
@Dao
interface StatsSnapshotDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(snapshot: StatsSnapshotEntity)

    /** Last 24 hours of snapshots for the dashboard sparkline */
    @Query("SELECT * FROM stats_snapshots ORDER BY timestampMs DESC LIMIT 1440")
    fun getRecentFlow(): Flow<List<StatsSnapshotEntity>>

    /** Last N snapshots as a plain list */
    @Query("SELECT * FROM stats_snapshots ORDER BY timestampMs DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 60): List<StatsSnapshotEntity>

    /** Purge old snapshots (keep last 7 days) */
    @Query("DELETE FROM stats_snapshots WHERE timestampMs < :cutoffMs")
    suspend fun purgeOlderThan(cutoffMs: Long)

    /** Total count */
    @Query("SELECT COUNT(*) FROM stats_snapshots")
    suspend fun count(): Long
}

// ── DAO: Per-app log operations ─────────────────────────────────────
@Dao
interface PerAppLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<PerAppLogEntity>)

    /** Get historical data for a specific package */
    @Query("SELECT * FROM per_app_logs WHERE packageName = :packageName ORDER BY timestampMs DESC LIMIT :limit")
    suspend fun getRecentForPackage(packageName: String, limit: Int = 100): List<PerAppLogEntity>

    /** Purge old records */
    @Query("DELETE FROM per_app_logs WHERE timestampMs < :cutoffMs")
    suspend fun purgeOlderThan(cutoffMs: Long)
}

// ── Room Database ────────────────────────────────────────────────────
@Database(
    entities = [PacketLogEntity::class, StatsSnapshotEntity::class, PerAppLogEntity::class],
    version = 2,
    exportSchema = false
)
abstract class FirewallDatabase : RoomDatabase() {
    abstract fun packetLogDao(): PacketLogDao
    abstract fun statsSnapshotDao(): StatsSnapshotDao
    abstract fun perAppLogDao(): PerAppLogDao

    companion object {
        @Volatile private var INSTANCE: FirewallDatabase? = null

        fun getInstance(context: Context): FirewallDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    FirewallDatabase::class.java,
                    "aegis_firewall.db"
                )
                .fallbackToDestructiveMigration() // Reset DB on schema change during dev
                .build()
                .also { INSTANCE = it }
            }
    }
}
