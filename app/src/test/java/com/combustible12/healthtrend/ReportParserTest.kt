package com.combustible12.healthtrend
import androidx.compose.ui.geometry.Offset
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
 @Test fun neverInventsReferenceRanges(){val x=ReportParser.parse("WBC 3.75").single();assertNull(x.referenceLow);assertNull(x.referenceHigh);assertEquals("×10^9/L",x.unit)}
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
 @Test fun trendPointAccessibilityIdentityKeepsMetricAndExactVisit(){
  val first=requireNotNull(parseDate("2026-09-25 00:00"));val last=requireNotNull(parseDate("2026-09-26 00:00"))
  assertEquals("趋势点 HGB 2026-09-25 00:00",trendPointContentDescription("HGB",first))
  assertEquals("趋势点 HGB 2026-09-26 00:00",trendPointContentDescription("HGB",last))
  assertNotEquals(trendPointContentDescription("HGB",first),trendPointContentDescription("HGB",last))
 }
 @Test fun editedTrendValueRecomputesStatusButKeepsHistoricalRange(){
  val x=LabResult(id="x",reportId="r",hospitalKey="医院",reportType="血常规",templateVersion=1,metricKey="HGB",rawName="HGB",value=102.0,unitAtTest="g/L",referenceLowAtTest=113.0,referenceHighAtTest=151.0,testedAtEpochMillis=1L,textValue="102")
  val edited=x.withEditedValue(120.0);assertEquals(120.0,edited.value!!,0.0);assertEquals(113.0,edited.referenceLowAtTest!!,0.0);assertEquals(151.0,edited.referenceHighAtTest!!,0.0);assertEquals(ResultStatus.NORMAL,edited.status());assertTrue(edited.editedByUser)
 }
 @Test fun blankOcrMetadataCorrectionCannotBypassSaveGate(){
  val row=DraftRow(name="HGB",key="HGB",text="102",unit="g/L",low="113",high="151",uncertain=false)
  val base=ReportDraft(hospital="医院",type="血常规",date="2026-09-26 09:30",rows=listOf(row),uncertain=emptySet())
  val blankHospital=updateOcrMetadata(base,"hospital","");assertTrue("hospital" in blankHospital.uncertain);assertFalse(blankHospital.valid());assertFalse(blankHospital.valid())
  val badDate=updateOcrMetadata(base,"date","not-a-date");assertTrue("date" in badDate.uncertain);assertFalse(badDate.valid());assertFalse(badDate.valid())
 }
 @Test fun ocrMetadataOnlyClearsUncertaintyAfterValidCorrection(){
  val d=ReportDraft(hospital="",type="",date="",rows=listOf(DraftRow(name="HGB",key="HGB",text="102",unit="g/L",low="113",high="151")),uncertain=setOf("hospital","type","date"))
  val invalidDate=updateOcrMetadata(updateOcrMetadata(updateOcrMetadata(d,"hospital","医院"),"type","血常规"),"date","2026-99-99")
  assertEquals(setOf("date"),invalidDate.uncertain);assertFalse(invalidDate.valid())
  val confirmed=updateOcrMetadata(invalidDate,"date","2026-09-26 09:30");assertTrue(confirmed.uncertain.isEmpty());assertTrue(confirmed.valid())
 }
 @Test fun reviewedValidMetricCanClearUncertaintyWithoutChangingItsData(){
  val row=DraftRow(name="HGB",key="HGB",text="102",unit="g/L",low="113",high="151",uncertain=true)
  assertTrue(row.valid());val reviewed=row.copy(uncertain=false);assertFalse(reviewed.uncertain);assertEquals(row.parsed(),reviewed.parsed())
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

 @Test fun mergedOcrLineSplitsMetricsWithoutCrossContamination(){
  val rows=ReportParser.parse("1 WBC 白细胞 7.25 3.5--9.5 10 9/L 22 MPV 平均血小板体积 9.1 6.5--12 fL")
  assertEquals(2,rows.size);assertEquals("WBC",rows[0].metricKey);assertEquals(7.25,rows[0].value!!,0.0);assertEquals(3.5,rows[0].referenceLow!!,0.0);assertEquals(9.5,rows[0].referenceHigh!!,0.0);assertEquals("×10^9/L",rows[0].unit)
  assertEquals("MPV",rows[1].metricKey);assertEquals(9.1,rows[1].value!!,0.0);assertEquals(6.5,rows[1].referenceLow!!,0.0);assertEquals(12.0,rows[1].referenceHigh!!,0.0);assertEquals("fL",rows[1].unit)
 }
 @Test fun reportDateBeatsSamplingAndAdministrativeDates(){
  val rows=ReportParser.parse("HGB 102 g/L 113-151")
  val meta=ReportMetadata.extract("采样时间 2026-09-25 08:00\n送检时间 2026-09-25 09:00\n审核时间 2026-09-26 10:00\n报告日期 2026-09-27\nHGB 102",rows)
  assertEquals("2026-09-27",meta.date)
 }
 @Test fun samplingDateIsOnlyClinicalFallback(){
  val rows=ReportParser.parse("HGB 102 g/L 113-151")
  val meta=ReportMetadata.extract("采样日期 2026-09-25\n审核时间 2026-09-26\nHGB 102",rows)
  assertEquals("2026-09-25",meta.date)
 }
 @Test fun canonicalUnitsRepairMissingOrCorruptOcrUnits(){
  val wbc=ReportParser.parse("WBC 7.25 3.5-9.5 22 MPV").single();assertEquals("×10^9/L",wbc.unit);assertEquals(3.5,wbc.referenceLow!!,0.0);assertEquals(9.5,wbc.referenceHigh!!,0.0)
  assertEquals("fL",ReportParser.parse("MPV 9.1 6.5-12").single().unit)
  assertEquals("g/L",ReportParser.parse("HGB 102 garbage 113-151").single().unit)
 }
 @Test fun reportSaveGateOnlyRequiresHospitalDateAndValidMetrics(){
  val row=DraftRow(name="HGB",key="HGB",text="102",unit="g/L",low="113",high="151",uncertain=true)
  val d=ReportDraft(hospital="医院",type="",date="2026-09-26",rows=listOf(row),uncertain=setOf("type"))
  assertTrue(d.valid());assertTrue(reportValidationProblems(d).isEmpty())
 }

 @Test fun trendPointDescriptionsRemainOneToOneWithVisits(){
  val visits=listOf(requireNotNull(parseDate("2026-09-25 00:00")),requireNotNull(parseDate("2026-09-26 00:00")))
  val descriptions=visits.map{trendPointContentDescription("HGB",it)}
  assertEquals(2,descriptions.distinct().size)
  assertEquals("趋势点 HGB 2026-09-25 00:00",descriptions[0])
  assertEquals("趋势点 HGB 2026-09-26 00:00",descriptions[1])
 }

 @Test fun trendHitTestingRejectsTapBetweenSeparatedVisits(){
  val points=listOf(requireNotNull(parseDate("2026-09-25 00:00")) to 102.0,requireNotNull(parseDate("2026-09-26 00:00")) to 120.0)
  val width=900f;val height=112f;val radius=24f
  val first=trendPointPosition(points,0,width,height,113.0,151.0);val last=trendPointPosition(points,1,width,height,113.0,151.0)
  val middle=Offset((first.x+last.x)/2f,(first.y+last.y)/2f)
  assertNull(nearestTrendPoint(points,middle,width,height,113.0,151.0,radius))
  assertEquals(0,nearestTrendPoint(points,first,width,height,113.0,151.0,radius))
  assertEquals(1,nearestTrendPoint(points,last,width,height,113.0,151.0,radius))
 }

 @Test fun trendTapMovementAtTouchSlopBoundaryIsDeterministic(){
  fun isTap(dx:Float,dy:Float,slop:Float)=dx*dx+dy*dy<=slop*slop
  assertTrue(isTap(8f,6f,10f))
  assertFalse(isTap(8.1f,6f,10f))
  assertTrue(isTap(0f,0f,10f))

 }
@Test fun oneSidedRangesAndValidAlternativeUnitsArePreserved(){
 val lt=ReportParser.parse("CRP 0.5 mg/L <5").single();assertEquals(5.0,lt.referenceHigh!!,0.0);assertNull(lt.referenceLow)
 val ge=ReportParser.parse("CRP 12 mg/L ≥10").single();assertEquals(10.0,ge.referenceLow!!,0.0);assertNull(ge.referenceHigh)
 val hgb=ReportParser.parse("HGB 10.2 g/dL 8.0-15.0").single();assertEquals("g/dL",hgb.unit);assertEquals(10.2,hgb.value!!,0.0)
}

 @Test fun administrativeMetadataNeverBecomesLabMetrics(){
  val raw="姓名 张三\n年龄 54\n性别 女\n样本号 12345\n标本号 A889\n条码号 998877\n病历号 M123\n住院号 Z456\n门诊号 O789\n床号 12\n科室 肿瘤科\n诊断 随访\n医生 李医生\n审核人 王医生\n送检时间 2026-09-25 09:00\n打印时间 2026-09-26 10:00\nWBC 7.25 ×10^9/L 3.5-9.5\nHGB 102 g/L 113-151"
  val rows=ReportParser.parse(raw)
  assertEquals(listOf("WBC","HGB"),rows.map{it.metricKey})
 }
 @Test fun allOneSidedReferenceOperatorsAreParsed(){
  assertEquals(5.0,ReportParser.parse("CRP 0.5 mg/L ≤5").single().referenceHigh!!,0.0)
  assertEquals(10.0,ReportParser.parse("CRP 12 mg/L >10").single().referenceLow!!,0.0)
 }

 @Test fun realSingleDigitDatesNormalizeAndSaveWithoutTime(){
  listOf("2016-9-18","2016-09-18","2016/9/18","2016/09/18").forEach{input->
   assertEquals("2016-09-18",normalizeDateText(input));assertNotNull(parseDate(input))
   val draft=updateOcrMetadata(ReportDraft(hospital="医院",date=input,rows=listOf(DraftRow(name="WBC",key="WBC",text="7.25",unit="×10^9/L",low="3.5",high="9.5"))),"date",input)
   assertEquals("2016-09-18",draft.date);assertTrue(draft.valid())
  }
  assertEquals("2016-09-18 09:30",normalizeDateText("2016-9-18 09:30"))
  assertEquals("2016-09-18 09:30",normalizeDateText("2016-09-18 09:30"))
 }

 @Test fun administrativeDatesNeverBecomeTheReportDate(){
  val rows=ReportParser.parse("WBC 7.25 3.5-9.5 10^9/L")
  listOf("送检时间 2016-9-18 09:30","审核时间 2016-9-18 09:30","打印时间 2016-9-18 09:30","出生日期 1972-5-6").forEach{raw->
   assertEquals(raw,"",ReportMetadata.extract("$raw\nWBC 7.25",rows).date)
  }
  val preferred=ReportMetadata.extract("送检时间 2016-9-17 09:30\n检查日期 2016-9-18\n打印时间 2016-9-19 09:30",rows)
  assertEquals("2016-09-18",preferred.date)
 }

 @Test fun realWbcOcrKeepsDecimalsAndPrintedRange(){
  val row=ReportParser.parse("WBC 白细胞 7.25 3.5--9.5 10 9/L").single()
  assertEquals("WBC",row.metricKey);assertEquals(7.25,row.value!!,0.0);assertEquals(3.5,row.referenceLow!!,0.0);assertEquals(9.5,row.referenceHigh!!,0.0);assertEquals("×10^9/L",row.unit)
  val spaced=ReportParser.parse("WBC 白细胞 7 . 25 3 . 5 — 9 . 5 10 9/L").single()
  assertEquals(7.25,spaced.value!!,0.0);assertEquals(3.5,spaced.referenceLow!!,0.0);assertEquals(9.5,spaced.referenceHigh!!,0.0)
 }

 @Test fun everyPrintedRangeSeparatorKeepsBothDecimalBounds(){
  listOf("3.5-9.5","3.5--9.5","3.5–9.5","3.5—9.5","3.5~9.5","3.5～9.5","3.5 至 9.5").forEach{range->
   val row=ReportParser.parse("WBC 7.25 $range 10^9/L").single()
   assertEquals(range,3.5,row.referenceLow!!,0.0);assertEquals(range,9.5,row.referenceHigh!!,0.0);assertTrue(range,row.referenceLow!!<=row.referenceHigh!!)
  }
 }

 @Test fun differentialPrefixAndSuffixAliasesShareKeysButKeepPrintedNames(){
  val cases=listOf(
   "#NEUT 中性粒细胞计数 5.77 2.0-7.0 10^9/L" to "NEUT#","NEUT# 中性粒细胞绝对值 5.77 2.0-7.0 10^9/L" to "NEUT#","%NEUT 中性粒细胞百分比 71.2 40-75 %" to "NEUT%","NEUT% 中性粒细胞百分比 71.2 40-75 %" to "NEUT%",
   "#LYMPH 淋巴细胞计数 1.2 0.8-4.0 10^9/L" to "LYMPH#","%LYMPH 淋巴细胞百分比 20 20-50 %" to "LYMPH%","#MONO 单核细胞计数 0.4 0.1-0.8 10^9/L" to "MONO#","%MONO 单核细胞百分比 5 3-10 %" to "MONO%",
   "#EOS 嗜酸性粒细胞计数 0.2 0.02-0.5 10^9/L" to "EOS#","%EOS 嗜酸性粒细胞百分比 2 0.5-5 %" to "EOS%","#BASO 嗜碱性粒细胞计数 0.03 0-0.1 10^9/L" to "BASO#","%BASO 嗜碱性粒细胞百分比 0.5 0-1 %" to "BASO%"
  )
  cases.forEach{(raw,key)->val row=ReportParser.parse(raw).single();assertEquals(key,row.metricKey);assertTrue(row.displayName.startsWith(raw.substringBefore(' ')));assertEquals(if(key.endsWith("#"))"×10^9/L" else "%",row.unit)}
  assertEquals("#NEUT 中性粒细胞计数",ReportParser.parse(cases.first().first).single().displayName)
  assertEquals(1,cases.map{ReportParser.parse(it.first).single()}.filter{it.metricKey=="NEUT#"}.groupBy{it.metricKey}.size)
 }

 @Test fun rememberedTemplateNeverOverwritesThisReportsRangeOrLegalUnit(){
  val current=ReportParser.parse("WBC 白细胞 7.25 3.5-9.5 10^9/L\nHGB 血红蛋白 10.2 g/dL 8.0-15.0")
  val template=HospitalLabTemplate("医院","血常规",1,true,listOf(LabFieldTemplate("WBC","WBC","×10^9/L",5.0,9.0),LabFieldTemplate("HGB","HGB","g/L",113.0,151.0)))
  val applied=applyRememberedTemplate(current,template)
  assertEquals(3.5,applied[0].referenceLow!!,0.0);assertEquals(9.5,applied[0].referenceHigh!!,0.0)
  assertEquals("g/dL",applied[1].unit);assertEquals(8.0,applied[1].referenceLow!!,0.0);assertEquals(15.0,applied[1].referenceHigh!!,0.0)
 }

 @Test fun savedRangeAndCanonicalKeyRemainAvailableToDetailsAndTrends(){
  val parsed=ReportParser.parse("#NEUT 中性粒细胞计数 5.77 2.0-7.0 10^9/L\nWBC 白细胞 7.25 3.5-9.5 10^9/L")
  val saved=parsed.mapIndexed{i,p->LabResult("$i","r","医院","血常规",1,p.metricKey,p.displayName,p.value,p.unit,p.referenceLow,p.referenceHigh,requireNotNull(parseDate("2016-9-18")),textValue=p.textValue,normalizedValue=UnitNormalizer.normalize(p.metricKey,p.value,p.unit).first,normalizedUnit=UnitNormalizer.normalize(p.metricKey,p.value,p.unit).second)}
  assertEquals("#NEUT 中性粒细胞计数",saved[0].rawName);assertEquals("NEUT#",saved[0].metricKey)
  assertEquals("3.5–9.5",rangeText(saved[1].referenceLowAtTest,saved[1].referenceHighAtTest));assertEquals(3.5,saved[1].trendReferenceRange().first!!,0.0);assertEquals(9.5,saved[1].trendReferenceRange().second!!,0.0)
  assertEquals(1,listOf(ReportParser.key("#NEUT"),ReportParser.key("NEUT#")).groupBy{it}.size)
 }
 @Test fun damagedDifferentialPrefixesRecoverFromChineseLabels(){
  val cases=listOf(
   "上NEUT 中性粒细胞计数 2.00 2.0-7.0 10^9/L" to "NEUT#",
   "红MPH 淋巴细胞百分比 20 20-50 %" to "LYMPH%",
   "三MONO 单核细胞计数 0.4 0.1-0.8 10^9/L" to "MONO#",
   "红EOS 嗜酸性粒细胞百分比 2 0.5-5 %" to "EOS%",
   "上BASO 嗜碱性粒细胞计数 0.03 0-0.1 10^9/L" to "BASO#"
  )
  cases.forEach{(raw,key)->assertEquals(raw,key,ReportParser.parse(raw).single().metricKey)}
 }
 @Test fun differentialChineseLabelsCoverCountAndPercentFamilies(){
  val cases=listOf(
   "中性粒细胞计数" to "NEUT#","中性粒细胞绝对值" to "NEUT#","中性粒细胞百分比" to "NEUT%","中性粒细胞比率" to "NEUT%",
   "淋巴细胞计数" to "LYMPH#","淋巴细胞绝对值" to "LYMPH#","淋巴细胞百分比" to "LYMPH%","淋巴细胞比例" to "LYMPH%",
   "单核细胞计数" to "MONO#","单核细胞绝对值" to "MONO#","单核细胞百分比" to "MONO%","单核细胞比率" to "MONO%",
   "嗜酸性粒细胞计数" to "EOS#","嗜酸性粒细胞绝对值" to "EOS#","嗜酸性粒细胞百分比" to "EOS%","嗜酸性粒细胞比例" to "EOS%",
   "嗜碱性粒细胞计数" to "BASO#","嗜碱性粒细胞绝对值" to "BASO#","嗜碱性粒细胞百分比" to "BASO%","嗜碱性粒细胞比率" to "BASO%"
  )
  cases.forEach{(name,key)->assertEquals(name,key,ReportParser.key(name))}
 }

 @Test fun xiapuConfirmedCbcReportKeepsAllPrintedRowsSeparate(){
  val raw=listOf(
   "WBC 白细胞 7.25 3.5-9.5 10^9/L",
   "#NEUT 中性粒细胞计数 5.77 2.00-7.00 10^9/L",
   "%NEUT 中性粒细胞百分比 79.4 50.0-70.0 %",
   "#LYMPH 淋巴细胞计数 1.31 0.80-4.00 10^9/L",
   "%LYMPH 淋巴细胞百分比 18.1 20.0-40.0 %",
   "#MONO 单核细胞计数 0.12 0.12-1.2 10^9/L",
   "%MONO 单核细胞百分比 1.7 3-12 %",
   "#EOS 嗜酸性粒细胞计数 0.05 0.02-0.5 10^9/L",
   "%EOS 嗜酸性粒细胞百分比 0.8 0.5-5 %",
   "#BASO 嗜碱性粒细胞计数 0.00 0.00-0.10 10^9/L",
   "%BASO 嗜碱性粒细胞百分比 0.0 0.0-1.0 %",
   "RBC 红细胞 3.99 3.68-5.13 10^12/L",
   "HGB 血红蛋白 113 113-151 g/L",
   "HCT 红细胞压积 34.20 34-45 %",
   "MCV 红细胞平均体积 85.7 80-100 fL",
   "MCH 平均血红蛋白量 28.3 27-34 pg",
   "MCHC 平均血红蛋白浓度 330 320-360 g/L",
   "RDW 红细胞分布宽度 12.8 11-16 %",
   "RDW-SD 红细胞分布宽度SD 40 35-56 fL",
   "PLT 血小板 232 100-300 10^9/L",
   "PCT 血小板压积 0.212 0.108--",
   "MPV 平均血小板体积 9.1 6.5-12 fL",
   "PDW 血小板分布宽度 16.4 15-17 %",
   "P-LCR 大型血小板比率 21.8 11-45",
   "%NRBC 有核红细胞比率 0.00 <=9999.99",
   "#NRBC 有核红细胞计数 0.000 <=9999.99",
   "P-LCR 大小血小板数目 51 30-90 10^9/L"
  ).joinToString("\n")
  val rows=ReportParser.parse(raw)
  assertEquals(27,rows.size)
  assertEquals(27,rows.map{it.metricKey}.distinct().size)
  fun row(k:String)=rows.single{it.metricKey==k}
  assertEquals("0.00",row("NRBC%").textValue);assertEquals(0.0,row("NRBC%").value!!,0.0)
  assertEquals("",row("NRBC%").unit);assertEquals(9999.99,row("NRBC%").referenceHigh!!,0.0)
  assertEquals("0.000",row("NRBC#").textValue);assertEquals("",row("NRBC#").unit)
  assertEquals(3.5,row("WBC").referenceLow!!,0.0);assertEquals(9.5,row("WBC").referenceHigh!!,0.0)
  assertEquals("×10^9/L",row("LYMPH#").unit);assertEquals("%",row("NEUT%").unit)
  assertEquals("%",row("PDW").unit);assertEquals("",row("P-LCR").unit);assertEquals("",row("PCT").unit);assertEquals(0.108,row("PCT").referenceLow!!,0.0);assertNull(row("PCT").referenceHigh);assertEquals("P-LCC",rows.last().metricKey)
 }

 @Test fun cbcMetricRejectsPlausibleUnitStolenFromNeighborColumn(){
  assertEquals("%",ReportParser.parse("%NEUT 中性粒细胞百分比 79.4 50-70 fL").single().unit)
  assertEquals("×10^9/L",ReportParser.parse("#LYMPH 淋巴细胞计数 1.31 0.8-4.0 %").single().unit)
  assertEquals("fL",ReportParser.parse("MPV 平均血小板体积 9.1 6.5-12 %").single().unit)
  assertEquals("%",ReportParser.parse("PDW 血小板分布宽度 16.4 15-17 fL").single().unit)
 }

 @Test fun specificChineseLabelWinsOverGenericSubstring(){
  assertEquals("RDW-SD",ReportParser.parse("RDW-SD 红细胞分布宽度SD 40 35-56 fL").single().metricKey)
  assertEquals("PCT",ReportParser.parse("PCT 血小板压积 0.212 >=0.108").single().metricKey)
  assertEquals("PDW",ReportParser.parse("PDW 血小板分布宽度 16.4 15-17 %").single().metricKey)
  assertEquals("MPV",ReportParser.parse("MPV 平均血小板体积 9.1 6.5-12 fL").single().metricKey)
  assertEquals("P-LCR",ReportParser.parse("P-LCR 大型血小板比率 21.8 11-45").single().metricKey)
 }
 @Test fun prefixNrbcNormalizesToStableSuffixKeys(){
  assertEquals("NRBC%",ReportParser.parse("%NRBC 有核红细胞比率 0.00 <=9999.99").single().metricKey)
  assertEquals("NRBC#",ReportParser.parse("#NRBC 有核红细胞计数 0.000 <=9999.99").single().metricKey)
 }

}
