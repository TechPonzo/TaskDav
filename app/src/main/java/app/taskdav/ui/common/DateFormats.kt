package app.taskdav.ui.common

import android.content.Context
import android.text.format.DateFormat as AndroidDateFormat
import app.taskdav.data.DateOrderPreference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

/**
 * Formats dates for display. Order comes from Appearance settings (default day/month/year);
 * time always follows the phone 12/24-hour preference.
 */
object DateFormats {
    private val orderOverride = AtomicReference(DateOrderPreference.DAY_MONTH_YEAR)

    fun setOrderPreference(pref: DateOrderPreference) {
        orderOverride.set(pref)
    }

    fun dateTime(context: Context, millis: Long): String {
        val date = Date(millis)
        return "${formatDate(context, date)} ${formatTime(context, date)}"
    }

    fun time(context: Context, millis: Long): String =
        formatTime(context, Date(millis))

    fun date(context: Context, millis: Long): String =
        formatDate(context, Date(millis))

    private fun formatTime(context: Context, date: Date): String =
        AndroidDateFormat.getTimeFormat(context).format(date)

    private fun formatDate(context: Context, date: Date): String {
        val pattern = when (orderOverride.get()) {
            DateOrderPreference.SYSTEM -> null
            DateOrderPreference.DAY_MONTH_YEAR -> "d/M/yy"
            DateOrderPreference.MONTH_DAY_YEAR -> "M/d/yy"
        }
        if (pattern != null) {
            return SimpleDateFormat(pattern, localeOf(context)).format(date)
        }
        return AndroidDateFormat.getDateFormat(context).format(date)
    }

    private fun localeOf(context: Context): Locale {
        val locales = context.resources.configuration.locales
        return if (!locales.isEmpty) locales[0] else Locale.getDefault()
    }
}
