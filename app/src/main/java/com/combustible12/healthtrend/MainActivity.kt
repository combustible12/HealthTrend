package com.combustible12.healthtrend

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.*
import java.time.format.DateTimeFormatter

val Warm=Color(0xFFFAF9F6);val Ink=Color(0xFF292927);val Muted=Color(0xFF817E78)
val Accent=Color(0xFFF28B58);val Good=Color(0xFF56A978);val Bad=Color(0xFFD9665B)
private val stamp=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
fun dateText(n:Long)=Instant.ofEpochMilli(n).atZone(ZoneId.systemDefault()).format(stamp)
fun parseDate(s:String):Long?=runCatching{if(s.trim().length==10)LocalDate.parse(s.trim()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()else LocalDateTime.parse(s.trim(),stamp).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()}.getOrNull()
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
 val reports=remember(revision){runCatching{store.reports()}.getOrElse{error="历史数据读取失败：${it.message}";emptyList()}}
 val entries=remember(revision){runCatching{store.entries()}.getOrElse{error="历史记录读取失败：${it.message}";emptyList()}}
 val templates=remember(revision){runCatching{store.templates()}.getOrElse{error="模板读取失败：${it.message}";emptyList()}}
 fun change(block:()->Unit){try{block();revision++}catch(e:Exception){error=e.message?:"操作失败"}}
 val importer=rememberReportImport(store,{draft=it},{error=it},deliver=draft==null&&report==null&&entry==null&&template==null&&viewer==null)
 Box(Modifier.fillMaxSize()){
 Box(Modifier.fillMaxSize().then(if(draft!=null||report!=null||entry!=null||template!=null||viewer!=null)Modifier.clearAndSetSemantics{} else Modifier)){
 Scaffold(containerColor=Warm,bottomBar={NavigationBar(containerColor=Color.White){listOf("首页" to Icons.Outlined.Home,"趋势" to Icons.Outlined.ShowChart,"记录" to Icons.Outlined.FolderOpen,"我的" to Icons.Outlined.Person).forEachIndexed{i,p->NavigationBarItem(selected=tab==i,onClick={tab=i},icon={Icon(p.second,p.first)},label={Text(p.first)})}}}){padding->
  val m=Modifier.padding(padding)
  when(tab){
   0->Home(m,reports,entries,importer,{kind->if(kind==null)tab=1 else entry=HealthEntry(kind=kind,title="",occurredAtEpochMillis=System.currentTimeMillis())},{report=it},{tab=2;recordsFilter="全部"})
   1->Trends(m,store,reports,revision,{key,value->change{store.setPrimary(key,value)}},{report=it},{r,x,v->change{store.updateValue(r.id,x.id,v)}})
   2->Records(m,reports,entries,recordsFilter,{recordsFilter=it},{report=it},{entry=it},{kind->entry=HealthEntry(kind=kind,title="",occurredAtEpochMillis=System.currentTimeMillis())})
   3->Mine(m,templates,{template=it},store,{error=it})
  }
 }
 }
 CompositionLocalProvider(LocalPageVisible provides (viewer==null)){
 if(draft!=null)ReportEditor(draft!!,store,{draft=null},{d->change{
   val existing=store.latestTemplate(d.hospital,d.type,d.system)
   val t=if(d.newTemplate||existing==null)store.confirmTemplate(d.hospital,d.type,d.parsed(),d.system,d.newTemplate)else existing
   val r=store.buildReport(d.hospital,d.type,preserveTimestamp(d.date,d.existing?.testedAtEpochMillis)!!,d.images,d.parsed(),t,d.ocr,d.system)
   val unchangedTemplate=d.existing!=null&&!d.newTemplate&&d.hospital==d.existing.hospitalKey&&d.type==d.existing.reportType&&d.system==d.existing.systemKey
   val version=if(unchangedTemplate)d.existing?.templateVersion else t.version
   store.saveReport(if(d.existing==null)r else r.copy(id=d.existing.id,templateVersion=version,results=r.results.mapIndexed{i,x->x.copy(id=d.rows[i].id,reportId=d.existing.id,templateVersion=version,editedByUser=true)}))
   draft=null
  }},{viewer=it})
 if(report!=null){val current=reports.firstOrNull{it.id==report!!.id}?:report!!;ReportDetail(current,{report=null},{draft=ReportDraft.from(current);report=null},{viewer=it},{change{store.deleteReport(current.id);report=null}},{result,value->change{store.updateValue(current.id,result.id,value)}})}
 if(entry!=null)EntryEditor(entry!!,store,{entry=null},{e->change{store.saveEntry(e);entry=null}},{change{store.deleteEntry(entry!!.id);entry=null}},{viewer=it})
 if(template!=null)TemplateEditor(template!!,{template=null},{items->change{store.confirmTemplate(template!!.hospitalKey,template!!.reportType,items,template!!.systemKey,true);template=null}})
 }
 if(viewer!=null)SourceViewer(viewer!!,{viewer=null})
 if(error!=null)AlertDialog(onDismissRequest={error=null},title={Text("操作未完成")},text={Text(error!!)},confirmButton={TextButton({error=null}){Text("知道了")}})
 }
}
@Composable fun Screen(m:Modifier,title:String,subtitle:String="",content:@Composable ColumnScope.()->Unit){Column(m.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){Spacer(Modifier.height(6.dp));Text(title,fontSize=28.sp,fontWeight=FontWeight.Bold);if(subtitle.isNotBlank())Text(subtitle,color=Muted);content();Spacer(Modifier.height(12.dp))}}
@Composable fun Paper(m:Modifier=Modifier,content:@Composable ColumnScope.()->Unit){Card(modifier=m.fillMaxWidth(),shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content)}}
@Composable fun Home(m:Modifier,reports:List<LabReport>,entries:List<HealthEntry>,importer:ImportActions,quick:(EntryKind?)->Unit,open:(LabReport)->Unit,timeline:()->Unit){Screen(m,"HealthTrend","把检查、症状和病历放在一条清楚的时间线上"){
 Card(shape=RoundedCornerShape(28.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFFFFEEE5))){Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text("今天要记录什么？",fontSize=21.sp,fontWeight=FontWeight.Bold);Button(importer.camera,Modifier.fillMaxWidth().height(54.dp),shape=RoundedCornerShape(18.dp),enabled=!importer.busy){Icon(Icons.Outlined.DocumentScanner,null);Spacer(Modifier.width(8.dp));Text("拍照识别检查报告")};Row{TextButton(importer.gallery,enabled=!importer.busy){Text("相册导入")};TextButton(importer.manual,enabled=!importer.busy){Text("手动录入")}};if(importer.busy)LinearProgressIndicator(Modifier.fillMaxWidth());if(importer.message.isNotBlank())Text(importer.message,fontSize=12.sp)}}
 Text("健康记录",fontSize=20.sp,fontWeight=FontWeight.Bold)
 Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){Quick("检查指标","趋势与异常",Icons.Outlined.MonitorHeart,Modifier.weight(1f)){quick(null)};Quick("症状记录","程度与频率",Icons.Outlined.EditNote,Modifier.weight(1f)){quick(EntryKind.SYMPTOM)}}
 Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){Quick("病历资料","报告与影像",Icons.Outlined.Description,Modifier.weight(1f)){quick(EntryKind.MEDICAL)};Quick("用药记录","时间与备注",Icons.Outlined.Medication,Modifier.weight(1f)){quick(EntryKind.MEDICATION)}}
 Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text("最近",fontSize=20.sp,fontWeight=FontWeight.Bold);TextButton(timeline){Text("病程时间轴")}}
 if(reports.isEmpty()&&entries.isEmpty())Paper{Text("还没有记录");Text("从第一份检查报告或症状开始。",color=Muted)}
 reports.take(2).forEach{r->ReportCard(r){open(r)}}
 entries.take(2).forEach{e->Paper{Text(e.kind.title+" · "+e.title,fontWeight=FontWeight.Bold);Text(dateText(e.occurredAtEpochMillis),color=Muted);Text(e.note.ifBlank{listOf(e.dose,e.frequency).filter{it.isNotBlank()}.joinToString(" · ")})}}
}}
@Composable fun Quick(t:String,s:String,icon:androidx.compose.ui.graphics.vector.ImageVector,m:Modifier,onClick:()->Unit){Card(onClick=onClick,modifier=m,shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){Column(Modifier.padding(18.dp)){Icon(icon,null,tint=Accent);Spacer(Modifier.height(20.dp));Text(t,fontWeight=FontWeight.Bold);Text(s,color=Muted,fontSize=12.sp)}}}
@Composable fun ReportCard(r:LabReport,open:()->Unit){Paper(Modifier.clickable(onClick=open)){Text(r.reportType,fontWeight=FontWeight.Bold);Text("${r.hospitalKey} · ${dateText(r.testedAtEpochMillis)}",color=Muted,fontSize=12.sp);Text("${r.results.size} 个项目 · ${r.results.count{it.status()==ResultStatus.HIGH||it.status()==ResultStatus.LOW}} 个超出参考范围");Text("查看指标和原报告 →",color=Accent)}}
@Composable fun Trends(m:Modifier,store:HealthStore,reports:List<LabReport>,revision:Int,priority:(String,Boolean)->Unit,open:(LabReport)->Unit,edit:(LabReport,LabResult,Double)->Unit){
 var category by rememberSaveable{mutableStateOf("血常规")};var mode by rememberSaveable{mutableStateOf("重点指标")};var query by rememberSaveable{mutableStateOf("")};var range by rememberSaveable{mutableStateOf("全部")}
 var selected by remember{mutableStateOf<Pair<LabReport,LabResult>?>(null)}
 var editing by remember{mutableStateOf(false)};var editValue by remember{mutableStateOf("")}
 val all=reports.flatMap{r->r.results.map{r to it}}.groupBy{it.second.metricKey}
 Screen(m,"指标趋势","点按曲线上的数据点可查看当次详情、编辑数值或打开原报告"){
  Row(Modifier.horizontalScroll(rememberScrollState())){listOf("血常规","肝功能","肾功能","肿瘤标志物").forEach{t->FilterChip(category==t,{category=t},label={Text(t)},modifier=Modifier.padding(end=8.dp))}}
  Row{listOf("重点指标","其他指标").forEach{t->FilterChip(mode==t,{mode=t},label={Text(t)},modifier=Modifier.padding(end=8.dp))}}
  Row(Modifier.horizontalScroll(rememberScrollState())){listOf("近3月","近6月","近1年","全部").forEach{t->FilterChip(range==t,{range=t},label={Text(t)},modifier=Modifier.padding(end=8.dp))}}
  OutlinedTextField(query,{query=it},label={Text("查找指标")},modifier=Modifier.fillMaxWidth(),singleLine=true)
  val filtered=all.filter{(key,list)->store.isPrimary(key)==(mode=="重点指标") && list.any{(r,x)->r.reportType==category&&(x.rawName.contains(query,true)||key.contains(query,true))}}
  if(filtered.isEmpty())Paper{Text("暂无符合条件的指标");Text("导入并核对报告后，这里显示真实历史数据。",color=Muted)}
  filtered.forEach{(key,list)->
   val cutoff=when(range){"近3月"->System.currentTimeMillis()-90L*86400000L;"近6月"->System.currentTimeMillis()-183L*86400000L;"近1年"->System.currentTimeMillis()-365L*86400000L;else->Long.MIN_VALUE}
   val points=list.filter{it.first.reportType==category&&it.first.testedAtEpochMillis>=cutoff}.sortedBy{it.first.testedAtEpochMillis}
   if(points.isEmpty())return@forEach
   val latest=points.last().second
   Paper{
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Column(Modifier.weight(1f)){Text(latest.rawName,fontWeight=FontWeight.Bold);Text(key,color=Muted,fontSize=12.sp)};Text(latest.textValue,fontSize=27.sp,fontWeight=FontWeight.Bold,color=statusColor(latest.status()))}
    Text("${latest.unitAtTest} · 当次参考 ${rangeText(latest.referenceLowAtTest,latest.referenceHighAtTest)} · ${latest.status().label()}",color=Muted,fontSize=12.sp)
    points.filter{it.second.normalizedValue!=null&&it.second.comparator.isEmpty()}.groupBy{val bounds=it.second.trendReferenceRange();listOf(it.second.normalizedUnit,it.first.hospitalKey,it.first.systemKey,it.first.templateVersion,bounds.first,bounds.second)}.forEach{(_,series)->
     val sx=series.last().second
     Text(listOf(sx.normalizedUnit.ifBlank{"单位未录入"},series.last().first.hospitalKey).filter{it.isNotBlank()}.joinToString(" · "),color=Muted,fontSize=12.sp)
     val bounds=sx.trendReferenceRange()
     Spark(series.map{it.first.testedAtEpochMillis to it.second.normalizedValue!!},Accent,bounds.first,bounds.second){index->selected=series[index]}
    }
    points.forEach{(r,x)->TextButton({selected=r to x},Modifier.fillMaxWidth()){Text("${dateText(r.testedAtEpochMillis)}   ${x.textValue} ${x.unitAtTest}   ${x.status().label()}",modifier=Modifier.weight(1f));Icon(Icons.Outlined.ChevronRight,null)}}
    TextButton({priority(key,!store.isPrimary(key))}){Text(if(store.isPrimary(key))"移到其他指标"else"设为重点指标")}
   }
  }
 }
 selected?.let{(r,x)->
  AlertDialog(onDismissRequest={selected=null;editing=false},title={Text(x.rawName)},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
   Text(dateText(r.testedAtEpochMillis),color=Muted);Text(r.hospitalKey.ifBlank{"医院未录入"},fontWeight=FontWeight.Medium)
   Text("${x.textValue} ${x.unitAtTest}",fontSize=24.sp,fontWeight=FontWeight.Bold,color=statusColor(x.status()))
   Text("当次参考：${rangeText(x.referenceLowAtTest,x.referenceHighAtTest)} · ${x.status().label()}",color=Muted)
   if(editing)OutlinedTextField(editValue,{editValue=it},label={Text("结果")},singleLine=true)
  }},confirmButton={
   if(editing)TextButton({val v=editValue.trim().toDoubleOrNull();if(v!=null&&v.isFinite()){edit(r,x,v);selected=r to x.withEditedValue(v);editing=false}}){Text("保存")}
   else TextButton({editValue=x.value?.toString().orEmpty();editing=true}){Text("编辑数值")}
  },dismissButton={Row{
   if(r.sourceImages.isNotEmpty())TextButton({selected=null;open(r)}){Text("查看原报告")}
   TextButton({selected=null;editing=false}){Text("关闭")}
  }})
 }
}
@Composable fun Spark(points:List<Pair<Long,Double>>,color:Color,referenceLow:Double?=null,referenceHigh:Double?=null,onPointClick:((Int)->Unit)?=null){
 fun pointPosition(index:Int,width:Float,height:Float):Offset{
  val values=points.map{it.second}+listOfNotNull(referenceLow,referenceHigh);val low=values.minOrNull()?:0.0;val high=values.maxOrNull()?:1.0;val span=(high-low).coerceAtLeast(1.0)
  val start=points.minOfOrNull{it.first}?:0L;val time=((points.maxOfOrNull{it.first}?:start)-start).coerceAtLeast(1L);val p=points[index]
  val x=if(points.size==1)width/2 else (8f+(width-16f)*(p.first-start).toDouble()/time).toFloat()
  val y=(height*.88-(p.second-low)/span*height*.76).toFloat();return Offset(x,y)
 }
 val interaction=if(onPointClick==null)Modifier else Modifier.pointerInput(points){detectTapGestures{tap->
  if(points.isNotEmpty()){val hit=points.indices.minByOrNull{i->val p=pointPosition(i,size.width.toFloat(),size.height.toFloat());(p.x-tap.x)*(p.x-tap.x)+(p.y-tap.y)*(p.y-tap.y)}
   if(hit!=null){val p=pointPosition(hit,size.width.toFloat(),size.height.toFloat());val dx=p.x-tap.x;val dy=p.y-tap.y;if(dx*dx+dy*dy<=48.dp.toPx()*48.dp.toPx())onPointClick(hit)}
  }
 }}
 Canvas(Modifier.fillMaxWidth().height(112.dp).then(interaction)){
  if(points.isEmpty())return@Canvas
  val values=points.map{it.second}+listOfNotNull(referenceLow,referenceHigh);val low=values.minOrNull()?:0.0;val high=values.maxOrNull()?:1.0;val span=(high-low).coerceAtLeast(1.0)
  fun y(v:Double)=(size.height*.88-(v-low)/span*size.height*.76).toFloat()
  if(referenceLow!=null&&referenceHigh!=null){val top=y(referenceHigh);val bottom=y(referenceLow);drawRect(color.copy(alpha=.10f),Offset(0f,top),Size(size.width,(bottom-top).coerceAtLeast(1f)))}
  val path=Path();points.indices.forEach{i->val at=pointPosition(i,size.width,size.height);if(i==0)path.moveTo(at.x,at.y)else path.lineTo(at.x,at.y)}
  drawPath(path,color,style=Stroke(2.dp.toPx()));points.indices.forEach{i->drawCircle(color,4.dp.toPx(),pointPosition(i,size.width,size.height))}
 }
}

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
