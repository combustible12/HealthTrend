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

class DistributionWidthIdentityMigrationTest {
 private fun result(id:String,key:String,name:String,unit:String,value:Double=12.0)=LabResult(
  id,"report-$id","合成医院","血常规",null,key,name,value,unit,9.0,17.0,1_700_000_000_000L,
  textValue="12.00",rawLine="$key $name 12.00 $unit 9-17",referenceRaw="9-17"
 )
 private fun report(id:String,row:LabResult,hospital:String="合成医院",date:Long=1_700_000_000_000L)=LabReport(
  "report-$id",hospital,"血常规",date,null,emptyList(),listOf(row.copy(reportId="report-$id",hospitalKey=hospital,testedAtEpochMillis=date))
 )

 @Test fun finalRecognitionMatrixUsesExplicitNameThenUnitAndRejectsConflicts(){
  listOf(
   Triple("RDW","%","RDW-CV"),Triple("RDW","fL","RDW-SD"),
   Triple("PDW","%","PDW-CV"),Triple("PDW","FL","PDW-SD"),
   Triple("RDW-CV","％","RDW-CV"),Triple("RDW-SD","fl","RDW-SD"),
   Triple("PDW-CV","%","PDW-CV"),Triple("PDW-SD","fL","PDW-SD")
  ).forEach{(key,unit,target)->
   val family=key.substringBefore('-')
   val name=when(key){
    "RDW-CV"->"红细胞分布宽度CV";"RDW-SD"->"红细胞分布宽度SD"
    "PDW-CV"->"血小板分布宽度CV";"PDW-SD"->"血小板分布宽度SD"
    "RDW"->"红细胞分布宽度";else->"血小板分布宽度"
   }
   val resolution=resolveDistributionWidthIdentity(key,name,unit)
   assertEquals("$key $unit",target,resolution.targetKey)
   assertNull("$key $unit",resolution.problem)
   assertEquals(if(target.endsWith("CV"))"%" else "fL",resolution.displayUnit)
   assertEquals(family,resolution.family)
  }
  assertNotNull(resolveDistributionWidthIdentity("PDW-CV","血小板分布宽度CV","fL").problem)
  assertNotNull(resolveDistributionWidthIdentity("RDW-SD","红细胞分布宽度SD","%").problem)
  assertNotNull(resolveDistributionWidthIdentity("PDW","血小板分布宽度","").problem)
  assertNotNull(resolveDistributionWidthIdentity("RDW-CV","红细胞分布宽度SD","fL").problem)
 }

 @Test fun migrationMergesLegacyIdentitiesAcrossHospitalsAndIsIdempotent(){
  val wbc=result("wbc","WBC","白细胞计数","10^9/L",5.0)
  val source=listOf(
   report("pdw-old",result("pdw-old","PDW","血小板分布宽度","%"),"医院甲",1L),
   report("pdw-cv",result("pdw-cv","PDW-CV","血小板分布宽度CV","％"),"医院乙",2L),
   report("rdw-sd",result("rdw-sd","RDW","红细胞分布宽度","FL"),"医院甲",3L),
   report("wbc",wbc,"医院甲",4L)
  )
  val first=migrateDistributionWidthHistory(source)
  assertEquals(listOf("PDW-CV","PDW-CV","RDW-SD","WBC"),first.reports.map{it.results.single().metricKey})
  assertEquals(listOf("%","%","fL","10^9/L"),first.reports.map{it.results.single().unitAtTest})
  assertTrue(first.reviews.isEmpty())
  source.zip(first.reports).forEach{(before,after)->
   assertEquals(before.id,after.id);assertEquals(before.hospitalKey,after.hospitalKey)
   assertEquals(before.testedAtEpochMillis,after.testedAtEpochMillis)
   assertEquals(before.results.single().id,after.results.single().id)
   assertEquals(before.results.single().value,after.results.single().value)
   assertEquals(before.results.single().referenceLowAtTest,after.results.single().referenceLowAtTest)
   assertEquals(before.results.single().referenceHighAtTest,after.results.single().referenceHighAtTest)
  }
  val second=migrateDistributionWidthHistory(first.reports)
  assertEquals(first.reports,second.reports);assertEquals(0,second.changedResults)
 }

