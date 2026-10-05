package com.combustible12.healthtrend

import android.os.Bundle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import java.time.*
import java.time.format.DateTimeFormatter

val Warm=Color(0xFFF6F6F6);val Ink=Color(0xFF292927);val Muted=Color(0xFF817E78)
val Accent=Color(0xFFF28B58);val Good=Color(0xFF28A957);val Bad=Color(0xFFF04444);val TrendBlue=Color(0xFF3F7FE8)
private val stamp=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
fun dateText(n:Long)=Instant.ofEpochMilli(n).atZone(ZoneId.systemDefault()).format(stamp)
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
 val ctx=LocalContext.current;val store=remember{HealthStore(ctx)}
 var revision by remember{mutableIntStateOf(0)};var error by remember{mutableStateOf<String?>(null)}
 var tab by rememberSaveable{mutableIntStateOf(0)};var recordsFilter by rememberSaveable{mutableStateOf("全部")}
 var draft by rememberSaveable(stateSaver=diskStateSaver<ReportDraft?>(ctx,"root-report")){mutableStateOf<ReportDraft?>(null)};var report by rememberSaveable(stateSaver=diskStateSaver<LabReport?>(ctx,"root-report-view")){mutableStateOf<LabReport?>(null)}
 var entry by rememberSaveable(stateSaver=diskStateSaver<HealthEntry?>(ctx,"root-entry")){mutableStateOf<HealthEntry?>(null)};var viewer by rememberSaveable{mutableStateOf<List<String>?>(null)}
 var template by rememberSaveable(stateSaver=diskStateSaver<HospitalLabTemplate?>(ctx,"root-template")){mutableStateOf<HospitalLabTemplate?>(null)}
 var imageDocument by rememberSaveable(stateSaver=diskStateSaver<ImageDocument?>(ctx,"root-image-document")){mutableStateOf<ImageDocument?>(null)}
 var courseRecord by rememberSaveable(stateSaver=diskStateSaver<CourseRecord?>(ctx,"root-course-record")){mutableStateOf<CourseRecord?>(null)}
 var imageDocumentPage by rememberSaveable{mutableIntStateOf(0)};var imageDocumentMatches by rememberSaveable{mutableStateOf<List<Int>>(emptyList())}
 val reports=remember(revision){runCatching{store.reports()}.getOrElse{error="历史数据读取失败：${it.message}";emptyList()}}
 val entries=remember(revision){runCatching{store.entries()}.getOrElse{error="历史记录读取失败：${it.message}";emptyList()}}
 val templates=remember(revision){runCatching{store.templates()}.getOrElse{error="模板读取失败：${it.message}";emptyList()}}
 val courseRecords=remember(revision){runCatching{store.courseRecords()}.getOrElse{error="病程记录读取失败：${it.message}";emptyList()}}
 fun change(block:()->Unit){try{block();revision++}catch(e:Exception){error=e.message?:"操作失败"}}
 val importer=rememberReportImport(store,{draft=it},{error=it},deliver=draft==null&&report==null&&entry==null&&template==null&&imageDocument==null&&courseRecord==null&&viewer==null)
 Box(Modifier.fillMaxSize()){
 Box(Modifier.fillMaxSize().then(if(draft!=null||report!=null||entry!=null||template!=null||imageDocument!=null||courseRecord!=null||viewer!=null)Modifier.clearAndSetSemantics{} else Modifier)){
 Scaffold(containerColor=Warm,bottomBar={NavigationBar(containerColor=Color.White){listOf("首页" to Icons.Outlined.Home,"趋势" to Icons.Outlined.ShowChart,"病程" to Icons.Outlined.Timeline,"图片资料" to Icons.Outlined.PhotoLibrary,"我的" to Icons.Outlined.Person).forEachIndexed{i,p->NavigationBarItem(selected=tab==i,onClick={tab=i},icon={Icon(p.second,p.first)},label={Text(p.first,fontSize=11.sp)},alwaysShowLabel=true)}}}){padding->
  val m=Modifier.padding(padding)
  when(tab){
   0->Home(m,reports,entries,importer,{kind->if(kind==null)tab=1 else entry=HealthEntry(kind=kind,title="",occurredAtEpochMillis=System.currentTimeMillis())},{report=it},{tab=2})
   1->Trends(m,store,reports,revision,{key,value->change{store.setPrimary(key,value)}},{viewer=it},{r,x,v->change{store.updateValue(r.id,x.id,v)}})
    2->CourseRecordsPage(m,courseRecords,{courseRecord=it},{
     val zone=java.time.ZoneId.systemDefault();val today=java.time.LocalDate.now(zone)
     val todayStart=today.atStartOfDay(zone).toInstant().toEpochMilli()
     val existing=courseRecords.firstOrNull{java.time.Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate()==today}
     courseRecord=existing?:CourseRecord(date=todayStart,title="")
    },{record->change{store.deleteCourseRecord(record.id)}},{images,index->viewer=images.drop(index)+images.take(index)})
    3->ImageDocumentsPage(m){document,page,matches->imageDocument=document;imageDocumentPage=page;imageDocumentMatches=matches}
    4->Mine(m,templates,{template=it},store,{error=it})
  }
 }
 }
 CompositionLocalProvider(LocalPageVisible provides (viewer==null)){
 if(draft!=null)ReportEditor(draft!!,store,{draft=null},{d->change{
   val existing=store.latestTemplate(d.hospital,d.type,d.system)
   val t=existing?:store.confirmTemplate(d.hospital,d.type,d.parsed(),d.system,false)
   val r=store.buildReport(d.hospital,d.type,preserveTimestamp(d.date,d.existing?.testedAtEpochMillis)!!,d.images,d.parsed(),t,d.ocr,d.system)
   val version=if(d.existing!=null&&d.hospital==d.existing.hospitalKey&&d.type==d.existing.reportType&&d.system==d.existing.systemKey)d.existing.templateVersion else t.version
   store.saveReport(if(d.existing==null)r else r.copy(id=d.existing.id,templateVersion=version,results=r.results.mapIndexed{i,x->x.copy(id=d.rows[i].id,reportId=d.existing.id,templateVersion=version,editedByUser=true)}))
   draft=null
  }},{viewer=it})
 if(report!=null){val current=reports.firstOrNull{it.id==report!!.id}?:report!!;ReportDetail(current,store,{report=null},{draft=ReportDraft.from(current);report=null},{viewer=it},{change{store.deleteReport(current.id);report=null}},{result,value,text->change{store.updateValue(current.id,result.id,value,text)}},{revision++})}
 if(entry!=null)EntryEditor(entry!!,store,{entry=null},{e->change{store.saveEntry(e);entry=null}},{change{store.deleteEntry(entry!!.id);entry=null}},{viewer=it})
 if(template!=null)TemplateEditor(template!!,{template=null},{fields->change{store.saveTemplateFields(template!!,fields);template=null}})
 if(imageDocument!=null)ImageDocumentViewer(imageDocument!!,imageDocumentPage,imageDocumentMatches,{imageDocument=null},{saved->ImageDocumentStore(ctx).save(saved);imageDocument=saved},{deleted->ImageDocumentStore(ctx).delete(deleted);imageDocument=null})
 if(courseRecord!=null){val current=courseRecords.firstOrNull{it.id==courseRecord!!.id}?:courseRecord!!;CourseRecordEditor(current,store,{courseRecord=null},{saved->change{store.saveCourseRecord(saved);courseRecord=null}},{change{store.deleteCourseRecord(current.id);courseRecord=null}},{images,index->viewer=images.drop(index)+images.take(index)})}
 }
 if(viewer!=null)SourceViewer(viewer!!,{viewer=null})
 if(error!=null)AlertDialog(onDismissRequest={error=null},title={Text("操作未完成")},text={Text(error!!)},confirmButton={TextButton({error=null}){Text("知道了")}})
 }
}
@Composable fun Screen(m:Modifier,title:String,subtitle:String="",spacing:androidx.compose.ui.unit.Dp=14.dp,content:@Composable ColumnScope.()->Unit){
 val scroll=rememberScrollState();val scope=rememberCoroutineScope()
 Box(m.fillMaxSize()){
  Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(20.dp),verticalArrangement=Arrangement.spacedBy(spacing)){
   Spacer(Modifier.height(6.dp));Text(title,fontSize=28.sp,fontWeight=FontWeight.Bold);if(subtitle.isNotBlank())Text(subtitle,color=Muted);content()
   if(scroll.maxValue>0)OutlinedButton({scope.launch{scroll.animateScrollTo(0)}},Modifier.align(Alignment.CenterHorizontally)){Icon(Icons.Outlined.VerticalAlignTop,null);Spacer(Modifier.width(6.dp));Text("回到顶部")}
   Spacer(Modifier.height(12.dp))
  }
  ScrollProgressRail(scroll,Modifier.align(Alignment.CenterEnd).padding(top=20.dp,bottom=20.dp,end=2.dp).width(24.dp).fillMaxHeight())
 }
}

