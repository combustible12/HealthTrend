package com.combustible12.healthtrend
import androidx.compose.ui.geometry.Offset
import org.junit.Assert.*
import org.junit.Test

class ReportParserTest {
  @Test fun maternalCbcRdwCvAndSdNeverSwapOnPaste(){
   val template=HospitalLabTemplate("福建省妇幼保健院","血常规",1,true,listOf(
    LabFieldTemplate("MCHC","平均血红蛋白浓度","g/L",316.0,354.0),
    LabFieldTemplate("RDW-CV","红细胞分布宽度CV","%",12.2,15.0),
    LabFieldTemplate("RDW-SD","红细胞分布宽度SD","fL",42.0,53.6),
    LabFieldTemplate("PLT","血小板计数","10^9/L",125.0,350.0)
   ))
   val raw="""医院：福建省妇幼保健院
检查类型：血常规
检查日期：2026-09-07
MCHC 平均血红蛋白浓度 334 g/L 316-354
RDW-CV 红细胞分布宽度CV 11.9 % 12.2-15.0
RDW-SD 红细胞分布宽度SD 38.50 fL 42.00-53.60
PLT 血小板计数 252 10^9/L 125-350"""
   val draft=pastedReportDraft(raw,template)
   assertEquals(listOf("MCHC","RDW-CV","RDW-SD","PLT"),draft.rows.map{it.key})
   assertEquals("11.9",draft.rows[1].text)
   assertEquals("%",draft.rows[1].unit)
   assertEquals("12.2",draft.rows[1].low)
   assertEquals("38.50",draft.rows[2].text)
   assertEquals("fL",draft.rows[2].unit)
   assertEquals("42.0",draft.rows[2].low)
  }

