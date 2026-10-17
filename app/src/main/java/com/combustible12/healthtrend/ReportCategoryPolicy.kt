package com.combustible12.healthtrend

/** App filing categories, not laboratory departments or assay methods. No hospital key. */
internal object ReportCategoryPolicy {
 val filingCategories=listOf("血常规","生化","肝功能","肾功能","电解质及肾功能","凝血功能","肿瘤标志物","甲状腺功能","传染病筛查","尿液检查","粪便检查","血型检查")
 private val forbidden=setOf("免疫","免疫学","临床免疫","免疫检查","免疫检验","生化免疫","病毒学","化学发光","elisa","其他","综合","常规检查")
 fun forbiddenCategory(category:String)=category.trim().lowercase().removeSuffix("检验报告").removeSuffix("报告") in forbidden
 private fun canonical(category:String)=when(category.trim()){
  "甲状腺"->"甲状腺功能";"凝血"->"凝血功能"
  "尿液分析","尿常规"->"尿液检查";"粪便常规","大便常规"->"粪便检查"
  "血型"->"血型检查";else->category.trim()
 }
 private val families by lazy {linkedMapOf(
  "传染病筛查" to UnifiedMetricStandard.keysForType("传染病筛查"),
  "血常规" to UnifiedMetricStandard.keysForType("血常规"),
  "肿瘤标志物" to UnifiedMetricStandard.keysForType("肿瘤标志物"),
  "甲状腺功能" to UnifiedMetricStandard.keysForType("甲状腺"),
  "凝血功能" to UnifiedMetricStandard.keysForType("凝血"),
  "尿液检查" to UnifiedMetricStandard.keysForType("尿液分析"),
  "粪便检查" to UnifiedMetricStandard.keysForType("粪便常规"),
  "生化" to UnifiedMetricStandard.keysForType("生化")
 )}
 private val knownCategories=setOf("传染病筛查","血常规","肿瘤标志物","甲状腺功能","凝血功能","尿液检查","粪便检查","血型检查","生化","肝功能","肾功能","电解质及肾功能")
 private fun family(key:String)=families.entries.filter{canonicalMetricKey(key) in it.value}.singleOrNull()?.key
 fun infer(keys:List<String>):String? {
  if(keys.isEmpty())return null
  val matches=keys.map{family(it)?:return null}.distinct()
  return matches.singleOrNull()
 }
 fun problems(category:String,name:String,keys:List<String>):List<String> {
  val problems=mutableListOf<String>()
  val current=canonical(category)
  if(forbiddenCategory(category))problems+="检查大类“$category”不是约定分类，请核对；乙肝两对半应归“传染病筛查”"
  val formal=ReportNaming.formalPanelName(name)
  val expected=if(formal.isNotBlank())ReportNaming.classify(formal,"").category.takeIf{it.isNotBlank()}?.let(::canonical) else null
  if(expected!=null&&expected!=current)problems+="正式组合“$name”应归“$expected”，与当前大类不一致，请核对"
  val namedFamilies=keys.mapNotNull(::family).toSet()
  if(expected!=null&&namedFamilies.any{it!=expected&&!(expected in setOf("肝功能","肾功能","电解质及肾功能")&&it=="生化")})
   problems+="正式检查组合与实际指标类别冲突，请核对原报告"
  // Unknown/custom panels remain reviewable; no guessed family or new analyte is invented.
  if(current in knownCategories&&namedFamilies.isNotEmpty()){
   if(namedFamilies.size>1)problems+="报告包含跨大类项目，请核对整份报告的分类，不自动拆分"
   else {
    val measured=namedFamilies.single()
    val allowed=if(measured=="生化")setOf("生化","肝功能","肾功能","电解质及肾功能") else setOf(measured)
    if(current !in allowed)problems+="实际指标属于“$measured”，与当前大类“$category”不一致，请核对"
   }
  }
  val specific=when(current){"肝功能"->UnifiedMetricStandard.keysForType("肝功能");"肾功能","电解质及肾功能"->UnifiedMetricStandard.keysForType("肾功能");else->null}
  if(specific!=null&&keys.any{family(it)=="生化"&&canonicalMetricKey(it) !in specific})
   problems+="实际生化项目超出“$category”组合范围，请核对是否应归“生化”"
  return problems.distinct()
 }
 fun metadataUnchanged(d:ReportDraft):Boolean {
  val old=d.existing?:return false
  return d.type==old.reportType&&d.reportName==ReportDraft.from(old).reportName&&d.rows.map{it.key}==old.results.map{it.metricKey}
 }
 fun validateSave(report:LabReport,old:LabReport?){
  if(old!=null&&report.reportType==old.reportType&&report.reportName==old.reportName&&report.results.map{it.metricKey}==old.results.map{it.metricKey})return
  val issues=problems(report.reportType,report.reportName.orEmpty(),report.results.map{it.metricKey})
  require(issues.isEmpty()){issues.joinToString("；")}
 }
}
