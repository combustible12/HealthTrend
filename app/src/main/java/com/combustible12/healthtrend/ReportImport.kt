package com.combustible12.healthtrend

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.*
import kotlinx.coroutines.*

class ImportActions(val paste:(String)->Unit,val manual:()->Unit,val busy:Boolean,val message:String)
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
    val initial=pastedReportDraft(raw,store=store)
    pendingDraft=initial
    message=""
   }catch(e:Exception){if(e is CancellationException)throw e;pendingError="粘贴的数据无法导入：${e.message}"}finally{busy=false}
  }
 }
}
@Composable fun rememberReportImport(ready:(ReportDraft)->Unit,error:(String)->Unit,deliver:Boolean=true):ImportActions {
 val model:ReportImportViewModel=viewModel()
 LaunchedEffect(model.pendingDraft,deliver){if(deliver)model.pendingDraft?.let{ready(it);model.consumeDraft()}}
 LaunchedEffect(model.pendingError){model.pendingError?.let{error(it);model.consumeError()}}
 return ImportActions(paste={model.paste(it)},manual={if(!model.busy)ready(ReportDraft(rows=listOf(DraftRow())))},busy=model.busy,message=model.message)
}

private val pastedMetadataLabel=Regex("^(医院|医疗机构|检查日期|检验日期|报告日期|日期|检查类型|报告类型|类型)\\s*[:：]")
internal fun normalizePastedReportText(raw:String)=raw.lines().map{it.trim()}.filter{it.isNotBlank()&&!pastedMetadataLabel.containsMatchIn(it)}
 .joinToString("\n"){if('\t' in it)it else it.replace('｜','|').split('|').joinToString(" "){part->part.trim()}.replace(Regex("\\s+")," ")}

/**
 * Converts manually verified clipboard or plain-text report data into a report draft.
 * The report-data module does not perform OCR. Each new report owns its measured rows;
 * legacy hospital templates are not consulted for matching or review.
 */
internal fun pastedReportDraft(raw:String,template:HospitalLabTemplate?=null,store:HealthStore?=null):ReportDraft{
 val normalized=normalizePastedReportText(raw)
 // Clipboard rows commonly start with an explicit analyzer code (for example "TP 总蛋白 78.10 ...").
 // Bind that exact code as the parenthesized identity before the strict parser runs; this does not
 // guess by name and never consults or changes legacy templates.
 val sourceType=ReportMetadata.extract(raw,emptyList()).reportType
 val parsed=ReportParser.parse(ReportParser.bindExplicitLeadingIdentities(normalized),sourceType).let{rows->store?.reconcileParsedMetrics(rows,sourceType)?:rows}
 if(parsed.isEmpty())throw IllegalArgumentException("未识别到任何检验项目，请检查粘贴内容的项目代码、结果和换行")
 val meta=ReportMetadata.extract(raw,parsed)
 // Clipboard data already has an explicit metric code on every row. Exact canonical
 // code matching is sufficient here; do not run heuristics intended for noisy image text,
 // heuristic, because units such as 10^9/L contain digits before the printed range.
 // The standard pool determines identity. Report source name, unit and printed
 // reference interval remain attached to this test, independent of hospital.
 val resolved=parsed
 return ReportDraft(
  hospital=meta.hospital,type=meta.reportType,reportName=meta.reportName,date=meta.date,ocr=raw,
  rows=resolved.map{p->DraftRow.from(p).copy(uncertain=metricNeedsReview(p,null))},uncertain=meta.uncertain+if(ReportCategoryPolicy.problems(meta.reportType,meta.reportName,resolved.map{it.metricKey}).isEmpty())emptySet() else setOf("type")
 )
}

/** New reports are reviewed by their own parsed identity/value/range, never by a hospital template.
 * Keep the legacy parameter temporarily for source compatibility with older callers/tests.
 */
@Suppress("UNUSED_PARAMETER")
fun metricNeedsReview(p:ParsedLabResult,template:HospitalLabTemplate?):Boolean{
 if(p.metricKey.isBlank()||p.metricKey=="未识别"||p.metricKey.startsWith("待核对:")||p.displayName=="待核对"||p.textValue.isBlank())return true
 if(Regex("成人|新生儿|晨尿").containsMatchIn(p.referenceRaw))return true
 if(p.reportFlag.length>1 || sourceFlagConflict(p.reportFlag,p.value,p.referenceLow,p.referenceHigh))return true
 if('?' in p.referenceRaw && PartialReferenceRanges.interval(p.referenceRaw,p.value,p.reportFlag.singleOrNull())==null)return true
 // A legacy confirmed template must not override a malformed interval or suppress review.
 if(p.referenceLow!=null&&p.referenceHigh!=null&&p.referenceLow>p.referenceHigh)return true
 // An explicit analyzer code must not silently override a conflicting Chinese
 // measurement name. Unknown names still require manual review upstream.
 val namedIdentity=SpecimenNames.resolve(p.displayName,p.metricKey)?:ReportParser.key(p.displayName)
 val clippedWidthFamily=when(p.displayName.trim()){
  "红细胞分布宽度","红细胞体积分布宽度"->"RDW"
  "血小板分布宽度","血小板体积分布宽度"->"PDW"
  else->null
 }
 val clippedIdentity=clippedWidthFamily?.let{family->when(p.unit.trim().lowercase()){
  "%","％"->"$family-CV";"fl"->"$family-SD";else->null
 }}
 if(clippedWidthFamily!=null && clippedIdentity==null && p.metricKey in setOf("RDW","PDW"))return true
 if(namedIdentity!="未识别" &&
    reconciledMetricIdentity(p.metricKey,namedIdentity).startsWith("待核对:")&&
    canonicalMetricKey(clippedIdentity.orEmpty())!=canonicalMetricKey(p.metricKey))return true
 if(distributionWidthIdentityProblem(p.metricKey,p.displayName,p.unit)!=null)return true
 return false
}