@Composable fun ScrollablePageColumn(modifier:Modifier=Modifier,padding:PaddingValues=PaddingValues(0.dp),arrangement:Arrangement.Vertical=Arrangement.Top,content:@Composable ColumnScope.()->Unit){
 val scroll=rememberScrollState();val scope=rememberCoroutineScope()
 Box(modifier){
  Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(padding),verticalArrangement=arrangement){content();if(scroll.maxValue>0)OutlinedButton({scope.launch{scroll.animateScrollTo(0)}},Modifier.align(Alignment.CenterHorizontally)){Icon(Icons.Outlined.VerticalAlignTop,null);Spacer(Modifier.width(6.dp));Text("回到顶部")}}
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
@Composable fun Paper(m:Modifier=Modifier,content:@Composable ColumnScope.()->Unit){Card(modifier=m.fillMaxWidth(),shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content)}}
@Composable fun TrendPaper(content:@Composable ColumnScope.()->Unit){Card(modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){Column(Modifier.padding(start=18.dp,end=18.dp,top=18.dp,bottom=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp),content=content)}}
@Composable fun Home(m:Modifier,reports:List<LabReport>,entries:List<HealthEntry>,importer:ImportActions,quick:(EntryKind?)->Unit,open:(LabReport)->Unit,timeline:()->Unit){
 var showPaste by rememberSaveable{mutableStateOf(false)};var pastedText by rememberSaveable{mutableStateOf("")}
 Screen(m,"HealthTrend","把检查、症状和病历放在一条清楚的时间线上"){
 Card(shape=RoundedCornerShape(28.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFFFFEEE5))){Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text("今天要记录什么？",fontSize=21.sp,fontWeight=FontWeight.Bold);Button(importer.camera,Modifier.fillMaxWidth().height(54.dp),shape=RoundedCornerShape(18.dp),enabled=!importer.busy){Icon(Icons.Outlined.DocumentScanner,null,tint=Color.White);Spacer(Modifier.width(8.dp));Text("拍照识别检查报告",color=Color.White)};Row(Modifier.horizontalScroll(rememberScrollState())){TextButton(importer.gallery,enabled=!importer.busy){Text("相册导入")};TextButton({showPaste=true},enabled=!importer.busy){Text("粘贴报告数据")};TextButton(importer.manual,enabled=!importer.busy){Text("手动录入")}};if(importer.busy)LinearProgressIndicator(Modifier.fillMaxWidth());if(importer.message.isNotBlank())Text(importer.message,fontSize=12.sp)}}
 Text("健康记录",fontSize=20.sp,fontWeight=FontWeight.Bold)
 Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){Quick("检查指标","趋势与异常",Icons.Outlined.MonitorHeart,Modifier.weight(1f)){quick(null)};Quick("症状记录","程度与频率",Icons.Outlined.EditNote,Modifier.weight(1f)){quick(EntryKind.SYMPTOM)}}
 Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){Quick("病历资料","报告与影像",Icons.Outlined.Description,Modifier.weight(1f)){quick(EntryKind.MEDICAL)};Quick("用药记录","时间与备注",Icons.Outlined.Medication,Modifier.weight(1f)){quick(EntryKind.MEDICATION)}}
 Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text("最近",fontSize=20.sp,fontWeight=FontWeight.Bold);TextButton(timeline){Text("病程时间轴")}}
 if(reports.isEmpty()&&entries.isEmpty())Paper{Text("还没有记录")}
 reports.take(2).forEach{r->ReportCard(r){open(r)}}
 entries.take(2).forEach{e->Paper{Text(e.kind.title+" · "+e.title,fontWeight=FontWeight.Bold);Text(dateText(e.occurredAtEpochMillis),color=Muted);Text(e.note.ifBlank{listOf(e.dose,e.frequency).filter{it.isNotBlank()}.joinToString(" · ")})}}
 }
 if(showPaste)AlertDialog(onDismissRequest={showPaste=false},title={Text("粘贴报告数据")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Text("复制聊天整理好的医院、日期和指标数据，粘贴后进入核对。",color=Muted,fontSize=12.sp);OutlinedTextField(pastedText,{pastedText=it},modifier=Modifier.fillMaxWidth().heightIn(min=220.dp),label={Text("报告数据")})}},confirmButton={TextButton({importer.paste(pastedText);showPaste=false;pastedText=""},enabled=pastedText.isNotBlank()&&!importer.busy){Text("进入核对")}},dismissButton={TextButton({showPaste=false}){Text("取消")}})
}
@Composable fun Quick(t:String,s:String,icon:androidx.compose.ui.graphics.vector.ImageVector,m:Modifier,onClick:()->Unit){Card(onClick=onClick,modifier=m,shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){Column(Modifier.padding(18.dp)){Icon(icon,null,tint=Accent);Spacer(Modifier.height(20.dp));Text(t,fontWeight=FontWeight.Bold);Text(s,color=Muted,fontSize=12.sp)}}}
@Composable fun ReportCard(r:LabReport,open:()->Unit){Paper(Modifier.clickable(onClick=open)){Text(r.reportType,fontWeight=FontWeight.Bold);Text("${r.hospitalKey} · ${dateText(r.testedAtEpochMillis)}",color=Muted,fontSize=12.sp);Text("${r.results.size} 个项目 · ${r.results.count{it.status()==ResultStatus.HIGH||it.status()==ResultStatus.LOW}} 个超出参考范围");}}
@Composable fun Trends(m:Modifier,store:HealthStore,reports:List<LabReport>,revision:Int,priority:(String,Boolean)->Unit,images:(List<String>)->Unit,edit:(LabReport,LabResult,Double)->Unit){
 var category by rememberSaveable{mutableStateOf("血常规")};var mode by rememberSaveable{mutableStateOf("重点指标")};var query by rememberSaveable{mutableStateOf("")};var range by rememberSaveable{mutableStateOf("全部")};var weightVersion by remember{mutableIntStateOf(0)};var showWeight by remember{mutableStateOf(false)};var weightText by remember{mutableStateOf("")};var weightDate by remember{mutableStateOf("")}
 var selected by remember{mutableStateOf<Pair<LabReport,LabResult>?>(null)}
 var editing by remember{mutableStateOf(false)};var editValue by remember{mutableStateOf("")}
 var imageError by remember{mutableStateOf<String?>(null)};var imageBusy by remember{mutableStateOf(false)}
 var imageTargetReportId by remember{mutableStateOf<String?>(null)}
 val imageScope=rememberCoroutineScope()
 val imagePicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){uris->
  val reportId=imageTargetReportId;imageTargetReportId=null
  if(reportId!=null&&uris.isNotEmpty()){imageBusy=true;imageScope.launch{try{withContext(kotlinx.coroutines.Dispatchers.IO){store.addReportImages(reportId,uris)};val refreshed=store.reports().firstOrNull{it.id==reportId};if(refreshed!=null){val old=selected?.second;val freshResult=old?.let{o->refreshed.results.firstOrNull{it.id==o.id}};if(freshResult!=null)selected=refreshed to freshResult}}catch(x:Exception){imageError="原图保存失败：${x.message}"}finally{imageBusy=false}}}
 }
 fun fixedTrendTitle(metricKey:String):String?=when(metricKey.trim().uppercase()){
  "WBC"->"白细胞 WBC";"#NEUT","NEUT#"->"中性粒细胞计数 #NEUT";"%NEUT","NEUT%"->"中性粒细胞百分比 %NEUT"
  "#LYMPH","LYMPH#"->"淋巴细胞计数 #LYMPH";"%LYMPH","LYMPH%"->"淋巴细胞百分比 %LYMPH"
  "#MONO","MONO#"->"单核细胞计数 #MONO";"%MONO","MONO%"->"单核细胞百分比 %MONO"
  "#EOS","EOS#"->"嗜酸性粒细胞计数 #EOS";"%EOS","EOS%"->"嗜酸性粒细胞百分比 %EOS"
  "#BASO","BASO#"->"嗜碱性粒细胞计数 #BASO";"%BASO","BASO%"->"嗜碱性粒细胞百分比 %BASO"
  "RBC"->"红细胞 RBC";"HGB"->"血红蛋白 HGB";"HCT"->"红细胞压积 HCT";"MCV"->"红细胞平均体积 MCV"
  "MCH"->"平均红细胞血红蛋白量 MCH";"MCHC"->"平均血红蛋白浓度 MCHC";"RDW"->"红细胞分布宽度 RDW";"RDW-SD"->"红细胞分布宽度SD RDW-SD"
  "PLT"->"血小板 PLT";"PCT"->"血小板压积 PCT";"MPV"->"平均血小板体积 MPV";"PDW"->"血小板分布宽度 PDW"
  "P-LCR"->"大型血小板比率 P-LCR";"NRBC%"->"有核红细胞比率 NRBC%";"NRBC#"->"有核红细胞计数 NRBC#";"P-LCC"->"大血小板数目 P-LCC"
  "TP"->"总蛋白 TP";"ALB"->"白蛋白 ALB";"GLOB"->"球蛋白 GLOB";"A/G"->"白球比 A/G";"TBIL"->"总胆红素 TBIL"
  "DBIL"->"直接胆红素 DBIL";"IBIL"->"间接胆红素 IBIL";"ALT"->"谷丙转氨酶 ALT";"AST"->"谷草转氨酶 AST"
  "GGT"->"谷氨酰转肽酶 GGT";"AST/ALT"->"谷草/谷丙 AST/ALT";"ALP"->"碱性磷酸酶 ALP";"CHE"->"胆碱酯酶 CHE"
  "TBA"->"总胆汁酸 TBA";"PA"->"前白蛋白 PA";"UREA"->"尿素 UREA";"CREA"->"肌酐 CREA";"UA"->"尿酸 UA"
  else->null
 }
 fun trendIdentity(result:LabResult):String{val stored=ReportParser.key(result.metricKey);val n=result.rawName;return if(stored=="P-LCR"&&(n.contains("大小血小板数目")||n.contains("大血小板数目")||n.contains("大型血小板数目"))&&!n.contains("比率"))"P-LCC" else stored}
 val all=reports.flatMap{r->r.results.map{x->r to x}}.groupBy{(_,result)->trendIdentity(result)}
 val weights=remember(weightVersion,revision){store.weightRecords()}
 Screen(m,"指标趋势",spacing=3.5.dp){
  Row(Modifier.horizontalScroll(rememberScrollState())){(listOf("血常规","肝功能","肾功能","肿瘤标志物","体重")+reports.map{it.reportType}.distinct().filterNot{it in setOf("血常规","肝功能","肾功能","肿瘤标志物","体重")}).forEach{t->FilterChip(category==t,{category=t},label={Text(t)},modifier=Modifier.padding(end=8.dp))}}
  Row{listOf("重点指标","其他指标").forEach{t->FilterChip(mode==t,{mode=t},label={Text(t)},modifier=Modifier.padding(end=8.dp))}}
  Row(Modifier.horizontalScroll(rememberScrollState())){listOf("近3月","近6月","近1年","全部").forEach{t->FilterChip(range==t,{range=t},label={Text(t)},modifier=Modifier.padding(end=8.dp))}}
  BasicTextField(query,{query=it},Modifier.fillMaxWidth().height(16.dp),singleLine=true,textStyle=LocalTextStyle.current.copy(fontSize=11.sp,color=Ink),decorationBox={inner->Row(Modifier.fillMaxSize().border(1.dp,Color(0xFF7B7B82),RoundedCornerShape(8.dp)).padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.weight(1f)){if(query.isEmpty())Text("查找指标",fontSize=11.sp,color=Muted);inner()};if(query.isNotEmpty())Icon(Icons.Outlined.Clear,"清空搜索",Modifier.size(12.dp).clickable{query=""},tint=Muted)}})
  if(category=="体重"){
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){TextButton({weightText="";weightDate=java.time.LocalDate.now().toString();showWeight=true}){Text("+ 记录体重")}}
   if(weights.isEmpty())Paper{Text("暂无体重记录")} else TrendPaper{
    val w=weights.last()
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.Bottom){Text("体重",fontWeight=FontWeight.Bold,fontSize=16.sp);Row(verticalAlignment=Alignment.Bottom){Text(String.format(java.util.Locale.US,"%.1f",w.kilograms),fontWeight=FontWeight.Bold,fontSize=20.sp,color=Good);Spacer(Modifier.width(4.dp));Text("kg",fontSize=11.sp,color=Good)}}
    Spark(points=weights.map{it.measuredAtEpochMillis to it.kilograms},color=TrendBlue,referenceLow=null,referenceHigh=null,onPointClick={},metricKey="WEIGHT",pointDescriptions=weights.map{"体重 "+String.format(java.util.Locale.US,"%.1f kg",it.kilograms)},valueLabels=weights.map{String.format(java.util.Locale.US,"%.1f",it.kilograms)},onShowPreview={})
   }
  }
  val filtered=all.filter{(key,list)->store.isPrimary(key)==(mode=="重点指标") && list.any{(r,x)->trendCategoryMatches(category,r,x)&&(x.rawName.contains(query,true)||key.contains(query,true))}}
  if(category!="体重"&&filtered.isEmpty())Paper{Text("暂无符合条件的指标")}
  if(category!="体重")filtered.forEach{(key,list)->
   val cutoff=when(range){"近3月"->System.currentTimeMillis()-90L*86400000L;"近6月"->System.currentTimeMillis()-183L*86400000L;"近1年"->System.currentTimeMillis()-365L*86400000L;else->Long.MIN_VALUE}
   val points=list.filter{trendCategoryMatches(category,it.first,it.second)&&it.first.testedAtEpochMillis>=cutoff}.sortedBy{it.first.testedAtEpochMillis}
   if(points.isEmpty())return@forEach
   val latest=points.last().second
   val latestStatus=latest.status()
   val valueColor=when(latestStatus){ResultStatus.NORMAL->Good;ResultStatus.LOW,ResultStatus.HIGH->Bad;else->Ink}
   TrendPaper{
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.Top){
     Column(Modifier.weight(1f)){
      val fixedTitle=fixedTrendTitle(key) ?: latest.rawName.trim()
      Text(fixedTitle,fontWeight=FontWeight.Bold,fontSize=16.sp,color=Ink,maxLines=1,overflow=TextOverflow.Ellipsis)
      val trendMeaning=points.asReversed().firstNotNullOfOrNull{(report,_)->store.latestTemplate(report.hospitalKey,report.reportType,report.systemKey)?.fields?.firstOrNull{ReportParser.key(it.metricKey)==ReportParser.key(key)}?.trendMeaning?.takeIf(String::isNotBlank)} ?: metricPurpose(key)
      trendMeaning?.let{Text(it,color=Accent,fontSize=12.sp,fontWeight=FontWeight.Medium,maxLines=1,overflow=TextOverflow.Ellipsis)}
      Text("参考范围: ${rangeText(latest.referenceLowAtTest,latest.referenceHighAtTest)} ${displayLabUnit(latest.unitAtTest)}",color=Muted,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
     }
     Column(horizontalAlignment=Alignment.End){
      Row(verticalAlignment=Alignment.Bottom){
       Text(latest.textValue,fontWeight=FontWeight.Bold,fontSize=20.sp,color=valueColor,maxLines=1)
       Spacer(Modifier.width(4.dp))
       Text(displayLabUnit(latest.unitAtTest),fontWeight=FontWeight.Normal,fontSize=11.sp,color=valueColor,maxLines=1,modifier=Modifier.padding(bottom=2.dp))
      }
      Text(latestStatus.label(),color=valueColor,fontSize=11.sp)
     }
    }
    var previewSeries by remember{mutableStateOf<List<Pair<LabReport,LabResult>>?>(null)}
    Row(Modifier.fillMaxWidth().offset(y=(-8).dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
     Text(latest.hospitalKey.ifBlank{"医院未录入"},color=Muted,fontSize=11.sp,maxLines=1)
     TextButton(onClick={previewSeries=points.filter{it.second.normalizedValue!=null&&it.second.comparator.isEmpty()}},modifier=Modifier.heightIn(min=32.dp),contentPadding=PaddingValues(horizontal=8.dp,vertical=0.dp)){Text("整体",fontSize=12.sp,color=Accent)}
    }
    listOf(points.filter{it.second.normalizedValue!=null&&it.second.comparator.isEmpty()}).forEach{series->
     val sx=series.last().second
     val bounds=sx.trendReferenceRange()
     val hasHistoricalAbnormal=series.any{(_,result)->result.status()==ResultStatus.HIGH||result.status()==ResultStatus.LOW}
     val trendColor=if(hasHistoricalAbnormal) Bad else TrendBlue
     Spark(
      points=series.map{it.first.testedAtEpochMillis to it.second.normalizedValue!!},
      color=trendColor,
      referenceLow=bounds.first,
      referenceHigh=bounds.second,
      onPointClick={index->selected=series[index]},
      metricKey=key,
      pointDescriptions=series.map{trendPointContentDescription(key,it.first.testedAtEpochMillis)},
      valueLabels=series.map{it.second.textValue},
      onShowPreview={previewSeries=series}
     )
    }
    TextButton({priority(key,mode!="重点指标")},modifier=Modifier.heightIn(min=28.dp),contentPadding=PaddingValues(horizontal=8.dp,vertical=0.dp)){Text(if(mode=="重点指标") "移到其他指标" else "设为重点指标",fontSize=12.sp)}
    previewSeries?.let{series->
     val sx=series.last().second
     val bounds=sx.trendReferenceRange()
     TrendPreviewDialog(series.map{it.first.testedAtEpochMillis to it.second.normalizedValue!!},Accent,bounds.first,bounds.second,latest.rawName,onDismiss={previewSeries=null})
    }
   }
  }
 }
 if(showWeight)AlertDialog(onDismissRequest={showWeight=false},title={Text("记录体重")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(weightDate,{weightDate=it},label={Text("日期 YYYY-MM-DD")},singleLine=true);OutlinedTextField(weightText,{weightText=it.filter{ch->ch.isDigit()||ch=='.'}},label={Text("体重 kg")},singleLine=true)}},confirmButton={TextButton({val kg=weightText.toDoubleOrNull();val day=runCatching{java.time.LocalDate.parse(weightDate)}.getOrNull();if(kg!=null&&kg>0&&day!=null){val at=day.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();store.saveWeight(WeightRecord(measuredAtEpochMillis=at,kilograms=kg));weightVersion++;showWeight=false}}){Text("保存")}},dismissButton={TextButton({showWeight=false}){Text("取消")}})
 selected?.let{(r,x)->
  AlertDialog(onDismissRequest={selected=null;editing=false},title={Text(x.rawName)},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
   Text(dateText(r.testedAtEpochMillis),color=Muted);Text(r.hospitalKey.ifBlank{"医院未录入"},fontWeight=FontWeight.Medium)
   ResultValueUnit(x.textValue,x.unitAtTest)
   Text("当次参考：${rangeText(x.referenceLowAtTest,x.referenceHighAtTest)} · ${x.status().label()}",color=Muted)
   if(editing)OutlinedTextField(editValue,{editValue=it},label={Text("结果")},singleLine=true)
  }},confirmButton={
   if(editing)TextButton({val v=editValue.trim().toDoubleOrNull();if(v!=null&&v.isFinite()){edit(r,x,v);val persisted=store.reports().firstOrNull{it.id==r.id}?.results?.firstOrNull{it.id==x.id}?:x.withEditedValue(v);selected=r to persisted;editing=false}}){Text("保存")}
   else TextButton({editValue=x.value?.toString().orEmpty();editing=true}){Text("编辑数值")}
  },dismissButton={Row{
   if(r.sourceImages.isNotEmpty())TextButton({selected=null;images(r.sourceImages.map{it.uri})}){Text("查看原报告")} else TextButton({imageTargetReportId=r.id;imagePicker.launch(arrayOf("image/*"))},enabled=!imageBusy){Text("导入原报告")}
   TextButton({selected=null;editing=false}){Text("关闭")}
  }})
 }
 if(imageError!=null)AlertDialog(onDismissRequest={imageError=null},title={Text("操作未完成")},text={Text(imageError!!)},confirmButton={TextButton({imageError=null}){Text("知道了")}})
}
fun trendCategoryMatches(category:String,report:LabReport,result:LabResult):Boolean{
 val key=ReportParser.key(result.metricKey.ifBlank{result.rawName})
 val liver=setOf("ALT","AST","GGT","ALP","TBIL","DBIL","IBIL","TBA","TP","ALB","GLOB","A/G","PA")
 val kidney=setOf("CREA","UREA","BUN","UA","EGFR")
 val cbc=setOf("WBC","RBC","HGB","HCT","MCV","MCH","MCHC","PLT","NEUT#","LYMPH#","MONO#","EOS#","BASO#")
 return when(category){
  "肝功能"->key in liver
  "肾功能"->key in kidney
  "血常规"->key in cbc||report.reportType=="血常规"
  "肿瘤标志物"->report.reportType=="肿瘤标志物"
  else->report.reportType==category
 }
}
fun metricPurpose(metricKey:String):String?=when(ReportParser.key(metricKey)){
 "WBC"->"↑感染/炎症 / ↓感染防御不足"
 "NEUT#"->"↑细菌感染/炎症 / ↓感染防御不足"
 "NEUT%"->"↑细菌感染/炎症"
 "LYMPH#","LYMPH%"->"↑病毒感染等"
 "MONO#","MONO%"->"↑感染/炎症"
 "EOS#","EOS%"->"↑过敏/寄生虫感染"
 "BASO#","BASO%"->"↑过敏/炎症"
 "RBC"->"↓贫血 / ↑脱水等"
 "HGB"->"↓贫血"
 "HCT"->"↓贫血 / ↑脱水等"
 "MCV"->"↑缺B12/叶酸等 / ↓缺铁等"
 "MCH","MCHC"->"↓缺铁等"
 "RDW","RDW-SD"->"↑贫血原因参考"
 "PLT"->"↑血栓 / ↓出血"
 "PCT","MPV","PDW","P-LCR","P-LCC"->"血小板辅助指标"
 "NRBC%","NRBC#"->"↑造血异常等"
 "ALT"->"↑肝细胞损伤"
 "AST"->"↑肝脏/肌肉损伤"
 "GGT"->"↑肝胆异常"
 "ALP"->"↑胆道/骨骼异常"
 "TBIL"->"↑肝胆/溶血异常"
 "DBIL"->"↑胆汁排出不畅"
 "IBIL"->"↑溶血/肝脏处理异常"
 "TBA"->"↑肝胆异常"
 "TP","ALB","PA"->"↓营养不足/肝合成减少"
 "GLOB"->"↑炎症/免疫异常"
 "A/G"->"↓肝脏/炎症问题"
 "AST/ALT"->"肝功能辅助指标"
 "CHE"->"↓肝脏合成能力不足"
 "UREA"->"↑肾脏排出减少/脱水"
 "CREA"->"↑肾功能下降"
 "UA"->"↑痛风/肾脏负担"
 else->null
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
@Composable fun Spark(points:List<Pair<Long,Double>>,color:Color,referenceLow:Double?=null,referenceHigh:Double?=null,onPointClick:((Int)->Unit)?=null,metricKey:String="",pointDescriptions:List<String> = emptyList(),valueLabels:List<String> = emptyList(),onShowPreview:()->Unit={}){
 fun pointPosition(index:Int,width:Float,height:Float)=trendPointPosition(points,index,width,height,referenceLow,referenceHigh)
 val viewportWidth=LocalConfiguration.current.screenWidthDp.dp-76.dp
 val plotHeight=125.dp
 val chartHeight=160.dp
 Box(Modifier.fillMaxWidth()){
  BoxWithConstraints(Modifier.fillMaxWidth().height(chartHeight)){
   val chartWidth=constraints.maxWidth
   val plotHeightPx=with(LocalDensity.current){plotHeight.roundToPx()}
   Canvas(Modifier.fillMaxWidth().height(plotHeight).semantics{contentDescription="趋势图 $metricKey"}){
    if(points.isEmpty())return@Canvas
    val values=points.map{it.second}+listOfNotNull(referenceLow,referenceHigh);val low=values.minOrNull()?:0.0;val high=values.maxOrNull()?:1.0;val span=(high-low).coerceAtLeast(1.0)
    fun y(v:Double)=(size.height*.88-(v-low)/span*size.height*.76).toFloat()
    val dash=androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f,10f),0f)
    when{
     referenceLow!=null&&referenceHigh!=null->{
      val top=y(referenceHigh);val bottom=y(referenceLow)
      drawRect(Good.copy(alpha=.08f),Offset(0f,top),Size(size.width,(bottom-top).coerceAtLeast(1f)))
      drawLine(Good.copy(alpha=.55f),Offset(0f,top),Offset(size.width,top),1.5.dp.toPx(),pathEffect=dash)
      drawLine(Good.copy(alpha=.55f),Offset(0f,bottom),Offset(size.width,bottom),1.5.dp.toPx(),pathEffect=dash)
     }
     referenceHigh!=null->{
      val top=y(referenceHigh)
      drawRect(Good.copy(alpha=.08f),Offset(0f,top),Size(size.width,(size.height-top).coerceAtLeast(1f)))
      drawLine(Good.copy(alpha=.55f),Offset(0f,top),Offset(size.width,top),1.5.dp.toPx(),pathEffect=dash)
     }
     referenceLow!=null->{
      val bottom=y(referenceLow)
      drawRect(Good.copy(alpha=.08f),Offset.Zero,Size(size.width,bottom.coerceAtLeast(1f)))
      drawLine(Good.copy(alpha=.55f),Offset(0f,bottom),Offset(size.width,bottom),1.5.dp.toPx(),pathEffect=dash)
     }
    }
    val path=Path()
    if(points.isNotEmpty()){
     val plotted=points.indices.map{i->pointPosition(i,size.width,size.height)}
     path.moveTo(plotted[0].x,plotted[0].y)
     for(i in 0 until plotted.lastIndex){
      val p0=plotted.getOrElse(i-1){plotted[i]}
      val p1=plotted[i]
      val p2=plotted[i+1]
      val p3=plotted.getOrElse(i+2){p2}
      val c1x=p1.x+(p2.x-p0.x)/6f;val c1y=p1.y+(p2.y-p0.y)/6f
      val c2x=p2.x-(p3.x-p1.x)/6f;val c2y=p2.y-(p3.y-p1.y)/6f
      path.cubicTo(c1x,c1y,c2x,c2y,p2.x,p2.y)
     }
    }
    drawPath(path,color,style=Stroke(2.dp.toPx()))
   }
   points.indices.forEach{i->
    val at=pointPosition(i,chartWidth.toFloat(),plotHeightPx.toFloat())
    Box(
     Modifier.offset{androidx.compose.ui.unit.IntOffset(at.x.toInt()-12.dp.roundToPx(),at.y.toInt()-12.dp.roundToPx())}
      .size(24.dp)
      .then(if(onPointClick==null)Modifier else Modifier.clickable{onPointClick(i)})
      .semantics{contentDescription=pointDescriptions.getOrNull(i) ?: "趋势点 $metricKey ${i+1}"},
     contentAlignment=Alignment.Center
    ){Canvas(Modifier.size(8.dp)){drawCircle(color)}}
    Column(
     Modifier.offset{androidx.compose.ui.unit.IntOffset(at.x.toInt()-40.dp.roundToPx(),plotHeightPx+4.dp.roundToPx())}.width(80.dp),
     horizontalAlignment=Alignment.CenterHorizontally
    ){
     Text(trendShortDate(points[i].first),fontSize=10.sp,lineHeight=11.sp,fontWeight=FontWeight.Normal,color=Muted,maxLines=1,textAlign=TextAlign.Center)
     Text(valueLabels.getOrNull(i).orEmpty().ifBlank{formatTrendValue(points[i].second)},fontSize=13.sp,lineHeight=14.sp,fontWeight=FontWeight.SemiBold,color=color,maxLines=1,textAlign=TextAlign.Center)
    }
   }
  }
 }
}
@Composable fun TrendPreviewDialog(points:List<Pair<Long,Double>>,color:Color,referenceLow:Double?,referenceHigh:Double?,metricKey:String,onDismiss:()->Unit){
 Dialog(onDismissRequest=onDismiss){
  Card(shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){
   Column(Modifier.padding(20.dp).fillMaxWidth(0.8f),verticalArrangement=Arrangement.spacedBy(12.dp)){
    Text("整体趋势 · $metricKey",fontWeight=FontWeight.Bold)
    if(points.isEmpty()){Text("暂无数据",color=Muted)}else{
     Canvas(Modifier.fillMaxWidth().height(160.dp)){
      val values=points.map{it.second}+listOfNotNull(referenceLow,referenceHigh);val low=values.minOrNull()?:0.0;val high=values.maxOrNull()?:1.0;val span=(high-low).coerceAtLeast(1.0)
      fun y(v:Double)=(size.height*.88-(v-low)/span*size.height*.76).toFloat()
      if(referenceLow!=null&&referenceHigh!=null){
       val top=y(referenceHigh);val bottom=y(referenceLow)
       drawRect(Good.copy(alpha=.08f),Offset(0f,top),Size(size.width,(bottom-top).coerceAtLeast(1f)))
       val dash=androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f,10f),0f)
       drawLine(Good.copy(alpha=.55f),Offset(0f,top),Offset(size.width,top),1.5.dp.toPx(),pathEffect=dash)
       drawLine(Good.copy(alpha=.55f),Offset(0f,bottom),Offset(size.width,bottom),1.5.dp.toPx(),pathEffect=dash)
      }
      val path=Path()
      val plotted=points.indices.map{i->
       val x=if(points.size==1)size.width/2 else 8f+(size.width-16f)*i/(points.size-1)
       Offset(x,y(points[i].second))
      }
      if(plotted.isNotEmpty()){
       path.moveTo(plotted[0].x,plotted[0].y)
       for(i in 0 until plotted.lastIndex){
        val p0=plotted.getOrElse(i-1){plotted[i]}
        val p1=plotted[i]
        val p2=plotted[i+1]
        val p3=plotted.getOrElse(i+2){p2}
        val c1x=p1.x+(p2.x-p0.x)/6f;val c1y=p1.y+(p2.y-p0.y)/6f
        val c2x=p2.x-(p3.x-p1.x)/6f;val c2y=p2.y-(p3.y-p1.y)/6f
        path.cubicTo(c1x,c1y,c2x,c2y,p2.x,p2.y)
       }
      }
      drawPath(path,color,style=Stroke(2.dp.toPx()))
     }
     Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
      Text(trendShortDate(points.first().first),fontSize=10.sp,color=Muted)
      Text(trendShortDate(points.last().first),fontSize=10.sp,color=Muted)
     }
    }
    TextButton({onDismiss()},Modifier.align(Alignment.End)){Text("关闭")}
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
@Composable fun Records(m:Modifier,reports:List<LabReport>,entries:List<HealthEntry>,filter:String,setFilter:(String)->Unit,open:(LabReport)->Unit,edit:(HealthEntry)->Unit,add:(EntryKind)->Unit){Screen(m,"病程时间轴","按记录发生时间排列"){
 Row(Modifier.horizontalScroll(rememberScrollState())){(listOf("全部","检查报告")+EntryKind.entries.map{it.title}+"症状报告").forEach{t->FilterChip(filter==t,{setFilter(t)},label={Text(t)},modifier=Modifier.padding(end=8.dp))}}
 if(filter=="症状报告"){SymptomReport(entries.filter{it.kind==EntryKind.SYMPTOM})}else{
  Row(Modifier.horizontalScroll(rememberScrollState())){EntryKind.entries.forEach{k->TextButton({add(k)}){Text("+ ${k.title}")}}}
  val events=(reports.filter{filter=="全部"||filter=="检查报告"}.map{Triple(it.testedAtEpochMillis,it,null as HealthEntry?)}+entries.filter{filter=="全部"||it.kind.title==filter}.map{Triple(it.occurredAtEpochMillis,null as LabReport?,it)}).sortedByDescending{it.first}
  if(events.isEmpty())Paper{Text("暂无记录")}
  events.forEach{(_,r,e)->if(r!=null)ReportCard(r){open(r)}else if(e!=null)Paper(Modifier.clickable{edit(e)}){Text(e.kind.title+" · "+e.title,fontWeight=FontWeight.Bold);Text(dateText(e.occurredAtEpochMillis),color=Muted,fontSize=12.sp);if(e.kind==EntryKind.SYMPTOM)Text("程度 ${e.severity}/10 · ${e.frequency} · ${e.duration}");if(e.kind==EntryKind.MEDICATION)Text(listOf(e.dose,e.route,e.frequency).filter{it.isNotBlank()}.joinToString(" · "));if(e.hospital.isNotBlank())Text(e.hospital);if(e.note.isNotBlank())Text(e.note);if(e.images.isNotEmpty())Text("${e.images.size} 张原图",color=Accent)}}
 }
}}
@Composable fun SymptomReport(entries:List<HealthEntry>){
 var start by rememberSaveable{mutableStateOf(LocalDate.now().minusDays(30).toString())};var end by rememberSaveable{mutableStateOf(LocalDate.now().toString())}
 OutlinedTextField(start,{start=it},label={Text("开始日期 YYYY-MM-DD")},modifier=Modifier.fillMaxWidth());OutlinedTextField(end,{end=it},label={Text("结束日期 YYYY-MM-DD")},modifier=Modifier.fillMaxWidth())
 val from=parseDate(start);val until=parseDate(end)?.let{Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).plusDays(1).toInstant().toEpochMilli()}
 if(from==null||until==null||from>=until){Text("请填写有效日期范围",color=Bad);return}
 val selected=entries.filter{it.occurredAtEpochMillis>=from&&it.occurredAtEpochMillis<until}
 val text=symptomReportText(start,end,selected)
 val ctx=LocalContext.current
 Paper{Text("期间共 ${selected.size} 次记录",fontWeight=FontWeight.Bold);selected.groupBy{it.title}.forEach{(name,rows)->Text("$name · ${rows.size} 次 · 最高 ${rows.maxOf{it.severity}}/10");Spark(rows.sortedBy{it.occurredAtEpochMillis}.map{it.occurredAtEpochMillis to it.severity.toDouble()},Accent)}}
 Button({shareText(ctx,"症状报告",text)},Modifier.fillMaxWidth()){Text("分享症状报告")}
 selected.forEach{e->Paper{Text(e.title+" · "+dateText(e.occurredAtEpochMillis),fontWeight=FontWeight.Bold);Text("程度 ${e.severity}/10  ${e.frequency}  ${e.duration}");Text(e.note)}}
}
