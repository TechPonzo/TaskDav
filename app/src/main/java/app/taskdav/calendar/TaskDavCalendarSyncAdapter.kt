package app.taskdav.calendar

import android.accounts.Account
import android.content.AbstractThreadedSyncAdapter
import android.content.ContentProviderClient
import android.content.Context
import android.content.SyncResult
import android.os.Bundle
import android.util.Log

/**
 * Exists so Calendar apps list TaskDav as a syncable account (like DAVx⁵).
 * Event publishing is done from the app UI via [SystemCalendarMirror] — this
 * adapter must not block or throw; SyncAdapter threads are often interrupted.
 */
class TaskDavCalendarSyncAdapter(
    context: Context,
    autoInitialize: Boolean,
) : AbstractThreadedSyncAdapter(context, autoInitialize) {

    override fun onPerformSync(
        account: Account?,
        extras: Bundle?,
        authority: String?,
        provider: ContentProviderClient?,
        syncResult: SyncResult?,
    ) {
        // No-op on purpose. Heavy work here (runBlocking) crashed the app when
        // Android cancelled the sync right after calendar permission was granted.
        Log.d(TAG, "sync tick for ${account?.name} — publish runs from TaskDav UI")
    }

    companion object {
        private const val TAG = "TaskDavCalSync"
    }
}
