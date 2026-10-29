@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.combustible12.healthtrend

import android.os.Bundle
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import java.time.*
import java.time.format.DateTimeFormatter

val Warm=Color(0xFFF6F6F6);val Ink=Color(0xFF292927);val Muted=Color(0xFF817E78)
val Accent=Color(0xFFF28B58);val SelectedTint=Color(0xFFF2ECFF);val Good=Color(0xFF28A957);val Bad=Color(0xFFF04444);val TrendBlue=Color(0xFF3F7FE8);val TreatmentMarkerGray=Color(0xFF8E9399)
data class TrendPreviewRequest(val points:List<Pair<Long,Double>>,val color:Color,val referenceBands:List<Pair<Double?,Double?>>,val segmentKeys:List<String>,val segmentColors:List<Color>,val metricKey:String,val referenceNotes:List<String>,val timelineDates:List<Long>,val eventDates:List<Long>)
private fun hospitalColorMap(keys:List<String>,abnormal:Boolean):Map<String,Color> =
 keys.mapIndexed{slot,key->trendHospitalKey(key) to Color(hospitalPaletteArgb(slot,abnormal))}.toMap()
private val stamp=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
fun dateText(n:Long)=Instant.ofEpochMilli(n).atZone(ZoneId.systemDefault()).format(stamp)
private tailrec fun Context.findActivity():Activity?=when(this){is Activity->this;is ContextWrapper->baseContext.findActivity();else->null}
fun normalizeDateText(s:String):String? {
 val m=Regex("^(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2})(?:\\s+(\\d{1,2}):(\\d{2}))?$").matchEntire(s.trim())?:return null
 return runCatching{
  val date=LocalDate.of(m.groupValues[1].toInt(),m.groupValues[2].toInt(),m.groupValues[3].toInt())
  if(m.groupValues[4].isBlank())date.toString()
  else LocalDateTime.of(date,LocalTime.of(m.groupValues[4].toInt(),m.groupValues[5].toInt())).format(stamp)
 }.getOrNull()
}
fun parseDate(s:String):Long?=normalizeDateText(s)?.let{normalized->runCatching{if(normalized.length==10)LocalDate.parse(normalized).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()else LocalDateTime.parse(normalized,stamp).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()}.getOrNull()}
fun preserveTimestamp(input:String,original:Long?):Long?=if(original!=null&&input==dateText(original))original else parseDate(input)
fun ResultStatus.label()=when(this){ResultStatus.LOW->"偏低";ResultStatus.HIGH->"偏高";ResultStatus.NORMAL->"范围内";ResultStatus.UNKNOWN->"待判断"}
fun rangeText(low:Double?,high:Double?)=when{low!=null&&high!=null->"$low–$high";low!=null->"≥$low";high!=null->"≤$high";else->"未录入"}
fun statusColor(s:ResultStatus)=when(s){ResultStatus.LOW,ResultStatus.HIGH->Bad;ResultStatus.NORMAL->Good;else->Muted}
class MainActivity:ComponentActivity(){override fun onCreate(b:Bundle?){super.onCreate(b);enableEdgeToEdge();setContent{MaterialTheme(colorScheme=lightColorScheme(background=Warm,surface=Color.White,primary=Accent,onPrimary=Ink,onSurface=Ink)){App()}}}}

