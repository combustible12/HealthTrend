package com.combustible12.healthtrend

/** Stable ids preserve data-point identity when a whole report is edited. */
data class DraftRow(val id:String=newId(),val name:String="",val key:String="",val text:String="",val unit:String="",val low:String="",val high:String="",val raw:String="",val uncertain:Boolean=false,val trendMeaning:String="",val referenceRaw:String="",val reportFlag:String=""):java.io.Serializable {
 fun parsed():ParsedLabResult{val cleanUnit=unit.trim().takeUnless{it.toDoubleOrNull()!=null}.orEmpty();return ParsedLabResult(key,name,LabResultValues.numeric(text),cleanUnit,low.toDoubleOrNull(),high.toDoubleOrNull(),raw,key in ReportParser.primaryKeys,text.trim(),text.trim().takeWhile{it in "<>≤≥"},rangeRaw(),reportFlag)}
 fun rangeRaw():String {
  if(LabResultValues.kind(text)!=LabValueKind.NUMBER&&low.isBlank()&&high.isBlank())return referenceRaw
  val original=PartialReferenceRanges.parts(referenceRaw)
  return if(referenceRaw.isNotBlank() && original.first==low && original.second==high)referenceRaw
   else if(low.isNotBlank()&&high.isNotBlank())"$low-$high" else if(low.isNotBlank())"≥$low" else if(high.isNotBlank())"≤$high" else ""
 }
 fun referenceNeedsReview():Boolean = reportFlag.length>1 ||
  ('?' in rangeRaw() && PartialReferenceRanges.interval(rangeRaw(),parsed().value,reportFlag.singleOrNull())==null)
 fun valid()=name.isNotBlank()&&key.isNotBlank()&&text.isNotBlank()&&(low.isBlank()||PartialReferenceRanges.bound(low)!=null)&&(high.isBlank()||PartialReferenceRanges.bound(high)!=null)&&ReportParser.valid(listOf(parsed()))
 private fun readResolve():Any=copy(referenceRaw=referenceRaw.orEmpty(),reportFlag=reportFlag.orEmpty())
 companion object{private const val serialVersionUID:Long=-2490116718920370338L
 fun from(p:ParsedLabResult)=DraftRow(name=p.displayName,key=p.metricKey,text=p.textValue,unit=p.unit,low=PartialReferenceRanges.parts(p.referenceRaw).first.takeIf{it.isNotBlank()}?:p.referenceLow?.toString().orEmpty(),high=PartialReferenceRanges.parts(p.referenceRaw).second.takeIf{it.isNotBlank()}?:p.referenceHigh?.toString().orEmpty(),referenceRaw=p.referenceRaw,reportFlag=p.reportFlag,raw=p.rawLine,uncertain=p.metricKey.isBlank()||p.textValue.isBlank()||(p.referenceLow==null)!=(p.referenceHigh==null))}
}
data class ReportDraft(val draftId:String=newId(),val hospital:String="",val type:String="血常规",val system:String="",val date:String=dateText(System.currentTimeMillis()),val images:List<String> = emptyList(),val ocr:String="",val rows:List<DraftRow> = emptyList(),val uncertain:Set<String> = emptySet(),val newTemplate:Boolean=false,val existing:LabReport?=null):java.io.Serializable{
 fun parsed()=rows.map{it.parsed()}
 fun valid()=reportValidationProblems(this).isEmpty()
 companion object{private const val serialVersionUID:Long=-5255338370631356265L
 fun from(r:LabReport)=ReportDraft(hospital=r.hospitalKey,type=r.reportType,system=r.systemKey,date=dateText(r.testedAtEpochMillis),images=r.sourceImages.map{it.uri},ocr=r.rawOcr,rows=r.results.map{x->DraftRow(x.id,x.rawName,x.metricKey,x.textValue,x.unitAtTest,PartialReferenceRanges.parts(x.referenceRaw).first.takeIf{it.isNotBlank()}?:x.referenceLowAtTest?.toString().orEmpty(),PartialReferenceRanges.parts(x.referenceRaw).second.takeIf{it.isNotBlank()}?:x.referenceHighAtTest?.toString().orEmpty(),x.rawLine,referenceRaw=x.referenceRaw,reportFlag=x.reportFlag)},existing=r)}
}
/** Frozen ambiguous identities may survive ordinary edits without being reclassified. */
internal fun unchangedLegacyIdentity(row:DraftRow,report:LabReport?):Boolean {
 val original=report?.results?.singleOrNull{it.id==row.id}?:return false
 return row.key==original.metricKey&&row.name==original.rawName&&row.unit==original.unitAtTest
}

