package com.combustible12.healthtrend

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal object SolidArrows {
    val Up = triangle("Up", 12f, 7f, 20f, 17f, 4f, 17f)
    val Down = triangle("Down", 12f, 17f, 4f, 7f, 20f, 7f)
    val Left = triangle("Left", 7f, 12f, 17f, 4f, 17f, 20f)
    val Right = triangle("Right", 17f, 12f, 7f, 20f, 7f, 4f)

    private fun triangle(name: String, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(x1, y1)
                lineTo(x2, y2)
                lineTo(x3, y3)
                close()
            }
        }.build()
}
