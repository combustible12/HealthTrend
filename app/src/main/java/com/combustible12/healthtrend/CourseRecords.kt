package com.combustible12.healthtrend

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import kotlin.math.roundToInt
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.key
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut


private val courseFilterHeight=32.dp

private val pressureColors=listOf(Color(0xFF2587C9),Color(0xFF218A50),Color(0xFFA96B08),Color(0xFFD9434E))
/** A high reading in either component determines the higher risk category. */
internal fun bloodPressureBand(systolic:Int,diastolic:Int):Int=when{
 systolic>=140||diastolic>=90->3
 systolic>=120||diastolic>=80->2
 systolic<90||diastolic<60->0
 else->1
}
private fun pulseValueColor(value:Int?):Color=when{
 value==null->Color(0xFFE5F8ED)
 value in 60..100->Color(0xFFCFF3E0)
 else->Color(0xFFFFDCE2)
}
@Composable private fun PressureReadingView(time:String,systolic:Int?,diastolic:Int?,pulse:Int?=null,showHeader:Boolean=true){
 Column(verticalArrangement=Arrangement.spacedBy(2.dp)){
  if(showHeader) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)){
   listOf("时间","脉搏","高压","低压").forEach{label->
    Text(label,Modifier.weight(1f),fontSize=11.sp,color=Muted,textAlign=androidx.compose.ui.text.style.TextAlign.Center,maxLines=1)
   }
  }
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)){
   @Composable fun Value(text:String,fill:Color,ink:Color){
    Box(Modifier.weight(1f).height(26.dp).clip(RoundedCornerShape(9.dp)).background(fill),contentAlignment=Alignment.Center){
     Text(text,fontSize=12.sp,fontWeight=FontWeight.Bold,color=ink,maxLines=1,softWrap=false)
    }
   }
   Value(time.ifBlank{"—"},Color(0xFFE6E6E6),Ink)
   Value(pulse?.toString() ?: "—",pulseValueColor(pulse),Ink)
   Value(systolic?.toString() ?: "—",pressureValueColor(systolic,true),if(systolic==null)Ink else Color.White)
   Value(diastolic?.toString() ?: "—",pressureValueColor(diastolic,false),if(diastolic==null)Ink else Color.White)
  }
 }
}
@Composable private fun CompactPressureField(value:String,onValueChange:(String)->Unit,modifier:Modifier=Modifier,hint:String="",fill:Color=Color.White,ink:Color=Ink){
 AppCompactInput(value,onValueChange,modifier,hint,fill,androidx.compose.ui.text.TextStyle(fontSize=17.sp,color=ink,textAlign=androidx.compose.ui.text.style.TextAlign.Center))
}
private fun pressureValueColor(value:Int?,systolic:Boolean):Color=when{
 value==null->Color(0xFFF2F2F2)
 value<(if(systolic)90 else 60)->pressureColors[0]
 value<(if(systolic)120 else 80)->pressureColors[1]
 value<(if(systolic)140 else 90)->pressureColors[2]
 else->pressureColors[3]
}
@Composable private fun PressureReadingEditor(reading:BloodPressureReading,onChange:(BloodPressureReading)->Unit,onRemove:(()->Unit)?=null){
 Column(verticalArrangement=Arrangement.spacedBy(5.dp)){
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.Top){
   @Composable fun Entry(label:String,value:String,fill:Color,change:(String)->Unit,hint:String="",ink:Color=Ink){
    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)){
     Text(label,fontSize=11.sp,color=Muted,maxLines=1,softWrap=false)
     CompactPressureField(value,change,Modifier.fillMaxWidth(),hint,fill,ink)
    }
   }
   Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)){
    Text("时间",fontSize=11.sp,color=Muted,maxLines=1)
    ClockTimeSelector(reading.time,{onChange(reading.copy(time=it))},Modifier.fillMaxWidth())
   }
   Entry("脉搏 次/分",reading.pulse?.toString().orEmpty(),pulseValueColor(reading.pulse),{v->onChange(reading.copy(pulse=v.filter(Char::isDigit).take(3).toIntOrNull()))})
   Entry("高压 mmHg",reading.systolic?.toString().orEmpty(),pressureValueColor(reading.systolic,true),{v->onChange(reading.copy(systolic=v.filter(Char::isDigit).take(3).toIntOrNull()))},ink=if(reading.systolic==null)Ink else Color.White)
   Entry("低压 mmHg",reading.diastolic?.toString().orEmpty(),pressureValueColor(reading.diastolic,false),{v->onChange(reading.copy(diastolic=v.filter(Char::isDigit).take(3).toIntOrNull()))},ink=if(reading.diastolic==null)Ink else Color.White)
   if(onRemove!=null)IconButton(onRemove,modifier=Modifier.size(26.dp)){Icon(Icons.Outlined.Close,"删除这组血压",tint=Muted)}
  }
  Text("脉搏 ${reading.pulse?.toString() ?: "—"} 次/分   血压 ${reading.systolic?.toString() ?: "—"}/${reading.diastolic?.toString() ?: "—"} mmHg",fontSize=13.sp,color=Ink)
 }
}
@Composable fun CourseRecordsPage(
 modifier:Modifier,
 records:List<CourseRecord>,
 store:HealthStore,
 open:(CourseRecord)->Unit,
 add:()->Unit,
 remove:(CourseRecord)->Unit,
 view:(List<String>,Int)->Unit,
 openWeightTrend:()->Unit={}
){
 val context=LocalContext.current
 var phases by remember{mutableStateOf(store.coursePhases())}
 var managingPhases by rememberSaveable{mutableStateOf(false)}
 var phaseError by remember{mutableStateOf<String?>(null)}
 var filter by rememberSaveable{mutableStateOf("全部")}
 var search by rememberSaveable{mutableStateOf("")}
 var requestedRecordId by remember{mutableStateOf<String?>(null)}
 var dateLookupMessage by remember{mutableStateOf<String?>(null)}
 val query=search.trim()
 val shown=records.filter{record->
  (query.isNotBlank()||filter=="全部"||record.phase==filter)&&DataSearch.course(query,record)
 }
 val listState=rememberLazyListState();val scope=rememberCoroutineScope()
 LaunchedEffect(requestedRecordId,shown.map{it.id}){
  val id=requestedRecordId?:return@LaunchedEffect
  val index=shown.indexOfFirst{it.id==id}
  if(index>=0){listState.animateScrollToItem(index);requestedRecordId=null}
 }
 fun pickCourseDate(){
  val initial=records.firstOrNull()?.let{courseLocalDate(it.date)}?:LocalDate.now()
  android.app.DatePickerDialog(context,{_,year,month,day->
   val selected=LocalDate.of(year,month+1,day)
   val target=courseRecordForDate(records,selected)
   if(target==null)dateLookupMessage="${selected.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))} 没有病程记录"
   else{filter="全部";search="";requestedRecordId=target.id}
  },initial.year,initial.monthValue-1,initial.dayOfMonth).show()
 }
 Box(modifier.fillMaxSize()){
  Column(Modifier.fillMaxSize().padding(horizontal=20.dp)){
   Spacer(Modifier.height(12.dp))
   Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
    Text("病程记录",modifier=Modifier.weight(1f).pointerInput(listState){detectTapGestures(onDoubleTap={scope.launch{listState.animateScrollToItem(0)}})},fontSize=28.sp,fontWeight=FontWeight.Bold)
    IconButton(::pickCourseDate){Icon(Icons.Outlined.CalendarMonth,"按日期查看病程记录",tint=Accent)}
    IconButton(add){Icon(Icons.Outlined.Add,"新增病程记录",tint=Accent)}
   }
   Spacer(Modifier.height(12.dp))
   AppSearchField(search,{search=it},"搜索病程记录",Modifier.fillMaxWidth().padding(top=6.dp),height=36.dp,showSearchIcon=true,shape=RoundedCornerShape(12.dp))
   Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
    Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(vertical=10.dp)){
    (listOf("全部")+phases).forEach{phase->FilterChip(filter==phase,{filter=phase},label={Text(phase)},modifier=Modifier.padding(end=8.dp).height(courseFilterHeight),border=null,colors=FilterChipDefaults.filterChipColors(containerColor=Color.White,selectedContainerColor=SelectedTint))}
    }
    TextButton({managingPhases=true}){Text("管理")}
   }
   Row(Modifier.fillMaxWidth().offset(x=(-6).dp).padding(end=8.dp)){
    Spacer(Modifier.width(22.dp))
    PortMaintenanceBar(store,Modifier.weight(1f))
   }
   Spacer(Modifier.height(8.dp))
   if(shown.isEmpty())Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text(if(query.isBlank())"还没有病程记录" else "没有找到相关病程记录",color=Muted)}
   else Box(Modifier.fillMaxSize()){
    LazyColumn(Modifier.fillMaxSize().offset(x=(-6).dp).padding(end=8.dp),state=listState,contentPadding=PaddingValues(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
    items(shown,key={it.id}){record->
     CourseTimelineCard(record,displayCourseTitle(record,records),store.weightRecordForCourse(record.id),{open(record)},view,openWeightTrend)
    }
    if(listState.canScrollBackward)item(key="back_to_top"){Box(Modifier.fillMaxWidth().padding(top=8.dp,bottom=12.dp),contentAlignment=Alignment.Center){TextButton({scope.launch{listState.animateScrollToItem(0)}}){Icon(Icons.Outlined.VerticalAlignTop,null);Spacer(Modifier.width(6.dp));Text("回到顶部")}}}
    }
    LazyScrollProgressRail(listState,Modifier.align(Alignment.CenterEnd).offset(x=16.dp).padding(top=8.dp,bottom=8.dp).width(10.dp).fillMaxHeight())
   }
  }
 }
 if(managingPhases)CoursePhaseManager(phases,{managingPhases=false}){updated->
  runCatching{store.saveCoursePhases(updated)}.onSuccess{phases=store.coursePhases();if(filter!="全部"&&filter !in phases)filter="全部"}.onFailure{phaseError=it.message?:"分类保存失败"}
 }
 phaseError?.let{message->AlertDialog(onDismissRequest={phaseError=null},title={Text("分类保存失败")},text={Text(message)},confirmButton={TextButton({phaseError=null}){Text("关闭")}})}
 dateLookupMessage?.let{message->AlertDialog(onDismissRequest={dateLookupMessage=null},title={Text("没有找到当天卡片")},text={Text(message)},confirmButton={TextButton({dateLookupMessage=null}){Text("知道了")}})}
}

