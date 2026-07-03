package com.asd.firewall.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.asd.firewall.db.FirewallDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * DbCleanupWorker — Periodically purges old packet logs and stats from the Room database.
 * 
 * Moved out of the ViewModel so that maintenance happens reliably in the background, 
 * even if the user hasn't opened the UI recently.
 */
class DbCleanupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.i("DbCleanupWorker", "Starting database cleanup...")
            val db = FirewallDatabase.getInstance(applicationContext)
            
            // Cutoff is 7 days ago
            val cutoffMs = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)
            
            db.packetLogDao().purgeOlderThan(cutoffMs)
            db.statsSnapshotDao().purgeOlderThan(cutoffMs)
            db.perAppLogDao().purgeOlderThan(cutoffMs)
            
            Log.i("DbCleanupWorker", "Database cleanup completed successfully.")
            Result.success()
        } catch (e: Exception) {
            Log.e("DbCleanupWorker", "Failed to clean database", e)
            Result.retry()
        }
    }
}
