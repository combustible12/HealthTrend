package com.combustible12.healthtrend

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TrendCategoryPicker(categories: List<String>, selected: String, select: (String) -> Unit, reorder: (List<String>) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val displayed = if (expanded || selected !in categories) categories else listOf(selected) + categories.filterNot { it == selected }
    val bounds = remember(displayed) { mutableMapOf<String, Rect>() }
    var target by remember { mutableStateOf<String?>(null) }
    val drag = if (!expanded) Modifier else Modifier.pointerInput(categories) {
        var source: String? = null
        var position = Offset.Zero
        fun categoryAt(point: Offset) = bounds.entries.firstOrNull { it.value.contains(point) }?.key
        detectDragGesturesAfterLongPress(
            onDragStart = { point ->
                position = point
                source = categoryAt(point)
                target = source
            },
            onDragCancel = { target = null },
            onDragEnd = {
                val from = categories.indexOf(source)
                val to = categories.indexOf(target)
                if (from >= 0 && to >= 0 && from != to) {
                    val ordered = categories.toMutableList()
                    ordered.add(to, ordered.removeAt(from))
                    reorder(ordered)
                }
                target = null
            }
        ) { change, delta ->
            change.consume()
            position += delta
            target = categoryAt(position)
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        FlowRow(
            Modifier.weight(1f).then(drag),
            maxLines = if (expanded) Int.MAX_VALUE else 1,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.5.dp)
        ) {
            displayed.forEach { label ->
                key(label) {
                    FilterChip(border=null,
                        selected = selected == label,
                        onClick = { select(label); expanded = false },
                        label = { Text(label) },
                        modifier = Modifier.onGloballyPositioned { bounds[label] = it.boundsInParent() },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = if (target == label) SelectedTint else Color.White,
                            selectedContainerColor = SelectedTint
                        )
                    )
                }
            }
        }
        IconButton({ expanded = !expanded }, Modifier.size(32.dp)) {
            Icon(if (expanded) SolidArrows.Up else SolidArrows.Down, if (expanded) "收起趋势分类" else "展开趋势分类", tint = SolidArrows.Tint)
        }
    }
}
