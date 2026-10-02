package com.combustible12.healthtrend

import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()
data class HospitalLabTemplate(val hospitalKey:String,val reportType:String,val version:Int,val confirmed:Boolean,val fields:List<LabFieldTemplate>,val systemKey:String=""):java.io.Serializable
data class LabFieldTemplate(val metricKey:String,val displayName:String,val unit:String,val referenceLow:Double?,val referenceHigh:Double?):java.io.Serializable
data class LabReport(val id:String,val hospitalKey:String,val reportType:String,val testedAtEpochMillis:Long,val templateVersion:Int?,val sourceImages:List<ReportImage>,val results:List<LabResult>,val rawOcr:String="",val systemKey:String=""):java.io.Serializable
data class ReportImage(val uri:String,val pageIndex:Int,val importedAtEpochMillis:Long):java.io.Serializable
data class LabResult(
 val id:String,val reportId:String,val hospitalKey:String,val reportType:String,val templateVersion:Int?,val metricKey:String,val rawName:String,
 val value:Double?,val unitAtTest:String,val referenceLowAtTest:Double?,val referenceHighAtTest:Double?,val testedAtEpochMillis:Long,
 val editedByUser:Boolean=false,val textValue:String=value?.toString().orEmpty(),val comparator:String="",val rawLine:String="",\n val normalizedValue:Double?=value,val normalizedUnit:String=unitAtTest
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
 fun withEditedValue(newValue:Double)=copy(value=newValue,textValue=newValue.toString(),comparator="",editedByUser=true,normalizedValue=UnitNormalizer.normalize(metricKey,newValue,unitAtTest).first,normalizedUnit=UnitNormalizer.normalize(metricKey,newValue,unitAtTest).second)
}
enum class ResultStatus { LOW,NORMAL,HIGH,UNKNOWN }
enum class EntryKind(val title:String) { SYMPTOM("症状记录"),MEDICAL("病历资料"),MEDICATION("用药记录") }
data class HealthEntry(
 val id:String=newId(),val kind:EntryKind,val title:String,val occurredAtEpochMillis:Long,
 val note:String="",val hospital:String="",val category:String="",val severity:Int=0,val frequency:String="",val duration:String="",
 val dose:String="",val route:String="",val endAtEpochMillis:Long?=null,val images:List<String> = emptyList()
):java.io.Serializable
data class SymptomEntry(val name:String,val severity:Int,val occurredAtEpochMillis:Long,val note:String="")
data class MedicalRecord(val title:String,val hospital:String,val occurredAtEpochMillis:Long,val category:String,val sourceImageUri:String?)


/** Conservative normalization: only conversions with an unambiguous laboratory-unit relationship are applied. */
object UnitNormalizer {
 fun normalize(metricKey:String,value:Double?,unit:String):Pair<Double?,String>{
  if(value==null)return null to unit
  val u=unit.replace("μ","u").replace("µ","u").replace(" ","").lowercase()
  return when {
   metricKey in setOf("WBC","NEUT#","PLT","RBC","LYMPH#") && u in setOf("10^9/l","×10^9/l","x10^9/l") -> value to "×10^9/L"
   metricKey in setOf("HGB","ALB") && u=="g/l" -> value to "g/L"
   metricKey=="ALB" && u=="g/dl" -> value*10.0 to "g/L"
   metricKey in setOf("TBIL","CREA","UA") && u in setOf("umol/l","μmol/l") -> value to "μmol/L"
   metricKey=="CREA" && u=="mg/dl" -> value*88.4 to "μmol/L"
   metricKey=="TBIL" && u=="mg/dl" -> value*17.104 to "μmol/L"
   metricKey=="UA" && u=="mg/dl" -> value*59.48 to "μmol/L"
   metricKey=="UREA" && u=="mmol/l" -> value to "mmol/L"
   metricKey=="UREA" && u=="mg/dl" -> value/6.006 to "mmol/L"
   else -> value to unit
  }
 }
}
