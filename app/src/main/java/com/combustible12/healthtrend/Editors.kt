package com.combustible12.healthtrend

import android.content.Intent
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
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
data class DraftRow(val id:String=newId(),val name:String="",val key:String="",val text:String="",val unit:String="",val low:String="",val high:String="",val raw:String="",val uncertain:Boolean=false,val trendMeaning:String=""):java.io.Serializable {
 fun parsed():ParsedLabResult{val cleanUnit=unit.trim().takeUnless{it.toDoubleOrNull()!=null}.orEmpty();return ParsedLabResult(key,name,text.trim().trimStart('<','>','≤','≥').toDoubleOrNull(),cleanUnit,low.toDoubleOrNull(),high.toDoubleOrNull(),raw,key in ReportParser.primaryKeys,text.trim(),text.trim().takeWhile{it in "<>≤≥"})}
 fun valid()=name.isNotBlank()&&key.isNotBlank()&&text.isNotBlank()&&(low.isBlank()||low.toDoubleOrNull()?.isFinite()==true)&&(high.isBlank()||high.toDoubleOrNull()?.isFinite()==true)&&ReportParser.valid(listOf(parsed()))
 companion object{fun from(p:ParsedLabResult)=DraftRow(name=p.displayName,key=p.metricKey,text=p.textValue,unit=p.unit,low=p.referenceLow?.toString().orEmpty(),high=p.referenceHigh?.toString().orEmpty(),raw=p.rawLine,uncertain=p.metricKey.isBlank()||p.textValue.isBlank()||(p.referenceLow==null)!=(p.referenceHigh==null))}
}
data class ReportDraft(val hospital:String="",val type:String="血常规",val system:String="",val date:String=dateText(System.currentTimeMillis()),val images:List<String> = emptyList(),val ocr:String="",val rows:List<DraftRow> = emptyList(),val uncertain:Set<String> = emptySet(),val newTemplate:Boolean=false,val existing:LabReport?=null):java.io.Serializable{
 fun parsed()=rows.map{it.parsed()}
 fun valid()=hospital.isNotBlank()&&parseDate(date)!=null&&rows.isNotEmpty()&&rows.all{it.valid()}
 companion object{fun from(r:LabReport)=ReportDraft(hospital=r.hospitalKey,type=r.reportType,system=r.systemKey,date=dateText(r.testedAtEpochMillis),images=r.sourceImages.map{it.uri},ocr=r.rawOcr,rows=r.results.map{x->DraftRow(x.id,x.rawName,x.metricKey,x.textValue,x.unitAtTest,x.referenceLowAtTest?.toString().orEmpty(),x.referenceHighAtTest?.toString().orEmpty(),x.rawLine)},existing=r)}
}
val LocalPageVisible=staticCompositionLocalOf{true}
@Composable fun FullPage(title:String,onClose:()->Unit,hidden:Boolean=false,navigationIcon:ImageVector=Icons.Outlined.Close,bottom:@Composable ()->Unit={},content:@Composable (Modifier)->Unit){
 val active=!hidden&&LocalPageVisible.current
 val focus=LocalFocusManager.current
 BackHandler(enabled=active,onBack=onClose)
 LaunchedEffect(active){if(active)focus.clearFocus()}
 Surface(color=Warm,modifier=Modifier.fillMaxSize().then(if(active)Modifier else Modifier.clearAndSetSemantics{})){Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()){
  Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){IconButton(onClose){Icon(navigationIcon,if(navigationIcon==Icons.Outlined.Close)"关闭" else "返回")};Text(title,fontSize=21.sp,modifier=Modifier.weight(1f))}
  Box(Modifier.weight(1f)){content(Modifier.fillMaxSize())}
  Surface(color=ColorWhite){Box(Modifier.fillMaxWidth().padding(12.dp)){bottom()}}
 }}
}
private val ColorWhite=androidx.compose.ui.graphics.Color.White
@Composable fun Field(value:String,onChange:(String)->Unit,label:String,m:Modifier=Modifier){Column(m.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(5.dp)){Text(label,fontSize=13.sp,color=Ink,modifier=Modifier.padding(start=10.dp));Row(Modifier.fillMaxWidth().background(Color.White,RoundedCornerShape(14.dp)).padding(start=14.dp,end=4.dp),verticalAlignment=Alignment.CenterVertically){BasicTextField(value,onChange,Modifier.weight(1f).padding(vertical=16.dp),singleLine=true,textStyle=LocalTextStyle.current.copy(fontSize=16.sp,color=Ink));if(value.isNotEmpty())IconButton({onChange("")}){Icon(Icons.Outlined.Clear,"清空$label")}}}}
@Composable fun RememberedField(value:String,onChange:(String)->Unit,label:String,options:List<String>,m:Modifier=Modifier){
 val saved=options.map{it.trim()}.filter{it.isNotBlank()}.distinct()
 Column(m.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(6.dp)){
  if(saved.isNotEmpty())Row(Modifier.horizontalScroll(rememberScrollState())){saved.forEach{option->FilterChip(selected=value.trim()==option,onClick={onChange(option)},label={Text(option)},modifier=Modifier.padding(end=8.dp))}}
  Field(value,onChange,label)
 }
}
@Composable fun ReportEditor(initial:ReportDraft,store:HealthStore,onClose:()->Unit,save:(ReportDraft)->Unit,images:(List<String>)->Unit){
 val context=LocalContext.current
 var d by remember(initial){mutableStateOf(initial)}
 var pendingDraftReplace by remember{mutableStateOf<Int?>(null)}
 var viewingDraftImage by rememberSaveable{mutableStateOf<Int?>(null)}
 var draftImageError by remember{mutableStateOf("")}
 val draftImageInput=rememberPhotoInput({uris->
  if(uris.isNotEmpty()){
   try{
    val owned=uris.map{store.ownImage(it)}
    val index=pendingDraftReplace;pendingDraftReplace=null
    d=if(index!=null&&index in d.images.indices)d.copy(images=d.images.toMutableList().also{it[index]=owned.first()})
      else d.copy(images=d.images+owned)
   }catch(e:Exception){draftImageError="原图导入失败：${e.message}"}
  }
 },{draftImageError=it})
 var editing by rememberSaveable{mutableStateOf<String?>(null)}
 var structureUnlocked by rememberSaveable{mutableStateOf(false)}
 FullPage(if(d.existing==null)"核对检查报告"else"编辑检查报告",onClose,hidden=editing!=null,bottom={Button({save(d)},Modifier.fillMaxWidth(),enabled=d.valid()){Text("保存")}}){m->
 ScrollablePageColumn(m,PaddingValues(horizontal=16.dp),Arrangement.spacedBy(12.dp)){
  run{RememberedField(d.hospital,{value->d=updateOcrMetadata(retargetImportedDraft(d,store,hospital=value),"hospital",value)},"医院",remember(store){store.rememberedHospitals()})}
  run{RememberedField(d.type,{value->d=updateOcrMetadata(retargetImportedDraft(d,store,type=value),"type",value)},"检查类型",remember(d.hospital,store){(store.templates().filter{it.hospitalKey==d.hospital}.map{it.reportType}+listOf("血常规","生化")).distinct()})}
  run{Field(d.date,{value->d=updateOcrMetadata(d,"date",value)},"检查日期/时间")}
  PhotoInputButtons(draftImageInput,true,"添加报告原图")
  if(d.images.isNotEmpty())TextButton({viewingDraftImage=0}){Text("查看原报告 · ${d.images.size} 页")}
  d.images.forEachIndexed{i,_->Row(verticalAlignment=Alignment.CenterVertically){
   Text("第 ${i+1} 张",modifier=Modifier.weight(1f),color=Muted)
   TextButton({pendingDraftReplace=i;draftImageInput.gallery()}){Text("更换")}
   TextButton({d=d.copy(images=d.images.filterIndexed{j,_->j!=i})}){Text("移除",color=Bad)}
  }}
  if(draftImageError.isNotBlank())Text(draftImageError,color=Bad)

  val activeTemplate=store.latestTemplate(d.hospital,d.type,d.system)
  d.rows.forEachIndexed{i,r->LabRowSummary(r,{editing=r.id},if(activeTemplate==null||structureUnlocked){{d=d.copy(rows=d.rows.filterIndexed{j,_->j!=i})}}else null)}
  run{
   if(activeTemplate==null||structureUnlocked)OutlinedButton({val added=DraftRow();d=d.copy(rows=d.rows+added);editing=added.id},Modifier.fillMaxWidth()){Text("+ 添加遗漏指标")}
   else TextButton({structureUnlocked=true}){Text("本次报告项目有变化")}
   Spacer(Modifier.height(12.dp))
  }
 }
 }
 d.rows.firstOrNull{it.id==editing}?.let{row->
  val units=remember(row.key,d.rows){(store.rememberedUnits(row.key)+d.rows.filter{it.key==row.key}.map{it.unit}).map{it.trim()}.filter{it.isNotBlank()}.distinct()}
  // Derive locks from the original saved row, never from the code being typed.
  // Otherwise entering an existing template code changes the editor structure mid-keystroke.
  val originalResult=d.existing?.results?.firstOrNull{it.id==row.id}
  val originalCode=originalResult?.metricKey
  val fixed=originalCode!=null && store.latestTemplate(d.hospital,d.type,d.system)?.fields?.any{it.metricKey==originalCode}==true
  MetricEditor(row,{changed->d=d.copy(rows=d.rows.map{if(it.id==changed.id)changed else it})},{editing=null},unitOptions=units,lockMetadata=fixed,lockIdentity=originalResult!=null)
 }
 if(viewingDraftImage!=null&&d.images.isNotEmpty())SourceViewer(
  d.images,
  {viewingDraftImage=null},
  viewingDraftImage!!.coerceIn(d.images.indices),
  onReplace={i->pendingDraftReplace=i;draftImageInput.gallery()},
  onRemove={i->
   val next=d.images.filterIndexed{j,_->j!=i};d=d.copy(images=next)
   if(next.isEmpty())viewingDraftImage=null else viewingDraftImage=i.coerceAtMost(next.lastIndex)
  },
  onMove={from,to->
   if(from in d.images.indices&&to in d.images.indices){val moved=d.images.toMutableList();val item=moved.removeAt(from);moved.add(to,item);d=d.copy(images=moved);viewingDraftImage=to}
  },
  onAdd={draftImageInput.gallery()},
  onAddCamera={draftImageInput.camera()},
  removalMessage="仅修改当前检查报告草稿，取消编辑不会更改已保存的原图。"
 )
}
@Composable fun LabRowSummary(r:DraftRow,edit:()->Unit,remove:(()->Unit)?,templateOnly:Boolean=false){Card(modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
 Row{Text(labDisplayTitle(r.name,r.key).ifBlank{"待核对指标"},modifier=Modifier.weight(1f));remove?.let{action->IconButton(action){Icon(Icons.Outlined.Delete,"删除指标")}}}
 if(templateOnly){Text(displayLabUnit(r.unit),fontSize=22.sp);if(r.trendMeaning.isNotBlank())Text(r.trendMeaning,color=Accent,fontSize=12.sp)} else ResultValueUnit(r.text,r.unit);Text("参考 ${rangeText(r.low.toDoubleOrNull(),r.high.toDoubleOrNull())}",color=Muted)
 TextButton(edit,contentPadding=PaddingValues(horizontal=4.dp,vertical=0.dp),modifier=Modifier.heightIn(min=28.dp)){Text("编辑")}
}}}
@Composable fun MetricEditor(row:DraftRow,edit:(DraftRow)->Unit,close:()->Unit,templateOnly:Boolean=false,unitOptions:List<String> = emptyList(),lockMetadata:Boolean=false,lockIdentity:Boolean=false){
 var unlock by rememberSaveable(row.id){mutableStateOf(false)}
 FullPage("核对指标",close,bottom={Button({if(row.valid()){edit(row.copy(uncertain=false));close()}},Modifier.fillMaxWidth(),enabled=row.valid()){Text("完成核对")}}){m->ScrollablePageColumn(m,PaddingValues(16.dp)){
  LabRowEditor(row,edit,templateOnly,unitOptions,lockMetadata&&!unlock,lockIdentity)
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
@Composable fun ResultValueUnit(value:String,unit:String,large:Boolean=false){Row(verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(8.dp)){Text(value,fontSize=if(large)27.sp else 24.sp,fontWeight=FontWeight.Bold);if(unit.isNotBlank())Text(displayLabUnit(unit),fontSize=14.sp,color=Muted,modifier=Modifier.padding(bottom=3.dp))}}
@Composable fun LabRowEditor(r:DraftRow,edit:(DraftRow)->Unit,templateOnly:Boolean=false,unitOptions:List<String> = emptyList(),lockMetadata:Boolean=false,lockIdentity:Boolean=false){Paper{
 Text("指标")
 if(lockMetadata)Text(labDisplayTitle(r.name,r.key),fontSize=18.sp) else {
  Field(labDisplayTitle(r.name,r.key),{edit(r.copy(name=it))},"项目名称")
  if(!lockIdentity)Field(r.key,{edit(r.copy(key=it))},"项目代码")
 }
 if(templateOnly)Field(r.trendMeaning,{edit(r.copy(trendMeaning=it))},"趋势说明（箭头在前）")
 if(!templateOnly)Field(r.text,{edit(r.copy(text=it))},"结果（支持 <、>、阴性等）")
 if(lockMetadata){Text("${r.unit.ifBlank{"单位未录入"}} · 参考 ${rangeText(r.low.toDoubleOrNull(),r.high.toDoubleOrNull())}",color=Muted)}else{
  if(unitOptions.isNotEmpty())Row(Modifier.horizontalScroll(rememberScrollState())){unitOptions.forEach{unit->FilterChip(selected=r.unit==unit,onClick={edit(r.copy(unit=unit))},label={Text(unit)},modifier=Modifier.padding(end=8.dp))}}
  Field(r.unit,{edit(r.copy(unit=it))},"单位")
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Field(r.low,{edit(r.copy(low=it))},"参考下限",Modifier.weight(1f));Field(r.high,{edit(r.copy(high=it))},"参考上限",Modifier.weight(1f))}
 }
}}
@Composable fun ReportDetail(r:LabReport,store:HealthStore,close:()->Unit,edit:()->Unit,images:(List<String>)->Unit,delete:()->Unit,update:(LabResult,Double,String)->Unit,onImagesChanged:()->Unit){
 var point by remember{mutableStateOf<LabResult?>(null)};var value by remember{mutableStateOf("")};var confirmDelete by remember{mutableStateOf(false)}
 var imageError by remember{mutableStateOf<String?>(null)};var busy by remember{mutableStateOf(false)}
 val scope=rememberCoroutineScope()
 val photoInput=rememberPhotoInput({uris->if(uris.isNotEmpty()){busy=true;scope.launch{try{withContext(Dispatchers.IO){store.addReportImages(r.id,uris)};onImagesChanged()}catch(x:Exception){imageError="原图保存失败：${x.message}"}finally{busy=false}}}},{imageError=it})
 FullPage(r.reportType,close,bottom={Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){TextButton(edit){Text("编辑报告")};TextButton({confirmDelete=true}){Text("删除报告",color=Bad)}}}){m->LazyColumn(m.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.Top){Column{Text(r.hospitalKey);Text(dateText(r.testedAtEpochMillis),color=Muted)};TextButton({if(r.sourceImages.isNotEmpty())images(r.sourceImages.map{it.uri}) else photoInput.gallery()},enabled=!busy){Text(if(r.sourceImages.isNotEmpty())"查看图片" else "导入图片")}}}
  item{PhotoInputButtons(photoInput,!busy,"相册添加原图")}
  if(busy)item{LinearProgressIndicator(Modifier.fillMaxWidth())}
  itemsIndexed(r.results,key={_,x->x.id}){_,x->Paper{Row{Text(labDisplayTitle(x.rawName,x.metricKey),modifier=Modifier.weight(1f));Text(x.status().label(),color=statusColor(x.status()))};ResultValueUnit(x.textValue,x.unitAtTest);Text("当次参考：${rangeText(x.referenceLowAtTest,x.referenceHighAtTest)}",color=Muted);if(x.editedByUser)Text("已手动修正",color=Accent,fontSize=12.sp);if(x.value!=null)TextButton({point=x;value=x.textValue.ifBlank{x.value.toString()}}){Text("编辑数据点")}}}
 }}
 if(point!=null)AlertDialog(onDismissRequest={point=null},title={Text("编辑 ${labDisplayTitle(point!!.rawName,point!!.metricKey)}")},text={Column{Field(value,{value=it},"结果")}},confirmButton={TextButton({update(point!!,value.toDouble(),value.trim());point=null},enabled=value.toDoubleOrNull()?.isFinite()==true){Text("保存")}},dismissButton={TextButton({point=null}){Text("取消")}})
 if(confirmDelete)DeleteConfirmation({confirmDelete=false},delete)
 if(imageError!=null)AlertDialog(onDismissRequest={imageError=null},title={Text("操作未完成")},text={Text(imageError!!)},confirmButton={TextButton({imageError=null}){Text("知道了")}})
}
@Composable fun DeleteConfirmation(close:()->Unit,remove:()->Unit){AlertDialog(onDismissRequest=close,title={Text("删除这条记录？")},text={Text("删除后无法在应用内恢复。")},confirmButton={TextButton({remove();close()}){Text("删除",color=Bad)}},dismissButton={TextButton(close){Text("取消")}})}
@Composable fun TemplateEditor(t:HospitalLabTemplate,close:()->Unit,save:(List<LabFieldTemplate>)->Unit){
 val context=LocalContext.current
 var editing by rememberSaveable{mutableStateOf<String?>(null)}
 var addedRowIds by rememberSaveable{mutableStateOf<List<String>>(emptyList())}
 var rows by remember(t.hospitalKey,t.reportType,t.fields){mutableStateOf(t.fields.map{DraftRow(name=it.displayName,key=it.metricKey,text="0",unit=it.unit,low=it.referenceLow?.toString().orEmpty(),high=it.referenceHigh?.toString().orEmpty(),trendMeaning=it.trendMeaning.ifBlank{metricPurpose(it.metricKey).orEmpty()})})}
 FullPage("医院模板",close,hidden=editing!=null,bottom={Button({save(rows.map{LabFieldTemplate(it.key,it.name,displayLabUnit(it.unit),it.low.toDoubleOrNull(),it.high.toDoubleOrNull(),it.trendMeaning.trim())})},Modifier.fillMaxWidth(),enabled=rows.isNotEmpty()&&rows.all{it.valid()}){Text("保存模板")}}){m->Column(m.padding(horizontal=12.dp)){
 Text("${t.hospitalKey}\n${t.reportType}",Modifier.padding(vertical=6.dp))
 LazyVerticalGrid(columns=GridCells.Fixed(2),modifier=Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp),contentPadding=PaddingValues(vertical=6.dp)){
  gridItemsIndexed(rows,key={_,r->r.id}){i,r->LabRowSummary(r,{editing=r.id},{rows=rows.filterIndexed{j,_->i!=j}},true)}
  item{TextButton({val added=DraftRow(text="0");rows=rows+added;addedRowIds=addedRowIds+added.id;editing=added.id}){Text("+ 添加指标")}}
 }
 }}
 rows.firstOrNull{it.id==editing}?.let{row->MetricEditor(row,{changed->rows=rows.map{if(it.id==changed.id)changed else it}},{editing=null},true,rows.filter{it.key==row.key}.map{it.unit}.filter{it.isNotBlank()}.distinct(),lockIdentity=row.id !in addedRowIds)}
}
@Composable fun EntryEditor(initial:HealthEntry,store:HealthStore,close:()->Unit,save:(HealthEntry)->Unit,delete:()->Unit,images:(List<String>)->Unit){
 val context=LocalContext.current
 var e by rememberSaveable(initial,stateSaver=diskStateSaver<HealthEntry>(context,"entry-editor")){mutableStateOf(initial)};var date by rememberSaveable{mutableStateOf(dateText(initial.occurredAtEpochMillis))};var end by rememberSaveable{mutableStateOf(initial.endAtEpochMillis?.let{dateText(it)}.orEmpty())};var error by remember{mutableStateOf("")};var deleting by remember{mutableStateOf(false)};var busy by remember{mutableStateOf(false)}
 val scope=rememberCoroutineScope()
 val photoInput=rememberPhotoInput({uris->if(uris.isNotEmpty()){busy=true;scope.launch{try{val owned=withContext(Dispatchers.IO){uris.map{store.ownImage(it)}};e=e.copy(images=e.images+owned)}catch(x:Exception){error="原图保存失败：${x.message}"}finally{busy=false}}}},{error=it})
 var pendingImageReplace by remember{mutableStateOf<Int?>(null)}
 val replaceInput=rememberPhotoInput({uris->
  val index=pendingImageReplace;pendingImageReplace=null
  if(index!=null&&uris.isNotEmpty()&&!busy){busy=true;scope.launch{try{
   val owned=withContext(Dispatchers.IO){store.ownImage(uris.first())}
   if(index in e.images.indices)e=e.copy(images=e.images.toMutableList().also{it[index]=owned})
  }catch(x:Exception){error="更换图片失败：${x.message}"}finally{busy=false}}}
 },{error=it})
 var pendingImageRemoval by remember{mutableStateOf<Int?>(null)}
 var viewingImage by rememberSaveable{mutableStateOf<Int?>(null)}
 val valid=e.title.isNotBlank()&&parseDate(date)!=null&&(end.isBlank()||parseDate(end)?.let{it>=parseDate(date)!!}==true)&&!busy
 FullPage(e.kind.title,close,bottom={Row{Button({save(e.copy(occurredAtEpochMillis=preserveTimestamp(date,initial.occurredAtEpochMillis)!!,endAtEpochMillis=preserveTimestamp(end,initial.endAtEpochMillis)))},Modifier.weight(1f),enabled=valid){Text("保存记录")};if(store.entries().any{it.id==e.id})TextButton({deleting=true}){Text("删除",color=Bad)}}}){m->ScrollablePageColumn(m,PaddingValues(16.dp),Arrangement.spacedBy(12.dp)){
 Field(e.title,{e=e.copy(title=it)},when(e.kind){EntryKind.SYMPTOM->"症状名称";EntryKind.MEDICAL->"病历标题";EntryKind.MEDICATION->"药品名称"});Field(date,{date=it},if(e.kind==EntryKind.MEDICATION)"开始时间 YYYY-MM-DD HH:mm"else"发生时间 YYYY-MM-DD HH:mm")
 when(e.kind){
  EntryKind.SYMPTOM->{Text("程度 ${e.severity}/10");Slider(e.severity.toFloat(),{e=e.copy(severity=it.toInt())},valueRange=0f..10f,steps=9);Field(e.frequency,{e=e.copy(frequency=it)},"频率 / 次数");Field(e.duration,{e=e.copy(duration=it)},"持续时间")}
  EntryKind.MEDICAL->{RememberedField(e.hospital,{e=e.copy(hospital=it)},"医院",remember(store){store.rememberedHospitals()});Field(e.category,{e=e.copy(category=it)},"分类（诊断、影像、出院等）")}
  EntryKind.MEDICATION->{Field(e.dose,{e=e.copy(dose=it)},"每次剂量（注明单位）");Field(e.frequency,{e=e.copy(frequency=it)},"用药频率 / 时间");Field(e.route,{e=e.copy(route=it)},"使用方式");Field(end,{end=it},"结束时间（选填）YYYY-MM-DD HH:mm")}
 }
 OutlinedTextField(e.note,{e=e.copy(note=it)},label={Text("备注 / 详细记录")},modifier=Modifier.fillMaxWidth(),minLines=3,trailingIcon={if(e.note.isNotEmpty())IconButton({e=e.copy(note="")}){Icon(Icons.Outlined.Clear,"清空备注")}})
 PhotoInputButtons(photoInput,!busy,"相册添加照片")
 if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
 e.images.forEachIndexed{i,u->Row(verticalAlignment=Alignment.CenterVertically){TextButton({viewingImage=i}){Text("查看第 ${i+1} 张原图")};IconButton({pendingImageReplace=i;replaceInput.gallery()},enabled=!busy){Icon(Icons.Outlined.Edit,"更换图片")};IconButton({pendingImageRemoval=i}){Icon(Icons.Outlined.Close,"移除图片")}}}
 if(error.isNotBlank())Text(error,color=Bad)
 }}
 if(pendingImageRemoval!=null)AlertDialog(onDismissRequest={pendingImageRemoval=null},title={Text("移除这张图片？")},text={Text("仅修改当前编辑草稿，取消编辑不会删除已保存的原图。")},confirmButton={TextButton({val index=pendingImageRemoval!!;e=e.copy(images=e.images.filterIndexed{j,_->j!=index});pendingImageRemoval=null}){Text("移除",color=Bad)}},dismissButton={TextButton({pendingImageRemoval=null}){Text("取消")}})
 if(viewingImage!=null&&e.images.isNotEmpty())SourceViewer(
  e.images,
  {viewingImage=null},
  viewingImage!!.coerceIn(e.images.indices),
  onReplace={i->pendingImageReplace=i;replaceInput.gallery()},
  onRemove={i->
   val next=e.images.filterIndexed{j,_->j!=i};e=e.copy(images=next)
   if(next.isEmpty())viewingImage=null else viewingImage=i.coerceAtMost(next.lastIndex)
  },
  onMove={from,to->
   if(from in e.images.indices&&to in e.images.indices){val moved=e.images.toMutableList();val item=moved.removeAt(from);moved.add(to,item);e=e.copy(images=moved);viewingImage=to}
  },
  onAdd={photoInput.gallery()},
  onAddCamera={photoInput.camera()},
  removalMessage="仅修改当前编辑草稿，取消编辑不会删除已保存的原图。"
 )
 if(deleting)DeleteConfirmation({deleting=false},delete)
}
/** Shared source-image viewer used by reports, entries and course records.
 * Image Documents retains its OCR-aware canvas; gestures and thumbnail navigation match here.
 */
