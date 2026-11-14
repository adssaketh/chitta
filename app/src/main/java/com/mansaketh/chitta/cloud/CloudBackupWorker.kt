package com.mansaketh.chitta.cloud

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CloudBackupEntryPoint {
    fun cloudBackupService(): CloudBackupService
    fun cloudPrefs(): CloudPrefs
}

private const val WORK_NAME = "chitta-cloud-backup"

/**
 * The daily Drive backup. It never shows a screen: when Google needs the person's yes again it stops and leaves a note in Settings
 * (DECISIONS D176). A missing connection is retried by WorkManager; everything else waits for the next day or the next app open.
 */
class CloudBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ep = EntryPointAccessors.fromApplication(applicationContext, CloudBackupEntryPoint::class.java)
        return when (val r = ep.cloudBackupService().backupIfDue()) {
            null, is CloudResult.Ok -> Result.success()
            is CloudResult.NeedsConsent -> {
                ep.cloudPrefs().setError("Google needs your OK again. Open Settings, Google Drive backup, and tap Back up now.")
                Result.success()
            }
            is CloudResult.Failed -> if (r.retryable && runAttemptCount < 3) Result.retry() else Result.success()
        }
    }
}

object CloudBackupScheduler {
    /** Keeps one daily job queued while the person is signed in and automatic backup is on; removes it otherwise. */
    fun sync(context: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) { wm.cancelUniqueWork(WORK_NAME); return }
        val request = PeriodicWorkRequestBuilder<CloudBackupWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build()).build()
        wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