@Composable fun App(){
 val ctx=LocalContext.current;val store=remember{HealthStore(ctx)};val draftStore=remember{ReportDraftStore(ctx)}
 var revision by remember{mutableIntStateOf(0)};var error by remember{mutableStateOf<String?>(null)}
 var tab by rememberSaveable{mutableIntStateOf(2)};var recordsFilter by rememberSaveable{mutableStateOf("全部")}
 var recordsOpen by rememberSaveable{mutableStateOf(false)};var draftsOpen by rememberSaveable{mutableStateOf(false)};var deleteDraftId by remember{mutableStateOf<String?>(null)}
 var replacementReport by remember{mutableStateOf<LabReport?>(null)};var replacementDraftId by remember{mutableStateOf<String?>(null)};var replacementCandidates by remember{mutableStateOf<List<LabReport>>(emptyList())};var selectedReplacementId by remember{mutableStateOf<String?>(null)}
 var draft by rememberSaveable(stateSaver=diskStateSaver<ReportDraft?>(ctx,"root-report")){mutableStateOf<ReportDraft?>(null)};var report by rememberSaveable(stateSaver=diskStateSaver<LabReport?>(ctx,"root-report-view")){mutableStateOf<LabReport?>(null)}
 var entry by rememberSaveable(stateSaver=diskStateSaver<HealthEntry?>(ctx,"root-entry")){mutableStateOf<HealthEntry?>(null)};var viewer by rememberSaveable{mutableStateOf<List<String>?>(null)};var viewerStart by rememberSaveable{mutableIntStateOf(0)}
 var imageDocument by rememberSaveable(stateSaver=diskStateSaver<ImageDocument?>(ctx,"root-image-document")){mutableStateOf<ImageDocument?>(null)}
 var courseRecord by rememberSaveable(stateSaver=diskStateSaver<CourseRecord?>(ctx,"root-course-record")){mutableStateOf<CourseRecord?>(null)}
 var trendCategoryRequest by remember{mutableStateOf<String?>(null)}
 var trendPreview by remember{mutableStateOf<TrendPreviewRequest?>(null)}
 var foregroundSequence by remember{mutableIntStateOf(0)}
 var snoozedReminderIds by remember{mutableStateOf<Set<String>>(emptySet())}
 val lifecycleOwner=LocalLifecycleOwner.current
 DisposableEffect(lifecycleOwner){
  val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_START){snoozedReminderIds=emptySet();foregroundSequence++}}
  lifecycleOwner.lifecycle.addObserver(observer)
  onDispose{lifecycleOwner.lifecycle.removeObserver(observer)}
 }
 var rememberedDocumentPages by rememberSaveable{mutableStateOf<List<String>>(emptyList())}
 fun rememberedPage(id:String):Int=rememberedDocumentPages.firstOrNull{it.startsWith("$id:")}?.substringAfterLast(":")?.toIntOrNull()?:0
 fun rememberPage(id:String,index:Int){rememberedDocumentPages=(rememberedDocumentPages.filterNot{it.startsWith("$id:")}+"$id:$index").takeLast(300)}
 var imageDocumentPage by rememberSaveable{mutableIntStateOf(0)};var imageDocumentMatches by rememberSaveable{mutableStateOf<List<Int>>(emptyList())}
 var attachReportId by remember{mutableStateOf<String?>(null)}
 var viewerReportId by remember{mutableStateOf<String?>(null)}
 var viewerCourseRecordId by remember{mutableStateOf<String?>(null)}
 var viewerCourseKind by remember{mutableStateOf<String?>(null)}
 var replaceImageIndex by remember{mutableStateOf<Int?>(null)}
 val attachScope=rememberCoroutineScope()
 var attachingReportImage by remember{mutableStateOf(false)}
 val replacePicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
  val id=viewerReportId;val courseId=viewerCourseRecordId;val courseKind=viewerCourseKind;val index=replaceImageIndex;replaceImageIndex=null
  if(uri!=null&&id==null&&courseId!=null&&courseKind!=null&&index!=null&&!attachingReportImage){
   attachingReportImage=true
   attachScope.launch{try{
    val owned=withContext(kotlinx.coroutines.Dispatchers.IO){store.ownImage(uri)}
    val current=store.courseRecords().firstOrNull{it.id==courseId}?:error("病程记录不存在")
    fun changed(items:List<String>)=items.toMutableList().also{require(index in it.indices);it[index]=owned}
    val updated=when(courseKind){"symptom"->current.copy(symptomImages=changed(current.symptomImages));"check"->current.copy(checkImages=changed(current.checkImages));"medicine"->current.copy(medicineImages=changed(current.medicineImages));else->current.copy(noteImages=changed(current.noteImages))}
    store.saveCourseRecord(updated);revision++
    viewer=when(courseKind){"symptom"->updated.symptomImages;"check"->updated.checkImages;"medicine"->updated.medicineImages;else->updated.noteImages};viewerStart=index
   }catch(e:Exception){error="更换图片失败：${e.message}"}finally{attachingReportImage=false}}
  }
  if(uri!=null&&id!=null&&index!=null&&!attachingReportImage){
   attachingReportImage=true
   attachScope.launch{
    try{
     withContext(kotlinx.coroutines.Dispatchers.IO){store.replaceReportImage(id,index,uri)}
     revision++
     viewer=store.reports().firstOrNull{it.id==id}?.sourceImages?.map{it.uri}
     viewerStart=index
    }catch(e:Exception){error="更换原图失败：${e.message}"}
    finally{attachingReportImage=false}
   }
  }
 }
 val attachPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){uris->
  val id=attachReportId
  attachReportId=null
  if(id!=null&&uris.isNotEmpty()){
   attachingReportImage=true
   attachScope.launch{
    try{withContext(kotlinx.coroutines.Dispatchers.IO){store.addReportImages(id,uris)};revision++;if(viewerReportId==id){viewer=store.reports().firstOrNull{it.id==id}?.sourceImages?.map{it.uri};viewerStart=(viewer?.lastIndex?:0).coerceAtLeast(0)}}
    catch(e:Exception){error="原图保存失败：${e.message}"}
    finally{attachingReportImage=false}
   }
  }
 }
 val viewerAddInput=rememberPhotoInput({uris->
  if(uris.isNotEmpty()&&!attachingReportImage){attachingReportImage=true;attachScope.launch{try{
   val reportId=viewerReportId;val courseId=viewerCourseRecordId;val kind=viewerCourseKind
   if(reportId!=null){
    withContext(kotlinx.coroutines.Dispatchers.IO){store.addReportImages(reportId,uris)};revision++
    viewer=store.reports().firstOrNull{it.id==reportId}?.sourceImages?.map{it.uri};viewerStart=(viewer?.lastIndex?:0).coerceAtLeast(0)
   }else if(courseId!=null&&kind!=null){
    val owned=withContext(kotlinx.coroutines.Dispatchers.IO){uris.map{store.ownImage(it)}}
    val current=store.courseRecords().firstOrNull{it.id==courseId}?:error("病程记录不存在")
    val updated=when(kind){"symptom"->current.copy(symptomImages=current.symptomImages+owned);"check"->current.copy(checkImages=current.checkImages+owned);"medicine"->current.copy(medicineImages=current.medicineImages+owned);else->current.copy(noteImages=current.noteImages+owned)}
    store.saveCourseRecord(updated);revision++
    viewer=when(kind){"symptom"->updated.symptomImages;"check"->updated.checkImages;"medicine"->updated.medicineImages;else->updated.noteImages};viewerStart=(viewer?.lastIndex?:0).coerceAtLeast(0)
   }
  }catch(e:Exception){error="图片保存失败：${e.message}"}finally{attachingReportImage=false}}}
 },{error=it})
 val reports=remember(revision){runCatching{store.reports()}.getOrElse{error="历史数据读取失败：${it.message}";emptyList()}}
 val entries=remember(revision){runCatching{store.entries()}.getOrElse{error="历史记录读取失败：${it.message}";emptyList()}}
 val courseRecords=remember(revision){runCatching{store.courseRecords()}.getOrElse{error="病程记录读取失败：${it.message}";emptyList()}}
 val treatmentReminders=remember(revision){runCatching{store.treatmentReminders()}.getOrElse{error="提醒读取失败：${it.message}";emptyList()}}
 LaunchedEffect(tab){if(tab==2)runCatching{store.ensureCourseRecordsThroughToday()}.onSuccess{added->if(added>0)revision++}.onFailure{error="病程日期补齐失败：${it.message}"}}
 LaunchedEffect(Unit){runCatching{store.ensureCourseRecordsThroughToday()}.onSuccess{added->if(added>0)revision++}.onFailure{error="病程日期补齐失败：${it.message}"}}
 fun change(block:()->Unit){try{block();revision++}catch(e:Exception){error=e.message?:"操作失败"}}
 val importer=rememberReportImport({draft=it},{error=it},deliver=!recordsOpen&&draft==null&&report==null&&entry==null&&imageDocument==null&&courseRecord==null&&viewer==null)
 Box(Modifier.fillMaxSize()){
 Box(Modifier.fillMaxSize().then(if(recordsOpen||draft!=null||report!=null||entry!=null||imageDocument!=null||courseRecord!=null||viewer!=null||trendPreview!=null)Modifier.clearAndSetSemantics{} else Modifier)){
 Scaffold(containerColor=Warm,bottomBar={NavigationBar(containerColor=Color.White){listOf("导入" to Icons.Outlined.Home,"趋势" to Icons.Outlined.ShowChart,"病程" to Icons.Outlined.Timeline,"图片资料" to Icons.Outlined.PhotoLibrary,"我的" to Icons.Outlined.Person).forEachIndexed{i,p->NavigationBarItem(selected=tab==i,onClick={tab=i},icon={Icon(p.second,p.first)},label={Text(p.first,fontSize=11.sp)},alwaysShowLabel=true)}}}){padding->
  val m=Modifier.padding(padding)
  var mainPageDrag by remember{mutableFloatStateOf(0f)}
  Box(Modifier.fillMaxSize().pointerInput(tab){
   if(tab!=1)detectHorizontalDragGestures(
    onDragStart={mainPageDrag=0f},
    onHorizontalDrag={_,amount->mainPageDrag+=amount},
    onDragCancel={mainPageDrag=0f},
    onDragEnd={
     if(kotlin.math.abs(mainPageDrag)>90f)tab=(tab+if(mainPageDrag<0)1 else -1).coerceIn(0,4)
     mainPageDrag=0f
    }
   )
  }){
  when(tab){
   0->Home(m,reports,entries,importer,treatmentReminders,{reminder->change{store.saveTreatmentReminder(reminder)}},{id->change{store.deleteTreatmentReminder(id)}},{kind->if(kind==null)tab=1 else entry=HealthEntry(kind=kind,title="",occurredAtEpochMillis=System.currentTimeMillis())},{report=it},{tab=2})
   1->Trends(m,store,reports,revision,{key,value->change{store.setPrimary(key,value)}},{uris->viewerReportId=reports.firstOrNull{r->r.sourceImages.map{it.uri}==uris||r.sourceImages.any{it.uri in uris}}?.id;viewerCourseRecordId=null;viewer=uris},{r,x,v->change{store.updateValue(r.id,x.id,v)}},{testedAt->
    val zone=java.time.ZoneId.systemDefault();val day=java.time.Instant.ofEpochMilli(testedAt).atZone(zone).toLocalDate()
    courseRecord=courseRecords.firstOrNull{java.time.Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate()==day}
     ?:CourseRecord(date=day.atStartOfDay(zone).toInstant().toEpochMilli(),title="")
   },onMainPageSwipe={direction->tab=(tab+direction).coerceIn(0,4)},resultChanged={revision++},requestedCategory=trendCategoryRequest,onRequestedCategoryConsumed={trendCategoryRequest=null},showPreview={trendPreview=it})
    2->CourseRecordsPage(m,courseRecords,store,{courseRecord=it},{courseRecord=CourseRecord(date=0L,title="")},{record->change{store.setWeightForCourse(record.id,record.date,null);store.deleteCourseRecord(record.id);store.ensureCourseRecordsThroughToday()}},{images,index->viewerReportId=null;viewerCourseRecordId=courseRecords.firstOrNull{it.symptomImages==images||it.checkImages==images||it.medicineImages==images||it.noteImages==images}?.id;viewerCourseKind=courseRecords.firstOrNull{it.id==viewerCourseRecordId}?.let{when(images){it.symptomImages->"symptom";it.checkImages->"check";it.medicineImages->"medicine";it.noteImages->"note";else->null}};viewer=images;viewerStart=index},{trendCategoryRequest="体重";tab=1})
    3->ImageDocumentsPage(m){document,page,matches->imageDocument=document;imageDocumentPage=if(matches.isNotEmpty()||page!=0)page else rememberedPage(document.id).coerceIn(document.pages.indices);imageDocumentMatches=matches}
    4->Mine(m,store,{error=it},{revision++},{recordsOpen=true})
  }
  }
 }
 }
 CompositionLocalProvider(LocalPageVisible provides (viewer==null)){
 if(recordsOpen)FullPage("记录",{recordsOpen=false},hidden=draft!=null||draftsOpen||report!=null||entry!=null||viewer!=null,topAction={TextButton({draftsOpen=true}){Text("待核对草稿")}}){m->
  Records(m,reports,entries,recordsFilter,{recordsFilter=it},{report=it},{r->viewerReportId=r.id;viewer=r.sourceImages.map{it.uri}},{r->if(!attachingReportImage){attachReportId=r.id;attachPicker.launch(arrayOf("image/*"))}},{entry=it},{kind->entry=HealthEntry(kind=kind,title="",occurredAtEpochMillis=System.currentTimeMillis())},title="",deleteReport={r->change{store.deleteReport(r.id)}})
 }
 if(draftsOpen)FullPage("待核对草稿",{draftsOpen=false},hidden=draft!=null){m->
  Column(m.verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
   val stored=remember(revision,draftsOpen){draftStore.all()}
   if(stored.isEmpty())Paper{Text("还没有暂存的报告")}
   var draftSearch by rememberSaveable{mutableStateOf("")}
   DataSearchField(draftSearch,{draftSearch=it},"搜索草稿文字和数据")
   stored.filter{DataSearch.draft(draftSearch,it)}.forEach{item->
    Paper{
     Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
      Column(Modifier.weight(1f)){
       Text(item.hospital.ifBlank{"医院待核对"}+" · "+item.type,fontWeight=FontWeight.Bold)
       Text(item.date+" · "+item.rows.size+" 个项目",color=Muted,fontSize=12.sp)
      }
      TextButton({draft=item}){Text("继续核对")}
      TextButton({deleteDraftId=item.draftId}){Text("删除",color=Bad)}
     }
    }
   }
  }
 }
 if(deleteDraftId!=null)AlertDialog(onDismissRequest={deleteDraftId=null},
  title={Text("删除待核对草稿？")},
  text={Text("只删除草稿，不会删除正式报告或已有原始图片。")},
  confirmButton={TextButton({try{draftStore.delete(deleteDraftId!!);revision++;deleteDraftId=null}catch(e:Exception){error=e.message}}){Text("确认删除",color=Bad)}},
  dismissButton={TextButton({deleteDraftId=null}){Text("取消")}})
 if(replacementReport!=null&&replacementCandidates.isNotEmpty())AlertDialog(
  onDismissRequest={replacementReport=null;replacementCandidates=emptyList();selectedReplacementId=null},
  title={Text("替换旧报告？")},
  text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
   Text("新报告已核对完成。请选择要替换的同医院、同日期、同检查类型旧报告。确认后旧报告退出列表和趋势，但会封存并可在“我的”中恢复。")
   replacementCandidates.forEach{old->Row(Modifier.fillMaxWidth().clickable{selectedReplacementId=old.id},verticalAlignment=Alignment.CenterVertically){
    RadioButton(selectedReplacementId==old.id,{selectedReplacementId=old.id});Column{Text("${old.hospitalKey} · ${old.reportType}");Text("${dateText(old.testedAtEpochMillis)} · ${old.results.size}项",color=Muted,fontSize=12.sp)}
   }}
  }},
  confirmButton={TextButton({val fresh=replacementReport;val oldId=selectedReplacementId;val draftId=replacementDraftId;if(fresh!=null&&oldId!=null)change{store.replaceReportWithConfirmed(oldId,fresh);if(draftId!=null)draftStore.delete(draftId);replacementReport=null;replacementCandidates=emptyList();selectedReplacementId=null;replacementDraftId=null;draft=null}},enabled=selectedReplacementId!=null){Text("确认替换")}},
  dismissButton={TextButton({replacementReport=null;replacementCandidates=emptyList();selectedReplacementId=null;replacementDraftId=null}){Text("返回核对")}}
 )
 if(draft!=null)ReportEditor(draft!!,store,{draft=null},{d->change{
   val saved=materializeReportDraft(d,store)
   val candidates=if(d.existing==null)store.replacementCandidates(saved)else emptyList()
   if(candidates.isNotEmpty()){
    replacementReport=saved;replacementDraftId=d.draftId;replacementCandidates=candidates;selectedReplacementId=candidates.singleOrNull()?.id
   }else{
    store.saveReport(saved);draftStore.delete(d.draftId);draft=null
   }
  }},{viewer=it},{pending->change{draftStore.save(pending);draft=null;draftsOpen=true}})
 if(report!=null){val current=reports.firstOrNull{it.id==report!!.id}?:report!!;ReportDetail(current,store,{report=null},{draft=ReportDraft.from(current);report=null},{viewerReportId=current.id;viewer=it},{change{store.deleteReport(current.id);report=null}},{result,value,text->change{store.updateValue(current.id,result.id,value,text)}},{revision++})}
 if(entry!=null)EntryEditor(entry!!,store,{entry=null},{e->change{store.saveEntry(e);entry=null}},{change{store.deleteEntry(entry!!.id);entry=null}},{viewer=it})
 if(imageDocument!=null)ImageDocumentViewer(imageDocument!!,imageDocumentPage,imageDocumentMatches,{imageDocument=null},{saved->ImageDocumentStore(ctx).save(saved);imageDocument=saved},{deleted->ImageDocumentStore(ctx).delete(deleted);rememberedDocumentPages=rememberedDocumentPages.filterNot{it.startsWith("${deleted.id}:")};imageDocument=null},{index->imageDocument?.let{rememberPage(it.id,index)}})
 if(courseRecord!=null){val current=courseRecords.firstOrNull{it.id==courseRecord!!.id}?:courseRecord!!;CourseRecordEditor(current,store,{courseRecord=null},{saved,kilograms,weightAt->change{store.saveCourseRecord(saved);store.setWeightForCourse(saved.id,weightAt?:saved.date,kilograms);courseRecord=null}},{change{store.setWeightForCourse(current.id,current.date,null);store.deleteCourseRecord(current.id);store.ensureCourseRecordsThroughToday();courseRecord=null}},{images,index->viewerReportId=null;viewerCourseRecordId=null;viewerCourseKind=null;viewer=images;viewerStart=index},onOpenWeightTrend={courseRecord=null;trendCategoryRequest="体重";tab=1},onOpenExisting={existing->courseRecord=existing})}
 val radiationNumber=remember(courseRecords){currentRadiotherapyNumber(courseRecords)}
 val dueTreatmentReminder=remember(treatmentReminders,radiationNumber,foregroundSequence,snoozedReminderIds){nextDueTreatmentReminder(treatmentReminders,radiationNumber,snoozedReminderIds)}
 if(dueTreatmentReminder!=null)TreatmentReminderDialog(dueTreatmentReminder,{snoozedReminderIds=snoozedReminderIds+dueTreatmentReminder.id},{change{store.saveTreatmentReminder(dueTreatmentReminder.copy(completedAt=System.currentTimeMillis()))}})
 }
 if(viewer!=null)SourceViewer(viewer!!,{viewer=null;viewerReportId=null;viewerCourseRecordId=null;viewerCourseKind=null},viewerStart,
  onReplace=if(viewerReportId!=null||viewerCourseRecordId!=null){{index->replaceImageIndex=index;replacePicker.launch(arrayOf("image/*"))}}else null,
  onRemove=if(viewerReportId!=null){{index->
   val id=viewerReportId!!
   change{store.removeReportImage(id,index)}
   val images=store.reports().firstOrNull{it.id==id}?.sourceImages?.map{it.uri}.orEmpty()
   if(images.isEmpty()){viewer=null;viewerReportId=null}else{viewer=images;viewerStart=index.coerceAtMost(images.lastIndex)}
  }}else if(viewerCourseRecordId!=null){{index->
   val id=viewerCourseRecordId!!;val kind=viewerCourseKind!!
   change{
    val current=store.courseRecords().first{it.id==id}
    val updated=when(kind){"symptom"->current.copy(symptomImages=current.symptomImages.filterIndexed{i,_->i!=index});"check"->current.copy(checkImages=current.checkImages.filterIndexed{i,_->i!=index});"medicine"->current.copy(medicineImages=current.medicineImages.filterIndexed{i,_->i!=index});else->current.copy(noteImages=current.noteImages.filterIndexed{i,_->i!=index})}
    store.saveCourseRecord(updated)
    val images=when(kind){"symptom"->updated.symptomImages;"check"->updated.checkImages;"medicine"->updated.medicineImages;else->updated.noteImages}
    if(images.isEmpty()){viewer=null;viewerCourseRecordId=null;viewerCourseKind=null}else{viewer=images;viewerStart=index.coerceAtMost(images.lastIndex)}
   }
  }}else null,
  onMove=if(viewerReportId!=null){{from,to->
   val id=viewerReportId!!
   change{store.moveReportImage(id,from,to)}
   viewer=store.reports().firstOrNull{it.id==id}?.sourceImages?.map{it.uri}
   viewerStart=to
  }}else if(viewerCourseRecordId!=null&&viewerCourseKind!=null){{from,to->
   val id=viewerCourseRecordId!!;val kind=viewerCourseKind!!
   change{
    val current=store.courseRecords().first{it.id==id}
    fun moved(items:List<String>):List<String>{if(from !in items.indices||to !in items.indices)return items;return items.toMutableList().also{val item=it.removeAt(from);it.add(to,item)}}
    val updated=when(kind){"symptom"->current.copy(symptomImages=moved(current.symptomImages));"check"->current.copy(checkImages=moved(current.checkImages));"medicine"->current.copy(medicineImages=moved(current.medicineImages));else->current.copy(noteImages=moved(current.noteImages))}
    store.saveCourseRecord(updated)
    viewer=when(kind){"symptom"->updated.symptomImages;"check"->updated.checkImages;"medicine"->updated.medicineImages;else->updated.noteImages};viewerStart=to
   }
  }}else null,
  onAdd=if(viewerReportId!=null||viewerCourseRecordId!=null){{viewerAddInput.gallery()}}else null,
  onAddCamera=if(viewerReportId!=null||viewerCourseRecordId!=null){{viewerAddInput.camera()}}else null,
  reportInfo=viewerReportId?.let{id->reports.firstOrNull{it.id==id}},
  removalMessage=if(viewerReportId!=null)"仅删除这份报告中的图片关联，不影响检查数据和趋势。" else "仅从这条病程记录移除当前图片，不影响其他内容。"
 )
 trendPreview?.let{preview->TrendPreviewDialog(preview.points,preview.color,preview.referenceBands,preview.segmentKeys,preview.segmentColors,preview.metricKey,{trendPreview=null},preview.referenceNotes,preview.timelineDates,preview.eventDates)}
 if(error!=null)AlertDialog(onDismissRequest={error=null},title={Text("操作未完成")},text={Text(error!!)},confirmButton={TextButton({error=null}){Text("知道了")}})
 }
}

