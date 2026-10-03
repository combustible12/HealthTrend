package com.combustible12.healthtrend

import android.content.Intent
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Stable ids preserve data-point identity when a whole report is edited. */
data class DraftRow(val id:String=newId(),val name:String="",val key:String="",val text:String="",val unit:String="",val low:String="",val high:String="",val raw:String="",val uncertain:Boolean=false):java.io.Serializable {
 fun parsed():ParsedLabResult{val cleanUnit=unit.trim().takeUnless{it.toDoubleOrNull()!=null}.orEmpty();val metric=key.ifBlank{ReportParser.key(name)};return ParsedLabResult(metric,name,text.trim().trimStart('<','>','≤','≥').toDoubleOrNull(),cleanUnit,low.toDoubleOrNull(),high.toDoubleOrNull(),raw,metric in ReportParser.primaryKeys,text.trim(),text.trim().takeWhile{it in "<>≤≥"})}
 fun valid()=name.isNotBlank()&&text.isNotBlank()&&(low.isBlank()||low.toDoubleOrNull()?.isFinite()==true)&&(high.isBlank()||high.toDoubleOrNull()?.isFinite()==true)&&ReportParser.valid(listOf(parsed()))
 companion object{fun from(p:ParsedLabResult)=DraftRow(name=p.displayName,key=p.metricKey,text=p.textValue,unit=p.unit,low=p.referenceLow?.toString().orEmpty(),high=p.referenceHigh?.toString().orEmpty(),raw=p.rawLine,uncertain=p.metricKey.isBlank()||p.textValue.isBlank()||(p.referenceLow==null)!=(p.referenceHigh==null))}
}
data class ReportDraft(val hospital:String="",val type:String="血常规",val system:String="",val date:String=dateText(System.currentTimeMillis()),val images:List<String> = emptyList(),val ocr:String="",val rows:List<DraftRow> = emptyList(),val uncertain:Set<String> = emptySet(),val newTemplate:Boolean=false,val existing:LabReport?=null):java.io.Serializable{
 fun parsed()=rows.map{it.parsed()}
 fun valid()=hospital.isNotBlank()&&parseDate(date)!=null&&rows.isNotEmpty()&&rows.all{it.valid()}
 companion object{fun from(r:LabReport)=ReportDraft(hospital=r.hospitalKey,type=r.reportType,system=r.systemKey,date=dateText(r.testedAtEpochMillis),images=r.sourceImages.map{it.uri},ocr=r.rawOcr,rows=r.results.map{x->DraftRow(x.id,x.rawName,x.metricKey,x.textValue,x.unitAtTest,x.referenceLowAtTest?.toString().orEmpty(),x.referenceHighAtTest?.toString().orEmpty(),x.rawLine)},existing=r)}
}
val LocalPageVisible=staticCompositionLocalOf{true}
@Composable fun FullPage(title:String,onClose:()->Unit,hidden:Boolean=false,bottom:@Composable ()->Unit={},content:@Composable (Modifier)->Unit){
 val active=!hidden&&LocalPageVisible.current
 val focus=LocalFocusManager.current
 BackHandler(enabled=active,onBack=onClose)
 LaunchedEffect(active){if(active)focus.clearFocus()}
 Surface(color=Warm,modifier=Modifier.fillMaxSize().then(if(active)Modifier else Modifier.clearAndSetSemantics{})){Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()){
  Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){IconButton(onClose){Icon(Icons.Outlined.Close,"关闭")};Text(title,fontSize=21.sp,modifier=Modifier.weight(1f))}
  Box(Modifier.weight(1f)){content(Modifier.fillMaxSize())}
  Surface(color=ColorWhite){Box(Modifier.fillMaxWidth().padding(12.dp)){bottom()}}
 }}
}
private val ColorWhite=androidx.compose.ui.graphics.Color.White
@Composable fun Field(value:String,onChange:(String)->Unit,label:String,m:Modifier=Modifier){OutlinedTextField(value,onChange,label={Text(label)},modifier=m.fillMaxWidth(),singleLine=true)}
@Composable fun RememberedField(value:String,onChange:(String)->Unit,label:String,options:List<String>,m:Modifier=Modifier){
 val saved=options.map{it.trim()}.filter{it.isNotBlank()}.distinct()
 Column(m.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(6.dp)){
  if(saved.isNotEmpty())Row(Modifier.horizontalScroll(rememberScrollState())){saved.forEach{option->FilterChip(selected=value.trim()==option,onClick={onChange(option)},label={Text(option)},modifier=Modifier.padding(end=8.dp))}}
  Field(value,onChange,label)
 }
}
@Composable fun ReportEditor(initial:ReportDraft,store:HealthStore,onClose:()->Unit,save:(ReportDraft)->Unit,images:(List<String>)->Unit){
 val context=LocalContext.current
 var d by rememberSaveable(initial,stateSaver=diskStateSaver<ReportDraft>(context,"report-editor")){mutableStateOf(initial)}
 var editing by rememberSaveable{mutableStateOf<String?>(null)}
 FullPage(if(d.existing==null)"核对检查报告"else"编辑检查报告",onClose,hidden=editing!=null,bottom={Button({save(d)},Modifier.fillMaxWidth(),enabled=d.valid()){Text("保存")}}){m->
 Column(m.verticalScroll(rememberScrollState()).padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  run{RememberedField(d.hospital,{value->d=updateOcrMetadata(retargetImportedDraft(d,store,hospital=value),"hospital",value)},"医院",remember(store){store.rememberedHospitals()})}
  run{Field(d.date,{value->d=updateOcrMetadata(d,"date",value)},"检查日期/时间")}
  if(d.images.isNotEmpty())run{TextButton({images(d.images)}){Text("查看原报告 · ${d.images.size} 页")}}
  d.rows.forEachIndexed{i,r->LabRowSummary(r,{editing=r.id},{d=d.copy(rows=d.rows.filterIndexed{j,_->j!=i})})}
  run{OutlinedButton({val added=DraftRow();d=d.copy(rows=d.rows+added);editing=added.id},Modifier.fillMaxWidth()){Text("+ 添加遗漏指标")};Spacer(Modifier.height(12.dp))}
 }
 }
 d.rows.firstOrNull{it.id==editing}?.let{row->
  val units=remember(row.key,d.rows){(store.rememberedUnits(row.key)+d.rows.filter{it.key==row.key}.map{it.unit}).map{it.trim()}.filter{it.isNotBlank()}.distinct()}
  val fixed=store.latestTemplate(d.hospital,d.type,d.system)?.fields?.any{ReportParser.key(it.metricKey)==ReportParser.key(row.key)}==true
  MetricEditor(row,{changed->d=d.copy(rows=d.rows.map{if(it.id==changed.id)changed else it})},{editing=null},unitOptions=units,lockMetadata=fixed)
 }
}
@Composable fun LabRowSummary(r:DraftRow,edit:()->Unit,remove:()->Unit,templateOnly:Boolean=false){Paper{
 Row{Text(r.name.ifBlank{"待核对指标"},modifier=Modifier.weight(1f));IconButton(remove){Icon(Icons.Outlined.Delete,"删除指标")}}
 Text(if(templateOnly)r.unit else "${r.text} ${r.unit}",fontSize=22.sp);Text("参考 ${rangeText(r.low.toDoubleOrNull(),r.high.toDoubleOrNull())}",color=Muted)
 TextButton(edit){Text("编辑")}
}}
@Composable fun MetricEditor(row:DraftRow,edit:(DraftRow)->Unit,close:()->Unit,templateOnly:Boolean=false,unitOptions:List<String> = emptyList(),lockMetadata:Boolean=false){
 var unlock by rememberSaveable(row.id){mutableStateOf(false)}
 FullPage("核对指标",close,bottom={Button({if(row.valid()){edit(row.copy(uncertain=false));close()}},Modifier.fillMaxWidth(),enabled=row.valid()){Text("完成核对")}}){m->Column(m.verticalScroll(rememberScrollState()).padding(16.dp)){
  LabRowEditor(row,edit,templateOnly,unitOptions,lockMetadata&&!unlock)
  if(lockMetadata&&!unlock)TextButton({unlock=true}){Text("本次报告项目或范围有变化")}
 }}
}
fun updateOcrMetadata(d:ReportDraft,field:String,value:String):ReportDraft=when(field){
 "hospital"->d.copy(hospital=value,uncertain=if(value.isBlank())d.uncertain+"hospital" else d.uncertain-"hospital")
 "type"->d.copy(type=value,uncertain=if(value.isBlank())d.uncertain+"type" else d.uncertain-"type")
 "date"->{val normalized=normalizeDateText(value);d.copy(date=normalized?:value,uncertain=if(normalized==null)d.uncertain+"date" else d.uncertain-"date")}
 else->d
}
/** A confirmed template is immutable; a changed or newly confirmed field needs a new version. */
fun templateNeedsNewVersion(template:HospitalLabTemplate?,rows:List<ParsedLabResult>):Boolean{
 if(template==null)return false
 return rows.any{row->
  val old=template.fields.firstOrNull{it.metricKey==row.metricKey}
  old==null || old.unit.trim()!=row.unit.trim() ||
   old.referenceLow!=row.referenceLow || old.referenceHigh!=row.referenceHigh
 }
}

