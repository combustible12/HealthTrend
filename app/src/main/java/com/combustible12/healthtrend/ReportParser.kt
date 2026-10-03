package com.combustible12.healthtrend

/** Values, units and limits come from the source, never population defaults. */
data class ParsedLabResult(val metricKey:String,val displayName:String,val value:Double?,val unit:String,val referenceLow:Double?,val referenceHigh:Double?,val rawLine:String,val primary:Boolean,val textValue:String=value?.toString().orEmpty(),val comparator:String="")
object ReportParser {
 val primaryKeys=setOf("WBC","NEUT#","HGB","PLT","ALT","AST","TBIL","ALB","CREA","UREA","UA")
 private val aliases=mapOf("白细胞计数" to "WBC","白细胞" to "WBC","中性粒细胞计数" to "NEUT#","中性粒细胞绝对值" to "NEUT#","中性粒细胞百分比" to "NEUT%","血红蛋白" to "HGB","血小板计数" to "PLT","血小板" to "PLT","丙氨酸氨基转移酶" to "ALT","谷丙转氨酶" to "ALT","天门冬氨酸氨基转移酶" to "AST","谷草转氨酶" to "AST","总胆红素" to "TBIL","白蛋白" to "ALB","肌酐" to "CREA","CRE" to "CREA","尿素" to "UREA","尿酸" to "UA")
 private val numeric=Regex("(?<![A-Za-z\\d.^×])[<>≤≥]?\\s*[-+]?\\d+(?:\\.\\d+)?(?:[eE][-+]?\\d+)?")
 private val range=Regex("([<>≤≥]?)\\s*([-+]?\\d+(?:\\.\\d+)?)\\s*(?:[-–—~～至]\\s*([-+]?\\d+(?:\\.\\d+)?))?")
 fun key(name:String):String {
  val clean=name.trim().replace(" ","")
  aliases[clean]?.let{return it}
  aliases[clean.substringBefore("(").substringBefore("（")]?.let{return it}
  Regex("[A-Za-z]+[#%]?").findAll(clean).map{it.value.uppercase()}.firstOrNull{it in primaryKeys || it in setOf("NEUT%","RBC","LYMPH#","LYMPH%","MCV","MCH","MCHC","RDW","MPV") }?.let{return it}
  return clean.uppercase().replace("NEUT％","NEUT%").ifBlank{"未命名"}
 }
 fun parse(text:String):List<ParsedLabResult> = text.lines().mapNotNull { source ->
  val line=source.trim().replace(Regex("^\\d+[.、]?\\s+(?=[A-Za-z\\p{IsHan}])"),"").replace('：',':').replace('％','%').replace(Regex("^(WBC|NEUT[#%]|HGB|PLT|ALT|AST|TBIL|ALB|CREA|UREA|UA|LDH|RBC|MCV|MCHC|MCH)(?=[<>≤≥]?[-+]?\\d)",RegexOption.IGNORE_CASE),"$1 ")
  if(line.isEmpty() || listOf("姓名","年龄","性别","条码","采样时间","报告时间","检验日期","参考范围","参考区间","病历号","住院号","门诊号","样本号","标本","科室","诊断","医生","审核","送检","床号","备注").any{line.contains(it)}) return@mapNotNull null
  val match=numeric.find(line)
  val textual=Regex("^(.*?)\\s+(阴性|阳性|弱阳性|未检出|正常|异常|[+-]{1,4})(.*)$").find(line)
  if(match==null && textual==null) return@mapNotNull null
  val isText=textual!=null && (match==null || textual.groupValues[1].length<match.range.first)
  val prefix=if(isText)textual!!.groupValues[1] else line.substring(0,match!!.range.first)
  val name=prefix.replace(Regex("(?<=[\\p{IsHan}])\\s+(?=[\\p{IsHan}])"),"").trim().trimEnd(':','↑','↓','*').replace(Regex("^\\d+[.、]\\s*"),"")
  if(name.isBlank() || !name.any{it.isLetter()} || name.length>55) return@mapNotNull null
  val rawValue=if(isText)textual!!.groupValues[2] else match!!.value.replace(" ","")
  val suffix=if(isText)textual!!.groupValues[3].trim() else line.substring(match!!.range.last+1).trim().trimStart('↑','↓','*')
  val limits=Regex("(\\d+(?:\\.\\d+)?)\\s*[-–—~～至]\\s*(\\d+(?:\\.\\d+)?)").find(suffix)
  val one=if(limits==null)Regex("[<>≤≥]\\s*[-+]?\\d+(?:\\.\\d+)?").find(suffix)else null
  val low=limits?.groupValues?.get(1)?.toDoubleOrNull() ?: one?.value?.takeIf{it.startsWith(">")||it.startsWith("≥")}?.replace(Regex("[>≥\\s]"),"")?.toDoubleOrNull()
  val high=limits?.groupValues?.get(2)?.toDoubleOrNull() ?: one?.value?.takeIf{it.startsWith("<")||it.startsWith("≤")}?.replace(Regex("[<≤\\s]"),"")?.toDoubleOrNull()
  val limitMatch=limits?:one
  val unitSource=(if(limitMatch!=null)suffix.substring(limitMatch.range.last+1)else suffix).trim().trim('↑','↓','*',' ','|')
  val unit=Regex("(?i)^(?:[×x]?10\\s*\\^?\\s*[-+]?\\d+\\s*/\\s*[lL]|[a-zA-Zμµ]+(?:/[a-zA-Zμµ]+)?|%)").find(unitSource)?.value?.replace(" ","").orEmpty()
  val k=key(name)
  if(k=="未命名" || listOf("病历","样本","标本","科室","诊断","医生","审核","送检","年龄").any{name.contains(it)}) return@mapNotNull null
  ParsedLabResult(k,name,rawValue.trimStart('<','>','≤','≥').toDoubleOrNull(),unit,low,high,source,k in primaryKeys,rawValue,rawValue.takeWhile{it in "<>≤≥"})
 }
 fun valid(items:List<ParsedLabResult>):Boolean=items.isNotEmpty() && items.all{it.displayName.isNotBlank()&&it.textValue.isNotBlank()&&(it.value==null||it.value.isFinite())&&(it.referenceLow==null||it.referenceHigh==null||it.referenceLow<=it.referenceHigh)}
}