internal fun nextDueTreatmentReminder(reminders:List<TreatmentReminder>,radiationNumber:Int,snoozedIds:Set<String> = emptySet()):TreatmentReminder?=
 reminders.filter{it.completedAt==null&&it.radiationNumber<=radiationNumber&&it.id !in snoozedIds}.minWithOrNull(compareBy<TreatmentReminder>{it.radiationNumber}.thenBy{it.createdAt})
@Composable fun Screen(m:Modifier,title:String,subtitle:String="",spacing:androidx.compose.ui.unit.Dp=14.dp,content:@Composable ColumnScope.()->Unit){
 val scroll=rememberScrollState();val scope=rememberCoroutineScope()
 Box(m.fillMaxSize()){
  Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(20.dp),verticalArrangement=Arrangement.spacedBy(spacing)){
   Spacer(Modifier.height(6.dp));if(title.isNotBlank())Text(title,fontSize=28.sp,fontWeight=FontWeight.Bold);if(subtitle.isNotBlank())Text(subtitle,color=Muted);content()
   if(scroll.maxValue>0)TextButton({scope.launch{scroll.animateScrollTo(0)}},Modifier.align(Alignment.CenterHorizontally)){Icon(Icons.Outlined.VerticalAlignTop,null);Spacer(Modifier.width(6.dp));Text("回到顶部")}
   Spacer(Modifier.height(12.dp))
  }
  ScrollProgressRail(scroll,Modifier.align(Alignment.CenterEnd).padding(top=20.dp,bottom=20.dp,end=2.dp).width(24.dp).fillMaxHeight())
 }
}

@Composable fun ScrollablePageColumn(modifier:Modifier=Modifier,padding:PaddingValues=PaddingValues(0.dp),arrangement:Arrangement.Vertical=Arrangement.Top,content:@Composable ColumnScope.()->Unit){
 val scroll=rememberScrollState();val scope=rememberCoroutineScope()
 Box(modifier){
  Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(padding),verticalArrangement=arrangement){content();if(scroll.maxValue>0)TextButton({scope.launch{scroll.animateScrollTo(0)}},Modifier.align(Alignment.CenterHorizontally)){Icon(Icons.Outlined.VerticalAlignTop,null);Spacer(Modifier.width(6.dp));Text("回到顶部")}}
  ScrollProgressRail(scroll,Modifier.align(Alignment.CenterEnd).padding(top=12.dp,bottom=12.dp,end=2.dp).width(24.dp).fillMaxHeight())
 }
}

@Composable fun ScrollProgressRail(scroll:ScrollState,modifier:Modifier=Modifier){
 val scope=rememberCoroutineScope();var size by remember{mutableStateOf(IntSize.Zero)};var dragging by remember{mutableStateOf(false)};var visible by remember{mutableStateOf(false)}
 LaunchedEffect(scroll.isScrollInProgress,dragging,scroll.value){
  if(scroll.isScrollInProgress||dragging)visible=true else{delay(850);visible=false}
 }
 AnimatedVisibility(visible=visible&&scroll.maxValue>0,modifier=modifier,enter=fadeIn(),exit=fadeOut()){
  Canvas(Modifier.fillMaxSize().onSizeChanged{size=it}.pointerInput(scroll.maxValue){
   fun seek(y:Float){if(size.height>0)scope.launch{scroll.scrollTo((y/size.height*scroll.maxValue).roundToInt().coerceIn(0,scroll.maxValue))}}
   detectDragGestures(onDragStart={dragging=true;seek(it.y)},onDragEnd={dragging=false},onDragCancel={dragging=false}){change,_->change.consume();seek(change.position.y)}
  }){
   val x=this.size.width/2f;drawLine(Color(0x337B7B82),Offset(x,0f),Offset(x,this.size.height),4.dp.toPx())
   val y=(scroll.value.toFloat()/scroll.maxValue.coerceAtLeast(1))*this.size.height
   drawCircle(Accent,7.dp.toPx(),Offset(x,y.coerceIn(7.dp.toPx(),this.size.height-7.dp.toPx())))
  }
 }
}
@Composable private fun TreatmentReminderDialog(reminder:TreatmentReminder,onLater:()->Unit,onComplete:()->Unit){
 Dialog(onDismissRequest=onLater){
  Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(30.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFFFFF4EC))){
   Column(Modifier.padding(horizontal=28.dp,vertical=30.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(18.dp)){
    Surface(shape=RoundedCornerShape(24.dp),color=Color.White){Icon(Icons.Outlined.NotificationsActive,"治疗提醒",Modifier.padding(18.dp).size(42.dp),tint=Accent)}
    Text("放${reminder.radiationNumber}提醒",fontSize=20.sp,color=Accent,fontWeight=FontWeight.Bold)
    Text(reminder.text,fontSize=30.sp,lineHeight=38.sp,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center)
    Button(onComplete,Modifier.fillMaxWidth().height(54.dp)){Text("已完成",fontSize=17.sp,color=Color.White)}
    TextButton(onLater,Modifier.fillMaxWidth()){Text("稍后再提醒",color=Muted)}
   }
  }
 }
}
@Composable fun Paper(m:Modifier=Modifier,content:@Composable ColumnScope.()->Unit){Card(modifier=m.fillMaxWidth(),shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content)}}
@Composable fun TrendPaper(content:@Composable ColumnScope.()->Unit){Card(modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){Column(Modifier.padding(start=18.dp,end=18.dp,top=18.dp,bottom=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp),content=content)}}
@Composable fun Home(m:Modifier,reports:List<LabReport>,entries:List<HealthEntry>,importer:ImportActions,reminders:List<TreatmentReminder>,saveReminder:(TreatmentReminder)->Unit,deleteReminder:(String)->Unit,quick:(EntryKind?)->Unit,open:(LabReport)->Unit,timeline:()->Unit){
 var showPaste by rememberSaveable{mutableStateOf(false)};var pastedText by rememberSaveable{mutableStateOf("")}
 Screen(m,"导入数据",""){
 Card(shape=RoundedCornerShape(28.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFFFFEEE5))){Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text("报告数据",fontSize=21.sp,fontWeight=FontWeight.Bold);Button({showPaste=true},Modifier.fillMaxWidth().height(54.dp),shape=RoundedCornerShape(18.dp),enabled=!importer.busy){Text("粘贴报告数据",color=Color.White)};Row(Modifier.horizontalScroll(rememberScrollState())){TextButton(importer.manual,enabled=!importer.busy){Text("手动录入")}};if(importer.busy)LinearProgressIndicator(Modifier.fillMaxWidth());if(importer.message.isNotBlank())Text(importer.message,fontSize=12.sp)}}
 TreatmentReminderPanel(reminders,saveReminder,deleteReminder)
 }
 if(showPaste)AlertDialog(onDismissRequest={showPaste=false},title={Text("数据")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){AppInputField(pastedText,{pastedText=it},minHeight=220.dp,minLines=8)}},confirmButton={TextButton({importer.paste(pastedText);showPaste=false;pastedText=""},enabled=pastedText.isNotBlank()&&!importer.busy){Text("进入核对")}},dismissButton={TextButton({showPaste=false}){Text("取消")}})
}
@Composable private fun TreatmentReminderPanel(reminders:List<TreatmentReminder>,save:(TreatmentReminder)->Unit,delete:(String)->Unit){
 var editing by remember{mutableStateOf<TreatmentReminder?>(null)}
 var adding by remember{mutableStateOf(false)}
 var showCompleted by rememberSaveable{mutableStateOf(false)}
 val pending=reminders.filter{it.completedAt==null};val completed=reminders.filter{it.completedAt!=null}
 Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){
  Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
   Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Outlined.Notifications,null,tint=Accent);Spacer(Modifier.width(8.dp));Text("提醒",Modifier.weight(1f),fontSize=20.sp,fontWeight=FontWeight.Bold);IconButton({adding=true}){Icon(Icons.Outlined.Add,"添加提醒",tint=Accent)}}
   if(pending.isEmpty())Text("暂无待提醒",fontSize=13.sp,color=Muted)
   pending.forEach{item->Row(Modifier.fillMaxWidth().clickable{editing=item}.padding(vertical=7.dp),verticalAlignment=Alignment.CenterVertically){Text("放${item.radiationNumber}",fontWeight=FontWeight.Bold,color=Accent);Spacer(Modifier.width(12.dp));Text(item.text,Modifier.weight(1f),maxLines=2,overflow=TextOverflow.Ellipsis);Icon(Icons.Outlined.ChevronRight,null,tint=Muted)}}
   if(completed.isNotEmpty()){TextButton({showCompleted=!showCompleted},contentPadding=PaddingValues(0.dp)){Text(if(showCompleted)"收起已完成" else "已完成 ${completed.size}",color=Muted)};if(showCompleted)completed.forEach{item->Row(Modifier.fillMaxWidth().clickable{editing=item}.padding(vertical=6.dp)){Text("放${item.radiationNumber} · ${item.text}",color=Muted)}}}
  }
 }
 val target=editing?:if(adding)TreatmentReminder(radiationNumber=1,text="") else null
 if(target!=null)TreatmentReminderEditor(target,{editing=null;adding=false},save,delete)
}
@Composable private fun TreatmentReminderEditor(initial:TreatmentReminder,close:()->Unit,save:(TreatmentReminder)->Unit,delete:(String)->Unit){
 var number by remember(initial.id){mutableStateOf(initial.radiationNumber.toString())};var text by remember(initial.id){mutableStateOf(initial.text)}
 val existing=initial.text.isNotBlank()
 AlertDialog(onDismissRequest=close,title={Text(if(existing)"编辑提醒" else "添加提醒")},text={Column(verticalArrangement=Arrangement.spacedBy(14.dp)){
  AppInputField(number,{number=it.filter(Char::isDigit).take(3)},label="放疗次数",prefix={Text("放")},singleLine=true)
  AppInputField(text,{text=it},label="提醒内容",minLines=2)
 }},confirmButton={TextButton({save(initial.copy(radiationNumber=number.toIntOrNull()?:0,text=text,completedAt=if(initial.radiationNumber==number.toIntOrNull())initial.completedAt else null));close()},enabled=(number.toIntOrNull()?:0)>0&&text.isNotBlank()){Text("保存")}},dismissButton={Row{if(initial.completedAt!=null)TextButton({save(initial.copy(completedAt=null));close()}){Text("恢复提醒")};if(existing)TextButton({delete(initial.id);close()}){Text("删除",color=Bad)};TextButton(close){Text("取消")}}})
}
@Composable fun Quick(t:String,s:String,icon:androidx.compose.ui.graphics.vector.ImageVector,m:Modifier,onClick:()->Unit){Card(onClick=onClick,modifier=m,shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){Column(Modifier.padding(18.dp)){Icon(icon,null,tint=Accent);Spacer(Modifier.height(20.dp));Text(t,fontWeight=FontWeight.Bold);Text(s,color=Muted,fontSize=12.sp)}}}
@Composable fun ReportCard(r:LabReport,open:()->Unit,viewImages:((List<String>)->Unit)?=null,addImage:(()->Unit)?=null){Paper(Modifier.clickable(onClick=open)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){val heading=ReportNaming.title(r);Text(heading.category,fontWeight=FontWeight.Bold);if(heading.name.isNotBlank())Text(heading.name,fontSize=13.sp,color=Muted)};IconButton(onClick={if(r.sourceImages.isNotEmpty()&&viewImages!=null)viewImages(r.sourceImages.map{it.uri}) else if(addImage!=null)addImage() else open()},modifier=Modifier.size(36.dp)){Icon(Icons.Outlined.Description,contentDescription=if(r.sourceImages.isNotEmpty())"查看报告原图" else "未保存原图，打开报告",tint=if(r.sourceImages.isNotEmpty())Accent else Muted)}};Text("${r.hospitalKey} · ${dateText(r.testedAtEpochMillis)}",color=Muted,fontSize=12.sp);Text("${r.results.size} 个项目 · ${r.results.count{it.status()==ResultStatus.HIGH||it.status()==ResultStatus.LOW}} 个超出参考范围");}}
/** Units belong to the reference rows; keep the latest result summary compact. */
@Composable internal fun TrendLatestSummary(value:String,status:String,color:Color){
 Column(Modifier.widthIn(max=160.dp),horizontalAlignment=Alignment.End){
  Text(value,fontWeight=FontWeight.Bold,fontSize=20.sp,color=color,softWrap=true)
  Text(status,color=color,fontSize=11.sp)
 }
}

