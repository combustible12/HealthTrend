package com.combustible12.healthtrend

import org.junit.Assert.*
import org.junit.Test

/** Synthetic-only regression cases: no patient data is stored in the repository. */
class MetricImportRegressionTest {
 @Test fun inverseLiverRatiosKeepDifferentIdentities() {
  assertEquals("AST/ALT", ReportParser.key("谷草/谷丙"))
  assertEquals("ALT/AST", ReportParser.key("谷丙/谷草"))
  assertNotEquals(ReportParser.key("谷草/谷丙"), ReportParser.key("谷丙/谷草"))
  val parsed=ReportParser.parse(ReportParser.bindExplicitLeadingIdentities(
   "ALT/AST 谷丙/谷草 0.82\nAST/ALT 谷草/谷丙 1.22"
  ))
  assertEquals(2,parsed.size)
  assertEquals(listOf("ALT/AST","AST/ALT"),parsed.map{it.metricKey})
  assertEquals(listOf("0.82","1.22"),parsed.map{it.textValue})
 }

 @Test fun resolvedOcrUncertaintyDoesNotBlockValidReport() {
  val draft=ReportDraft(
   hospital="测试医院",type="生化",date="2026-10-13",
   rows=listOf(DraftRow(name="肌酐",key="CREA",text="46",unit="μmol/L",uncertain=true))
  )
  assertTrue(reportValidationProblems(draft).toString(),draft.valid())
  assertTrue(reportValidationProblems(draft).isEmpty())
 }

 @Test fun unknownMetricStillNeedsExplicitIdentityConfirmation() {
  val draft=ReportDraft(
   hospital="测试医院",type="生化",date="2026-10-13",
   rows=listOf(DraftRow(name="新型检验项目",key="待核对:新型检验项目",text="2.0",uncertain=true))
  )
  assertFalse(draft.valid())
  assertTrue(reportValidationProblems(draft).any{it.contains("需要确认")})
 }

 @Test fun duplicateIdentityCannotBeSaved() {
  val draft=ReportDraft(
   hospital="测试医院",type="生化",date="2026-10-13",
   rows=listOf(
    DraftRow(name="白蛋白",key="ALB",text="42"),
    DraftRow(name="清蛋白",key="ALB",text="42")
   )
  )
  assertFalse(draft.valid())
  assertTrue(reportValidationProblems(draft).any{it.contains("重复")})
 }

 @Test fun oneSidedReferenceRangeIsNotAnUnknownMetric() {
  val draft=ReportDraft(
   hospital="测试医院",type="血常规",date="2026-10-13",
   rows=listOf(DraftRow(name="血小板压积",key="PCT",text="0.225",low="0.108",uncertain=true))
  )
  assertTrue(reportValidationProblems(draft).toString(),draft.valid())
 }

 @Test fun commonUnitsConvertValueAndBothReferenceLimitsTogether() {
  val metric="CREA"
  val value=UnitNormalizer.normalize(metric,0.8,"mg/dL")
  val low=UnitNormalizer.normalize(metric,0.6,"mg/dL")
  val high=UnitNormalizer.normalize(metric,1.1,"mg/dL")
  assertEquals("μmol/L",value.second)
  assertEquals(value.second,low.second)
  assertEquals(value.second,high.second)
  assertEquals(70.72,value.first!!,0.001)
  assertEquals(53.04,low.first!!,0.001)
  assertEquals(97.24,high.first!!,0.001)
 }
 @Test fun missingLowerBoundUsesSameCommonUnit() {
  val upper=UnitNormalizer.normalize("CREA",1.1,"mg/dL")
  val missing=UnitNormalizer.normalize("CREA",null,"mg/dL")
  assertNull(missing.first)
  assertEquals(upper.second,missing.second)
 }
 @Test fun unifiedPoolAliasesMapToOneMetric() {
  assertEquals("WBC",UnifiedMetricStandard.resolveName("白细胞数"))
  assertEquals("WBC",UnifiedMetricStandard.resolveName("白细胞计数"))
  assertEquals("ALT",UnifiedMetricStandard.resolveName("丙氨酸氨基转移酶"))
  assertEquals("10^9/L",UnifiedMetricStandard.definition("WBC")?.unit)
 }
}