internal fun courseRecordForDate(records:List<CourseRecord>,date:LocalDate):CourseRecord?=records.firstOrNull{courseLocalDate(it.date)==date}

internal val chemotherapyCoursePhases=setOf("化疗","化疗日","放化")
private val radiotherapyCoursePhases=setOf("放疗","放疗日","放化")

private fun courseLocalDate(epoch:Long):LocalDate=Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).toLocalDate()

internal fun automaticCourseTitle(record:CourseRecord,records:List<CourseRecord>):String{
 val target=courseLocalDate(record.date)
 val rows=(records.filterNot{it.id==record.id}+record).map{it to courseLocalDate(it.date)}
 val chemotherapyDates=rows.filter{(item,date)->item.phase in chemotherapyCoursePhases&&!date.isAfter(target)}.map{it.second}.distinct().sorted()
 val latestChemotherapy=chemotherapyDates.lastOrNull()
 val priorRows=records.filterNot{it.id==record.id}.map{it to courseLocalDate(it.date)}.filter{!it.second.isAfter(target)}
 val chemoPattern=Regex("(\\d+)化(\\d+)")
 val chemoAnchor=priorRows.mapNotNull{(item,date)->chemoPattern.find(item.title)?.let{match->Triple(date,match.groupValues[1].toInt(),match.groupValues[2].toInt())}}.maxByOrNull{it.first}
 val chemotherapy=when{
  record.phase in chemotherapyCoursePhases->{
   val cycle=(chemoAnchor?.second?:chemotherapyDates.dropLast(1).size)+1
   "${cycle}化1"
  }
  chemoAnchor!=null->"${chemoAnchor.second}化${chemoAnchor.third+java.time.temporal.ChronoUnit.DAYS.between(chemoAnchor.first,target)}"
  latestChemotherapy!=null->{val cycle=chemotherapyDates.indexOf(latestChemotherapy)+1;val day=java.time.temporal.ChronoUnit.DAYS.between(latestChemotherapy,target)+1;"${cycle}化${day}"}
  else->null
 }
 val radiotherapy=if(record.phase in radiotherapyCoursePhases){
  val radiationPattern=Regex("放(\\d+)")
  val anchor=priorRows.mapNotNull{(item,date)->radiationPattern.find(item.title)?.groupValues?.get(1)?.toIntOrNull()?.let{date to it}}.maxByOrNull{it.first}
  val count=if(anchor==null)rows.filter{(item,date)->item.phase in radiotherapyCoursePhases&&!date.isAfter(target)}.map{it.second}.distinct().size
   else anchor.second+rows.filter{(item,date)->item.phase in radiotherapyCoursePhases&&date.isAfter(anchor.first)&&!date.isAfter(target)}.map{it.second}.distinct().size
  "放$count"
 }else null
 return listOfNotNull(chemotherapy,radiotherapy).joinToString("-")
}