@Composable internal fun TrendReferenceText(text:String){
 Text(text,color=Muted,fontSize=12.sp,lineHeight=17.sp,softWrap=true)
}

/** The short explanation remains fully readable at narrow widths and larger font scales. */
@Composable internal fun TrendMeaningText(text:String,onClick:()->Unit){
 Text(text,color=Accent,fontSize=12.sp,lineHeight=17.sp,fontWeight=FontWeight.Medium,
  softWrap=true,modifier=Modifier.fillMaxWidth().clickable(onClick=onClick))
}

/** Card and dialog share the exact same resolved educational entry. */
@Composable internal fun TrendPurposeExplanation(metricKey:String,title:String,purpose:MetricPurposeCatalogue.Entry=MetricPurposeCatalogue.forDisplay(metricKey)){
 var open by remember(metricKey){mutableStateOf(false)}
 TrendMeaningText(purpose.summary){open=true}
 if(open)AlertDialog(onDismissRequest={open=false},title={Text(title)},text={Text(purpose.detail)},confirmButton={TextButton({open=false}){Text("知道了")}})
}

/** Compact card controls: change group, or pin this exact trend identity first. */
@Composable internal fun TrendCardActions(primary:Boolean,onMove:()->Unit,onPin:()->Unit){
 Row(horizontalArrangement=Arrangement.spacedBy(2.dp)){
  IconButton(onClick=onMove,modifier=Modifier.size(36.dp)){
   Icon(Icons.Outlined.ArrowBack,if(primary)"移到其他指标" else "移到重点指标",Modifier.size(20.dp),tint=Accent)
  }
  IconButton(onClick=onPin,modifier=Modifier.size(36.dp)){
   Icon(Icons.Outlined.ArrowUpward,"置于最上方",Modifier.size(20.dp),tint=Accent)
  }
 }
}

