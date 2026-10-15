package com.bustime.app.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bustime.app.data.repository.AppRepository

/** Network-constrained background check; WorkManager's minimum periodic interval is 15 minutes. */
class DataSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val repository = AppRepository.getInstance(applicationContext)
        repository.syncData()
        return if (repository.didLastSyncFail()) Result.retry() else Result.success()
    }
}
