package com.combustible12.healthtrend

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@Composable
internal fun CourseCalendar(records: List<CourseRecord>, reminders: List<TreatmentReminder>, openCourse: (CourseRecord) -> Unit) {
    val today = LocalDate.now()
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    var selectedDate by rememberSaveable { mutableStateOf("") }
    val month = YearMonth.parse(monthText)
    val zone = ZoneId.systemDefault()
    val recordsByDate = remember(records, zone) {
        records.associateBy { Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate() }
    }
    val remindersByDate = remember(reminders) {
        reminders.filter { it.triggerDate.isNotBlank() }.groupBy { it.triggerDate }
    }
    val offset = month.atDay(1).dayOfWeek.value - 1
    fun changeMonth(delta: Long) {
        monthText = month.plusMonths(delta).toString()
        selectedDate = ""
    }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("病程日历", Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                IconButton({ changeMonth(-1) }) { Icon(Icons.Outlined.ChevronLeft, "上个月") }
                Text("${month.year}年${month.monthValue}月", fontSize = 14.sp)
                IconButton({ changeMonth(1) }) { Icon(Icons.Outlined.ChevronRight, "下个月") }
            }
            Row {
                "一二三四五六日".forEach { Text(it.toString(), Modifier.weight(1f), color = Muted, textAlign = TextAlign.Center, fontSize = 12.sp) }
            }
            repeat((offset + month.lengthOfMonth() + 6) / 7) { week ->
                Row {
                    repeat(7) { weekday ->
                        val day = week * 7 + weekday - offset + 1
                        if (day !in 1..month.lengthOfMonth()) {
                            Spacer(Modifier.weight(1f))
                        } else {
                            val date = month.atDay(day)
                            val key = date.toString()
                            val record = recordsByDate[date]
                            val datedReminders = remindersByDate[key].orEmpty()
                            val marked = datedReminders.isNotEmpty() || record != null &&
                                (record.userEdited || !record.isBlankCourseCard() || record.phase !in listOf("", "观察"))
                            Column(
                                Modifier.weight(1f).background(if (selectedDate == key) SelectedTint else Color.Transparent, RoundedCornerShape(8.dp))
                                    .clickable {
                                        selectedDate = key
                                        if (record != null && datedReminders.isEmpty()) openCourse(record)
                                    }.semantics { contentDescription = if (marked) "$key 有记录或提醒" else key }
                                    .heightIn(min = 36.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                            ) {
                                Text(day.toString(), color = if (date == today) Accent else Ink, fontSize = 14.sp)
                                Box(Modifier.size(5.dp).background(if (marked) Accent else Color.Transparent, CircleShape))
                            }
                        }
                    }
                }
            }
            if (selectedDate.isNotEmpty()) {
                val record = recordsByDate[LocalDate.parse(selectedDate)]
                val items = remindersByDate[selectedDate].orEmpty()
                for (item in items) Text("${treatmentReminderTriggerLabel(item)} · ${item.text}", fontSize = 14.sp)
                if (record != null) TextButton({ openCourse(record) }) { Text("查看当天病程") }
                else if (items.isEmpty()) Text("当天没有病程记录或提醒", fontSize = 13.sp, color = Muted)
            }
        }
    }
}
