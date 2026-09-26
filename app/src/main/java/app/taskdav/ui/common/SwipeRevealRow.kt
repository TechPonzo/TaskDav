package app.taskdav.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val ActionSlotWidth = 64.dp

/**
 * Swipe the foreground content left to reveal [actions] on the trailing edge.
 * Prefer [SwipeRevealAction] for edge-to-edge full-height action cells.
 */
@Composable
fun SwipeRevealRow(
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    cornerRadius: Dp = 16.dp,
    actions: @Composable RowScope.(close: () -> Unit) -> Unit,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    var actionsWidth by remember { mutableFloatStateOf(0f) }
    val maxReveal = -actionsWidth

    fun close() {
        scope.launch { offsetX.animateTo(0f, tween(180)) }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius)),
    ) {
        // Match content height first, then pin actions to the trailing edge.
        Box(modifier = Modifier.matchParentSize()) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .onSizeChanged { actionsWidth = it.width.toFloat() },
                verticalAlignment = Alignment.CenterVertically,
                content = { actions(::close) },
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .background(contentColor)
                .pointerInput(actionsWidth) {
                    if (actionsWidth <= 0f) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                val target = if (offsetX.value < maxReveal * 0.45f) {
                                    maxReveal
                                } else {
                                    0f
                                }
                                offsetX.animateTo(target, tween(180))
                            }
                        },
                        onDragCancel = {
                            scope.launch { offsetX.animateTo(0f, tween(180)) }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                offsetX.snapTo(
                                    (offsetX.value + dragAmount).coerceIn(maxReveal, 0f),
                                )
                            }
                        },
                    )
                },
        ) {
            content()
        }
    }
}

/** Full-height action cell for [SwipeRevealRow] (no inner padding gaps). */
@Composable
fun SwipeRevealAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(ActionSlotWidth)
            .background(containerColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = contentColor,
            modifier = Modifier.size(22.dp),
        )
    }
}
