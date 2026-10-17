package com.combustible12.healthtrend

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Runs production SharedPreferences/JSON persistence with synthetic data in an Android JVM. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
class CatalogueStorageRoundTripTest {
 @Test fun repeatedReconciliationCannotClearUnknownOrConflictingIdentity(){
  val context=RuntimeEnvironment.getApplication()
  context.getSharedPreferences("healthtrend_store_v1",Context.MODE_PRIVATE).edit().clear().commit()
  val store=HealthStore(context)
  listOf("AST / UNKNOWN 2.340 - 0-9","CK - UNKNOWN 2.340 - 0-9","CA - 999 2.340 - 0-9","AST ZZZ 2.340 - 0-9","AST/ALT 谷草转氨酶 2.340 - 0-9","AST / UNKNOWN 谷草转氨酶 2.340 - 0-9","CK - UNKNOWN 肌酸激酶 2.340 - 0-9").forEach{line->
   val draft=pastedReportDraft("合成医院\n生化\n2001-01-02\n$line",store=store)
   assertFalse(line,draft.valid());assertTrue(line,draft.rows.single().key.startsWith("待核对:"))
   val repeated=store.reconcileParsedMetrics(draft.parsed(),"生化")
   assertEquals(line,draft.rows.single().key,repeated.single().metricKey)
  }
  val unnamed=ReportDraft(hospital="合成医院",type="生化",date="2001-01-02",rows=listOf(DraftRow(key="AST",name="待核对",text="2.340")))
  assertFalse(unnamed.valid())
 }
 @Test fun allCatalogueMetricsSurviveImportStorageEditAndExport(){
  val context=RuntimeEnvironment.getApplication()
  context.getSharedPreferences("healthtrend_store_v1",Context.MODE_PRIVATE).edit().clear().commit()
  val store=HealthStore(context);val failures=mutableListOf<String>()
  UnifiedMetricStandard.keys().forEach{key->
   try{
    val d=UnifiedMetricStandard.definition(key)!!
    val category=ReportCategoryPolicy.infer(listOf(key))?:"合成检验"
    val source="合成医院\n$category\n2001-01-02\n$key ${d.name} 2.340 ${d.unit.ifBlank{"-"}} 0-9"
    val imported=pastedReportDraft(source,store=store)
    assertEquals(key,imported.rows.single().key)
    assertTrue(reportValidationProblems(imported).toString(),imported.valid())
    val report=materializeReportDraft(imported,store);store.saveReport(report)
    val reopened=HealthStore(context)
    val saved=reopened.reports().single{it.id==report.id}
    val edited=materializeReportDraft(ReportDraft.from(saved).let{it.copy(rows=it.rows.map{r->r.copy(text="3.450")})},reopened)
    reopened.saveReport(edited)
    val latest=HealthStore(context).reports().single{it.id==report.id}
    val roundtrip=pastedReportDraft(reopened.exportReportAsPastedText(latest),store=reopened)
    assertTrue(reportValidationProblems(roundtrip).toString(),roundtrip.valid())
    assertEquals(listOf(key,"3.450",d.unit,"0-9"),roundtrip.rows.single().let{listOf(it.key,it.text,it.unit,it.rangeRaw())})
    assertEquals("2.340",report.results.single().textValue)
   }catch(e:AssertionError){failures+="$key: ${e.message}"}catch(e:Exception){failures+="$key: ${e.message}"}
  }
  println("CATALOGUE_STORAGE keys=${UnifiedMetricStandard.keys().size} failures=${failures.size}")
  failures.forEach{println("STORAGE_FAIL $it")}
  assertTrue(failures.joinToString("\n"),failures.isEmpty())
 }
 @Test fun compoundOnlyCodesImportSaveReopenAndExportWithChineseNames(){
  val context=RuntimeEnvironment.getApplication()
  context.getSharedPreferences("healthtrend_store_v1",Context.MODE_PRIVATE).edit().clear().commit()
  val store=HealthStore(context)
  val raw="合成医院\n生化\n2001-01-02\nAST 24.00 U/L 0-40\nALT 12.00 U/L 0-40\nAST : ALT 2.000\nALT／AST 0.500\nA / G 1.500\nCKMB:CK 0.050\nUREA / CREA 0.060"
  val draft=pastedReportDraft(raw,store=store)
  assertTrue(reportValidationProblems(draft).toString(),draft.valid())
  assertEquals(7,draft.rows.size)
  assertEquals(listOf("AST","ALT","AST/ALT","ALT/AST","A/G","CKMB/CK","UREA/CREA"),draft.rows.map{it.key})
  assertTrue(draft.rows.none{it.name=="待核对"})
  store.saveReport(materializeReportDraft(draft,store))
  val reopened=HealthStore(context)
  val saved=reopened.reports().single()
  val exported=reopened.exportReportAsPastedText(saved)
  assertTrue(exported,exported.contains("AST/ALT 谷草/谷丙 2.000"))
  val imported=pastedReportDraft(exported,store=reopened)
  assertTrue(reportValidationProblems(imported).toString(),imported.valid())
  assertEquals(draft.rows.map{listOf(it.key,it.name,it.text,it.unit,it.rangeRaw())},imported.rows.map{listOf(it.key,it.name,it.text,it.unit,it.rangeRaw())})
 }
 @Test fun importSaveReopenEditExportAndTrendRetainNewCatalogueSemantics(){
  val context=RuntimeEnvironment.getApplication()
  val prefs=context.getSharedPreferences("healthtrend_store_v1",Context.MODE_PRIVATE)
  prefs.edit().clear().commit()
  val course="[{\"synthetic\":\"untouched\"}]"
  prefs.edit().putString("course_records",course).commit()
  val lines=listOf(
   "WBC 白细胞计数 6.420 N 10E9/L 3-10",
   "RBC 红细胞计数 4.120 N 10E12/L 3-5",
   "FDP 纤维蛋白(原)降解产物 1.70 mg/L 0-5",
   "HCVAB 丙肝抗体 0.08 S/CO 0-1",
   "HIVAGAB 人免疫缺陷病毒抗原/抗体联合检测 0.03 S/CO 0-1",
   "TPPA 梅毒螺旋体抗体 阴性 - 阴性",
   "A/G 白球比例 1.750 - 1.0-2.5",
   "PDW 血小板体积分布宽度 18.00 Y % 10-16",
   "RDW 红细胞体积分布宽度 12.80 N % &lt;15")
  val store=HealthStore(context)
  val legacy=store.buildReportFromPool("合成医院","血常规",1000000000000L,emptyList(),ReportParser.parse("PDW 血小板分布宽度 13.00 % 10-17"))
   .let{r->r.copy(results=r.results.map{it.copy(metricKey="PDW")})}
  store.saveReport(legacy)
  val actualPaste=pastedReportDraft("合成医院\n合成检验\n2026-10-01\n"+lines.joinToString("\n"),store=store)
  assertFalse(actualPaste.rows.map{it.key}.toString(),actualPaste.rows.any{it.key.startsWith("待核对:")})
  assertFalse(actualPaste.rows.toString(),actualPaste.rows.any{it.uncertain})
  val parsed=actualPaste.parsed()
  assertEquals(lines.size,parsed.size)
  val draft=ReportDraft(hospital="合成医院",type="合成检验",date="2026-10-01",rows=parsed.map{DraftRow.from(it)})
  assertTrue(reportValidationProblems(draft).toString(),draft.valid())
  val report=materializeReportDraft(draft,store)
  store.saveReport(report)
  var reopened=HealthStore(context)
  assertEquals(legacy,reopened.reports().single{it.id==legacy.id})
  var saved=reopened.reports().single{it.id==report.id}
  fun snapshot(r:LabReport)=r.results.map{listOf(it.metricKey,it.textValue,it.unitAtTest,it.referenceRaw,it.reportFlag)}
  assertEquals(snapshot(report),snapshot(saved))
  val edited=materializeReportDraft(ReportDraft.from(saved).copy(ocr="合成备注"),reopened)
  reopened.saveReport(edited)
  reopened=HealthStore(context);saved=reopened.reports().single{it.id==report.id}
  assertEquals(snapshot(report),snapshot(saved))
  val exported=ReportParser.parse(reopened.exportReportAsPastedText(saved))
  assertEquals(saved.results.map{it.metricKey},exported.map{it.metricKey})
  assertEquals(saved.results.map{it.textValue},exported.map{it.textValue})
  assertEquals(saved.results.map{it.unitAtTest},exported.map{it.unit})
  assertEquals(saved.results.map{it.referenceRaw},exported.map{it.referenceRaw})
  assertEquals(saved.results.map{it.reportFlag},exported.map{it.reportFlag})
  assertEquals("10^9/L",reopened.trend("WBC").single().second.normalizedUnit)
  assertEquals("10^12/L",reopened.trend("RBC").single().second.normalizedUnit)
  assertEquals("阴性",reopened.trend("TPPA").single().second.textValue)
  assertNull(reopened.trend("TPPA").single().second.value)
  assertTrue(reopened.trend("HCVAB").single().second.value!=null)
  assertEquals(course,prefs.getString("course_records",null))
  assertEquals(legacy,reopened.reports().single{it.id==legacy.id})
 }
}
