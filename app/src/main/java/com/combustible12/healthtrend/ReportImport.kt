package com.combustible12.healthtrend

import android.net.Uri
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.*
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ImportActions(val camera:()->Unit,val gallery:()->Unit,val paste:(String)->Unit,val manual:()->Unit,val busy:Boolean,val message:String)
private suspend fun <T> Task<T>.result():T=suspendCancellableCoroutine{c->addOnSuccessListener{if(c.isActive)c.resume(it)}.addOnFailureListener{if(c.isActive)c.resumeWithException(it)}.addOnCanceledListener{c.cancel()}}
class ReportImportViewModel(application:Application):AndroidViewModel(application){
 private val context=application.applicationContext
 private val store=HealthStore(context)
 var busy by mutableStateOf(false);private set
 var message by mutableStateOf("");private set
 var pendingDraft by mutableStateOf<ReportDraft?>(null);private set
 var pendingError by mutableStateOf<String?>(null);private set
 fun consumeDraft(){pendingDraft=null}
 fun consumeError(){pendingError=null}
 fun paste(raw:String){
  if(raw.isBlank()||busy)return
  busy=true;message="正在整理粘贴的数据…"
  viewModelScope.launch{
   try{
    val initial=pastedReportDraft(raw)
    val template=store.latestTemplate(initial.hospital,initial.type,initial.system)
    pendingDraft=pastedReportDraft(raw,template)
    message=""
   }catch(e:Exception){if(e is CancellationException)throw e;pendingError="粘贴的数据无法导入：${e.message}"}finally{busy=false}
  }
 }
 fun recognize(uris:List<Uri>){if(uris.isEmpty()||busy)return;busy=true;message="正在保存原图和识别报告…"
  viewModelScope.launch{
   val owned=mutableListOf<String>();val texts=mutableListOf<String>();val failures=mutableListOf<String>()
   try{
    val recognizer=TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    try{uris.forEachIndexed{i,uri->
     val saved=withContext(Dispatchers.IO){store.ownImage(uri)};owned.add(saved)
     message="正在识别第 ${i+1}/${uris.size} 页…"
     try{
      val input=withContext(Dispatchers.IO){InputImage.fromBitmap(decodeReportBitmap(context,Uri.parse(saved),maxDimension=12000),0)}
      val t=recognizer.process(input).result()
      texts.add(ReportOcr.tableText(t))
     }catch(e:Exception){if(e is CancellationException)throw e;failures.add("第 ${i+1} 页识别失败，原图已保留，可手动补录。")}
    }}finally{recognizer.close()}
    val raw=texts.joinToString("\n\n");val parsed=ReportParser.parse(raw)
    val meta=ReportMetadata.extract(raw,parsed)
    val hospital=meta.hospital
    val date=meta.date
    val type=meta.reportType
    val template=store.latestTemplate(hospital,type)
    val resolved=if(template!=null)templateDrivenResults(parsed,template) else parsed
    pendingDraft=ReportDraft(hospital=hospital,type=type,date=date,images=owned,ocr=raw,rows=resolved.map{p->DraftRow.from(p).copy(uncertain=metricNeedsReview(p,template))},uncertain=meta.uncertain)
    message=failures.joinToString("\n")
   }catch(e:Exception){if(e is CancellationException)throw e;pendingError="报告导入未完成：${e.message}"}finally{busy=false}
  }
 }

}
@Composable fun rememberReportImport(store:HealthStore,ready:(ReportDraft)->Unit,error:(String)->Unit,deliver:Boolean=true):ImportActions {
 val context=LocalContext.current
 val model:ReportImportViewModel=viewModel()
 var cameraUri by rememberSaveable{mutableStateOf<Uri?>(null)}
 LaunchedEffect(model.pendingDraft,deliver){if(deliver)model.pendingDraft?.let{ready(it);model.consumeDraft()}}
 LaunchedEffect(model.pendingError){model.pendingError?.let{error(it);model.consumeError()}}
 val gallery=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){model.recognize(it)}
 val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()){success->val uri=cameraUri;if(success&&uri!=null)model.recognize(listOf(uri));cameraUri=null}
 return ImportActions(camera={if(!model.busy)try{val dir=File(context.cacheDir,"capture").apply{mkdirs()};val file=File.createTempFile("report-",".jpg",dir);val uri=FileProvider.getUriForFile(context,context.packageName+".files",file);cameraUri=uri;camera.launch(uri)}catch(e:Exception){error("无法启动相机：${e.message}")}},gallery={if(!model.busy)gallery.launch(arrayOf("image/*"))},paste={model.paste(it)},manual={if(!model.busy)ready(ReportDraft(rows=listOf(DraftRow())))},busy=model.busy,message=model.message)
}

