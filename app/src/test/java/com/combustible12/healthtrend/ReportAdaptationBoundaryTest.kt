package com.combustible12.healthtrend

import org.junit.Assert.*
import org.junit.Test

class ReportAdaptationBoundaryTest {
 @Test fun specimenNamesCannotPolluteGlobalBloodIdentities(){
  assertEquals("GLU",ReportParser.parse("葡萄糖 4.000 mmol/L 3-6").single().metricKey)
  assertEquals("UR-GLU",ReportParser.parse("葡萄糖 阴性 - 阴性","尿常规").single().metricKey)
  assertEquals("ST-WBC",ReportParser.parse("镜检白细胞 未检出 - 未检出","粪便常规").single().metricKey)
  assertTrue(ReportParser.parse("镜检白细胞 未检出 - 未检出").single().metricKey.startsWith("待核对:"))
  assertEquals("WBC",ReportParser.parse("WBC 白细胞计数 4.000 10^9/L 3-6","尿常规").single().metricKey)
 }
 @Test fun embeddedDigitsAndComplexUnitsRetainSourceTokens(){
  val p=ReportParser.parse("CA125 糖类抗原125 8.2300 U/ml 1.000-9.000").single()
  assertEquals("8.2300",p.textValue);assertEquals("1.000-9.000",p.referenceRaw)
  listOf("PT 凝血酶原时间 8.2300 sec 1-9" to "sec","OSM 渗透压 8.2300 mOsm/kgH2O 1-9" to "mOsm/kgH2O","DDIMER D-二聚体 8.2300 ug/ml.FEU 1-9" to "ug/ml.FEU").forEach{(line,unit)->assertEquals(unit,ReportParser.parse(line).single().unit)}
 }
 @Test fun blankTextReferenceAndComparatorNeverInventPrecision(){
  val text=ReportParser.parse("ST-COLOR\t粪便颜色\t合成描述\t-\t").single()
  assertEquals("",text.unit);assertEquals("",text.referenceRaw)
  val limit=ReportParser.parse("CA724 糖类抗原CA72-4 <0.700 U/ml 0-9").single()
  assertEquals("<0.700",limit.textValue);assertEquals("<",limit.comparator)
 }
 @Test fun explicitWidthConflictAndUnknownUnitRemainReviewable(){
  val conflict=ReportParser.parse("PDW-CV 血小板分布宽度CV 12.00 fL 10-20").single()
  assertEquals("PDW-CV",conflict.metricKey);assertTrue(metricNeedsReview(conflict,null))
  assertTrue(ReportParser.parse("大血小板 12.00 - 10-20").single().metricKey.startsWith("待核对:"))
  assertEquals("",ReportParser.parse("HGB 100 garbage 90-120").single().unit)
 }
 @Test fun uncertainReferenceConditionsArePreservedForManualSelection(){
  val p=ReportParser.parse("UR-SG\t尿比重\t1.020\t-\t成人随机尿1.003-1.030；新生儿1.002-1.004；晨尿>1.020").single()
  assertNull(p.referenceLow);assertNull(p.referenceHigh);assertTrue(metricNeedsReview(p,null))
  assertEquals(p.referenceRaw,DraftRow.from(p).parsed().referenceRaw)
 }
 @Test fun latestTextColorsAreGrayForUnremarkableGreenOnlyForProtectiveAntibody(){
  fun r(key:String,text:String)=LabResult("x","r","合成医院","筛查",null,key,"合成项目",null,"",null,null,1L,textValue=text,referenceRaw="阴性")
  assertEquals(Muted,qualitativeTrendColor(r("HBSAG","阴性")))
  assertEquals(Good,qualitativeTrendColor(r("HBSAB","阳性")))
  assertEquals(Bad,qualitativeTrendColor(r("HBSAG","阳性")))
  assertEquals(Muted,qualitativeTrendColor(r("HBSAG","弱阳性")))
 }
}
