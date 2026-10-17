package com.combustible12.healthtrend

import android.content.Context
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
class ReportCategoryPolicyTest {
 private fun store():HealthStore {
  val c=RuntimeEnvironment.getApplication()
  c.getSharedPreferences("healthtrend_store_v1",Context.MODE_PRIVATE).edit().clear().commit()
  return HealthStore(c)
 }
 private fun source(category:String="免疫",panel:String="乙肝二对半(定性)")=
  "合成医院\n$category\n$panel\n2001-01-02\nHBSAG 乙肝表面抗原 阴性(-)  阴性(-)\nHBSAB 乙肝表面抗体 阴性(-)  阴性(-)"
 private fun rejected(block:()->Unit){try{block();fail("classification must be reviewed")}catch(e:IllegalArgumentException){assertTrue(e.message.orEmpty().contains("核对"))}}
 @Test fun wrongCategoryRemainsVisibleAndCannotSaveUntilUserCorrects(){
  val store=store();val d=pastedReportDraft(source(),store=store)
  assertEquals("免疫",d.type);assertEquals("乙肝二对半(定性)",d.reportName)
  assertEquals(2,d.rows.size);assertTrue("type" in d.uncertain)
  assertFalse(d.valid());assertTrue(reportValidationProblems(d).any{it.contains("传染病筛查")})
  rejected{materializeReportDraft(d,store)};assertTrue(store.reports().isEmpty())
  val corrected=d.copy(type="传染病筛查")
  assertTrue(reportValidationProblems(corrected).toString(),corrected.valid())
  val report=materializeReportDraft(corrected,store);store.saveReport(report)
  val reopened=HealthStore(RuntimeEnvironment.getApplication()).reports().single()
  assertEquals(report,reopened)
  val roundTrip=pastedReportDraft(store.exportReportAsPastedText(reopened),store=store)
  assertEquals(corrected.type,roundTrip.type);assertEquals(corrected.reportName,roundTrip.reportName)
  assertEquals(corrected.rows.map{listOf(it.key,it.text,it.unit,it.referenceRaw)},roundTrip.rows.map{listOf(it.key,it.text,it.unit,it.referenceRaw)})
 }
 @Test fun forbiddenProfessionOrAssayLabelsAreNotAcceptedWithOrWithoutPanel(){
  for(category in listOf("免疫","免疫学","临床免疫","生化免疫","病毒学","化学发光","ELISA","免疫学（定性）","免 疫")){
   val d=pastedReportDraft(source(category))
   assertEquals(category,d.type);assertFalse(category,d.valid())
   val single=pastedReportDraft("合成医院\n$category\n2001-01-02\nHBSAG 乙肝表面抗原 阴性  阴性")
   assertEquals(category,single.type);assertFalse(category,single.valid())
  }
 }
 @Test fun headerCannotOverrideAnalyteIdentityAndMixedChemistryIsNotLiverOnly(){
  assertTrue(ReportCategoryPolicy.problems("血常规","乙肝两对半（定性）",listOf("HBSAG")).isNotEmpty())
  assertTrue(ReportCategoryPolicy.problems("甲状腺功能","甲功五项",listOf("HBSAG")).isNotEmpty())
  assertTrue(ReportCategoryPolicy.problems("血常规","",listOf("HBSAG","CUSTOM-synthetic")).isNotEmpty())
  assertTrue(ReportCategoryPolicy.problems("肝功能","",listOf("ALT","CREA")).isNotEmpty())
  assertTrue(ReportCategoryPolicy.problems("生化","",listOf("ALT","CREA")).isEmpty())
  for((category,keys) in listOf("血常规" to listOf("WBC","HGB"),"凝血功能" to listOf("PT","APTT","DDIMER"),"甲状腺功能" to listOf("FT3","FT4","TSH"),"尿液检查" to listOf("UR-GLU"),"粪便检查" to listOf("ST-WBC")))
   assertTrue(category,ReportCategoryPolicy.problems(category,"",keys).isEmpty())
  assertTrue(ReportCategoryPolicy.problems("传染病筛查","",listOf("FT3")).isNotEmpty())
 }
 @Test fun storeGuardRejectsWrongNewCategoryWithoutWritingAnyRecord(){
  val store=store();val draft=pastedReportDraft(source("传染病筛查"))
  val good=materializeReportDraft(draft,store)
  val wrong=good.copy(reportType="免疫",results=good.results.map{it.copy(reportType="免疫")})
  rejected{store.saveReport(wrong)};assertTrue(store.reports().isEmpty())
 }
 @Test fun oldWrongCategoryIsReadOnlyUntilExplicitCorrectionAndExportDoesNotPropagateIt(){
  val store=store();val good=materializeReportDraft(pastedReportDraft(source("传染病筛查")),store);store.saveReport(good)
  val prefs=RuntimeEnvironment.getApplication().getSharedPreferences("healthtrend_store_v1",Context.MODE_PRIVATE)
  val raw=JSONArray(prefs.getString("reports","[]"));raw.getJSONObject(0).put("type","免疫")
  prefs.edit().putString("reports",raw.toString()).putString("course_records","[]").commit()
  val before=prefs.getString("reports",null)
  val old=store.reports().single();assertEquals("免疫",old.reportType)
  rejected{store.exportReportAsPastedText(old)}
  assertEquals(before,prefs.getString("reports",null))
  val editing=ReportDraft.from(old);assertTrue(reportValidationProblems(editing).toString(),editing.valid())
  val corrected=materializeReportDraft(editing.copy(type="传染病筛查"),store)
  assertEquals(old.id,corrected.id);assertEquals(old.results.map{it.id},corrected.results.map{it.id})
  assertEquals(old.results.map{it.textValue},corrected.results.map{it.textValue})
  store.saveReport(corrected);assertEquals("传染病筛查",store.reports().single().reportType)
  assertEquals("[]",prefs.getString("course_records",null))
 }
 @Test fun confirmedReplacementCannotBypassTheSameCategoryGuard(){
  val store=store();val date=parseDate("2001-01-02")!!
  val old=store.buildReportFromPool("合成医院","血常规",date,emptyList(),ReportParser.parse("HGB 血红蛋白 123.00 g/L 110-150"))
  store.saveReport(old)
  val bad=store.buildReportFromPool("合成医院","血常规",date,emptyList(),ReportParser.parse("HBSAG 乙肝表面抗原 阴性 - 阴性"))
  rejected{store.replaceReportWithConfirmed(old.id,bad)}
  assertEquals(old,store.reports().single());assertTrue(store.archivedReportReplacements().isEmpty())
 }
 @Test fun unknownMetricNeverGetsGuessedIntoAnInfectionCategory(){
  assertNull(ReportCategoryPolicy.infer(listOf("CUSTOM-synthetic")))
  assertNull(ReportCategoryPolicy.infer(listOf("HBSAG","FT3")))
  assertEquals("传染病筛查",ReportCategoryPolicy.infer(listOf("HBSAG","HBSAB")))
  val unknown=DraftRow(name="合成自定义项目",key="CUSTOM-synthetic",text="1.00",unit="",referenceRaw="")
  val d=ReportDraft(hospital="合成医院",type="人工确认检查",date="2001-01-02",rows=listOf(unknown))
  assertTrue(reportValidationProblems(d).toString(),d.valid());assertEquals("CUSTOM-synthetic",d.rows.single().key)
 }
}
