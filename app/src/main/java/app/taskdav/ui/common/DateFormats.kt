package app.taskdav.ui.common

import android.content.Context
import android.text.format.DateFormat as AndroidDateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.taskdav.data.DateOrderPreference
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicReference

/** Appearance date order; provided by [app.taskdav.ui.theme.TaskDavTheme]. */
val LocalDateOrder = compositionLocalOf { DateOrderPreference.DAY_MONTH_YEAR }

/**
 * Formats dates for display. Order comes from Appearance settings (default day/month/year);
 * time always follows the phone 12/24-hour preference.
 */
object DateFormats {
    private val orderOverride = AtomicReference(DateOrderPreference.DAY_MONTH_YEAR)

    fun setOrderPreference(pref: DateOrderPreference) {
        orderOverride.set(pref)
    }

    fun orderPreference(): DateOrderPreference = orderOverride.get()

    fun patternFor(pref: DateOrderPreference, context: Context): String =
        when (pref) {
            DateOrderPreference.DAY_MONTH_YEAR -> "dd/MM/yyyy"
            DateOrderPreference.MONTH_DAY_YEAR -> "MM/dd/yyyy"
            DateOrderPreference.SYSTEM -> {
                val locale = localeOf(context)
                // Prefer a numeric skeleton so typed input stays unambiguous.
                val best = AndroidDateFormat.getBestDateTimePattern(locale, "ddMMyyyy")
                when {
                    best.indexOf('d') < best.indexOf('M') -> "dd/MM/yyyy"
                    else -> "MM/dd/yyyy"
                }
            }
        }

    fun patternHint(pref: DateOrderPreference, context: Context): String =
        patternFor(pref, context).uppercase(Locale.ROOT)

    /** Locale whose short-date order matches the appearance preference. */
    fun localeForDateInput(base: Locale, pref: DateOrderPreference = orderOverride.get()): Locale {
        val language = base.language.ifBlank { "en" }
        return when (pref) {
            DateOrderPreference.SYSTEM -> base
            DateOrderPreference.DAY_MONTH_YEAR ->
                Locale.Builder().setLanguage(language).setRegion("GB").build()
            DateOrderPreference.MONTH_DAY_YEAR ->
                Locale.Builder().setLanguage("en").setRegion("US").build()
        }
    }

    fun dateTime(context: Context, millis: Long, pref: DateOrderPreference = orderOverride.get()): String {
        val date = Date(millis)
        return "${formatDate(context, date, pref)} ${formatTime(context, date)}"
    }

    fun time(context: Context, millis: Long): String =
        formatTime(context, Date(millis))

    fun date(context: Context, millis: Long, pref: DateOrderPreference = orderOverride.get()): String =
        formatDate(context, Date(millis), pref)

    /**
     * Parse a user-typed date in the appearance format into UTC midnight millis
     * (Material DatePicker selection representation).
     */
    fun parseToUtcMidnight(
        raw: String,
        pref: DateOrderPreference,
        context: Context,
    ): Long? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val pattern = patternFor(pref, context)
        val parser = SimpleDateFormat(pattern, localeOf(context)).apply {
            isLenient = false
            timeZone = TimeZone.getDefault()
        }
        val parsed = try {
            parser.parse(trimmed) ?: return null
        } catch (_: ParseException) {
            return null
        }
        val local = Calendar.getInstance().apply { time = parsed }
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        utc.clear()
        utc.set(
            local.get(Calendar.YEAR),
            local.get(Calendar.MONTH),
            local.get(Calendar.DAY_OF_MONTH),
        )
        return utc.timeInMillis
    }

    fun formatUtcMidnight(
        utcMidnightMillis: Long,
        pref: DateOrderPreference,
        context: Context,
    ): String {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = utcMidnightMillis
        }
        val local = Calendar.getInstance()
        local.clear()
        local.set(
            utc.get(Calendar.YEAR),
            utc.get(Calendar.MONTH),
            utc.get(Calendar.DAY_OF_MONTH),
            12,
            0,
            0,
        )
        return date(context, local.timeInMillis, pref)
    }

    private fun formatTime(context: Context, date: Date): String =
        AndroidDateFormat.getTimeFormat(context).format(date)

    private fun formatDate(context: Context, date: Date, pref: DateOrderPreference): String {
        val pattern = patternFor(pref, context)
        return SimpleDateFormat(pattern, localeOf(context)).format(date)
    }

    private fun localeOf(context: Context): Locale {
        val locales = context.resources.configuration.locales
        return if (!locales.isEmpty) locales[0] else Locale.getDefault()
    }
}

@Composable
fun rememberFormattedDate(millis: Long): String {
    val context = LocalContext.current
    val order = LocalDateOrder.current
    return remember(millis, order, context) {
        DateFormats.date(context, millis, order)
    }
}
