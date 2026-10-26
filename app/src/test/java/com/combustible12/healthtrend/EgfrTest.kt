package com.combustible12.healthtrend

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class EgfrTest {
 @Test fun equationMatchesPublishedAdultExamplesAndDoesNotUseWeight(){
  assertEquals(110.32,ckdepi2021Egfr(46.0,54,EgfrSex.FEMALE)!!,0.01)
  assertEquals(91.69,ckdepi2021Egfr(88.4,50,EgfrSex.MALE)!!,0.01)
  assertEquals(68.63,ckdepi2021Egfr(88.4,50,EgfrSex.FEMALE)!!,0.01)
 }

 @Test fun derivesOnePointPerExactAdultCreatinineAndPreservesSourceIdentity(){
  val zone=ZoneId.of("UTC")
  val report=report("r1","c1",46.0,"μmol/L",LocalDate.of(2026,9,23),zone)
  val row=derivedEgfrRows(listOf(report),PatientProfile(birthDate="1972/01/02",sex="女性"),zone).single()
  assertSame(report,row.first)
  assertEquals(DERIVED_EGFR_KEY,row.second.metricKey)
  assertEquals("c1",row.second.id)
  assertEquals("r1",row.second.reportId)
  assertEquals(DERIVED_EGFR_UNIT,row.second.unitAtTest)
  assertEquals(90.0,row.second.referenceLowAtTest!!,0.0)
  assertEquals("110.3",row.second.textValue)
  assertEquals(report.results.single(),originalTrendPoint(row).second)
 }

 @Test fun mgDlAndUmolLProduceSameEstimate(){
  val zone=ZoneId.of("UTC");val day=LocalDate.of(2026,1,2);val profile=PatientProfile(birthDate="1976-01-01",sex="M")
  val metric=derivedEgfrRows(listOf(report("a","ca",1.0,"mg/dL",day,zone)),profile,zone).single().second.value!!
  val canonical=derivedEgfrRows(listOf(report("b","cb",88.4,"μmol/L",day,zone)),profile,zone).single().second.value!!
  assertEquals(canonical,metric,0.000001)
 }

 @Test fun ageIsCalculatedAtEachReportDate(){
  val zone=ZoneId.of("UTC");val profile=PatientProfile(birthDate="1976-06-15",sex="男")
  val before=derivedEgfrRows(listOf(report("a","ca",88.4,"μmol/L",LocalDate.of(2026,6,14),zone)),profile,zone).single().second.value!!
  val birthday=derivedEgfrRows(listOf(report("b","cb",88.4,"μmol/L",LocalDate.of(2026,6,15),zone)),profile,zone).single().second.value!!
  assertEquals(ckdepi2021Egfr(88.4,49,EgfrSex.MALE)!!,before,0.000001)
  assertEquals(ckdepi2021Egfr(88.4,50,EgfrSex.MALE)!!,birthday,0.000001)
  assertTrue(before>birthday)
 }

 @Test fun refusesComparatorMissingDemographicsAndMinor(){
  val zone=ZoneId.of("UTC");val day=LocalDate.of(2026,1,2)
  val exact=report("a","ca",88.4,"μmol/L",day,zone)
  assertTrue(derivedEgfrRows(listOf(exact),PatientProfile(),zone).isEmpty())
  assertTrue(derivedEgfrRows(listOf(exact),PatientProfile(birthDate="2010-01-01",sex="男"),zone).isEmpty())
  val comparator=exact.copy(results=listOf(exact.results.single().copy(comparator="<",textValue="<88.4")))
  assertTrue(derivedEgfrRows(listOf(comparator),PatientProfile(birthDate="1970-01-01",sex="男"),zone).isEmpty())
 }

 private fun report(id:String,resultId:String,value:Double,unit:String,day:LocalDate,zone:ZoneId):LabReport{
  val time=day.atStartOfDay(zone).toInstant().toEpochMilli()
  val result=LabResult(resultId,id,"合成医院","肾功能",null,"CREA","肌酐",value,unit,40.0,100.0,time)
  return LabReport(id,"合成医院","肾功能",time,null,emptyList(),listOf(result))
 }
}
