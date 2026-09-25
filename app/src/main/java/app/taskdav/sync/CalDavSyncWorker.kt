package app.taskdav.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.taskdav.TaskDavApp
import java.util.concurrent.TimeUnit

class CalDavSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as TaskDavApp
        return try {
            if (!app.repository.isCalDavMode() || !app.repository.accountConfigured()) {
                return Result.success()
            }
            app.repository.syncNow()
            Result.success()
        } catch (e: Exception) {
            app.accountStore.setLastSync(
                System.currentTimeMillis(),
                "Sync failed: ${e.message ?: e.javaClass.simpleName}",
            )
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_PERIODIC = "taskdav_periodic_sync"
        const val UNIQUE_ONCE = "taskdav_once_sync"

        fun enqueuePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<CalDavSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun enqueueNow(context: Context) {
            val app = context.applicationContext
            if (app is TaskDavApp && !app.repository.isCalDavMode()) return
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<CalDavSyncWorker>()
                .setConstraints(constraints)
                .build()
            // APPEND_OR_REPLACE: don't cancel an in-flight sync (REPLACE could drop a push)
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_ONCE,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request,
            )
        }

        fun cancelAll(context: Context) {
            val wm = WorkManager.getInstance(context)
            wm.cancelUniqueWork(UNIQUE_ONCE)
            wm.cancelUniqueWork(UNIQUE_PERIODIC)
        }
    }
}
