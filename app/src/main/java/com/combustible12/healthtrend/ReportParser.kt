package com.combustible12.healthtrend

/** Values, units and limits come from the source, never population defaults. */
data class ParsedLabResult(val metricKey:String,val displayName:String,val value:Double?,val unit:String,val referenceLow:Double?,val referenceHigh:Double?,val rawLine:String,val primary:Boolean,val textValue:String=value?.toString().orEmpty(),val comparator:String="")
object ReportParser {
 val primaryKeys=setOf("WBC","NEUT#","HGB","PLT","ALT","AST","TBIL","ALB","CREA","UREA","UA")
 private val differentialBases=setOf("NEUT","LYMPH","MONO","EOS","BASO","NRBC")
 private val aliases=mapOf("白细胞计数" to "WBC","白细胞" to "WBC","中性粒细胞计数" to "NEUT#","中性粒细胞绝对值" to "NEUT#","中性粒细胞百分比" to "NEUT%","单核细胞计数" to "MONO#","单核细胞绝对值" to "MONO#","单核细胞百分比" to "MONO%","嗜酸性粒细胞计数" to "EOS#","嗜酸性粒细胞绝对值" to "EOS#","嗜酸性粒细胞百分比" to "EOS%","嗜碱性粒细胞计数" to "BASO#","嗜碱性粒细胞绝对值" to "BASO#","嗜碱性粒细胞百分比" to "BASO%","血红蛋白" to "HGB","血小板计数" to "PLT","血小板" to "PLT","总蛋白" to "TP","球蛋白" to "GLOB","白球比" to "A/G","直接胆红素" to "DBIL","间接胆红素" to "IBIL","谷氨酰转肽酶" to "GGT","谷草/谷丙" to "AST/ALT","碱性磷酸酶" to "ALP","胆碱酯酶" to "CHE","总胆汁酸" to "TBA","前白蛋白" to "PA","丙氨酸氨基转移酶" to "ALT","谷丙转氨酶" to "ALT","天门冬氨酸氨基转移酶" to "AST","谷草转氨酶" to "AST","总胆红素" to "TBIL","白蛋白" to "ALB","肌酐" to "CREA","CRE" to "CREA","尿素" to "UREA","尿酸" to "UA","红细胞" to "RBC","淋巴细胞计数" to "LYMPH#","淋巴细胞绝对值" to "LYMPH#","淋巴细胞百分比" to "LYMPH%","平均红细胞体积" to "MCV","平均红细胞血红蛋白量" to "MCH","平均红细胞血红蛋白浓度" to "MCHC","红细胞分布宽度" to "RDW","平均血小板体积" to "MPV","血小板分布宽度" to "PDW","血小板压积" to "PCT","红细胞压积" to "HCT","淋巴细胞比率" to "LYMPH%","淋巴细胞比例" to "LYMPH%","中性粒细胞比率" to "NEUT%","中性粒细胞比例" to "NEUT%","单核细胞比率" to "MONO%","单核细胞比例" to "MONO%","嗜酸性粒细胞比率" to "EOS%","嗜酸性粒细胞比例" to "EOS%","嗜碱性粒细胞比率" to "BASO%","嗜碱性粒细胞比例" to "BASO%","有核红细胞比率" to "NRBC%","有核红细胞百分比" to "NRBC%","有核红细胞计数" to "NRBC#","大型血小板比率" to "P-LCR","大小血小板数目" to "P-LCC","大血小板数目" to "P-LCC","大型血小板数目" to "P-LCC","红细胞分布宽度SD" to "RDW-SD","乳酸脱氢酶" to "LDH")
 private val numeric=Regex("(?<![A-Za-z\\d.^×])[<>≤≥]?\\s*[-+]?\\d+(?:\\.\\d+)?(?:[eE][-+]?\\d+)?")
 private val range=Regex("([<>≤≥]?)\\s*([-+]?\\d+(?:\\.\\d+)?)\\s*(?:[-–—~～至]\\s*([-+]?\\d+(?:\\.\\d+)?))?")
 private val canonicalUnits=mapOf(
  "WBC" to "×10^9/L","NEUT#" to "×10^9/L","LYMPH#" to "×10^9/L","MONO#" to "×10^9/L","EOS#" to "×10^9/L","BASO#" to "×10^9/L","PLT" to "×10^9/L",
  "RBC" to "×10^12/L","HGB" to "g/L","HCT" to "%","NEUT%" to "%","LYMPH%" to "%","MONO%" to "%","EOS%" to "%","BASO%" to "%",
  "MCV" to "fL","MCH" to "pg","MCHC" to "g/L","RDW" to "%","RDW-CV" to "%","RDW-SD" to "fL","MPV" to "fL","PDW" to "%","PCT" to "%","NRBC%" to "%","NRBC#" to "×10^9/L","P-LCR" to "%","P-LCC" to "×10^9/L",
  "ALT" to "U/L","AST" to "U/L","ALB" to "g/L","TBIL" to "μmol/L",
  "CREA" to "μmol/L","UREA" to "mmol/L","UA" to "μmol/L","LDH" to "U/L"
 )
 private fun normalizedUnit(raw:String):String{
  val compact=raw.replace(" ","").replace("x","×",true).replace("µ","μ").replace("10⁹","10^9").replace("10¹²","10^12")
  return if(compact.startsWith("10^"))"×$compact" else compact
 }
 private fun resolvedUnit(metricKey:String,ocr:String):String{
  val expected=canonicalUnits[metricKey]?:return ocr
  // Blank on the printed report stays blank. Hospital templates may fill a previously
  // confirmed fixed unit later, but the generic parser must not invent source data.
  if(ocr.isBlank())return ""
  val got=normalizedUnit(ocr);val want=normalizedUnit(expected)
  if(got.equals(want,true))return expected
  // Known CBC families are dimension-specific. A valid-looking unit from the neighboring
  // OCR column (for example %NEUT receiving MPV's fL) is still invalid for this metric.
  val countKeys=setOf("WBC","NEUT#","LYMPH#","MONO#","EOS#","BASO#","NRBC#","PLT","P-LCC")
  val percentKeys=setOf("NEUT%","LYMPH%","MONO%","EOS%","BASO%","NRBC%","HCT","RDW","RDW-CV","PCT","PDW","P-LCR")
  if(metricKey in countKeys)return expected
  if(metricKey in percentKeys)return expected
  if(metricKey in setOf("MCV","RDW-SD","MPV","MCH","MCHC","RBC"))return expected
  // Chemistry can legitimately arrive in convertible alternatives; preserve those until
  // value and reference range are normalized together.
  val validAlternative=Regex("(?i)^(?:[×]?10\\^-?\\d+/L|[a-zA-Zμ]+/[a-zA-Z]+|[a-zA-Zμ]+|%)$").matches(got)
  return if(validAlternative) ocr else expected
 }
 fun key(name:String):String {
  // Explicit code always wins; OCR-only labels use deterministic exact-name/code matching.
  val identity=Regex("[（(]\\s*([^（）()]+?)\\s*[）)]").findAll(name).map{normalizeCode(it.groupValues[1])}.lastOrNull()
  if(identity in metricIdentityKeys)return identity!!
  val label=name.trim().replace('：',':').replace('／','/')
  val leading=label.substringBefore(' ').substringBefore('\\t').trim().trimEnd(':')
  val code=normalizeCode(leading)
  if(code in metricIdentityKeys)return code
  val full=normalizeCode(label)
  if(full in metricIdentityKeys)return full
  return aliases.entries.sortedByDescending{it.key.length}.firstOrNull{label==it.key || label.startsWith(it.key+" ") || label.startsWith(it.key+"（") || label.startsWith(it.key+"(")}?.value ?: "未识别"
 }
 private val metricIdentityKeys=setOf("WBC","NEUT#","NEUT%","LYMPH#","LYMPH%","MONO#","MONO%","EOS#","EOS%","BASO#","BASO%","RBC","HGB","HCT","MCV","MCH","MCHC","RDW","RDW-CV","RDW-SD","PLT","PCT","MPV","PDW","P-LCR","P-LCC","NRBC#","NRBC%","TP","ALB","GLOB","A/G","TBIL","DBIL","IBIL","ALT","AST","AST/ALT","GGT","ALP","CHE","TBA","PA","UREA","CREA","UA","LDH","SCC","AFP","CEA","CA125","CA153","CA199","CA724","CYFRA21-1","NSE","HE4","UREA/CREA","GLU","K","NA","CL","HCO3","CA","MG","PHOS","AG","OSM","CK","CKMB","CKMB/CK","TG","CHOL","APOA1","APOB","HDLC","LDLC")
 internal fun bindExplicitLeadingIdentities(text:String):String=text.lines().joinToString("\n"){line->
  val trimmed=line.trim()
  if(trimmed.isBlank())return@joinToString line
  if(key(trimmed)!="未识别")return@joinToString trimmed
  // Clipboard contract: the first whitespace-delimited token is the complete metric code.
  // Identity is exact: AST != AST/ALT, NEUT# != NEUT%, P-LCR != P-LCC.
  val token=trimmed.substringBefore(' ').substringBefore('\t').trim()
  val explicit=normalizeCode(token)
  if(explicit in metricIdentityKeys)"（$explicit） "+trimmed.removePrefix(token).trimStart() else line
 }
 private val astKeySelfCheck by lazy {
  check(key("谷草转氨酶（AST）")=="AST")
  check(key("谷草/谷丙（AST/ALT）")=="AST/ALT")
  check(key("谷草/谷丙（AST/ALT）")!="AST")
  true
 }
 private fun normalizeCode(raw:String):String{
  val c=raw.uppercase().replace("％","%").replace('：',':').replace('／','/').trim().let{code->
   if(code in setOf("AST:ALT","A:G","CKMB:CK"))code.replace(':','/') else code
  }
  val base=c.trimStart('#','%').trimEnd('#','%')
  val marker=when{
   c.startsWith("#")||c.endsWith("#")->"#"
   c.startsWith("%")||c.endsWith("%")->"%"
   else->""
  }
  return (if(base in differentialBases&&marker.isNotEmpty())base+marker else c).let{if(it=="CRE")"CREA" else it}
 }
 private val knownCode=Regex("(?i)(?<![A-Za-z])(?:AST/ALT(?![A-Za-z])|AST(?!/?ALT)(?![A-Za-z])|A/G|[#%](?:NEUT|LYMPH|MONO|EOS|BASO|NRBC)|(?:NEUT|LYMPH|MONO|EOS|BASO|NRBC)[#%]|WBC|RBC|HGB|HCT|MCV|MCHC|MCH|RDW-CV|RDW-SD|RDW|PLT|MPV|PDW|PCT|P-LCR|P-LCC|TP|GLOB|DBIL|IBIL|GGT|ALP|CHE|TBA|PA|ALT|TBIL|ALB|CREA|CRE|UREA|UA|LDH|SCC|AFP|CEA|CA125|CA153|CA199|CA724|CYFRA21-1|NSE|HE4|UREA/CREA|GLU|HCO3|PHOS|CKMB/CK|CKMB|CHOL|APOA1|APOB|HDLC|LDLC|OSM|MG|AG|TG|CK|NA|CL|CA|K)(?![A-Za-z])")
 private fun segments(text:String)=text.lines().flatMap{raw->
  // A bound clipboard row starts with "(CODE)". It is already one complete metric row;
  // never rescan its Chinese name for additional code-like substrings.
  if(Regex("^\\s*[（(][^（）()]+[）)]").containsMatchIn(raw))return@flatMap listOf(raw)
  val hits=knownCode.findAll(raw).toList()
  if(hits.size<2) listOf(raw) else hits.indices.map{i->raw.substring(hits[i].range.first,if(i+1<hits.size)hits[i+1].range.first else raw.length).trim().replace(Regex("^\\d+[.、]?\\s*"),"")}
 }
 fun parse(text:String):List<ParsedLabResult> = segments(text).mapNotNull { source ->
  val line=source.trim().replace(Regex("^\\d+[.、]?\\s+(?=[A-Za-z\\p{IsHan}])"),"").replace('：',':').replace('％','%').replace(Regex("(?<=\\d)\\s*\\.\\s*(?=\\d)"),".").replace(Regex("^((?:AST/ALT)|WBC|[#%]?(?:NEUT|LYMPH|MONO|EOS|BASO)|(?:NEUT|LYMPH|MONO|EOS|BASO)[#%]|NRBC[#%]|P-LCR|P-LCC|RDW-CV|RDW-SD|HGB|PLT|ALT|AST(?!/?ALT)|TBIL|ALB|CREA|UREA|UA|LDH|RBC|MCV|MCHC|MCH)(?=[<>≤≥]?[-+]?\\d)",RegexOption.IGNORE_CASE),"$1 ")
  if(line.isEmpty() || listOf("姓名","年龄","性别","条码","采样时间","报告时间","检验日期","参考范围","参考区间","病历号","住院号","门诊号","样本号","标本号","标本","科室","诊断","医生","审核","送检","打印","床号","备注").any{line.contains(it)}) return@mapNotNull null
  val match=numeric.find(line)
  val textual=Regex("^(.*?)\\s+(阴性|阳性|弱阳性|未检出|正常|异常|[+-]{1,4})(.*)$").find(line)
  if(match==null && textual==null) return@mapNotNull null
  val isText=textual!=null && (match==null || textual.groupValues[1].length<match.range.first)
  val prefix=if(isText)textual!!.groupValues[1] else line.substring(0,match!!.range.first)
  val parsedName=prefix.replace(Regex("(?<=[\\p{IsHan}])\\s+(?=[\\p{IsHan}])"),"").trim().trimEnd(':','↑','↓','*').replace(Regex("^\\d+[.、]\\s*"),"")
  if(parsedName.isBlank() || !parsedName.any{it.isLetter()} || parsedName.length>55) return@mapNotNull null
  // Identity and display text are separate. Parenthesized code is internal only.
  val kIdentity=key(parsedName)
  if(kIdentity=="未识别")return@mapNotNull null
  val identityPattern=Regex("[（(]\\s*"+Regex.escape(kIdentity)+"\\s*[）)]",RegexOption.IGNORE_CASE)
  val name=parsedName.replace(identityPattern,"").replace(Regex("\\s+")," ").trim().ifBlank{kIdentity}
  val rawValue=if(isText)textual!!.groupValues[2] else match!!.value.replace(" ","")
  val suffix=if(isText)textual!!.groupValues[3].trim() else line.substring(match!!.range.last+1).trim().trimStart('↑','↓','*')
  val limits=Regex("([-+]?\\d+(?:\\.\\d+)?)\\s*(?:-{1,2}|–|—|~|～|至)\\s*([-+]?\\d+(?:\\.\\d+)?)").find(suffix)
  val one=if(limits==null)Regex("(?:<=|>=|[<>≤≥])\\s*[-+]?\\d+(?:\\.\\d+)?").find(suffix) ?: Regex("[-+]?\\d+(?:\\.\\d+)?\\s*--(?:\\s|$)").find(suffix) else null
  val low=limits?.groupValues?.get(1)?.toDoubleOrNull() ?: one?.value?.let{v->when{v.trim().startsWith(">")||v.trim().startsWith("≥")->v.replace(Regex("[>=≥\\s]"),"").toDoubleOrNull();v.trim().endsWith("--")->v.replace("--","").trim().toDoubleOrNull();else->null}}
  val high=limits?.groupValues?.get(2)?.toDoubleOrNull() ?: one?.value?.takeIf{it.trim().startsWith("<")||it.trim().startsWith("≤")}?.replace(Regex("[<=≤\\s]"),"")?.toDoubleOrNull()
  val limitMatch=limits?:one
  val unitText=if(limitMatch!=null)suffix.removeRange(limitMatch.range).trim() else suffix
  // Keep the result token isolated from any neighboring OCR column. A second bare number
  // after the result is never another result; it belongs to range/flags/garbage and must not be displayed.
  val ocrUnit=Regex("(?i)(?:[×x]?10\\s*\\^?\\s*[-+]?\\d+\\s*/\\s*[lL]|[a-zA-Zμµ]+(?:/[a-zA-Zμµ]+)?|%)").findAll(unitText).map{it.value.replace(" ","")}.firstOrNull{ normalizedUnit(it).contains("/") || it=="%" || canonicalUnits.values.any{expected->normalizedUnit(expected).equals(normalizedUnit(it),true)} }.orEmpty()
  var k=kIdentity
  val unit=resolvedUnit(k,ocrUnit)
  if(k=="未识别" || listOf("病历","样本","标本","科室","诊断","医生","审核","送检","年龄").any{name.contains(it)}) return@mapNotNull null
  ParsedLabResult(k,name,rawValue.trimStart('<','>','≤','≥').toDoubleOrNull(),unit,low,high,source,k in primaryKeys,rawValue.trim(),rawValue.takeWhile{it in "<>≤≥"})
 }
 fun valid(items:List<ParsedLabResult>):Boolean=items.isNotEmpty() && items.all{it.displayName.isNotBlank()&&it.textValue.isNotBlank()&&(it.value==null||it.value.isFinite())&&(it.referenceLow==null||it.referenceHigh==null||it.referenceLow<=it.referenceHigh)}
}
