package com.combustible12.healthtrend

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
class DimerBasisTest {
 private fun result(unit:String,value:Double=0.5,rawLine:String="")=LabResult(
  unit+value,"report-$unit","医院","凝血功能",null,"DDIMER","D-二聚体",value,unit,0.0,0.55,1L,
  textValue=value.toString(),rawLine=rawLine,normalizedValue=UnitNormalizer.normalize("DDIMER",value,unit).first,
  normalizedUnit=UnitNormalizer.normalize("DDIMER",value,unit).second
 )

 @Test fun feuAndDduAreSeparateCardsWhileTypographyAndScalesCollapseWithinBasis(){
  val feu=listOf("ug/mL FEU","µg/ml.FEU","μg FEU/mL","mg/L FEU").map{result(it)}
  val ddu=listOf("ug/mL DDU","μg/ml.DDU","mg/L DDU").map{result(it)}
  assertTrue(feu.all{trendUnitIdentity(it)=="μg/mL FEU"})
  assertTrue(ddu.all{trendUnitIdentity(it)=="μg/mL DDU"})
  assertEquals(1,feu.map{trendCardIdentity("DDIMER",it)}.distinct().size)
  assertEquals(1,ddu.map{trendCardIdentity("DDIMER",it)}.distinct().size)
  assertNotEquals(trendCardIdentity("DDIMER",feu.first()),trendCardIdentity("DDIMER",ddu.first()))
  assertNotEquals(trendCardIdentity("DDIMER",feu.first()),trendCardIdentity("DDIMER",result("ug/mL")))
  assertFalse(trendUnitsCompatible(feu.first(),ddu.first()))
 }

 @Test fun scaleConversionChangesValueAndReferenceRangeButNeverCrossesBasis(){
  val ng=result("ng/mL FEU",500.0)
  assertEquals(0.5,ng.trendPlotValue()!!,0.0)
  assertEquals("μg/mL FEU",trendUnitIdentity(ng))
  assertEquals(0.0,ng.trendReferenceRange().first!!,0.0)
  assertEquals(0.00055,ng.trendReferenceRange().second!!,0.0)
  val micro=result("μg/mL FEU",0.5)
  assertTrue(trendUnitsCompatible(ng,micro))
  assertEquals(DimerBasis.DDU,dDimerBasis(result("μg/mL",rawLine="D-二聚体 0.5 μg/mL DDU")))
 }

 @Test fun parserPreservesBasisFromCommonUnitLayouts(){
  listOf("ug/ml.FEU","μg/mL FEU","μg FEU/mL").forEach{unit->
   val parsed=ReportParser.parse("DDIMER D-二聚体 0.22 $unit 0-0.55").single()
   assertEquals("$unit", "μg/mL FEU",parsed.unit)
  }
  val ddu=ReportParser.parse("DDIMER D-二聚体 0.11 ug/mL DDU 0-0.25").single()
  assertEquals("μg/mL DDU",ddu.unit)
 }

 @Test fun onlyTwoConfirmedHistoricalRowsGainFeuAndMigrationIsIdempotent(){
  val zone=ZoneId.systemDefault()
  fun report(hospital:String,date:LocalDate,value:Double)=LabReport(
   "$hospital-$date",hospital,"凝血功能",date.atStartOfDay(zone).toInstant().toEpochMilli(),null,emptyList(),
   listOf(result("ug/mL",value).copy(id="$hospital-$value",reportId="$hospital-$date",hospitalKey=hospital,
    referenceLowAtTest=0.0,referenceHighAtTest=0.55,testedAtEpochMillis=date.atStartOfDay(zone).toInstant().toEpochMilli()))
  )
  val maternal=report("福建省妇幼保健院",LocalDate.of(2026,9,7),0.22)
  val cancer=report("福建省肿瘤医院",LocalDate.of(2026,10,23),0.23)
  val unknown=report("其他医院",LocalDate.of(2026,10,23),0.23)
  val first=migrateDimerBasisHistory(listOf(maternal,cancer,unknown),zone)
  assertEquals(2,first.changedResults)
  assertEquals(listOf("μg/mL FEU","μg/mL FEU","ug/mL"),first.reports.map{it.results.single().unitAtTest})
  val second=migrateDimerBasisHistory(first.reports,zone)
  assertEquals(0,second.changedResults)
  assertEquals(first.reports,second.reports)
 }

 @Test fun storePersistsConfirmedMaternalFeuWithoutChangingValueOrRange(){
  val context=RuntimeEnvironment.getApplication()
  context.getSharedPreferences("healthtrend_store_v1",Context.MODE_PRIVATE).edit().clear().commit()
  context.getSharedPreferences("healthtrend_safety_snapshots",Context.MODE_PRIVATE).edit().clear().commit()
  val store=HealthStore(context);val zone=ZoneId.systemDefault()
  val date=LocalDate.of(2026,9,7).atStartOfDay(zone).toInstant().toEpochMilli()
  val parsed=ReportParser.parse("DDIMER D-二聚体 0.22 ug/mL 0-0.55","凝血功能")
  store.saveReport(store.buildReportFromPool("福建省妇幼保健院","凝血功能",date,emptyList(),parsed))
  val saved=HealthStore(context).reports().single().results.single()
  assertEquals(0.22,saved.value!!,0.0)
  assertEquals(0.0,saved.referenceLowAtTest!!,0.0)
  assertEquals(0.55,saved.referenceHighAtTest!!,0.0)
  assertEquals("μg/mL FEU",saved.unitAtTest)
  assertEquals("μg/mL FEU",saved.normalizedUnit)
 }
}
