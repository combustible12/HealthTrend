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

class ImportActions(val camera:()->Unit,val gallery:()->Unit,val manual:()->Unit,val busy:Boolean,val message:String)
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
    pendingDraft=ReportDraft(hospital=hospital,type=type,date=date,images=owned,ocr=raw,rows=store.applyTemplate(parsed,template).map{p->DraftRow.from(p).copy(uncertain=metricNeedsReview(p,template))},uncertain=meta.uncertain)
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
 return ImportActions(camera={if(!model.busy)try{val dir=File(context.cacheDir,"capture").apply{mkdirs()};val file=File.createTempFile("report-",".jpg",dir);val uri=FileProvider.getUriForFile(context,context.packageName+".files",file);cameraUri=uri;camera.launch(uri)}catch(e:Exception){error("无法启动相机：${e.message}")}},gallery={if(!model.busy)gallery.launch(arrayOf("image/*"))},manual={if(!model.busy)ready(ReportDraft(rows=listOf(DraftRow())))},busy=model.busy,message=model.message)
}

/** Spatial reconstruction prevents OCR block order from separating a result from its name. */
object ReportOcr {
 fun tableText(t:com.google.mlkit.vision.text.Text):String {
  val lines=t.textBlocks.flatMap{it.lines}.filter{it.boundingBox!=null}.sortedBy{it.boundingBox!!.centerY()}
  val groups=mutableListOf<MutableList<com.google.mlkit.vision.text.Text.Line>>()
  lines.forEach{line->val box=line.boundingBox!!;val group=groups.lastOrNull();val base=group?.firstOrNull()?.boundingBox
   if(base!=null && kotlin.math.abs(box.centerY()-base.centerY())<=minOf(box.height(),base.height())*.5)group.add(line)else groups.add(mutableListOf(line))}
  return groups.joinToString("\n"){row->row.sortedBy{it.boundingBox!!.left}.joinToString("  "){line->line.elements.joinToString(" "){it.text}}}
 }
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
 if(d.existing!=null||d.ocr.isBlank()||
   (hospital==d.hospital&&type==d.type&&system==d.system))return next
 val original=ReportParser.parse(d.ocr)
 val previous=store.latestTemplate(d.hospital,d.type,d.system)
 val target=store.latestTemplate(hospital,type,system)
 fun same(r:DraftRow,unit:String,low:Double?,high:Double?)=
  r.unit.trim()==unit.trim()&&r.low.toDoubleOrNull()==low&&r.high.toDoubleOrNull()==high
 val rows=d.rows.map{row->
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
  val preferredDates=lines.filter{line->preferredDateLabels.any{line.contains(it)}&&!line.contains("审核")&&!line.contains("送检")&&!line.contains("打印")}.mapNotNull{dateRegex.find(it)}
  val sampledDates=lines.filter{line->fallbackClinicalLabels.any{line.contains(it)}}.mapNotNull{dateRegex.find(it)}
  // Patient identity fields and report timing are separate domains. Generic date/time labels
  // are accepted only when unambiguous and never allowed to override an explicit clinical date.
  val genericDates=lines.filter{line->(line.contains("日期")||line.contains("时间"))&&!line.contains("出生")&&!line.contains("生日")}.mapNotNull{dateRegex.find(it)}
  val fallbackDates=lines.filterNot{line->line.contains("出生")||line.contains("生日")}.flatMap{line->dateRegex.findAll(line).toList()}
  val dates=when{preferredDates.isNotEmpty()->preferredDates;sampledDates.isNotEmpty()->sampledDates;genericDates.size==1->genericDates;fallbackDates.size==1->fallbackDates;else->emptyList()}
  val date=dates.firstOrNull()?.let{m->"${m.groupValues[1]}-${m.groupValues[2].padStart(2,'0')}-${m.groupValues[3].padStart(2,'0')}"}.orEmpty()
  val explicit=when{
   raw.contains("血常规")||raw.contains("血细胞分析")->"血常规"
   raw.contains("肝功能")->"肝功能"
   raw.contains("肾功能")->"肾功能"
   raw.contains("肿瘤标志")||raw.contains("肿瘤标记")->"肿瘤标志物"
   else->""
  }
  val keys=items.map{it.metricKey}.toSet()
  val inferred=when{
   keys.intersect(setOf("WBC","NEUT#","HGB","PLT","RBC")).size>=2->"血常规"
   keys.intersect(setOf("ALT","AST","TBIL","ALB")).size>=2->"肝功能"
   keys.intersect(setOf("CREA","UREA","UA")).size>=2->"肾功能"
   else->"检查报告"
  }
  val type=explicit.ifBlank{inferred}
  val uncertain=buildSet{if(hospital.isBlank())add("hospital");if(date.isBlank())add("date");if(explicit.isBlank())add("type")}
  return ReportMetadataResult(hospital,type,date,uncertain)
 }
}
