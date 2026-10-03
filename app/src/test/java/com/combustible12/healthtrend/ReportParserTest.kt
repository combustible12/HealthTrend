package com.combustible12.healthtrend
import org.junit.Assert.*
import org.junit.Test

class ReportParserTest {
 @Test fun patientProfilePersistsIndependentlyFromClinicalRecords(){
  // Model-level contract: patient identity can exist without becoming report metadata.
  val p=PatientProfile(name="患者甲",birthDate="1972-05-06",sex="女",note="")
  val raw="采样日期：2026-09-26\nHGB 102 g/L 113-151"
  val meta=ReportMetadata.extract(raw,ReportParser.parse(raw))
  assertEquals("1972-05-06",p.birthDate);assertEquals("2026-09-26",meta.date);assertNotEquals(p.birthDate,meta.date)
 }
 @Test fun allMetricsAndPrintedRangesAreRetained(){
  val rows=ReportParser.parse("WBC 3.75 ×10^9/L 3.5-9.5\nNEUT% 51.2 % 40-75\nNEUT# 1.92 ×10^9/L 2-7\nLDH 189 U/L 120-250\nHGB 102 g/L 113-151")
  assertEquals(5,rows.size);assertEquals("LDH",rows[3].metricKey);assertEquals(120.0,rows[3].referenceLow!!,0.0);assertEquals(51.2,rows[1].value!!,0.0);assertFalse(rows[1].primary);assertEquals("NEUT#",rows[2].metricKey)
 }
 @Test fun digitInMetricNameIsNotAValue(){val x=ReportParser.parse("VitaminB12 阴性").single();assertEquals("阴性",x.textValue);assertNull(x.value);val n=ReportParser.parse("B12 130 100-600 pg/mL").single();assertEquals(130.0,n.value!!,0.0);assertEquals("pg/mL",n.unit)}
 @Test fun ocrConcatenationStillFindsResult(){val c=ReportParser.parse("白细胞计数3.75 3.5-9.5").single();assertEquals("WBC",c.metricKey);assertEquals(3.75,c.value!!,0.0);val e=ReportParser.parse("WBC3.75 3.5-9.5").single();assertEquals("WBC",e.metricKey);assertEquals(3.75,e.value!!,0.0)}
 @Test fun neverInventsReferenceRanges(){val x=ReportParser.parse("WBC 3.75").single();assertNull(x.referenceLow);assertNull(x.referenceHigh);assertEquals("",x.unit)}
 @Test fun unknownTextAndComparatorsAreNotDropped(){val rows=ReportParser.parse("HBsAg 阴性\nCRP <0.5 mg/L <5");assertEquals(2,rows.size);assertNull(rows[0].value);assertEquals("阴性",rows[0].textValue);assertEquals("<",rows[1].comparator);assertEquals(5.0,rows[1].referenceHigh!!,0.0)}
 @Test fun rowNumberAndScientificNotation(){val x=ReportParser.parse("1 WBC 3.75 ×10^9/L 3.5-9.5").single();assertEquals("WBC",x.metricKey);assertEquals(3.75,x.value!!,0.0)}
 @Test fun ChineseNamesAndAbbreviations(){assertEquals("WBC",ReportParser.key("白细胞计数(WBC)"));assertEquals("NEUT%",ReportParser.key("中性粒细胞百分比"));assertEquals("CREA",ReportParser.key("CRE"))}
 @Test fun editingDoesNotRoundOriginalTimestamp(){val original=1672531200123L;assertEquals(original,preserveTimestamp(dateText(original),original));val edited="2026-09-26 09:30";assertEquals(parseDate(edited),preserveTimestamp(edited,original));assertNull(preserveTimestamp("",null))}
 @Test fun historicalStatusAndEditedValue(){val r=LabResult("1","r","h","血常规",1,"HGB","血红蛋白",102.0,"g/L",113.0,151.0,1L);assertEquals(ResultStatus.LOW,r.status());val edit=r.withEditedValue(130.0);assertEquals(ResultStatus.NORMAL,edit.status());assertEquals(113.0,edit.referenceLowAtTest!!,0.0);assertTrue(edit.editedByUser);assertEquals(ResultStatus.UNKNOWN,r.copy(referenceLowAtTest=null,referenceHighAtTest=null).status())}

