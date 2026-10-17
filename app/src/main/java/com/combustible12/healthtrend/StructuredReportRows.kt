package com.combustible12.healthtrend

/** Source-specific names are scoped to the specimen, never global blood aliases. */
internal object SpecimenNames {
 private val urine=mapOf("葡萄糖" to "UR-GLU","胆红素" to "UR-BIL","酮体" to "UR-KET","比重" to "UR-SG","PH" to "UR-PH","蛋白质" to "UR-PRO","尿胆原" to "UR-URO","亚硝酸盐" to "UR-NIT","隐血（红细胞）" to "UR-BLD","隐血(红细胞)" to "UR-BLD","白细胞酯酶" to "UR-LEU")
 private val stool=mapOf("颜色" to "ST-COLOR","性状" to "ST-CONSISTENCY","蛔虫卵" to "ST-ASCARIS","鞭虫卵" to "ST-TRICHURIS","钩虫卵" to "ST-HOOKWORM","蛲虫卵" to "ST-PINWORM","阿米巴滋养体" to "ST-AMOEBA-T","阿米巴包囊体" to "ST-AMOEBA-C","霉菌" to "ST-FUNGI","镜检白细胞" to "ST-WBC","镜检红细胞" to "ST-RBC","脂肪球" to "ST-FAT")
 fun names(type:String):Map<String,String> = when{
  type.contains("尿")||type.startsWith("UR-")->urine
  type.contains("粪便")||type.contains("大便")||type.startsWith("ST-")->stool
  else->emptyMap()
 }
 fun resolve(name:String,type:String)=names(type).entries.singleOrNull{it.key.equals(name.trim(),true)}?.value
}

/** Identify a complete known name BEFORE looking for a result, including embedded digits.
 * Returns a structured row for the existing parser; rawLine remains the untouched source.
 * Unknown names/units still use the conservative legacy parser and manual review. */
internal object StructuredReportRows {
 private val units=Regex("(?:[×x]?10(?:\\^|[eE])?[-+]?\\d+/L|[a-zμµ][a-z0-9μµ.^]*(?:/[a-z0-9μµ.^]+)+|%|fl|pg|s|sec)",RegexOption.IGNORE_CASE)
 private val labels by lazy {
  UnifiedMetricStandard.keys().flatMap{key->
   val d=UnifiedMetricStandard.definition(key)!!
   (d.aliases+d.name+ReportParser.builtInAliasNames(key)).map{it to key}
  }.plus(listOf("红细胞分布宽度" to "RDW","血小板分布宽度" to "PDW","红细胞体积分布宽度" to "RDW","血小板体积分布宽度" to "PDW","大血小板" to "P-LCR"))
 }
 fun prepare(source:String,type:String):String? {
  if('\t' in source)return null
  var rest=source.trim().trimStart('*').replace(Regex("^\\d+[.、]?\\s+(?=[A-Za-z\\p{IsHan}])"),"")
  var explicit:String?=null
  val bound=Regex("^[（(]([^（）()]+)[）)]\\s+").find(rest)
  if(bound!=null){
   explicit=ReportParser.key(bound.groupValues[1]).takeUnless{it=="未识别"}?:return null
   rest=rest.substring(bound.range.last+1)
  }else{
   val token=rest.substringBefore(' ')
   if(token.none{it.code in 0x3400..0x9fff}&&' ' in rest){
    explicit=ReportParser.key(token).takeUnless{it=="未识别"}
    if(explicit!=null)rest=rest.substringAfter(' ').trimStart()
   }
  }
  val base=SpecimenNames.names(type).map{it.key to it.value}+labels
  val candidates=base.flatMap{(label,k)->
   val suffix=if(rest.startsWith(label,true))Regex("^[（(]([^（）()]+)[）)](?=\\s)").find(rest.drop(label.length)) else null
   if(suffix!=null&&ReportParser.key(suffix.groupValues[1])==k)listOf((label+suffix.value) to k,label to k) else listOf(label to k)
  }
  val named=candidates.sortedByDescending{it.first.length}.firstOrNull{(name,_)->
   rest.startsWith(name,true)&&rest.length>name.length&&rest[name.length].isWhitespace()
  }
  val name:String;var key:String
  if(named!=null){name=rest.take(named.first.length);key=explicit?:named.second;rest=rest.drop(named.first.length).trimStart()}
  else if(explicit!=null && rest.substringBefore(' ').let{LabResultValues.numeric(it)!=null||LabResultValues.kind(it) in setOf(LabValueKind.QUALITATIVE,LabValueKind.SEMIQUANTITATIVE)}){
   key=explicit;name=UnifiedMetricStandard.definition(key)?.name?:return null
  }else return null
  var value=rest.substringBefore(' ').trimEnd('↑','↓')
  if(!LabResultValues.valid(value))return null
  var tail=rest.substringAfter(' ',"").trim()
  if(value in setOf("阴性","阳性"))Regex("^[（(][+-][）)](?=\\s|$)").find(tail)?.let{value+=" "+it.value;tail=tail.drop(it.value.length).trim()}
  // Keep standalone report flags for sourceReportFlag(), outside the structured result.
  tail=tail.trimStart('↑','↓').trim()
  var unit=""
  val first=tail.substringBefore(' ')
  if(first=="-"){tail=tail.substringAfter(' ',"").trim()}
  else if(units.matches(first)){unit=first;tail=tail.substringAfter(' ',"").trim()}
  else if(LabResultValues.numeric(value)!=null && tail.isNotBlank() &&
   PartialReferenceRanges.parts(tail).let{it.first.isBlank()&&it.second.isBlank()} &&
   LabResultValues.kind(tail)!=LabValueKind.QUALITATIVE)return null
  // Range-before-unit rows remain handled by the existing layout parser.
  if(LabResultValues.numeric(value)!=null&&tail.split(' ').any{units.matches(it)})return null
  if(name=="大血小板"&&unit!="%")return null
  if(key in setOf("RDW","PDW"))key=sourceDistributionWidthIdentity(name,unit)?:key
  val displayName=name.takeIf{it.any{ch->ch.code in 0x3400..0x9fff}}?:UnifiedMetricStandard.definition(key)?.name?:return null
  return listOf(key,displayName,value,unit.ifBlank{"-"},tail).joinToString("\t")
 }
}