internal fun displayCourseTitle(record:CourseRecord,records:List<CourseRecord>):String=
 if(record.titleAuto)record.title.ifBlank{automaticCourseTitle(record,records)} else record.title

internal fun chemotherapyEventDates(records:List<CourseRecord>,zone:ZoneId=ZoneId.systemDefault()):List<Long>{
 val treatmentDay=Regex("""(?:^|-)\d+化1(?:-|$)""")
 val dates=records.filter{record->
  record.phase in setOf("化疗","化疗日")||treatmentDay.containsMatchIn(displayCourseTitle(record,records))
 }.map{record->courseLocalDate(record.date)}.distinct().sorted()
 return dates.filterIndexed{index,date->index==0||date!=dates[index-1].plusDays(1)}
  .map{date->date.atStartOfDay(zone).toInstant().toEpochMilli()}
}

internal fun legacyGrowthFactorInjectionNote(note:String):Boolean{
 val normalized=note.replace(Regex("\\s+"),"")
 if("升白针" !in normalized)return false
 return listOf("未打升白针","没打升白针","不打升白针","不用升白针","无需升白针","计划打升白针","准备打升白针","考虑打升白针","可能打升白针").none{it in normalized}
}

internal fun growthFactorInjectionEventDates(records:List<CourseRecord>,zone:ZoneId=ZoneId.systemDefault()):List<Long>{
 val dates=records.filter{it.growthFactorInjection}.map{courseLocalDate(it.date)}.distinct().sorted()
 return dates.filterIndexed{index,date->index==0||date!=dates[index-1].plusDays(1)}
  .map{date->date.atStartOfDay(zone).toInstant().toEpochMilli()}
}

internal fun CourseRecord.isBlankCourseCard():Boolean = titleAuto&&!growthFactorInjection&&symptomText.isBlank()&&systolic==null&&diastolic==null&&pulse==null&&additionalPressures.isEmpty()&&symptomImages.isEmpty()&&checkText.isBlank()&&checkImages.isEmpty()&&medicineText.isBlank()&&medicineImages.isEmpty()&&noteText.isBlank()&&noteImages.isEmpty()

internal fun currentRadiotherapyNumber(records:List<CourseRecord>):Int = records.mapNotNull{record->Regex("放(\\d+)").find(displayCourseTitle(record,records))?.groupValues?.get(1)?.toIntOrNull()}.maxOrNull()?:0

internal fun nextPortMaintenanceDate(lastDate:String):String?=runCatching{LocalDate.parse(lastDate).plusDays(28).toString()}.getOrNull()

@Composable private fun PortMaintenanceBar(store:HealthStore,modifier:Modifier=Modifier){
 val context=LocalContext.current
 var lastDate by remember{mutableStateOf(store.portMaintenanceLastDate().orEmpty())}
 val nextDate=nextPortMaintenanceDate(lastDate).orEmpty()
 fun compact(value:String)=runCatching{LocalDate.parse(value).format(DateTimeFormatter.ofPattern("MM/dd"))}.getOrElse{"--/--"}
 fun pickLastDate(){
  val initial=runCatching{LocalDate.parse(lastDate)}.getOrElse{LocalDate.now()}
  android.app.DatePickerDialog(context,{_,year,month,day->
   val selected=LocalDate.of(year,month+1,day).toString()
   store.savePortMaintenanceLastDate(selected)
   lastDate=selected
  },initial.year,initial.monthValue-1,initial.dayOfMonth).show()
 }
 Surface(modifier.fillMaxWidth().height(courseFilterHeight).semantics{contentDescription="港维护色块"},shape=RoundedCornerShape(12.dp),color=Color.White){
  Row(Modifier.fillMaxSize().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically){
   Text("港维护",fontSize=13.sp,fontWeight=FontWeight.Normal,color=Muted)
   Spacer(Modifier.width(12.dp))
   Text(compact(lastDate),Modifier.clickable{pickLastDate()},fontSize=13.sp,fontWeight=FontWeight.Normal,color=Ink)
   Spacer(Modifier.width(8.dp))
   Text("→",fontSize=13.sp,fontWeight=FontWeight.Normal,color=Muted)
   Spacer(Modifier.width(8.dp))
   Text(compact(nextDate),fontSize=14.sp,fontWeight=FontWeight.Bold,color=Ink)
   Spacer(Modifier.weight(1f))
   Text("已维护",Modifier.clickable(enabled=nextDate.isNotBlank()){
    store.savePortMaintenanceLastDate(nextDate)
    lastDate=nextDate
   }.padding(start=12.dp,top=4.dp,bottom=4.dp),fontSize=13.sp,fontWeight=FontWeight.Normal,color=if(nextDate.isNotBlank())Accent else Muted)
  }
 }
}

