package com.combustible12.healthtrend

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Every non-card occurrence must pass both paths; each gap fails this gate and is logged.
 * Fixtures contain only generic analyte terms and units; all values/dates/ranges are synthetic. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
class TwelveImageAcceptanceAuditTest {
 private val context get()=RuntimeEnvironment.getApplication()
 private fun audit(row:JSONObject,standard:Boolean):String {
  context.getSharedPreferences("healthtrend_store_v1",Context.MODE_PRIVATE).edit().clear().commit()
  val store=HealthStore(context)
  val key=row.getString("key");val kind=row.getString("kind")
  if(kind=="血型卡片"){
   val profile=BloodGroupProfile("A","阴性","阳性","阴性","2001-01-01")
   store.saveBloodGroupProfile(profile)
   assertEquals(profile,HealthStore(context).bloodGroupProfile())
   return "卡片保存通过；不验自动导入/趋势"
  }
  val definition=UnifiedMetricStandard.definition(key)?:return "缺词典身份"
  val value=when(kind){"阴阳性"->"阴性";"弱阳性"->"弱阳性";"半定量"->"2+";"未检出"->"未检出";"描述"->"合成描述";"带比较符数值"->"<0.700";else->"7.2300"}
  val reference=when(kind){"阴阳性","弱阳性","半定量"->"阴性";"未检出"->"未检出";"描述"->"";else->"1.000-9.000"}
  val unit=row.getString("unit")
  val expectedUnit=displayLabUnit(unit)
  val line=if(standard)listOf(key,definition.name,value,unit.ifBlank{"-"},reference).joinToString("\t")
   else "${row.getString("name")} $value ${unit.ifBlank{"-"}} $reference"
  return try{
   val draft=pastedReportDraft("合成医院\n${row.getString("type")}\n2001-01-01\n$line",store=store)
   if(draft.rows.size!=1)return "项目数不符:${draft.rows.size}"
   val item=draft.rows.single()
   if(item.key!=key)return "身份未匹配:${item.key}"
   if(item.uncertain||!draft.valid())return "需要核对:${reportValidationProblems(draft).joinToString("；")}"
   if(item.text!=value)return "结果文本变化"
   if(item.unit!=expectedUnit)return "单位变化:${item.unit}"
   if(item.parsed().referenceRaw!=reference)return "参考文本变化:${item.parsed().referenceRaw}"
   val report=materializeReportDraft(draft,store);store.saveReport(report)
   val reopened=HealthStore(context)
   val saved=reopened.reports().single()
   if(saved!=report)return "重读变化"
   val edited=materializeReportDraft(ReportDraft.from(saved).copy(ocr="合成编辑"),reopened)
   reopened.saveReport(edited)
   val after=HealthStore(context).reports().single()
   if(after.results!=saved.results)return "编辑改变结果"
   val exported=pastedReportDraft(reopened.exportReportAsPastedText(after),store=reopened).rows.single()
   if(exported.key!=key||exported.text!=value||exported.unit!=expectedUnit||exported.parsed().referenceRaw!=reference)return "导出再导入变化"
   val series=reopened.trend(key)
   if(kind=="带比较符数值"){
    if(series.isNotEmpty())return "比较符结果被当作精确趋势点"
    if(after.results.single().comparator!="<")return "结果比较符丢失"
   }else if(series.single().second.textValue!=value)return "趋势入口变化"
   "合成链路通过"
  }catch(e:Exception){"未完成:${e.message.orEmpty().substringBefore('\n')}"}
 }
 @Test fun changingUnresolvedSpecimenTypeDoesNotRewriteSavedIdentities(){
  context.getSharedPreferences("healthtrend_store_v1",Context.MODE_PRIVATE).edit().clear().commit()
  val store=HealthStore(context)
  val pending=ReportDraft(hospital="合成医院",type="检查报告",date="2001-01-01",rows=listOf(DraftRow(name="镜检白细胞",key="待核对:镜检白细胞",text="未检出")))
  assertEquals("ST-WBC",retargetImportedDraft(pending,store,type="粪便常规").rows.single().key)
  val old=store.buildReportFromPool("合成医院","生化",1000000000000L,emptyList(),ReportParser.parse("GLU 葡萄糖 4.000 mmol/L 3-6"))
  val edited=retargetImportedDraft(ReportDraft.from(old),store,type="尿液分析")
  assertEquals(old.results.map{it.metricKey},edited.rows.map{it.key})
  assertEquals("尿液分析",ReportMetadata.extract("合成医院\n尿液分析\n2001-01-01\n葡萄糖 阴性 - 阴性",emptyList()).reportType)
  assertEquals("传染病筛查",ReportMetadata.extract("合成医院\n传染病筛查\n2001-01-01\nHBSAG 乙肝表面抗原 阴性 - 阴性",emptyList()).reportType)
 }
 @Test fun completePanelsKeepAllRowsAcrossBothImportPaths(){
  val data=JSONArray(javaClass.getResourceAsStream("/twelve-image-terms.json")!!.bufferedReader().use{it.readText()})
  val groups=(0 until data.length()).map{data.getJSONObject(it)}.filter{it.getString("kind")!="血型卡片"}
   .groupBy{it.getString("image")+it.getString("panel")}
  assertEquals(18,groups.size)
  groups.forEach{(panel,rows)->listOf(false,true).forEach{standard->
   context.getSharedPreferences("healthtrend_store_v1",Context.MODE_PRIVATE).edit().clear().commit()
   val store=HealthStore(context)
   val lines=rows.map{r->
    val key=r.getString("key");val kind=r.getString("kind")
    val value=when(kind){"阴阳性"->"阴性";"弱阳性"->"弱阳性";"半定量"->"2+";"未检出"->"未检出";"描述"->"合成描述";"带比较符数值"->"<0.700";else->"7.2300"}
    val reference=when(kind){"阴阳性","弱阳性","半定量"->"阴性";"未检出"->"未检出";"描述"->"";else->"1.000-9.000"}
    val unit=r.getString("unit").ifBlank{"-"}
    if(standard)listOf(key,UnifiedMetricStandard.definition(key)!!.name,value,unit,reference).joinToString("\t")
    else "${r.getString("name")} $value $unit $reference"
   }
   val draft=pastedReportDraft("合成医院\n${rows.first().getString("type")}\n2001-01-01\n"+lines.joinToString("\n"),store=store)
   assertEquals("$panel rows ($standard)",rows.size,draft.rows.size)
   assertEquals(rows.map{it.getString("key")},draft.rows.map{it.key})
   assertFalse("$panel review ($standard)",draft.rows.any{it.uncertain})
   assertTrue(reportValidationProblems(draft).toString(),draft.valid())
   val report=materializeReportDraft(draft,store);store.saveReport(report)
   val reloaded=HealthStore(context)
   assertEquals(report,reloaded.reports().single())
   val exported=pastedReportDraft(reloaded.exportReportAsPastedText(report),store=reloaded)
   assertEquals(draft.hospital,exported.hospital)
   assertEquals(draft.type,exported.type)
   assertEquals(draft.date,exported.date)
   assertEquals(draft.rows.map{it.key},exported.rows.map{it.key})
   assertEquals(draft.rows.map{it.text},exported.rows.map{it.text})
   assertEquals(draft.rows.map{it.unit},exported.rows.map{it.unit})
   assertEquals(draft.rows.map{it.parsed().referenceRaw},exported.rows.map{it.parsed().referenceRaw})
   println("HT12_PANEL|$panel|$standard|${rows.size}|PASS")
  }}
 }
 @Test fun evaluateEveryVisibleProjectOccurrenceAndPrintActualOutcomes(){
  val data=JSONArray(javaClass.getResourceAsStream("/twelve-image-terms.json")!!.bufferedReader().use{it.readText()})
  assertEquals(194,data.length())
  val keys=mutableSetOf<String>();var direct=0;var standard=0;var cards=0
  repeat(data.length()){index->
   val row=data.getJSONObject(index);keys+=row.getString("key")
   val a=audit(row,false);val b=audit(row,true)
   if(a=="合成链路通过")direct++
   if(b=="合成链路通过")standard++
   if(row.getString("kind")=="血型卡片")cards++
   println("HT12|${row.getString("id")}|$a|$b")
  }
  assertEquals(113,keys.size);assertEquals(4,cards)
  println("HT12_TOTAL|194|113|$direct|$standard|$cards")
  assertEquals("原名称入口仍有未完成项，查看HT12逐行日志",190,direct)
  assertEquals("固定格式入口仍有未完成项，查看HT12逐行日志",190,standard)
 }
}