/** Aliases are requested details, never a permanent label above a trend title. */
@Composable fun TrendMetricTitle(title:String,aliases:List<String>){
 var showAliases by remember(title){mutableStateOf(false)}
 Text(title,fontWeight=FontWeight.Bold,fontSize=16.sp,color=Ink,maxLines=1,
  overflow=TextOverflow.Ellipsis,modifier=Modifier.clickable{showAliases=true})
 if(showAliases)AlertDialog(onDismissRequest={showAliases=false},title={Text(title)},
  text={Column(Modifier.heightIn(max=320.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
   Text("其他别名",fontWeight=FontWeight.SemiBold)
   Text(aliases.filter{it.isNotBlank()&&it!=title}.distinct().joinToString("、").ifBlank{"暂无其他别名"})
  }},confirmButton={TextButton({showAliases=false}){Text("关闭")}})
}

@Composable fun Trends(m:Modifier,store:HealthStore,reports:List<LabReport>,revision:Int,priority:(String,Boolean)->Unit,images:(List<String>)->Unit,edit:(LabReport,LabResult,Double)->Unit,openCourse:(Long)->Unit,onMainPageSwipe:(Int)->Unit,resultChanged:()->Unit={},requestedCategory:String?=null,onRequestedCategoryConsumed:()->Unit={},showPreview:(TrendPreviewRequest)->Unit={}){
 val trendFocus=LocalFocusManager.current
 var textRevision by remember{mutableIntStateOf(0)}
 var category by rememberSaveable{mutableStateOf("血常规")};var mode by rememberSaveable{mutableStateOf("重点指标")};var query by rememberSaveable{mutableStateOf("")};var range by rememberSaveable{mutableStateOf("全部")};var weightVersion by remember{mutableIntStateOf(0)};var showWeight by remember{mutableStateOf(false)};var weightText by remember{mutableStateOf("")};var weightDate by remember{mutableStateOf("")};var weightTime by remember{mutableStateOf("")};var editingWeight by remember{mutableStateOf<WeightRecord?>(null)};var deletingWeight by remember{mutableStateOf<WeightRecord?>(null)}
 LaunchedEffect(requestedCategory){requestedCategory?.let{category=it;query="";onRequestedCategoryConsumed()}}
 var selected by remember{mutableStateOf<Pair<LabReport,LabResult>?>(null)}
 var editing by remember{mutableStateOf(false)};var editValue by remember{mutableStateOf("")}
 var imageError by remember{mutableStateOf<String?>(null)};var imageBusy by remember{mutableStateOf(false)}
 var imageTargetReportId by remember{mutableStateOf<String?>(null)}
 val imageScope=rememberCoroutineScope()
 val imagePicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){uris->
  val reportId=imageTargetReportId;imageTargetReportId=null
  if(reportId!=null&&uris.isNotEmpty()){imageBusy=true;imageScope.launch{try{withContext(kotlinx.coroutines.Dispatchers.IO){store.addReportImages(reportId,uris)};val refreshed=store.reports().firstOrNull{it.id==reportId};if(refreshed!=null){val old=selected?.second;val freshResult=old?.let{o->refreshed.results.firstOrNull{it.id==o.id}};if(freshResult!=null)selected=refreshed to freshResult}}catch(x:Exception){imageError="原图保存失败：${x.message}"}finally{imageBusy=false}}}
 }
 fun fixedTrendTitle(metricKey:String):String?=when(canonicalMetricKey(metricKey)){
  "WBC"->"白细胞";"NEUT#"->"中性粒细胞计数";"NEUT%"->"中性粒细胞百分比"
  "LYMPH#"->"淋巴细胞计数";"LYMPH%"->"淋巴细胞百分比";"MONO#"->"单核细胞计数";"MONO%"->"单核细胞百分比"
  "EOS#"->"嗜酸性粒细胞计数";"EOS%"->"嗜酸性粒细胞百分比";"BASO#"->"嗜碱性粒细胞计数";"BASO%"->"嗜碱性粒细胞百分比"
  "RBC"->"红细胞";"HGB"->"血红蛋白";"HCT"->"红细胞压积";"MCV"->"红细胞平均体积";"MCH"->"平均红细胞血红蛋白量"
  "MCHC"->"平均血红蛋白浓度";"RDW-CV"->"红细胞分布宽度CV";"RDW-SD"->"红细胞分布宽度SD";"PLT"->"血小板"
  "PCT"->"血小板压积";"MPV"->"平均血小板体积";"PDW-CV"->"血小板分布宽度CV";"PDW-SD"->"血小板分布宽度SD";"P-LCR"->"大型血小板比率"
  "NRBC%"->"有核红细胞比率";"NRBC#"->"有核红细胞计数";"P-LCC"->"大血小板数目"
  "TP"->"总蛋白";"ALB"->"白蛋白";"GLOB"->"球蛋白";"A/G"->"白球比";"TBIL"->"总胆红素";"DBIL"->"直接胆红素";"IBIL"->"间接胆红素"
  "ALT"->"谷丙转氨酶";"AST"->"谷草转氨酶";"AST/ALT"->"谷草/谷丙";"GGT"->"谷氨酰转肽酶";"ALP"->"碱性磷酸酶";"CHE"->"胆碱酯酶"
  "TBA"->"总胆汁酸";"PA"->"前白蛋白";"UREA"->"尿素";"CREA"->"肌酐";"UA"->"尿酸";"UREA/CREA"->"尿素/肌酐";DERIVED_EGFR_KEY->"eGFR";"AMY"->"淀粉酶";"CKMB"->"肌酸激酶MB亚型";"CK"->"肌酸激酶";"PHOS"->"无机磷酸盐"
  else->null
 }
 val activeReports=remember(reports,textRevision){if(textRevision==0)reports else store.reports()}
 val sourcePairs=activeReports.distinctBy{it.id}.flatMap{r->r.results.distinctBy{it.id}.filter{result->
  val key=canonicalMetricKey(result.metricKey)
  key !in setOf("RDW","PDW")&&distributionWidthIdentityProblem(key,result.rawName,result.unitAtTest)==null
 }.map{x->r to x}}
 val egfrPairs=remember(activeReports,revision,textRevision){derivedEgfrRows(activeReports,store.patientProfile())}
 val egfrProfile=remember(revision){store.patientProfile()}
 val hasCreatinine=sourcePairs.any{canonicalMetricKey(it.second.metricKey)=="CREA"}
 val egfrUnavailableReason=when{
  !hasCreatinine||egfrPairs.isNotEmpty()->null
  profileBirthDate(egfrProfile.birthDate)==null->"已找到肌酐记录；请先在“我的”补全出生日期，eGFR 才会按检测当日年龄计算。"
  egfrSex(egfrProfile.sex)==null->"已找到肌酐记录；请先在“我的”补全性别，eGFR 才会使用 CKD-EPI 2021 公式计算。"
  else->"已找到肌酐记录，但没有可用于成人 eGFR 估算的精确数值。"
 }
 val all=(sourcePairs+egfrPairs).mapNotNull{pair->
  trendCardIdentity(store.trendIdentity(pair.second.metricKey),pair.second)?.let{it.copy(unit=trendUnitIdentity(pair.second)) to pair}
 }.groupBy({it.first},{it.second})
 // Hospital color identity is global to the whole Trends screen, not recalculated per metric.
 // Persisted palette slots survive sorting, filtering, missing metrics and new hospitals.
 val globalHospitalRows=store.trendHospitalPaletteKeys(activeReports.sortedWith(compareBy<LabReport>{it.testedAtEpochMillis}.thenBy{it.id}).map{it.hospitalKey})
 val globalNormalHospitalColors=hospitalColorMap(globalHospitalRows,false)
 val globalAbnormalHospitalColors=hospitalColorMap(globalHospitalRows,true)
 val weights=remember(weightVersion,revision){store.weightRecords()}
 val chemotherapyDates=remember(revision){chemotherapyEventDates(store.courseRecords())}
 val fixedTrendCategories=listOf("血常规","肝功能","肾功能","尿常规","凝血功能","电解质及肾功能","肿瘤标志物","甲状腺功能","传染病筛查","粪便常规","体重")
  val trendCategories=fixedTrendCategories+reports.map{it.reportType}.distinct().filterNot{it in fixedTrendCategories||it=="SCC"}
 val categoryScroll=rememberScrollState()
 var categorySwipe by remember{mutableFloatStateOf(0f)}
 var trendPageDrag by remember{mutableFloatStateOf(0f)}
 val trendSwipeModifier=m.pointerInput(category,trendCategories){
  detectHorizontalDragGestures(
   onDragStart={trendPageDrag=0f},
   onHorizontalDrag={_,amount->trendPageDrag+=amount},
   onDragCancel={trendPageDrag=0f},
   onDragEnd={
    if(kotlin.math.abs(trendPageDrag)>90f){
     val i=trendCategories.indexOf(category)
     if(trendPageDrag<0){if(i<trendCategories.lastIndex)category=trendCategories[i+1] else onMainPageSwipe(1)}
     else{if(i>0)category=trendCategories[i-1] else onMainPageSwipe(-1)}
    }
    trendPageDrag=0f
   }
  )
 }
 Screen(trendSwipeModifier,"指标趋势",spacing=3.5.dp){
  Row(Modifier.horizontalScroll(categoryScroll)){trendCategories.forEach{t->FilterChip(category==t,{category=t},label={Text(t)},modifier=Modifier.padding(end=8.dp),border=null,colors=FilterChipDefaults.filterChipColors(containerColor=Color.White,selectedContainerColor=SelectedTint))}}
  Row{listOf("重点指标","其他指标").forEach{t->FilterChip(mode==t,{mode=t},label={Text(t)},modifier=Modifier.padding(end=8.dp),border=null,colors=FilterChipDefaults.filterChipColors(containerColor=Color.White,selectedContainerColor=SelectedTint))}}
  Row(Modifier.horizontalScroll(rememberScrollState())){listOf("近3月","近6月","近1年","全部").forEach{t->FilterChip(range==t,{range=t},label={Text(t)},modifier=Modifier.padding(end=8.dp),border=null,colors=FilterChipDefaults.filterChipColors(containerColor=Color.White,selectedContainerColor=SelectedTint))}}
  AppSearchField(query,{query=it},"查找指标",height=48.dp,shape=RoundedCornerShape(8.dp))
  if(category=="体重"&&query.isBlank()){
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){TextButton({editingWeight=null;weightText="";val now=java.time.ZonedDateTime.now();weightDate=now.toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd"));weightTime=now.toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));showWeight=true}){Text("+ 记录体重")}}
   if(weights.isEmpty())Paper{Text("暂无体重记录")} else TrendPaper{
    val w=weights.last()
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.Bottom){Text("体重",fontWeight=FontWeight.Bold,fontSize=16.sp);Row(verticalAlignment=Alignment.Bottom){Text(String.format(java.util.Locale.US,"%.1f",w.kilograms),fontWeight=FontWeight.Bold,fontSize=20.sp,color=Good);Spacer(Modifier.width(4.dp));Text("kg",fontSize=11.sp,color=Good)}}
    val series=weightTrendSeries(weights)
    Spark(points=series.points,color=TrendBlue,referenceLow=null,referenceHigh=null,onPointClick={index->series.records.getOrNull(index)?.let{editingWeight=it;weightText=it.kilograms.toString();val z=java.time.Instant.ofEpochMilli(it.measuredAtEpochMillis).atZone(java.time.ZoneId.systemDefault());weightDate=z.toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd"));weightTime=z.toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));showWeight=true}},metricKey="WEIGHT",pointDescriptions=series.records.map{"体重 "+java.time.Instant.ofEpochMilli(it.measuredAtEpochMillis).atZone(java.time.ZoneId.systemDefault()).toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))+" "+String.format(java.util.Locale.US,"%.1f kg",it.kilograms)},valueLabels=series.records.map{String.format(java.util.Locale.US,"%.1f",it.kilograms)},linePointIndices=series.earliestPointIndices,stackEqualDates=true,onShowPreview={})
   }
  }
  if(query.isNotBlank())weights.filter{DataSearch.weight(query,it)}.forEach{w->
   Paper{Text("体重 · "+dateText(w.measuredAtEpochMillis),fontWeight=FontWeight.Bold);Text("${w.kilograms} kg");TextButton({editingWeight=w;weightText=w.kilograms.toString();val z=java.time.Instant.ofEpochMilli(w.measuredAtEpochMillis).atZone(java.time.ZoneId.systemDefault());weightDate=z.toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd"));weightTime=z.toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));showWeight=true}){Text("编辑体重")}}
  }
  val searching=query.isNotBlank()
  // Load the editable dictionary once per render, not once for every trend row.
  val savedAliases=remember(revision){store.metricAliases()}
  val preferredNames=remember(revision){store.preferredMetricNames()}
  val aliasIndex=remember(revision){savedAliases.entries.groupBy({canonicalMetricKey(it.value)},{it.key})}
  val builtInIndex=remember{ReportParser.builtInMetricKeys().associateWith{ReportParser.builtInAliasNames(it)}}
  fun aliasesFor(key:String)=(aliasIndex[key].orEmpty()+builtInIndex[key].orEmpty()+
   if(key==DERIVED_EGFR_KEY)listOf("估算肾小球滤过率","肾小球滤过率","CKD-EPI 2021") else emptyList()).distinct()
  val filtered=orderTrendCards(all.filter{(card,list)->
   val key=card.metricKey
   val matchesText=list.any{(r,x)->DataSearch.row(query,r,x,savedAliases,preferredNames[key])} || DataSearch.matches(query,aliasesFor(key))
   matchesText && (searching || store.isPrimary(key)==(mode=="重点指标") && list.any{(r,x)->trendCategoryMatches(category,r,x)})
  }.toList(),store.trendCardOrder(mode=="重点指标")){it.first.storageKey()}
  Column(verticalArrangement=Arrangement.spacedBy(3.5.dp)){
  if(category=="肾功能"&&!searching&&egfrUnavailableReason!=null)Paper{Text(egfrUnavailableReason,color=Muted,fontSize=13.sp)}
  if((category!="体重"||searching)&&filtered.isEmpty())Paper{Text("暂无符合条件的指标")}
  if(category!="体重"||searching)filtered.forEach{(card,list)->
   val key=card.metricKey
   val cutoff=when(range){"近3月"->System.currentTimeMillis()-90L*86400000L;"近6月"->System.currentTimeMillis()-183L*86400000L;"近1年"->System.currentTimeMillis()-365L*86400000L;else->Long.MIN_VALUE}
   val points=list.filter{it.first.testedAtEpochMillis>=cutoff}.sortedWith(compareBy<Pair<LabReport,LabResult>>{it.first.testedAtEpochMillis}.thenBy{it.first.id}.thenBy{it.second.id})
   val treatmentDates=chemotherapyDates.filter{it>=cutoff}
   if(points.isEmpty())return@forEach
   val latest=points.last().second
   val latestStatus=latest.status()
   val valueColor=if(latest.value==null)qualitativeTrendColor(latest) else when(latestStatus){ResultStatus.NORMAL->Good;ResultStatus.LOW,ResultStatus.HIGH->Bad;else->Muted}
   TrendPaper{
    val metricAliases=aliasesFor(key)
    val purpose=MetricPurposeCatalogue.forDisplay(key)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.Top){
     Column(Modifier.weight(1f)){
      val baseTitle=preferredNames[key]?:fixedTrendTitle(key)?:labDisplayTitle(latest.rawName,key)
      val fixedTitle=dDimerTrendTitle(baseTitle,card.dimerBasis)
      TrendMetricTitle(fixedTitle,metricAliases)
      TrendPurposeExplanation(key,fixedTrendTitle(key)?:labDisplayTitle(latest.rawName,key),purpose)
     }
     TrendLatestSummary(latest.trendDisplayText(),latest.resultStatusLabel(),valueColor)
    }
    val textPoints=points.filter{it.second.value==null&&it.second.textValue.isNotBlank()}
    var textPreview by remember{mutableStateOf(false)}
    // Equivalent spellings have already collapsed to one identity. A genuinely
    // incompatible unit must never be connected to the latest unit's series.
    val compatiblePoints=points.filter{it.second.trendPlotValue()!=null&&trendUnitIdentity(it.second)==card.unit}
    val rangeDisplayRows=points.mapNotNull{(_,x)->
     val range=x.trendReferenceDisplayText().takeIf{it.isNotBlank()}?:return@mapNotNull null
     val unit=if(key=="PCT") "%" else displayLabUnit(if(range==x.referenceRaw.trim())x.unitAtTest else trendUnitIdentity(x))
     "参考范围: ${qualitativeDisplayText(range)} $unit".trim()+x.trendRangeEstimateText().takeIf{it.isNotBlank()}?.let{" · $it"}.orEmpty()
    }.distinct()
    val hospitalRows=points.map{trendHospitalKey(it.first.hospitalKey)}.distinct()
    val hasHistoricalAbnormal=trendHasHistoricalAbnormal(list.map{it.second})
    val hospitalColors=if(hasHistoricalAbnormal)globalAbnormalHospitalColors else globalNormalHospitalColors
    val pointHospitalColors=compatiblePoints.map{(report,result)->hospitalColors.getValue(trendHospitalKey(report.hospitalKey))}
    fun openOverallPreview(series:List<Pair<LabReport,LabResult>>){
     showPreview(TrendPreviewRequest(
      points=series.map{it.first.testedAtEpochMillis to it.second.trendPlotValue()!!},
      color=if(hasHistoricalAbnormal)Bad else TrendBlue,
      referenceBands=series.map{(_,r)->r.trendPlotReferenceRange()},
      segmentKeys=series.map{trendHospitalKey(it.first.hospitalKey)},
      segmentColors=series.map{(report,_)->hospitalColors.getValue(trendHospitalKey(report.hospitalKey))},
      metricKey=labDisplayTitle(latest.rawName,latest.metricKey),
      referenceNotes=series.map{it.second.trendRangeEstimateText()}.filter{it.isNotBlank()}.distinct(),
      timelineDates=trendTimelineDates(activeReports,series.map{it.first},cutoff),
      eventDates=treatmentDates
     ))
    }
    Box(Modifier.fillMaxWidth()){
     Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(2.dp)){
      rangeDisplayRows.forEach{text->TrendReferenceText(text)}
      hospitalRows.forEach{hospital->
       Row(verticalAlignment=Alignment.CenterVertically){
        Canvas(Modifier.size(8.dp).semantics{contentDescription="医院图例 $hospital"}){drawCircle(hospitalColors.getValue(hospital))}
        Spacer(Modifier.width(6.dp))
        Text(hospital,color=Muted,fontSize=11.sp,lineHeight=14.sp,maxLines=1)
       }
      }
     }
     if(compatiblePoints.isNotEmpty() && rangeDisplayRows.isNotEmpty()){
      TextButton(
       onClick={trendFocus.clearFocus();openOverallPreview(compatiblePoints)},
       modifier=Modifier.align(Alignment.TopEnd).heightIn(min=24.dp),
       contentPadding=PaddingValues(horizontal=8.dp,vertical=0.dp)
      ){Text("整体",fontSize=12.sp,color=Accent)}
     }
    }
    if(textPoints.isNotEmpty()){
     Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){TextButton({trendFocus.clearFocus();textPreview=true}){Text("整体",fontSize=12.sp,color=Accent)}}
     TextResultTrend(textPoints,onPointClick={selected=it})
    }
    if(textPreview)AlertDialog(onDismissRequest={textPreview=false},title={Text(dDimerTrendTitle(labDisplayTitle(latest.rawName,key),card.dimerBasis))},text={TextResultTrend(textPoints,onPointClick={selected=it;textPreview=false})},confirmButton={TextButton({textPreview=false}){Text("关闭")}})
    if(compatiblePoints.isNotEmpty()){
     Spacer(Modifier.height(if(rangeDisplayRows.isNotEmpty()||hospitalRows.isNotEmpty()) 4.dp else 0.dp))
     val trendColor=if(hasHistoricalAbnormal) Bad else TrendBlue
     val referenceBands=compatiblePoints.map{(_,x)->x.trendPlotReferenceRange()}
     Spark(
      points=compatiblePoints.map{it.first.testedAtEpochMillis to it.second.trendPlotValue()!!},
      color=trendColor,
      referenceLow=null,
      referenceHigh=null,
      referenceBands=referenceBands,
      segmentKeys=compatiblePoints.map{trendHospitalKey(it.first.hospitalKey)},
      segmentColors=pointHospitalColors,
      onPointClick={index->selected=originalTrendPoint(compatiblePoints[index])},
      metricKey=key,
      pointDescriptions=compatiblePoints.map{trendPointContentDescription(key,it.first.testedAtEpochMillis)},
      valueLabels=compatiblePoints.map{it.second.trendDisplayText()},
      // Hospital color remains the point/line/legend identity. An indeterminate
      // comparison is still gray as text and is not presented as normal.
      valueColors=compatiblePoints.mapIndexed{i,(_,result)->if(result.status()==ResultStatus.UNKNOWN)Muted else pointHospitalColors[i]},
      // Include reports without this metric so a missed measurement breaks the curve.
      timelineDates=trendTimelineDates(activeReports,compatiblePoints.map{it.first},cutoff),
      eventDates=treatmentDates,
      onShowPreview={trendFocus.clearFocus();openOverallPreview(compatiblePoints)}
     )
    }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){
     TrendCardActions(
      primary=mode=="重点指标",
      onMove={priority(key,mode!="重点指标")},
      onPin={store.pinTrendCard(card.storageKey(),mode=="重点指标");resultChanged()}
     )
    }
   }
  }
   }
 }
 if(showWeight){
  val weightDay=runCatching{java.time.LocalDate.parse(weightDate.replace('/','-'))}.getOrNull()
  val weightPickerState=rememberDatePickerState(
   initialSelectedDateMillis=weightDay?.atStartOfDay(java.time.ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
  )
  var showWeightDatePicker by remember{mutableStateOf(false)}
  AlertDialog(
   onDismissRequest={showWeight=false},
   title={Text(if(editingWeight==null)"记录体重" else "修改体重")},
   text={
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
     Row(
      Modifier.fillMaxWidth().background(Color.White,RoundedCornerShape(12.dp)).clickable{showWeightDatePicker=true}.padding(horizontal=16.dp,vertical=12.dp),
      horizontalArrangement=Arrangement.SpaceBetween,
      verticalAlignment=Alignment.CenterVertically
     ){
      Text(weightDate,fontSize=16.sp,color=Ink)
      Icon(Icons.Outlined.DateRange,"选择日期",tint=Accent)
     }
     Column(Modifier.fillMaxWidth().background(Color.White,RoundedCornerShape(12.dp)).padding(horizontal=16.dp,vertical=12.dp)){
      Text("记录时间",fontSize=12.sp,color=Muted)
      ClockTimeSelector(weightTime,{weightTime=it},Modifier.fillMaxWidth().padding(top=6.dp))
     }
     AppInputField(weightText,{weightText=it.filter{ch->ch.isDigit()||ch=='.'}},label="体重 kg",singleLine=true)
    }
   },
   confirmButton={TextButton({val kg=weightText.toDoubleOrNull();val day=runCatching{java.time.LocalDate.parse(weightDate.replace('/','-'))}.getOrNull();val time=runCatching{java.time.LocalTime.parse(weightTime,java.time.format.DateTimeFormatter.ofPattern("H:mm"))}.getOrNull();if(kg!=null&&kg>0&&day!=null&&time!=null){val at=day.atTime(time).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();store.saveWeight(WeightRecord(id=editingWeight?.id?:newId(),measuredAtEpochMillis=at,kilograms=kg,courseRecordIds=editingWeight?.courseRecordIds.orEmpty()));weightVersion++;editingWeight=null;showWeight=false}}){Text("保存")}},
   dismissButton={Row { if(editingWeight!=null)TextButton({deletingWeight=editingWeight;showWeight=false}){Text("删除",color=Bad)};TextButton({editingWeight=null;showWeight=false}){Text("取消")} }}
  )
  if(showWeightDatePicker){
   DatePickerDialog(
    onDismissRequest={showWeightDatePicker=false},
    confirmButton={TextButton({
     weightPickerState.selectedDateMillis?.let{millis->
      weightDate=java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneOffset.UTC).toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd"))
     }
     showWeightDatePicker=false
    }){Text("确定")}},
    dismissButton={TextButton({showWeightDatePicker=false}){Text("取消")}}
   ){DatePicker(state=weightPickerState)}
  }
 }
 if(deletingWeight!=null){
  AlertDialog(onDismissRequest={deletingWeight=null},title={Text("删除体重记录？")},text={Text("删除后将从体重趋势中移除这条记录。")},confirmButton={TextButton({deletingWeight?.let{store.deleteWeight(it.id);weightVersion++};deletingWeight=null;editingWeight=null}){Text("确认删除",color=Bad)}},dismissButton={TextButton({deletingWeight=null}){Text("取消")}})
 }
 selected?.let{(selectedReport,selectedResult)->
  // Trend points may hold an older report snapshot. Resolve the persisted report
  // each time the dialog is shown so attachment status is never taken from stale data.
  val r=store.reports().firstOrNull{it.id==selectedReport.id}?:selectedReport
  val x=r.results.firstOrNull{it.id==selectedResult.id}?:selectedResult
  AlertDialog(onDismissRequest={selected=null;editing=false},title={Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text(labDisplayTitle(x.rawName,x.metricKey));IconButton({selected=null;openCourse(r.testedAtEpochMillis)}){Icon(Icons.Outlined.Timeline,"打开当天病程",tint=Accent)}}},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
   Text(dateText(r.testedAtEpochMillis),color=Muted);Text(r.hospitalKey.ifBlank{"医院未录入"},fontWeight=FontWeight.Medium)
   ResultValueUnit(x.textValue,x.unitAtTest)
   Text("当次参考：${qualitativeDisplayText(x.referenceRaw.ifBlank{rangeText(x.referenceLowAtTest,x.referenceHighAtTest)})} · ${x.resultStatusLabel()}",color=Muted)
   if(editing)Field(editValue,{editValue=it},"结果")
  }},confirmButton={Row{
   if(editing)TextButton({try{if(LabResultValues.valid(editValue)){store.updateResultText(r.id,x.id,editValue);textRevision++;resultChanged();val persisted=store.reports().first{it.id==r.id};selected=persisted to persisted.results.first{it.id==x.id};editing=false}}catch(e:Exception){imageError=e.message?:"保存失败"}},enabled=LabResultValues.valid(editValue)){Text("保存")}
   else TextButton({editValue=x.textValue;editing=true}){Text("编辑结果")}
  }},dismissButton={Row{
   if(r.sourceImages.isNotEmpty())TextButton({selected=null;images(r.sourceImages.map{it.uri})}){Text("查看原报告")} else TextButton({imageTargetReportId=r.id;imagePicker.launch(arrayOf("image/*"))},enabled=!imageBusy){Text("导入原报告")}
   TextButton({selected=null;editing=false}){Text("关闭")}
  }})
 }
 if(imageError!=null)AlertDialog(onDismissRequest={imageError=null},title={Text("操作未完成")},text={Text(imageError!!)},confirmButton={TextButton({imageError=null}){Text("知道了")}})
}
fun trendCategoryMatches(category:String,report:LabReport,result:LabResult):Boolean{
 val key=canonicalMetricKey(result.metricKey)
 val liver=setOf("ALT","AST","AST/ALT","GGT","ALP","TBIL","DBIL","IBIL","TBA","TP","ALB","GLOB","A/G","PA","CHE")
 val kidney=setOf("CREA","UREA","BUN","UA","EGFR",DERIVED_EGFR_KEY)
 val cbc=setOf("WBC","RBC","HGB","HCT","MCV","MCH","MCHC","PLT","NEUT#","LYMPH#","MONO#","EOS#","BASO#","NEUT%","LYMPH%","MONO%","EOS%","BASO%","RDW-CV","RDW-SD","PCT","MPV","PDW-CV","PDW-SD","P-LCR","P-LCC","NRBC#","NRBC%")
 return when(category){
  "肝功能"->key in liver
  "肾功能"->key in kidney || key=="UREA/CREA"
  "血常规"->key in cbc||report.reportType=="血常规"
  "肿瘤标志物"->key in setOf("SCC","AFP","CEA","CA125","CA153","CA199","CA724","CYFRA21-1","NSE","HE4") || report.reportType in setOf("肿瘤标志物","SCC")
  "电解质及肾功能"->report.reportType=="电解质及肾功能"
  else->report.reportType==category
 }
}
fun trendPointContentDescription(metricKey:String,testedAtEpochMillis:Long)="趋势点 $metricKey ${dateText(testedAtEpochMillis)}"
fun trendPointPosition(points:List<Pair<Long,Double>>,index:Int,width:Float,height:Float,referenceLow:Double?=null,referenceHigh:Double?=null):Offset{
 val values=points.map{it.second}+listOfNotNull(referenceLow,referenceHigh);val low=values.minOrNull()?:0.0;val high=values.maxOrNull()?:1.0;val span=(high-low).coerceAtLeast(1.0);val p=points[index]
 val pad=56f
 val x=if(points.size==1)width/2 else pad+(width-2*pad)*index/(points.size-1)
 val y=(height*.88-(p.second-low)/span*height*.76).toFloat();return Offset(x,y)
}
fun nearestTrendPoint(points:List<Pair<Long,Double>>,tap:Offset,width:Float,height:Float,referenceLow:Double?=null,referenceHigh:Double?=null,radius:Float):Int?{
 if(points.isEmpty()||width<=0f||height<=0f||radius<0f)return null
 val hit=points.indices.minByOrNull{i->val p=trendPointPosition(points,i,width,height,referenceLow,referenceHigh);val dx=p.x-tap.x;val dy=p.y-tap.y;dx*dx+dy*dy}?:return null
 val p=trendPointPosition(points,hit,width,height,referenceLow,referenceHigh);val dx=p.x-tap.x;val dy=p.y-tap.y
 return hit.takeIf{dx*dx+dy*dy<=radius*radius}
}
/** Report dates for the same examination type, including visits where this metric was not measured.
 * A point-free date must remain on the x-axis so Spark splits the line rather than bridging it.
 * Pure function for synthetic regression tests; does not modify saved reports.
 */
