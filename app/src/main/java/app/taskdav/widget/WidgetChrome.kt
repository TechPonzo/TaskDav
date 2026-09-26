package app.taskdav.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle

@Composable
fun WidgetShell(
    colors: WidgetColors,
    title: String,
    subtitle: String? = null,
    headerAction: Action? = null,
    headerTrailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(20.dp)
            .background(colors.surface)
            .padding(14.dp),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(
                modifier = GlanceModifier
                    .width(3.dp)
                    .height(18.dp)
                    .cornerRadius(2.dp)
                    .background(colors.primary),
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            val titleModifier = GlanceModifier.defaultWeight().then(
                if (headerAction != null) GlanceModifier.clickable(headerAction) else GlanceModifier,
            )
            Column(modifier = titleModifier) {
                Text(
                    text = title,
                    style = TextStyle(
                        color = colors.primary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = TextStyle(
                            color = colors.onSurfaceVariant,
                            fontSize = 11.sp,
                        ),
                        maxLines = 1,
                    )
                }
            }
            headerTrailing?.invoke()
        }
        Spacer(modifier = GlanceModifier.height(10.dp))
        Column(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            content()
        }
    }
}

@Composable
fun WidgetEmptyLine(colors: WidgetColors, text: String) {
    Text(
        text = text,
        style = TextStyle(
            color = colors.onSurfaceVariant,
            fontSize = 13.sp,
        ),
        maxLines = 2,
    )
}

@Composable
fun WidgetRangeChip(
    label: String,
    selected: Boolean,
    colors: WidgetColors,
    onClick: Action,
) {
    val bg = if (selected) colors.primary else colors.primaryContainer
    val fg = if (selected) colors.onPrimary else colors.onPrimaryContainer
    Text(
        text = label,
        style = TextStyle(
            color = fg,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        ),
        modifier = GlanceModifier
            .cornerRadius(10.dp)
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable(onClick),
    )
}
