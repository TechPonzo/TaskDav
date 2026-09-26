package app.taskdav.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.CheckboxDefaults
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.taskdav.TaskDavApp
import app.taskdav.sync.CalDavSyncWorker

private val TaskIdKey = ActionParameters.Key<Long>("task_id")

class TasksWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val colors = WidgetTheme.colors(context)
        val model = WidgetDataLoader.loadTasks(context)
        val openTasks = actionStartActivity(WidgetIntents.openTab(context, "tasks"))
        provideContent {
            GlanceTheme {
                TasksWidgetContent(context, colors, model, openTasks)
            }
        }
    }
}

@Composable
private fun TasksWidgetContent(
    context: Context,
    colors: WidgetColors,
    model: TasksWidgetModel,
    openTasks: androidx.glance.action.Action,
) {
    WidgetShell(
        colors = colors,
        title = "Tasks",
        subtitle = "${model.openCount} open",
        headerAction = openTasks,
    ) {
        if (model.tasks.isEmpty()) {
            WidgetEmptyLine(colors, "No open tasks.")
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(model.tasks, itemId = { it.id }) { task ->
                    val openTask = actionStartActivity(WidgetIntents.openTask(context, task.id))
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CheckBox(
                            checked = false,
                            onCheckedChange = actionRunCallback<CompleteTaskAction>(
                                actionParametersOf(TaskIdKey to task.id),
                            ),
                            colors = CheckboxDefaults.colors(
                                checkedColor = colors.primary,
                                uncheckedColor = colors.onSurfaceVariant,
                            ),
                        )
                        Spacer(modifier = GlanceModifier.width(4.dp))
                        Column(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .clickable(openTask),
                        ) {
                            Text(
                                text = task.title,
                                style = TextStyle(
                                    color = if (task.overdue) {
                                        colors.primary
                                    } else {
                                        colors.onSurface
                                    },
                                    fontSize = 13.sp,
                                    fontWeight = if (task.overdue) {
                                        FontWeight.Bold
                                    } else {
                                        FontWeight.Normal
                                    },
                                ),
                                maxLines = 2,
                            )
                            if (task.dueLabel != null) {
                                Text(
                                    text = if (task.overdue) {
                                        "Overdue · ${task.dueLabel}"
                                    } else {
                                        task.dueLabel
                                    },
                                    style = TextStyle(
                                        color = colors.onSurfaceVariant,
                                        fontSize = 11.sp,
                                    ),
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

class CompleteTaskAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val taskId = parameters[TaskIdKey] ?: return
        val app = context.applicationContext as? TaskDavApp ?: return
        app.repository.toggleComplete(taskId)
        CalDavSyncWorker.enqueueNow(context)
        WidgetUpdater.updateAllNow(context)
    }
}

class TasksWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TasksWidget()
}
