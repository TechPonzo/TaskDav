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
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle

class NotesWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val colors = WidgetTheme.colors(context)
        val model = WidgetDataLoader.loadNotes(context)
        val openNotes = actionStartActivity(WidgetIntents.openTab(context, "notes"))
        provideContent {
            GlanceTheme {
                NotesWidgetContent(context, colors, model, openNotes)
            }
        }
    }
}

@Composable
private fun NotesWidgetContent(
    context: Context,
    colors: WidgetColors,
    model: NotesWidgetModel,
    openNotes: androidx.glance.action.Action,
) {
    WidgetShell(
        colors = colors,
        title = "Notes",
        subtitle = if (model.notes.isEmpty()) null else "${model.notes.size} recent",
        headerAction = openNotes,
    ) {
        if (model.notes.isEmpty()) {
            WidgetEmptyLine(colors, "No notes yet.")
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(model.notes, itemId = { it.id }) { note ->
                    val openNote = actionStartActivity(WidgetIntents.openNote(context, note.id))
                    Column(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                            .clickable(openNote),
                    ) {
                        Text(
                            text = note.title,
                            style = TextStyle(
                                color = colors.onSurface,
                                fontSize = 13.sp,
                            ),
                            maxLines = 1,
                        )
                        if (note.subtitle != null) {
                            Text(
                                text = note.subtitle,
                                style = TextStyle(
                                    color = colors.onSurfaceVariant,
                                    fontSize = 11.sp,
                                ),
                                maxLines = 2,
                            )
                        }
                    }
                }
            }
        }
    }
}

class NotesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NotesWidget()
}
