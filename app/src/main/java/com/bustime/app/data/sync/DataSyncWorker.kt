package com.bustime.app.data.sync

import android.content.Context
import android.content.Intent
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
        val changed = repository.syncData()
        if (changed) {
            applicationContext.sendBroadcast(
                Intent(ACTION_DATA_SYNCED).setPackage(applicationContext.packageName)
            )
        }
        return if (repository.didLastSyncFail()) Result.retry() else Result.success()
    }

    companion object {
        const val ACTION_DATA_SYNCED = "com.bustime.app.ACTION_DATA_SYNCED"
    }
}