private val pastedMetadataLabel=Regex("^(医院|医疗机构|检查日期|检验日期|报告日期|日期|检查类型|报告类型|类型)\\s*[:：]")
internal fun normalizePastedReportText(raw:String)=raw.lines().map{it.trim()}.filter{it.isNotBlank()&&!pastedMetadataLabel.containsMatchIn(it)}
 .joinToString("\n"){it.replace('｜','|').split('|').joinToString(" "){part->part.trim()}.replace(Regex("\\s+")," ")}

/**
 * Converts chat-assisted, clipboard or plain-text report data into the same draft used
 * by camera/gallery OCR. A confirmed hospital template still owns every fixed field;
 * pasted text only supplies this visit's values and metadata.
 */
internal fun pastedReportDraft(raw:String,template:HospitalLabTemplate?=null):ReportDraft{
 val normalized=normalizePastedReportText(raw)
 // Clipboard rows commonly start with an explicit analyzer code (for example "TP 总蛋白 78.10 ...").
 // Bind that exact code as the parenthesized identity before the strict parser runs; this does not
 // guess by name and does not change the stored template.
 val parsed=ReportParser.parse(ReportParser.bindExplicitLeadingIdentities(normalized))
 val meta=ReportMetadata.extract(raw,parsed)
 val resolved=if(template!=null)templateDrivenResults(parsed,template) else parsed
 return ReportDraft(
  hospital=meta.hospital,type=meta.reportType,date=meta.date,ocr=normalized,
  rows=resolved.map{p->DraftRow.from(p).copy(uncertain=metricNeedsReview(p,template))},uncertain=meta.uncertain
 )
}

/** Spatial reconstruction prevents OCR block order from separating a result from its name. */
internal data class OcrCell(val text:String,val left:Int,val right:Int,val cy:Int,val h:Int)
internal fun reconstructOcrTable(cells:List<OcrCell>):String{
 if(cells.isEmpty())return ""
 val minX=cells.minOf{it.left};val maxX=cells.maxOf{it.right};val width=(maxX-minX).coerceAtLeast(1)
 // Infer a divider from row-local gaps. Full-width headers do not contribute, so they cannot
 // bridge the two analyzer tables and accidentally merge NEUT with MPV on the same line.
 val candidates=cells.groupBy{cell->
  val tolerance=(cell.h.coerceAtLeast(1)*.7).toInt().coerceAtLeast(1)
  cell.cy/tolerance
 }.values.mapNotNull{row->
  val sorted=row.sortedBy{it.left};if(sorted.size<2)null else sorted.zipWithNext()
   .map{(a,b)->(b.left-a.right) to ((a.right+b.left)/2)}
   .filter{it.first>width*.08}.maxByOrNull{it.first}
 }
 val center=(minX+maxX)/2
 val split=candidates.filter{kotlin.math.abs(it.second-center)<width*.3}.map{it.second}.sorted().let{if(it.isEmpty())null else it[it.size/2]}
 val columns=if(split==null)listOf(cells) else listOf(cells.filter{(it.left+it.right)/2<split},cells.filter{(it.left+it.right)/2>=split}).filter{it.isNotEmpty()}
 return columns.joinToString("\n"){column->
  val rows=mutableListOf<MutableList<OcrCell>>()
  column.sortedWith(compareBy<OcrCell>{it.cy}.thenBy{it.left}).forEach{cell->
   val row=rows.lastOrNull();val base=row?.firstOrNull()
   if(base!=null&&kotlin.math.abs(cell.cy-base.cy)<=minOf(cell.h,base.h)*.55)row.add(cell) else rows.add(mutableListOf(cell))
  }
  rows.joinToString("\n"){row->row.sortedBy{it.left}.joinToString("  "){it.text}}
 }
}
object ReportOcr {
 fun tableText(t:com.google.mlkit.vision.text.Text):String {
  val cells=t.textBlocks.flatMap{it.lines}.mapNotNull{line->line.boundingBox?.let{b->OcrCell(line.elements.joinToString(" "){it.text},b.left,b.right,b.centerY(),b.height())}}
  return reconstructOcrTable(cells).ifBlank{t.text}
 }
}

