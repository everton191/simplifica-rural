package br.com.simplificarural.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class RuralBackupWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val backup = runCatching { LocalBackupStore(applicationContext).create() }
            .getOrElse { return Result.retry() }

        val cloudProvider = CloudBackupRegistry(applicationContext).activeProvider()
        if (cloudProvider != null && cloudProvider.isConnected()) {
            when (cloudProvider.upload(backup.file)) {
                CloudUploadResult.Uploaded, CloudUploadResult.NotConnected -> Unit
                is CloudUploadResult.Failed -> return Result.retry()
            }
        }
        return Result.success()
    }
}

object BackupScheduler {
    private const val INITIAL_WORK_NAME = "simplifica-rural-initial-backup"
    private const val HOURLY_WORK_NAME = "simplifica-rural-hourly-backup"

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()

        val initialBackup = OneTimeWorkRequestBuilder<RuralBackupWorker>()
            .setConstraints(constraints)
            .build()
        val hourlyBackup = PeriodicWorkRequestBuilder<RuralBackupWorker>(1, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context.applicationContext).apply {
            enqueueUniqueWork(INITIAL_WORK_NAME, ExistingWorkPolicy.KEEP, initialBackup)
            enqueueUniquePeriodicWork(HOURLY_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, hourlyBackup)
        }
    }
}