internal fun trendTimelineDates(allReports:List<LabReport>,pointReports:List<LabReport>,cutoff:Long):List<Long>{
 val types=pointReports.map{it.reportType}.toSet()
 return (allReports.filter{it.reportType in types && it.testedAtEpochMillis>=cutoff}
  .map{it.testedAtEpochMillis}+pointReports.map{it.testedAtEpochMillis}).distinct().sorted()
}

/** PCHIP-like monotone Hermite tangents: shared slopes prevent kinks and overshoot. */
internal fun monotoneTrendSlopes(points:List<Offset>):List<Float>{
 if(points.size<2)return List(points.size){0f}
 val dx=points.zipWithNext{a,b->(b.x-a.x).coerceAtLeast(0.0001f)}
 val sec=points.zipWithNext().mapIndexed{i,(a,b)->(b.y-a.y)/dx[i]}
 if(sec.size==1)return listOf(sec[0],sec[0])
 val slopes=MutableList(points.size){0f}
 fun edge(h0:Float,h1:Float,d0:Float,d1:Float):Float{
  val m=((2f*h0+h1)*d0-h0*d1)/(h0+h1)
  return when{
   m*d0<=0f->0f
   d0*d1<0f&&kotlin.math.abs(m)>3f*kotlin.math.abs(d0)->3f*d0
   else->m
  }
 }
 slopes[0]=edge(dx[0],dx[1],sec[0],sec[1])
 slopes[slopes.lastIndex]=edge(dx.last(),dx[dx.lastIndex-1],sec.last(),sec[sec.lastIndex-1])
 for(i in 1 until points.lastIndex){
  val before=sec[i-1];val after=sec[i]
  if(before*after<=0f){slopes[i]=0f;continue}
  val w1=2f*dx[i]+dx[i-1];val w2=dx[i]+2f*dx[i-1]
  slopes[i]=(w1+w2)/(w1/before+w2/after)
 }
 return slopes
}