@Composable private fun CourseTimelineCard(record:CourseRecord,displayTitle:String,weight:WeightRecord?,onOpen:()->Unit,view:(List<String>,Int)->Unit,openWeightTrend:()->Unit){
 val phaseColors=coursePhaseColors(record.phase)
 Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),verticalAlignment=Alignment.Top){
  Column(Modifier.width(22.dp).fillMaxHeight(),horizontalAlignment=Alignment.CenterHorizontally){
   Spacer(Modifier.height(25.dp));Box(Modifier.size(10.dp).background(phaseColors.second,CircleShape));Box(Modifier.width(2.dp).weight(1f).background(Color(0xFFE7DDD6)))
  }
  Card(onClick=onOpen,modifier=Modifier.weight(1f),shape=RoundedCornerShape(14.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){
   Column(Modifier.padding(start=16.dp,top=14.dp,end=12.dp,bottom=14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Top){
     Row(Modifier.weight(1f).padding(top=2.dp),verticalAlignment=Alignment.CenterVertically){
      val weekday=listOf("一","二","三","四","五","六","日")[Instant.ofEpochMilli(record.date).atZone(ZoneId.systemDefault()).dayOfWeek.value-1]
      Box(Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(phaseColors.first),contentAlignment=Alignment.Center){
       Text(weekday,fontSize=13.sp,fontWeight=FontWeight.Bold,color=phaseColors.second)
      }
      Spacer(Modifier.width(8.dp))
      val heading=courseDate(record.date)+(displayTitle.trim().takeIf{it.isNotBlank()}?.let{" · $it"}?:"")
      Text(heading,fontWeight=FontWeight.Bold,fontSize=15.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
     }
     Spacer(Modifier.width(6.dp))
     Surface(shape=RoundedCornerShape(14.dp),color=phaseColors.first){Text(record.phase,Modifier.padding(horizontal=7.dp,vertical=3.dp),color=phaseColors.second,fontSize=10.sp,fontWeight=FontWeight.Medium)}
    }
    Column(verticalArrangement=Arrangement.spacedBy(5.dp)){
     val showPrimary=record.systolic!=null||record.diastolic!=null||record.pulse!=null
     if(showPrimary||record.additionalPressures.isNotEmpty()){
      Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)){
       listOf("时间","脉搏","高压","低压").forEach{label->Text(label,Modifier.weight(1f),fontSize=11.sp,color=Muted,textAlign=androidx.compose.ui.text.style.TextAlign.Center,maxLines=1)}
      }
     }
     if(showPrimary)PressureReadingView(record.pressureTime,record.systolic,record.diastolic,record.pulse,showHeader=false)
     record.additionalPressures.forEach{reading->PressureReadingView(reading.time,reading.systolic,reading.diastolic,reading.pulse,showHeader=false)}
    }
    if(weight!=null)CourseWeightView(weight,openWeightTrend)
    if(record.symptomText.isNotBlank()||record.symptomImages.isNotEmpty())CourseSection(Icons.Outlined.MonitorHeart,"症状",record.symptomText,record.symptomImages,view)
    if(record.checkText.isNotBlank()||record.checkImages.isNotEmpty())CourseSection(Icons.Outlined.FactCheck,"检查",record.checkText,record.checkImages,view)
    if(record.medicineText.isNotBlank()||record.medicineImages.isNotEmpty())CourseSection(Icons.Outlined.Medication,"药品 / 取药",record.medicineText,record.medicineImages,view)
    if(record.noteText.isNotBlank()||record.noteImages.isNotEmpty())CourseSection(Icons.Outlined.Notes,"备注",record.noteText,record.noteImages,view)
   }
  }
 }
}

@Composable private fun CourseWeightView(weight:WeightRecord,onOpenTrend:()->Unit){
 Column(verticalArrangement=Arrangement.spacedBy(7.dp)){
  Row(Modifier.fillMaxWidth().clickable(onClick=onOpenTrend).semantics{contentDescription="体重模块标题"},verticalAlignment=Alignment.CenterVertically){
   Icon(Icons.Outlined.MonitorWeight,null,Modifier.size(20.dp),tint=Accent);Spacer(Modifier.width(9.dp));Text("体重",Modifier.weight(1f),color=Muted,fontSize=13.sp,lineHeight=17.sp);Icon(Icons.Outlined.ChevronRight,"打开体重趋势",Modifier.size(18.dp),tint=Muted)
  }
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)){
   listOf("时间","体重","体表面积").forEach{label->Text(label,Modifier.weight(1f),fontSize=11.sp,color=Muted,textAlign=androidx.compose.ui.text.style.TextAlign.Center,maxLines=1)}
  }
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)){
   @Composable fun Value(text:String,description:String){Box(Modifier.weight(1f).height(26.dp).clip(RoundedCornerShape(9.dp)).background(Color(0xFFE6E6E6)).semantics{contentDescription=description},contentAlignment=Alignment.Center){Text(text,fontSize=12.sp,fontWeight=FontWeight.Bold,color=Ink,maxLines=1,softWrap=false)}}
   Value(weightTimeText(weight.measuredAtEpochMillis),"体重卡片时间色块")
   Value(courseWeightText(weight.kilograms),"体重卡片数值色块")
   Value(bodySurfaceAreaText(weight.heightCmAtMeasurement,weight.kilograms),"体重卡片体表面积色块")
  }
 }
}

private fun coursePhaseColors(phase:String)=when(phase){
 "化疗","化疗日"->Color(0xFFFFEEE5) to Color(0xFFF2763D)
 "恢复期"->Color(0xFFE5F8EE) to Color(0xFF20A66A)
 "观察"->Color(0xFFE4F2FF) to Color(0xFF3285C8)
 "放疗","放疗日"->Color(0xFFF0E7FF) to Color(0xFF8554C0)
 "放化"->Color(0xFFF7E7F0) to Color(0xFFA24D79)
 else->{
  val palette=listOf(
   Color(0xFFFFE8F0) to Color(0xFFBE4E7A),
   Color(0xFFE0F5F2) to Color(0xFF25867B),
   Color(0xFFFFF2D6) to Color(0xFFA77920),
   Color(0xFFEBE9FF) to Color(0xFF7160B7)
  )
  palette[Math.floorMod(phase.hashCode(),palette.size)]
 }
}

@Composable private fun CourseTextRow(icon:androidx.compose.ui.graphics.vector.ImageVector,text:String,muted:Boolean=false){Row(verticalAlignment=Alignment.Top){Icon(icon,null,Modifier.size(20.dp),tint=Accent);Spacer(Modifier.width(9.dp));Text(text,Modifier.weight(1f),color=if(muted)Muted else Ink,fontSize=if(muted)13.sp else 15.sp,lineHeight=if(muted)17.sp else 19.sp)}}

@Composable private fun CourseSection(icon:androidx.compose.ui.graphics.vector.ImageVector,label:String,text:String,images:List<String>,view:(List<String>,Int)->Unit){
 Column(verticalArrangement=Arrangement.spacedBy(7.dp)){
  if(text.isNotBlank())CourseTextRow(icon,text,true)
  else if(images.isNotEmpty())CourseTextRow(icon,label)
  if(images.isNotEmpty())CourseThumbnails(images,{view(images,it)})
 }
}

@Composable private fun CourseThumbnails(images:List<String>,open:(Int)->Unit,remove:((Int)->Unit)?=null,replace:((Int)->Unit)?=null){
 Column(Modifier.padding(start=29.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
  images.withIndex().chunked(4).forEach{row->
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
    row.forEach{(i,uri)->Box{
     CourseThumbnail(uri,Modifier.size(52.dp).clickable{open(i)})
     if(replace!=null)Surface(onClick={replace(i)},modifier=Modifier.align(Alignment.BottomEnd).offset(x=5.dp,y=5.dp).size(22.dp),shape=CircleShape,color=Color.White,shadowElevation=2.dp){Icon(Icons.Outlined.Edit,"更换图片",Modifier.padding(4.dp),tint=Accent)}
     if(remove!=null)Surface(onClick={remove(i)},modifier=Modifier.align(Alignment.TopEnd).offset(x=5.dp,y=(-5).dp).size(22.dp),shape=CircleShape,color=Color.White,shadowElevation=2.dp){Icon(Icons.Outlined.Close,"移除图片",Modifier.padding(4.dp),tint=Muted)}
    }}
   }
  }
 }
}

