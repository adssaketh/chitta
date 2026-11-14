package com.mansaketh.chitta.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Constraints
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
interface FundPriceEntryPoint {
    fun fundPriceService(): FundPriceService
    fun uiPrefs(): UiPrefs
}

private const val WORK_NAME = "chitta-fund-prices"

/** Once a day, when the phone is online, fetch the price file. A failed day is simply tried again tomorrow (and on app open). */
class FundPriceWorker(private val context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ep = EntryPointAccessors.fromApplication(context, FundPriceEntryPoint::class.java)
        if (!ep.uiPrefs().fundPricesAuto.value) return Result.success()
        ep.fundPriceService().refresh()
        return Result.success()
    }
}

object FundPriceScheduler {
    /** Keeps one daily job queued while the switch is on, and removes it when it is off. */
    fun sync(context: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) { wm.cancelUniqueWork(WORK_NAME); return }
        val request = PeriodicWorkRequestBuilder<FundPriceWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