/** Draw a monotone cubic interval, splitting color exactly at the midpoint when hospitals differ. */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHospitalTrendJoin(
 start:Offset,end:Offset,from:Color,to:Color,strokeWidth:Float,
 startSlope:Float,endSlope:Float
){
 val dx=end.x-start.x
 if(dx<=0f){drawLine(from,start,end,strokeWidth);return}
 val c1=Offset(start.x+dx/3f,start.y+dx*startSlope/3f)
 val c2=Offset(end.x-dx/3f,end.y-dx*endSlope/3f)
 if(from==to){
  val path=Path().apply{moveTo(start.x,start.y);cubicTo(c1.x,c1.y,c2.x,c2.y,end.x,end.y)}
  drawPath(path,from,style=Stroke(strokeWidth));return
 }
 fun mid(a:Offset,b:Offset)=Offset((a.x+b.x)/2f,(a.y+b.y)/2f)
 val a=mid(start,c1);val b=mid(c1,c2);val c=mid(c2,end)
 val d=mid(a,b);val e=mid(b,c);val center=mid(d,e)
 val first=Path().apply{moveTo(start.x,start.y);cubicTo(a.x,a.y,d.x,d.y,center.x,center.y)}
 val second=Path().apply{moveTo(center.x,center.y);cubicTo(e.x,e.y,c.x,c.y,end.x,end.y)}
 drawPath(first,from,style=Stroke(strokeWidth))
 drawPath(second,to,style=Stroke(strokeWidth))
}

@Composable fun Spark(points:List<Pair<Long,Double>>,color:Color,referenceLow:Double?=null,referenceHigh:Double?=null,referenceBands:List<Pair<Double?,Double?>> = emptyList(),segmentKeys:List<String> = emptyList(),segmentColors:List<Color> = emptyList(),onPointClick:((Int)->Unit)?=null,metricKey:String="",pointDescriptions:List<String> = emptyList(),valueLabels:List<String> = emptyList(),valueColors:List<Color> = emptyList(),timelineDates:List<Long> = points.map{it.first},linePointIndices:List<Int>?=null,stackEqualDates:Boolean=false,eventDates:List<Long> = emptyList(),onShowPreview:()->Unit={}){
 val layout=trendTimeline(points,timelineDates,stackEqualDates)
 val timeline=layout.dates
 val pointSlots=layout.pointSlots
 val pointBySlot=buildMap{pointSlots.forEachIndexed{index,slot->if(!containsKey(slot))put(slot,index)}}
 val density=LocalDensity.current
 val textMeasurer=rememberTextMeasurer()
 val dateStyle=TextStyle(fontSize=10.sp,fontWeight=FontWeight.Normal)
 val valueStyle=TextStyle(fontSize=13.sp,fontWeight=FontWeight.SemiBold)
 val groupGap=16.dp
 val edgePadding=12.dp
 val groupWidthsPx=timeline.mapIndexed{slot,date->
  val pointIndex=pointBySlot[slot] ?: -1
  val value=if(pointIndex>=0)valueLabels.getOrNull(pointIndex).orEmpty().ifBlank{formatTrendValue(points[pointIndex].second)} else ""
  val datePx=textMeasurer.measure(text=trendShortDate(date),style=dateStyle,maxLines=1).size.width
  val valuePx=if(value.isBlank())0 else textMeasurer.measure(text=value,style=valueStyle,maxLines=1).size.width
  maxOf(datePx,valuePx)
 }
 val groupWidths=groupWidthsPx.map{px->with(density){px.toDp()}}
 val centers=mutableListOf<androidx.compose.ui.unit.Dp>()
 var cursor=edgePadding
 groupWidths.forEachIndexed{i,w->
  centers+=cursor+w/2
  cursor+=w
  if(i<groupWidths.lastIndex)cursor+=groupGap
 }
 val naturalWidth=cursor+edgePadding
 val viewportWidth=LocalConfiguration.current.screenWidthDp.dp-76.dp
 val contentWidth=maxOf(viewportWidth,naturalWidth)
 // If the series is shorter than the viewport, keep the same group gaps and center the whole run.
 val extra=(contentWidth-naturalWidth)/2
 val centered=centers.map{it+extra}
 val scroll=rememberScrollState()
 LaunchedEffect(contentWidth,timeline.size){scroll.scrollTo(scroll.maxValue)}
 val plotHeight=125.dp
 val chartHeight=160.dp
 val allBands=referenceBands+listOf(referenceLow to referenceHigh)
 val plotHeightPx=with(density){plotHeight.toPx()}
 val geometry=trendGeometry(points,timelineDates,segmentKeys,allBands,centered.map{with(density){it.toPx()}},plotHeightPx,stackEqualDates)
 val treatmentEvents=trendEventPositions(eventDates,timeline,centered.map{with(density){it.toPx()}})
 val treatmentLineTop=with(density){24.dp.toPx()}
 fun pointPosition(index:Int)=geometry.points[index].let{Offset(it.x,it.y)}
 Box(Modifier.fillMaxWidth().horizontalScroll(scroll)){
  BoxWithConstraints(Modifier.width(contentWidth).height(chartHeight)){
   // Canvas and clickable overlay share the already computed geometry.
   Canvas(Modifier.width(contentWidth).height(plotHeight).semantics{contentDescription="趋势图 $metricKey"}){
    if(points.isEmpty())return@Canvas
    fun y(v:Double)=geometry.scale.y(v,size.height)
    val dash=androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f,10f),0f)
    if(referenceBands.size==points.size&&points.isNotEmpty()){
     points.indices.forEach{i->
      val slot=pointSlots.getOrElse(i){i}
      val center=with(density){centered.getOrElse(slot){contentWidth/2}.toPx()}
      val left=if(slot==0)0f else (center+with(density){centered[slot-1].toPx()})/2f
      val right=if(slot==timeline.lastIndex)size.width else (center+with(density){centered[slot+1].toPx()})/2f
      val (rl,rh)=referenceBands[i]
      referenceBandPixels(rl,rh,size.height,::y)?.let{(top,bottom)->
       drawRect(Good.copy(alpha=.08f),Offset(left,top),Size((right-left).coerceAtLeast(1f),bottom-top))
      }
      rh?.let{drawLine(Good.copy(alpha=.55f),Offset(left,y(it)),Offset(right,y(it)),1.5.dp.toPx(),pathEffect=dash)}
      rl?.let{drawLine(Good.copy(alpha=.55f),Offset(left,y(it)),Offset(right,y(it)),1.5.dp.toPx(),pathEffect=dash)}

     }
    }else when{
     referenceLow!=null&&referenceHigh!=null->{val top=y(referenceHigh);val bottom=y(referenceLow);drawRect(Good.copy(alpha=.08f),Offset(0f,top),Size(size.width,(bottom-top).coerceAtLeast(1f)));drawLine(Good.copy(alpha=.55f),Offset(0f,top),Offset(size.width,top),1.5.dp.toPx(),pathEffect=dash);drawLine(Good.copy(alpha=.55f),Offset(0f,bottom),Offset(size.width,bottom),1.5.dp.toPx(),pathEffect=dash)}
     referenceHigh!=null->{val top=y(referenceHigh);drawLine(Good.copy(alpha=.55f),Offset(0f,top),Offset(size.width,top),1.5.dp.toPx(),pathEffect=dash)}
     referenceLow!=null->{val bottom=y(referenceLow);drawLine(Good.copy(alpha=.55f),Offset(0f,bottom),Offset(size.width,bottom),1.5.dp.toPx(),pathEffect=dash)}
    }
    treatmentEvents.forEach{event->drawLine(TreatmentMarkerGray.copy(alpha=.28f),Offset(event.x,treatmentLineTop),Offset(event.x,size.height),1.dp.toPx())}
    val plotted=points.indices.map(::pointPosition)
    val segments=linePointIndices?.let{selected->
     val valid=selected.filter{it in points.indices}
     trendSegments(valid.map{pointSlots[it]},emptyList()).map{segment->segment.map{valid[it]}}
    }?:geometry.segments
    segments.forEach{indices->val slopes=monotoneTrendSlopes(indices.map{plotted[it]});indices.zipWithNext().forEachIndexed{j,(previous,index)->drawHospitalTrendJoin(plotted[previous],plotted[index],segmentColors.getOrNull(previous)?:color,segmentColors.getOrNull(index)?:color,2.dp.toPx(),slopes[j],slopes[j+1])}}
   }
   points.indices.forEach{i->val at=pointPosition(i);Box(Modifier.offset{androidx.compose.ui.unit.IntOffset((at.x-12.dp.toPx()).roundToInt(),(at.y-12.dp.toPx()).roundToInt())}.size(24.dp).then(if(onPointClick==null)Modifier else Modifier.clickable{onPointClick(i)}).semantics{contentDescription=pointDescriptions.getOrNull(i)?:"趋势点 $metricKey ${i+1}"},contentAlignment=Alignment.Center){Canvas(Modifier.size(8.dp)){drawCircle(segmentColors.getOrNull(i)?:color)}}}
   treatmentEvents.forEach{event->
    Column(Modifier.offset{androidx.compose.ui.unit.IntOffset((event.x-22.dp.toPx()).roundToInt(),0)}.width(44.dp).semantics{contentDescription="化疗日期 ${trendShortDate(event.epochMillis)}"},horizontalAlignment=Alignment.CenterHorizontally){
     Text(trendShortDate(event.epochMillis),fontSize=9.sp,lineHeight=10.sp,color=TreatmentMarkerGray.copy(alpha=.72f),maxLines=1)
     Canvas(Modifier.width(10.dp).height(6.dp)){val triangle=Path().apply{moveTo(0f,0f);lineTo(size.width,0f);lineTo(size.width/2f,size.height);close()};drawPath(triangle,TreatmentMarkerGray.copy(alpha=.38f))}
    }
   }
   timeline.forEachIndexed{slot,date->
    val pointIndex=pointBySlot[slot]?:-1
    val groupWidth=groupWidths[slot]
    val center=centered[slot]
    Column(Modifier.offset(x=center-groupWidth/2,y=plotHeight+4.dp).width(groupWidth),horizontalAlignment=Alignment.CenterHorizontally){
     Text(trendShortDate(date),style=dateStyle.copy(lineHeight=11.sp,color=Muted,textAlign=TextAlign.Center),maxLines=1)
     Text(if(pointIndex>=0)valueLabels.getOrNull(pointIndex).orEmpty().ifBlank{formatTrendValue(points[pointIndex].second)} else "",style=valueStyle.copy(lineHeight=14.sp,color=if(pointIndex>=0)valueColors.getOrNull(pointIndex)?:segmentColors.getOrNull(pointIndex)?:color else color,textAlign=TextAlign.Center),maxLines=1)
    }
   }
  }
 }
}
@Composable internal fun TrendPreviewAxis(points:List<Pair<Long,Double>>,timelineDates:List<Long>,eventDates:List<Long>){
 if(points.isEmpty())return
 BoxWithConstraints(Modifier.fillMaxWidth().height(18.dp)){
  val density=LocalDensity.current
  val widthPx=with(density){maxWidth.toPx()}
  val layout=trendTimeline(points,timelineDates)
  val centers=layout.dates.indices.map{i->if(layout.dates.size==1)widthPx/2 else 8f+(widthPx-16f)*i/(layout.dates.size-1)}
  val events=trendEventPositions(eventDates,layout.dates,centers)
  Text(trendShortDate(points.first().first),Modifier.align(Alignment.CenterStart),fontSize=10.sp,color=Muted)
  Text(trendShortDate(points.last().first),Modifier.align(Alignment.CenterEnd),fontSize=10.sp,color=Muted)
  val endpoints=setOf(trendShortDate(points.first().first),trendShortDate(points.last().first))
  events.filter{trendShortDate(it.epochMillis) !in endpoints}.forEach{event->
   val x=with(density){event.x.toDp()}
   val left=(x-22.dp).coerceIn(0.dp,(maxWidth-44.dp).coerceAtLeast(0.dp))
   Text(trendShortDate(event.epochMillis),Modifier.offset(x=left).width(44.dp).semantics{contentDescription="整体趋势化疗日期 ${trendShortDate(event.epochMillis)}"},fontSize=10.sp,color=TreatmentMarkerGray.copy(alpha=.78f),textAlign=TextAlign.Center,maxLines=1)
  }
 }
}
@Composable internal fun TrendPreviewHeader(metricKey:String,landscape:Boolean,onToggleLandscape:()->Unit){
 Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
  Text("整体趋势 · $metricKey",Modifier.weight(1f),fontWeight=FontWeight.Bold)
  IconButton(onToggleLandscape,modifier=Modifier.size(36.dp)){
   Icon(if(landscape)Icons.Outlined.StayCurrentPortrait else Icons.Outlined.ScreenRotation,if(landscape)"退出横屏" else "横屏显示",tint=Accent)
  }
 }
}
@Composable internal fun TrendPreviewDialogFrame(landscape:Boolean,content:@Composable ()->Unit){
 Box(
  Modifier.then(
   if(landscape)Modifier.fillMaxSize().padding(horizontal=16.dp,vertical=12.dp)
   else Modifier
  ),
  contentAlignment=Alignment.Center
 ){content()}
}
internal fun Modifier.trendPreviewCardSize(landscape:Boolean):Modifier=
 then(if(landscape)Modifier.fillMaxWidth(.88f).fillMaxHeight(.82f) else Modifier)