@Composable private fun CourseThumbnail(uri:String,modifier:Modifier){
 val context=LocalContext.current
 val bitmap by produceState<android.graphics.Bitmap?>(null,uri){value=withContext(Dispatchers.IO){runCatching{decodeReportBitmap(context,Uri.parse(uri),maxPixels=250_000,maxDimension=600)}.getOrNull()}}
 Box(modifier.clip(RoundedCornerShape(9.dp)).background(Color(0xFFF0EDEA)),contentAlignment=Alignment.Center){if(bitmap!=null)Image(bitmap!!.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)else Icon(Icons.Outlined.Image,null,tint=Muted)}
}

@Composable fun CourseRecordEditor(initial:CourseRecord,store:HealthStore,onClose:()->Unit,onSave:(CourseRecord,Double?,Long?)->Unit,onDelete:()->Unit,onView:(List<String>,Int)->Unit,onOpenWeightTrend:()->Unit={},onOpenExisting:(CourseRecord)->Unit={}){
 val context=LocalContext.current
 val coursePhases=remember{store.coursePhases()}
 val exists=remember(initial.id){store.courseRecords().any{it.id==initial.id}}
 var record by rememberSaveable(initial,stateSaver=diskStateSaver<CourseRecord>(context,"course-record-editor")){mutableStateOf(initial)}
 var date by rememberSaveable{mutableStateOf(if(exists)courseEditorDate(initial.date) else "")}
 var duplicateRecord by remember{mutableStateOf<CourseRecord?>(null)}
 val initialWeight=remember(initial.id){store.weightRecordForCourse(initial.id)}
 var weightText by rememberSaveable(initial.id){mutableStateOf(initialWeight?.kilograms?.let(::courseWeightText).orEmpty())}
 var weightTime by rememberSaveable(initial.id){mutableStateOf(initialWeight?.let{weightTimeText(it.measuredAtEpochMillis)}?:LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")))}
 var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf("")};var deleting by remember{mutableStateOf(false)}
 var pendingImageRemoval by remember{mutableStateOf<Pair<String,Int>?>(null)}
 var pendingImageReplace by remember{mutableStateOf<Pair<String,Int>?>(null)}
 var viewingKind by rememberSaveable{mutableStateOf<String?>(null)}
 var viewingIndex by rememberSaveable{mutableIntStateOf(0)}
 val scope=rememberCoroutineScope()
 fun addImages(target:String,uris:List<Uri>){if(uris.isEmpty())return;busy=true;scope.launch{try{val owned=withContext(Dispatchers.IO){uris.map{store.ownImage(it)}};record=when(target){"symptom"->record.copy(symptomImages=record.symptomImages+owned);"check"->record.copy(checkImages=record.checkImages+owned);"medicine"->record.copy(medicineImages=record.medicineImages+owned);else->record.copy(noteImages=record.noteImages+owned)}}catch(e:Exception){error="图片保存失败：${e.message}"}finally{busy=false}}}
 val replaceInput=rememberPhotoInput({uris->
  val target=pendingImageReplace
  pendingImageReplace=null
  if(target!=null&&uris.isNotEmpty()&&!busy){busy=true;scope.launch{try{
   val owned=withContext(Dispatchers.IO){store.ownImage(uris.first())}
   val (kind,index)=target
   fun replaced(items:List<String>):List<String>{require(index in items.indices);return items.toMutableList().also{it[index]=owned}}
   record=when(kind){"symptom"->record.copy(symptomImages=replaced(record.symptomImages));"check"->record.copy(checkImages=replaced(record.checkImages));"medicine"->record.copy(medicineImages=replaced(record.medicineImages));else->record.copy(noteImages=replaced(record.noteImages))}
  }catch(e:Exception){error="更换图片失败：${e.message}"}finally{busy=false}}}
 },{error=it})
 val symptomInput=rememberPhotoInput({addImages("symptom",it)},{error=it})
 val checkInput=rememberPhotoInput({addImages("check",it)},{error=it})
 val medicineInput=rememberPhotoInput({addImages("medicine",it)},{error=it})
 val noteInput=rememberPhotoInput({addImages("note",it)},{error=it})
 val remembered=remember(initial.id){store.courseRecords().sortedByDescending{it.updatedAt}}
 val rememberedTitles=remember(remembered){remembered.map{it.title}.filter{it.isNotBlank()}.distinctBy(::courseTitleMemoryKey)}
 val rememberedChecks=remember(remembered){remembered.map{it.checkText}.filter{it.isNotBlank()}.distinct()}
 val rememberedMedicines=remember(remembered){remembered.map{it.medicineText}.filter{it.isNotBlank()}.distinct()}
 val selectedDate=courseDateMillis(date,initial.date)
 val automaticTitle=selectedDate?.let{automaticCourseTitle(record.copy(date=it,title="",titleAuto=true),remembered)}.orEmpty()
 val editorTitle=if(record.titleAuto)automaticTitle else record.title
 val kilograms=weightText.toDoubleOrNull();val weightAt=weightDateTimeMillis(date,weightTime);val validWeight=weightText.isBlank()||(kilograms!=null&&kilograms>0&&kilograms.isFinite()&&weightAt!=null)
 val valid=selectedDate!=null&&validWeight&&!busy
 val editorScroll=rememberScrollState()
 FullPage(if(exists)"编辑病程记录" else "新增病程记录",onClose,topAction={if(exists)IconButton({deleting=true}){Icon(Icons.Outlined.Delete,"删除病程记录",tint=Bad)}},bottom={Button({
  val saved=record.copy(date=selectedDate!!,title=editorTitle).let{item->if(!item.titleAuto&&Regex("放\\d+").find(item.title)==null&&item.phase in radiotherapyCoursePhases)item.copy(phase="观察") else item}
  onSave(saved,kilograms,if(kilograms==null)null else weightAt)
 },Modifier.fillMaxWidth(),enabled=valid){Text("保存记录",color=Color.White)}}){m->
  Column(m.verticalScroll(editorScroll).padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
   CourseDateField(date){candidate->
    val millis=courseDateMillis(candidate,initial.date)
    val match=millis?.let{target->val day=courseLocalDate(target);store.courseRecords().firstOrNull{it.id!=initial.id&&courseLocalDate(it.date)==day}}
    if(match==null)date=candidate else if(match.isBlankCourseCard()&&store.weightRecordForCourse(match.id)==null)onOpenExisting(match) else duplicateRecord=match
   }
   Text("阶段",fontWeight=FontWeight.Medium)
   Row(Modifier.horizontalScroll(rememberScrollState())){(coursePhases+record.phase).filter{it.isNotBlank()}.distinct().forEach{phase->FilterChip(record.phase==phase,{record=record.copy(phase=phase,title="",titleAuto=true)},label={Text(phase)},modifier=Modifier.padding(end=8.dp),border=null,colors=FilterChipDefaults.filterChipColors(containerColor=Color.White,selectedContainerColor=SelectedTint))}}
   Text("治疗事件",fontWeight=FontWeight.Medium)
   Row{FilterChip(selected=record.growthFactorInjection,onClick={record=record.copy(growthFactorInjection=!record.growthFactorInjection)},label={Text("升白针")},leadingIcon=if(record.growthFactorInjection){{Icon(Icons.Outlined.Vaccines,null,Modifier.size(18.dp))}}else null,border=null,colors=FilterChipDefaults.filterChipColors(containerColor=Color.White,selectedContainerColor=Color(0xFFE3E8ED)))}
   CourseRememberedField(editorTitle,{record=record.copy(title=it,titleAuto=false)},"标题",rememberedTitles)
   CourseEditorHeading(Icons.Outlined.Favorite,"血压-脉搏")
   Column(verticalArrangement=Arrangement.spacedBy(7.dp)){
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)){
   val ranges=listOf("60–100","<90\\n<60","90–119\\n60–79","120–139\\n80–89","≥140\\n≥90")
   ranges.forEachIndexed{index,label->
    Box(Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(8.dp)).background(if(index==0)Color(0xFFF4F4F4) else Color(0xFFEAEAEA)),contentAlignment=Alignment.Center){
     Text(label.replace("\\n","\n"),fontSize=11.sp,lineHeight=14.sp,color=Color(0xFF666666),textAlign=androidx.compose.ui.text.style.TextAlign.Center)
    }
   }
  }
    Column(verticalArrangement=Arrangement.spacedBy(14.dp)){
     PressureReadingEditor(BloodPressureReading(record.pressureTime,record.systolic,record.diastolic,record.pulse),{r->record=record.copy(pressureTime=r.time,systolic=r.systolic,diastolic=r.diastolic,pulse=r.pulse)})
     record.additionalPressures.forEachIndexed{index,reading->
      PressureReadingEditor(reading,{r->record=record.copy(additionalPressures=record.additionalPressures.toMutableList().also{it[index]=r})},{record=record.copy(additionalPressures=record.additionalPressures.filterIndexed{i,_->i!=index})})
     }
    }
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp){
     TextButton({record=record.copy(additionalPressures=record.additionalPressures+BloodPressureReading(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))))},contentPadding=PaddingValues(horizontal=0.dp,vertical=0.dp),modifier=Modifier.height(32.dp)){Icon(Icons.Outlined.Add,null,tint=Color(0xFFFF9163));Text("添加一组血压-脉搏",color=Color(0xFFFF9163))}
    }
    CourseWeightEditor(weightTime,{weightTime=it},weightText,{weightText=sanitizeWeightInput(it)},initialWeight?.heightCmAtMeasurement?:store.patientProfile().heightCm.toDoubleOrNull(),onOpenWeightTrend)
    CourseEditorHeading(Icons.Outlined.MonitorHeart,"症状")
   }
   AppInputField(record.symptomText,{record=record.copy(symptomText=it)},minLines=2,trailingIcon={if(record.symptomText.isNotEmpty())IconButton({record=record.copy(symptomText="")}){Icon(Icons.Outlined.Clear,"清空症状")}})
   PhotoInputButtons(symptomInput,!busy,"相册添加症状图片")
   if(record.symptomImages.isNotEmpty())CourseThumbnails(record.symptomImages,{i->viewingKind="symptom";viewingIndex=i},{i->pendingImageRemoval="symptom" to i},{i->pendingImageReplace="symptom" to i;replaceInput.gallery()})
   CourseEditorHeading(Icons.Outlined.FactCheck,"检查")
   CourseRememberedField(record.checkText,{record=record.copy(checkText=it)},"检查内容",rememberedChecks,2)
   PhotoInputButtons(checkInput,!busy,"相册添加检查图片")
   if(record.checkImages.isNotEmpty())CourseThumbnails(record.checkImages,{i->viewingKind="check";viewingIndex=i},{i->pendingImageRemoval="check" to i},{i->pendingImageReplace="check" to i;replaceInput.gallery()})
   CourseEditorHeading(Icons.Outlined.Medication,"药品 / 取药")
   CourseRememberedField(record.medicineText,{record=record.copy(medicineText=it)},"药品 / 取药内容",rememberedMedicines,2)
   PhotoInputButtons(medicineInput,!busy,"相册添加药品图片")
   if(record.medicineImages.isNotEmpty())CourseThumbnails(record.medicineImages,{i->viewingKind="medicine";viewingIndex=i},{i->pendingImageRemoval="medicine" to i},{i->pendingImageReplace="medicine" to i;replaceInput.gallery()})
   CourseEditorHeading(Icons.Outlined.Notes,"备注")
   AppInputField(record.noteText,{record=record.copy(noteText=it)},minLines=3,trailingIcon={if(record.noteText.isNotEmpty())IconButton({record=record.copy(noteText="")}){Icon(Icons.Outlined.Clear,"清空备注")}})
   PhotoInputButtons(noteInput,!busy,"相册添加备注图片")
   if(record.noteImages.isNotEmpty())CourseThumbnails(record.noteImages,{i->viewingKind="note";viewingIndex=i},{i->pendingImageRemoval="note" to i},{i->pendingImageReplace="note" to i;replaceInput.gallery()})
   if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
   if(error.isNotBlank())Text(error,color=Bad)
  }
 }
 val activeViewingKind=viewingKind
 if(activeViewingKind!=null){
  val currentImages=when(activeViewingKind){"symptom"->record.symptomImages;"check"->record.checkImages;"medicine"->record.medicineImages;else->record.noteImages}
  if(currentImages.isNotEmpty())SourceViewer(
   currentImages,
   {viewingKind=null},
   viewingIndex.coerceIn(currentImages.indices),
   onReplace={i->pendingImageReplace=activeViewingKind to i;replaceInput.gallery()},
   onRemove={i->
    fun removed(items:List<String>)=items.filterIndexed{j,_->j!=i}
    record=when(activeViewingKind){"symptom"->record.copy(symptomImages=removed(record.symptomImages));"check"->record.copy(checkImages=removed(record.checkImages));"medicine"->record.copy(medicineImages=removed(record.medicineImages));else->record.copy(noteImages=removed(record.noteImages))}
    val next=when(activeViewingKind){"symptom"->record.symptomImages;"check"->record.checkImages;"medicine"->record.medicineImages;else->record.noteImages}
    if(next.isEmpty())viewingKind=null else viewingIndex=i.coerceAtMost(next.lastIndex)
   },
   onMove={from,to->
    fun moved(items:List<String>):List<String>{if(from !in items.indices||to !in items.indices)return items;return items.toMutableList().also{val item=it.removeAt(from);it.add(to,item)}}
    record=when(activeViewingKind){"symptom"->record.copy(symptomImages=moved(record.symptomImages));"check"->record.copy(checkImages=moved(record.checkImages));"medicine"->record.copy(medicineImages=moved(record.medicineImages));else->record.copy(noteImages=moved(record.noteImages))}
    viewingIndex=to
   },
   onAdd={when(activeViewingKind){"symptom"->symptomInput.gallery();"check"->checkInput.gallery();"medicine"->medicineInput.gallery();else->noteInput.gallery()}},
   onAddCamera={when(activeViewingKind){"symptom"->symptomInput.camera();"check"->checkInput.camera();"medicine"->medicineInput.camera();else->noteInput.camera()}},
   removalMessage="仅修改当前病程编辑草稿，取消编辑不会更改已保存的原图。"
  )
 }
 if(pendingImageRemoval!=null)AlertDialog(onDismissRequest={pendingImageRemoval=null},title={Text("移除这张图片？")},text={Text("仅从当前病程记录移除，取消编辑不会更改已保存的原图。")},confirmButton={TextButton({val (kind,index)=pendingImageRemoval!!;record=when(kind){"symptom"->record.copy(symptomImages=record.symptomImages.filterIndexed{j,_->j!=index});"check"->record.copy(checkImages=record.checkImages.filterIndexed{j,_->j!=index});"medicine"->record.copy(medicineImages=record.medicineImages.filterIndexed{j,_->j!=index});else->record.copy(noteImages=record.noteImages.filterIndexed{j,_->j!=index})};pendingImageRemoval=null}){Text("移除",color=Bad)}},dismissButton={TextButton({pendingImageRemoval=null}){Text("取消")}})
 if(deleting)AlertDialog(onDismissRequest={deleting=false},title={Text("确认删除这一天的病程？")},text={Text("${courseEditorDate(initial.date)} · ${initial.title.ifBlank{"未填写标题"}}\n删除后原有症状、检查、药品、备注及关联图片记录无法在应用内恢复；自动补齐只会生成空白日期卡片。")},confirmButton={TextButton({deleting=false;onDelete()}){Text("确认永久删除",color=Bad)}},dismissButton={TextButton({deleting=false}){Text("取消")}})
 duplicateRecord?.let{existing->AlertDialog(onDismissRequest={duplicateRecord=null},title={Text("该日期已有病程记录")},text={Text("${courseEditorDate(existing.date)} 已经有填写内容，不能重复新增。")},confirmButton={TextButton({duplicateRecord=null;onOpenExisting(existing)}){Text("打开已有记录")}},dismissButton={TextButton({duplicateRecord=null}){Text("取消")}})}
}