  @Test fun pastedChatReportUsesMetadataAndConfirmedTemplateSkeleton(){
  val template=HospitalLabTemplate("霞浦县中医院","生化",1,true,listOf(
   LabFieldTemplate("ALT","谷丙转氨酶","U/L",7.0,40.0),
   LabFieldTemplate("CREA","肌酐","umol/L",35.0,80.0)
  ))
  val raw="""医院：霞浦县中医院
检查类型：生化
检查日期：2026-9-23
ALT | 谷丙转氨酶 | 23 | 错误单位 | 9-50
CREA | 肌酐 | 46 | umol/L | 35-80"""
  val draft=pastedReportDraft(raw,template)
  assertEquals("霞浦县中医院",draft.hospital);assertEquals("生化",draft.type);assertEquals("2026-09-23",draft.date)
  assertEquals(listOf("ALT","CREA"),draft.rows.map{it.key});assertEquals(listOf("23","46"),draft.rows.map{it.text})
  assertEquals("U/L",draft.rows[0].unit);assertEquals("7.0",draft.rows[0].low);assertEquals("40.0",draft.rows[0].high)
 }
 @Test fun fixedClipboardFormatKeepsOnlyActuallyTestedMetrics(){
  val cbcLines=listOf(
   "WBC 白细胞 6.42 10^9/L 3.5-9.5",
   "NEUT# 中性粒细胞计数 4.10 10^9/L 2.00-7.00",
   "NEUT% 中性粒细胞百分比 63.9 % 50.0-70.0",
   "LYMPH# 淋巴细胞计数 1.72 10^9/L 0.80-4.00",
   "LYMPH% 淋巴细胞百分比 26.8 % 20.0-40.0",
   "MONO# 单核细胞计数 0.45 10^9/L 0.12-1.2",
   "MONO% 单核细胞百分比 7.0 % 3-12",
   "EOS# 嗜酸性粒细胞计数 0.12 10^9/L 0.02-0.5",
   "EOS% 嗜酸性粒细胞百分比 1.9 % 0.5-5",
   "BASO# 嗜碱性粒细胞计数 0.03 10^9/L 0.00-0.10",
   "BASO% 嗜碱性粒细胞百分比 0.4 % 0.0-1.0",
   "RBC 红细胞 4.18 10^12/L 3.68-5.13",
   "HGB 血红蛋白 121 g/L 113-151",
   "HCT 红细胞压积 36.40 % 34-45",
   "MCV 红细胞平均体积 87.1 fL 80-100",
   "MCH 平均红细胞血红蛋白量 29.0 pg 27-34",
   "MCHC 平均红细胞血红蛋白浓度 333 g/L 320-360",
   "RDW 红细胞分布宽度 12.70 % 11-16",
   "RDW-SD 红细胞分布宽度SD 42 fL 35-56",
   "PLT 血小板 245 10^9/L 100-300",
   "PCT 血小板压积 0.221 0.108--",
   "MPV 平均血小板体积 9.4 fL 6.5-12",
   "PDW 血小板分布宽度 16.1 % 15-17",
   "P-LCR 大型血小板比率 22.4 11-45",
   "NRBC% 有核红细胞比率 0.00 <=9999.99",
   "NRBC# 有核红细胞计数 0.000 <=9999.99",
   "P-LCC 大小血小板数目 55 10^9/L 30-90"
  )
  val cbcTemplate=HospitalLabTemplate("测试市中心医院","血常规",1,true,
   cbcLines.map{ReportParser.parse(ReportParser.bindExplicitLeadingIdentities(it)).single()}.map{
    LabFieldTemplate(it.metricKey,it.displayName,displayLabUnit(it.unit),it.referenceLow,it.referenceHigh)
   })
  val fullRaw=(listOf("测试市中心医院","血常规","2026-10-06")+cbcLines).joinToString("\n")
  val full=pastedReportDraft(fullRaw,cbcTemplate)
  assertEquals("测试市中心医院",full.hospital);assertEquals("血常规",full.type);assertEquals("2026-10-06",full.date)
  assertEquals(27,full.rows.size);assertTrue(full.rows.all{it.text.isNotBlank()});assertTrue(full.valid())
  assertEquals("36.40",full.rows.single{it.key=="HCT"}.text)
  assertEquals("0.00",full.rows.single{it.key=="NRBC%"}.text);assertEquals("0.000",full.rows.single{it.key=="NRBC#"}.text)
  assertEquals("10^9/L",full.rows.single{it.key=="NEUT#"}.unit);assertEquals("10^12/L",full.rows.single{it.key=="RBC"}.unit)
  assertEquals("P-LCR",full.rows.single{it.key=="P-LCR"}.key);assertEquals("P-LCC",full.rows.single{it.key=="P-LCC"}.key)

  val missingKeys=setOf("EOS#","EOS%","NRBC%","NRBC#")
  val partialRaw=(listOf("测试市中心医院","血常规","2026-10-07")+cbcLines.filterNot{line->missingKeys.any{line.startsWith("$it ")}}).joinToString("\n")
  val partial=pastedReportDraft(partialRaw,cbcTemplate)
  assertEquals(23,partial.rows.size);assertTrue(partial.rows.none{it.key in missingKeys});assertTrue(partial.valid())

  val chemistry=listOf(
   "TP 总蛋白 72.40 g/L 65-85","ALB 白蛋白 41.80 g/L 40-55","GLOB 球蛋白 30.60 g/L 20-40",
   "A/G 白球比 1.4 1.5-2.5","TBIL 总胆红素 8.10 umol/L 3.4-20.6","DBIL 直接胆红素 1.30 umol/L <=6.84",
   "IBIL 间接胆红素 6.80 umol/L 2-15.22","ALT 谷丙转氨酶 24 U/L 7-40","AST 谷草转氨酶 19 U/L 13-35",
   "AST/ALT 谷草/谷丙 0.79","GGT 谷氨酰转肽酶 17 U/L 7-45","ALP 碱性磷酸酶 68 U/L 35-100",
   "UREA 尿素 4.8 mmol/L 1.43-7.14","CREA 肌酐 52 umol/L 35-80","UA 尿酸 310 umol/L 90-357"
  )
  val chemistryTemplate=HospitalLabTemplate("测试市中心医院","生化",1,true,
   (chemistry+listOf("LDH 乳酸脱氢酶 180 U/L 120-250","CHE 胆碱酯酶 7000 U/L 5000-12000","TBA 总胆汁酸 5.0 umol/L 0-10")).map{
    ReportParser.parse(ReportParser.bindExplicitLeadingIdentities(it)).single()
   }.map{LabFieldTemplate(it.metricKey,it.displayName,displayLabUnit(it.unit),it.referenceLow,it.referenceHigh)})
  val chemDraft=pastedReportDraft((listOf("测试市中心医院","生化","2026-10-08")+chemistry).joinToString("\n"),chemistryTemplate)
  assertEquals(15,chemDraft.rows.size);assertTrue(chemDraft.valid())
  assertEquals(19.0,chemDraft.rows.single{it.key=="AST"}.parsed().value!!,0.0)
  assertEquals(0.79,chemDraft.rows.single{it.key=="AST/ALT"}.parsed().value!!,0.0)
  assertTrue(chemDraft.rows.none{it.key in setOf("LDH","CHE","TBA")})
 }

