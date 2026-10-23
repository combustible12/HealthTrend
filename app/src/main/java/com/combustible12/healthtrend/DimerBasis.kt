package com.combustible12.healthtrend

import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

internal enum class DimerBasis(val label:String) { FEU("FEU"),DDU("DDU"),UNSPECIFIED("未标注") }

private fun normalizedDimerText(text:String)=Normalizer.normalize(text.trim(),Normalizer.Form.NFKC)
 .replace("µ","μ").replace(Regex("\\s+")," ")

internal fun dDimerBasisFromText(text:String):DimerBasis {
 val normalized=normalizedDimerText(text)
 // Laboratory units commonly attach the basis directly (ug/ml.FEU, μg/mLFEU,
 // or μg FEU/mL), so alphabetic unit characters cannot serve as boundaries.
 val hasFeu=Regex("(?i)FEU").containsMatchIn(normalized)
 val hasDdu=Regex("(?i)DDU").containsMatchIn(normalized)
 return when {hasFeu&&!hasDdu->DimerBasis.FEU;hasDdu&&!hasFeu->DimerBasis.DDU;else->DimerBasis.UNSPECIFIED}
}

private fun dimerBaseToken(unit:String):String = normalizedDimerText(unit)
 .replace(Regex("(?i)FEU|DDU"),"")
 .replace(Regex("[\\s.()（）]+"),"")
 .replace("μ","u").lowercase(Locale.ROOT)

/** Canonical typography only; scale-changing conversions are performed with the numeric value. */
internal fun canonicalDimerDisplayUnit(unit:String):String? {
 val basis=dDimerBasisFromText(unit)
 val base=when(dimerBaseToken(unit)){
  "ug/ml","mcg/ml"->"μg/mL"
  "mg/l"->"mg/L"
  "ng/ml"->"ng/mL"
  "ug/l","mcg/l"->"μg/L"
  "ng/l"->"ng/L"
  else->return null
 }
 return base+(if(basis==DimerBasis.UNSPECIFIED)"" else " ${basis.label}")
}

/** Convert a D-dimer result and its bounds to μg/mL without crossing FEU/DDU identities. */
internal fun normalizeDimerValue(value:Double,unit:String):Pair<Double,String>? {
 val factor=when(dimerBaseToken(unit)){
  "ug/ml","mcg/ml","mg/l"->1.0
  "ng/ml","ug/l","mcg/l"->0.001
  "ng/l"->0.000001
  else->return null
 }
 val basis=dDimerBasisFromText(unit)
 val canonical="μg/mL"+(if(basis==DimerBasis.UNSPECIFIED)"" else " ${basis.label}")
 return value*factor to canonical
}

internal fun dDimerBasis(result:LabResult):DimerBasis {
 if(canonicalMetricKey(result.metricKey)!="DDIMER")return DimerBasis.UNSPECIFIED
 val unitBasis=dDimerBasisFromText(result.unitAtTest)
 return if(unitBasis!=DimerBasis.UNSPECIFIED)unitBasis else dDimerBasisFromText(result.rawLine)
}

internal data class TrendCardIdentity(val metricKey:String,val dimerBasis:DimerBasis?=null,val unit:String="")

/** Stable display preference identity. Unit and D-dimer basis remain isolated. */
internal fun TrendCardIdentity.storageKey():String =
 listOf(metricKey,dimerBasis?.name.orEmpty(),unit).joinToString("\u001f")

/** Unlabelled D-dimer is a review state, never a third trend identity. */
internal fun trendCardIdentity(metricKey:String,result:LabResult):TrendCardIdentity? {
 val key=canonicalMetricKey(metricKey)
 if(key!="DDIMER")return TrendCardIdentity(key)
 val basis=dDimerBasis(result)
 return if(basis==DimerBasis.UNSPECIFIED)null else TrendCardIdentity(key,basis)
}

internal fun dDimerTrendTitle(base:String,basis:DimerBasis?):String =
 if(basis==null)base else "$base（${basis.label}）"

private fun LabReport.localDate(zone:ZoneId)=Instant.ofEpochMilli(testedAtEpochMillis).atZone(zone).toLocalDate()
private fun close(a:Double?,b:Double)=a!=null&&kotlin.math.abs(a-b)<1e-9

private fun confirmedHistoricalFeu(report:LabReport,result:LabResult,zone:ZoneId):Boolean {
 if(canonicalMetricKey(result.metricKey)!="DDIMER"||dDimerBasis(result)!=DimerBasis.UNSPECIFIED)return false
 if(!close(result.referenceLowAtTest,0.0)||!close(result.referenceHighAtTest,0.55))return false
 return when(report.hospitalKey.trim() to report.localDate(zone)){
  "福建省妇幼保健院" to LocalDate.of(2026,9,7)->close(result.value,0.22)
  "福建省肿瘤医院" to LocalDate.of(2026,10,23)->close(result.value,0.23)
  else->false
 }
}

internal data class DimerBasisMigration(val reports:List<LabReport>,val changedResults:Int)

/**
 * Persist explicit FEU/DDU evidence and the two records confirmed by the user.
 * No hospital-wide default is created; future unlabelled reports remain unspecified.
 */
internal fun migrateDimerBasisHistory(source:List<LabReport>,zone:ZoneId=ZoneId.systemDefault()):DimerBasisMigration {
 var changed=0
 val migrated=source.map{report->report.copy(results=report.results.map results@{result->
  if(canonicalMetricKey(result.metricKey)!="DDIMER")return@results result
  val explicit=dDimerBasisFromText(result.unitAtTest).takeUnless{it==DimerBasis.UNSPECIFIED}
   ?:dDimerBasisFromText(result.rawLine).takeUnless{it==DimerBasis.UNSPECIFIED}
  val basis=explicit?:DimerBasis.FEU.takeIf{confirmedHistoricalFeu(report,result,zone)}?:return@results result
  val sourceUnit=canonicalDimerDisplayUnit(result.unitAtTest).orEmpty().substringBefore(" FEU").substringBefore(" DDU")
   .ifBlank{"μg/mL"}
  val correctedUnit="$sourceUnit ${basis.label}"
  val normalized=UnitNormalizer.normalize("DDIMER",result.value,correctedUnit)
  val next=result.copy(unitAtTest=correctedUnit,normalizedValue=normalized.first,normalizedUnit=normalized.second)
  if(next!=result)changed++
  next
 })}
 return DimerBasisMigration(migrated,changed)
}