 @Test fun trendBoundsUseTheValuesUnitWithoutChangingHistory(){
  val crea=LabResult("c","r","h","肾功能",1,"CREA","肌酐",1.0,"mg/dL",0.6,1.2,1L,normalizedValue=88.4,normalizedUnit="μmol/L")
  val bounds=crea.trendReferenceRange()
  assertEquals(53.04,bounds.first!!,0.000001);assertEquals(106.08,bounds.second!!,0.000001)
  assertEquals(0.6,crea.referenceLowAtTest!!,0.0);assertEquals(1.2,crea.referenceHighAtTest!!,0.0)
  assertEquals(ResultStatus.NORMAL,crea.status())
  val albumin=crea.copy(metricKey="ALB",unitAtTest="g/dL",referenceLowAtTest=3.5,referenceHighAtTest=5.2)
  assertEquals(35.0,albumin.trendReferenceRange().first!!,0.000001)
  assertEquals(52.0,albumin.trendReferenceRange().second!!,0.000001)
  assertEquals(null to null,crea.copy(referenceLowAtTest=null,referenceHighAtTest=null).trendReferenceRange())
 }
 @Test fun sharedSymptomReportContainsRealLineBreaksAndEachEvent(){
  val entries=listOf(HealthEntry(kind=EntryKind.SYMPTOM,title="小腿酸痛",occurredAtEpochMillis=1000L,severity=4,note="晚上明显"))
  val text=symptomReportText("2026-09-01","2026-09-30",entries)
  assertTrue(text.startsWith("症状报告 2026-09-01 至 2026-09-30\n记录 1 次\n"))
  assertTrue(text.contains("小腿酸痛：1 次"))
  assertTrue(text.contains("晚上明显\n"));assertFalse(text.contains("\\n"))
 }
 @Test fun metricReviewRuleTrustsConfirmedTemplateButFlagsIncompleteNewRows(){
  val complete=ParsedLabResult("HGB","血红蛋白",102.0,"g/L",113.0,151.0,"HGB 102",true)
  assertFalse(metricNeedsReview(complete,null))
  assertTrue(metricNeedsReview(complete.copy(unit=""),null))
  assertTrue(metricNeedsReview(complete.copy(referenceHigh=null),null))
  val template=HospitalLabTemplate(hospitalKey="医院",reportType="血常规",version=1,confirmed=true,fields=listOf(LabFieldTemplate("HGB","血红蛋白","g/L",113.0,151.0)))
  assertFalse(metricNeedsReview(complete.copy(unit="",referenceLow=null,referenceHigh=null),template))
 }
 @Test fun patientProfileIsIndependentFromReportMetadata(){
  val profile=PatientProfile(name="患者",birthDate="1972-05-06",sex="女")
  assertEquals("1972-05-06",profile.birthDate)
  val meta=ReportMetadata.extract("采样时间：2026-09-26\nHGB 102 g/L 113-151",ReportParser.parse("HGB 102 g/L 113-151"))
  assertEquals("2026-09-26",meta.date);assertFalse(meta.date==profile.birthDate)
 }
 @Test fun reportDateIgnoresPatientBirthDateWhenClinicalDateExists(){
  val raw="出生日期：1972-05-06\n采样时间：2026-09-26 09:30\nHGB 102 g/L 113-151"
  val meta=ReportMetadata.extract(raw,ReportParser.parse(raw));assertEquals("2026-09-26",meta.date)
 }
 @Test fun demographicDateDoesNotBlockSingleUnlabelledReportDateFallback(){
  val raw="出生日期：1972-05-06\n2026-09-26\nHGB 102 g/L 113-151"
  val meta=ReportMetadata.extract(raw,ReportParser.parse(raw))
  assertEquals("2026-09-26",meta.date)
 }
 @Test fun patientBirthDateAloneIsNotAcceptedAsReportDate(){
  val raw="出生日期：1972-05-06\nHGB 102 g/L 113-151"
  val meta=ReportMetadata.extract(raw,ReportParser.parse(raw));assertEquals("",meta.date);assertTrue("date" in meta.uncertain)
 }
 @Test fun trendPointPositionSeparatesFirstAndLastPointInMultiPointSeries(){
  val points=listOf(0L to 102.0,86400000L to 120.0)
  val first=trendPointPosition(points,0,320f,112f,100.0,151.0)
  val last=trendPointPosition(points,1,320f,112f,100.0,151.0)
  assertTrue(first.x<last.x);assertTrue(first!=last)
  assertEquals(0,nearestTrendPoint(points,first,320f,112f,100.0,151.0,1f))
  assertEquals(1,nearestTrendPoint(points,last,320f,112f,100.0,151.0,1f))
 }
 @Test fun trendPointPositionMatchesHitTestingForEveryPoint(){
  val points=listOf(0L to 102.0,86400000L to 120.0,172800000L to 108.0)
  points.indices.forEach{index->
   val p=trendPointPosition(points,index,320f,112f,100.0,151.0)
   assertEquals(index,nearestTrendPoint(points,p,320f,112f,100.0,151.0,1f))
  }
 }
 @Test fun trendPointHitTestingCoversEdgesAndInvalidGeometry(){
  val points=listOf(0L to 100.0,100L to 120.0)
  // First point is at x=8, y=88 for this geometry; radius boundary is inclusive.
  assertEquals(0,nearestTrendPoint(points,androidx.compose.ui.geometry.Offset(28f,88f),200f,100f,100.0,120.0,20f))
  assertNull(nearestTrendPoint(points,androidx.compose.ui.geometry.Offset(28.1f,88f),200f,100f,100.0,120.0,20f))
  assertNull(nearestTrendPoint(points,androidx.compose.ui.geometry.Offset.Zero,0f,100f,radius=48f))
  assertNull(nearestTrendPoint(points,androidx.compose.ui.geometry.Offset.Zero,100f,0f,radius=48f))
  assertNull(nearestTrendPoint(points,androidx.compose.ui.geometry.Offset.Zero,100f,100f,radius=-1f))
 }
 @Test fun trendPointHitTestingHonorsNearestPointAndRadius(){
  val points=listOf(0L to 100.0,100L to 120.0)
  val first=nearestTrendPoint(points,androidx.compose.ui.geometry.Offset(8f,88f),200f,100f,100.0,120.0,20f)
  assertEquals(0,first)
  assertNull(nearestTrendPoint(points,androidx.compose.ui.geometry.Offset(100f,0f),200f,100f,100.0,120.0,10f))
  assertEquals(0,nearestTrendPoint(listOf(5L to 110.0),androidx.compose.ui.geometry.Offset(100f,50f),200f,100f,100.0,120.0,50f))
  assertNull(nearestTrendPoint(emptyList(),androidx.compose.ui.geometry.Offset.Zero,200f,100f,radius=50f))
 }
 @Test fun editingTrendValuePreservesHistoricalRangeAndRecalculatesStatus(){
  val original=LabResult("p","r","医院","血常规",1,"HGB","血红蛋白",102.0,"g/L",113.0,151.0,1L)
  val edited=original.withEditedValue(120.0)
  assertEquals(120.0,edited.value!!,0.0);assertEquals(ResultStatus.NORMAL,edited.status())
  assertEquals(113.0,edited.referenceLowAtTest!!,0.0);assertEquals(151.0,edited.referenceHighAtTest!!,0.0)
  assertEquals("g/L",edited.normalizedUnit);assertEquals(120.0,edited.normalizedValue!!,0.0);assertTrue(edited.editedByUser)
 }
 @Test fun unconfirmedTemplateNeverSuppressesOcrReview(){
  val row=ParsedLabResult("HGB","血红蛋白",102.0,"",null,null,"HGB 102",true)
  val draftTemplate=HospitalLabTemplate("医院","血常规",1,false,listOf(LabFieldTemplate("HGB","血红蛋白","g/L",113.0,151.0)))
  assertTrue(metricNeedsReview(row,draftTemplate))
  assertFalse(metricNeedsReview(row,draftTemplate.copy(confirmed=true)))
 }
 @Test fun templateReviewDoesNotTrustUnknownMetricJustBecauseTemplateExists(){
  val known=ParsedLabResult("HGB","血红蛋白",102.0,"g/L",113.0,151.0,"HGB 102",true)
  val template=HospitalLabTemplate("医院","血常规",1,true,listOf(LabFieldTemplate("HGB","血红蛋白","g/L",113.0,151.0)))
  val unknown=ParsedLabResult("NEW","新指标",1.2,"",null,null,"NEW 1.2",false)
  assertFalse(metricNeedsReview(known.copy(unit="",referenceLow=null,referenceHigh=null),template))
  assertTrue(metricNeedsReview(unknown,template))
 }
 @Test fun editedTrendValueRecomputesStatusButKeepsHistoricalRange(){
  val x=LabResult(rawName="HGB",metricKey="HGB",textValue="102",value=102.0,unitAtTest="g/L",referenceLowAtTest=113.0,referenceHighAtTest=151.0)
  val edited=x.withEditedValue(120.0);assertEquals(120.0,edited.value!!,0.0);assertEquals(113.0,edited.referenceLowAtTest!!,0.0);assertEquals(151.0,edited.referenceHighAtTest!!,0.0);assertEquals(ResultStatus.NORMAL,edited.status());assertTrue(edited.editedByUser)
 }
 @Test fun reportValidationNamesEveryRemainingOcrBlocker(){
  val row=DraftRow(name="HGB",key="HGB",text="102",unit="g/L",low="113",high="151",uncertain=true)
  val d=ReportDraft(hospital="医院",type="血常规",date="2026-09-26",rows=listOf(row),uncertain=setOf("hospital"))
  val p=reportValidationProblems(d);assertTrue("医院待人工确认" in p);assertTrue("第1项尚未完成核对" in p)
  assertEquals(emptyList<String>(),reportValidationProblems(d.copy(uncertain=emptySet(),rows=listOf(row.copy(uncertain=false)))))
 }
 @Test fun reviewedValidMetricCanClearUncertaintyWithoutChangingItsData(){
  val row=DraftRow(name="HGB",key="HGB",text="102",unit="g/L",low="113",high="151",uncertain=true)
  assertTrue(row.valid());val reviewed=row.copy(uncertain=false);assertFalse(reviewed.uncertain);assertEquals(row.parsed(),reviewed.parsed())
 }
 @Test fun uncertainMetricRowRequiresExplicitReview(){
  val row=DraftRow(name="未知指标",text="1.2",raw="未知指标 1.2",uncertain=true)
  val draft=ReportDraft(hospital="测试医院",type="血常规",date="2026-09-26",rows=listOf(row))
  assertFalse(draft.valid())
  assertTrue(draft.copy(rows=listOf(row.copy(key="UNKNOWN",uncertain=false))).valid())
 }
 @Test fun uncertainMetadataBlocksSaveUntilConfirmed(){
  val row=DraftRow(name="血红蛋白",key="HGB",text="102",unit="g/L",low="113",high="151")
  val uncertain=ReportDraft(hospital="福建省妇幼保健院",type="血常规",date="2026-09-26",rows=listOf(row),uncertain=setOf("type"))
  assertFalse(uncertain.valid());assertTrue(uncertain.copy(uncertain=emptySet()).valid())
 }
 @Test fun metadataDateRequiresDisambiguationWhenMultipleUnlabelledDatesExist(){
  val rows=ReportParser.parse("HGB 102 g/L 113-151")
  val ambiguous=ReportMetadata.extract("2026-09-20\\n2026-09-26\\nHGB 102",rows)
  assertTrue(ambiguous.date.isBlank());assertTrue(ambiguous.uncertain.contains("date"))
  val labelled=ReportMetadata.extract("出生日期 1972-01-01\\n采样日期 2026-09-26\\nHGB 102",rows)
  assertEquals("2026-09-26",labelled.date)
 }
 @Test fun metadataDateRejectsUnrelatedNumbersAndPadsSingleDigits(){
  val rows=ReportParser.parse("HGB 102 g/L 113-151")
  assertEquals("2026-09-06",ReportMetadata.extract("报告日期：2026.9.6\\nHGB 102",rows).date)
  val missing=ReportMetadata.extract("患者号 20260926\\nHGB 102",rows)
  assertTrue(missing.date.isBlank());assertTrue(missing.uncertain.contains("date"))
 }
 @Test fun metadataHospitalParsingNeverConsumesFollowingDateLine(){
  val rows=ReportParser.parse("HGB 102 g/L 113-151")
  val meta=ReportMetadata.extract("医疗机构：福建省妇幼保健院\n2026-09-26\nHGB 102",rows)
  assertEquals("福建省妇幼保健院",meta.hospital)
  assertEquals("2026-09-26",meta.date)
 }
 @Test fun metadataHospitalLabelAllowsColonWhitespaceOrNoSeparator(){
  val rows=ReportParser.parse("WBC 4.0 ×10^9/L 3.5-9.5\nHGB 120 g/L 113-151")
  listOf("医疗机构：福建省妇幼保健院","医疗机构: 福建省妇幼保健院","医疗机构 福建省妇幼保健院").forEach{raw->
   assertEquals("福建省妇幼保健院",ReportMetadata.extract(raw+"\n2026-09-26",rows).hospital)
  }
 }
 @Test fun metadataExtractionDoesNotSilentlyTrustMissingIdentityOrDate(){
  val rows=ReportParser.parse("HGB 102 g/L 113-151")
  val meta=ReportMetadata.extract("HGB 102 g/L 113-151",rows)
  assertTrue(meta.uncertain.contains("hospital"));assertTrue(meta.uncertain.contains("date"));assertTrue(meta.uncertain.contains("type"))
  assertTrue(meta.hospital.isBlank());assertTrue(meta.date.isBlank())
 }
 @Test fun metadataExtractionCoversLiverKidneyAndExplicitDateFormats(){
  val liver=ReportParser.parse("ALT 23 U/L 7-40\nAST 20 U/L 13-35\nALB 42.9 g/L 40-55")
  val liverMeta=ReportMetadata.extract("医疗机构 福建省肿瘤医院\n采样日期：2026/09/23\nALT 23\nAST 20\nALB 42.9",liver)
  assertEquals("福建省肿瘤医院",liverMeta.hospital);assertEquals("2026-09-23",liverMeta.date);assertEquals("肝功能",liverMeta.reportType);assertTrue(liverMeta.uncertain.contains("type"))
  val kidney=ReportParser.parse("CREA 46 umol/L 35-80\nUREA 5.2 mmol/L 1.43-7.14\nUA 328 umol/L 90-357")
  val kidneyMeta=ReportMetadata.extract("福建省妇幼保健院\n肾功能检验报告\n报告日期 2026年9月25日",kidney)
  assertEquals("肾功能",kidneyMeta.reportType);assertFalse(kidneyMeta.uncertain.contains("type"));assertEquals("2026-09-25",kidneyMeta.date)
 }
 @Test fun metadataExtractionPrefersClinicalLabelsAndInfersPanels(){
  val blood=ReportParser.parse("WBC 3.75 ×10^9/L 3.5-9.5\nHGB 102 g/L 113-151")
  val meta=ReportMetadata.extract("姓名 张三\n医疗机构：福建省妇幼保健院\n采样时间 2026年9月26日 08:30\nWBC 3.75\nHGB 102",blood)
  assertEquals("福建省妇幼保健院",meta.hospital);assertEquals("2026-09-26",meta.date);assertEquals("血常规",meta.reportType)
  assertTrue("inferred type must be confirmed",meta.uncertain.contains("type"))
  val explicit=ReportMetadata.extract("福建省肿瘤医院\n肿瘤标志物检验报告\n报告日期：2026/09/29",emptyList())
  assertEquals("肿瘤标志物",explicit.reportType);assertFalse(explicit.uncertain.contains("type"))
 }
}