 @Test fun astAndAstAltStaySeparateAndTitlesHideInternalCodes(){
  val raw="""测试医院
生化
2026-10-06
AST 谷草转氨酶 22 U/L 13-35
AST/ALT 谷草/谷丙 0.88"""
  val template=HospitalLabTemplate("测试医院","生化",1,true,listOf(
   LabFieldTemplate("AST","AST 谷草转氨酶","U/L",13.0,35.0),
   LabFieldTemplate("AST/ALT","AST/ALT 谷草/谷丙","",null,null)
  ))
  val draft=pastedReportDraft(raw,template)
  assertEquals(listOf("AST","AST/ALT"),draft.rows.map{it.key})
  assertEquals(listOf("22","0.88"),draft.rows.map{it.text})
  assertEquals("谷草转氨酶",labDisplayTitle(draft.rows[0].name,draft.rows[0].key))
  assertEquals("谷草/谷丙",labDisplayTitle(draft.rows[1].name,draft.rows[1].key))
  assertFalse(labDisplayTitle(draft.rows[0].name,draft.rows[0].key).contains("AST"))
  assertEquals("（AST） 谷草转氨酶 22 U/L 13-35",ReportParser.bindExplicitLeadingIdentities("AST 谷草转氨酶 22 U/L 13-35"))
  assertEquals("（AST/ALT） 谷草/谷丙 0.88",ReportParser.bindExplicitLeadingIdentities("AST/ALT 谷草/谷丙 0.88"))
  assertNotEquals(ReportParser.key("谷草转氨酶（AST）"),ReportParser.key("谷草/谷丙（AST/ALT）"))
  val direct=ReportParser.parse(ReportParser.bindExplicitLeadingIdentities("AST 谷草转氨酶 22 U/L 13-35\\nAST/ALT 谷草/谷丙 0.88"))
  assertEquals("谷草转氨酶",direct.single{it.metricKey=="AST"}.displayName)
  assertEquals("22",direct.single{it.metricKey=="AST"}.textValue)
  assertEquals("谷草/谷丙",direct.single{it.metricKey=="AST/ALT"}.displayName)
  assertEquals("0.88",direct.single{it.metricKey=="AST/ALT"}.textValue)
  assertEquals("谷草/谷丙",labDisplayTitle("AST/ALT 谷草/谷丙","AST/ALT"))
  assertEquals("AST/ALT 谷草/谷丙",labDisplayTitle("AST/ALT 谷草/谷丙","AST"))
 }


 @Test fun pollutedXiapuBiochemistryTemplateRepairsIdentityWithoutOverwritingUserFields(){
  val storeRepair=HealthStore::class.java.getDeclaredMethod("repairXiacuBiochemistryTemplate",HospitalLabTemplate::class.java).apply{isAccessible=true}
  val polluted=HospitalLabTemplate("霞浦县中医院","生化",7,true,listOf(
   LabFieldTemplate("AST","AST 谷草转氨酶","U/L",13.0,35.0,"用户说明A"),
   LabFieldTemplate("AST","AST/ALT 谷草/谷丙","自定义单位",null,null,"用户说明B")
  ))
  val repaired=storeRepair.invoke(null,polluted) as HospitalLabTemplate
  assertEquals(listOf("AST","AST/ALT"),repaired.fields.map{it.metricKey})
  assertEquals("自定义单位",repaired.fields[1].unit)
  assertEquals("用户说明B",repaired.fields[1].trendMeaning)
  val raw="""霞浦县中医院
生化
2026-10-06
AST 谷草转氨酶 22 U/L 13-35
AST/ALT 谷草/谷丙 0.88"""
  val draft=pastedReportDraft(raw,repaired)
  assertEquals("22",draft.rows.single{it.key=="AST"}.text)
  assertEquals("0.88",draft.rows.single{it.key=="AST/ALT"}.text)
 }

