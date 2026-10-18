package com.combustible12.healthtrend

import org.junit.Assert.*
import org.junit.Test

/** Full catalogue identity sweep; all result/interval values are synthetic. */
class CatalogueIdentitySweepTest {
 @Test fun manufacturerSpacedCodesNeverBecomeCalciumOrLoseTheirNumericSuffix(){
  val names=mapOf("CA72-4" to "CA724","CA19-9" to "CA199","CA15-3" to "CA153","CA12-5" to "CA125","CYFRA21-1" to "CYFRA21-1")
  names.forEach{(code,key)->
   val name=UnifiedMetricStandard.definition(key)!!.name
   val spaced=code.replace(Regex("(?<=[A-Za-z])(?=\\d)")," ")
   listOf(spaced,spaced.replace("-"," - "),code.replace("-"," -"),code.replace("-","- ")).forEach{form->
    listOf("$form $name <2.50 U/mL 0-9","$form <2.50 U/mL 0-9").forEach{source->
     val row=ReportParser.parse(ReportParser.bindExplicitLeadingIdentities(source)).single()
     assertEquals(source,key,row.metricKey);assertEquals(source,"<2.50",row.textValue)
    }
   }
  }
  listOf("CA 72-UNKNOWN","CA 72 / UNKNOWN","CYFRA 21-UNKNOWN").forEach{code->
   val row=ReportParser.parse(ReportParser.bindExplicitLeadingIdentities("$code 糖类抗原724 <2.50 U/mL 0-9")).single()
   assertTrue(code,row.metricKey.startsWith("待核对:"))
  }
  listOf("125","-125").forEach{value->
   val row=ReportParser.parse("CA $value mmol/L -200-200").single()
   assertEquals("CA",row.metricKey);assertEquals(value,row.textValue)
  }
  val ca125=ReportParser.parse("CA 125 糖类抗原125 <2.50 U/mL 0-9").single()
  assertEquals("CA125",ca125.metricKey);assertEquals("<2.50",ca125.textValue)
  val ambiguous=pastedReportDraft("合成医院\n生化\n2001-01-02\nCA 125 <2.50 U/mL 0-9")
  assertFalse(ambiguous.valid())
 }
 @Test fun unregisteredSymbolicNamesCannotReuseKnownPrefixesOrCodes(){
  listOf("ALB 白蛋白 / UNKNOWN","白蛋白 / UNKNOWN","CA724 糖类抗原72/4","CA724 糖类抗原72:4","CA724 糖类抗原(72/4)","CA724 糖类抗原72+4","GGT Y-谷氨酰转肽酶").forEach{name->
   val source="$name <2.50 g/L 0-9"
   val draft=pastedReportDraft("合成医院\n生化\n2001-01-02\n$source")
   assertEquals(source,1,draft.rows.size)
   assertFalse(source,draft.valid())
  }
  listOf("/","／",":","：","(",")","（","）").forEach{assertFalse(it,LabResultValues.valid(it))}
 }
 @Test fun punctuationInsideKnownNamesNeverBecomesResultOrTruncatesIdentity(){
  val failures=mutableListOf<String>();var cases=0
  UnifiedMetricStandard.keys().forEach{key->
   val d=UnifiedMetricStandard.definition(key)!!
   (d.aliases+d.name).filter{it.any{ch->ch in "-/()（）#%γ"||ch.isDigit()}}.forEach{name->
    val forms=listOf(name,name.replace("-","－"),name.replace("-"," - "),name.replace("/","／"),name.replace("/"," / "),name.replace('（','(').replace('）',')'),name.replace('(','（').replace(')','）'),name.replace("-","‑"),name.replace("γ","Γ"),name.replace("%","％").replace("#","＃"),name.replace(Regex("(?<=[A-Za-z])(?=\\d)")," ")).distinct()
    forms.forEach{label->listOf("$key $label <2.50 ${d.unit.ifBlank{"-"}} 0-9","$label <2.50 ${d.unit.ifBlank{"-"}} 0-9").forEach{source->
     cases++
     val rows=ReportParser.parse(ReportParser.bindExplicitLeadingIdentities(source))
     if(rows.size!=1||rows.single().metricKey!=key||rows.single().textValue!="<2.50"||rows.single().displayName=="待核对")failures+="$source -> ${rows.map{listOf(it.metricKey,it.displayName,it.textValue)}}"
    }}
   }
  }
  println("NAME_SYMBOL_SWEEP cases=$cases failures=${failures.size}")
  failures.forEach{println("NAME_SYMBOL_FAIL $it")}
  assertTrue(failures.joinToString("\n"),failures.isEmpty())
 }
 @Test fun everyKnownNameKeepsResultWhenReferenceRepeatsItsUnit(){
  var cases=0
  UnifiedMetricStandard.keys().forEach{key->
   val d=UnifiedMetricStandard.definition(key)!!
   if(d.unit.isBlank())return@forEach
   (d.aliases+d.name).forEach{name->
    val source="$key $name <2.50 ${d.unit} 0.00~15.00 ${d.unit}"
    val row=ReportParser.parse(ReportParser.bindExplicitLeadingIdentities(source)).single()
    assertEquals(source,key,row.metricKey)
    assertEquals(source,"<2.50",row.textValue)
    assertEquals(source,d.unit,row.unit)
    assertEquals(source,"0.00~15.00",row.referenceRaw)
    cases++
   }
  }
  println("REPEATED_REFERENCE_UNIT_SWEEP cases=$cases failures=0")
 }
 @Test fun unknownCompoundSuffixesAndGarbageCannotBindToKnownPrefixes(){
  listOf("AST / UNKNOWN","CK - UNKNOWN","CA / 999","CA - 999","NEUT# / UNKNOWN","AST ZZZ").forEach{code->
   val source="$code 2.340 - 0-9"
   val row=ReportParser.parse(ReportParser.bindExplicitLeadingIdentities(source)).single()
   assertTrue("Unknown code must stay pending: $source -> $row",row.metricKey.startsWith("待核对:"))
   assertTrue(metricNeedsReview(row,null))
   val draft=ReportDraft(hospital="合成医院",type="生化",date="2001-01-02",rows=listOf(DraftRow.from(row)))
   assertFalse("Unknown code must not save: $row",draft.valid())
  }
 }
 @Test fun similarClinicalIdentitiesCannotOverrideEachOtherByName(){
  val groups=listOf(listOf("AST","ALT","AST/ALT","ALT/AST"),listOf("CK","CKMB","CKMB/CK"),
   listOf("NEUT#","NEUT%"),listOf("LYMPH#","LYMPH%"),listOf("MONO#","MONO%"),listOf("EOS#","EOS%"),listOf("BASO#","BASO%"),listOf("NRBC#","NRBC%"),
   listOf("RDW-CV","RDW-SD"),listOf("PDW-CV","PDW-SD"),listOf("PCT","PCTIN"),listOf("CRP","HSCRP"),
   listOf("CA","CA125","CA153","CA199","CA724"),listOf("T3","FT3"),listOf("T4","FT4"),listOf("CTNI","CTNT"),
   listOf("AG","A/G"),listOf("WBC","ST-WBC","UR-LEU"),listOf("RBC","ST-RBC"),listOf("HIVAB","HIVAGAB"),listOf("HPV16-DNA","HPV18-DNA","HPV-OTHER12-DNA"))
  var cases=0
  groups.forEach{group->group.forEach{key->group.filter{it!=key}.forEach{other->
   val d=UnifiedMetricStandard.definition(key)!!;val name=UnifiedMetricStandard.definition(other)!!.name
   val row=ReportParser.parse(ReportParser.bindExplicitLeadingIdentities("$key $name 2.340 ${d.unit.ifBlank{"-"}} 0-9")).single()
   assertTrue("$key must not accept $other: $row",metricNeedsReview(row,null))
   assertNotEquals(other,row.metricKey)
   val manual=ReportDraft(hospital="合成医院",type="合成检验",date="2001-01-02",rows=listOf(DraftRow(key=key,name=name,text="2.340",unit=d.unit)))
   assertFalse("Manual editing must not bypass identity conflict: $key / $other",manual.valid())
   cases++
  }}}
  println("CONFUSABLE_SWEEP cases=$cases failures=0")
 }
 @Test fun completeCodeAliasesAndNegativeResultsRemainUnambiguous(){
  val failures=mutableListOf<String>();var cases=0
  MetricDictionary.sourceCodeAliases().forEach{alias->
   val key=canonicalMetricKey(alias);val d=UnifiedMetricStandard.definition(key)?:return@forEach
   listOf(alias,alias.replace("-"," - "),alias.replace("/"," / "),alias.replace("-","－")).distinct().forEach{code->
    cases++
    val source="$code 2.340 ${d.unit.ifBlank{"-"}} 0-9"
    val rows=ReportParser.parse(ReportParser.bindExplicitLeadingIdentities(source))
    if(rows.size!=1||rows.single().metricKey!=key||rows.single().textValue!="2.340")failures+="$source -> $rows"
   }
  }
  val calcium=ReportParser.parse(ReportParser.bindExplicitLeadingIdentities("CA -125 mmol/L -200-0")).single()
  assertEquals("CA",calcium.metricKey);assertEquals("-125",calcium.textValue)
  println("CODE_ALIAS_SWEEP cases=$cases failures=${failures.size}")
  failures.forEach{println("CODE_ALIAS_FAIL $it")}
  assertTrue(failures.joinToString("\n"),failures.isEmpty())
 }
 @Test fun sourceCodeTypographyNeverBecomesAnotherMetric(){
  val failures=mutableListOf<String>();var cases=0
  UnifiedMetricStandard.keys().forEach{key->
   val d=UnifiedMetricStandard.definition(key)!!
   val forms=listOf(key.replace("-","－"),key.replace("-","‑"),key.replace("-","−"),key.replace("-"," - "),key.replace("/"," / "),key.replace("/","："),key.replace("#","＃").replace("%","％"))+
    if(key.endsWith('#')||key.endsWith('%'))listOf(key.takeLast(1)+key.dropLast(1)) else emptyList()
   forms.distinct().forEach{code->
    cases++
    val source="$code 2.340 ${d.unit.ifBlank{"-"}} 0-9"
    val rows=ReportParser.parse(ReportParser.bindExplicitLeadingIdentities(source))
    if(rows.size!=1||rows.single().metricKey!=key||rows.single().textValue!="2.340"||rows.single().displayName=="待核对")failures+="$key | $source -> ${rows.map{listOf(it.metricKey,it.displayName,it.textValue)}}"
   }
  }
  println("TYPOGRAPHY_SWEEP cases=$cases failures=${failures.size}")
  failures.forEach{println("TYPOGRAPHY_FAIL $it")}
  assertTrue(failures.joinToString("\n"),failures.isEmpty())
 }
 @Test fun everyCatalogueNameAndCodeSurvivesProductionParsing(){
  val failures=mutableListOf<String>();var cases=0
  UnifiedMetricStandard.keys().forEach{key->
   val d=UnifiedMetricStandard.definition(key)!!
   val unit=d.unit.ifBlank{"-"}
   val names=(d.aliases+d.name+ReportParser.builtInAliasNames(key)).filter{UnifiedMetricStandard.resolveName(it)==key||ReportParser.key(it)==key}.distinct()
   val inputs=names.flatMap{name->listOf("$key $name 2.340 $unit 0-9","$name 2.340 $unit 0-9",listOf(key,name,"2.340",unit,"0-9").joinToString("\t"))}+
    listOf("$key 2.340 $unit 0-9","${key.lowercase()} 2.340 $unit 0-9")
   inputs.forEach{source->
    cases++
    val rows=ReportParser.parse(ReportParser.bindExplicitLeadingIdentities(source))
    val row=rows.singleOrNull()
    if(row==null||row.metricKey!=key||row.textValue!="2.340"||row.displayName=="待核对"||row.unit!=d.unit||row.referenceRaw!="0-9")
     failures+="$key | $source -> ${rows.map{listOf(it.metricKey,it.displayName,it.textValue,it.unit,it.referenceRaw)}}"
   }
  }
  println("CATALOGUE_SWEEP keys=${UnifiedMetricStandard.keys().size} cases=$cases failures=${failures.size}")
  failures.forEach{println("CATALOGUE_FAIL $it")}
  assertTrue(failures.joinToString("\n"),failures.isEmpty())
 }
}
