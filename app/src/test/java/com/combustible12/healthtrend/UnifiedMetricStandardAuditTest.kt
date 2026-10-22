package com.combustible12.healthtrend

import org.junit.Assert.*
import org.junit.Test

/** Synthetic dictionary checks; not a substitute for per-analyte clinical review. */
class UnifiedMetricStandardAuditTest {
 @Test fun ca724UsesHyphenatedStandardNameAndKeepsLegacyAliases(){
  assertEquals("糖类抗原72-4",UnifiedMetricStandard.definition("CA724")!!.name)
  listOf("糖类抗原724","糖类抗原72-4","糖类抗原CA72-4","糖类抗原CA724").forEach{name->
   assertEquals(name,"CA724",ReportParser.key(name))
   assertEquals("糖类抗原72-4",standardReportMetricName("CA724",name,"U/mL"))
   val draft=pastedReportDraft("合成医院\n肿瘤标志物\n2001-01-02\nCA724 $name <1.00 U/mL 0.00-12.10")
   assertTrue(draft.valid())
   assertEquals("CA724",draft.rows.single().key)
   assertEquals("糖类抗原72-4",draft.rows.single().name)
   assertEquals("<1.00",draft.rows.single().text)
   assertEquals("0.00-12.10",draft.rows.single().rangeRaw())
  }
 }

