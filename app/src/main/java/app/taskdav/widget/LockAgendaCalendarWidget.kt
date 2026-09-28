package app.taskdav.widget

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.Switch
import androidx.glance.appwidget.SwitchDefaults
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.ToggleableStateKey
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.taskdav.data.LocaleHelper

/**
 * Compact week/month agenda for lock-screen hosts (Samsung LockStar / OEMs that
 * still honor [android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD]).
 * Stock Pixel lock screens generally do not host third-party widgets.
 */
class LockAgendaCalendarWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val localized = LocaleHelper.wrap(context)
        val colors = WidgetTheme.colors(context)
        val weekModel = WidgetDataLoader.loadAgenda(context, AgendaRange.WEEK)
        val monthModel = WidgetDataLoader.loadAgenda(context, AgendaRange.MONTH)
        val openCalendar = actionStartActivity(WidgetIntents.openTab(context, "calendar"))
        provideContent {
            val range = rangeFromPrefs(currentState<Preferences>())
            val model = if (range == AgendaRange.MONTH) monthModel else weekModel
            GlanceTheme {
                LockAgendaContent(
                    context = localized,
                    colors = colors,
                    model = model,
                    openCalendar = openCalendar,
                )
            }
        }
    }

    companion object {
        val RangePrefKey = stringPreferencesKey("lock_agenda_range")
        const val MaxEvents = 4

        fun rangeFromPrefs(prefs: Preferences): AgendaRange =
            if (prefs[RangePrefKey] == "month") AgendaRange.MONTH else AgendaRange.WEEK
    }
}

@Composable
private fun LockAgendaContent(
    context: Context,
    colors: WidgetColors,
    model: AgendaWidgetModel,
    openCalendar: androidx.glance.action.Action,
) {
    val events = model.events.take(LockAgendaCalendarWidget.MaxEvents)
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(16.dp)
            .background(colors.surface)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .clickable(openCalendar),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = model.headerTitle,
                    style = TextStyle(
                        color = colors.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
                Text(
                    text = model.subtitle,
                    style = TextStyle(
                        color = colors.onSurfaceVariant,
                        fontSize = 10.sp,
                    ),
                    maxLines = 1,
                )
            }
            Text(
                text = "W",
                style = TextStyle(
                    color = if (model.range == AgendaRange.WEEK) colors.primary else colors.onSurfaceVariant,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            Spacer(modifier = GlanceModifier.width(2.dp))
            Switch(
                checked = model.range == AgendaRange.MONTH,
                onCheckedChange = actionRunCallback<ToggleLockAgendaRangeAction>(),
                colors = SwitchDefaults.switchColors(
                    checkedThumbColor = colors.onPrimary,
                    checkedTrackColor = colors.primary,
                    uncheckedThumbColor = colors.onSurfaceVariant,
                    uncheckedTrackColor = colors.primaryContainer,
                ),
            )
            Spacer(modifier = GlanceModifier.width(2.dp))
            Text(
                text = "M",
                style = TextStyle(
                    color = if (model.range == AgendaRange.MONTH) colors.primary else colors.onSurfaceVariant,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
        Spacer(modifier = GlanceModifier.height(6.dp))
        if (events.isEmpty()) {
            Text(
                text = if (model.range == AgendaRange.WEEK) {
                    context.getString(app.taskdav.R.string.widget_empty_events_week)
                } else {
                    context.getString(app.taskdav.R.string.widget_empty_events_month)
                },
                style = TextStyle(color = colors.onSurfaceVariant, fontSize = 11.sp),
                maxLines = 2,
            )
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(events, itemId = { it.id }) { event ->
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                    ) {
                        Text(
                            text = event.timeLabel,
                            style = TextStyle(
                                color = colors.onSurfaceVariant,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            maxLines = 1,
                            modifier = GlanceModifier.width(72.dp),
                        )
                        Spacer(modifier = GlanceModifier.width(4.dp))
                        Text(
                            text = event.title,
                            style = TextStyle(color = colors.onSurface, fontSize = 12.sp),
                            maxLines = 1,
                            modifier = GlanceModifier.defaultWeight(),
                        )
                    }
                }
            }
        }
    }
}

class ToggleLockAgendaRangeAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val toMonth = parameters[ToggleableStateKey] == true
        val value = if (toMonth) "month" else "week"
        Log.i("LockAgendaWidget", "Toggle range → $value")
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[LockAgendaCalendarWidget.RangePrefKey] = value
        }
        LockAgendaCalendarWidget().update(context, glanceId)
    }
}

class LockAgendaCalendarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LockAgendaCalendarWidget()
}
