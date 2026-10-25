package com.combustible12.healthtrend

import org.junit.Assert.*
import org.junit.Test

class MetricPurposeCatalogueTest {
 private val reviewed by lazy {
  javaClass.getResourceAsStream("/metric-purpose-review.tsv")!!.bufferedReader().useLines { lines ->
   lines.drop(1).filter{it.isNotBlank()}.map{it.split('\t')}.toList()
  }
 }
 @Test fun everyActualStandardHasTheReviewedCodeNameSummaryAndDetail() {
  val standards=UnifiedMetricStandard.keys()
  assertEquals(147,standards.size)
  assertEquals(standards,reviewed.map{it[0]}.toSet())
  assertEquals(standards.size,reviewed.size)
  reviewed.forEach { row ->
   val (key,name,summary,detail)=row
   assertEquals(name,UnifiedMetricStandard.definition(key)!!.name)
   assertEquals("Wrong summary for $key",summary,metricPurpose(key))
   assertEquals("Wrong detail for $key",detail,metricPurposeDetail(key))
   assertTrue("Too long: $key",summary.length<=16)
   assertTrue("Missing specific detail: $key",detail.length>summary.length+12)
   assertTrue(detail.startsWith(name+"："))
  }
 }
 @Test fun everyExistingCodeAliasResolvesTheSameEntryWithoutChangingIdentity() {
  MetricDictionary.sourceCodeAliases().forEach { alias ->
   val canonical=canonicalMetricKey(alias)
   assertNotNull(alias,MetricPurposeCatalogue.find(canonical))
   assertSame(alias,MetricPurposeCatalogue.find(canonical),MetricPurposeCatalogue.find(alias.lowercase()))
   assertEquals(canonical,canonicalMetricKey(alias))
  }
 }
 @Test fun registeredNamesAndAliasesResolveExactlyAndNeverBySubstring() {
  UnifiedMetricStandard.keys().forEach { key ->
   val d=UnifiedMetricStandard.definition(key)!!
   (d.aliases+d.name+ReportParser.builtInAliasNames(key)).forEach { alias ->
    // Only unambiguous exact labels may select an educational entry.
    val owners=MetricPurposeCatalogue.keys().filter { candidate ->
     val def=UnifiedMetricStandard.definition(candidate)
     (def?.aliases.orEmpty()+listOfNotNull(def?.name)+ReportParser.builtInAliasNames(candidate))
      .any{UnifiedMetricStandard.normalizedName(it)==UnifiedMetricStandard.normalizedName(alias)}
    }
    if(owners.size==1) assertSame("$key / $alias",MetricPurposeCatalogue.find(key),MetricPurposeCatalogue.find(alias))
   }
   assertNull(MetricPurposeCatalogue.find(d.name+"自定义后缀"))
  }
 }
 @Test fun oldKeysKeepDetailsAndDoNotGuessDistributionWidthUnits() {
  listOf("RDW","PDW","AMY","HIV","SYPHILIS").forEach { assertNotNull(it,MetricPurposeCatalogue.find(it)) }
  assertNotEquals(MetricPurposeCatalogue.find("RDW"),MetricPurposeCatalogue.find("RDW-CV"))
  assertNotEquals(MetricPurposeCatalogue.find("PDW"),MetricPurposeCatalogue.find("PDW-SD"))
  assertTrue(metricPurposeDetail("RDW").contains("原报告单位"))
  assertSame(MetricPurposeCatalogue.find("HIVAB"),MetricPurposeCatalogue.find("HIV"))
 }
 @Test fun importantSimilarMetricsKeepCorrectMedicalMeaning() {
  val expected=mapOf("PCT" to "血小板", "PCTIN" to "细菌感染", "ALP" to "骨代谢", "MYO" to "骨骼肌",
   "HBSAB" to "疫苗", "HBCAB" to "既往或当前感染", "HBEAB" to "不能据此认定病毒已清除",
   "HIVAB" to "确证", "HIVAGAB" to "确证", "HCVAB" to "HCV RNA", "CREA" to "eGFR",
   "UREA/CREA" to "不能直接套用尿素氮", "DDIMER" to "FEU与DDU", "FDP" to "与D-二聚体不同",
   "CKMB" to "活性与质量浓度", "AST" to "肌肉", "ALT/AST" to "互为反向", "HBA1C" to "2至3个月")
  expected.forEach { (key,term) -> assertTrue("$key must explain $term",metricPurposeDetail(key).contains(term)) }
  listOf("PCT" to "PCTIN","AST" to "AST/ALT","FT4" to "T4","P-LCR" to "P-LCC","RDW-CV" to "RDW-SD","PDW-CV" to "PDW-SD").forEach { (a,b) ->
   assertNotEquals(metricPurposeDetail(a),metricPurposeDetail(b))
  }
 }
 @Test fun quantitativeAndQualitativeConsumersShareOneEntry() {
  listOf("WBC","CEA","HBsAb","ANTI-HCV","HIV-AB","TPPA","HPV16-DNA","UR-GLU").forEach { key ->
   val entry=MetricPurposeCatalogue.forDisplay(key)
   assertEquals(entry.summary,metricPurpose(key))
   assertEquals(entry.detail,metricPurposeDetail(key))
  }
  listOf("HBsAb","HIV","HIVAB","HIVAGAB","TPPA","HCVAB").forEach { assertEquals(metricPurposeDetail(it),qualitativeMetricPurpose(it)) }
 }
 @Test fun unknownAndConflictedCustomKeysHaveExplicitNonDiagnosticFallback() {
  listOf("UNKNOWN-METRIC","待核对:代码名称冲突:AST","自定义CEA","CKMB-MASS").forEach {
   assertNull(MetricPurposeCatalogue.find(it))
   assertSame(MetricPurposeCatalogue.unavailable,MetricPurposeCatalogue.forDisplay(it))
   assertEquals("指标说明待补充",metricPurpose(it))
   assertTrue(metricPurposeDetail(it).contains("不"))
  }
 }
 @Test fun tumorMarkersDoNotClaimDiagnosisAndNormalResultsAreNotCalledCancer() {
  UnifiedMetricStandard.keysForType("肿瘤标志物").forEach {
   assertTrue(metricPurpose(it).contains("辅助"))
   assertTrue(metricPurposeDetail(it).contains("不能单独确诊"))
   assertFalse(metricPurpose(it).contains("患癌"))
  }
 }
}
