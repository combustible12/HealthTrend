package com.combustible12.healthtrend

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
class DataSearchTest {
 private val at=parseDate("2001-01-02")!!
 private fun report():LabReport {
  val store=HealthStore(RuntimeEnvironment.getApplication())
  return store.buildReportFromPool("合成甲医院","血常规",at,emptyList(),ReportParser.parse("HGB 血红蛋白 123.00 g/L 110-150")).copy(reportName="血细胞分析（成人）")
 }
 @Test fun reportSearchIncludesMetadataAliasesNumbersUnitsAndRanges(){
  val report=report()
  for(query in listOf("合成甲医院","血常规","血细胞分析","2001/01/02","hgb","血色素","123.00","g/L","110-150","血常规 123.00"))
   assertTrue(query,DataSearch.report(query,report))
  assertTrue(DataSearch.report("自定义叫法",report,mapOf("自定义叫法" to "HGB")))
  assertFalse(DataSearch.report("98765",report))
  assertFalse(DataSearch.report("HGB 98765",report))
  assertTrue(DataSearch.report("  ",report))
 }
 @Test fun detailMatchesOnlyCorrespondingRowsAndPreservesOriginals(){
  val report=report();val row=report.results.single()
  val other=row.copy(metricKey="WBC",rawName="白细胞",value=5.2,normalizedValue=5.2,textValue="5.20",unitAtTest="10^9/L",referenceLowAtTest=3.0,referenceHighAtTest=10.0,referenceRaw="3-10",rawLine="")
  assertTrue(DataSearch.row("123.00",report,row))
  assertFalse(DataSearch.row("123.00",report,other))
  assertTrue(DataSearch.row("合成甲医院 5.20",report,other))
  val qualitative=row.copy(metricKey="HBSAB",rawName="乙肝表面抗体",value=null,textValue="阳性(+)",unitAtTest="",referenceLowAtTest=null,referenceHighAtTest=null,referenceRaw="阴性(-)",rawLine="")
  assertTrue(DataSearch.row("阳性",report,qualitative))
  assertTrue(DataSearch.row("阴性",report,qualitative))
  val converted=row.copy(value=12.3,textValue="12.30",unitAtTest="g/dL",referenceLowAtTest=11.0,referenceHighAtTest=15.0,referenceRaw="11-15",rawLine="")
  assertTrue(DataSearch.row("123.0",report,converted))
  assertTrue(DataSearch.row("g/dL",report,converted))
  assertEquals("123.00",row.textValue)
 }
 @Test fun courseAndEntriesIncludeNumericAndDateFields(){
  val course=CourseRecord(date=at,phase="恢复期",title="合成记录",systolic=128,diastolic=79,pulse=73,additionalPressures=listOf(BloodPressureReading("21:30",132,81,75)),medicineText="合成药")
  for(q in listOf("2001/01/02","恢复期","128/79","73","132/81","75","21:30","合成药"))assertTrue(q,DataSearch.course(q,course))
  val entry=HealthEntry(kind=EntryKind.MEDICATION,title="合成药品",occurredAtEpochMillis=at,dose="2片",route="口服",note="饭后")
  for(q in listOf("合成药品","2001/01/02","2片","口服","饭后"))assertTrue(q,DataSearch.entry(q,entry))
  assertTrue(DataSearch.weight("61.5",WeightRecord(measuredAtEpochMillis=at,kilograms=61.5)))
 }
 @Test fun draftSearchNeverRemovesHiddenRowsAndSearchNeverWritesStorage(){
  val draft=ReportDraft(hospital="合成医院",reportName="明确小类",rows=listOf(DraftRow(name="血红蛋白",key="HGB",text="123.00",unit="g/L"),DraftRow(name="未核对项目",key="待核对:未知",text="?")))
  assertTrue(DataSearch.draft("明确小类",draft))
  assertTrue(DataSearch.draftRow("123.00",draft.rows.first()))
  assertFalse(DataSearch.draftRow("123.00",draft.rows.last()))
  assertEquals(2,draft.rows.size)
  val context=RuntimeEnvironment.getApplication();val store=HealthStore(context)
  val prefs=context.getSharedPreferences("healthtrend_store_v1",Context.MODE_PRIVATE)
  prefs.edit().clear().commit();val report=report();store.saveReport(report)
  val before=prefs.getString("reports",null)
  DataSearch.report("123.00",store.reports().single())
  assertEquals(before,prefs.getString("reports",null))
 }
}