@Composable private fun CourseRememberedField(value:String,onChange:(String)->Unit,label:String,options:List<String>,minLines:Int=1){
 var focused by remember{mutableStateOf(false)}
 val query=value.trim()
 val suggestions=options.filter{it!=query&&(query.isBlank()||it.contains(query,ignoreCase=true))}.take(5)
 Column(Modifier.fillMaxWidth()){
  AppInputField(value,onChange,singleLine=minLines==1,minLines=minLines,trailingIcon={if(value.isNotEmpty())IconButton({onChange("")}){Icon(Icons.Outlined.Clear,"清空$label")}},onFocusedChange={focused=it})
  if(focused&&suggestions.isNotEmpty())Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(bottomStart=12.dp,bottomEnd=12.dp),color=Color.White,shadowElevation=3.dp){
   Column{suggestions.forEachIndexed{index,option->
    Text(option,Modifier.fillMaxWidth().clickable{onChange(option)}.padding(horizontal=14.dp,vertical=11.dp),maxLines=2,overflow=TextOverflow.Ellipsis)
    if(index<suggestions.lastIndex)HorizontalDivider(color=Color(0xFFEDE8E3))
   }}
  }
 }
}

@Composable private fun CourseDateField(value:String,onChange:(String)->Unit){
 val context=LocalContext.current
 Box(Modifier.fillMaxWidth()){
  AppInputField(value,{},placeholder={Text("选择日期")},trailingIcon={Icon(Icons.Outlined.CalendarMonth,null)},readOnly=true,singleLine=true)
  Box(Modifier.matchParentSize().clickable{
   val initial=runCatching{LocalDate.parse(value)}.getOrElse{LocalDate.now()}
   android.app.DatePickerDialog(context,{_,year,month,day->
    onChange(LocalDate.of(year,month+1,day).toString())
   },initial.year,initial.monthValue-1,initial.dayOfMonth).apply{datePicker.maxDate=System.currentTimeMillis()}.show()
  })
 }
}

