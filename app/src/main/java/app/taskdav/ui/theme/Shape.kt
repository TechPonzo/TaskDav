package app.taskdav.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val TaskDavShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

object TaskDavRadii {
    val hero = 28.dp
    val card = 20.dp
    val row = 16.dp
    val chip = 12.dp
    val input = 12.dp
    val nav = 28.dp
    val pill = 999.dp
}