/**
 * Once a hospital/panel template is confirmed it owns the fixed row structure.
 * OCR contributes only this visit's value/text/comparator/raw source. A damaged OCR
 * code therefore cannot create a second row or overwrite confirmed unit/range/name.
 */
internal fun templateDrivenResults(items:List<ParsedLabResult>,template:HospitalLabTemplate):List<ParsedLabResult>{
 val matches=matchTemplateRows(template.fields,items)
 return template.fields.mapIndexed{index,field->
  val source=matches[index]
  val reliable=source?.let{templateResultIsIndependent(it,field)}==true
  ParsedLabResult(
   metricKey=field.metricKey,
   displayName=field.displayName,
   value=source?.value?.takeIf{reliable},
   unit=displayLabUnit(field.unit),
   referenceLow=field.referenceLow,
   referenceHigh=field.referenceHigh,
   rawLine=source?.rawLine.orEmpty(),
   primary=field.metricKey in ReportParser.primaryKeys,
   textValue=source?.textValue?.takeIf{reliable}.orEmpty(),
   comparator=source?.comparator?.takeIf{reliable}.orEmpty()
 )
 }
}

/**
 * Matches the whole OCR panel to the confirmed template in one pass.  Fixed fields are
 * never copied from OCR; matching only identifies which visit value belongs to each
 * template row.  A candidate can be consumed once, so one damaged OCR row cannot fill
 * several similarly named template fields.
 */
internal fun matchTemplateRows(fields:List<LabFieldTemplate>,items:List<ParsedLabResult>):Map<Int,ParsedLabResult>{
 // A confirmed template owns metric identity. OCR may only provide a value to the
 // exact same canonical metric key. Names and row position are never allowed to
 // override identity; ambiguous duplicates stay empty for explicit review.
 val byKey=items.groupBy{it.metricKey}
 return fields.mapIndexedNotNull{index,field->
  val key=field.metricKey
  byKey[key]?.singleOrNull()?.let{index to it}
 }.toMap()
}

/**
 * A template range printed without a readable result must never be promoted to the
 * visit value.  This is the dangerous OCR shape behind "2.00" replacing "2.57".
 */
internal fun templateResultIsIndependent(source:ParsedLabResult,field:LabFieldTemplate):Boolean{
 if(source.textValue.isBlank())return false
 val cleaned=source.rawLine
  .replace(Regex("(?i)[×x]?10\\s*\\^?\\s*[-+]?\\d+\\s*/\\s*[lL]")," ")
  .replace(Regex("^\\s*\\d+[.、]?\\s+(?=[A-Za-z#%\\p{IsHan}])"),"")
 val number=Regex("(?<![A-Za-z\\d.^])(?:[<>≤≥]\\s*)?[-+]?\\d+(?:\\.\\d+)?")
 val first=number.find(cleaned)?:return false
 val twoSided=Regex("[-+]?\\d+(?:\\.\\d+)?\\s*(?:-{1,2}|–|—|~|～|至)\\s*[-+]?\\d+(?:\\.\\d+)?").find(cleaned)
 val oneSided=Regex("(?:<=|>=|[<>≤≥])\\s*[-+]?\\d+(?:\\.\\d+)?|[-+]?\\d+(?:\\.\\d+)?\\s*--(?:\\s|$)").find(cleaned)
 val printedRange=twoSided?:oneSided
 // A result is a separate token before the printed range. If the first number is the
 // range itself, leave the visit value empty rather than copying a boundary.
 return printedRange==null||first.range.last<printedRange.range.first
}

