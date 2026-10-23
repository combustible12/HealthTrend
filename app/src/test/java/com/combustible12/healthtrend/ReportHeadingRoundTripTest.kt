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
 @Test fun lymphocyteAliasesShareOneNameThroughSaveReopenAndExport(){
  val store=store()
  listOf("淋巴细胞%","淋巴细胞百分比").forEach{name->
   val source="合成医院\n血常规\n2001-01-02\nLYMPH% $name 23.400 % 10.00~40.00"
   val draft=pastedReportDraft(source,store=store)
   assertTrue(reportValidationProblems(draft).toString(),draft.valid())
   assertEquals("淋巴细胞百分比",draft.rows.single().name)
   val report=materializeReportDraft(draft,store);store.saveReport(report)
   val reopened=HealthStore(RuntimeEnvironment.getApplication()).reports().single{it.id==report.id}
   assertEquals("淋巴细胞百分比",reopened.results.single().rawName)
   assertEquals(source,reopened.rawOcr)
   assertTrue(reopened.results.single().rawLine.contains(name))
   val output=store.exportReportAsPastedText(reopened)
   assertTrue(output,output.contains("LYMPH% 淋巴细胞百分比 23.400 % 10.00~40.00"))
   val reimport=pastedReportDraft(output,store=store)
   assertEquals("LYMPH%",reimport.rows.single().key)
   assertEquals("淋巴细胞百分比",reimport.rows.single().name)
   assertEquals("23.400",reimport.rows.single().text)
   assertEquals("10.00~40.00",reimport.rows.single().referenceRaw)
  }
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
   Triple("梅毒螺旋体抗体（TPPA）","传染病筛查",""),
   Triple("糖类抗原CA724测定","肿瘤标志物",""))
  examples.forEach{(title,category,name)->
   val heading=ReportNaming.resolve("合成医院\n$title\n$day",category)
   assertEquals(title,ReportHeading(category,name),heading)
  }
 }
 @Test fun arbitraryNamesAndAnalytesCannotBecomeSecondLine(){
  for(name in listOf("糖类抗原CA724测定","TPPA","任意检查名称","FT3、FT4、TSH","甲胎蛋白 癌胚抗原"))assertEquals(name,"",ReportNaming.formalPanelName(name))
  for(name in listOf("甲功五项","输注前血型4项","乙肝二对半(定性)","乙肝两对半（定性）"))assertEquals(name,name,ReportNaming.formalPanelName(name))
  val store=store()
  val original=store.buildReportFromPool("合成医院","肿瘤标志物",parseDate(day)!!,emptyList(),ReportParser.parse("CA724 糖类抗原724 2.00 U/mL 0-10")).copy(reportName="糖类抗原CA724测定")
  assertEquals(listOf("肿瘤标志物"),ReportNaming.title(original).lines())
  val draft=ReportDraft.from(original).copy(reportName="任意单项名")
  assertEquals("",materializeReportDraft(draft,store).reportName)
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
 @Test fun sourceNamesSurvivePreferredNamesEditReopenAndExport(){
  val store=store()
  val cases=listOf(
   Triple("甲状腺功能","FT3","游离-T3"),Triple("甲状腺功能","FT4","游离-T4"),
   Triple("甲状腺功能","TSH","促甲状腺素（III代）"),
   Triple("肿瘤标志物","CA724","糖类抗原CA72-4"),
   Triple("生化","GGT","γ-谷氨酰转肽酶"),Triple("生化","A/G","白蛋白/球蛋白"),
   Triple("凝血功能","FDP","纤维蛋白（原）降解产物"),
   Triple("生化","AST/ALT","谷草/谷丙比值")
  )
  cases.forEach{(category,key,name)->
   val raw="合成医院\n$category\n2001-01-02\n$key\t$name\t2.340\t-\t1.0-3.0"
   val draft=pastedReportDraft(raw,store=store)
   assertEquals(raw,UnifiedMetricStandard.definition(key)!!.name,draft.rows.single().name)
   assertEquals(raw,key,draft.rows.single().key)
   assertTrue(reportValidationProblems(draft).toString(),draft.valid())
   val report=materializeReportDraft(draft,store);store.saveReport(report)
   val context=RuntimeEnvironment.getApplication()
   val reopened=HealthStore(context).reports().single{it.id==report.id}
   assertEquals(UnifiedMetricStandard.definition(key)!!.name,reopened.results.single().rawName)
   val edited=ReportDraft.from(reopened)
   assertEquals(UnifiedMetricStandard.definition(key)!!.name,edited.rows.single().name)
   store.saveReport(materializeReportDraft(edited,store))
   store.savePreferredMetricName(key,"合成显示名称")
   val exported=store.exportReportAsPastedText(reopened)
   assertTrue(exported,exported.contains(UnifiedMetricStandard.definition(key)!!.name))
   assertFalse(exported,exported.contains("合成显示名称"))
   store.deletePreferredMetricName(key)
   val imported=pastedReportDraft(exported,store=store)
   assertEquals(UnifiedMetricStandard.definition(key)!!.name,imported.rows.single().name)
   assertEquals(key,imported.rows.single().key)
   assertEquals("2.340",imported.rows.single().text)
   store.deleteReport(report.id)
  }
 }
 @Test fun correctingDatePreservesReportIdentityNamesAndOtherReports(){
  val store=store()
  val initial=pastedReportDraft("合成医院\n甲状腺功能\n2001-01-02\nFT3 游离-T3 4.20 pmol/L 3-6",store=store)
  val report=materializeReportDraft(initial,store);store.saveReport(report)
  val other=report.copy(id="untouched-report",results=report.results.map{it.copy(id="other-"+it.id,reportId="untouched-report")});store.saveReport(other)
  val edited=ReportDraft.from(report).copy(date="2001-01-03")
  store.saveReport(materializeReportDraft(edited,store))
  val context=RuntimeEnvironment.getApplication()
  val reopened=HealthStore(context).reports()
  val changed=reopened.single{it.id==report.id}
  assertEquals("2001-01-03",dateText(changed.testedAtEpochMillis).take(10))
  assertEquals(report.results.map{it.rawName},changed.results.map{it.rawName})
  assertEquals(report.results.map{it.textValue},changed.results.map{it.textValue})
  assertEquals(other,reopened.single{it.id==other.id})
  store.deleteReport(changed.id)
  assertEquals(listOf(other),HealthStore(context).reports())
 }
 @Test fun markedCatalogueNamesSurviveStorageAndDoNotRewriteHistory(){
  val store=store();val context=RuntimeEnvironment.getApplication()
  val legacy=materializeReportDraft(pastedReportDraft("合成医院\n血常规\n2001-01-01\nHGB 血红蛋白 100.00 g/L 90-120"),store)
  store.saveReport(legacy)
  val prefs=context.getSharedPreferences("healthtrend_store_v1",Context.MODE_PRIVATE)
  val before=prefs.getString("reports",null)
  HealthStore(context).reports();assertEquals(before,prefs.getString("reports",null))
  UnifiedMetricStandard.keys().forEach{key->
   val d=UnifiedMetricStandard.definition(key)!!
   val type=ReportCategoryPolicy.infer(listOf(key))?:"合成检验"
   val sourceUnit=if(key=="DDIMER")"ug/mL FEU" else d.unit.ifBlank{"-"}
   val raw="合成医院\n$type\n2001-01-02\n$key * ＊ ${d.name} 2.340 $sourceUnit 0-9"
   val draft=pastedReportDraft(raw,store=store)
   assertEquals(key,draft.rows.single().key);assertEquals(d.name,draft.rows.single().name)
   assertTrue("$key: ${reportValidationProblems(draft)}",draft.valid())
   assertEquals(before,prefs.getString("reports",null))
   val report=materializeReportDraft(draft,store);store.saveReport(report)
   val reopened=HealthStore(context).reports().single{it.id==report.id}
   assertEquals(d.name,reopened.results.single().rawName)
   assertEquals("2.340",reopened.results.single().textValue)
   val edit=ReportDraft.from(reopened).let{it.copy(rows=it.rows.map{row->row.copy(text="3.450")})}
   store.saveReport(materializeReportDraft(edit,store))
   val edited=HealthStore(context).reports().single{it.id==report.id}
   val imported=pastedReportDraft(store.exportReportAsPastedText(edited),store=store)
   assertEquals(d.name,imported.rows.single().name);assertEquals("3.450",imported.rows.single().text)
   val expectedUnit=if(key=="DDIMER")"μg/mL FEU" else d.unit
   assertEquals(expectedUnit,imported.rows.single().unit);assertEquals("0-9",imported.rows.single().rangeRaw())
   assertEquals(legacy,HealthStore(context).reports().single{it.id==legacy.id})
   store.deleteReport(report.id)
  }
  assertEquals(listOf(legacy),HealthStore(context).reports())
 }
}