 @Test fun unresolvedAndSameReportCollisionsAreRetainedForReviewWithoutDroppingRows(){
  val missing=report("missing",result("missing","PDW","血小板分布宽度",""))
  val conflicting=report("conflict",result("conflict","RDW-SD","红细胞分布宽度SD","%"))
  val duplicate=report("duplicate",result("old","PDW","血小板分布宽度","%")).let{r->r.copy(results=listOf(
   r.results.single(),result("confirmed","PDW-CV","血小板分布宽度CV","%").copy(reportId=r.id)
  ))}
  val migrated=migrateDistributionWidthHistory(listOf(missing,conflicting,duplicate))
  assertEquals(listOf("PDW","RDW-SD"),migrated.reports.take(2).map{it.results.single().metricKey})
  assertEquals(listOf("PDW","PDW-CV"),migrated.reports.last().results.map{it.metricKey})
  assertEquals(4,migrated.reviews.size)
  assertEquals(4,migrated.reports.sumOf{it.results.size})
 }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
class DistributionWidthStoreMigrationTest {
 private fun legacyReportJson():JSONObject=JSONObject().put("id","legacy-report").put("hospital","历史医院")
  .put("type","血常规").put("reportName",JSONObject.NULL).put("date",123456789L).put("tv",JSONObject.NULL)
  .put("ocr","原始文本").put("system","").put("images",JSONArray()).put("results",JSONArray().put(JSONObject()
   .put("id","legacy-result").put("key","PDW").put("raw","血小板分布宽度")
   .put("value",16.0).put("text","16.0").put("cmp","").put("line","PDW 血小板分布宽度 16.0 % 15-17")
   .put("referenceRaw","15-17").put("reportFlag","").put("unit","％").put("low",15.0).put("high",17.0)
   .put("edited",false).put("normalizedValue",16.0).put("normalizedUnit","％")))

 @Test fun storeMigratesPersistsReopensExportsAndKeepsOriginalEvidence(){
  val context=RuntimeEnvironment.getApplication()
  val prefs=context.getSharedPreferences("healthtrend_store_v1",Context.MODE_PRIVATE)
  prefs.edit().clear().putString("reports",JSONArray().put(legacyReportJson()).toString())
   .putString("templates","[]").putString("metric_standard_ranges",JSONArray().put(JSONObject()
    .put("metric","PDW").put("unit","％").put("low",15.0).put("high",17.0)).toString()).commit()
  val store=HealthStore(context)
  val migrated=store.reports().single();val row=migrated.results.single()
  assertEquals("PDW-CV",row.metricKey);assertEquals("%",row.unitAtTest)
  assertEquals("16.0",row.textValue);assertEquals(16.0,row.value!!,0.0)
  assertEquals(123456789L,migrated.testedAtEpochMillis);assertEquals("历史医院",migrated.hospitalKey)
  assertEquals("15-17",row.referenceRaw);assertEquals("PDW 血小板分布宽度 16.0 % 15-17",row.rawLine)
  val afterFirst=prefs.getString("reports",null)
  assertEquals(migrated,HealthStore(context).reports().single())
  assertEquals(afterFirst,prefs.getString("reports",null))
  val ranges=store.referenceRangeHistory("PDW-CV")
  assertEquals(1,ranges.size);assertEquals(15.0,ranges.single().lower!!,0.0);assertEquals(17.0,ranges.single().upper!!,0.0)
  val exported=store.exportReportAsPastedText(migrated)
  val imported=pastedReportDraft(exported,store=store)
  assertTrue(reportValidationProblems(imported).toString(),imported.valid())
  assertEquals("PDW-CV",imported.rows.single().key)
  assertEquals("%",imported.rows.single().unit)
 }
}