@Composable private fun CourseEditorHeading(icon:androidx.compose.ui.graphics.vector.ImageVector,title:String){Row(verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=Accent);Spacer(Modifier.width(8.dp));Text(title,fontWeight=FontWeight.Bold,fontSize=18.sp)}}

@Composable private fun CourseWeightEditor(time:String,onTimeChange:(String)->Unit,value:String,onValueChange:(String)->Unit,heightCm:Double?,onOpenTrend:()->Unit){
 Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
  Row(Modifier.fillMaxWidth().clickable(onClick=onOpenTrend).padding(vertical=2.dp).semantics{contentDescription="体重模块标题"},verticalAlignment=Alignment.CenterVertically){
   Icon(Icons.Outlined.MonitorWeight,null,tint=Accent);Spacer(Modifier.width(8.dp));Text("体重",Modifier.weight(1f),fontWeight=FontWeight.Bold,fontSize=18.sp);Icon(Icons.Outlined.ChevronRight,"打开体重趋势",tint=Muted)
  }
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.Top){
   Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)){
    Text("时间",fontSize=11.sp,color=Muted,maxLines=1)
    ClockTimeSelector(time,onTimeChange,Modifier.fillMaxWidth().semantics{contentDescription="体重记录时间"})
   }
   Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)){
    Text("体重",fontSize=11.sp,color=Muted,maxLines=1)
    CompactPressureField(value,onValueChange,Modifier.fillMaxWidth().semantics{contentDescription="体重输入色块"},fill=Color.White)
   }
   Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)){
    Text("体表面积",fontSize=11.sp,color=Muted,maxLines=1)
    Box(Modifier.fillMaxWidth().height((38f*2f/3f).dp).clip(RoundedCornerShape(9.dp)).background(Color.White).semantics{contentDescription="体重编辑体表面积色块"},contentAlignment=Alignment.Center){
     Text(bodySurfaceAreaText(heightCm,value.toDoubleOrNull()),fontSize=17.sp,color=Ink,maxLines=1,softWrap=false)
    }
   }
  }
 }
}

