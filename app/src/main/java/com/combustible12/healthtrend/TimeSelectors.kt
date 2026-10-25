package com.combustible12.healthtrend

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun ClockTimeSelector(value:String,onSelect:(String)->Unit,modifier:Modifier=Modifier){
 val context=LocalContext.current
 val time=runCatching{LocalTime.parse(value.trim(),DateTimeFormatter.ofPattern("H:mm"))}.getOrElse{LocalTime.now()}
 Box(modifier.height(38.dp).clickable{
  TimePickerDialog(context,{_,hour,minute->onSelect("%02d:%02d".format(hour,minute))},time.hour,time.minute,true).show()
 },contentAlignment=Alignment.Center){
  Text(value,maxLines=1,fontSize=17.sp,textAlign=TextAlign.Center)
 }
}

@Composable
fun ReportDateTimeSelector(value:String,onSelect:(String)->Unit,modifier:Modifier=Modifier){
 val context=LocalContext.current
 val normalized=value.trim().replace('/','-')
 val day=runCatching{LocalDate.parse(normalized.take(10))}.getOrElse{LocalDate.now()}
 val time=Regex("(\\d{1,2}):(\\d{2})").find(normalized)?.let{runCatching{LocalTime.of(it.groupValues[1].toInt(),it.groupValues[2].toInt())}.getOrNull()}
 Box(modifier.clickable{
  DatePickerDialog(context,{_,year,month,dayOfMonth->
   val chosen="%04d-%02d-%02d".format(year,month+1,dayOfMonth)
   val base=time?:LocalTime.now()
   TimePickerDialog(context,{_,hour,minute->onSelect("$chosen %02d:%02d".format(hour,minute))},base.hour,base.minute,true).show()
  },day.year,day.monthValue-1,day.dayOfMonth).show()
 }){
  Text(value.ifBlank{"选择检查日期和时间"},Modifier.padding(12.dp))
 }
}
