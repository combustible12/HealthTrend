package com.combustible12.healthtrend
import org.junit.Assert.*
import org.junit.Test

class ReportParserTest {
 @Test fun allMetricsAndPrintedRangesAreRetained(){
  val rows=ReportParser.parse("WBC 3.75 ×10^9/L 3.5-9.5\nNEUT% 51.2 % 40-75\nNEUT# 1.92 ×10^9/L 2-7\nLDH 189 U/L 120-250\nHGB 102 g/L 113-151")
  assertEquals(5,rows.size);assertEquals("LDH",rows[3].metricKey);assertEquals(120.0,rows[3].referenceLow!!,0.0);assertEquals(51.2,rows[1].value!!,0.0);assertFalse(rows[1].primary);assertEquals("NEUT#",rows[2].metricKey)
 }
 @Test fun digitInMetricNameIsNotAValue(){val x=ReportParser.parse("VitaminB12 阴性").single();assertEquals("阴性",x.textValue);assertNull(x.value);val n=ReportParser.parse("B12 130 100-600 pg/mL").single();assertEquals(130.0,n.value!!,0.0);assertEquals("pg/mL",n.unit)}
 @Test fun neverInventsReferenceRanges(){val x=ReportParser.parse("WBC 3.75").single();assertNull(x.referenceLow);assertNull(x.referenceHigh);assertEquals("",x.unit)}
 @Test fun unknownTextAndComparatorsAreNotDropped(){val rows=ReportParser.parse("HBsAg 阴性\nCRP <0.5 mg/L <5");assertEquals(2,rows.size);assertNull(rows[0].value);assertEquals("阴性",rows[0].textValue);assertEquals("<",rows[1].comparator);assertEquals(5.0,rows[1].referenceHigh!!,0.0)}
 @Test fun rowNumberAndScientificNotation(){val x=ReportParser.parse("1 WBC 3.75 ×10^9/L 3.5-9.5").single();assertEquals("WBC",x.metricKey);assertEquals(3.75,x.value!!,0.0)}
 @Test fun ChineseNamesAndAbbreviations(){assertEquals("WBC",ReportParser.key("白细胞计数(WBC)"));assertEquals("NEUT%",ReportParser.key("中性粒细胞百分比"));assertEquals("CREA",ReportParser.key("CRE"))}
 @Test fun historicalStatusAndEditedValue(){val r=LabResult("1","r","h","血常规",1,"HGB","血红蛋白",102.0,"g/L",113.0,151.0,1L);assertEquals(ResultStatus.LOW,r.status());val edit=r.withEditedValue(130.0);assertEquals(ResultStatus.NORMAL,edit.status());assertEquals(113.0,edit.referenceLowAtTest!!,0.0);assertTrue(edit.editedByUser);assertEquals(ResultStatus.UNKNOWN,r.copy(referenceLowAtTest=null,referenceHighAtTest=null).status())}
}
