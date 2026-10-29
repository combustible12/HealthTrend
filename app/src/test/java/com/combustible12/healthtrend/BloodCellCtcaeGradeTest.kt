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
  assertEquals("建议：无",normal.advice)
  val missingRange=result("WBC",2.5,null).let(::bloodCellCtcaeV5Grade)!!
  assertEquals("暂时无法分级",missingRange.label)
  assertEquals("缺少正常下限",missingRange.reason)
  assertNull(missingRange.advice)
  assertEquals("暂时无法分级",result("WBC",2.5,4.0).copy(comparator="<").let(::bloodCellCtcaeV5Grade)!!.label)
  assertEquals("暂时无法分级",result("WBC",2.5,4.0,"cells/L").let(::bloodCellCtcaeV5Grade)!!.label)
  assertNull(bloodCellCtcaeV5Grade(result("RBC",3.0,4.0,"10^12/L")))
 }

 @Test fun conciseActionsAreGradeAndMetricSpecific(){
  assertEquals("处理：复查血常规。",grade("NEUT#",1.2,1.8).advice)
  assertEquals("处理：复查血常规。",grade("PLT",60.0,125.0).advice)
  assertEquals("处理：复查血常规。",grade("HGB",90.0,115.0,"g/L").advice)

  val neutrophil=grade("NEUT#",0.72,1.8)
  assertEquals(3,neutrophil.grade)
  assertEquals("可能处理：升白针、抗感染或调整治疗。",neutrophil.advice)

  val platelet=grade("PLT",40.0,125.0)
  assertEquals("可能处理：输注血小板或调整治疗。",platelet.advice)

  val anemia=grade("HGB",75.0,115.0,"g/L")
  assertEquals("可能处理：输血。",anemia.advice)
 }

 @Test fun gradeOneKeepsMetricSpecificWarning(){
  assertEquals("建议：轻度降低。38℃或寒战等，就医。",grade("WBC",3.5,4.0).advice)
  assertEquals("建议：轻度降低。出血不止、黑便、血尿或剧烈头痛时，就医。",grade("PLT",100.0,125.0).advice)
  assertEquals("建议：轻度降低。出现气促、胸痛、明显头晕或晕厥时，就医。",grade("HGB",105.0,115.0,"g/L").advice)
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
