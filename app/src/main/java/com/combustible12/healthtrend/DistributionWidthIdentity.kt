package com.combustible12.healthtrend

/** The only four confirmed RDW/PDW identities supported by the unified pool. */
internal val confirmedDistributionWidthKeys=setOf("RDW-CV","RDW-SD","PDW-CV","PDW-SD")

internal data class DistributionWidthResolution(
 val family:String?,val targetKey:String?,val displayUnit:String,val problem:String?
)

private fun distributionWidthFamilyFromName(name:String):String? {
 val normalized=UnifiedMetricStandard.normalizedName(ReportParser.sourceMetricName(name))
 return when {
  normalized.contains("红细胞分布宽度")||normalized.contains("红细胞体积分布宽度")||
   Regex("(^|[^a-z])rdw(?:-(?:cv|sd))?($|[^a-z])",RegexOption.IGNORE_CASE).containsMatchIn(normalized)->"RDW"
  normalized.contains("血小板分布宽度")||normalized.contains("血小板体积分布宽度")||
   Regex("(^|[^a-z])pdw(?:-(?:cv|sd))?($|[^a-z])",RegexOption.IGNORE_CASE).containsMatchIn(normalized)->"PDW"
  else->null
 }
}

private fun distributionWidthVariantFromName(name:String):String? {
 val normalized=UnifiedMetricStandard.normalizedName(ReportParser.sourceMetricName(name))
 val cv=normalized.contains("变异系数")||Regex("(?:^|[^a-z])(?:rdw|pdw)?-?cv(?:$|[^a-z])",RegexOption.IGNORE_CASE).containsMatchIn(normalized)
 val sd=normalized.contains("标准差")||Regex("(?:^|[^a-z])(?:rdw|pdw)?-?sd(?:$|[^a-z])",RegexOption.IGNORE_CASE).containsMatchIn(normalized)
 return when {cv&&sd->"CONFLICT";cv->"CV";sd->"SD";else->null}
}

internal fun normalizedDistributionWidthUnit(unit:String):String? = when(
 java.text.Normalizer.normalize(unit.trim(),java.text.Normalizer.Form.NFKC)
  .replace(Regex("\\s+"),"").lowercase(java.util.Locale.ROOT)
) {"%"->"%";"fl"->"fL";else->null}

/**
 * Resolve identity from explicit CV/SD evidence first, then from the source unit.
 * A missing/unsupported unit or any contradiction remains reviewable and is never guessed.
 */
internal fun resolveDistributionWidthIdentity(metricKey:String,name:String,unit:String):DistributionWidthResolution {
 val key=canonicalMetricKey(metricKey)
 val keyFamily=when(key){"RDW","RDW-CV","RDW-SD"->"RDW";"PDW","PDW-CV","PDW-SD"->"PDW";else->null}
 val nameFamily=distributionWidthFamilyFromName(name)
 if(keyFamily==null&&nameFamily==null)return DistributionWidthResolution(null,null,unit,null)
 if(keyFamily!=null&&nameFamily!=null&&keyFamily!=nameFamily)
  return DistributionWidthResolution(keyFamily,null,unit,"指标代码与名称属于不同的分布宽度家族，请核对")
 val family=keyFamily?:nameFamily
 val keyVariant=when{key.endsWith("-CV")->"CV";key.endsWith("-SD")->"SD";else->null}
 val nameVariant=distributionWidthVariantFromName(name)
 if(nameVariant=="CONFLICT")return DistributionWidthResolution(family,null,unit,"指标名称同时包含CV和SD，请核对")
 if(keyVariant!=null&&nameVariant!=null&&keyVariant!=nameVariant)
  return DistributionWidthResolution(family,null,unit,"指标代码与名称的CV/SD身份冲突，请核对")
 val explicit=keyVariant?:nameVariant
 val displayUnit=normalizedDistributionWidthUnit(unit)
 if(displayUnit==null)return DistributionWidthResolution(family,null,unit,
  if(unit.isBlank())"缺少单位，无法确认CV/SD身份，请核对" else "单位仅支持%或fL，无法确认CV/SD身份，请核对")
 val unitVariant=if(displayUnit=="%")"CV" else "SD"
 if(explicit!=null&&explicit!=unitVariant)
  return DistributionWidthResolution(family,null,displayUnit,"$explicit 指标与 $displayUnit 单位冲突，请核对")
 return DistributionWidthResolution(family,"$family-${explicit?:unitVariant}",displayUnit,null)
}

internal data class DistributionWidthReview(
 val reportId:String,val resultId:String,val metricKey:String,val name:String,val unit:String,val problem:String
)
internal data class DistributionWidthMigration(
 val reports:List<LabReport>,val reviews:List<DistributionWidthReview>,val changedResults:Int
)

/** Pure, repeatable migration. IDs, values, dates, hospitals and source reference ranges are untouched. */
internal fun migrateDistributionWidthHistory(source:List<LabReport>):DistributionWidthMigration {
 val reviews=mutableListOf<DistributionWidthReview>();var changed=0
 val migrated=source.map{report->
  val resolutions=report.results.associateWith{resolveDistributionWidthIdentity(it.metricKey,it.rawName,it.unitAtTest)}
  val targetCounts=resolutions.values.mapNotNull{it.targetKey}.groupingBy{it}.eachCount()
  report.copy(results=report.results.map{result->
   val resolution=resolutions.getValue(result)
   if(resolution.family==null)return@map result
   val collision=resolution.targetKey?.let{(targetCounts[it]?:0)>1}==true
   val problem=if(collision)"同一报告内存在多个将归入 ${resolution.targetKey} 的项目，已保留原记录并等待核对" else resolution.problem
   if(problem!=null){
    reviews+=DistributionWidthReview(report.id,result.id,result.metricKey,result.rawName,result.unitAtTest,problem)
    result
   }else{
    val target=resolution.targetKey!!
    val normalized=UnitNormalizer.normalize(target,result.value,resolution.displayUnit)
    val next=result.copy(metricKey=target,unitAtTest=resolution.displayUnit,
     normalizedValue=normalized.first,normalizedUnit=normalized.second)
    if(next!=result)changed++
    next
   }
  })
 }
 return DistributionWidthMigration(migrated,reviews,changed)
}
