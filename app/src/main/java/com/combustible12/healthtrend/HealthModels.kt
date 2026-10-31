package com.combustible12.healthtrend

import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()
/** Stable metric identity shared by import, templates, reports and trends.
 * Only explicit, unambiguous aliases are mapped. Historical records remain untouched. */
/** Shared canonical metric identity, preserving compound-code distinctions. */
fun canonicalMetricKey(raw:String):String=MetricDictionary.canonical(raw)

object MetricDictionary {
 private val aliases=mapOf(
  "CA-125" to "CA125","CA-153" to "CA153","CA-199" to "CA199","CA72-4" to "CA724",
  "CA12-5" to "CA125","CA15-3" to "CA153","CA19-9" to "CA199","D-DIMER" to "DDIMER",
  "ANTI-HBS" to "HBSAB","ANTI-HBE" to "HBEAB","ANTI-HBC" to "HBCAB",
  "ANTI-HCV" to "HCVAB","HCV-AB" to "HCVAB","HIVAG/AB" to "HIVAGAB","HIV-AG/AB" to "HIVAGAB",
  "ANTI-HIV" to "HIVAB","HIV-AB" to "HIVAB","ANTI-TP" to "TPAB","TP-AB" to "TPAB",
  "CR" to "CREA","CRE" to "CREA","CREAT" to "CREA","CREATININE" to "CREA",
  "UREA/CR" to "UREA/CREA","PI" to "PHOS","P" to "PHOS",
  "PHOSPHATE" to "PHOS","CK-MB" to "CKMB","CK_MB" to "CKMB","CK-MB/CK" to "CKMB/CK"
 )
 internal fun sourceCodeAliases():Set<String> = aliases.keys
 fun canonical(raw:String):String {
  val code=raw.trim().uppercase().replace('／','/').replace('％','%')
  val compound=if(code in setOf("AST:ALT","ALT:AST","A:G","CKMB:CK"))code.replace(':','/') else code
  return aliases[compound]?:compound
 }
}

/** These states carry unresolved source evidence; only an explicit row confirmation may clear them. */
internal fun requiresExplicitMetricIdentityConfirmation(key:String,name:String):Boolean =
 name.trim()=="待核对"||key.startsWith("待核对:代码名称冲突:")||key.startsWith("待核对:未知复合代码:")||key.startsWith("待核对:未解析行:")

/** New/changed rows cannot pair a known code with another known measurement name. */
internal fun metricIdentityNameProblem(key:String,name:String,unit:String):String? {
 val canonical=canonicalMetricKey(key)
 if(canonical !in ReportParser.builtInMetricKeys())return null
 val clipped=sourceDistributionWidthIdentity(name,unit)?.takeIf{canonical.endsWith("-CV")||canonical.endsWith("-SD")}
 val named=clipped?:SpecimenNames.resolve(name,canonical)?:ReportParser.key(name)
 return if(reconciledMetricIdentity(canonical,named).startsWith("待核对:"))"项目代码与中文名称属于不同指标，请核对" else symbolicMetricNameProblem(canonical,name)
}

/** One symbol policy for pasted rows, manual review and both storage save paths. */
internal fun symbolicMetricNameProblem(key:String,name:String,type:String=key):String? {
 val canonical=canonicalMetricKey(key)
 if(canonical !in ReportParser.builtInMetricKeys())return null
 val source=ReportParser.sourceMetricName(name)
 if(source.none{!it.isLetterOrDigit()&&!it.isWhitespace()})return null
 // Known different names remain evidence for the code/name conflict validator.
 if(UnifiedMetricStandard.resolveName(source)!=null)return null
 val d=UnifiedMetricStandard.definition(canonical)
 val names=d?.let{it.aliases+it.name}.orEmpty()+ReportParser.builtInAliasNames(canonical)+
  SpecimenNames.names(type).filterValues{it==canonical}.keys
 val label=source.replace(Regex("[（(]([^（）()]+)[）)]$")){m->
  if(ReportParser.key(m.groupValues[1])==canonical)"" else m.value
 }
 return if(names.any{known->listOf(source,label).any{UnifiedMetricStandard.normalizedName(known)==UnifiedMetricStandard.normalizedName(it)}})null
  else "项目名称含未确认的符号组合，请核对完整名称"
}