/** Shared by the real editor save path and JVM regression tests. */
fun materializeReportDraft(d:ReportDraft,store:HealthStore):LabReport {
 require(d.valid()){reportValidationProblems(d).joinToString("；")}
 val old=d.existing
 val date=preserveTimestamp(d.date,old?.testedAtEpochMillis)!!
 val created=store.buildReportFromPool(d.hospital,d.type,date,d.images,d.parsed(),d.ocr,d.system)
 val id=old?.id?:created.id
 val images=old?.sourceImages.orEmpty().associateBy{it.uri}
 val results=d.rows.zip(created.results).map{(row,fresh)->
  val previous=old?.results?.singleOrNull{it.id==row.id}
  if(previous!=null&&row==ReportDraft.from(old).rows.single{it.id==row.id}&&
   d.hospital==old.hospitalKey&&d.type==old.reportType&&date==old.testedAtEpochMillis)previous
  else fresh.copy(id=row.id,reportId=id,templateVersion=old?.templateVersion,
   metricKey=row.key,rawName=row.name,unitAtTest=row.unit,
   editedByUser=old!=null||fresh.editedByUser)
 }
 return created.copy(id=id,templateVersion=old?.templateVersion,results=results,
  sourceImages=if(old!=null&&d.images==old.sourceImages.map{it.uri})old.sourceImages else d.images.distinct().mapIndexed{index,uri->
   images[uri]?.copy(pageIndex=index)?:ReportImage(uri,index,System.currentTimeMillis())
  })
}

fun reportValidationProblems(d:ReportDraft):List<String> {
 val problems=mutableListOf<String>()
 if(d.hospital.isBlank())problems+="请填写医院"
 if(d.type.isBlank())problems+="请填写检查类型"
 if(parseDate(d.date)==null)problems+="请填写有效检查日期"
 if(d.rows.isEmpty())problems+="报告没有指标"
 d.rows.forEachIndexed { index,row ->
  val label="第${index+1}项（${row.name.ifBlank{"未命名"}}）"
  val key=row.key.trim()
  when {
   row.name.isBlank()->problems+="${label}：请输入中文项目名称"
   !unchangedLegacyIdentity(row,d.existing)&&distributionWidthIdentityProblem(key,row.name,row.unit)!=null->problems+="${label}：${distributionWidthIdentityProblem(key,row.name,row.unit)}"
   key.isBlank()||key=="未识别"||key.startsWith("待核对:")->
    problems+="${label}：需要确认项目身份"
   row.text.isBlank()->problems+="${label}：检测结果不能为空"
   !LabResultValues.valid(row.text)&&
    d.existing?.results?.singleOrNull{it.id==row.id}?.textValue!=row.text->
    problems+="${label}：检测结果无效"
   row.low.isNotBlank()&&PartialReferenceRanges.bound(row.low)==null->
    problems+="${label}：参考下限无效"
   row.high.isNotBlank()&&PartialReferenceRanges.bound(row.high)==null->
    problems+="${label}：参考上限无效"
   row.low.toDoubleOrNull()!=null&&row.high.toDoubleOrNull()!=null&&
    row.low.toDouble() > row.high.toDouble()->
    problems+="${label}：参考下限大于上限"
   d.existing==null&&row.name.none{it.code in 0x3400..0x9FFF}->
    problems+="${label}：需要确认中文名称"
  }
 }

 val identities=d.rows.map{canonicalMetricKey(it.key.removePrefix("待核对:").trim())}
 if(identities.distinct().size!=identities.size)problems+="项目代码重复"
 // A source report can include two measurements of the same canonical metric
 // only if the user explicitly separates them; do not silently overwrite one.
 if(d.rows.any{it.key=="未识别"||it.key.isBlank()})problems+="存在未确认的项目代码"
 return problems.distinct()
}
