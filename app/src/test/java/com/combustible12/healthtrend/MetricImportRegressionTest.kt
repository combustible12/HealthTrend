package com.combustible12.healthtrend

import org.junit.Assert.*
import org.junit.Test

/** Synthetic-only regression cases: no patient data is stored in the repository. */
class MetricImportRegressionTest {
 @Test fun explicitCodeAndChineseMeasurementConflictRequiresReview() {
  val cases=listOf(
   Triple("AST/ALT 谷丙/谷草 0.82","AST/ALT","ALT/AST"),
   Triple("CKMB/CK 肌酸激酶同工酶 2.5","CKMB/CK","CKMB"),
   Triple("NEUT# 中性粒细胞百分比 52.0 % 40-75","NEUT#","NEUT%"),
   Triple("RDW-CV 红细胞分布宽度SD 42.0 fL 35-56","RDW-CV","RDW-SD")
  )
  cases.forEach{(line,code,nameIdentity)->
   val parsed=ReportParser.parse(ReportParser.bindExplicitLeadingIdentities(line)).single()
   assertEquals(code,parsed.metricKey)
   assertEquals(nameIdentity,ReportParser.key(parsed.displayName))
   assertTrue(reconciledMetricIdentity(code,nameIdentity).startsWith("待核对:代码名称冲突:$code"))
  }
 }
 @Test fun matchingExplicitCodeAndChineseMeasurementRemainsConfirmed() {
  val parsed=ReportParser.parse(ReportParser.bindExplicitLeadingIdentities(
   "AST/ALT 谷草/谷丙 1.22"
  )).single()
  assertEquals("AST/ALT",reconciledMetricIdentity(parsed.metricKey,ReportParser.key(parsed.displayName)))
 }
 @Test fun mismatchBetweenWidthIdentityAndReportedUnitBlocksSave() {
  assertNotNull(distributionWidthIdentityProblem("PDW-CV","血小板分布宽度CV","fL"))
  assertNotNull(distributionWidthIdentityProblem("PDW-SD","血小板分布宽度SD","%"))
  assertNotNull(distributionWidthIdentityProblem("RDW-CV","红细胞分布宽度SD","%"))
  assertNull(distributionWidthIdentityProblem("PDW","血小板分布宽度","fL"))
  assertNull(distributionWidthIdentityProblem("PDW","血小板分布宽度","%"))
  assertNotNull(distributionWidthIdentityProblem("PDW","血小板分布宽度CV","%"))
  assertNull(distributionWidthIdentityProblem("PDW-CV","血小板分布宽度CV","%"))
  assertNull(distributionWidthIdentityProblem("PDW-SD","血小板分布宽度SD","fL"))
  val bad=ReportDraft(hospital="模拟医院",type="血常规",date="2026-10-16",
   rows=listOf(DraftRow(name="血小板分布宽度CV",key="PDW-CV",text="14.2",unit="fL")))
  assertFalse(bad.valid())
  assertTrue(reportValidationProblems(bad).any{it.contains("CV")&&it.contains("SD")})
 }
 @Test fun genericPdwKeepsItsIdentityAcrossLegacyAndNewEdits() {
  val result=LabResult("row","report","合成医院","血常规",4,"PDW","血小板分布宽度",12.0,"fL",8.0,18.0,1760000000000L,textValue="12.00")
  val original=LabReport("report","合成医院","血常规",1760000000000L,4,emptyList(),listOf(result))
  val draft=ReportDraft.from(original)
  assertTrue(draft.valid())
  assertTrue(draft.copy(rows=draft.rows.map{it.copy(text="13.00")}).valid())
  val changedUnit=draft.copy(rows=draft.rows.map{it.copy(unit="%")})
  assertTrue(changedUnit.valid());assertEquals("PDW",changedUnit.rows.single().key)
  assertTrue(draft.copy(existing=null).valid())
 }
 @Test fun legacyTemplateDoesNotTakeOverNewClipboardReport() {
  val source="示例医院\n血常规\n2026-10-16\nWBC 白细胞计数 5.00 10^9/L 3.5-9.5"
  val legacy=HospitalLabTemplate("旧医院","血常规",1,true,listOf(
   LabFieldTemplate("PLT","血小板计数","10^9/L",100.0,300.0)
  ))
  val draft=pastedReportDraft(source,legacy)
  assertEquals(1,draft.rows.size)
  assertEquals("WBC",draft.rows.single().key)
  assertEquals("10^9/L",draft.rows.single().unit)
  assertEquals("5.00",draft.rows.single().text)
 }
 @Test fun plateletDistributionWidthCvAndSdHaveDistinctIdentities() {
  assertEquals("PDW-CV",UnifiedMetricStandard.resolveName("血小板分布宽度CV"))
  assertEquals("PDW-SD",UnifiedMetricStandard.resolveName("血小板分布宽度SD"))
  assertEquals("PDW-CV",ReportParser.key("PDW-CV"))
  assertEquals("PDW-SD",ReportParser.key("PDW-SD"))
  assertEquals("%",UnifiedMetricStandard.definition("PDW-CV")?.unit)
  assertEquals("fL",UnifiedMetricStandard.definition("PDW-SD")?.unit)
  assertEquals("",UnifiedMetricStandard.definition("PDW")?.unit)
  assertNotEquals(canonicalMetricKey("PDW"),canonicalMetricKey("PDW-CV"))
  assertNotEquals(canonicalMetricKey("PDW-CV"),canonicalMetricKey("PDW-SD"))
 }
 @Test fun colonSeparatedInverseRatiosKeepDirections() {
  assertEquals("AST/ALT",canonicalMetricKey("AST:ALT"))
  assertEquals("ALT/AST",canonicalMetricKey("ALT:AST"))
  assertNotEquals(canonicalMetricKey("ALT:AST"),canonicalMetricKey("AST:ALT"))
  assertEquals("ALT/AST",ReportParser.key("ALT:AST"))
  assertEquals("AST/ALT",ReportParser.key("AST:ALT"))
 }
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