/** Rebind unresolved pasted fields when the hospital, panel or laboratory system changes. */
fun retargetImportedDraft(
 d:ReportDraft,store:HealthStore,
 hospital:String=d.hospital,type:String=d.type,system:String=d.system
):ReportDraft{
 val next=d.copy(hospital=hospital,type=type,system=system)
 if(d.existing!=null||(hospital==d.hospital&&type==d.type&&system==d.system))return next
 // Reclassifying source metadata must never rebuild a template or change manually corrected rows.
 return next.copy(rows=d.rows.map {row->
  if(requiresExplicitMetricIdentityConfirmation(row.key,row.name))return@map row
  if(row.key.isBlank()||row.key=="未识别"||row.key.startsWith("待核对:")){
   val known=SpecimenNames.resolve(row.name,type)?:store.resolveMetricKey(row.name,row.unit)
   if(known=="未识别")row else row.copy(key=known,uncertain=false)
  }else row
 })
}

data class ReportMetadataResult(val hospital:String,val reportType:String,val date:String,val uncertain:Set<String>,val reportName:String="")
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
  // Explicit report metadata must survive export/import unchanged. Body analyte
  // keywords can infer a missing type, but cannot overwrite a supplied header.
  val labelledType=lines.firstNotNullOfOrNull{line->
   Regex("^(?:检查类型|报告类型|类型)\\s*[:：]\\s*(.+)$").matchEntire(line)?.groupValues?.get(1)?.trim()?.takeIf{it.isNotBlank()}
  }
  val knownHeaders=setOf("血常规","生化","肝功能","肾功能","肿瘤标志物","传染病筛查","甲状腺","甲状腺功能","凝血","凝血功能","尿液分析","尿常规","粪便常规","血型","电解质及肾功能")
  val declared=labelledType?:lines.firstOrNull{it in knownHeaders}
  val explicit=declared?:when{
   raw.contains("乙肝二对半")||raw.contains("乙肝两对半")||raw.contains("HBsAg")||raw.contains("抗-HCV")||raw.contains("Anti-HCV")->"传染病筛查"
   raw.contains("游离-T3")||raw.contains("游离-T4")||raw.contains("促甲状腺素")||raw.contains("FT3")&&raw.contains("FT4")->"甲状腺功能"
   raw.contains("粪便常规")||raw.contains("粪便分析")->"粪便常规"
   raw.contains("糖类抗原CA72")||raw.contains("CA72-4")||raw.contains("CA-153")||raw.contains("CA-199")||raw.contains("CA-125")->"肿瘤标志物"
   raw.contains("尿液分析")||raw.contains("尿干化学分析")||raw.contains("尿常规")||raw.contains("尿液检验")->"尿常规"
   raw.contains("凝血")||raw.contains("D-Dimer")&&raw.contains("APTT")->"凝血功能"
   raw.contains("SCC")||raw.contains("鳞状细胞癌相关抗原")->"肿瘤标志物"
   raw.contains("AFP")&&raw.contains("CA125")->"肿瘤标志物"
   raw.contains("常规生化全套")||raw.contains("生化全套")->"生化"
   raw.contains("电解质")||raw.contains("临床生化")&&raw.contains("尿素")->"电解质及肾功能"
   raw.contains("血常规")||raw.contains("血细胞分析")->"血常规"
   raw.contains("生化")->"生化"
   raw.contains("肝功能")->"肝功能"
   raw.contains("肾功能")->"肾功能"
   raw.contains("肿瘤标志")||raw.contains("肿瘤标记")->"肿瘤标志物"
   else->""
  }
  val keys=items.map{it.metricKey}.toSet()
  val inferred=when{
   keys.intersect(setOf("AFP","CEA","CA199","CA125","CA153","CA724","CYFRA21-1","NSE","HE4","SCC")).isNotEmpty()->"肿瘤标志物"
   keys.intersect(setOf("PT","INR","APTT","FIB","TT","D-Dimer")).size>=2->"凝血功能"
   keys.intersect(setOf("WBC","NEUT#","HGB","PLT","RBC")).size>=2->"血常规"
   // A mixed chemistry panel must stay one panel instead of being split into
   // liver/kidney templates merely because it contains ALT or CREA.
   keys.intersect(setOf("TP","ALB","GLOB","TBIL","ALT","AST","GGT","ALP","CHE","TBA","PA","UREA","CREA","UA")).size>=5->"生化"
   keys.intersect(setOf("ALT","AST","TBIL","ALB")).size>=2->"肝功能"
   keys.intersect(setOf("CREA","UREA","UA")).size>=2->"肾功能"
   else->"检查报告"
  }
  val type=explicit.ifBlank{if(inferred!="检查报告")inferred else ReportCategoryPolicy.infer(items.map{it.metricKey})?:inferred}
  val uncertain=buildSet{if(hospital.isBlank())add("hospital");if(date.isBlank())add("date");if(explicit.isBlank())add("type")}
  val heading=ReportNaming.resolve(raw,type)
  return ReportMetadataResult(hospital,heading.category,date,uncertain,heading.name)
 }
}
