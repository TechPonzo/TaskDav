package app.taskdav.calendar

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentResolver
import android.content.Context
import android.provider.CalendarContract
import android.util.Log

/**
 * Registers a device account so Calendar apps list TaskDav next to DAVx⁵ / Google,
 * including for local-only users (no CalDAV required).
 */
object TaskDavCalendarAccount {
    const val ACCOUNT_NAME = "TaskDav"
    const val ACCOUNT_TYPE = "app.taskdav"

    private const val TAG = "TaskDavCalAccount"

    data class Status(
        val accountPresent: Boolean,
        val calendarId: Long?,
        val message: String,
    )

    fun ensure(context: Context): Account {
        val am = AccountManager.get(context)
        val existing = am.getAccountsByType(ACCOUNT_TYPE)
        val account = if (existing.isNotEmpty()) {
            existing[0]
        } else {
            val acc = Account(ACCOUNT_NAME, ACCOUNT_TYPE)
            val added = am.addAccountExplicitly(acc, null, null)
            if (!added) {
                Log.e(TAG, "addAccountExplicitly failed for $ACCOUNT_TYPE")
                // Still return the account object; calendar insert may still work if
                // the system registered the type after a race.
            } else {
                Log.i(TAG, "Created account $ACCOUNT_NAME / $ACCOUNT_TYPE")
            }
            acc
        }
        // Mark calendar authority syncable so Samsung / Google Calendar list the account.
        // Do not requestSync here — SyncAdapter cancellation after permission grant
        // used to crash the process.
        runCatching {
            ContentResolver.setIsSyncable(account, CalendarContract.AUTHORITY, 1)
            ContentResolver.setSyncAutomatically(account, CalendarContract.AUTHORITY, true)
        }.onFailure { Log.w(TAG, "setSyncAutomatic failed", it) }
        return account
    }

    fun isPresent(context: Context): Boolean =
        runCatching {
            AccountManager.get(context).getAccountsByType(ACCOUNT_TYPE).isNotEmpty()
        }.getOrDefault(false)

    fun status(context: Context): Status {
        return runCatching {
            val accountPresent = isPresent(context)
            val calendarId = findCalendarId(context)
            val message = when {
                accountPresent && calendarId != null ->
                    "TaskDav account is active — enable it in the Calendar app's calendar list."
                accountPresent && calendarId == null ->
                    "TaskDav account exists but no calendar yet. Toggle publish off/on."
                else ->
                    "TaskDav account not created yet. Turn on Show TaskDav in Android Calendar."
            }
            Status(accountPresent, calendarId, message)
        }.getOrElse { e ->
            Log.w(TAG, "status check failed", e)
            Status(
                accountPresent = false,
                calendarId = null,
                message = "Could not check calendar status (permission may be needed).",
            )
        }
    }

    fun findCalendarId(context: Context): Long? {
        return runCatching {
            val projection = arrayOf(CalendarContract.Calendars._ID)
            val selection =
                "${CalendarContract.Calendars.ACCOUNT_NAME}=? AND ${CalendarContract.Calendars.ACCOUNT_TYPE}=?"
            val args = arrayOf(ACCOUNT_NAME, ACCOUNT_TYPE)
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                selection,
                args,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else null
            }
        }.onFailure { Log.w(TAG, "findCalendarId failed", it) }.getOrNull()
    }

    fun remove(context: Context) {
        val am = AccountManager.get(context)
        for (account in am.getAccountsByType(ACCOUNT_TYPE)) {
            runCatching {
                am.removeAccountExplicitly(account)
            }.onFailure { Log.w(TAG, "removeAccount failed", it) }
        }
    }
}
