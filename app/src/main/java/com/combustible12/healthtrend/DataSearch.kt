package com.combustible12.healthtrend

import java.text.Normalizer
import java.util.Locale

/** Read-only matching of stored text and measurements. Never reparses or rewrites data. */
internal object DataSearch {
 private fun normalize(s:String)=Normalizer.normalize(s,Normalizer.Form.NFKC).lowercase(Locale.ROOT).replace('/', '-').replace('～','~')
 fun matches(query:String,fields:Iterable<String>):Boolean {
  val terms=normalize(query).trim().split(Regex("\\s+")).filter{it.isNotEmpty()}
  if(terms.isEmpty())return true
  val values=fields.map(::normalize)
  return terms.all{term->values.any{term in it}}
 }
 private fun dates(time:Long)=listOf(dateText(time),dateText(time).take(10).replace('-', '/'))
 fun resultFields(x:LabResult,aliases:Map<String,String> = emptyMap(),preferred:String?=null):List<String> {
  val key=canonicalMetricKey(x.metricKey)
  val definition=UnifiedMetricStandard.definition(key)
  return listOf(x.rawName,key,labDisplayTitle(x.rawName,key),definition?.name.orEmpty(),preferred.orEmpty(),x.textValue,x.unitAtTest,x.referenceRaw,
   x.referenceLowAtTest?.toString().orEmpty(),x.referenceHighAtTest?.toString().orEmpty(),rangeText(x.referenceLowAtTest,x.referenceHighAtTest),x.rawLine,x.reportFlag,x.resultStatusLabel())+
   definition?.aliases.orEmpty()+ReportParser.builtInAliasNames(key)+aliases.filterValues{canonicalMetricKey(it)==key}.keys+dates(x.testedAtEpochMillis)
 }
 fun reportFields(r:LabReport):List<String> {
  val title=ReportNaming.title(r)
  return listOf(r.hospitalKey,r.reportType,title.category,title.name,r.systemKey)+dates(r.testedAtEpochMillis)
 }
 fun report(query:String,r:LabReport,aliases:Map<String,String> = emptyMap(),preferred:Map<String,String> = emptyMap())=
  matches(query,reportFields(r)+r.rawOcr+r.results.flatMap{resultFields(it,aliases,preferred[canonicalMetricKey(it.metricKey)])})
 fun row(query:String,r:LabReport,x:LabResult,aliases:Map<String,String> = emptyMap(),preferred:String?=null)=matches(query,reportFields(r)+resultFields(x,aliases,preferred))
 fun draftRow(query:String,row:DraftRow):Boolean {
  val definition=UnifiedMetricStandard.definition(row.key)
  return matches(query,listOf(row.name,row.key,row.text,row.unit,row.low,row.high,row.referenceRaw,row.raw,row.trendMeaning,row.reportFlag,definition?.name.orEmpty())+definition?.aliases.orEmpty())
 }
 fun draft(query:String,d:ReportDraft)=matches(query,listOf(d.hospital,d.type,d.reportName,d.date,d.system,d.ocr)+d.rows.flatMap{listOf(it.name,it.key,it.text,it.unit,it.low,it.high,it.referenceRaw,it.raw)})
 fun entry(query:String,e:HealthEntry)=matches(query,listOf(e.kind.title,e.title,e.note,e.hospital,e.category,e.frequency,e.duration,e.dose,e.route,e.severity.toString())+dates(e.occurredAtEpochMillis)+e.endAtEpochMillis?.let(::dates).orEmpty())
 fun course(query:String,r:CourseRecord)=matches(query,listOf(r.phase,r.title,r.symptomText,r.checkText,r.medicineText,r.noteText,r.pressureTime,r.systolic?.toString().orEmpty(),r.diastolic?.toString().orEmpty(),r.pulse?.toString().orEmpty(),"${r.systolic?:""}/${r.diastolic?:""}")+dates(r.date)+r.additionalPressures.flatMap{listOf(it.time,it.systolic?.toString().orEmpty(),it.diastolic?.toString().orEmpty(),it.pulse?.toString().orEmpty(),"${it.systolic?:""}/${it.diastolic?:""}")})
 fun weight(query:String,w:WeightRecord)=matches(query,listOf("体重","kg","公斤",w.kilograms.toString(),String.format(Locale.US,"%.1f",w.kilograms))+dates(w.measuredAtEpochMillis))
}
