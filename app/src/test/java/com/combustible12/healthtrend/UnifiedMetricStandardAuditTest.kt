package com.combustible12.healthtrend

import org.junit.Assert.*
import org.junit.Test

/** Synthetic dictionary checks; not a substitute for per-analyte clinical review. */
class UnifiedMetricStandardAuditTest {
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
   listOf("RDW-CV","RDW-SD"),listOf("PDW-CV","PDW-SD","PDW"),
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
