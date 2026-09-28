package app.taskdav.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.taskdav.TaskDavApp
import java.util.concurrent.TimeUnit

/** Refreshes events from the device CalendarContract into TaskDav (no network required). */
class PhoneCalendarImportWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as TaskDavApp
        return try {
            if (!app.repository.phoneCalendarImportEnabled()) {
                return Result.success()
            }
            app.repository.importPhoneCalendarEvents()
            app.notifyWidgetsChanged()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_PERIODIC = "taskdav_phone_calendar_import"
        const val UNIQUE_ONCE = "taskdav_phone_calendar_import_once"

        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<PhoneCalendarImportWorker>(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun enqueueNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<PhoneCalendarImportWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_ONCE,
                ExistingWorkPolicy.REPLACE,
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
