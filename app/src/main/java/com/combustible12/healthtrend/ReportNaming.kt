package com.combustible12.healthtrend

/** Report headings are independent from metric identities and measured data. */
internal data class ReportHeading(val category:String,val name:String="") {
 fun lines()=listOf(category)+listOfNotNull(name.takeIf{it.isNotBlank()&&it!=category})
}
internal object ReportNaming {
 private val categories=setOf("血常规","生化","肝功能","肾功能","电解质及肾功能","肿瘤标志物","传染病筛查","甲状腺","甲状腺功能","凝血","凝血功能","尿液分析","尿常规","尿液检查","粪便常规","粪便检查","血型","血型检查","检查报告")
 private fun isList(s:String)=Regex("[,，、;；]").containsMatchIn(s)||Regex("测定").findAll(s).count()>1
 /** Only explicit recognized panel names qualify; a short analyte name is not a panel. */
 fun formalPanelName(source:String):String {
  val name=source.trim()
  if(name.isBlank()||isList(name))return ""
  val numbered=Regex("[一二三四五六七八九十百两0-9]+项(?:检查|检测|测定)?(?:[（(][^）)]*[）)])?$").containsMatchIn(name)
  val hepatitis=Regex("^(?:乙肝|乙型肝炎)(?:二|两)对半(?:[（(][^）)]*[）)])?$").matches(name)
  val known=name in setOf("血细胞分析","血细胞分析(成人)","血细胞分析（成人）","常规生化全套检查","生化全套检查","生化全套")
  return name.takeIf{numbered||hepatitis||known}.orEmpty()
 }
 fun classify(source:String,fallback:String):ReportHeading {
  val name=source.trim()
  if(name.isBlank())return ReportHeading(fallback)
  if(name in categories)return ReportHeading(name)
  val category=when {
   name.contains("血细胞分析")->"血常规"
   name.contains("乙肝")||name.contains("二对半")||name.contains("两对半")->"传染病筛查"
   name.contains("血型")->"血型检查"
   name.contains("甲功")||name.contains("甲状腺")->"甲状腺功能"
   name.contains("生化")->"生化"
   name.contains("肝功")->"肝功能"
   name.contains("肾功")->"肾功能"
   name.contains("凝血")->"凝血功能"
   name.contains("TPPA",true)||name.contains("梅毒")||name.contains("HIV",true)||name.contains("HCV",true)->"传染病筛查"
   name.contains("糖类抗原")||name.contains("肿瘤标志")||name.contains("CA724",true)||name.contains("CA72-4",true)->"肿瘤标志物"
   else->fallback
  }
  return ReportHeading(category,formalPanelName(name).takeUnless{it==category}.orEmpty())
 }
 /** Only the heading before an explicit date is read; result rows never supply a title. */
 fun header(raw:String):List<String> {
  val lines=raw.lines().map{it.trim()}.filter{it.isNotBlank()}
  val hospital=lines.indexOfFirst{it.contains("医院")||it.contains("保健院")||it.contains("卫生院")||it.contains("医学中心")}
  if(hospital<0)return emptyList()
  val date=(hospital+1 until lines.size).firstOrNull{Regex("^(?:检查日期\\s*[:：]\\s*)?20\\d{2}[-/.年]\\d{1,2}[-/.月]\\d{1,2}").containsMatchIn(lines[it])}?:return emptyList()
  val middle=lines.subList(hospital+1,date).map{it.replace(Regex("^(?:检查大类|检查名称|检查类型|报告类型|类型)\\s*[:：]\\s*"),"")}
  if(middle.size !in 1..2)return emptyList()
  return middle.takeIf{rows->rows.all{it.length<=180&&!Regex("^(姓名|性别|年龄|患者|采样|送检|审核|打印|标本|病区|床号)").containsMatchIn(it)}}.orEmpty()
 }
 fun resolve(raw:String,fallback:String):ReportHeading {
  val rows=header(raw)
  return when(rows.size){
   2->ReportHeading(rows[0],classify(rows[1],rows[0]).name.takeUnless{rows[1]==rows[0]}.orEmpty())
   1->if(ReportCategoryPolicy.forbiddenCategory(rows.single()))ReportHeading(rows.single()) else classify(rows.single(),fallback)
   else->ReportHeading(fallback)
  }
 }
 fun title(report:LabReport):ReportHeading {
  val classified=classify(report.reportType,report.reportType)
  val saved=report.reportName.orEmpty().trim()
  if(report.reportName!=null)return ReportHeading(report.reportType,formalPanelName(saved).takeUnless{it==report.reportType}.orEmpty())
  // Read-only recovery of a specific original heading, never a storage migration.
  val source=resolve(report.rawOcr,classified.category)
  return if(source.category==classified.category&&source.name.isNotBlank())source else classified
 }
}
