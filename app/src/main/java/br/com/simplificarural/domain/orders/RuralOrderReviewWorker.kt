package br.com.simplificarural.domain.orders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class RuralOrderReviewWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching { RuralOrderService(applicationContext).reviewDueToday() }.fold({ Result.success() }, { Result.retry() })
}
object RuralOrderReviewScheduler { fun schedule(context: Context) { WorkManager.getInstance(context).enqueueUniquePeriodicWork("rural-order-review", ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<RuralOrderReviewWorker>(1, TimeUnit.DAYS).build()) } }
