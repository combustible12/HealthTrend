package com.combustible12.healthtrend

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

internal object SolidArrows {
    val Tint get() = TrendEventShapeColor
    val DisabledTint get() = Tint.copy(alpha = Tint.alpha * .55f)

    private const val InlineId = "direction-right"
    fun inlineLabel(before: String, after: String = "") = buildAnnotatedString {
        append(before)
        appendInlineContent(InlineId, "▸")
        append(after)
    }

    val inlineContent = mapOf(
        InlineId to InlineTextContent(Placeholder(.8.em, 1.em, PlaceholderVerticalAlign.TextCenter)) {
            Icon(Right, null, Modifier.fillMaxSize(), tint = Tint)
        }
    )

    val Up = triangle("Up", 0f)
    val Right = triangle("Right", 90f)
    val Down = triangle("Down", 180f)
    val Left = triangle("Left", 270f)

    private fun triangle(name: String, rotation: Float) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            group(rotate = rotation, pivotX = 12f, pivotY = 12f) {
                // Keep the original icon slot. The visible outline is 10⅔ by 6⅔.
                path(fill = SolidColor(Color.Black)) {
                    moveTo(12f, 8.6667f)
                    curveTo(12.28f, 8.6667f, 12.51f, 8.80f, 12.72f, 9.06f)
                    lineTo(17.13f, 14.23f)
                    curveTo(17.27f, 14.40f, 17.3333f, 14.54f, 17.3333f, 14.70f)
                    curveTo(17.3333f, 15.08f, 17.04f, 15.3333f, 16.63f, 15.3333f)
                    lineTo(7.37f, 15.3333f)
                    curveTo(6.96f, 15.3333f, 6.6667f, 15.08f, 6.6667f, 14.70f)
                    curveTo(6.6667f, 14.54f, 6.73f, 14.40f, 6.87f, 14.23f)
                    lineTo(11.28f, 9.06f)
                    curveTo(11.49f, 8.80f, 11.72f, 8.6667f, 12f, 8.6667f)
                    close()
                }
            }
        }.build()
}