 @Test fun albuminGlobulinRatioAliasPreservesDimensionlessSourceRange(){
  assertEquals("A/G",UnifiedMetricStandard.resolveName("白球比例"))
  assertEquals("A/G",ReportParser.key("白球比例"))
  assertEquals("A/G",ReportParser.key("白球比"))
  val parsed=ReportParser.parse("A/G 白球比例 1.750 - 1.0-2.5").single()
  assertEquals("A/G",parsed.metricKey);assertEquals("白球比例",parsed.displayName)
  assertEquals("1.750",parsed.textValue);assertEquals("",parsed.unit)
  assertEquals(1.0,parsed.referenceLow!!,0.0);assertEquals(2.5,parsed.referenceHigh!!,0.0)
  assertEquals("1.0-2.5",parsed.referenceRaw)
  assertEquals("",UnifiedMetricStandard.definition("A/G")!!.unit)
  assertNotEquals("ALT/AST",parsed.metricKey);assertNotEquals("AST/ALT",parsed.metricKey)
 }
 @Test fun hpvNucleicAcidPanelPreservesGenotypesAndPooledIdentity(){
  val keys=setOf("HPV16-DNA","HPV18-DNA","HPV-OTHER12-DNA")
  assertEquals(keys,UnifiedMetricStandard.keysForType("HPV"))
  keys.forEach{key->val d=UnifiedMetricStandard.definition(key)!!;assertEquals(key,ReportParser.key(d.name));assertEquals("",d.unit)}
  assertEquals("HPV16-DNA",ReportParser.key("HPV16核酸"))
  assertEquals("HPV18-DNA",ReportParser.key("HPV18型核酸"))
  assertEquals("HPV-OTHER12-DNA",ReportParser.key("其他12种高危型HPV（不分型）"))
  assertNull(UnifiedMetricStandard.resolveName("高危型HPV"))
  assertNull(UnifiedMetricStandard.resolveName("HPV31核酸"))
  assertNotEquals(canonicalMetricKey("HPV16-DNA"),canonicalMetricKey("HPV16抗体"))
 }
 @Test fun urineAndStoolDefinitionsNeverCollapseIntoBloodOrChemistry(){
  val urine=UnifiedMetricStandard.keysForType("尿液分析");val stool=UnifiedMetricStandard.keysForType("粪便常规")
  assertEquals(10,urine.size);assertEquals(12,stool.size);assertTrue(urine.intersect(stool).isEmpty())
  (urine+stool).forEach{key->val d=UnifiedMetricStandard.definition(key)!!;assertEquals(key,ReportParser.key(d.name));assertEquals("",d.unit)}
  assertEquals("WBC",ReportParser.key("白细胞"));assertEquals("RBC",ReportParser.key("红细胞"))
  assertEquals("GLU",ReportParser.key("葡萄糖"));assertEquals("TP",ReportParser.key("总蛋白"))
  assertEquals("UR-LEU",ReportParser.key("尿白细胞酯酶"));assertEquals("ST-WBC",ReportParser.key("粪便白细胞"))
 }
 @Test fun infectiousScreeningHasDistinctIdentitiesAndNoInventedUnits(){
  val keys=setOf("HBSAG","HBSAB","HBEAG","HBEAB","HBCAB","HIVAB","HIVAGAB","HCVAB","TPAB","TPPA")
  assertEquals(keys,UnifiedMetricStandard.keysForType("传染病筛查"))
  keys.forEach{key->val d=requireNotNull(UnifiedMetricStandard.definition(key));assertEquals("",d.unit);assertEquals(key,ReportParser.key(d.name))}
  assertEquals("HBSAB",canonicalMetricKey("anti-HBs"));assertEquals("HIVAB",canonicalMetricKey("anti-HIV"))
  assertEquals("TPAB",ReportParser.key("梅毒螺旋体特异性抗体（定性）"))
  assertEquals("TP",ReportParser.key("TP"));assertEquals("总蛋白",UnifiedMetricStandard.definition("TP")!!.name)
  assertNotEquals(canonicalMetricKey("TP"),canonicalMetricKey("TPAB"))
  assertNotEquals(canonicalMetricKey("HIV"),canonicalMetricKey("HIVAB"))
 }
 @Test fun catalogueHasUniqueKeysChineseNamesAndNoBlindAliasMerging(){
  val keys=UnifiedMetricStandard.keys()
  assertTrue("Catalogue unexpectedly empty",keys.size>=100)
  val definitions=keys.associateWith{requireNotNull(UnifiedMetricStandard.definition(it))}
  assertEquals(keys.size,definitions.size)
  assertTrue("Every catalogue item needs a display name",definitions.values.all{it.name.isNotBlank()})
  assertTrue("Display names should be in Chinese",definitions.values.all{d->d.name.any{it.code in 0x3400..0x9fff}})
  assertEquals("No two canonical metrics may share an identical standard name",
   definitions.size,definitions.values.map{it.name}.toSet().size)
  val owner=mutableMapOf<String,MutableSet<String>>()
  definitions.forEach{(key,definition)->
   (definition.aliases+definition.name).forEach{alias->
    assertTrue("Blank alias on $key",alias.isNotBlank())
    owner.getOrPut(alias){mutableSetOf()}.add(key)
   }
  }
  owner.filterValues{it.size>1}.forEach{(alias,_)->
   assertNull("An ambiguous alias cannot be auto-selected: $alias",UnifiedMetricStandard.resolveName(alias))
  }
 }
 @Test fun nearNamesNeverCollapseBySubstring(){
  val distinct=listOf("AST","ALT","AST/ALT","ALT/AST","NEUT#","NEUT%",
   "LYMPH#","LYMPH%","RDW-CV","RDW-SD","PCT","PCTIN","CRP","HSCRP","CK","CKMB","CKMB/CK")
  assertEquals(distinct.size,distinct.map{canonicalMetricKey(it)}.toSet().size)
  distinct.forEach{assertNotNull("Missing distinct definition: $it",UnifiedMetricStandard.definition(it))}
 }
 @Test fun everyStandardMetricCodeIsAcceptedAsAnExplicitIdentity(){
  UnifiedMetricStandard.keys().forEach{key->
   assertEquals("Explicit identifier not recognized: $key",key,ReportParser.key(key))
  }
 }
 @Test fun clinicalOppositesNeverMerge(){
  val groups=listOf(
   listOf("RDW-CV","RDW-SD"),listOf("PDW-CV","PDW-SD"),
   listOf("NEUT#","NEUT%"),listOf("LYMPH#","LYMPH%"),
   listOf("MONO#","MONO%"),listOf("EOS#","EOS%"),
   listOf("BASO#","BASO%"),listOf("NRBC#","NRBC%"),
   listOf("PCT","PCTIN"),listOf("CRP","HSCRP"),
   listOf("CK","CKMB","CKMB/CK"),listOf("AST","ALT","AST/ALT","ALT/AST"),
   listOf("CTNI","CTNT")
  )
  groups.forEach{keys->assertEquals("Identity merge: $keys",keys.size,keys.map(::canonicalMetricKey).toSet().size)}
 }
 @Test fun noUniversalReferenceIntervalIsDefinedInTheCatalogue(){
  // Intervals belong to each patient report and must not be fabricated here.
  assertTrue(UnifiedMetricStandard.keys().all{it.isNotBlank()})
  assertEquals("",UnifiedMetricStandard.definition("DDIMER")!!.unit)
  assertEquals("",UnifiedMetricStandard.definition("VITD25")!!.unit)
 }
}
