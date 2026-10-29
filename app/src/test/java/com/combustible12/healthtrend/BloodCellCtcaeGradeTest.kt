package com.combustible12.healthtrend

import org.junit.Assert.*
import org.junit.Test

class BloodCellCtcaeGradeTest {
 @Test fun gradesWhiteCellsNeutrophilsAndPlateletsAtCtcaeV5Boundaries(){
  assertGrades("WBC",4.0,listOf(3.0 to 1,2.999 to 2,2.0 to 2,1.999 to 3,1.0 to 3,0.999 to 4))
  assertGrades("NEUT#",1.8,listOf(1.5 to 1,1.499 to 2,1.0 to 2,0.999 to 3,0.5 to 3,0.499 to 4))
  assertGrades("PLT",125.0,listOf(75.0 to 1,74.999 to 2,50.0 to 2,49.999 to 3,25.0 to 3,24.999 to 4))
 }

 @Test fun gradesHemoglobinNumericallyWithoutInventingClinicalGradeFour(){
  assertGrades("HGB",115.0,listOf(100.0 to 1,99.9 to 2,80.0 to 2,79.9 to 3,30.0 to 3),"g/L")
 }

 @Test fun normalMissingRangeComparatorAndUnknownUnitAreExplicit(){
  val normal=grade("WBC",4.0,4.0)
  assertEquals("未达到 1 级",normal.label)
  assertEquals("无需因本项分级额外处理，按原计划复查。",normal.advice)
  assertEquals("无法判定",result("WBC",2.5,null).let(::bloodCellCtcaeV5Grade)!!.label)
  assertEquals("无法判定",result("WBC",2.5,4.0).copy(comparator="<").let(::bloodCellCtcaeV5Grade)!!.label)
  assertEquals("无法判定",result("WBC",2.5,4.0,"cells/L").let(::bloodCellCtcaeV5Grade)!!.label)
  assertNull(bloodCellCtcaeV5Grade(result("RBC",3.0,4.0,"10^12/L")))
 }

 @Test fun gradeThreeAdviceIsActionableAndMetricSpecific(){
  val neutrophil=grade("NEUT#",0.72,1.8)
  assertEquals(3,neutrophil.grade)
  assertTrue(neutrophil.advice.contains("当天联系治疗团队"))
  assertTrue(neutrophil.advice.contains("38℃"))
  assertTrue(neutrophil.advice.contains("由医生评估"))

  val platelet=grade("PLT",40.0,125.0)
  assertTrue(platelet.advice.contains("出血不止"))
  assertTrue(platelet.advice.contains("剧烈头痛"))

  val anemia=grade("HGB",75.0,115.0,"g/L")
  assertTrue(anemia.advice.contains("气促"))
  assertTrue(anemia.advice.contains("晕厥"))
 }

 private fun assertGrades(key:String,low:Double,cases:List<Pair<Double,Int>>,unit:String="10^9/L"){
  cases.forEach{(value,expected)->assertEquals("$key $value",expected,grade(key,value,low,unit).grade)}
 }
 private fun grade(key:String,value:Double,low:Double,unit:String="10^9/L")=
  bloodCellCtcaeV5Grade(result(key,value,low,unit))!!
 private fun result(key:String,value:Double,low:Double?,unit:String="10^9/L")=LabResult(
  id="x",reportId="r",hospitalKey="h",reportType="血常规",templateVersion=null,
  metricKey=key,rawName=key,value=value,unitAtTest=unit,referenceLowAtTest=low,
  referenceHighAtTest=null,testedAtEpochMillis=0L
 )
}
