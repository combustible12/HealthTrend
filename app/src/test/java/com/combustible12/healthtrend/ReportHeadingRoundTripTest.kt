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
class ReportHeadingRoundTripTest {
 private val day="2001-01-02"
 private fun store():HealthStore {
  val context=RuntimeEnvironment.getApplication()
  context.getSharedPreferences("healthtrend_store_v1",Context.MODE_PRIVATE).edit().clear().commit()
  return HealthStore(context)
 }
 @Test fun legacySpecificHeaderIsPreservedUnderItsCategory(){
  val draft=pastedReportDraft("合成医院\n乙肝二对半(定性)\n$day\nHBSAG 乙肝表面抗原 阴性(-)  阴性(-)")
  assertEquals("传染病筛查",draft.type)
  assertEquals("乙肝二对半(定性)",draft.reportName)
  assertEquals(1,draft.rows.size)
  val blood=pastedReportDraft("合成医院\n血细胞分析(成人)\n$day\nWBC 白细胞计数 5.20 10^9/L 3-10")
  assertEquals("血常规",blood.type);assertEquals("血细胞分析(成人)",blood.reportName)
 }
 @Test fun twoHeadingExportSurvivesStorageEditAndReimport(){
  val store=store()
  val raw="合成医院\n传染病筛查\n乙肝两对半（定性）\n$day\nHBSAG 乙肝表面抗原 阴性(-)  阴性(-)\nHBSAB 乙肝表面抗体 阳性(+)  阴性(-)"
  val draft=pastedReportDraft(raw,store=store)
  assertEquals(2,draft.rows.size);assertTrue(reportValidationProblems(draft).toString(),draft.valid())
  val report=materializeReportDraft(draft,store);store.saveReport(report)
  val reopened=HealthStore(RuntimeEnvironment.getApplication()).reports().single()
  assertEquals(report,reopened)
  val exported=store.exportReportAsPastedText(reopened)
  assertEquals(listOf("合成医院","传染病筛查","乙肝两对半（定性）",day),exported.lines().take(4))
  val next=pastedReportDraft(exported,store=store)
  assertEquals(draft.type,next.type);assertEquals(draft.reportName,next.reportName)
  assertEquals(draft.rows.map{listOf(it.key,it.text,it.unit,it.referenceRaw)},next.rows.map{listOf(it.key,it.text,it.unit,it.referenceRaw)})
  val edited=materializeReportDraft(ReportDraft.from(reopened).copy(reportName=""),store)
  store.saveReport(edited)
  assertEquals(listOf("传染病筛查"),ReportNaming.title(store.reports().single()).lines())
  assertEquals(listOf("合成医院","传染病筛查",day),store.exportReportAsPastedText(edited).lines().take(3))
 }
 @Test fun listsNeverBecomeSubtitlesOrInventedItemCounts(){
  val examples=listOf(
   Triple("游离三碘甲状腺原氨酸测定，游离甲状腺素测定，促甲状腺素测定","甲状腺功能",""),
   Triple("CA125、CA153、CA199、CEA","肿瘤标志物",""),
   Triple("甲功五项","甲状腺功能","甲功五项"),
   Triple("输注前血型4项","血型检查","输注前血型4项"),
   Triple("常规生化全套检查","生化","常规生化全套检查"),
   Triple("梅毒螺旋体抗体（TPPA）","传染病筛查","梅毒螺旋体抗体（TPPA）"),
   Triple("糖类抗原CA724测定","肿瘤标志物","糖类抗原CA724测定"))
  examples.forEach{(title,category,name)->
   val heading=ReportNaming.resolve("合成医院\n$title\n$day",category)
   assertEquals(title,ReportHeading(category,name),heading)
  }
 }
 @Test fun categoryOnlyAndOldStoredReportStayUnchanged(){
  val store=store()
  val old=store.buildReportFromPool("合成医院","生化",parseDate(day)!!,emptyList(),ReportParser.parse("ALT 谷丙转氨酶 12.00 U/L 7-40"))
  store.saveReport(old)
  val context=RuntimeEnvironment.getApplication()
  val prefs=context.getSharedPreferences("healthtrend_store_v1",Context.MODE_PRIVATE)
  val before=prefs.getString("reports",null)
  val report=HealthStore(context).reports().single()
  assertEquals(old,report);assertEquals(listOf("生化"),ReportNaming.title(report).lines())
  val text=store.exportReportAsPastedText(report)
  assertEquals(listOf("合成医院","生化",day),text.lines().take(3))
  assertEquals(before,prefs.getString("reports",null))
  val draft=pastedReportDraft(text)
  assertEquals("生化",draft.type);assertEquals("",draft.reportName);assertEquals("12.00",draft.rows.single().text)
 }
}