internal fun bodySurfaceAreaSquareMeters(heightCm:Double?,kilograms:Double?):Double?{
 if(heightCm==null||kilograms==null||!heightCm.isFinite()||!kilograms.isFinite()||heightCm<=0.0||kilograms<=0.0)return null
 return 0.007184*Math.pow(heightCm,0.725)*Math.pow(kilograms,0.425)
}
internal fun bodySurfaceAreaText(heightCm:Double?,kilograms:Double?):String=bodySurfaceAreaSquareMeters(heightCm,kilograms)?.let{
 java.math.BigDecimal.valueOf(it).setScale(3,java.math.RoundingMode.HALF_UP).toPlainString()
}?:"—"

internal fun sanitizeWeightInput(input:String):String{
 val filtered=input.replace(',','.').replace('，','.').filter{it.isDigit()||it=='.'}
 val dot=filtered.indexOf('.')
 return if(dot<0)filtered.take(4) else filtered.take(dot).take(4)+"."+filtered.drop(dot+1).filter(Char::isDigit).take(2)
}
private fun courseWeightText(value:Double)=java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
private fun weightTimeText(epochMillis:Long)=Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
private fun weightDateTimeMillis(date:String,time:String):Long?=runCatching{LocalDate.parse(date).atTime(LocalTime.parse(time,DateTimeFormatter.ofPattern("H:mm"))).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()}.getOrNull()

private fun courseDate(epoch:Long):String{val d=Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault());return "${d.monthValue}/${d.dayOfMonth}"}
private fun courseEditorDate(epoch:Long)=Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).toLocalDate().toString()
private fun courseTitleMemoryKey(value:String)=Regex("第[零一二三四五六七八九十百两\\d]+天").replace(value.trim(),"第X天")
private fun courseDateMillis(value:String,original:Long):Long?=runCatching{
 val date=LocalDate.parse(value);val originalTime=Instant.ofEpochMilli(original).atZone(ZoneId.systemDefault()).toLocalTime()
 date.atTime(originalTime).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}.getOrNull()

@Composable private fun LazyScrollProgressRail(state:androidx.compose.foundation.lazy.LazyListState,modifier:Modifier=Modifier){
 var dragging by remember{mutableStateOf(false)};var visible by remember{mutableStateOf(false)};var height by remember{mutableIntStateOf(0)};val scope=rememberCoroutineScope()
 val total=state.layoutInfo.totalItemsCount
 LaunchedEffect(state.isScrollInProgress,dragging,state.firstVisibleItemIndex,state.firstVisibleItemScrollOffset){if(state.isScrollInProgress||dragging)visible=true else{kotlinx.coroutines.delay(850);visible=false}}
 AnimatedVisibility(visible=visible&&total>1,modifier=modifier,enter=fadeIn(),exit=fadeOut()){
  androidx.compose.foundation.Canvas(Modifier.fillMaxSize().onSizeChanged{height=it.height}.pointerInput(total){
   fun seek(y:Float){if(height>0&&total>0)scope.launch{state.scrollToItem(((y/height)*(total-1)).roundToInt().coerceIn(0,total-1))}}
   detectDragGestures(onDragStart={dragging=true;seek(it.y)},onDragEnd={dragging=false},onDragCancel={dragging=false}){change,_->seek(change.position.y)}
  }){val x=size.width/2f;drawLine(Color(0x337B7B82),androidx.compose.ui.geometry.Offset(x,0f),androidx.compose.ui.geometry.Offset(x,size.height),4.dp.toPx());val progress=state.firstVisibleItemIndex.toFloat()/(total-1).coerceAtLeast(1);drawCircle(Accent,7.dp.toPx(),androidx.compose.ui.geometry.Offset(x,(progress*size.height).coerceIn(7.dp.toPx(),size.height-7.dp.toPx())))}
 }
}


@Composable private fun CoursePhaseManager(phases:List<String>,close:()->Unit,update:(List<String>)->Unit){
 var name by rememberSaveable{mutableStateOf("")}
 var draft by remember{mutableStateOf(phases)}
 val rowHeight=with(LocalDensity.current){56.dp.toPx()}
 val candidate=name.trim()
 AlertDialog(onDismissRequest=close,title={Text("管理分类")},text={
  Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
   Text("拖动左侧手柄排序，完成后保存。删除分类不会删除已有病程记录。",fontSize=12.sp,color=Muted)
   Column{
    draft.forEach{phase->key(phase){
     val colors=coursePhaseColors(phase)
     Row(Modifier.fillMaxWidth().height(56.dp),verticalAlignment=Alignment.CenterVertically){
      Icon(Icons.Outlined.DragHandle,"拖动排序：$phase",tint=Muted,modifier=Modifier.size(48.dp).pointerInput(phase){
       var distance=0f
       detectVerticalDragGestures(onDragStart={distance=0f},onDragEnd={distance=0f},onDragCancel={distance=0f}){change,amount->
        change.consume()
        distance+=amount
        while(kotlin.math.abs(distance)>rowHeight/2f){
         val from=draft.indexOf(phase)
         val direction=if(distance>0)1 else -1
         val to=from+direction
         if(from<0||to !in draft.indices){distance=0f;break}
         draft=draft.toMutableList().also{it.add(to,it.removeAt(from))}
         distance-=direction*rowHeight
        }
       }
      })
      Box(Modifier.size(10.dp).background(colors.second,CircleShape))
      Spacer(Modifier.width(10.dp))
      Text(phase,Modifier.weight(1f))
      IconButton({draft=draft-phase}){Icon(Icons.Outlined.Delete,"删除分类：$phase",tint=Bad)}
     }
    }}
   }
   AppInputField(name,{name=it},label="新分类名称",singleLine=true)
   TextButton({draft=draft+candidate;name=""},enabled=candidate.isNotBlank()&&candidate!="全部"&&candidate !in draft){Text("添加分类")}
  }
 },confirmButton={TextButton({update(draft);close()}){Text("完成")}})
}
