package com.combustible12.healthtrend

import android.app.DatePickerDialog
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal data class PortHistory(
 val placement: LocalDate? = null,
 val flushes: List<LocalDate> = emptyList(),
 val legacy: LocalDate? = null
) {
 val latest get() = listOfNotNull(placement, flushes.maxOrNull(), legacy).maxOrNull()

 fun update(date: LocalDate, isPlacement: Boolean, replacing: LocalDate? = null): PortHistory {
  require(date <= LocalDate.now()) { "请选择今天或更早的日期" }
  val placementDate = if (isPlacement) date else placement
  val remaining = if (isPlacement || replacing == null) flushes else flushes - replacing
  require(isPlacement || replacing == null || date == replacing || date !in remaining) { "这一天已有冲管记录" }
  val dates = if (isPlacement) flushes else (remaining + date).distinct().sorted()
  require(placementDate == null || dates.none { it < placementDate }) { "冲管日期不能早于安港日期" }
  return PortHistory(placementDate, dates, legacy?.takeUnless { it == date || it == replacing })
 }

 fun interval(index: Int): Long? {
  val previous = flushes.getOrNull(index - 1) ?: placement ?: return null
  return ChronoUnit.DAYS.between(previous, flushes[index])
 }
}

private fun pickPortDate(context: Context, initial: LocalDate?, select: (LocalDate) -> Unit) {
 val day = minOf(initial ?: LocalDate.now(), LocalDate.now())
 DatePickerDialog(context, { _, year, month, date -> select(LocalDate.of(year, month + 1, date)) },
  day.year, day.monthValue - 1, day.dayOfMonth).apply {
  datePicker.maxDate = System.currentTimeMillis()
 }.show()
}

@Composable internal fun PortMaintenanceSection(store: HealthStore) {
 val context = LocalContext.current
 var history by remember(store) { mutableStateOf(store.portHistory()) }
 var showHistory by rememberSaveable { mutableStateOf(false) }
 fun save(date: LocalDate, placement: Boolean, replacing: LocalDate? = null) {
  try {
   history = store.recordPortDate(date, placement, replacing)
  } catch (error: IllegalArgumentException) {
   Toast.makeText(context, error.message, Toast.LENGTH_LONG).show()
  } catch (error: IllegalStateException) {
   Toast.makeText(context, error.message, Toast.LENGTH_LONG).show()
  }
 }
 fun pick(initial: LocalDate?, placement: Boolean, replacing: LocalDate? = null) {
  pickPortDate(context, initial) { save(it, placement, replacing) }
 }
 Row(Modifier.fillMaxWidth().offset(x=(-6).dp).padding(end=8.dp), verticalAlignment=Alignment.CenterVertically) {
  Box(Modifier.width(22.dp).height(courseFilterHeight).clickable { showHistory = true }, contentAlignment=Alignment.Center) {
   Icon(Icons.Outlined.History, "港维护记录", Modifier.size(18.dp), tint=Accent)
  }
  PortMaintenanceBar(history.latest?.toString().orEmpty(), Modifier.weight(1f),
   onPick = {
    val latest = history.latest
    pick(latest, latest != null && latest == history.placement && latest !in history.flushes, latest)
   },
   onDone = { save(LocalDate.now(), false) })
 }
 if (showHistory) {
  AlertDialog(onDismissRequest={ showHistory=false }, containerColor=androidx.compose.ui.graphics.Color.White,
   title={ Text("港维护记录") },
   text={
    LazyColumn(Modifier.fillMaxWidth().heightIn(max=400.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
     item {
      TextButton(onClick={ pick(history.placement ?: history.legacy, true) }) {
       Text(history.placement?.let { "安港  $it" } ?: "安港")
      }
     }
     history.legacy?.let { old ->
      item {
       Column {
        Text("旧版维护日期  $old")
        Row {
         TextButton(onClick={ pick(old, true, old) }) { Text("记为安港") }
         TextButton(onClick={ pick(old, false, old) }) { Text("记为冲管") }
        }
       }
      }
     }
     items(history.flushes.size) { reverseIndex ->
      val index = history.flushes.lastIndex - reverseIndex
      val date = history.flushes[index]
      Card(onClick={ pick(date, false, date) }, modifier=Modifier.fillMaxWidth(),
       colors=CardDefaults.cardColors(containerColor=androidx.compose.ui.graphics.Color(0xFFF4F4F4))) {
       Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement=Arrangement.SpaceBetween,
        verticalAlignment=Alignment.CenterVertically) {
        Text("冲${index + 1}")
        Text(date.toString())
        Text(history.interval(index)?.let { "${it}天" } ?: "—")
       }
      }
     }
    }
   },
   confirmButton={ TextButton(onClick={ pick(null, false) }) { Text("添加冲管") } },
   dismissButton={ TextButton(onClick={ showHistory=false }) { Text("关闭") } })
 }
}