/** Protect distinct CV (relative dispersion) and SD (absolute width) identities.
 * A conflicting source unit or explicit CV/SD label requires manual correction;
 * never reinterpret fL as % or silently switch a confirmed metric key.
 * Null means this identity has no specialized CV/SD guard.
 */
fun distributionWidthIdentityProblem(metricKey:String,name:String,unit:String):String? {
 val resolution=resolveDistributionWidthIdentity(metricKey,name,unit)
 if(resolution.family==null)return null
 val key=canonicalMetricKey(metricKey)
 if(resolution.problem!=null)return resolution.problem
 return if(key !in confirmedDistributionWidthKeys || key!=resolution.targetKey)
  "分布宽度身份应归入 ${resolution.targetKey}，请核对" else null
}

/** Used for recognition of new rows; saved history is handled by the idempotent migration. */
internal fun sourceDistributionWidthIdentity(name:String,unit:String):String? {
 return resolveDistributionWidthIdentity("",name,unit).takeIf{it.problem==null}?.targetKey
}

/** Hide appended/prefixed metric abbreviations, preserving English inside actual names. */
fun labDisplayTitle(name:String,key:String):String{
 val raw=name.trim()
 if(raw.isBlank())return ""
 // Legacy templates may still have a code prefix. Remove the complete key only;
 // never let AST partially strip AST/ALT.
 val prefix=Regex("^\\s*"+Regex.escape(key)+"(?=\\s|$)",RegexOption.IGNORE_CASE)
 val wrapped=Regex("[（(]\\s*"+Regex.escape(key)+"\\s*[）)]",RegexOption.IGNORE_CASE)
 val suffix=Regex("(?i)\\s+"+Regex.escape(key)+"\\s*$")
 val legacyPrefix=when{
  key.endsWith("#")->Regex("^\\s*#"+Regex.escape(key.dropLast(1))+"(?=\\s|$)",RegexOption.IGNORE_CASE)
  key.endsWith("%")->Regex("^\\s*%"+Regex.escape(key.dropLast(1))+"(?=\\s|$)",RegexOption.IGNORE_CASE)
  else->null
 }
 val cleaned=(legacyPrefix?.let{raw.replace(it,"")}?:raw).replace(wrapped,"").replace(prefix,"").replace(suffix,"").trim()
 val first=cleaned.substringBefore(' ').trim()
 val withoutAlias=if(first!=cleaned && canonicalMetricKey(first)==canonicalMetricKey(key))cleaned.substringAfter(' ').trim() else cleaned
 return withoutAlias.replace(Regex("\\s+")," ").trim()
}
data class HospitalLabTemplate(val hospitalKey:String,val reportType:String,val version:Int,val confirmed:Boolean,val fields:List<LabFieldTemplate>,val systemKey:String=""):java.io.Serializable
data class LabFieldTemplate(val metricKey:String,val displayName:String,val unit:String,val referenceLow:Double?,val referenceHigh:Double?,val trendMeaning:String=metricPurpose(metricKey).orEmpty()):java.io.Serializable
data class LabReport(val id:String,val hospitalKey:String,val reportType:String,val testedAtEpochMillis:Long,val templateVersion:Int?,val sourceImages:List<ReportImage>,val results:List<LabResult>,val rawOcr:String="",val systemKey:String="",val reportName:String?=null):java.io.Serializable {
 companion object {private const val serialVersionUID:Long=-5053754111289098495L}
}
data class ReportImage(val uri:String,val pageIndex:Int,val importedAtEpochMillis:Long):java.io.Serializable
data class LabResult(
 val id:String,val reportId:String,val hospitalKey:String,val reportType:String,val templateVersion:Int?,val metricKey:String,val rawName:String,
 val value:Double?,val unitAtTest:String,val referenceLowAtTest:Double?,val referenceHighAtTest:Double?,val testedAtEpochMillis:Long,
 val editedByUser:Boolean=false,val textValue:String=value?.toString().orEmpty(),val comparator:String="",val rawLine:String="",
 val normalizedValue:Double?=value,val normalizedUnit:String=unitAtTest,
 val referenceRaw:String="",val reportFlag:String=""
):java.io.Serializable{
 companion object {private const val serialVersionUID:Long=-3909522735447564327L}
 private fun readResolve():Any=copy(referenceRaw=referenceRaw.orEmpty(),reportFlag=reportFlag.orEmpty())
 fun status():ResultStatus {
  val n=value ?: return when(referenceAgreement()){ReferenceAgreement.MATCH->ResultStatus.NORMAL;ReferenceAgreement.MISMATCH->ResultStatus.HIGH;ReferenceAgreement.UNKNOWN->ResultStatus.UNKNOWN}
  if(comparator.isNotEmpty()) return comparisonResultStatus(this)
  if(reportFlag.length>1 || sourceFlagConflict(reportFlag,n,referenceLowAtTest,referenceHighAtTest))return ResultStatus.UNKNOWN
  if('?' in referenceRaw) {
   val interval=PartialReferenceRanges.interval(referenceRaw,n,reportFlag.singleOrNull()) ?: return ResultStatus.UNKNOWN
   return when {
    n < interval.lowerLimit -> ResultStatus.LOW
    n > interval.upperLimit -> ResultStatus.HIGH
    n >= interval.lower.maximum && n <= interval.upper.minimum -> ResultStatus.NORMAL
    else -> ResultStatus.UNKNOWN
   }
  }
  return when {
   referenceLowAtTest!=null && n<referenceLowAtTest -> ResultStatus.LOW
   referenceHighAtTest!=null && n>referenceHighAtTest -> ResultStatus.HIGH
   referenceLowAtTest==null && referenceHighAtTest==null -> ResultStatus.UNKNOWN
   else -> ResultStatus.NORMAL
  }
 }
 /** Normalize chart bounds using the same conversion as its values; keep stored history unchanged. */
 fun trendReferenceRange():Pair<Double?,Double?> =
  UnitNormalizer.normalize(metricKey,referenceLowAtTest,unitAtTest).first to
  UnitNormalizer.normalize(metricKey,referenceHighAtTest,unitAtTest).first
 /** User-approved plotting estimate only; never writes the report or changes status(). */
 fun trendPlotReferenceRange():Pair<Double?,Double?> {
  val partial=if('?' in referenceRaw&&reportFlag.length<=1)PartialReferenceRanges.interval(referenceRaw,value,reportFlag.singleOrNull()) else null
  if('?' in referenceRaw&&partial==null)return null to null
  return UnitNormalizer.normalize(metricKey,partial?.lowerLimit?:referenceLowAtTest,unitAtTest).first to
   UnitNormalizer.normalize(metricKey,partial?.upperLimit?:referenceHighAtTest,unitAtTest).first
 }
 /** Display-only equivalence: do not rewrite original range text or result precision. */
 fun trendReferenceDisplayText():String {
  val raw=referenceRaw.trim()
  val simpleRange=Regex("""^[+-]?(?:\d+(?:\.\d*)?|\.\d+)\s*[-–—~～−－]\s*[+-]?(?:\d+(?:\.\d*)?|\.\d+)$""")
  val bounds=trendReferenceRange()
  fun number(n:Double)=java.math.BigDecimal.valueOf(if(n==0.0)0.0 else n).stripTrailingZeros().toPlainString()
  // Do not turn a legacy PCT bound with no preserved source range into a new
  // "≥0.108"/"≤0.25" label. A real one-sided source such as "0.108--"
  // remains verbatim, while its known edge can still be drawn by the chart.
  if(canonicalMetricKey(metricKey)=="PCT"&&raw.isBlank()&&
   ((bounds.first==null)!=(bounds.second==null)))return ""
  if((raw.isEmpty()||simpleRange.matches(raw))&&bounds.first!=null&&bounds.second!=null)
   return "${number(bounds.first!!)}–${number(bounds.second!!)}"
  return raw.ifBlank { when {
   bounds.first!=null -> "≥${number(bounds.first!!)}"
   bounds.second!=null -> "≤${number(bounds.second!!)}"
   else -> "未录入"
  } }
 }
 fun trendRangeEstimateText():String {
  if('?' !in referenceRaw||reportFlag.length>1)return ""
  val partial=PartialReferenceRanges.interval(referenceRaw,value,reportFlag.singleOrNull())?:return ""
  val bounds=trendPlotReferenceRange()
  fun number(v:Double)=java.math.BigDecimal.valueOf(v).stripTrailingZeros().toPlainString()
  return listOfNotNull(
   bounds.first?.takeUnless{partial.lower.exact}?.let{"估计下限≈${number(it)}"},
   bounds.second?.takeUnless{partial.upper.exact}?.let{"估计上限≈${number(it)}"}
  ).joinToString("，")
 }
 fun withEditedValue(newValue:Double,displayText:String?=null)=copy(value=newValue,textValue=displayText?.trim()?.takeIf{it.toDoubleOrNull()==newValue}?:formatEditedValue(newValue,textValue),comparator="",editedByUser=true,normalizedValue=UnitNormalizer.normalize(metricKey,newValue,unitAtTest).first,normalizedUnit=UnitNormalizer.normalize(metricKey,newValue,unitAtTest).second)
 private fun formatEditedValue(v:Double,previous:String):String{
  val decimals=previous.substringAfter('.', "").takeWhile{it.isDigit()}.length
  return if(decimals>0)"%.${decimals}f".format(java.util.Locale.US,v) else {
   val whole=v.toLong();if(v==whole.toDouble())whole.toString() else v.toString()
  }
 }
}
enum class ResultStatus { LOW,NORMAL,HIGH,UNKNOWN }
/** Exact stored identity and compatible measurement scope; no name/code rewriting. */
data class TrendSeriesIdentity(val hospital:String,val type:String,val metricKey:String,val unit:String)
/** Compare the current canonical unit identity, never a stale serialized spelling. */
fun trendUnitIdentity(result:LabResult):String =
 UnitNormalizer.normalize(result.metricKey,result.value,result.unitAtTest).second.let{unit->
  // Plateletcrit is displayed and compared as percent. Historical rows that
  // omitted the printed unit belong to the same PCT series.
  if(canonicalMetricKey(result.metricKey)=="PCT"&&(unit.isBlank()||unit=="%"))"%" else unit
 }