@Composable fun SourceViewer(uris:List<String>,close:()->Unit,initialIndex:Int=0,onReplace:((Int)->Unit)?=null,onRemove:((Int)->Unit)?=null,onMove:((Int,Int)->Unit)?=null,onAdd:(()->Unit)?=null,onAddCamera:(()->Unit)?=null,reportInfo:LabReport?=null,removalMessage:String="仅移除当前图片，不影响其他记录。"){
 if(uris.isEmpty()){LaunchedEffect(Unit){close()};return}
 var index by rememberSaveable(uris,initialIndex){mutableIntStateOf(initialIndex.coerceIn(uris.indices))}
 var confirmRemoval by remember{mutableStateOf(false)}
 val context=LocalContext.current
 val listState=rememberLazyListState()
 var viewport by remember{mutableStateOf(androidx.compose.ui.unit.IntSize.Zero)}
 var retry by remember(index){mutableIntStateOf(0)}
 fun select(next:Int){if(next in uris.indices)index=next}
 LaunchedEffect(index){listState.animateScrollToItem(index)}
 val loaded by produceState<Pair<android.graphics.Bitmap?,String?>>(null to null,index,retry){
  value=withContext(Dispatchers.IO){try{decodeReportBitmap(context,Uri.parse(uris[index])) to null}catch(e:Exception){null to "原图无法读取：${e.message}"}}
 }
 val bitmap=loaded.first
 val gesture=rememberImageGestureState(index,viewport,bitmap?.width?:0,bitmap?.height?:0,{select(index-1)},{select(index+1)})
 FullPage(if(reportInfo!=null)"${reportInfo.reportType} · 图片 ${index+1}/${uris.size}" else "图片 ${index+1}/${uris.size}",close,bottom={
  Column{
   LazyRow(Modifier.fillMaxWidth().height(64.dp),state=listState,horizontalArrangement=Arrangement.spacedBy(6.dp),contentPadding=PaddingValues(horizontal=8.dp)){
    itemsIndexed(uris){i,uri->
     val thumb by produceState<android.graphics.Bitmap?>(null,uri){value=withContext(Dispatchers.IO){runCatching{decodeReportBitmap(context,Uri.parse(uri))}.getOrNull()}}
     Box(Modifier.size(54.dp).border(if(i==index)2.dp else 1.dp,if(i==index)Accent else Color.LightGray,RoundedCornerShape(8.dp)).clickable{select(i)},contentAlignment=Alignment.Center){
      if(thumb!=null)Image(thumb!!.asImageBitmap(),"第 ${i+1} 张",Modifier.fillMaxSize(),contentScale=androidx.compose.ui.layout.ContentScale.Crop)
      else Text("${i+1}",color=Muted)
     }
    }
   }
   if(onReplace!=null||onRemove!=null||onAdd!=null||onAddCamera!=null||onMove!=null){
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){
     if(onReplace!=null)TextButton({onReplace(index)}){Text("更换")}
     if(onAdd!=null)TextButton(onAdd){Text("相册添加")}
     if(onAddCamera!=null)TextButton(onAddCamera){Text("直接拍照")}
     if(onRemove!=null)TextButton({confirmRemoval=true}){Text("删除",color=Bad)}
     if(onMove!=null){
      TextButton({onMove(index,index-1)},enabled=index>0){Text("前移")}
      TextButton({onMove(index,index+1)},enabled=index<uris.lastIndex){Text("后移")}
     }
    }
   }
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
    TextButton({select(index-1)},enabled=index>0){Text("上一张")}
    TextButton({gesture.reset()}){Text("重置缩放")}
    TextButton({select(index+1)},enabled=index<uris.lastIndex){Text("下一张")}
   }
  }
 }){m->
  Column(m){
   if(reportInfo!=null)Column(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(3.dp)){
    Text(reportInfo.hospitalKey,color=Ink,fontSize=14.sp)
    Text(dateText(reportInfo.testedAtEpochMillis),color=Muted,fontSize=13.sp)
   }
   Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().onSizeChanged{viewport=it}.then(gesture.modifier),contentAlignment=Alignment.Center){
    if(loaded.first!=null)Image(loaded.first!!.asImageBitmap(),"图片",Modifier.fillMaxSize().graphicsLayer{scaleX=gesture.zoom;scaleY=gesture.zoom;translationX=gesture.x;translationY=gesture.y},contentScale=androidx.compose.ui.layout.ContentScale.Fit)
    else Column(horizontalAlignment=Alignment.CenterHorizontally){
     Text(loaded.second?:"正在读取原图…")
     if(loaded.second!=null)TextButton({retry++}){Text("重试")}
    }
   }
  }
  }
 if(confirmRemoval)AlertDialog(onDismissRequest={confirmRemoval=false},title={Text("删除这张图片？")},text={Text(removalMessage)},confirmButton={TextButton({confirmRemoval=false;onRemove?.invoke(index)}){Text("删除",color=Bad)}},dismissButton={TextButton({confirmRemoval=false}){Text("取消")}})

}
fun shareText(context:Context,title:String,text:String){context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT,title).putExtra(Intent.EXTRA_TEXT,text),"分享"))}
