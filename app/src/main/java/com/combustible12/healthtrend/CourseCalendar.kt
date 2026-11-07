package com.combustible12.healthtrend

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.rounded.Warning
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

internal enum class CalendarTreatment(val label: String, val color: Color) {
    CHEMOTHERAPY("化疗", Color(0xFF9862C6)),
    RADIOTHERAPY("放疗", Color(0xFF3584C6)),
    BRACHYTHERAPY("后装", Color(0xFF299879)),
    FOLLOW_UP("复查", Color(0xFFD17A35))
}

internal fun calendarTreatments(record: CourseRecord?, reminders: List<TreatmentReminder>, chemotherapyDay: Boolean): List<CalendarTreatment> {
    val title = record?.title.orEmpty()
    val phase = record?.phase.orEmpty()
    return buildList {
        if (chemotherapyDay) add(CalendarTreatment.CHEMOTHERAPY)
        if (phase in setOf("放疗", "放疗日", "放化") || Regex("""(?:^|-)放\\d+(?:-|$)""").containsMatchIn(title))
            add(CalendarTreatment.RADIOTHERAPY)
        if (phase == "后装" || title.contains("后装") || reminders.any { it.purpose == ReminderPurpose.BRACHYTHERAPY })
            add(CalendarTreatment.BRACHYTHERAPY)
        if (phase == "复查" || phase == "结疗复查" || title.contains("复查") || reminders.any { it.purpose == ReminderPurpose.FOLLOW_UP })
            add(CalendarTreatment.FOLLOW_UP)
    }
}

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
    val chemotherapyDates = remember(records, zone) {
        chemotherapyEventDates(records, zone).map { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }.toSet()
    }
    val offset = month.atDay(1).dayOfWeek.value - 1
    fun changeMonth(delta: Long) {
        monthText = month.plusMonths(delta).toString()
        selectedDate = ""
    }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Warm)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("病程日历", Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                IconButton({ changeMonth(-1) }) { Icon(Icons.Outlined.ChevronLeft, "上个月") }
                Text("${month.year}年${month.monthValue}月", fontSize = 14.sp)
                IconButton({ changeMonth(1) }) { Icon(Icons.Outlined.ChevronRight, "下个月") }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                CalendarTreatment.values().forEach { treatment ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(Modifier.size(5.dp).background(treatment.color, CircleShape))
                        Text(treatment.label, fontSize = 11.sp, color = Muted)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Warning, null, Modifier.size(12.dp), tint = Color(0xFFE6A34B))
                    Text("提醒", fontSize = 11.sp, color = Muted)
                }
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
                            val treatments = calendarTreatments(record, datedReminders, date in chemotherapyDates)
                            val hasReminder = datedReminders.any { it.completedAt == null }
                            val marked = treatments.isNotEmpty() || hasReminder
                            Column(
                                Modifier.weight(1f).background(if (selectedDate == key) SelectedTint else Color.Transparent, RoundedCornerShape(8.dp))
                                    .clickable {
                                        selectedDate = key
                                        if (record != null && datedReminders.isEmpty()) openCourse(record)
                                    }.semantics { contentDescription = if (marked) "$key 有记录或提醒" else key }
                                    .heightIn(min = 36.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                            ) {
                                Box {
                                    Text(day.toString(), color = if (date == today) Accent else Color.White, fontSize = 14.sp)
                                    if (hasReminder) Icon(
                                        Icons.Rounded.Warning, "$key 提醒",
                                        Modifier.align(Alignment.TopEnd).offset(x = 10.dp, y = (-2).dp).size(10.dp),
                                        tint = Color(0xFFE6A34B)
                                    )
                                }
                                Row(Modifier.height(12.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                                    treatments.forEach { treatment ->
                                        Box(Modifier.size(4.dp).background(treatment.color, CircleShape).semantics { contentDescription = "$key ${treatment.label}圆点" })
                                    }
                                }
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