fun metricNeedsReview(p:ParsedLabResult,template:HospitalLabTemplate?):Boolean{
 if(p.metricKey.isBlank()||p.textValue.isBlank())return true
 val field=template?.takeIf{it.confirmed}?.fields?.firstOrNull{it.metricKey==p.metricKey}
 // Only a confirmed hospital template is authoritative for unit/range; drafts/unconfirmed
 // templates must never suppress explicit OCR review.
 if(field!=null)return false
 // Without a confirmed template, numeric rows need a unit and a complete two-sided
 // reference range before they can be accepted without an explicit row review.
 if(p.referenceLow!=null&&p.referenceHigh!=null&&p.referenceLow>p.referenceHigh)return true
 return false
}

/** Rebind OCR fields when the hospital, panel or laboratory system changes. */
fun retargetImportedDraft(
 d:ReportDraft,store:HealthStore,
 hospital:String=d.hospital,type:String=d.type,system:String=d.system
):ReportDraft{
 val next=d.copy(hospital=hospital,type=type,system=system)
 if(d.existing!=null||(hospital==d.hospital&&type==d.type&&system==d.system))return next
 val target=store.latestTemplate(hospital,type,system)
 if(d.ocr.isBlank()){
  if(target==null)return next
  val current=d.rows.associateBy{it.key.ifBlank{ReportParser.key(it.name)}}
  return next.copy(rows=target.fields.map{field->
   current[field.metricKey]?:DraftRow(name=field.displayName,key=field.metricKey,unit=field.unit,
    low=field.referenceLow?.toString().orEmpty(),high=field.referenceHigh?.toString().orEmpty())
  })
 }
 val original=ReportParser.parse(d.ocr)
 val previous=store.latestTemplate(d.hospital,d.type,d.system)
 fun same(r:DraftRow,unit:String,low:Double?,high:Double?)=
  r.unit.trim()==unit.trim()&&r.low.toDoubleOrNull()==low&&r.high.toDoubleOrNull()==high
 if(target!=null){
  return next.copy(rows=templateDrivenResults(original,target).map{p->DraftRow.from(p).copy(uncertain=metricNeedsReview(p,target))})
 }
 val rows=d.rows.map{row->
  // No confirmed target template: preserve user edits while retargeting OCR fields.
  // A repeated metric can occur on different pages. Only rebind a row when its
  // OCR source is unique; otherwise keep the user's fields and require review.
  val rawMatches=if(row.raw.isBlank())emptyList() else original.filter{it.rawLine==row.raw}
  val keyMatches=original.filter{it.metricKey==row.key}
  val source=when{
   rawMatches.size==1->rawMatches.single()
   rawMatches.isNotEmpty()->null
   keyMatches.size==1->keyMatches.single()
   else->null
  }
  if(source==null)row.copy(uncertain=true)
  else{
   val old=previous?.fields?.firstOrNull{it.metricKey==row.key}
   val inherited=old!=null&&same(row,old.unit,old.referenceLow,old.referenceHigh)
   if(inherited||same(row,source.unit,source.referenceLow,source.referenceHigh)){
    val resolved=store.applyTemplate(listOf(source),target).single()
    row.copy(unit=resolved.unit,low=resolved.referenceLow?.toString().orEmpty(),
     high=resolved.referenceHigh?.toString().orEmpty(),
     uncertain=metricNeedsReview(resolved,target))
   }else row.copy(uncertain=true)
  }
 }
 return next.copy(rows=rows)
}

