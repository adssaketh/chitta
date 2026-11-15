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
interface PersonalSyncEntryPoint {
    fun personalSync(): PersonalSync
}

private const val SYNC_WORK_NAME = "chitta-personal-sync"

/** The hourly round while the app is closed. It does nothing when nobody is signed in. A failed round is left for the next hour or the next app open. */
class PersonalSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val sync = EntryPointAccessors.fromApplication(applicationContext, PersonalSyncEntryPoint::class.java).personalSync()
        return when (val r = sync.run("hourly")) {
            is SyncResult.Failed -> if (r.retryable && runAttemptCount < 2) Result.retry() else Result.success()
            else -> Result.success()
        }
    }
}

object PersonalSyncScheduler {
    /** Keeps one hourly job queued. Cheap: a round with nothing to do reads the cloud once. */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<PersonalSyncWorker>(1, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(SYNC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