fun trendUnitsCompatible(a:LabResult,b:LabResult):Boolean =
 canonicalMetricKey(a.metricKey)==canonicalMetricKey(b.metricKey)&&trendUnitIdentity(a)==trendUnitIdentity(b)
fun trendSeriesKey(result:LabResult)=TrendSeriesIdentity(result.hospitalKey,result.reportType,canonicalMetricKey(result.metricKey),trendUnitIdentity(result))
enum class EntryKind(val title:String) { SYMPTOM("症状记录"),MEDICAL("病历资料"),MEDICATION("用药记录") }
data class HealthEntry(
 val id:String=newId(),val kind:EntryKind,val title:String,val occurredAtEpochMillis:Long,
 val note:String="",val hospital:String="",val category:String="",val severity:Int=0,val frequency:String="",val duration:String="",
 val dose:String="",val route:String="",val endAtEpochMillis:Long?=null,val images:List<String> = emptyList()
):java.io.Serializable
data class BloodPressureReading(val time:String="",val systolic:Int?=null,val diastolic:Int?=null,val pulse:Int?=null):java.io.Serializable

data class CourseRecord(
 val id:String=newId(),
 val date:Long=System.currentTimeMillis(),
 val phase:String="观察",
 val title:String="",
 val symptomText:String="",
 val systolic:Int?=null,
 val diastolic:Int?=null,
 val pressureTime:String="",
 val pulse:Int?=null,
 val additionalPressures:List<BloodPressureReading> = emptyList(),
 val symptomImages:List<String> = emptyList(),
 val checkText:String="",
 val checkImages:List<String> = emptyList(),
 val treatmentText:String="",
 val treatmentImages:List<String> = emptyList(),
 val reminderText:String="",
 val reminderDate:String="",
 val reminderCompletedAt:Long?=null,
 val medicineText:String="",
 val medicineImages:List<String> = emptyList(),
 val dietText:String="",
 val dietImages:List<String> = emptyList(),
 val noteText:String="",
 val noteImages:List<String> = emptyList(),
 val createdAt:Long=System.currentTimeMillis(),
 val updatedAt:Long=System.currentTimeMillis(),
 val titleAuto:Boolean=true,
 val growthFactorInjection:Boolean=false
):java.io.Serializable
data class TreatmentReminder(
 val id:String=newId(),
 val radiationNumber:Int,
 val text:String,
 val completedAt:Long?=null,
 val createdAt:Long=System.currentTimeMillis(),
 val triggerDate:String="",
 val triggerTime:String="09:00"
):java.io.Serializable
data class PatientProfile(val name:String="",val birthDate:String="",val sex:String="",val note:String="",val bloodType:String="",val heightCm:String=""):java.io.Serializable
data class SymptomEntry(val name:String,val severity:Int,val occurredAtEpochMillis:Long,val note:String="")
data class MedicalRecord(val title:String,val hospital:String,val occurredAtEpochMillis:Long,val category:String,val sourceImageUri:String?)