 @Test fun pastedReportWithoutTemplateStillCreatesEditableDraft(){
  val raw="医院：测试医院\n检查类型：肾功能\n报告日期：2026/9/29\nUREA 尿素 5.2 mmol/L 1.43-7.14"
  val draft=pastedReportDraft(raw)
  assertEquals("2026-09-29",draft.date);assertEquals("肾功能",draft.type);assertEquals("UREA",draft.rows.single().key);assertEquals("5.2",draft.rows.single().text)
 }
 @Test fun trendLabelsKeepCompactDatesValuesAndYearContext(){
  val first=parseDate("2026-09-18")!!;val last=parseDate("2026-09-26")!!
  assertEquals("09/18",trendShortDate(first));assertEquals("2026年",trendYearLabel(listOf(first to 7.25,last to 3.75)))
  assertEquals("2025–2026年",trendYearLabel(listOf(parseDate("2025-12-31")!! to 1.0,last to 2.0)))
  assertEquals("7.25",formatTrendValue(7.25));assertEquals("3",formatTrendValue(3.0))
 }
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
 @Test fun neverInventsReferenceRangesOrUnits(){val x=ReportParser.parse("WBC 3.75").single();assertNull(x.referenceLow);assertNull(x.referenceHigh);assertEquals("",x.unit)}
 @Test fun unknownTextAndComparatorsAreNotDropped(){val rows=ReportParser.parse("HBsAg 阴性\nCRP <0.5 mg/L <5");assertEquals(2,rows.size);assertNull(rows[0].value);assertEquals("阴性",rows[0].textValue);assertEquals("<",rows[1].comparator);assertEquals(5.0,rows[1].referenceHigh!!,0.0)}
 @Test fun rowNumberAndScientificNotation(){val x=ReportParser.parse("1 WBC 3.75 ×10^9/L 3.5-9.5").single();assertEquals("WBC",x.metricKey);assertEquals(3.75,x.value!!,0.0)}
 @Test fun ChineseNamesAndAbbreviations(){assertEquals("WBC",ReportParser.key("白细胞计数(WBC)"));assertEquals("NEUT%",ReportParser.key("中性粒细胞百分比"));assertEquals("CREA",ReportParser.key("CRE"))}
 @Test fun editingDoesNotRoundOriginalTimestamp(){val original=1672531200123L;assertEquals(original,preserveTimestamp(dateText(original),original));val edited="2026-09-26 09:30";assertEquals(parseDate(edited),preserveTimestamp(edited,original));assertNull(preserveTimestamp("",null))}
 @Test fun historicalStatusAndEditedValue(){val r=LabResult("1","r","h","血常规",1,"HGB","血红蛋白",102.0,"g/L",113.0,151.0,1L,textValue="102");assertEquals(ResultStatus.LOW,r.status());val edit=r.withEditedValue(130.0);assertEquals(ResultStatus.NORMAL,edit.status());assertEquals("130",edit.textValue);assertEquals(113.0,edit.referenceLowAtTest!!,0.0);assertTrue(edit.editedByUser);assertEquals(ResultStatus.UNKNOWN,r.copy(referenceLowAtTest=null,referenceHighAtTest=null).status())}
 @Test fun editedValuePreservesConfirmedDisplayPrecision(){assertEquals("0.00",LabResult("1","r","h","血常规",1,"NRBC%","有核红细胞比率",0.0,"",null,9999.99,1L,textValue="0.00").withEditedValue(0.0).textValue);assertEquals("0.000",LabResult("2","r","h","血常规",1,"NRBC#","有核红细胞计数",0.0,"",null,9999.99,1L,textValue="0.000").withEditedValue(0.0).textValue);assertEquals("34.20",LabResult("3","r","h","血常规",1,"HCT","红细胞压积",34.2,"%",34.0,45.0,1L,textValue="34.20").withEditedValue(34.2).textValue);val changed=LabResult("4","r","h","血常规",1,"NRBC#","有核红细胞计数",0.0,"",null,9999.99,1L,textValue="0.000").withEditedValue(1.234,"1.234");assertEquals(1.234,changed.value!!,0.0);assertEquals("1.234",changed.textValue)}

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
  val changed=LabResult("4","r","h","血常规",null,"NRBC#","NRBC#",0.0,"",null,null,0,textValue="0.000").withEditedValue(1.234,"1.234")
  assertEquals(1.234,changed.value!!,0.0);assertEquals("1.234",changed.textValue)
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
 @Test fun trendPointsUseEvenHorizontalSlotsSoValueAndDateLabelsCannotCollide(){
  val points=listOf(0L to 7.25,5L to 4.35,6L to 2.36,7L to 3.75)
  val positions=points.indices.map{trendPointPosition(points,it,256f,150f)}
  assertEquals(listOf(56f,104f,152f,200f),positions.map{it.x})
 }
 @Test fun trendPointHitTestingCoversEdgesAndInvalidGeometry(){
  val points=listOf(0L to 100.0,100L to 120.0)
  // First point is at x=56, y=88 for this geometry; radius boundary is inclusive.
  assertEquals(0,nearestTrendPoint(points,androidx.compose.ui.geometry.Offset(76f,88f),200f,100f,100.0,120.0,20f))
  assertNull(nearestTrendPoint(points,androidx.compose.ui.geometry.Offset(76.1f,88f),200f,100f,100.0,120.0,20f))
  assertNull(nearestTrendPoint(points,androidx.compose.ui.geometry.Offset.Zero,0f,100f,radius=48f))
  assertNull(nearestTrendPoint(points,androidx.compose.ui.geometry.Offset.Zero,100f,0f,radius=48f))
  assertNull(nearestTrendPoint(points,androidx.compose.ui.geometry.Offset.Zero,100f,100f,radius=-1f))
 }
 @Test fun trendPointHitTestingHonorsNearestPointAndRadius(){
  val points=listOf(0L to 100.0,100L to 120.0)
  val first=nearestTrendPoint(points,androidx.compose.ui.geometry.Offset(56f,88f),200f,100f,100.0,120.0,20f)
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
 @Test fun trendSeriesIgnoresHospitalTemplateVersionAndHistoricalRange(){
  val old=LabResult("1","r1","霞浦县中医院","血常规",3,"WBC","白细胞",7.25,"10^9/L",5.0,9.0,1L,normalizedValue=7.25,normalizedUnit="×10^9/L")
  val current=old.copy(id="2",reportId="r2",templateVersion=5,value=4.35,textValue="4.35",referenceLowAtTest=3.5,referenceHighAtTest=9.5,testedAtEpochMillis=2L,normalizedValue=4.35)
  assertEquals(trendSeriesKey(old),trendSeriesKey(current))
  assertEquals(1,listOf(old,current).groupBy(::trendSeriesKey).size)
 }
 @Test fun trendCardsExplainWhatCoreValuesAreUsedFor(){
  assertEquals("免疫力、感染风险",metricPurpose("WBC"))
  assertTrue(requireNotNull(metricPurpose("#NEUT")).contains("骨髓抑制"))
  assertTrue(requireNotNull(metricPurpose("HGB")).contains("贫血"))
  assertTrue(requireNotNull(metricPurpose("PLT")).contains("出血风险"))
  assertTrue(requireNotNull(metricPurpose("ALT")).contains("肝"))
  assertEquals("胆红素与黄疸",metricPurpose("TBIL"))
  assertTrue(requireNotNull(metricPurpose("ALB")).contains("营养状态"))
  assertEquals("肾功能核心指标",metricPurpose("CREA"))
  assertTrue(requireNotNull(metricPurpose("UA")).contains("化疗后常见升高"))
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
 @Test fun missingUnitsStayBlankWhileCorruptRecognizedUnitsAreRepaired(){
  val wbc=ReportParser.parse("WBC 7.25 3.5-9.5 22 MPV").single();assertEquals("",wbc.unit);assertEquals(3.5,wbc.referenceLow!!,0.0);assertEquals(9.5,wbc.referenceHigh!!,0.0)
  assertEquals("",ReportParser.parse("MPV 9.1 6.5-12").single().unit)
  assertEquals("",ReportParser.parse("HGB 102 garbage 113-151").single().unit)
  assertEquals("×10^9/L",ReportParser.parse("WBC 7.25 3.5-9.5 fL").single().unit)
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
 @Test fun rememberedTemplateOnlyFillsMissingFixedFields(){
  val current=ReportParser.parse("WBC 白细胞 7.25")
  val template=HospitalLabTemplate("霞浦县中医院","血常规",1,true,listOf(LabFieldTemplate("WBC","白细胞","×10^9/L",3.5,9.5)))
  val applied=applyRememberedTemplate(current,template).single()
  assertEquals(7.25,applied.value!!,0.0);assertEquals("7.25",applied.textValue);assertEquals("×10^9/L",applied.unit);assertEquals(3.5,applied.referenceLow!!,0.0);assertEquals(9.5,applied.referenceHigh!!,0.0)
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
  assertEquals("0.000",row("NRBC#").textValue);assertEquals("",row("NRBC#").unit);assertEquals(9999.99,row("NRBC#").referenceHigh!!,0.0)
  assertEquals("0.00",row("BASO#").textValue);assertEquals("0.0",row("BASO%").textValue);assertEquals("34.20",row("HCT").textValue)
  assertEquals(3.5,row("WBC").referenceLow!!,0.0);assertEquals(9.5,row("WBC").referenceHigh!!,0.0)
  assertEquals("×10^9/L",row("LYMPH#").unit);assertEquals("%",row("NEUT%").unit)
  assertEquals("%",row("PDW").unit);assertEquals("",row("P-LCR").unit);assertEquals("",row("PCT").unit);assertEquals(0.108,row("PCT").referenceLow!!,0.0);assertNull(row("PCT").referenceHigh);assertEquals("P-LCC",rows.last().metricKey)
  val expected=listOf(
   "WBC" to Triple(7.25,3.5,9.5),"NEUT#" to Triple(5.77,2.0,7.0),"NEUT%" to Triple(79.4,50.0,70.0),
   "LYMPH#" to Triple(1.31,0.8,4.0),"LYMPH%" to Triple(18.1,20.0,40.0),"MONO#" to Triple(0.12,0.12,1.2),
   "MONO%" to Triple(1.7,3.0,12.0),"EOS#" to Triple(0.05,0.02,0.5),"EOS%" to Triple(0.8,0.5,5.0),
   "BASO#" to Triple(0.0,0.0,0.1),"BASO%" to Triple(0.0,0.0,1.0),"RBC" to Triple(3.99,3.68,5.13),
   "HGB" to Triple(113.0,113.0,151.0),"HCT" to Triple(34.2,34.0,45.0),"MCV" to Triple(85.7,80.0,100.0),
   "MCH" to Triple(28.3,27.0,34.0),"MCHC" to Triple(330.0,320.0,360.0),"RDW" to Triple(12.8,11.0,16.0),
   "RDW-SD" to Triple(40.0,35.0,56.0),"PLT" to Triple(232.0,100.0,300.0),"MPV" to Triple(9.1,6.5,12.0),
   "PDW" to Triple(16.4,15.0,17.0),"P-LCR" to Triple(21.8,11.0,45.0),"P-LCC" to Triple(51.0,30.0,90.0)
  )
  expected.forEach{(key,v)->val x=row(key);assertEquals(key,v.first,x.value!!,0.0);assertEquals(key,v.second,x.referenceLow!!,0.0);assertEquals(key,v.third,x.referenceHigh!!,0.0)}
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

 @Test fun numericOnlyUnitCannotBecomeDisplayedUnit(){
  val row=DraftRow(name="有核红细胞比率",key="NRBC%",text="0.00",unit="0.00",high="9999.99").parsed()
  assertEquals("0.00",row.textValue);assertEquals("",row.unit)
 }

 @Test fun printedMetricCodePrecedence(){assertEquals("RDW-SD",ReportParser.parse("RDW-SD 红细胞分布宽度SD 40 35-56 fL").single().metricKey);assertEquals("PCT",ReportParser.parse("PCT 血小板压积 0.212 >=0.108").single().metricKey)}

 @Test fun confirmedXiupuReportDateWinsOverSendTime(){
  val rows=ReportParser.parse("WBC 白细胞 7.25 3.5-9.5 10^9/L")
  val meta=ReportMetadata.extract("霞浦县中医院检验报告单\n送检时间 2026-9-18 8:37:55\n报告日期 2026-09-18 08:23\nWBC 白细胞 7.25",rows)
  assertEquals("2026-09-18",meta.date)
 }

 @Test fun twoColumnOcrHeaderCannotMergeIndependentRows(){
  val cells=listOf(
   OcrCell("霞浦县中医院检验报告单",0,1000,50,40),
   OcrCell("%NEUT 中性粒细胞百分比 79.4 50.0-70.0 %",20,450,200,30),
   OcrCell("MPV 平均血小板体积 9.1 6.5-12 fL",560,980,200,30),
   OcrCell("#LYMPH 淋巴细胞计数 1.31 0.8-4.0 10^9/L",20,450,240,30),
   OcrCell("PDW 血小板分布宽度 16.4 15-17 %",560,980,240,30)
  )
  val text=reconstructOcrTable(cells)
  val lines=text.lines()
  assertTrue(lines.any{it.contains("%NEUT")&&!it.contains("MPV")})
  assertTrue(lines.any{it.contains("MPV")&&!it.contains("%NEUT")})
  assertEquals(79.4,ReportParser.parse(text).first{it.metricKey=="NEUT%"}.value!!,0.0)
  assertEquals(9.1,ReportParser.parse(text).first{it.metricKey=="MPV"}.value!!,0.0)
 }

 @Test fun suffixNrbcNotationAlsoKeepsPrecisionAndOneSidedRange(){
  val rows=ReportParser.parse("NRBC% 有核红细胞比率 0.00 <=9999.99\nNRBC# 有核红细胞计数 0.000 <=9999.99")
  val pct=rows.single{it.metricKey=="NRBC%"};val count=rows.single{it.metricKey=="NRBC#"}
  assertEquals("0.00",pct.textValue);assertEquals("",pct.unit);assertNull(pct.referenceLow);assertEquals(9999.99,pct.referenceHigh!!,0.0)
  assertEquals("0.000",count.textValue);assertEquals("",count.unit);assertNull(count.referenceLow);assertEquals(9999.99,count.referenceHigh!!,0.0)
 }

 @Test fun numericOnlyUnitsAreRejectedButDigitBearingRealUnitsSurvive(){
  assertEquals("",sanitizeLabUnit("0.00"));assertEquals("",sanitizeLabUnit("109"));assertEquals("",sanitizeLabUnit(" 51 "))
  assertEquals("10^9/L",sanitizeLabUnit("10^9/L"));assertEquals("10^12/L",sanitizeLabUnit("10^12/L"));assertEquals("mg/dL",sanitizeLabUnit("mg/dL"))
  val template=HospitalLabTemplate("医院","血常规",1,true,listOf(LabFieldTemplate("NRBC%","有核红细胞比率","0.00",null,9999.99)))
  val applied=applyRememberedTemplate(ReportParser.parse("NRBC% 有核红细胞比率 0.00 <=9999.99"),template).single()
  assertEquals("",applied.unit);assertEquals("0.00",applied.textValue);assertEquals(9999.99,applied.referenceHigh!!,0.0)
 }

 @Test fun confirmedTemplateOwnsFixedFieldsAndRejectsOcrGarbageRows(){
  val template=HospitalLabTemplate("霞浦县中医院","血常规",5,true,listOf(
   LabFieldTemplate("NEUT#","#NEUT 中性粒细胞计数","10^9/L",2.0,7.0),
   LabFieldTemplate("LYMPH#","#LYMPH 淋巴细胞计数","10^9/L",0.8,4.0),
   LabFieldTemplate("EOS#","#EOS 嗜酸性粒细胞计数","10^9/L",0.02,0.5)
  ))
  val ocr=ReportParser.parse("上NEUT 中性粒细胞计数 2.57 2.00-7.00 109/L\nFLYMPH 淋巴细胞计数 1.50 0.80-4.00 fL\nI嗜酸性粒细胞计数 0.04 0.02-0.5 %\n乱码项目 999 1-2")
  val rows=templateDrivenResults(ocr,template)
  assertEquals(listOf("NEUT#","LYMPH#","EOS#"),rows.map{it.metricKey})
  assertEquals(listOf(2.57,1.50,0.04),rows.map{it.value})
  assertTrue(rows.all{it.unit=="10^9/L"})
  assertEquals(listOf(2.0,0.8,0.02),rows.map{it.referenceLow})
  assertEquals(listOf("#NEUT 中性粒细胞计数","#LYMPH 淋巴细胞计数","#EOS 嗜酸性粒细胞计数"),rows.map{it.displayName})
 }

 @Test fun displayUnitNeverAddsMultiplicationSign(){
  assertEquals("10^9/L",displayLabUnit("×10^9/L"))
  assertEquals("10^12/L",displayLabUnit("x10^12/L"))
  assertEquals("g/L",displayLabUnit("g/L"))
 }

 @Test fun mixedChemistryReportIsDetectedAsBiochemistry(){
  val raw="霞浦县中医院检验报告单\nTP 总蛋白 75.50 65-85 g/L\nALB 白蛋白 42.90 40-55 g/L\nALT 谷丙转氨酶 23 7-40 U/L\nAST 谷草转氨酶 20 13-35 U/L\nUREA 尿素 5.2 1.43-7.14 mmol/L\nCREA 肌酐 46 35-80 umol/L\nUA 尿酸 328 90-357 umol/L"
  val parsed=ReportParser.parse(raw)
  assertEquals("生化",ReportMetadata.extract(raw,parsed).reportType)
 }

 @Test fun confirmedTemplateImportKeepsDisplayPrecisionAndFixedMetadata(){
  val template=HospitalLabTemplate("霞浦县中医院","血常规",5,true,listOf(
   LabFieldTemplate("HCT","红细胞压积","%",34.0,45.0),
   LabFieldTemplate("NRBC%","有核红细胞比率","",null,9999.99),
   LabFieldTemplate("NRBC#","有核红细胞计数","",null,9999.99)
  ))
  val parsed=ReportParser.parse("HCT 红细胞压积 32.30 34-45 %\nNRBC% 有核红细胞比率 0.00 <=9999.99\nNRBC# 有核红细胞计数 0.000 <=9999.99")
  val rows=templateDrivenResults(parsed,template)
  assertEquals(listOf("32.30","0.00","0.000"),rows.map{it.textValue})
  assertEquals(listOf("%","",""),rows.map{it.unit})
 }

 @Test fun templateRangeAloneCanNeverBecomeTheVisitResult(){
  val field=LabFieldTemplate("NEUT#","#NEUT 中性粒细胞计数","10^9/L",2.0,7.0)
  val missing=ReportParser.parse("上NEUT 中性粒细胞计数 2.00-7.00").single()
  assertFalse(templateResultIsIndependent(missing,field))
  val blank=templateDrivenResults(listOf(missing),HospitalLabTemplate("霞浦县中医院","血常规",5,true,listOf(field))).single()
  assertNull(blank.value);assertEquals("",blank.textValue);assertEquals("10^9/L",blank.unit)
  val real=ReportParser.parse("上NEUT 中性粒细胞计数 2.57 2.00-7.00 109/L").single()
  assertTrue(templateResultIsIndependent(real,field));assertEquals(2.57,templateDrivenResults(listOf(real),HospitalLabTemplate("霞浦县中医院","血常规",5,true,listOf(field))).single().value!!,0.0)
 }

 @Test fun duplicateSameIdentityOcrRowsRemainBlankInsteadOfChoosingByPosition(){
  val template=HospitalLabTemplate("医院","血常规",1,true,listOf(LabFieldTemplate("HGB","HGB 血红蛋白","g/L",113.0,151.0)))
  val rows=ReportParser.parse("HGB 血红蛋白 102 113-151 g/L\nHGB 血红蛋白 120 113-151 g/L")
  val resolved=templateDrivenResults(rows,template).single()
  assertNull(resolved.value);assertEquals("",resolved.textValue)
  assertEquals(113.0,resolved.referenceLow!!,0.0);assertEquals(151.0,resolved.referenceHigh!!,0.0)
 }

 @Test fun suppliedXiacuBiochemistryTemplateIsSeparateAndEditable(){
  val template=xiapuBiochemistryTemplate()
  assertEquals("霞浦县中医院",template.hospitalKey);assertEquals("生化",template.reportType);assertEquals(18,template.fields.size)
  assertEquals(listOf("TP","ALB","GLOB","A/G"),template.fields.take(4).map{it.metricKey})
  assertEquals(6.84,template.fields.single{it.metricKey=="DBIL"}.referenceHigh!!,0.0)
  assertNull(template.fields.single{it.metricKey=="DBIL"}.referenceLow)
  assertNotEquals("血常规",template.reportType)
  val edited=template.copy(fields=template.fields.map{if(it.metricKey=="TP")it.copy(referenceLow=64.0)else it})
  assertEquals(64.0,edited.fields.first().referenceLow!!,0.0)
 }

 @Test fun conflictingYearOnSameAdministrativeDayRequiresDateReview(){
  val raw="报告日期 2016-09-18\n打印时间 2026-09-18 10:00\nWBC 白细胞 4.35 3.5-9.5 10^9/L"
  val meta=ReportMetadata.extract(raw,ReportParser.parse(raw))
  assertEquals("",meta.date);assertTrue("date" in meta.uncertain)
 }

 @Test fun realTwentySevenRowCbcUsesTemplateSkeletonAndOnlyNewValues(){
  val fixed="""WBC 白细胞 7.25 3.5-9.5 10^9/L
#NEUT 中性粒细胞计数 5.77 2.00-7.00 10^9/L
%NEUT 中性粒细胞百分比 79.4 50.0-70.0 %
#LYMPH 淋巴细胞计数 1.31 0.80-4.00 10^9/L
%LYMPH 淋巴细胞百分比 18.1 20.0-40.0 %
#MONO 单核细胞计数 0.12 0.12-1.2 10^9/L
%MONO 单核细胞百分比 1.7 3-12 %
#EOS 嗜酸性粒细胞计数 0.05 0.02-0.5 10^9/L
%EOS 嗜酸性粒细胞百分比 0.8 0.5-5 %
#BASO 嗜碱性粒细胞计数 0.00 0.00-0.10 10^9/L
%BASO 嗜碱性粒细胞百分比 0.0 0.0-1.0 %
RBC 红细胞 3.99 3.68-5.13 10^12/L
HGB 血红蛋白 113 113-151 g/L
HCT 红细胞压积 34.20 34-45 %
MCV 红细胞平均体积 85.7 80-100 fL
MCH 平均红细胞血红蛋白量 28.3 27-34 pg
MCHC 平均血红蛋白浓度 330 320-360 g/L
RDW 红细胞分布宽度 12.8 11-16 %
RDW-SD 红细胞分布宽度SD 40 35-56 fL
PLT 血小板 232 100-300 10^9/L
PCT 血小板压积 0.212 0.108--
MPV 平均血小板体积 9.1 6.5-12 fL
PDW 血小板分布宽度 16.4 15-17 %
P-LCR 大型血小板比率 21.8 11-45
%NRBC 有核红细胞比率 0.00 <=9999.99
#NRBC 有核红细胞计数 0.000 <=9999.99
P-LCR 大小血小板数目 51 30-90 10^9/L"""
  val templateRows=ReportParser.parse(fixed)
  assertEquals("confirmed template parser keys=${templateRows.map{it.metricKey}}",27,templateRows.size)
  val template=HospitalLabTemplate("霞浦县中医院","血常规",5,true,templateRows.map{LabFieldTemplate(it.metricKey,it.displayName,displayLabUnit(it.unit),it.referenceLow,it.referenceHigh)})
  val current="""WBC 白细胞 4.35 5.0-9.0 109/L
上NEUT 中性粒细胞计数 2.57 2.00-7.00 109/L
%NEUT 中性粒细胞百分比 59.2 50.0-70.0 fL
FLYMPH 淋巴细胞计数 1.50 0.80-4.00 109/L
%LYMPH 淋巴细胞百分比 34.4 20.0-40.0 %
#MONO 单核细胞计数 0.23 0.12-1.2 10^9/L
%MONO 单核细胞百分比 5.2 3-12 %
I嗜酸性粒细胞计数 0.04 0.02-0.5 109/L
%EOS 嗜酸性粒细胞百分比 0.9 0.5-5 %
#BASO 嗜碱性粒细胞计数 0.01 0.00-0.10 10^9/L
%BASO 嗜碱性粒细胞百分比 0.3 0.0-1.0 %
RBC 红细胞 3.76 3.68-5.13 10^12/L
HGB 血红蛋白 106 113-151 g/L
HCT 红细胞压积 32.30 34-45 %
MCV 红细胞平均体积 86.0 80-100 fL
MCH 平均红细胞血红蛋白量 28.2 27-34 pg
MCHC 平均血红蛋白浓度 328 320-360 g/L
RDW 红细胞分布宽度 13.0 11-16 %
RDW-SD 红细胞分布宽度SD 41 35-56 fL
PLT 血小板 282 100-300 10^9/L
PCT 血小板压积 0.255 0.108--
MPV 平均血小板体积 9.0 6.5-12 fL
PDW 血小板分布宽度 16.4 15-17 %
P-LCR 大型血小板比率 20.8 11-45
%NRBC 有核红细胞比率 0.00 <=9999.99
#NRBC 有核红细胞计数 0.000 <=9999.99
P-LCR 大小血小板数目 59 30-90 10^9/L
乱码项目 999 1-2"""
  val rows=templateDrivenResults(ReportParser.parse(current),template)
  assertEquals("template-driven keys=${rows.map{it.metricKey}}",27,rows.size)
  assertEquals("fixed key order",template.fields.map{it.metricKey},rows.map{it.metricKey})
  assertEquals("fixed display names",template.fields.map{it.displayName},rows.map{it.displayName})
  fun row(k:String)=rows.single{it.metricKey==k}
  assertEquals(4.35,row("WBC").value!!,0.0);assertEquals(3.5,row("WBC").referenceLow!!,0.0);assertEquals(9.5,row("WBC").referenceHigh!!,0.0)
  assertEquals(2.57,row("NEUT#").value!!,0.0);assertEquals(1.50,row("LYMPH#").value!!,0.0);assertEquals(0.04,row("EOS#").value!!,0.0)
  assertEquals("32.30",row("HCT").textValue);assertEquals("86.0",row("MCV").textValue);assertEquals("0.00",row("NRBC%").textValue);assertEquals("0.000",row("NRBC#").textValue)
  assertEquals("10^9/L",row("WBC").unit);assertTrue(rows.none{it.displayName.contains("上NEUT")||it.displayName.contains("FLYMPH")||it.displayName.contains("I嗜酸")||it.unit=="109/L"})
 }



}