 @Test fun reportedUnitIsNeverReplacedByDictionaryDefault() {
  val parsed=ReportParser.parse("PDW-CV 血小板分布宽度CV 13.0 fL 9-17")
  assertEquals(1,parsed.size)
  assertEquals("PDW-CV",parsed.single().metricKey)
  assertEquals("fL",parsed.single().unit)
  val withoutUnit=ReportParser.parse("PDW-SD 血小板分布宽度SD 13.0 9-17")
  assertEquals(1,withoutUnit.size)
  assertEquals("",withoutUnit.single().unit)
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

 @Test fun pastedTemplateKeepsOriginalAliasAndOwnReferenceRange() {
  val template=HospitalLabTemplate("示例医院","血常规",1,true,listOf(
   LabFieldTemplate("WBC","白细胞计数","10^9/L",3.5,9.5)
  ))
  val pasted=listOf(ParsedLabResult("WBC","白细胞数目",4.2,"10^9/L",4.0,10.0,
   "WBC 白细胞数目 4.2 10^9/L 4-10",true,"4.2"))
  val rows=templateDrivenPastedResults(pasted,template)
  assertEquals(1,rows.size)
  assertEquals("WBC",rows.single().metricKey)
  assertEquals("白细胞数目",rows.single().displayName)
  assertEquals(4.0,rows.single().referenceLow!!,0.001)
  assertEquals(10.0,rows.single().referenceHigh!!,0.001)
 }
 @Test fun reportOriginalValueAndConvertedReferenceUseSameUnit() {
  val result=LabResult("x","report","示例医院","生化",null,"CREA","血肌酐",
   0.8,"mg/dL",0.6,1.1,1L,textValue="0.8")
  assertEquals(70.72,UnitNormalizer.normalize(result.metricKey,result.value,result.unitAtTest).first!!,0.001)
  val bounds=result.trendReferenceRange()
  assertEquals(53.04,bounds.first!!,0.001)
  assertEquals(97.24,bounds.second!!,0.001)
  assertEquals("0.8",result.textValue)
  assertEquals("mg/dL",result.unitAtTest)
 }

 @Test fun typeCatalogueContainsStandardMetricsWithoutHospitalReports() {
  assertTrue("WBC" in UnifiedMetricStandard.keysForType("血常规"))
  assertTrue("ALT" in UnifiedMetricStandard.keysForType("生化"))
  assertTrue("ALT" in UnifiedMetricStandard.keysForType("肝功能"))
  assertTrue("CA125" in UnifiedMetricStandard.keysForType("肿瘤标志物"))
  assertTrue("TSH" in UnifiedMetricStandard.keysForType("甲状腺功能"))
  assertTrue("CTNI" in UnifiedMetricStandard.keysForType("心肌"))
  assertFalse("NEUT#" in UnifiedMetricStandard.keysForType("凝血"))
 }
 @Test fun metricRegistryDoesNotMergeSimilarButDifferentIdentities() {
  assertEquals("CRP",UnifiedMetricStandard.resolveName("C反应蛋白"))
  assertEquals("HSCRP",UnifiedMetricStandard.resolveName("超敏C反应蛋白"))
  assertEquals("PCT",UnifiedMetricStandard.resolveName("血小板压积"))
  assertEquals("PCTIN",UnifiedMetricStandard.resolveName("降钙素原"))
  assertNotEquals("AST/ALT","ALT/AST")
  assertEquals("AST/ALT",UnifiedMetricStandard.resolveName("谷草/谷丙"))
  assertEquals("ALT/AST",UnifiedMetricStandard.resolveName("谷丙/谷草"))
 }

 @Test fun newReportReviewCannotBeBypassedByLegacyTemplate() {
  val legacy=HospitalLabTemplate("示例医院","血常规",1,true,listOf(
   LabFieldTemplate("WBC","白细胞计数","10^9/L",3.5,9.5)
  ))
  val invalid=ParsedLabResult("WBC","白细胞计数",4.2,"10^9/L",12.0,2.0,
   "WBC 白细胞计数 4.2 10^9/L 12-2",true,"4.2")
  assertTrue(metricNeedsReview(invalid,null))
  assertTrue(metricNeedsReview(invalid,legacy))
 }
 @Test fun newReportReviewAcceptsItsOwnValidRangeWithOrWithoutLegacyTemplate() {
  val legacy=HospitalLabTemplate("示例医院","血常规",1,true,listOf(
   LabFieldTemplate("WBC","白细胞计数","10^9/L",3.5,9.5)
  ))
  val current=ParsedLabResult("WBC","白细胞计数",4.2,"10^9/L",4.0,10.0,
   "WBC 白细胞计数 4.2 10^9/L 4-10",true,"4.2")
  assertFalse(metricNeedsReview(current,null))
  assertFalse(metricNeedsReview(current,legacy))
 }

 @Test fun reportCannotBeSavedWithoutExaminationType() {
  val draft=ReportDraft(hospital="模拟医院",type=" ",date="2026-10-15",
   rows=listOf(DraftRow(name="白细胞计数",key="WBC",text="5.1",unit="10^9/L")))
  assertFalse(draft.valid())
  assertTrue(reportValidationProblems(draft).any{it.contains("检查类型")})
 }
 @Test fun missingMeasurementDateRemainsOnTrendTimeline() {
  fun report(id:String,date:Long)=LabReport(id,"模拟医院","血常规",date,null,emptyList(),emptyList())
  val old=report("first",1000L)
  val missing=report("missing",2000L)
  val latest=report("latest",3000L)
  assertEquals(listOf(1000L,2000L,3000L),trendTimelineDates(listOf(old,missing,latest),listOf(old,latest),0L))
 }
 @Test fun canonicalIdentityDoesNotGuessDifferentMeasurements() {
  assertNotEquals(canonicalMetricKey("NEUT#"),canonicalMetricKey("NEUT%"))
  assertNotEquals(canonicalMetricKey("RDW-CV"),canonicalMetricKey("RDW-SD"))
  assertNotEquals(canonicalMetricKey("AST/ALT"),canonicalMetricKey("ALT/AST"))
  assertNotEquals(canonicalMetricKey("PCT"),canonicalMetricKey("PCTIN"))
  assertNotEquals(canonicalMetricKey("CUSTOM-EXAMPLE"),canonicalMetricKey("WBC"))
 }
 @Test fun detailedRowValidationIdentifiesBadBounds() {
  val draft=ReportDraft(hospital="模拟医院",type="生化",date="2026-10-14",
   rows=listOf(DraftRow(name="肌酐",key="CREA",text="1.0",unit="mg/dL",low="2",high="1")))
  assertTrue(reportValidationProblems(draft).any{it.contains("第1项")&&it.contains("下限大于上限")})
 }
 @Test fun standardDictionaryKeepsOppositeRatiosSeparate() {
  assertEquals("AST/ALT",UnifiedMetricStandard.resolveName("谷草/谷丙"))
  assertEquals("ALT/AST",UnifiedMetricStandard.resolveName("谷丙/谷草"))
 }
 @Test fun unifiedRangeIdentityNeverContainsHospitalOrReportType() {
  val fields=HealthStore.ObservedReferenceRange::class.java.declaredFields.map{it.name}.toSet()
  assertFalse("Hospital does not belong in metric dictionary ranges", "hospitalKey" in fields)
  assertFalse("Report type does not belong in metric dictionary ranges", "reportType" in fields)
  val range=HealthStore.ObservedReferenceRange("WBC","10^9/L",3.5,9.5,0L,0L,0)
  assertEquals("WBC",range.metricKey)
  assertEquals(3.5,range.lower!!,0.0)
  assertEquals(9.5,range.upper!!,0.0)
 }

}
