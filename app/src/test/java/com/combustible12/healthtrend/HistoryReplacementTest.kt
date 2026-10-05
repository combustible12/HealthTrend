package com.combustible12.healthtrend

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

/** Entirely synthetic data. Real patient values belong only in private import files. */
class HistoryReplacementTest {
 private val zone=ZoneId.of("Asia/Shanghai")
 private val keys=(1..27).map{"synthetic-$it"}
 private val template=HospitalLabTemplate("测试医院","测试检查",9,true,keys.map{LabFieldTemplate(it,it,"test-unit",0.0,1000.0)})
 private fun timestamp(day:Int)=LocalDate.of(2025,1,day).atStartOfDay(zone).toInstant().toEpochMilli()
 private fun report(day:Int)=LabReport("report-$day",template.hospitalKey,template.reportType,timestamp(day),2,
  listOf(ReportImage("file:///private/image-$day",0,1L)),
  keys.mapIndexed{i,key->LabResult("$day-$i","report-$day",template.hospitalKey,template.reportType,2,key,key,-1.0,"old-unit",null,null,timestamp(day))},"preserved OCR")
 private fun batch(n:Int=6)=HistoryReplacementBatch(template.hospitalKey,template.reportType,"",keys,
  (1..n).map{day->HistoryReplacementRow(LocalDate.of(2025,1,day),keys.indices.map{i->"${day*100+i}.00"})})
 @Test fun replacesEveryCellOnceAndPreservesUnrelatedRecords(){
  val all=(1..6).map(::report);val other=report(20).copy(hospitalKey="另一个医院")
  val fixed=replaceReportHistory(all+other,template,batch(),zone)
  assertEquals(other,fixed.last())
  assertEquals(162,fixed.dropLast(1).sumOf{it.results.size})
  keys.forEach{key->assertEquals(6,fixed.dropLast(1).flatMap{it.results}.count{it.metricKey==key})}
  all.indices.forEach{d->
   val before=all[d];val after=fixed[d]
   assertEquals(before.id,after.id);assertEquals(before.testedAtEpochMillis,after.testedAtEpochMillis)
   assertEquals(before.sourceImages,after.sourceImages);assertEquals(before.rawOcr,after.rawOcr)
   assertEquals(keys,after.results.map{it.metricKey})
   after.results.forEachIndexed{i,r->assertEquals(batch().reports[d].values[i],r.textValue);assertEquals("test-unit",r.unitAtTest);assertEquals(9,r.templateVersion)}
  }
  assertEquals(162,fixed.dropLast(1).flatMap{it.results}.map{it.id}.distinct().size)
 }
 @Test fun seventhReportIsIndependentAndIsNeverCappedAtSix(){
  val fixed=replaceReportHistory((1..6).map(::report),template,batch(),zone)
  val seventh=report(7);val seven=fixed+seventh
  keys.forEach{key->assertEquals(7,seven.flatMap{it.results}.count{it.metricKey==key})}
  assertEquals(seventh,replaceReportHistory(seven,template,batch(),zone).last())
  val allSeven=replaceReportHistory((1..7).map(::report),template,batch(7),zone)
  assertEquals(189,allSeven.sumOf{it.results.size})
 }
 @Test fun corruptedOldItemsAreReplacedAsAWhole(){
  val old=(1..6).map{report(it).copy(results=listOf(report(it).results.first(),report(it).results.first().copy(id="duplicate-${it}")))}
  assertEquals(162,replaceReportHistory(old,template,batch(),zone).sumOf{it.results.size})
 }
 private fun rejects(block:()->Unit){try{block();fail("Expected rejection")}catch(e:IllegalArgumentException){}}
 @Test fun rejectsMissingDuplicateAndWrongScopeWithoutChangingInput(){
  val all=(1..6).map(::report);val original=all.toList()
  rejects{replaceReportHistory(all.drop(1),template,batch(),zone)}
  rejects{replaceReportHistory(all+report(1).copy(id="duplicate-date"),template,batch(),zone)}
  rejects{replaceReportHistory(all,template,batch().copy(metricKeys=keys.reversed()),zone)}
  rejects{replaceReportHistory(all,template,batch().copy(hospital="其他医院"),zone)}
  rejects{replaceReportHistory(all,template,batch().copy(system="其他仪器"),zone)}
  rejects{replaceReportHistory(all,template,batch().copy(reports=batch().reports+batch().reports.first()),zone)}
  rejects{replaceReportHistory(all,template,batch().copy(reports=batch().reports.map{it.copy(values=it.values.drop(1))}),zone)}
  rejects{replaceReportHistory(all,template,batch().copy(reports=batch().reports.map{it.copy(values=it.values.map{"NaN"})}),zone)}
  assertEquals(original,all)
 }
 @Test fun precisionAndDimensionIdentitiesRemainSeparate(){
  val ids=listOf("NEUT#","NEUT%","NRBC#","NRBC%","P-LCR","P-LCC")
  val t=template.copy(fields=ids.map{LabFieldTemplate(it,it,"",null,null)})
  val b=HistoryReplacementBatch(t.hospitalKey,t.reportType,"",ids,listOf(HistoryReplacementRow(LocalDate.of(2025,1,1),listOf("2.30","32.30","0.000","0.00","18.0","49"))))
  val result=replaceReportHistory(listOf(report(1)),t,b,zone).single().results
  assertEquals(ids,result.map{it.metricKey});assertEquals(b.reports.single().values,result.map{it.textValue})
 }
 @Test fun trendIdentityDoesNotGuessNamesOrMergeIncompatibleScopes(){
  val x=report(1).results.first()
  assertEquals(trendSeriesKey(x),trendSeriesKey(x.copy(rawName="改名",templateVersion=99,referenceHighAtTest=123.0)))
  assertNotEquals(trendSeriesKey(x),trendSeriesKey(x.copy(metricKey=" "+x.metricKey)))
  assertNotEquals(trendSeriesKey(x),trendSeriesKey(x.copy(hospitalKey="另一医院")))
  assertNotEquals(trendSeriesKey(x),trendSeriesKey(x.copy(reportType="另一检查")))
  assertNotEquals(trendSeriesKey(x),trendSeriesKey(x.copy(normalizedUnit="另一单位")))
 }
}