/** Conservative normalization: only conversions with an unambiguous laboratory-unit relationship are applied. */
object UnitNormalizer {
 /**
  * Canonical identity for typographic variants of the same laboratory unit.
  * The source unit remains in [LabResult.unitAtTest]; this value is only used
  * for comparison, conversion, trend plotting and display.
  */
 private fun equivalentUnit(metricKey:String,unit:String):String{
  val source=java.text.Normalizer.normalize(unit.trim(),java.text.Normalizer.Form.NFKC)
   .replace("µ","μ").replace(Regex("\\s+"),"")
   .replace("10⁹","10^9").replace("10¹²","10^12")
  if(source.isBlank())return ""
  if(canonicalMetricKey(metricKey)=="DDIMER")canonicalDimerDisplayUnit(source)?.let{return it}
  val token=source.replace("μ","u").lowercase(java.util.Locale.ROOT).replaceFirst(Regex("^[×x](?=10\\^)"),"")
  val preferred=UnifiedMetricStandard.definition(canonicalMetricKey(metricKey))?.unit.orEmpty()
  fun identity(value:String)=java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFKC)
   .replace("μ","u").replace("µ","u").replace(Regex("\\s+"),"")
   .replace("10⁹","10^9").replace("10¹²","10^12")
   .lowercase(java.util.Locale.ROOT).replaceFirst(Regex("^[×x](?=10\\^)"),"")
  if(preferred.isNotBlank()&&token==identity(preferred))return preferred
  return when(token){
   "10^9/l","10e9/l"->"10^9/L"
   "10^12/l","10e12/l"->"10^12/L"
   "g/l"->"g/L";"g/dl"->"g/dL";"mg/l"->"mg/L";"mg/dl"->"mg/dL"
   "ng/l"->"ng/L";"ng/ml"->"ng/mL";"pg/ml"->"pg/mL"
   "ug/l","mcg/l"->"μg/L";"ug/ml","mcg/ml"->"μg/mL"
   "umol/l"->"μmol/L";"mmol/l"->"mmol/L";"nmol/l"->"nmol/L";"pmol/l"->"pmol/L"
   "u/l"->"U/L";"u/ml"->"U/mL";"miu/l"->"mIU/L";"iu/l"->"IU/L"
   "fl"->"fL";"mm/h"->"mm/h";"%"->"%";"s","sec","sec.","secs","second","seconds","秒"->"s";"pg"->"pg"
   else->source
  }
 }
 fun normalize(metricKey:String,value:Double?,unit:String):Pair<Double?,String>{
  if(value==null)return null to normalize(metricKey,1.0,unit).second
  val key=canonicalMetricKey(metricKey)
  val canonical=equivalentUnit(key,unit)
  if(key=="DDIMER")normalizeDimerValue(value,canonical)?.let{return it}
  val u=canonical.replace("μ","u").replace("µ","u").replace(" ","").lowercase()
  return when {
   key in setOf("WBC","NEUT#","LYMPH#","MONO#","EOS#","BASO#","NRBC#","PLT","P-LCC") && u in setOf("10^9/l","×10^9/l","x10^9/l","10e9/l") -> value to "10^9/L"
   key in setOf("NEUT%","LYMPH%","MONO%","EOS%","BASO%","NRBC%","HCT","RDW-CV","PDW-CV","PCT","P-LCR") && u=="%" -> value to "%"
   key in setOf("RDW-SD","PDW-SD") && u=="fl" -> value to "fL"
   key=="RBC" && u in setOf("10^12/l","×10^12/l","x10^12/l","10e12/l") -> value to "10^12/L"
   key in setOf("HGB","ALB") && u=="g/l" -> value to "g/L"
   key=="HGB" && u=="g/dl" -> value*10.0 to "g/L"
   key=="ALB" && u=="g/dl" -> value*10.0 to "g/L"
   key in setOf("TBIL","CREA","UA") && u=="umol/l" -> value to "μmol/L"
   key=="CREA" && u=="mg/dl" -> value*88.4 to "μmol/L"
   key=="TBIL" && u=="mg/dl" -> value*17.104 to "μmol/L"
   key=="UA" && u=="mg/dl" -> value*59.48 to "μmol/L"
   key=="GLU" && u=="mmol/l" -> value to "mmol/L"
   key=="GLU" && u=="mg/dl" -> value/18.0182 to "mmol/L"
   key=="UREA" && u=="mmol/l" -> value to "mmol/L"
   key=="FDP" && u in setOf("mg/l","ug/ml") -> value to "μg/mL"
   // UREA mg/dL may be confused with BUN mg/dL: keep the source unit until identity is confirmed.
   else -> value to canonical
  }
 }
}

fun symptomReportText(start:String,end:String,selected:List<HealthEntry>):String =
 buildString{append("症状报告 $start 至 $end\n记录 ${selected.size} 次\n");selected.groupBy{it.title}.forEach{(name,rows)->append("$name：${rows.size} 次，平均程度 ${"%.1f".format(rows.map{it.severity}.average())}/10，最高 ${rows.maxOf{it.severity}}/10\n");rows.sortedBy{it.occurredAtEpochMillis}.forEach{e->append("${dateText(e.occurredAtEpochMillis)} 程度${e.severity} ${e.frequency} ${e.duration} ${e.note}\n")}}}

data class WeightRecord(
 val id:String=newId(),
 val measuredAtEpochMillis:Long=System.currentTimeMillis(),
 val kilograms:Double,
 val courseRecordIds:List<String> = emptyList(),
 val heightCmAtMeasurement:Double? = null
):java.io.Serializable
