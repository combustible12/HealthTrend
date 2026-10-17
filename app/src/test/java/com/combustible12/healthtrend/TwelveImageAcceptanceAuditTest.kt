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
  val line=if(standard)listOf(key,definition.name,value,unit.ifBlank{"-"},reference).joinToString("\t")
   else "${row.getString("name")} $value ${unit.ifBlank{"-"}} $reference"
  return try{
   val draft=pastedReportDraft("合成医院\n${row.getString("type")}\n2001-01-01\n$line",store=store)
   if(draft.rows.size!=1)return "项目数不符:${draft.rows.size}"
   val item=draft.rows.single()
   if(item.key!=key)return "身份未匹配:${item.key}"
   if(item.uncertain||!draft.valid())return "需要核对:${reportValidationProblems(draft).joinToString("；")}"
   if(item.text!=value)return "结果文本变化"
   if(item.unit!=unit)return "单位变化:${item.unit}"
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
   if(exported.key!=key||exported.text!=value||exported.unit!=unit||exported.parsed().referenceRaw!=reference)return "导出再导入变化"
   val series=reopened.trend(key)
   if(kind=="带比较符数值"){
    if(series.isNotEmpty())return "比较符结果被当作精确趋势点"
    if(after.results.single().comparator!="<")return "结果比较符丢失"
   }else if(series.single().second.textValue!=value)return "趋势入口变化"
   "合成链路通过"
  }catch(e:Exception){"未完成:${e.message.orEmpty().substringBefore('\n')}"}
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
