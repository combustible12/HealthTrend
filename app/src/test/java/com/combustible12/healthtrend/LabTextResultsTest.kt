package com.combustible12.healthtrend

import org.junit.Assert.*
import org.junit.Test

class LabTextResultsTest {
 private fun row(text:String,reference:String="阴性")=LabResult("synthetic","synthetic","合成医院","传染病筛查",null,"HBSAG","乙肝表面抗原",null,"",null,null,1,textValue=text,referenceRaw=reference)
 @Test fun numericPrecisionAndKindAreIndependent(){assertEquals(LabValueKind.NUMBER,LabResultValues.kind("0.00"));assertEquals(0.0,LabResultValues.numeric("0.00")!!,0.0);assertEquals(LabValueKind.SEMIQUANTITATIVE,LabResultValues.kind("2+"));assertNull(LabResultValues.numeric("2+"))}
 @Test fun referenceNotPolarityDeterminesAgreement(){assertEquals(ResultStatus.NORMAL,row("阳性","阳性").status());assertEquals(ResultStatus.HIGH,row("阳性").status());assertEquals(ResultStatus.NORMAL,row("阴性").status())}
 @Test fun uncertaintyAlwaysGrayEvenIfReferenceMatches(){listOf("弱阳性","可疑","±","无法判断","待复查").forEach{assertEquals(ResultStatus.UNKNOWN,row(it,it).status())}}
 @Test fun absentReferenceIsUnknown(){assertEquals(ResultStatus.UNKNOWN,row("阳性","").status())}
 @Test fun negativeAndNotDetectedAreComparable(){assertEquals(ResultStatus.NORMAL,row("未检出").status());assertEquals(ResultStatus.NORMAL,row("阴性（-）","阴性(-)").status())}
 @Test fun gradedPositiveUsesOnlyExplicitNegativeReference(){assertEquals(ResultStatus.HIGH,row("2+").status());assertEquals(ResultStatus.UNKNOWN,row("2+","").status());assertEquals("2+",row("2+").textValue)}
 @Test fun descriptionsArePreservedWithoutInventedJudgment(){assertEquals(LabValueKind.DESCRIPTION,LabResultValues.kind("棕黄色"));assertEquals(ResultStatus.UNKNOWN,row("棕黄色","").status());assertEquals(ResultStatus.UNKNOWN,row("B型","").status())}
 @Test fun nonfiniteAndEmptyAreNotResults(){listOf(""," ","NaN","Infinity","1e999").forEach{assertFalse(it,LabResultValues.valid(it))}}
 @Test fun mixedClipboardKeepsTextsReferencesAndNumericsSeparate(){
  val parsed=ReportParser.parse("HBSAG 乙肝表面抗原 阴性 - 阴性\nUR-LEU 尿白细胞酯酶 2+ - 阴性\nUR-SG 尿比重 1.025 - 1.003-1.030\nST-COLOR 粪便颜色 棕黄色 -\nHPV16-DNA 人乳头瘤病毒16型核酸 阳性（+） - 阴性（-）")
  assertEquals(5,parsed.size);assertEquals(listOf("阴性","2+","1.025","棕黄色","阳性（+）"),parsed.map{it.textValue})
  assertEquals("阴性",parsed[0].referenceRaw);assertEquals("",parsed[0].unit);assertNull(parsed[0].value);assertNull(parsed[1].value);assertEquals(1.025,parsed[2].value!!,0.0)
  assertEquals("HPV16-DNA",parsed[4].metricKey);assertEquals("阴性（-）",parsed[4].referenceRaw)
 }
 @Test fun lettersAndDigitsInNamesNeverBecomeResult(){val x=ReportParser.parse("HPV18-DNA 人乳头瘤病毒18型核酸 阴性 - 阴性").single();assertNull(x.value);assertEquals("阴性",x.textValue)}
 @Test fun draftCanSaveAndReferenceSurvivesOtherEdits(){val p=ReportParser.parse("HBSAB 乙肝表面抗体 阳性 - 阴性").single();val d=DraftRow.from(p);assertTrue(d.valid());assertEquals("阴性",d.copy(unit="IU/L").parsed().referenceRaw);assertEquals("阳性",d.copy(referenceRaw="阳性").parsed().referenceRaw)}
 @Test fun freeTextTabRoundTripKeepsSpaces(){val p=ReportParser.parse("ST-CONSISTENCY\t粪便性状\t软 含少量黏液\t-\t").single();assertEquals("软 含少量黏液",p.textValue);assertEquals("",p.unit)}
 @Test fun sourceAbbreviationsResolveOnlyWithExactChineseAntibodyIdentity(){assertEquals("TPAB",ReportParser.parse("TP 梅毒螺旋体特异性抗体 阴性 - 阴性").single().metricKey);assertEquals("HIVAB",ReportParser.parse("HIV 人免疫缺陷病毒抗体 阴性 - 阴性").single().metricKey);assertEquals("TP",ReportParser.key("TP"))}
 @Test fun editTextNeverStoresFakeNumericOrChangesIdentity(){val old=row("阴性");val next=old.withEditedResult("阳性（+）");assertNull(next.value);assertNull(next.normalizedValue);assertEquals(old.metricKey,next.metricKey);assertEquals(old.referenceRaw,next.referenceRaw);assertEquals("阳性（+）",next.textValue);assertTrue(next.editedByUser)}
 @Test fun numericalEditKeepsSourcePrecision(){val next=row("阴性").withEditedResult("0.250");assertEquals("0.250",next.textValue);assertEquals(0.250,next.normalizedValue!!,0.0)}
 @Test fun numericResultCanPreserveTextualReference(){val p=ReportParser.parse("HIVAB 人免疫缺陷病毒抗体 0.50 S/CO 阴性").single();assertEquals(0.5,p.value!!,0.0);assertEquals("0.50",p.textValue);assertEquals("阴性",p.referenceRaw)}
 @Test fun identicalDescriptionCanMatchButNoInterpretationIsInvented(){assertEquals(ResultStatus.NORMAL,row("棕黄色","棕黄色").status());assertEquals(ResultStatus.UNKNOWN,row("软","成形").status())}
 @Test fun unrelatedReferenceVocabulariesDoNotForceJudgment(){assertEquals(ResultStatus.UNKNOWN,row("阴性","正常").status())}
}
