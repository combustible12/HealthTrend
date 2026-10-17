package com.combustible12.healthtrend

import org.junit.Assert.*
import org.junit.Test

/** Synthetic rows exercise the same parser, draft and chart projection as imports. */
class ReportCatalogueAdaptationTest {
 private fun result(p:ParsedLabResult)=LabResult("point","report","合成医院","检验",null,p.metricKey,p.displayName,p.value,p.unit,p.referenceLow,p.referenceHigh,1L,textValue=p.textValue,referenceRaw=p.referenceRaw,reportFlag=p.reportFlag)
 @Test fun missingIdentitiesAndMethodsStayDistinct(){
  val rows=listOf(
   "FDP 纤维蛋白(原)降解产物 1.70 mg/L 0-5" to "FDP",
   "Anti-HCV 丙肝抗体 0.08 S/CO 0-1" to "HCVAB",
   "HIVAg/Ab 人免疫缺陷病毒抗原/抗体联合检测 0.03 S/CO 0-1" to "HIVAGAB",
   "TPPA 梅毒螺旋体抗体 阴性 - 阴性" to "TPPA")
  rows.forEach{(line,key)->val p=ReportParser.parse(line).single();assertEquals(line,key,p.metricKey);assertTrue(DraftRow.from(p).valid())}
  assertNotEquals(ReportParser.key("HIV抗体"),ReportParser.key("HIV抗原/抗体联合检测"))
  assertNotEquals(ReportParser.key("TP"),ReportParser.key("TPPA"))
  assertNotEquals(ReportParser.key("TPAB"),ReportParser.key("TPPA"))
  assertTrue(UnifiedMetricStandard.keysForType("凝血").contains("FDP"))
  assertTrue(UnifiedMetricStandard.keysForType("传染病筛查").containsAll(setOf("HCVAB","HIVAGAB","TPPA")))
 }
 @Test fun analyzerCountUnitsNeverMultiplyMeasurement(){
  listOf("WBC 白细胞计数 6.420 N 10E9/L 3.0-10.0" to "10^9/L", "RBC 红细胞计数 4.120 N 10E12/L 3.0-5.0" to "10^12/L").forEach{(line,normalized)->
   val p=ReportParser.parse(line).single();val r=result(p)
   assertEquals(if(p.metricKey=="WBC")"10E9/L" else "10E12/L",p.unit)
   assertEquals(p.value,UnitNormalizer.normalize(p.metricKey,p.value,p.unit).first)
   assertEquals(normalized,UnitNormalizer.normalize(p.metricKey,p.value,p.unit).second)
   assertEquals(p.value,r.normalizedValue);assertEquals("N",p.reportFlag)
  }
  assertEquals("1.20e-3",ReportParser.parse("CRP C反应蛋白 1.20e-3 mg/L 0-5").single().textValue)
 }
 @Test fun aliasesAndWidthUnitsResolveWithoutOverridingExplicitIdentity(){
  val cases=mapOf("平均RBC体积" to ("fL" to "MCV"),"平均RBC血红蛋白含量" to ("pg" to "MCH"),"平均RBC血红蛋白浓度" to ("g/L" to "MCHC"),"红细胞体积分布宽度" to ("%" to "RDW-CV"),"血小板体积分布宽度" to ("%" to "PDW-CV"),"红细胞分布宽度" to ("fL" to "RDW-SD"),"血小板分布宽度" to ("fL" to "PDW-SD"))
  cases.forEach{(name,pair)->val p=ReportParser.parse("$name 12.00 ${pair.first} 10-20").single();assertEquals(name,pair.second,p.metricKey);assertFalse(name,metricNeedsReview(p,null))}
  val conflict=ReportParser.parse("PDW-CV 血小板分布宽度CV 12.00 fL 10-20").single()
  assertEquals("PDW-CV",conflict.metricKey);assertTrue(metricNeedsReview(conflict,null))
  assertTrue(metricNeedsReview(ReportParser.parse("血小板体积分布宽度 12.00 - 10-20").single(),null))
 }
 @Test fun encodedComparatorAndFlagsPreserveSourceAndDetermineDirection(){
  val source="红细胞体积分布宽度 12.80 N % &lt;15"
  val p=ReportParser.parse(source).single();assertEquals(source,p.rawLine);assertEquals("<15",p.referenceRaw);assertEquals(15.0,p.referenceHigh!!,0.0);assertEquals("%",p.unit)
  listOf("2.00" to ResultStatus.LOW,"20.00" to ResultStatus.HIGH).forEach{(v,status)->
   val outside=ReportParser.parse("WBC 白细胞计数 $v Y 10E9/L 3-10").single()
   assertEquals("Y",outside.reportFlag);assertEquals(status,result(outside).status());assertFalse(metricNeedsReview(outside,null))
  }
  listOf("5.00 Y","2.00 N").forEach{value->
   val conflict=ReportParser.parse("WBC 白细胞计数 $value 10E9/L 3-10").single()
   assertTrue(metricNeedsReview(conflict,null));assertTrue(DraftRow.from(conflict).referenceNeedsReview());assertEquals(ResultStatus.UNKNOWN,result(conflict).status())
  }
  val partial=ReportParser.parse("PCT 血小板压积 0.250 Y % 0.188-0.2??").single()
  assertEquals("Y",partial.reportFlag);assertEquals(ResultStatus.UNKNOWN,result(partial).status())
 }
 @Test fun omittedAndExplicitBlankUnitsRetainQualitativeReference(){
  listOf("TPPA 梅毒螺旋体抗体 阴性 阴性","TPPA 梅毒螺旋体抗体 阴性 - 阴性").forEach{line->
   val p=ReportParser.parse(line).single();assertEquals("阴性",p.textValue);assertEquals("阴性",p.referenceRaw);assertEquals("",p.unit)
  }
 }
 @Test fun qualitativeReferenceIgnoresStaleNumericEditorBounds(){
  val row=DraftRow(key="TPPA",name="梅毒螺旋体抗体",text="阴性",low="0",high="1",referenceRaw="阴性")
  assertEquals("阴性",row.parsed().referenceRaw)
  assertEquals("阴性",row.copy(text="阳性").parsed().referenceRaw)
 }
}
