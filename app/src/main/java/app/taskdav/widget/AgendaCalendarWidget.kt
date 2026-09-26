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
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.glance.layout.Alignment
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

/**
 * Week/Month toggle is doable in Glance, but only via Glance Preferences state + a
 * compound button ([Switch]). Two [androidx.glance.Button]s that only differ by
 * PendingIntent extras are conflated by Android; SharedPreferences alone also stay
 * stale while a Glance session is alive because [provideGlance] may not re-run.
 *
 * Fix: keep range in [PreferencesGlanceStateDefinition], flip it with [Switch]
 * ([ToggleableStateKey]), and choose between preloaded week/month models via
 * [currentState] so the UI updates even when only composition re-runs.
 */
class AgendaCalendarWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val colors = WidgetTheme.colors(context)
        val weekModel = WidgetDataLoader.loadAgenda(context, AgendaRange.WEEK)
        val monthModel = WidgetDataLoader.loadAgenda(context, AgendaRange.MONTH)
        val openCalendar = actionStartActivity(WidgetIntents.openTab(context, "calendar"))
        provideContent {
            val range = rangeFromPrefs(currentState<Preferences>())
            val model = if (range == AgendaRange.MONTH) monthModel else weekModel
            GlanceTheme {
                AgendaCalendarContent(
                    colors = colors,
                    model = model,
                    openCalendar = openCalendar,
                )
            }
        }
    }

    companion object {
        val RangePrefKey = stringPreferencesKey("agenda_range")

        fun rangeFromPrefs(prefs: Preferences): AgendaRange =
            if (prefs[RangePrefKey] == "month") AgendaRange.MONTH else AgendaRange.WEEK
    }
}

@Composable
private fun AgendaCalendarContent(
    colors: WidgetColors,
    model: AgendaWidgetModel,
    openCalendar: androidx.glance.action.Action,
) {
    WidgetShell(
        colors = colors,
        title = model.headerTitle,
        subtitle = model.subtitle,
        headerAction = openCalendar,
        headerTrailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "W",
                    style = TextStyle(
                        color = if (model.range == AgendaRange.WEEK) {
                            colors.primary
                        } else {
                            colors.onSurfaceVariant
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Spacer(modifier = GlanceModifier.width(4.dp))
                Switch(
                    checked = model.range == AgendaRange.MONTH,
                    onCheckedChange = actionRunCallback<ToggleAgendaRangeAction>(),
                    colors = SwitchDefaults.switchColors(
                        checkedThumbColor = colors.onPrimary,
                        checkedTrackColor = colors.primary,
                        uncheckedThumbColor = colors.onSurfaceVariant,
                        uncheckedTrackColor = colors.primaryContainer,
                    ),
                )
                Spacer(modifier = GlanceModifier.width(4.dp))
                Text(
                    text = "M",
                    style = TextStyle(
                        color = if (model.range == AgendaRange.MONTH) {
                            colors.primary
                        } else {
                            colors.onSurfaceVariant
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
        },
    ) {
        if (model.events.isEmpty()) {
            WidgetEmptyLine(
                colors,
                if (model.range == AgendaRange.WEEK) {
                    "No events this week."
                } else {
                    "No events this month."
                },
            )
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(model.events, itemId = { it.id }) { event ->
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable(openCalendar),
                    ) {
                        Text(
                            text = event.timeLabel,
                            style = TextStyle(
                                color = colors.onSurfaceVariant,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            maxLines = 1,
                            modifier = GlanceModifier.width(88.dp),
                        )
                        Spacer(modifier = GlanceModifier.width(6.dp))
                        Text(
                            text = event.title,
                            style = TextStyle(
                                color = colors.onSurface,
                                fontSize = 13.sp,
                            ),
                            maxLines = 2,
                            modifier = GlanceModifier.defaultWeight(),
                        )
                    }
                }
            }
        }
    }
}

class ToggleAgendaRangeAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val toMonth = parameters[ToggleableStateKey] == true
        val value = if (toMonth) "month" else "week"
        Log.i("AgendaCalendarWidget", "Toggle range → $value")
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[AgendaCalendarWidget.RangePrefKey] = value
        }
        AgendaCalendarWidget().update(context, glanceId)
    }
}

class AgendaCalendarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AgendaCalendarWidget()
}
