package app.taskdav.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle

class CalendarWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val colors = WidgetTheme.colors(context)
        val model = WidgetDataLoader.loadCalendar(context)
        val openCalendar = actionStartActivity(WidgetIntents.openTab(context, "calendar"))
        provideContent {
            GlanceTheme {
                CalendarWidgetContent(colors, model, openCalendar)
            }
        }
    }
}

@Composable
private fun CalendarWidgetContent(
    colors: WidgetColors,
    model: CalendarWidgetModel,
    openCalendar: androidx.glance.action.Action,
) {
    WidgetShell(
        colors = colors,
        title = model.headerDate,
        subtitle = "Today",
        headerAction = openCalendar,
    ) {
        if (model.events.isEmpty()) {
            WidgetEmptyLine(colors, "Clear day.")
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
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            maxLines = 1,
                            modifier = GlanceModifier.width(56.dp),
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

class CalendarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CalendarWidget()
}