data class ReportMetadataResult(val hospital:String,val reportType:String,val date:String,val uncertain:Set<String>)
object ReportMetadata {
 private val dateRegex=Regex("(20\\d{2})\\s*[-/年.]\\s*(\\d{1,2})\\s*[-/月.]\\s*(\\d{1,2})(?:\\s*[日号]?)")
 fun extract(raw:String,items:List<ParsedLabResult>):ReportMetadataResult{
  val lines=raw.lines().map{it.trim()}.filter{it.isNotBlank()}
  val hospitalLabel=Regex("^(医院名称|送检医院|医疗机构|机构名称)\\s*[:：]?\\s*")
  val hospitalCandidates=lines.mapNotNull{line->
   val labelled=hospitalLabel.find(line)
   val stripped=if(labelled!=null)line.substring(labelled.range.last+1).trim() else line
   val candidate=stripped.substringAfterLast("：").substringAfterLast(":").trim()
   candidate.takeIf{it.length in 4..60&&(it.contains("医院")||it.contains("保健院")||it.contains("卫生院")||it.contains("医学中心"))}
  }
  val hospital=hospitalCandidates.minByOrNull{it.length}.orEmpty()
  val preferredDateLabels=listOf("检查日期","检查时间","检验日期","检验时间","报告日期","报告时间")
  val fallbackClinicalLabels=listOf("采样日期","采样时间")
  val excludedDateLabels=listOf("出生","生日","送检","审核","打印")
  val preferredDates=lines.filter{line->preferredDateLabels.any{line.contains(it)}&&excludedDateLabels.none{line.contains(it)}}.mapNotNull{dateRegex.find(it)}
  val sampledDates=lines.filter{line->fallbackClinicalLabels.any{line.contains(it)}}.mapNotNull{dateRegex.find(it)}
  // Patient identity fields and report timing are separate domains. Generic date/time labels
  // are accepted only when unambiguous and never allowed to override an explicit clinical date.
  val genericDates=lines.filter{line->(line.contains("日期")||line.contains("时间"))&&excludedDateLabels.none{line.contains(it)}}.mapNotNull{dateRegex.find(it)}
  val fallbackDates=lines.filter{line->excludedDateLabels.none{line.contains(it)}}.flatMap{line->dateRegex.findAll(line).toList()}
  val dates=when{preferredDates.isNotEmpty()->preferredDates;sampledDates.isNotEmpty()->sampledDates;genericDates.size==1->genericDates;fallbackDates.size==1->fallbackDates;else->emptyList()}
  fun normalized(m:MatchResult)="${m.groupValues[1]}-${m.groupValues[2].padStart(2,'0')}-${m.groupValues[3].padStart(2,'0')}"
  val distinct=dates.map(::normalized).distinct()
  val administrativeDates=lines.filter{line->excludedDateLabels.any{line.contains(it)}}.flatMap{line->dateRegex.findAll(line).map(::normalized).toList()}
  val candidate=distinct.singleOrNull().orEmpty()
  // An administrative timestamp never supplies the report date, but a same-month/day
  // year conflict is strong evidence that OCR changed 2026 into 2016. Require review.
  val yearConflict=candidate.isNotBlank()&&administrativeDates.any{it.substring(5)==candidate.substring(5)&&it.substring(0,4)!=candidate.substring(0,4)}
  val date=candidate.takeUnless{yearConflict}.orEmpty()
  val explicit=when{
   raw.contains("血常规")||raw.contains("血细胞分析")->"血常规"
   raw.contains("生化")->"生化"
   raw.contains("肝功能")->"肝功能"
   raw.contains("肾功能")->"肾功能"
   raw.contains("肿瘤标志")||raw.contains("肿瘤标记")->"肿瘤标志物"
   else->""
  }
  val keys=items.map{it.metricKey}.toSet()
  val inferred=when{
   keys.intersect(setOf("WBC","NEUT#","HGB","PLT","RBC")).size>=2->"血常规"
   // A mixed chemistry panel must stay one panel instead of being split into
   // liver/kidney templates merely because it contains ALT or CREA.
   keys.intersect(setOf("TP","ALB","GLOB","TBIL","ALT","AST","GGT","ALP","CHE","TBA","PA","UREA","CREA","UA")).size>=5->"生化"
   keys.intersect(setOf("ALT","AST","TBIL","ALB")).size>=2->"肝功能"
   keys.intersect(setOf("CREA","UREA","UA")).size>=2->"肾功能"
   else->"检查报告"
  }
  val type=explicit.ifBlank{inferred}
  val uncertain=buildSet{if(hospital.isBlank())add("hospital");if(date.isBlank())add("date");if(explicit.isBlank())add("type")}
  return ReportMetadataResult(hospital,type,date,uncertain)
 }
}