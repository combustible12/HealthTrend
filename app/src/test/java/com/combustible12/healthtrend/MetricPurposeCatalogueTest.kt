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
  assertEquals("白细胞计数",MetricPurposeCatalogue.resolveKey("白细胞计数")?.let{UnifiedMetricStandard.definition(it)?.name})
  val standards=UnifiedMetricStandard.keys()
  assertEquals(147,standards.size)
  assertEquals(standards,reviewed.map{it[0]}.toSet())
  assertEquals(standards.size,reviewed.size)
  reviewed.forEach { row ->
   val (key,name,summary,detail)=row
   assertEquals(name,UnifiedMetricStandard.definition(key)!!.name)
   assertEquals("Wrong summary for $key",summary,metricPurpose(key))
   assertEquals("Wrong detail for $key",detail,metricPurposeDetail(key))
   assertTrue("Too long: $key",summary.length<=24)
   assertTrue("Missing specific detail: $key",detail.length>summary.length+12)
   if(LegacyMetricPurpose.summary(key)==null)assertTrue(detail.startsWith(name+"："))
   else assertEquals("Legacy detail changed: $key",LegacyMetricPurpose.detail(key),detail)
  }
 }
 @Test fun summariesThatExistedBeforeTheCatalogueStayVerbatim() {
  val legacy=mapOf(
   "WBC" to "↑感染/炎症 / ↓感染防御不足", "NEUT#" to "↑细菌感染/炎症 / ↓感染防御不足", "NEUT%" to "↑细菌感染/炎症",
   "LYMPH#" to "↑病毒感染等", "LYMPH%" to "↑病毒感染等", "MONO#" to "↑感染/炎症", "MONO%" to "↑感染/炎症",
   "EOS#" to "↑过敏/寄生虫感染", "EOS%" to "↑过敏/寄生虫感染", "BASO#" to "↑过敏/炎症", "BASO%" to "↑过敏/炎症",
   "RBC" to "↓贫血 / ↑脱水等", "HGB" to "↓贫血", "HCT" to "↓贫血 / ↑脱水等", "MCV" to "↑缺B12/叶酸等 / ↓缺铁等",
   "MCH" to "↓缺铁等", "MCHC" to "↓缺铁等", "RDW-CV" to "↑贫血原因参考", "RDW-SD" to "↑贫血原因参考",
   "PLT" to "↑血栓 / ↓出血", "PCT" to "血小板辅助指标", "MPV" to "血小板辅助指标", "PDW-CV" to "血小板辅助指标",
   "PDW-SD" to "血小板辅助指标", "P-LCR" to "血小板辅助指标", "P-LCC" to "血小板辅助指标", "NRBC%" to "↑造血异常等", "NRBC#" to "↑造血异常等",
   "ALT" to "↑肝细胞损伤", "AST" to "↑肝脏/肌肉损伤", "GGT" to "↑肝胆异常", "ALP" to "↑胆道/骨骼异常",
   "TBIL" to "↑肝胆/溶血异常", "DBIL" to "↑胆汁排出不畅", "IBIL" to "↑溶血/肝脏处理异常", "TBA" to "↑肝胆异常",
   "TP" to "↓营养不足/肝合成减少", "ALB" to "↓营养不足/肝合成减少", "PA" to "↓营养不足/肝合成减少",
   "GLOB" to "↑炎症/免疫异常", "A/G" to "↓肝脏/炎症问题", "AST/ALT" to "肝功能辅助指标", "CHE" to "↓肝脏合成能力不足",
   "UREA" to "↑肾脏排出减少/脱水", "CREA" to "↑肾功能下降", "UA" to "↑痛风/肾脏负担",
   "HBSAG" to "阳性提示可能存在乙肝病毒感染", "HBSAB" to "阳性提示存在乙肝表面抗体", "HBEAG" to "阳性可提示乙肝病毒复制较活跃",
   "HBEAB" to "阳性提示存在乙肝e抗体，需结合其他指标", "HBCAB" to "阳性提示曾经或目前感染过乙肝病毒",
   "HIVAB" to "筛查阳性需要进一步确证", "TPAB" to "阳性需结合其他梅毒检查判断"
  )
  legacy.forEach { (key,summary) -> assertEquals(key,summary,metricPurpose(key)) }
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
   assertTrue(metricPurpose(it).startsWith("↑"))
   assertTrue(metricPurposeDetail(it).contains("不能单独确诊"))
   assertFalse(metricPurpose(it).contains("患癌"))
  }
 }
 @Test fun newlyAddedSummariesExplainResultsInsteadOfRepeatingCategories() {
  val meaningless=setOf("凝血功能评估","肿瘤监测辅助指标","电解质平衡指标","甲状腺功能评估","血脂代谢评估")
  UnifiedMetricStandard.keys().filter{LegacyMetricPurpose.summary(it)==null}.forEach {
   assertFalse("Meaningless category label remains: $it",metricPurpose(it) in meaningless)
  }
 }
 @Test fun newlyAddedSummariesAvoidVagueOrTautologicalWording() {
  val vague=listOf("提示","倾向","相关","常见于","可见于")
  val tautological=listOf("数量增加","比例增加","占比增加","水平升高","数值升高")
  MetricPurposeCatalogue.keys().filter {
   LegacyMetricPurpose.summary(it)==null && LegacyMetricPurpose.qualitativeSummary(it)==null
  }.forEach { key ->
   val summary=metricPurpose(key)
   vague.forEach { word -> assertFalse("Vague wording '$word' remains: $key / $summary",summary.contains(word)) }
   tautological.forEach { word -> assertFalse("Tautology '$word' remains: $key / $summary",summary.contains(word)) }
  }
 }
}