@Composable fun TrendPreviewDialog(points:List<Pair<Long,Double>>,color:Color,referenceBands:List<Pair<Double?,Double?>>,segmentKeys:List<String>,segmentColors:List<Color> = emptyList(),metricKey:String,onDismiss:()->Unit,referenceNotes:List<String> = emptyList(),timelineDates:List<Long> = points.map{it.first},eventDates:List<Long> = emptyList()){
 val context=LocalContext.current
 val activity=remember(context){context.findActivity()}
 val originalOrientation=remember(activity){activity?.requestedOrientation?:ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED}
 val landscape=LocalConfiguration.current.orientation==Configuration.ORIENTATION_LANDSCAPE
 fun restoreOrientation(){activity?.requestedOrientation=originalOrientation}
 DisposableEffect(activity,originalOrientation){onDispose{restoreOrientation()}}
 BackHandler{restoreOrientation();onDismiss()}
 val cardInteraction=remember{MutableInteractionSource()}
 Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=.55f)).clickable{restoreOrientation();onDismiss()},contentAlignment=Alignment.Center){
  TrendPreviewDialogFrame(landscape){
   Card(modifier=Modifier.trendPreviewCardSize(landscape).clickable(interactionSource=cardInteraction,indication=null){},shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){
    Column(
     Modifier.fillMaxWidth().then(if(landscape)Modifier.fillMaxHeight() else Modifier.verticalScroll(rememberScrollState())).padding(if(landscape)14.dp else 20.dp),
     verticalArrangement=Arrangement.spacedBy(if(landscape)8.dp else 12.dp)
    ){
    TrendPreviewHeader(metricKey,landscape){
     activity?.requestedOrientation=if(landscape)originalOrientation else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }
    if(referenceNotes.isNotEmpty())Text(referenceNotes.joinToString("；"),color=Muted,fontSize=12.sp,maxLines=if(landscape)1 else Int.MAX_VALUE,overflow=TextOverflow.Ellipsis)
    if(points.isEmpty()){Text("暂无数据",color=Muted)}else{
     Canvas(Modifier.fillMaxWidth().then(if(landscape)Modifier.weight(1f) else Modifier.height(160.dp))){
      val layout=trendTimeline(points,timelineDates)
      val centers=layout.dates.indices.map{i->if(layout.dates.size==1)size.width/2 else 8f+(size.width-16f)*i/(layout.dates.size-1)}
      val geometry=trendGeometry(points,timelineDates,segmentKeys,referenceBands,centers,size.height)
      val treatmentEvents=trendEventPositions(eventDates,layout.dates,centers)
      fun y(v:Double)=geometry.scale.y(v,size.height)
      if(referenceBands.size==points.size){
       points.indices.forEach{i->
        val slot=geometry.timeline.pointSlots[i]
        val center=centers[slot]
        val left=if(slot==0)0f else (centers[slot-1]+center)/2f
        val right=if(slot==geometry.timeline.dates.lastIndex)size.width else (center+centers[slot+1])/2f
        val (rl,rh)=referenceBands[i]
        referenceBandPixels(rl,rh,size.height,::y)?.let{(top,bottom)->
         drawRect(Good.copy(alpha=.08f),Offset(left,top),Size((right-left).coerceAtLeast(1f),bottom-top))
        }
        val dash=androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f,10f),0f)
        rh?.let{drawLine(Good.copy(alpha=.55f),Offset(left,y(it)),Offset(right,y(it)),1.5.dp.toPx(),pathEffect=dash)}
        rl?.let{drawLine(Good.copy(alpha=.55f),Offset(left,y(it)),Offset(right,y(it)),1.5.dp.toPx(),pathEffect=dash)}

       }
      }
      treatmentEvents.forEach{event->drawLine(TreatmentMarkerGray.copy(alpha=.28f),Offset(event.x,0f),Offset(event.x,size.height),1.dp.toPx())}
      val plotted=geometry.points.map{Offset(it.x,it.y)}
      geometry.segments.filter{it.size>=2}.forEach{indices->
       val slopes=monotoneTrendSlopes(indices.map{plotted[it]})
       indices.zipWithNext().forEachIndexed{j,(previous,index)->
        drawHospitalTrendJoin(plotted[previous],plotted[index],segmentColors.getOrNull(previous)?:color,segmentColors.getOrNull(index)?:color,2.dp.toPx(),slopes[j],slopes[j+1])
       }
      }
      // Overall preview: connected measurements are represented by the smooth curve only.
      // Only isolated one-point segments get a visible dot.
      geometry.segments.filter{it.size==1}.forEach{indices->
       val i=indices.single();val p=plotted[i]
       drawCircle(segmentColors.getOrNull(i)?:color,4.dp.toPx(),p)
      }
     }
     TrendPreviewAxis(points,timelineDates,eventDates)
    }
    TextButton({restoreOrientation();onDismiss()},Modifier.align(Alignment.End)){Text("关闭")}
    }
   }
  }
 }
}
internal fun trendYearLabel(points:List<Pair<Long,Double>>):String{
 val years=points.map{dateText(it.first).substring(0,4)}.distinct()
 return when(years.size){0->"";1->"${years.single()}年";else->"${years.first()}–${years.last()}年"}
}
internal fun trendShortDate(epochMillis:Long)=dateText(epochMillis).substring(5,10).replace("-","/")
internal fun formatTrendValue(value:Double)=if(value%1.0==0.0)value.toLong().toString() else value.toString().trimEnd('0').trimEnd('.')
@Composable fun Records(m:Modifier,reports:List<LabReport>,entries:List<HealthEntry>,filter:String,setFilter:(String)->Unit,open:(LabReport)->Unit,viewImages:(LabReport)->Unit,addImage:(LabReport)->Unit,edit:(HealthEntry)->Unit,add:(EntryKind)->Unit,title:String="记录",deleteReport:((LabReport)->Unit)?=null){Screen(m,title){
 var deletingReport by remember{mutableStateOf<LabReport?>(null)}
 deletingReport?.let{target->ReportDeleteDialog(target,{deletingReport=null}){deleteReport?.invoke(target);deletingReport=null}}
 var recordSearch by rememberSaveable{mutableStateOf("")}
 val searchContext=LocalContext.current
 val searchStore=remember(searchContext){HealthStore(searchContext)}
 val searchAliases=searchStore.metricAliases()
 val searchNames=reports.flatMap{it.results}.associate{canonicalMetricKey(it.metricKey) to searchStore.preferredMetricName(it.metricKey).orEmpty()}
 DataSearchField(recordSearch,{recordSearch=it},"搜索全部报告、数据和记录文字")
 val types=reports.map{ReportNaming.title(it).category}.distinct().sorted()
 val filters=listOf("全部")+types
 Row(Modifier.horizontalScroll(rememberScrollState())){filters.forEach{t->FilterChip(filter==t,{setFilter(t)},label={Text(t)},modifier=Modifier.padding(end=8.dp),border=null,colors=FilterChipDefaults.filterChipColors(containerColor=Color.White,selectedContainerColor=SelectedTint))}}
 val visible=reports.filter{(recordSearch.isNotBlank()||filter=="全部"||ReportNaming.title(it).category==filter)&&DataSearch.report(recordSearch,it,searchAliases,searchNames)}
 val grouped=visible.groupBy{ReportNaming.title(it).category}.toList().sortedBy{it.first}
 if(grouped.isEmpty())Paper{Text(if(recordSearch.isBlank())"暂无检查报告" else "没有匹配的检查报告")}
 grouped.forEach{(type,items)->
  Text(type,fontWeight=FontWeight.Bold,fontSize=18.sp)
  items.sortedByDescending{it.testedAtEpochMillis}.chunked(2).forEach{pair->
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.Top){
    pair.forEach{r->Column(Modifier.weight(1f)){
     ReportCard(r,{open(r)},{viewImages(r)},{addImage(r)})
     if(deleteReport!=null)TextButton({deletingReport=r},Modifier.align(Alignment.End)){Text("删除报告",color=Bad)}
    }}
    if(pair.size==1)Spacer(Modifier.weight(1f))
   }
  }
 }
 if(recordSearch.isNotBlank())entries.filter{DataSearch.entry(recordSearch,it)}.sortedByDescending{it.occurredAtEpochMillis}.forEach{e->
  Paper(Modifier.clickable{edit(e)}){Text(e.kind.title+" · "+e.title,fontWeight=FontWeight.Bold);Text(dateText(e.occurredAtEpochMillis),color=Muted);Text(listOf(e.note,e.dose,e.frequency,e.route).filter{it.isNotBlank()}.joinToString(" · "))}
 }

}}
@Composable fun SymptomReport(entries:List<HealthEntry>){
 var start by rememberSaveable{mutableStateOf(LocalDate.now().minusDays(30).toString())};var end by rememberSaveable{mutableStateOf(LocalDate.now().toString())}
 Field(start,{start=it},"开始日期 YYYY-MM-DD");Field(end,{end=it},"结束日期 YYYY-MM-DD")
 val from=parseDate(start);val until=parseDate(end)?.let{Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).plusDays(1).toInstant().toEpochMilli()}
 if(from==null||until==null||from>=until){Text("请填写有效日期范围",color=Bad);return}
 val selected=entries.filter{it.occurredAtEpochMillis>=from&&it.occurredAtEpochMillis<until}
 val text=symptomReportText(start,end,selected)
 val ctx=LocalContext.current
 Paper{Text("期间共 ${selected.size} 次记录",fontWeight=FontWeight.Bold);selected.groupBy{it.title}.forEach{(name,rows)->Text("$name · ${rows.size} 次 · 最高 ${rows.maxOf{it.severity}}/10");Spark(rows.sortedBy{it.occurredAtEpochMillis}.map{it.occurredAtEpochMillis to it.severity.toDouble()},Accent)}}
 Button({shareText(ctx,"症状报告",text)},Modifier.fillMaxWidth()){Text("分享症状报告")}
 selected.forEach{e->Paper{Text(e.title+" · "+dateText(e.occurredAtEpochMillis),fontWeight=FontWeight.Bold);Text("程度 ${e.severity}/10  ${e.frequency}  ${e.duration}");Text(e.note)}}
}

/** The target is explicit so cancel cannot mutate records or delete a neighbouring card. */
@Composable internal fun ReportDeleteDialog(report:LabReport,cancel:()->Unit,confirm:()->Unit){
 AlertDialog(onDismissRequest=cancel,title={Text("删除这份报告？")},
  text={Text("${report.hospitalKey}\n${ReportNaming.title(report).category} · ${dateText(report.testedAtEpochMillis)}\n${report.results.size} 个项目")},
  confirmButton={TextButton(confirm){Text("确认删除",color=Bad)}},
  dismissButton={TextButton(cancel){Text("取消")}})
}