fun reportValidationProblems(d:ReportDraft):List<String> = if(d.valid()) emptyList() else listOf("invalid")
@Composable fun LabRowEditor(r:DraftRow,edit:(DraftRow)->Unit,templateOnly:Boolean=false,unitOptions:List<String> = emptyList(),lockMetadata:Boolean=false){Paper{
 Text("指标")
 if(lockMetadata)Text(r.name,fontSize=18.sp) else Field(r.name,{edit(r.copy(name=it,key=ReportParser.key(it)))},"项目名称")
 if(!templateOnly)Field(r.text,{edit(r.copy(text=it))},"结果（支持 <、>、阴性等）")
 if(lockMetadata){Text("${r.unit.ifBlank{"单位未录入"}} · 参考 ${rangeText(r.low.toDoubleOrNull(),r.high.toDoubleOrNull())}",color=Muted)}else{
  if(unitOptions.isNotEmpty())Row(Modifier.horizontalScroll(rememberScrollState())){unitOptions.forEach{unit->FilterChip(selected=r.unit==unit,onClick={edit(r.copy(unit=unit))},label={Text(unit)},modifier=Modifier.padding(end=8.dp))}}
  Field(r.unit,{edit(r.copy(unit=it))},"单位")
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Field(r.low,{edit(r.copy(low=it))},"参考下限",Modifier.weight(1f));Field(r.high,{edit(r.copy(high=it))},"参考上限",Modifier.weight(1f))}
 }
}}
@Composable fun ReportDetail(r:LabReport,close:()->Unit,edit:()->Unit,images:(List<String>)->Unit,delete:()->Unit,update:(LabResult,Double)->Unit){
 var point by remember{mutableStateOf<LabResult?>(null)};var value by remember{mutableStateOf("")};var confirmDelete by remember{mutableStateOf(false)}
 FullPage(r.reportType,close,bottom={Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){TextButton(edit){Text("编辑报告")};TextButton({confirmDelete=true}){Text("删除报告",color=Bad)}}}){m->LazyColumn(m.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
 item{Text(r.hospitalKey);Text(dateText(r.testedAtEpochMillis),color=Muted)}
 if(r.sourceImages.isNotEmpty())item{Button({images(r.sourceImages.map{it.uri})},Modifier.fillMaxWidth()){Text("查看原报告 · ${r.sourceImages.size} 页")}}
 itemsIndexed(r.results,key={_,x->x.id}){_,x->Paper{Row{Text(x.rawName,modifier=Modifier.weight(1f));Text(x.status().label(),color=statusColor(x.status()))};Text("${x.textValue} ${x.unitAtTest}",fontSize=24.sp);Text("当次参考：${rangeText(x.referenceLowAtTest,x.referenceHighAtTest)}",color=Muted);if(x.editedByUser)Text("已手动修正",color=Accent,fontSize=12.sp);if(x.value!=null)TextButton({point=x;value=x.textValue.ifBlank{x.value.toString()}}){Text("编辑数据点")}}}
 }}
 if(point!=null)AlertDialog(onDismissRequest={point=null},title={Text("编辑 ${point!!.rawName}")},text={Column{Field(value,{value=it},"结果")}},confirmButton={TextButton({update(point!!,value.toDouble());point=null},enabled=value.toDoubleOrNull()?.isFinite()==true){Text("保存")}},dismissButton={TextButton({point=null}){Text("取消")}})
 if(confirmDelete)DeleteConfirmation({confirmDelete=false},delete)
}
@Composable fun DeleteConfirmation(close:()->Unit,remove:()->Unit){AlertDialog(onDismissRequest=close,title={Text("删除这条记录？")},text={Text("删除后无法在应用内恢复。")},confirmButton={TextButton({remove();close()}){Text("删除",color=Bad)}},dismissButton={TextButton(close){Text("取消")}})}
@Composable fun TemplateEditor(t:HospitalLabTemplate,close:()->Unit,save:(List<ParsedLabResult>)->Unit){
 val context=LocalContext.current
 var editing by rememberSaveable{mutableStateOf<String?>(null)}
 var rows by rememberSaveable(t,stateSaver=diskStateSaver<List<DraftRow>>(context,"template-editor")){mutableStateOf(t.fields.map{DraftRow(name=it.displayName,key=it.metricKey,text="0",unit=it.unit,low=it.referenceLow?.toString().orEmpty(),high=it.referenceHigh?.toString().orEmpty())})}
 FullPage("医院模板 v${t.version}",close,hidden=editing!=null,bottom={Button({save(rows.map{it.parsed()})},Modifier.fillMaxWidth(),enabled=rows.isNotEmpty()&&rows.all{it.valid()}){Text("主动确认新版模板")}}){m->LazyColumn(m.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
 item{Text("${t.hospitalKey}\n${t.reportType}")}
 itemsIndexed(rows,key={_,r->r.id}){i,r->LabRowSummary(r,{editing=r.id},{rows=rows.filterIndexed{j,_->i!=j}},true)}
 item{TextButton({val added=DraftRow(text="0");rows=rows+added;editing=added.id}){Text("+ 添加指标")}}
 }}
 rows.firstOrNull{it.id==editing}?.let{row->MetricEditor(row,{changed->rows=rows.map{if(it.id==changed.id)changed else it}},{editing=null},true,rows.filter{it.key==row.key}.map{it.unit}.filter{it.isNotBlank()}.distinct())}
}
@Composable fun EntryEditor(initial:HealthEntry,store:HealthStore,close:()->Unit,save:(HealthEntry)->Unit,delete:()->Unit,images:(List<String>)->Unit){
 val context=LocalContext.current
 var e by rememberSaveable(initial,stateSaver=diskStateSaver<HealthEntry>(context,"entry-editor")){mutableStateOf(initial)};var date by rememberSaveable{mutableStateOf(dateText(initial.occurredAtEpochMillis))};var end by rememberSaveable{mutableStateOf(initial.endAtEpochMillis?.let{dateText(it)}.orEmpty())};var error by remember{mutableStateOf("")};var deleting by remember{mutableStateOf(false)};var busy by remember{mutableStateOf(false)}
 val scope=rememberCoroutineScope()
 val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){uris->if(uris.isNotEmpty()){busy=true;scope.launch{try{val owned=withContext(Dispatchers.IO){uris.map{store.ownImage(it)}};e=e.copy(images=e.images+owned)}catch(x:Exception){error="原图保存失败：${x.message}"}finally{busy=false}}}}
 val valid=e.title.isNotBlank()&&parseDate(date)!=null&&(end.isBlank()||parseDate(end)?.let{it>=parseDate(date)!!}==true)&&!busy
 FullPage(e.kind.title,close,bottom={Row{Button({save(e.copy(occurredAtEpochMillis=preserveTimestamp(date,initial.occurredAtEpochMillis)!!,endAtEpochMillis=preserveTimestamp(end,initial.endAtEpochMillis)))},Modifier.weight(1f),enabled=valid){Text("保存记录")};if(store.entries().any{it.id==e.id})TextButton({deleting=true}){Text("删除",color=Bad)}}}){m->Column(m.verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
 Field(e.title,{e=e.copy(title=it)},when(e.kind){EntryKind.SYMPTOM->"症状名称";EntryKind.MEDICAL->"病历标题";EntryKind.MEDICATION->"药品名称"});Field(date,{date=it},if(e.kind==EntryKind.MEDICATION)"开始时间 YYYY-MM-DD HH:mm"else"发生时间 YYYY-MM-DD HH:mm")
 when(e.kind){
  EntryKind.SYMPTOM->{Text("程度 ${e.severity}/10");Slider(e.severity.toFloat(),{e=e.copy(severity=it.toInt())},valueRange=0f..10f,steps=9);Field(e.frequency,{e=e.copy(frequency=it)},"频率 / 次数");Field(e.duration,{e=e.copy(duration=it)},"持续时间")}
  EntryKind.MEDICAL->{RememberedField(e.hospital,{e=e.copy(hospital=it)},"医院",remember(store){store.rememberedHospitals()});Field(e.category,{e=e.copy(category=it)},"分类（诊断、影像、出院等）")}
  EntryKind.MEDICATION->{Field(e.dose,{e=e.copy(dose=it)},"每次剂量（注明单位）");Field(e.frequency,{e=e.copy(frequency=it)},"用药频率 / 时间");Field(e.route,{e=e.copy(route=it)},"使用方式");Field(end,{end=it},"结束时间（选填）YYYY-MM-DD HH:mm")}
 }
 OutlinedTextField(e.note,{e=e.copy(note=it)},label={Text("备注 / 详细记录")},modifier=Modifier.fillMaxWidth(),minLines=3)
 OutlinedButton({picker.launch(arrayOf("image/*"))},enabled=!busy){Text("添加照片 / 原报告")}
 if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
 e.images.forEachIndexed{i,u->Row(verticalAlignment=Alignment.CenterVertically){TextButton({images(e.images)}){Text("查看第 ${i+1} 张原图")};IconButton({e=e.copy(images=e.images.filterIndexed{j,_->j!=i})}){Icon(Icons.Outlined.Close,"移除图片")}}}
 if(error.isNotBlank())Text(error,color=Bad)
 }}
 if(deleting)DeleteConfirmation({deleting=false},delete)
}
@Composable fun SourceViewer(uris:List<String>,close:()->Unit){
 var index by rememberSaveable{mutableIntStateOf(0)};val context=LocalContext.current
 var viewport by remember{mutableStateOf(androidx.compose.ui.unit.IntSize.Zero)}
 var zoom by rememberSaveable(index){mutableFloatStateOf(1f)};var x by rememberSaveable(index){mutableFloatStateOf(0f)};var y by rememberSaveable(index){mutableFloatStateOf(0f)}
 val loaded by produceState<Pair<android.graphics.Bitmap?,String?>>(null to null,index){value=withContext(Dispatchers.IO){try{val b=decodeReportBitmap(context,Uri.parse(uris[index]));b to null}catch(e:Exception){null to "原图无法读取：${e.message}"}}}
 FullPage("原报告 ${index+1}/${uris.size}",close,bottom={Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){TextButton({index--;zoom=1f},enabled=index>0){Text("上一页")};TextButton({zoom=1f;x=0f;y=0f}){Text("重置缩放")};TextButton({index++;zoom=1f},enabled=index<uris.lastIndex){Text("下一页")}}}){m->Box(m.clipToBounds().onSizeChanged{viewport=it}.pointerInput(index,viewport){detectTransformGestures{centroid,pan,scale,_->val next=(zoom*scale).coerceIn(1f,8f);val ratio=next/zoom;val cx=centroid.x-viewport.width/2f;val cy=centroid.y-viewport.height/2f;x=(x-cx)*ratio+cx+pan.x;y=(y-cy)*ratio+cy+pan.y;zoom=next}},contentAlignment=Alignment.Center){
 loaded.first?.let{Image(it.asImageBitmap(),"原始检查报告",Modifier.fillMaxSize().graphicsLayer{scaleX=zoom;scaleY=zoom;translationX=x;translationY=y})}?:Text(loaded.second?:"正在读取原图…")
 }}
}
fun shareText(context:Context,title:String,text:String){context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT,title).putExtra(Intent.EXTRA_TEXT,text),"分享"))}
