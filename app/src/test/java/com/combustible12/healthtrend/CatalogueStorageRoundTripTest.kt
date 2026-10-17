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
