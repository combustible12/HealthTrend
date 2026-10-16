package com.combustible12.healthtrend

import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()
/** Stable metric identity shared by import, templates, reports and trends.
 * Only explicit, unambiguous aliases are mapped. Historical records remain untouched. */
/** Shared canonical metric identity, preserving compound-code distinctions. */
fun canonicalMetricKey(raw:String):String=MetricDictionary.canonical(raw)

object MetricDictionary {
 private val aliases=mapOf(
  "CR" to "CREA","CRE" to "CREA","CREAT" to "CREA","CREATININE" to "CREA",
  "UREA/CR" to "UREA/CREA","PI" to "PHOS","P" to "PHOS",
  "PHOSPHATE" to "PHOS","CK-MB" to "CKMB","CK_MB" to "CKMB"
 )
 fun canonical(raw:String):String {
  val code=raw.trim().uppercase().replace('／','/').replace('％','%')
  val compound=if(code in setOf("AST:ALT","ALT:AST","A:G","CKMB:CK"))code.replace(':','/') else code
  return aliases[compound]?:compound
 }
}

/** Protect distinct CV (relative dispersion) and SD (absolute width) identities.
 * A conflicting source unit or explicit CV/SD label requires manual correction;
 * never reinterpret fL as % or silently switch a confirmed metric key.
 * Null means this identity has no specialized CV/SD guard.
 */
fun distributionWidthIdentityProblem(metricKey:String,name:String,unit:String):String? {
 val key=canonicalMetricKey(metricKey)
 val family=when(key){
  "PDW","PDW-CV","PDW-SD"->"PDW"
  "RDW","RDW-CV","RDW-SD"->"RDW"
  else->return null
 }
 val rawUnit=unit.trim().lowercase()
 val isPercent=rawUnit in setOf("%","％")
 val isVolume=rawUnit=="fl"
 val label=name.uppercase().replace("－","-").replace(" ","")
 val explicitCv=label.contains("CV")||label.contains("变异系数")
 val explicitSd=label.contains("SD")||label.contains("标准差")
 if(explicitCv&&explicitSd)return "指标名称同时包含CV和SD，请核对"
 if(key=="$family-CV"&&(explicitSd||isVolume))return "CV指标与SD名称或fL单位冲突，请核对身份"
 if(key=="$family-SD"&&(explicitCv||isPercent))return "SD指标与CV名称或%单位冲突，请核对身份"
 if(key==family&&(explicitCv||explicitSd||isPercent||isVolume))
  return "未区分CV/SD的旧指标身份需要核对，不得直接按单位自动归类"
 return null
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
data class LabReport(val id:String,val hospitalKey:String,val reportType:String,val testedAtEpochMillis:Long,val templateVersion:Int?,val sourceImages:List<ReportImage>,val results:List<LabResult>,val rawOcr:String="",val systemKey:String=""):java.io.Serializable
data class ReportImage(val uri:String,val pageIndex:Int,val importedAtEpochMillis:Long):java.io.Serializable
data class LabResult(
 val id:String,val reportId:String,val hospitalKey:String,val reportType:String,val templateVersion:Int?,val metricKey:String,val rawName:String,
 val value:Double?,val unitAtTest:String,val referenceLowAtTest:Double?,val referenceHighAtTest:Double?,val testedAtEpochMillis:Long,
 val editedByUser:Boolean=false,val textValue:String=value?.toString().orEmpty(),val comparator:String="",val rawLine:String="",
 val normalizedValue:Double?=value,val normalizedUnit:String=unitAtTest
):java.io.Serializable{
 fun status():ResultStatus {
  val n=value ?: return ResultStatus.UNKNOWN
  if(comparator.isNotEmpty()) return ResultStatus.UNKNOWN
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
fun trendSeriesKey(result:LabResult)=TrendSeriesIdentity(result.hospitalKey,result.reportType,canonicalMetricKey(result.metricKey),result.normalizedUnit)
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
 val medicineText:String="",
 val medicineImages:List<String> = emptyList(),
 val noteText:String="",
 val noteImages:List<String> = emptyList(),
 val createdAt:Long=System.currentTimeMillis(),
 val updatedAt:Long=System.currentTimeMillis()
):java.io.Serializable
data class PatientProfile(val name:String="",val birthDate:String="",val sex:String="",val note:String="",val bloodType:String=""):java.io.Serializable
data class SymptomEntry(val name:String,val severity:Int,val occurredAtEpochMillis:Long,val note:String="")
data class MedicalRecord(val title:String,val hospital:String,val occurredAtEpochMillis:Long,val category:String,val sourceImageUri:String?)


/** Conservative normalization: only conversions with an unambiguous laboratory-unit relationship are applied. */
object UnitNormalizer {
 fun normalize(metricKey:String,value:Double?,unit:String):Pair<Double?,String>{
  if(value==null)return null to normalize(metricKey,1.0,unit).second
  val u=unit.replace("μ","u").replace("µ","u").replace(" ","").lowercase()
  return when {
   metricKey in setOf("WBC","NEUT#","LYMPH#","MONO#","EOS#","BASO#","NRBC#","PLT","P-LCC") && u in setOf("10^9/l","×10^9/l","x10^9/l") -> value to "10^9/L"
   metricKey in setOf("NEUT%","LYMPH%","MONO%","EOS%","BASO%","NRBC%","HCT","RDW","PCT","PDW","P-LCR") && u=="%" -> value to "%"
   metricKey=="RBC" && u in setOf("10^12/l","×10^12/l","x10^12/l") -> value to "10^12/L"
   metricKey in setOf("HGB","ALB") && u=="g/l" -> value to "g/L"
   metricKey=="HGB" && u=="g/dl" -> value*10.0 to "g/L"
   metricKey=="ALB" && u=="g/dl" -> value*10.0 to "g/L"
   metricKey in setOf("TBIL","CREA","UA") && u in setOf("umol/l","μmol/l") -> value to "μmol/L"
   metricKey=="CREA" && u=="mg/dl" -> value*88.4 to "μmol/L"
   metricKey=="TBIL" && u=="mg/dl" -> value*17.104 to "μmol/L"
   metricKey=="UA" && u=="mg/dl" -> value*59.48 to "μmol/L"
   metricKey=="GLU" && u=="mmol/l" -> value to "mmol/L"
   metricKey=="GLU" && u=="mg/dl" -> value/18.0182 to "mmol/L"
   metricKey=="UREA" && u=="mmol/l" -> value to "mmol/L"
   // UREA mg/dL may be confused with BUN mg/dL: keep the source unit until identity is confirmed.
   else -> value to unit
  }
 }
}

fun symptomReportText(start:String,end:String,selected:List<HealthEntry>):String =
 buildString{append("症状报告 $start 至 $end\n记录 ${selected.size} 次\n");selected.groupBy{it.title}.forEach{(name,rows)->append("$name：${rows.size} 次，平均程度 ${"%.1f".format(rows.map{it.severity}.average())}/10，最高 ${rows.maxOf{it.severity}}/10\n");rows.sortedBy{it.occurredAtEpochMillis}.forEach{e->append("${dateText(e.occurredAtEpochMillis)} 程度${e.severity} ${e.frequency} ${e.duration} ${e.note}\n")}}}

data class WeightRecord(val id:String=newId(),val measuredAtEpochMillis:Long=System.currentTimeMillis(),val kilograms:Double):java.io.Serializable
