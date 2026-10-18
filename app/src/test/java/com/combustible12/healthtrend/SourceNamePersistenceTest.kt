package com.combustible12.healthtrend
import org.junit.Test
import org.junit.Assert.*
class SourceNamePersistenceTest {
 @Test fun unitlessOneSidedReferencesAreNotMistakenForSecondResults(){
  listOf("NRBC%","NRBC#","PCT").forEach{key->
   val name=UnifiedMetricStandard.definition(key)!!.name
   listOf("<=9.000",">=0.000","≤9.000","≥0.000","<9.000",">0.000").forEach{reference->
    val source="$key $name 0.000 $reference"
    val row=ReportParser.parse(ReportParser.bindExplicitLeadingIdentities(source)).single()
    assertEquals(source,key,row.metricKey);assertEquals(source,"0.000",row.textValue)
    assertEquals(source,"",row.unit);assertEquals(source,reference,row.referenceRaw)
   }
  }
  val bad=pastedReportDraft("合成医院\n甲状腺功能\n2001-01-02\nFT4 游离-T4 4 13.00 pmol/L 9-20")
  assertFalse(bad.valid());assertEquals("",bad.rows.single().text)
 }
 @Test fun recognizedChineseRowsWithoutResultsCannotSilentlyDisappear(){
  listOf("游离-T4","游离-T4 pmol/L","*游离-T4 pmol/L 9.00~20.00").forEach{incomplete->
   val raw="合成医院\n甲状腺功能\n2001-01-02\nFT3 游离-T3 4.20 pmol/L 3-6\n$incomplete\nTSH 促甲状腺激素 2.100 mIU/L 0.3-5"
   val d=pastedReportDraft(raw)
   assertEquals(raw,3,d.rows.size);assertEquals("FT3",d.rows.first().key);assertEquals("TSH",d.rows.last().key)
   assertTrue(d.rows[1].key.startsWith("待核对:"));assertTrue(d.rows[1].raw.contains("游离-T4"))
   assertFalse(d.valid())
  }
 }
 @Test fun administrativeTimestampOnSameLineCannotHideClinicalDate(){
  listOf("检查日期 2001-01-02 打印日期 2001-01-03",
   "打印日期 2001-01-03 检查日期 2001-01-02",
   "采样时间 2001-01-02 08:30 审核时间 2001-01-03 09:00",
   "出生日期 1970-01-01 报告日期 2001-01-02").forEach{source->
   assertEquals(source,"2001-01-02",ReportMetadata.extract(source,emptyList()).date)
  }
  assertEquals("",ReportMetadata.extract("检查日期 2001-01-02 报告日期 2001-01-03 打印日期 2001-01-04",emptyList()).date)
  assertEquals("",ReportMetadata.extract("打印日期 2001-01-03 审核日期 2001-01-02",emptyList()).date)
 }
 @Test fun numericComparisonsKeepTheSameMeaningAcrossImportAndEditing(){
  listOf("<2.340",">2.340","≤2.340","≥2.340","<=2.340",">=2.340","+2.340","-2.340","2.340e-3").forEach{value->
   assertNotNull(value,LabResultValues.numeric(value))
   listOf("CA724 糖类抗原CA72-4 $value U/mL 0.00~9.00",
    "CA724\t糖类抗原CA72-4\t$value\tU/mL\t0.00~9.00",
    "CA724 糖类抗原CA72-4 $value 0.00~9.00 U/mL").forEach{line->
    val row=ReportParser.parse(ReportParser.bindExplicitLeadingIdentities(line)).single()
    assertEquals(line,"CA724",row.metricKey);assertEquals(line,value,row.textValue)
    assertEquals(line,LabResultValues.numeric(value),row.value)
    val edited=DraftRow.from(row).parsed()
    assertEquals(line,row.value,edited.value);assertEquals(line,row.comparator,edited.comparator)
   }
  }
 }
 @Test fun knownNamesAndCodeOnlyRowsKeepIdentityWithoutSourceNameReplacement(){
  val failures=mutableListOf<String>();var count=0
  UnifiedMetricStandard.keys().forEach{key->
   val d=UnifiedMetricStandard.definition(key)!!
   val unit=d.unit.ifBlank{"-"}
   val names=(d.aliases+d.name+ReportParser.builtInAliasNames(key))
    .filter{ReportParser.key(it)==key&&it.any{ch->ch.code in 0x3400..0x9fff}}.distinct()
   names.forEach{name->
    val line="$name 2.340 $unit 0.00~9.00"
    count++
    val rows=ReportParser.parse(line)
    if(rows.size!=1||rows.single().let{it.metricKey!=key||it.displayName!=name||it.textValue!="2.340"})failures+="$line -> $rows"
   }
   val codeOnly="$key 2.340 $unit 0.00~9.00"
   count++
   val rows=ReportParser.parse(ReportParser.bindExplicitLeadingIdentities(codeOnly))
   if(rows.size!=1||rows.single().let{it.metricKey!=key||it.displayName!=d.name||it.textValue!="2.340"})failures+="$codeOnly -> $rows"
  }
  println("MODULE_NAME_CODE_MATRIX cases=$count failures=${failures.size}")
  assertTrue(failures.take(30).joinToString("\n"),failures.isEmpty())
 }
 @Test fun textResultsAndEmptyUnitsAgreeAcrossLayouts(){
  listOf("HBSAG","HBSAB","TPPA","UR-PRO").forEach{key->
   val name=UnifiedMetricStandard.definition(key)!!.name
   listOf("阴性(-)","阳性(+)","弱阳性","++","未检出").forEach{value->
    listOf(" ","\t"," | ").forEach{separator->
     val raw=listOf(key,name,value,"-","阴性(-)").joinToString(separator)
     val row=pastedReportDraft("合成医院\n传染病筛查\n2001-01-02\n$raw").rows.single()
     assertEquals(raw,key,row.key);assertEquals(raw,name,row.name)
     assertEquals(raw,value,row.text);assertEquals(raw,"",row.unit);assertEquals(raw,"阴性(-)",row.referenceRaw)
    }
   }
  }
 }
 @Test fun catalogueIdentityAndFieldsAreInvariantAcrossImportLayouts(){
  val failures=mutableListOf<String>();var count=0
  UnifiedMetricStandard.keys().forEach{key->
   val definition=UnifiedMetricStandard.definition(key)!!
   val names=(definition.aliases+definition.name+ReportParser.builtInAliasNames(key))
    .filter{ReportParser.key(it)==key&&it.any{ch->ch.code in 0x3400..0x9fff}}.distinct()
   names.forEach{name->listOf("2.340","<2.340","-2.340","0.000").forEach{value->
    val unit=definition.unit.ifBlank{"-"}
    val reference="0.00~9.00"
    listOf(" ","\t"," | ").forEach{separator->
     count++
     val line=listOf(key,name,value,unit,reference).joinToString(separator)
     val rows=pastedReportDraft("合成医院\n生化\n2001-01-02\n$line").rows
     if(rows.size!=1||rows.single().let{it.key!=key||it.name!=name||it.text!=value||it.unit!=unit.takeUnless{it=="-"}.orEmpty()||it.referenceRaw!=reference})failures+="$line -> $rows"
    }
   }}
  }
  println("MODULE_LAYOUT_MATRIX cases=$count failures=${failures.size}")
  assertTrue(failures.take(30).joinToString("\n"),failures.isEmpty())
 }
 @Test fun completeNamesRemainIndependentFromIdentity(){
  val cases=listOf("FT3" to "游离-T3","FT4" to "游离-T4","TSH" to "促甲状腺素（III代）","CA724" to "糖类抗原CA72-4","GGT" to "γ-谷氨酰转肽酶","A/G" to "白蛋白/球蛋白","FDP" to "纤维蛋白（原）降解产物","AST/ALT" to "谷草/谷丙比值")
  cases.forEach{(key,name)->
   val raw="$key\t$name\t2.340\t-\t1.0-3.0"
   val row=ReportParser.parse(ReportParser.bindExplicitLeadingIdentities(raw)).single()
   assertEquals(raw,key,row.metricKey);assertEquals(raw,name,row.displayName)
   assertEquals("2.340",row.textValue)
  }
 }
 @Test fun qualitativeSourceNameKeepsItsParenthesizedMethod(){
  val row=ReportParser.parse("TPPA\t梅毒螺旋体抗体（TPPA）\t阴性\t-\t阴性").single()
  assertEquals("TPPA",row.metricKey);assertEquals("梅毒螺旋体抗体（TPPA）",row.displayName)
 }
 @Test fun conflictingDatesOnOneLineCannotSilentlyChooseFirst(){
  val raw="检查日期 2001-01-02 报告日期 2001-01-03"
  val meta=ReportMetadata.extract(raw,emptyList())
  assertEquals("",meta.date);assertTrue("date" in meta.uncertain)
 }
 @Test fun explicitPastedDateIsPreservedWithoutGuessingFromAnalytes(){
  val raw="合成医院\n甲状腺功能\n2001-01-02\nFT3 游离-T3 4.20 pmol/L 3-6"
  val draft=pastedReportDraft(raw)
  assertEquals("2001-01-02",draft.date);assertEquals("游离-T3",draft.rows.single().name)
 }
 @Test fun leadingReportStarsAreNotNameConflictsOrResultDigits(){
  val cases=listOf(Triple("FT3","游离-T3","4.20"),Triple("FT4","游离-T4","13.00"),Triple("TSH","促甲状腺素(III代)","2.100"))
  cases.forEach{(key,name,value)->listOf("*","＊","★","☆").forEach{star->
   listOf("$key $star$name $value pmol/L 1.0~20.0 pmol/L","$key\t$star$name\t$value\tpmol/L\t1.0~20.0").forEach{raw->
    val row=ReportParser.parse(ReportParser.bindExplicitLeadingIdentities(raw),"甲状腺功能").single()
    assertEquals(raw,key,row.metricKey);assertEquals(raw,name,row.displayName);assertEquals(raw,value,row.textValue)
   }
  }}
 }
 @Test fun removingLeadingStarsDoesNotAcceptUnknownInternalSymbols(){
  val row=ReportParser.parse(ReportParser.bindExplicitLeadingIdentities("FT4 *游离/T4 13.00 pmol/L 1-20"),"甲状腺功能").single()
  assertTrue(row.metricKey.startsWith("待核对:"))
 }
 @Test fun allCatalogueNamesUseSameMarkerPolicyAcrossEntryLayouts(){
  val failures=mutableListOf<String>();var count=0
  UnifiedMetricStandard.keys().forEach{key->
   val d=UnifiedMetricStandard.definition(key)!!
   val names=(d.aliases+d.name+ReportParser.builtInAliasNames(key)).filter{ReportParser.key(it)==key}.distinct()
   names.forEach{name->
    val unit=d.unit.ifBlank{"-"}
    val baseline=ReportParser.parse(ReportParser.bindExplicitLeadingIdentities("$key\t$name\t2.340\t$unit\t0-9")).single()
    listOf("*","＊","★","☆","* ＊ ","\uFEFF* ").forEach{marker->
     listOf("$key $marker$name 2.340 $unit 0-9","$marker$key $name 2.340 $unit 0-9","$key\t$marker$name\t2.340\t$unit\t0-9").forEach{raw->
      count++
      val rows=ReportParser.parse(ReportParser.bindExplicitLeadingIdentities(raw))
      if(rows.size!=1||rows.single().let{it.metricKey!=baseline.metricKey||it.displayName!=baseline.displayName||it.textValue!=baseline.textValue||it.unit!=baseline.unit||it.referenceRaw!=baseline.referenceRaw})failures+="$raw -> $rows"
     }
    }
   }
  }
  println("MODULE_MARKER_MATRIX cases=$count failures=${failures.size}")
  assertTrue(failures.take(30).joinToString("\n"),failures.isEmpty())
 }
 @Test fun symbolicIdentityConflictsRetainMeasurementsAndStayBlocked(){
  listOf("FT4" to "游离/T4","A/G" to "白蛋白/UNKNOWN","CA724" to "糖类抗原72+4").forEach{(key,name)->
   val row=ReportParser.parse(ReportParser.bindExplicitLeadingIdentities("$key\t$name\t<2.340\tpmol/L\t0-9")).single()
   assertTrue(row.metricKey.startsWith("待核对:"));assertEquals(name,row.displayName)
   assertEquals("<2.340",row.textValue);assertEquals("<",row.comparator)
   assertEquals("pmol/L",row.unit);assertEquals("0-9",row.referenceRaw)
   assertFalse(ReportDraft(hospital="合成医院",type="甲状腺功能",date="2001-01-02",rows=listOf(DraftRow.from(row))).valid())
   assertNotNull(metricIdentityNameProblem(key,name,"pmol/L"))
  }
 }
 @Test fun failedCodeBearingRowIsRetainedRatherThanDropped(){
  val raw="合成医院\n甲状腺功能\n2001-01-02\nFT3 游离-T3 4.20 pmol/L 3-6\nFT4 游离-T4"
  val d=pastedReportDraft(raw)
  assertEquals(2,d.rows.size);assertFalse(d.valid())
  assertTrue(d.rows.last().key.startsWith("待核对:"));assertTrue(d.rows.last().raw.contains("游离-T4"))
 }
 @Test fun failedRowsKeepTheirOriginalPositionAndCannotDisappear(){
  val raw="合成医院\n甲状腺功能\n2001-01-02\nFT3 游离-T3 4.20 pmol/L 3-6\nFT4\nTSH 促甲状腺激素 2.100 mIU/L 0.3-5"
  val d=pastedReportDraft(raw)
  assertEquals(3,d.rows.size);assertEquals("FT3",d.rows[0].key)
  assertTrue(d.rows[1].key.startsWith("待核对:未解析行:"));assertEquals("TSH",d.rows[2].key)
  assertFalse(d.valid())
 }
 @Test fun mixedReportSourcesCannotSilentlyChooseHospitalOrDate(){
  val raw="合成甲医院\n甲状腺功能\n2001-01-02\nFT3 游离-T3 4.20 pmol/L 3-6\n合成乙医院\n甲状腺功能\n2001-01-03\nFT4 游离-T4 13.00 pmol/L 9-20"
  val d=pastedReportDraft(raw)
  assertEquals("",d.hospital);assertEquals("",d.date);assertFalse(d.valid());assertEquals(2,d.rows.size)
 }
}
