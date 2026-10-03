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
 val parsed=ReportParser.parse(normalized)
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
 fun chinese(s:String)=s.filter{it.code in 0x4E00..0x9FFF}.replace(Regex("^(上|下|三|红|丨|I)+"),"")
 fun identityScore(fieldIndex:Int,itemIndex:Int):Int{
  val field=fields[fieldIndex];val item=items[itemIndex]
  val keyMatch=ReportParser.key(field.metricKey)==ReportParser.key(item.metricKey)
  val expected=chinese(field.displayName);val actual=chinese(item.displayName)
  val nameScore=when{
   expected.length<2||actual.length<2->0
   expected==actual->90
   expected.contains(actual)||actual.contains(expected)->70
   else->0
  }
  if(!keyMatch&&nameScore==0)return Int.MIN_VALUE
  return (if(keyMatch)120 else 0)+nameScore
 }
 fun score(fieldIndex:Int,itemIndex:Int)=identityScore(fieldIndex,itemIndex)+(20-kotlin.math.abs(fieldIndex-itemIndex).coerceAtMost(20))
 data class Candidate(val field:Int,val item:Int,val score:Int)
 // If several OCR rows have the same strongest identity evidence, position alone is not
 // enough to choose a medical result. Leave that template value empty for user review.
 val ambiguous=fields.indices.filter{fi->
  val evidence=items.indices.map{ii->identityScore(fi,ii)}.filter{it>0}
  val strongest=evidence.maxOrNull()?:return@filter false
  evidence.count{it==strongest}>1
 }.toSet()
 val candidates=fields.indices.filterNot{it in ambiguous}.flatMap{fi->items.indices.mapNotNull{ii->score(fi,ii).takeIf{it>0}?.let{Candidate(fi,ii,it)}}}
  .sortedWith(compareByDescending<Candidate>{it.score}.thenBy{it.field}.thenBy{it.item})
 val usedFields=mutableSetOf<Int>();val usedItems=mutableSetOf<Int>();val result=mutableMapOf<Int,ParsedLabResult>()
 candidates.forEach{candidate->if(candidate.field !in usedFields&&candidate.item !in usedItems){usedFields+=candidate.field;usedItems+=candidate.item;result[candidate.field]=items[candidate.item]}}
 return result
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
  val current=d.rows.associateBy{ReportParser.key(it.key.ifBlank.�Y��[Y_J_B��]\���^���J����]\��]��Y[˛X\ٚY[O���\��[�ԙ\ܝ\��\���^J�Y[�Y]�X��^JWOΑ�Y�����[YOY�Y[�\�^S�[YK�^OT�\ܝ\��\���^J�Y[�Y]�X��^JK[�]Y�Y[�[�]���Y�Y[��Y�\�[��S��˝���[��
K�ܑ[\J
KY�Y�Y[��Y�\�[��RY�˝���[��
K�ܑ[\J
JB�JB�B��[ܚY�[�[T�\ܝ\��\��\��J��܊B��[�]�[�\�\�ܙK�]\�[\]J���][�\K��\�[JB��[��[YJ���Y����[�]���[���Α�X�O�Y���X�O�OB���[�]��[J
OO][�]��[J
I�����˝��X�Sܓ�[

OO[�ɉ���Y����X�Sܓ�[

OOZY��Y�\��]O[�[
^�]\���^���J����][\]Q�]�[��\�[�ܚY�[�[\��]
K�X\�k�w��_��b��Z