package com.combustible12.healthtrend

import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Display-only identity for values calculated by HealthTrend, kept separate from reported eGFR. */
internal const val DERIVED_EGFR_KEY = "EGFR-CKDEPI2021"
internal const val DERIVED_EGFR_UNIT = "mL/min/1.73m²"

internal enum class EgfrSex { FEMALE, MALE }

internal fun egfrSex(value:String):EgfrSex?=when(value.trim().lowercase(Locale.ROOT)){
 "女","女性","female","f"->EgfrSex.FEMALE
 "男","男性","male","m"->EgfrSex.MALE
 else->null
}

internal fun profileBirthDate(value:String):LocalDate? =
 runCatching{LocalDate.parse(value.trim().replace('/','-'))}.getOrNull()

/** 2021 CKD-EPI creatinine equation for adults; creatinine input is in μmol/L. */
internal fun ckdepi2021Egfr(creatinineUmolL:Double,age:Int,sex:EgfrSex):Double?{
 if(!creatinineUmolL.isFinite()||creatinineUmolL<=0.0||age<18)return null
 val scr=creatinineUmolL/88.4
 val kappa=if(sex==EgfrSex.FEMALE)0.7 else 0.9
 val alpha=if(sex==EgfrSex.FEMALE)-0.241 else -0.302
 val femaleFactor=if(sex==EgfrSex.FEMALE)1.012 else 1.0
 return 142.0*min(scr/kappa,1.0).pow(alpha)*max(scr/kappa,1.0).pow(-1.200)*
  0.9938.pow(age.toDouble())*femaleFactor
}

internal fun derivedEgfrRows(
 reports:List<LabReport>,profile:PatientProfile,zone:ZoneId=ZoneId.systemDefault()
):List<Pair<LabReport,LabResult>>{
 val birth=profileBirthDate(profile.birthDate)?:return emptyList()
 val sex=egfrSex(profile.sex)?:return emptyList()
 return reports.distinctBy{it.id}.flatMap{report->
  val testDate=Instant.ofEpochMilli(report.testedAtEpochMillis).atZone(zone).toLocalDate()
  val age=Period.between(birth,testDate).years
  if(testDate.isBefore(birth)||age<18)emptyList() else report.results.distinctBy{it.id}.mapNotNull{source->
   if(canonicalMetricKey(source.metricKey)!="CREA"||source.comparator.isNotBlank())return@mapNotNull null
   val normalized=UnitNormalizer.normalize("CREA",source.value,source.unitAtTest)
   val creatinine=normalized.first?.takeIf{normalized.second=="μmol/L"}?:return@mapNotNull null
   val value=ckdepi2021Egfr(creatinine,age,sex)?:return@mapNotNull null
   report to LabResult(
    id=source.id,reportId=report.id,hospitalKey=report.hospitalKey,reportType=report.reportType,
    templateVersion=report.templateVersion,metricKey=DERIVED_EGFR_KEY,rawName="eGFR（CKD-EPI 2021）",
    value=value,unitAtTest=DERIVED_EGFR_UNIT,referenceLowAtTest=90.0,referenceHighAtTest=null,
    testedAtEpochMillis=report.testedAtEpochMillis,textValue=String.format(Locale.US,"%.1f",value),
    rawLine="由肌酐、检测日期对应年龄和性别估算（CKD-EPI 2021）",
    normalizedValue=value,normalizedUnit=DERIVED_EGFR_UNIT,referenceRaw="≥90"
   )
  }
 }
}

internal fun originalTrendPoint(pair:Pair<LabReport,LabResult>):Pair<LabReport,LabResult> =
 if(pair.second.metricKey==DERIVED_EGFR_KEY)
  pair.first.results.firstOrNull{it.id==pair.second.id}?.let{pair.first to it}?:pair
 else pair
