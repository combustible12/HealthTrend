package com.combustible12.healthtrend

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

@Volatile var debugLog:String=""

/** Keep both pieces of evidence visible when a report code and Chinese name disagree. */
internal fun reconciledMetricIdentity(explicitInput:String,namedInput:String):String {
 val explicit=canonicalMetricKey(explicitInput)
 val named=canonicalMetricKey(namedInput)
 // A generic antibody name does not contradict an explicit TPPA method code.
 if(explicit=="TPPA"&&named=="TPAB")return explicit
 return if(namedInput!="未识别"&&named!=explicit)"待核对:代码名称冲突:$explicit" else explicit
}

/** Keep v1 preference names and fields so existing installations migrate in place. */
class HealthStore(private val context:Context) {
 private val prefs=context.getSharedPreferences("healthtrend_store_v1",Context.MODE_PRIVATE)
 // Startup must never seed obsolete hospital templates on a fresh install.
 // Existing legacy templates remain stored unchanged for compatibility/backup.
 // Historical repairs are explicit opt-in operations, never automatic.
 private fun read(key:String)=JSONArray(prefs.getString(key,"[]"))
 private fun write(key:String,a:JSONArray){
  // A durable rollback checkpoint is written BEFORE changing critical datasets.
  // Never restore automatically: doing so could overwrite newer confirmed data.
  if(key in setOf("reports","course_records","templates","metric_standard_ranges","report_identity_links")){
   val previous=prefs.getString(key,null)
   if(previous!=null && previous!=a.toString()){
    val safety=context.getSharedPreferences("healthtrend_safety_snapshots",Context.MODE_PRIVATE)
    check(safety.edit().putString(key+":previous",previous)
     .putLong(key+":savedAt",System.currentTimeMillis()).commit()){
     "无法保存历史数据安全快照，已取消本次修改"
    }
   }
  }
  check(prefs.edit().putString(key,a.toString()).putInt("schema",2).commit()){
   "记录保存失败，请检查设备存储空间"
  }
 }
 private fun <T> rows(a:JSONArray,fn:(JSONObject)->T)= (0 until a.length()).map{fn(a.getJSONObject(it))}
 internal fun correctMaternalBiochemistryAstAlt(t:HospitalLabTemplate):HospitalLabTemplate {
  if(t.hospitalKey.trim()!="福建省妇幼保健院"||t.reportType.trim()!="生化"||t.systemKey.isNotBlank())return t
  // Replace only the erroneous built-in label; keep all other user-confirmed fields intact.
  // Repair only two unambiguous legacy placeholders in the confirmed 36-row layout.
  // Never infer identities from position for shortened or reordered user templates.
  val expectedAt=mapOf(13 to ("AST/ALT" to "谷草/谷丙"),33 to ("CKMB/CK" to "肌酸激酶同工酶/肌酸激酶"))
  val safeLayout=t.fields.size==36 &&
    t.fields.getOrNull(12)?.metricKey=="AST" &&
    t.fields.getOrNull(14)?.metricKey=="GGT" &&
    t.fields.getOrNull(32)?.metricKey=="CKMB" &&
    t.fields.getOrNull(34)?.metricKey=="AG"
  val repaired=if(safeLayout)t.fields.mapIndexed{index,field->
    val target=expectedAt[index]
    val placeholder=field.displayName.trim() in setOf("待核对指标","") &&
      (field.metricKey.isBlank() || field.metricKey.startsWith("UNKNOWN",true) || field.metricKey.startsWith("UNMATCHED",true))
    if(target!=null && placeholder && t.fields.none{it.metricKey==target.first})
      field.copy(metricKey=target.first,displayName=target.second)
    else field
  } else t.fields
  val corrected=repaired.map{field->
   if(field.metricKey=="ALT" && field.displayName.trim() in setOf("丙氨酸氨基转移酶","谷丙氨酸氨基转移酶"))
     field.copy(displayName="谷丙转氨酶")
    else if(field.metricKey=="AST" && field.displayName.trim() in setOf("门冬氨酸氨基转移酶","天门冬氨酸氨基转移酶"))
    field.copy(displayName="谷草转氨酶")
   else if(field.metricKey=="CKMB/CK" && field.displayName.trim() in setOf("CKMB/CK","CKMB:CK","待核对指标","肌酸激酶同工酶与肌酸激酶比值"))
     field.copy(displayName="肌酸激酶同工酶/肌酸激酶")
    else if(field.metricKey=="AST/ALT"&&field.displayName.trim() in setOf("AST:ALT","AST/ALT","谷草/谷丙比值","AST/ALT 谷草/谷丙"))
    field.copy(displayName="谷草/谷丙")
   else field
  }
  return if(corrected==t.fields)t else t.copy(fields=corrected)
 }
 internal fun ensureMaternalCbcRdwFields(t:HospitalLabTemplate):HospitalLabTemplate {
  if(t.hospitalKey.trim()!="福建省妇幼保健院" || t.reportType.trim()!="血常规" || t.systemKey.isNotBlank())return t
  val original=t.fields
  val candidates=original.withIndex().filter{(_,f)->f.metricKey in setOf("RDW","RDW-CV","RDW-SD")}
  // Never guess when a legacy template has multiple entries with the same unit.
  if(candidates.count{displayLabUnit(it.value.unit)=="fL"}>1 || candidates.count{displayLabUnit(it.value.unit)=="%"}>1)return t
  val sd=candidates.singleOrNull{displayLabUnit(it.value.unit)=="fL"}
  val cv=candidates.singleOrNull{displayLabUnit(it.value.unit)=="%"}
  if(sd==null)return t
  val fields=original.toMutableList()
  fields[sd.index]=sd.value.copy(metricKey="RDW-SD",displayName="红细胞分布宽度SD")
  if(cv!=null){
   fields[cv.index]=cv.value.copy(metricKey="RDW-CV",displayName="红细胞分布宽度CV")
   val moved=fields.removeAt(cv.index)
   fields.add(fields.indexOfFirst{it.metricKey=="RDW-SD"},moved)
  }else{
   fields.add(fields.indexOfFirst{it.metricKey=="RDW-SD"},LabFieldTemplate("RDW-CV","红细胞分布宽度CV","%",12.2,15.0))
  }
  return if(fields==original)t else t.copy(fields=fields)
 }
 internal fun repairXiacuBiochemistryTemplate(t:HospitalLabTemplate):HospitalLabTemplate{
  if(t.hospitalKey.trim()!="霞浦县中医院"||t.reportType.trim()!="生化"||t.systemKey.isNotBlank())return t
  val repaired=t.fields.map{field->
   // Repair identity only. Preserve user-edited name/unit/range/trend meaning.
   val explicit=field.displayName.trim().substringBefore(' ').uppercase()
   when(explicit){
    "AST"->if(field.metricKey=="AST")field else field.copy(metricKey="AST")
    "AST/ALT"->if(field.metricKey=="AST/ALT")field else field.copy(metricKey="AST/ALT")
    else->field
   }
  }
  // Persist only a safe repair: every metric identity must remain unique.
  return if(repaired.map{it.metricKey}.distinct().size==repaired.size)t.copy(fields=repaired) else t
 }
 fun bloodGroupProfile():BloodGroupProfile=BloodGroupProfile.fromJson(prefs.getString("blood_group_profile",null))
 @Synchronized fun saveBloodGroupProfile(p:BloodGroupProfile){check(prefs.edit().putString("blood_group_profile",p.validated().toJson().toString()).commit()){"血型保存失败"}}
 fun patientProfile():PatientProfile { val raw=prefs.getString("patient_profile",null)?:return PatientProfile();return runCatching{val o=JSONObject(raw);PatientProfile(o.optString("name"),o.optString("birthDate"),o.optString("sex"),o.optString("note"),o.optString("bloodType"))}.getOrDefault(PatientProfile()) }
 @Synchronized fun savePatientProfile(p:PatientProfile){val o=JSONObject().put("name",p.name.trim()).put("birthDate",p.birthDate.trim()).put("sex",p.sex.trim()).put("note",p.note.trim()).put("bloodType",p.bloodType.trim());check(prefs.edit().putString("patient_profile",o.toString()).commit())}
 @Synchronized fun reports():List<LabReport>{
  val reports=rows(read("reports"),::reportFromJson).sortedByDescending{it.testedAtEpochMillis}
  return reports
 }
 data class ReportReplacement(
  val id:String,val oldReport:LabReport,val newReport:LabReport,
  val replacedAt:Long,val restoredAt:Long?
 )
 @Synchronized fun archivedReportReplacements():List<ReportReplacement> = rows(read("report_replacement_archive")){o->
  ReportReplacement(o.getString("id"),reportFromJson(o.getJSONObject("old")),reportFromJson(o.getJSONObject("new")),
   o.getLong("at"),if(o.isNull("restored"))null else o.getLong("restored"))
 }.sortedByDescending{it.replacedAt}
 fun replacementCandidates(report:LabReport):List<LabReport>{
  val zone=java.time.ZoneId.systemDefault()
  val day=java.time.Instant.ofEpochMilli(report.testedAtEpochMillis).atZone(zone).toLocalDate()
  return reports().filter{old->
   old.id!=report.id&&old.hospitalKey.trim()==report.hospitalKey.trim()&&old.reportType.trim()==report.reportType.trim()&&
    java.time.Instant.ofEpochMilli(old.testedAtEpochMillis).atZone(zone).toLocalDate()==day
  }
 }
 private fun validateReplacementReport(r:LabReport,old:LabReport){
  require(r.results.isNotEmpty()){"报告至少需要一项检验结果"}
  require(r.hospitalKey.isNotBlank()&&r.reportType.isNotBlank()&&r.testedAtEpochMillis>0){"报告来源或日期无效"}
  require(r.results.all{it.value==null||it.value.isFinite()}){"报告包含无效数值"}
  require(r.results.all{it.metricKey.isNotBlank()&&it.metricKey!="未识别"&&!it.metricKey.startsWith("待核对:")&&it.reportId==r.id}){"报告项目身份无效或尚未核对"}
  require(r.results.map{canonicalMetricKey(it.metricKey)}.distinct().size==r.results.size){"报告存在重复项目 ID"}
  require(r.results.none{distributionWidthIdentityProblem(it.metricKey,it.rawName,it.unitAtTest)!=null}){"报告项目CV/SD身份与单位不一致，请核对后保存"}
  require(old.id==r.id){"替换报告必须沿用原报告 ID"}
 }
 /** Atomically replaces one active report while retaining both versions as a reversible audit event. */
 @Synchronized fun replaceReportWithConfirmed(oldReportId:String,confirmed:LabReport):ReportReplacement{
  val active=reports();val old=active.singleOrNull{it.id==oldReportId}?:error("待替换的旧报告不存在")
  require(replacementCandidates(confirmed).any{it.id==oldReportId}){"医院、日期或检查类型与旧报告不一致"}
  val id=old.id
  val prepared=confirmed.copy(id=id,sourceImages=(old.sourceImages+confirmed.sourceImages).distinctBy{it.uri},results=confirmed.results.map{
   it.copy(reportId=id,hospitalKey=confirmed.hospitalKey,reportType=confirmed.reportType,testedAtEpochMillis=confirmed.testedAtEpochMillis)
  })
  validateReplacementReport(prepared,old)
  val event=ReportReplacement(newId(),old,prepared,System.currentTimeMillis(),null)
  val beforeReports=read("reports").toString();val beforeArchive=read("report_replacement_archive").toString()
  val afterReports=JSONArray().apply{(active.filterNot{it.id==id}+prepared).forEach{put(reportToJson(it))}}
  val afterArchive=JSONArray(beforeArchive).put(replacementToJson(event))
  val safety=context.getSharedPreferences("healthtrend_safety_snapshots",Context.MODE_PRIVATE)
  check(safety.edit().putString("reports:previous",beforeReports).putString("report_replacement_archive:previous",beforeArchive).putLong("reports:savedAt",System.currentTimeMillis()).commit()){"无法保存替换前安全快照"}
  try{
   check(prefs.edit().putString("reports",afterReports.toString()).putString("report_replacement_archive",afterArchive.toString()).putInt("schema",2).commit()){"报告替换保存失败"}
   val stored=reports().singleOrNull{it.id==id}?:error("替换后无法读取新报告")
   check(stored.results.map{it.metricKey to it.textValue}==prepared.results.map{it.metricKey to it.textValue}){"替换后报告内容校验失败"}
   check(archivedReportReplacements().any{it.id==event.id&&it.oldReport.results==old.results}){"旧报告封存校验失败"}
  }catch(e:Exception){prefs.edit().putString("reports",beforeReports).putString("report_replacement_archive",beforeArchive).commit();throw e}
  prepared.results.forEach{if('?' !in it.referenceRaw&&(it.referenceLowAtTest!=null||it.referenceHighAtTest!=null))addStandardRange(it.metricKey,it.unitAtTest,it.referenceLowAtTest,it.referenceHighAtTest)}
  return event
 }
 @Synchronized fun restoreArchivedReport(replacementId:String){
  val events=archivedReportReplacements();val event=events.singleOrNull{it.id==replacementId}?:error("封存记录不存在")
  require(event.restoredAt==null){"这份旧报告已经恢复"}
  val active=reports();require(active.any{it.id==event.newReport.id}){"当前替换报告不存在，不能自动恢复"}
  val restoredEvent=event.copy(restoredAt=System.currentTimeMillis())
  val beforeReports=read("reports").toString();val beforeArchive=read("report_replacement_archive").toString()
  val afterReports=JSONArray().apply{(active.filterNot{it.id==event.newReport.id}+event.oldReport).forEach{put(reportToJson(it))}}
  val afterArchive=JSONArray().apply{events.reversed().forEach{put(replacementToJson(if(it.id==event.id)restoredEvent else it))}}
  try{
   check(prefs.edit().putString("reports",afterReports.toString()).putString("report_replacement_archive",afterArchive.toString()).commit()){"旧报告恢复保存失败"}
   check(reports().any{it.id==event.oldReport.id&&it.results==event.oldReport.results}){"旧报告恢复校验失败"}
  }catch(e:Exception){prefs.edit().putString("reports",beforeReports).putString("report_replacement_archive",beforeArchive).commit();throw e}
 }
 /**
  * Preserve reference intervals by metric identity + unit + bounds only.
  * Hospital stays on LabReport, never inside the dictionary or its range key.
  */
 data class ObservedReferenceRange(
  val metricKey:String,val unit:String,val lower:Double?,val upper:Double?,
  val firstObservedAt:Long,val lastObservedAt:Long,val reportCount:Int
 )
 @Synchronized fun referenceRangeHistory(metricKey:String):List<ObservedReferenceRange> {
  val identity=canonicalMetricKey(metricKey)
  data class Evidence(val report:LabReport,val result:LabResult)
  val evidence=reports().flatMap{report->report.results
   .filter{canonicalMetricKey(it.metricKey)==identity &&
    (it.referenceLowAtTest!=null||it.referenceHighAtTest!=null)&&'?' !in it.referenceRaw}
   .map{result->
    val lower=UnitNormalizer.normalize(identity,result.referenceLowAtTest,result.unitAtTest)
    val upper=UnitNormalizer.normalize(identity,result.referenceHighAtTest,result.unitAtTest)
    val unit=if(result.referenceLowAtTest!=null)lower.second else upper.second
    val low=lower.first
    val high=upper.first
    Evidence(report,result.copy(unitAtTest=unit,referenceLowAtTest=low,referenceHighAtTest=high))
   }
  }
  return evidence.groupBy{e->listOf(
   identity,e.result.unitAtTest,e.result.referenceLowAtTest?.toString().orEmpty(),
   e.result.referenceHighAtTest?.toString().orEmpty()
  )}.values.map{group->
   val first=group.first()
   ObservedReferenceRange(identity,first.result.unitAtTest,
    first.result.referenceLowAtTest,first.result.referenceHighAtTest,
    group.minOf{it.report.testedAtEpochMillis},
    group.maxOf{it.report.testedAtEpochMillis},
    group.map{it.report.id}.distinct().size)
  }.sortedWith(compareBy<ObservedReferenceRange>{it.firstObservedAt}
   .thenBy{it.unit}.thenBy{it.lower}.thenBy{it.upper})
 }
 data class StandardRange(val metricKey:String,val unit:String,val low:Double?,val high:Double?)
 private fun normalizedStandardRange(key:String,unit:String,low:Double?,high:Double?):StandardRange {
  val identity=canonicalMetricKey(key)
  require(low!=null||high!=null){"参考范围不能为空"}
  require(low==null||low.isFinite()){"参考下限无效"}
  require(high==null||high.isFinite()){"参考上限无效"}
  val a=UnitNormalizer.normalize(identity,low,unit)
  val b=UnitNormalizer.normalize(identity,high,unit)
   val normalizedLow=a.first;val normalizedHigh=b.first
   require(normalizedLow==null||normalizedHigh==null||normalizedLow<=normalizedHigh){"参考范围上下限不正确"}
  val common=UnifiedMetricStandard.definition(identity)?.unit.orEmpty()
  val actual=if(low!=null)a.second else b.second
  // An unconvertible source unit must never be relabelled as the common unit.
  val target=if(common.isNotBlank()&&actual.equals(common,true))common else actual
  return StandardRange(identity,target,a.first,b.first)
 }
 private fun standardRangesStored():List<StandardRange> =
  rows(read("metric_standard_ranges")){o->StandardRange(
   o.getString("metric"),o.getString("unit"),o.doubleOrNull("low"),o.doubleOrNull("high"))}
 @Synchronized fun standardRanges(metricKey:String):List<StandardRange> {
  val key=canonicalMetricKey(metricKey)
  return standardRangesStored().filter{it.metricKey==key}.distinct()
 }
 /** Add or reuse a range in the persistent standard pool, independent of hospital. */
 @Synchronized fun addStandardRange(metricKey:String,unit:String,low:Double?,high:Double?):StandardRange {
  val entry=normalizedStandardRange(metricKey,unit,low,high)
  val existing=standardRangesStored()
  if(entry in existing)return entry
  val updated=existing+entry
  write("metric_standard_ranges",JSONArray().apply{updated.forEach{range->
   put(JSONObject().put("metric",range.metricKey).put("unit",range.unit)
    .put("low",range.low?:JSONObject.NULL).put("high",range.high?:JSONObject.NULL))
  }})
  return entry
 }
 @Synchronized fun removeStandardRange(entry:StandardRange){
  val retained=standardRangesStored().filterNot{it==entry}
  write("metric_standard_ranges",JSONArray().apply{retained.forEach{range->
   put(JSONObject().put("metric",range.metricKey).put("unit",range.unit)
    .put("low",range.low?:JSONObject.NULL).put("high",range.high?:JSONObject.NULL))
  }})
 }
 @Synchronized fun editStandardRange(previous:StandardRange,unit:String,low:Double?,high:Double?):StandardRange {
  val entry=normalizedStandardRange(previous.metricKey,unit,low,high)
  val updated=standardRangesStored().filterNot{it==previous}.plus(entry).distinct()
  write("metric_standard_ranges",JSONArray().apply{updated.forEach{range->
   put(JSONObject().put("metric",range.metricKey).put("unit",range.unit)
    .put("low",range.low?:JSONObject.NULL).put("high",range.high?:JSONObject.NULL))
  }})
  return entry
 }
 @Synchronized fun saveReport(input:LabReport){
  // Do not rewrite a confirmed report's metric identities on ordinary save.
  // Legacy identity repairs are intentionally not part of the save path.
  val normalized=input
  // Report editors can hold an older snapshot than an attachment import.
  // Keep persisted images when that snapshot is saved; never silently discard them.
  val persisted=reports().firstOrNull{it.id==normalized.id}
  val existingImages=persisted?.sourceImages.orEmpty()
  val r=if(existingImages.isEmpty())normalized else normalized.copy(
   sourceImages=(existingImages+normalized.sourceImages).distinctBy{it.uri}
  )
  require(r.results.isNotEmpty()){"报告至少需要一项检验结果"}
  require(r.hospitalKey.isNotBlank()){"医院名称不能为空"}
  require(r.reportType.isNotBlank()){"检查类型不能为空"}
  require(r.testedAtEpochMillis>0){"检查日期无效"}
  require(r.results.all{it.value==null||it.value.isFinite()}){"报告包含无效数值"}
  require(r.results.all{it.metricKey.isNotBlank()&&it.metricKey!="未识别"&&
   !it.metricKey.startsWith("待核对:")&&it.reportId==r.id&&
   it.hospitalKey==r.hospitalKey&&it.reportType==r.reportType}){"报告项目身份无效或尚未核对"}
  require(r.results.map{canonicalMetricKey(it.metricKey)}.distinct().size==r.results.size){"报告存在重复项目 ID"}
  require(r.results.none{result->
   val old=persisted?.results?.singleOrNull{it.id==result.id}
   val preserved=old!=null&&old.metricKey==result.metricKey&&old.rawName==result.rawName&&old.unitAtTest==result.unitAtTest
   !preserved&&distributionWidthIdentityProblem(result.metricKey,result.rawName,result.unitAtTest)!=null
  }){"报告项目CV/SD身份与单位不一致，请核对后保存"}
  // Verify persisted content before recording secondary reference-range observations.
  // If verification fails, restore the exact previous reports JSON.
  val originalReports=read("reports").toString()
  val ordered=r
  try {
   write("reports",JSONArray().apply{
    (reports().filterNot{it.id==r.id}+ordered).forEach{put(reportToJson(it))}
   })
   val stored=reports().firstOrNull{it.id==r.id}?:error("报告保存验证失败：重新读取不到报告")
   check(stored.hospitalKey==ordered.hospitalKey &&
    stored.reportType==ordered.reportType &&
    stored.testedAtEpochMillis==ordered.testedAtEpochMillis &&
    stored.results.size==ordered.results.size &&
    stored.results.map{it.id}==ordered.results.map{it.id} &&
    stored.results.map{it.metricKey}==ordered.results.map{it.metricKey} &&
    stored.results.map{it.rawName}==ordered.results.map{it.rawName} &&
    stored.results.map{it.value}==ordered.results.map{it.value} &&
    stored.results.map{it.textValue}==ordered.results.map{it.textValue} &&
    stored.results.map{it.unitAtTest}==ordered.results.map{it.unitAtTest} &&
    stored.results.map{it.referenceLowAtTest}==ordered.results.map{it.referenceLowAtTest} &&
    stored.results.map{it.referenceHighAtTest}==ordered.results.map{it.referenceHighAtTest} &&
    stored.results.map{it.referenceRaw to it.reportFlag}==ordered.results.map{it.referenceRaw to it.reportFlag}){
    "报告保存验证失败：重新读取的数据与保存内容不一致"
   }
   check(r.sourceImages.all{image->stored.sourceImages.any{it.uri==image.uri}}){
    "报告保存验证失败：原图关联未持久化"
   }
  }catch(e:Exception){
   runCatching{write("reports",JSONArray(originalReports))}
    .onFailure{rollback->e.addSuppressed(rollback)}
   throw e
  }
  // Only after the report itself has passed read-back verification.
  ordered.results.forEach{result->
   if('?' !in result.referenceRaw&&(result.referenceLowAtTest!=null||result.referenceHighAtTest!=null)){
    addStandardRange(result.metricKey,result.unitAtTest,
     result.referenceLowAtTest,result.referenceHighAtTest)
   }
  }
 }
 @Synchronized fun deleteReport(id:String){
  val before=read("reports").toString()
  val original=reports()
  if(original.none{it.id==id})return
  val expected=original.filterNot{it.id==id}
  try {
   write("reports",JSONArray().apply{expected.forEach{put(reportToJson(it))}})
   val actual=reports()
   check(actual.map{it.id}.toSet()==expected.map{it.id}.toSet() &&
    actual.size==expected.size &&
    actual.none{it.id==id}){
    "报告删除验证失败：原报告仍存在或其他报告丢失"
   }
  }catch(e:Exception){
   runCatching{write("reports",JSONArray(before))}
    .onFailure{rollback->e.addSuppressed(rollback)}
   throw e
  }
 }
 private fun historyReplacementBatch(text:String):HistoryReplacementBatch{
  val o=JSONObject(text)
  require(o.getString("format")=="healthtrend-history-replacement-v1"){"不是 HealthTrend 历史核对文件"}
  val keys=o.getJSONArray("metricKeys").let{a->(0 until a.length()).map{a.getString(it)}}
  val reportRows=o.getJSONArray("reports").let{a->(0 until a.length()).map{i->
   val r=a.getJSONObject(i);val values=r.getJSONArray("values")
   HistoryReplacementRow(java.time.LocalDate.parse(r.getString("date")),(0 until values.length()).map{values.getString(it)})
  }}
  val fields=o.optJSONArray("fields")?.let{a->rows(a){f->LabFieldTemplate(f.getString("key"),f.getString("name"),displayLabUnit(f.getString("unit")),f.doubleOrNull("low"),f.doubleOrNull("high"),f.optString("trendMeaning").ifBlank{metricPurpose(f.getString("key")).orEmpty()})}}
  return HistoryReplacementBatch(o.getString("hospital"),o.getString("type"),o.optString("system"),keys,reportRows,fields)
 }
 @Synchronized fun previewHistoryReplacement(text:String):String{
  val batch=historyReplacementBatch(text)
  val source=latestTemplate(batch.hospital,batch.type,batch.system)?:error("未找到已确认的医院模板")
  val template=historyReplacementTemplate(source,batch)
  replaceReportHistory(reports(),template,batch)
  val binding=if(template==source)"" else "\n\n按核对文件重建完整模板，清除旧项目身份。\n"+template.fields.mapIndexed{i,f->"${i+1}. ${labDisplayTitle(f.displayName,f.metricKey)}"}.joinToString("\n")
  return "${batch.hospital} · ${batch.type}\n${batch.reports.size} 份报告 × ${batch.metricKeys.size} 项 = ${batch.reports.sumOf{it.values.size}} 个结果\n${batch.reports.joinToString("、"){it.date.toString().replace('-','/')}}$binding"
 }
 @Synchronized fun importHistoryReplacement(text:String):String{
  val batch=historyReplacementBatch(text)
  val fingerprint=java.security.MessageDigest.getInstance("SHA-256").digest(batch.copy(reports=batch.reports.sortedBy{it.date}).toString().toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
  val marker="history_replacement:$fingerprint"
  if(prefs.getBoolean(marker,false))return "此核对文件已导入，无需重复替换"
  val source=latestTemplate(batch.hospital,batch.type,batch.system)?:error("未找到已确认的医院模板")
  val template=historyReplacementTemplate(source,batch)
  val all=reports();val replaced=replaceReportHistory(all,template,batch)
  val before=prefs.getString("reports","[]")?:"[]"
  val templatesBefore=prefs.getString("templates","[]")?:"[]"
  val templatesAfter=JSONArray(templatesBefore).apply{if(template!=source){for(i in 0 until length()){
   val original=getJSONObject(i)
   if(original.getString("hospital")==batch.hospital&&original.getString("type")==batch.type&&original.optString("system")==batch.system)put(i,templateToJson(template))
  }}}
  val originalById=all.associateBy{it.id}
  val updates=replaced.filter{originalById[it.id]!==it}.associateBy{it.id}
  val removed=originalById.keys-replaced.map{it.id}.toSet()
  val raw=JSONArray(before)
  val after=JSONArray().apply{(0 until raw.length()).forEach{i->
   val original=raw.getJSONObject(i)
   if(original.getString("id") !in removed)put(updates[original.getString("id")]?.let(::reportToJson)?:original)
  }}
  val previousBackup=prefs.getString("history_replacement_backup",null)
  val previousTemplateBackup=prefs.getString("history_replacement_template_backup",null)
  val hadMarker=prefs.contains(marker)
  // One atomic preference write: no partial replacement and no marker without data.
  val saved=prefs.edit().putString("history_replacement_backup",before)
   .putString("history_replacement_template_backup",templatesBefore).putString("templates",templatesAfter.toString())
   .putString("reports",after.toString()).putBoolean(marker,true).commit()
  if(!saved){
   // A failed disk commit may still update SharedPreferences' in-memory cache.
   val rollback=prefs.edit().putString("reports",before).putString("templates",templatesBefore)
   if(previousTemplateBackup==null)rollback.remove("history_replacement_template_backup") else rollback.putString("history_replacement_template_backup",previousTemplateBackup)
   if(previousBackup==null)rollback.remove("history_replacement_backup") else rollback.putString("history_replacement_backup",previousBackup)
   if(hadMarker)rollback.putBoolean(marker,false) else rollback.remove(marker)
   rollback.commit()
   error("报告替换保存失败，已恢复替换前的记录")
  }
  return "已替换 ${batch.reports.size} 份报告，共 ${batch.reports.sumOf{it.values.size}} 个结果"
 }
 @Synchronized fun addReportImages(reportId:String,uris:List<Uri>){
  if(uris.isEmpty())return
  val r=reports().first{it.id==reportId}
  val now=System.currentTimeMillis()
  val start=r.sourceImages.size
  val owned=uris.mapIndexed{i,uri->ReportImage(ownImage(uri),start+i,now)}
  saveReport(r.copy(sourceImages=r.sourceImages+owned))
  // Verify the durable record rather than trusting the in-memory update.
  // Do not delete copied images on failure: they may be the only surviving originals.
  val saved=reports().firstOrNull{it.id==reportId}
   ?:error("原图关联验证失败：报告不存在")
  check(owned.all{image->saved.sourceImages.any{it.uri==image.uri}}){
   "原图关联验证失败：重新读取报告后未找到新图片"
  }
  check(owned.all{image->
   val path=Uri.parse(image.uri).path
   path!=null && File(path).isFile && File(path).length()>0L
  }){"原图文件验证失败：图片不存在或为空"}
 }

 /** Explicit attachment edits use the existing report store, without changing lab results.
  * Do not call saveReport: its stale-editor protection intentionally merges old attachments. */
 @Synchronized fun updateReportImages(reportId:String,images:List<ReportImage>){
  val current=reports().firstOrNull{it.id==reportId}?:error("报告不存在")
  require(images.map{it.uri}.distinct().size==images.size){"重复原图"}
  val updated=current.copy(sourceImages=images.mapIndexed{index,image->image.copy(pageIndex=index)})
  val original=read("reports").toString()
  write("reports",JSONArray().apply{(reports().filterNot{it.id==reportId}+updated).forEach{put(reportToJson(it))}})
  val actual=reports().firstOrNull{it.id==reportId}?.sourceImages?.map{it.uri}
  if(actual!=updated.sourceImages.map{it.uri}){
   runCatching{write("reports",JSONArray(original))}
   error("原图更新验证失败，已尝试恢复原记录")
  }
 }
 @Synchronized fun replaceReportImage(reportId:String,index:Int,uri:Uri){
  val current=reports().firstOrNull{it.id==reportId}?:error("报告不存在")
  require(index in current.sourceImages.indices){"原图序号无效"}
  val owned=ownImage(uri)
  val path=Uri.parse(owned).path
  check(path!=null&&File(path).isFile&&File(path).length()>0){"新原图文件保存失败"}
  val updated=current.sourceImages.toMutableList()
  updated[index]=ReportImage(owned,index,System.currentTimeMillis())
  updateReportImages(reportId,updated)
 }
 @Synchronized fun removeReportImage(reportId:String,index:Int){
  val current=reports().firstOrNull{it.id==reportId}?:error("报告不存在")
  require(index in current.sourceImages.indices){"原图序号无效"}
  updateReportImages(reportId,current.sourceImages.filterIndexed{i,_->i!=index})
 }
 @Synchronized fun moveReportImage(reportId:String,from:Int,to:Int){
  val current=reports().firstOrNull{it.id==reportId}?:error("报告不存在")
  require(from in current.sourceImages.indices&&to in current.sourceImages.indices){"原图序号无效"}
  val items=current.sourceImages.toMutableList()
  items.add(to,items.removeAt(from))
  updateReportImages(reportId,items)
 }

 /** Report-management links are explicit audit events, separate from frozen trend mappings. */
 data class ReportIdentityLink(
  val id:String,val source:String,val target:String,val confirmedAt:Long,val revokedAt:Long?,
  val reportIds:List<String> = emptyList(),val resultIds:List<String> = emptyList(),
  val sourceNames:List<String> = emptyList(),val sourceUnits:List<String> = emptyList()
 )
 data class ReportIdentityCandidate(
  val source:String,val sourceNames:List<String>,val sourceUnits:List<String>,
  val reportIds:List<String>,val resultIds:List<String>,
  val activeTarget:String?,val activeLinkId:String?
 )
 @Synchronized fun reportIdentityLinks():List<ReportIdentityLink> = rows(read("report_identity_links")){o->
  ReportIdentityLink(o.getString("id"),o.getString("source"),o.getString("target"),o.getLong("at"),
   if(o.isNull("revoked"))null else o.getLong("revoked"),
   o.optJSONArray("reports").stringList(),o.optJSONArray("results").stringList(),
   o.optJSONArray("names").stringList(),o.optJSONArray("units").stringList())
 }
 fun reportPoolIdentity(sourceInput:String):String {
  return reportIdentityLinks().lastOrNull{it.source.equals(sourceInput,true)&&it.revokedAt==null}?.target?:sourceInput
 }
 /** Complete read-only inventory for explicit historical identity review.
  * Merely listing candidates never creates a link or changes a report. */
 fun reportIdentityCandidates():List<ReportIdentityCandidate>{
  val links=reportIdentityLinks()
  return reports().flatMap{report->report.results.map{result->report to result}}
   .filter{(_,result)->result.metricKey.startsWith("CUSTOM-",true)}
   .groupBy{(_,result)->result.metricKey.trim().lowercase()}
   .map{(_,occurrences)->
    val source=occurrences.first().second.metricKey.trim()
    val active=links.lastOrNull{it.source.equals(source,true)&&it.revokedAt==null}
    ReportIdentityCandidate(source,
     occurrences.map{it.second.rawName.trim()}.filter{it.isNotBlank()}.distinct().sorted(),
     occurrences.map{it.second.unitAtTest.trim()}.filter{it.isNotBlank()}.distinct().sorted(),
     occurrences.map{it.first.id}.distinct().sorted(),occurrences.map{it.second.id}.distinct().sorted(),
     active?.target,active?.id)
   }.sortedBy{it.source}
 }
 @Synchronized fun confirmReportIdentityLink(source:String,targetInput:String){
  val normalizedSource=source.trim()
  val target=canonicalMetricKey(targetInput)
  require(normalizedSource.startsWith("CUSTOM-",true)){"仅为历史自定义指标建立关联"}
  require(target!="PDW"&&UnifiedMetricStandard.definition(target)!=null){"请选择明确的统一指标，普通PDW已冻结"}
  val occurrences=reports().flatMap{report->report.results.map{report to it}}
   .filter{(_,result)->result.metricKey.equals(normalizedSource,true)}
  val matches=occurrences.map{it.second}
  require(matches.isNotEmpty()){"没有对应历史指标"}
  val persistedSource=matches.first().metricKey
  require(matches.all{val explicit=ReportParser.key(it.rawName);explicit=="未识别"||canonicalMetricKey(explicit)==target}){"原报告名称包含不同或模糊测量身份，请先核对"}
  require(matches.none{distributionWidthIdentityProblem(target,it.rawName,it.unitAtTest)!=null}){"测量身份与原报告冲突"}
  require(reportIdentityLinks().none{it.source.equals(persistedSource,true)&&it.revokedAt==null}){"请先撤销原关联"}
  writeReportIdentityLinks(reportIdentityLinks()+ReportIdentityLink(newId(),persistedSource,target,System.currentTimeMillis(),null,
   occurrences.map{it.first.id}.distinct().sorted(),matches.map{it.id}.distinct().sorted(),
   matches.map{it.rawName.trim()}.filter{it.isNotBlank()}.distinct().sorted(),
   matches.map{it.unitAtTest.trim()}.filter{it.isNotBlank()}.distinct().sorted()))
 }
 @Synchronized fun revokeReportIdentityLink(id:String){
  writeReportIdentityLinks(reportIdentityLinks().map{if(it.id==id&&it.revokedAt==null)it.copy(revokedAt=System.currentTimeMillis())else it})
 }
 private fun writeReportIdentityLinks(links:List<ReportIdentityLink>){
  write("report_identity_links",JSONArray().apply{links.forEach{
   put(JSONObject().put("id",it.id).put("source",it.source).put("target",it.target)
    .put("at",it.confirmedAt).put("revoked",it.revokedAt?:JSONObject.NULL)
    .put("reports",JSONArray(it.reportIds)).put("results",JSONArray(it.resultIds))
    .put("names",JSONArray(it.sourceNames)).put("units",JSONArray(it.sourceUnits)))
  }})
 }

 /** Explicit, persistent display-only identity mapping. Never change stored report metricKeys.
  * Only old CUSTOM identities may be linked to an existing standard identity;
  * no name similarity or unit inference is permitted.
  */
 @Synchronized fun trendIdentityMappings():Map<String,String> =
  rows(read("trend_identity_mappings")){o->
   val source=canonicalMetricKey(o.optString("source"))
   val target=canonicalMetricKey(o.optString("target"))
   if(source.startsWith("CUSTOM-")&&target.isNotBlank()&&source!=target)source to target else null
  }.filterNotNull().toMap()
 fun trendIdentity(metricKey:String):String{
  val key=canonicalMetricKey(metricKey)
  return trendIdentityMappings()[key]?:key
 }
 @Synchronized fun confirmTrendIdentityMapping(sourceInput:String,targetInput:String){
  val source=canonicalMetricKey(sourceInput)
  val target=canonicalMetricKey(targetInput)
  require(source.startsWith("CUSTOM-")){"仅允许为历史自定义指标建立显式兼容映射"}
  require(target.isNotBlank()&&target!=source&&!target.startsWith("CUSTOM-")){"请选择不同的现有标准指标"}
  require(UnifiedMetricStandard.definition(target)!=null){"目标不在标准指标词典中"}
  require(reports().any{report->report.results.any{canonicalMetricKey(it.metricKey)==source}}){"未找到该历史自定义指标"}
  val mappings=trendIdentityMappings().toMutableMap()
  mappings[source]=target
  write("trend_identity_mappings",JSONArray().apply{mappings.toSortedMap().forEach{(from,to)->
   put(JSONObject().put("source",from).put("target",to))
  }})
 }
 @Synchronized fun removeTrendIdentityMapping(sourceInput:String){
  val source=canonicalMetricKey(sourceInput)
  val mappings=trendIdentityMappings().toMutableMap()
  if(mappings.remove(source)==null)return
  write("trend_identity_mappings",JSONArray().apply{mappings.toSortedMap().forEach{(from,to)->
   put(JSONObject().put("source",from).put("target",to))
  }})
 }
 fun trend(key:String)=reports().flatMap{r->r.results.filter{trendIdentity(it.metricKey)==trendIdentity(key) && it.textValue.isNotBlank() && (it.value==null||it.comparator.isEmpty())}.map{r to it}}.sortedWith(compareBy<Pair<LabReport,LabResult>>{it.first.testedAtEpochMillis}.thenBy{it.second.id})
 /** User aliases are separate from hospital templates and historical report snapshots. */
 @Synchronized fun metricAliases():Map<String,String>{
  val data=read("metric_aliases")
  return (0 until data.length()).mapNotNull { index ->
   val row=data.optJSONObject(index) ?: return@mapNotNull null
   val alias=row.optString("alias").trim()
   val key=row.optString("metricKey").trim()
   if(alias.isBlank()||key.isBlank())null else alias to key
  }.toMap()
 }
 @Synchronized fun saveMetricAlias(aliasInput:String,metricKeyInput:String){
  val alias=aliasInput.trim()
  val metricKey=canonicalMetricKey(metricKeyInput.trim())
  require(alias.isNotBlank()&&metricKey.isNotBlank()){"别名和项目代码不能为空"}
  require(!alias.contains('\n')&&!metricKey.contains('\n')){"别名或代码不能包含换行"}
  val validKeys=(customMetrics().map{it.key}+
   reports().flatMap{it.results.map{result->canonicalMetricKey(result.metricKey)}})
   .toSet()
  require(metricKey in validKeys || metricKey in ReportParser.builtInMetricKeys()){
   "项目代码不存在，请先在报告中确认并保存该指标"
  }
  val builtin=ReportParser.key(alias)
  require(builtin=="未识别"||canonicalMetricKey(builtin)==canonicalMetricKey(metricKey)){"该名称已经对应其他指标：$builtin"}
  val owners=metricCatalogue().filter{(key,names)->canonicalMetricKey(key)!=canonicalMetricKey(metricKey)&&names.any{it.equals(alias,true)}}.keys
  require(owners.isEmpty()){"该名称已属于其他指标：${owners.joinToString("、")}"}
  val current=metricAliases().toMutableMap()
  require(current.none{(otherName,otherKey)->
   otherName.equals(alias,true)&&otherName!=alias&&canonicalMetricKey(otherKey)!=metricKey
  }){"同名别名已经对应其他指标"}
  require(preferredMetricNames().none{(key,name)->name.equals(alias,true)&&canonicalMetricKey(key)!=metricKey}){
   "该名称已经是其他指标的正式名称"
  }
  current[alias]=metricKey
  write("metric_aliases",JSONArray().apply{current.toSortedMap().forEach{(name,key)->
   put(JSONObject().put("alias",name).put("metricKey",key))
  }})
 }
 @Synchronized fun deleteMetricAlias(alias:String){
  val current=metricAliases().toMutableMap()
  current.remove(alias.trim())
  write("metric_aliases",JSONArray().apply{current.toSortedMap().forEach{(name,key)->
   put(JSONObject().put("alias",name).put("metricKey",key))
  }})
 }
 /** Preferred Chinese display names are presentation-only; source report names stay intact. */
 @Synchronized fun preferredMetricNames():Map<String,String>{
  val data=read("metric_preferred_names")
  return (0 until data.length()).mapNotNull { index ->
   val row=data.optJSONObject(index) ?: return@mapNotNull null
   val key=row.optString("metricKey").trim()
   val name=row.optString("name").trim()
   if(key.isBlank()||name.isBlank())null else key to name
  }.toMap()
 }
 @Synchronized fun savePreferredMetricName(metricKey:String,input:String){
  val key=canonicalMetricKey(metricKey.trim())
  val name=input.trim()
  require(key in metricCatalogue()){"指标不存在"}
  require(name.isNotBlank()&&name.any{it.code in 0x3400..0x9FFF}){"正式名称必须包含中文"}
  require(!name.contains('\n')){"名称不能包含换行"}
  val builtin=ReportParser.key(name)
  require(builtin=="未识别"||canonicalMetricKey(builtin)==key){"该名称已经属于其他指标"}
  val catalogueOwners=metricCatalogue().filter{(other,names)->
   canonicalMetricKey(other)!=key && names.any{it.equals(name,true)}
  }.keys
  require(catalogueOwners.isEmpty()){"该名称已用于其他指标：${catalogueOwners.joinToString("、")}"}
  val conflictingAliases=metricAliases().filter{(alias,owner)->
   alias.equals(name,true)&&canonicalMetricKey(owner)!=key
  }
  require(conflictingAliases.isEmpty()){"该名称已被其他指标用作别名"}
  val current=preferredMetricNames().toMutableMap()
  require(current.none{(other,existing)->canonicalMetricKey(other)!=key&&existing.equals(name,true)}){"该名称已经被其他指标使用"}
  current[key]=name
  write("metric_preferred_names",JSONArray().apply{current.toSortedMap().forEach{(id,label)->
   put(JSONObject().put("metricKey",id).put("name",label))
  }})
 }
 @Synchronized fun deletePreferredMetricName(metricKey:String){
  val current=preferredMetricNames().toMutableMap()
  current.remove(canonicalMetricKey(metricKey))
  write("metric_preferred_names",JSONArray().apply{current.toSortedMap().forEach{(id,label)->
   put(JSONObject().put("metricKey",id).put("name",label))
  }})
 }
 fun preferredMetricName(metricKey:String):String?=preferredMetricNames()[canonicalMetricKey(metricKey)]
 /** Only exact aliases are resolved; never fuzzy-merge two different lab identities. */
 /**
  * Resolve from code/name first. Unit is a secondary tie-breaker only:
  * never identify a metric by unit alone, and never reject an otherwise
  * unambiguous name because its unit differs or is convertible.
  */
 fun resolveMetricKey(label:String,unit:String=""):String {
  val clean=label.trim().replace('：',':').replace('／','/')
  if(clean.isBlank())return "未识别"
  sourceDistributionWidthIdentity(clean,unit)?.let{return it}
  // New-report recognition must not learn identities from legacy report raw names.
  // Those names remain available in metricCatalogue() for history/presentation only.
  val catalogue=recognitionMetricCatalogue()
  val preferred=preferredMetricNames()
  val userAliases=metricAliases()
  fun namesMatch(name:String):Boolean =
   name.equals(clean,true) || (clean.length>name.length && clean.startsWith(name,true) &&
    clean[name.length] in " \t（(")
  // A single authoritative exact name/alias mapping wins. Conflicting owners
  // are unresolved; an arbitrary collection iteration order must not decide.
  val exact=(preferred.filterValues{it.equals(clean,true)}.keys+
   userAliases.filterKeys{it.equals(clean,true)}.values+
   catalogue.filterValues{names->names.any{it.equals(clean,true)}}.keys)
   .map(::canonicalMetricKey).distinct()
  if(exact.size==1)return exact.single()
  if(exact.size>1)return "未识别"
  val direct=ReportParser.key(clean)
  if(direct!="未识别")return canonicalMetricKey(direct)
  val candidates=catalogue.filterValues{names->names.any(::namesMatch)}.keys
   .map(::canonicalMetricKey).distinct()
  if(candidates.size==1)return candidates.single()
  if(candidates.isEmpty()||unit.isBlank())return "未识别"
  val matching=candidates.filter{key->
   val knownUnits=rememberedUnits(key)+listOfNotNull(UnifiedMetricStandard.definition(key)?.unit)
   knownUnits.any{known->
    val a=UnitNormalizer.normalize(key,1.0,unit)
    val b=UnitNormalizer.normalize(key,1.0,known)
    a.second.equals(b.second,true) && a.first!=null&&b.first!=null
   }
  }
  return matching.singleOrNull()?:"未识别"
 }
 /** Apply the user dictionary to parsed rows without replacing source text.
  * A complete explicit code is strong evidence, but it must agree with an
  * independently recognizable Chinese measurement identity. Conflicts stay
  * reviewable and preserve the explicit code in the pending key/raw line. */
 fun reconcileParsedMetrics(rows:List<ParsedLabResult>,reportType:String=""):List<ParsedLabResult> =
  rows.map{row->
   if(row.metricKey.isNotBlank() && row.metricKey!="未识别" &&
      !row.metricKey.startsWith("待核对:")){
    val explicit=canonicalMetricKey(row.metricKey)
    val named=SpecimenNames.resolve(row.displayName,reportType)?:resolveMetricKey(row.displayName,row.unit)
    val reconciled=reconciledMetricIdentity(explicit,named)
    if(reconciled!=explicit)row.copy(metricKey=reconciled)
    else row
   }else{
    val byName=resolveMetricKey(row.displayName,row.unit)
    val key=if(byName!="未识别")byName else resolveMetricKey(row.rawLine,row.unit)
    if(key=="未识别")row else row.copy(metricKey=key)
   }
  }
 fun metricAliasNames(metricKey:String):List<String> =
  metricAliases().filterValues{canonicalMetricKey(it)==canonicalMetricKey(metricKey)}.keys.sorted()
 /** User-confirmed identities without a recognized medical code. UUIDs are
  * persistent; never derive identity from a mutable display name. */
 data class CustomMetric(val key:String,val name:String,val types:Set<String>)
 private fun customMetrics():List<CustomMetric> = rows(read("custom_metrics")){o->
  CustomMetric(o.getString("key"),o.getString("name"),
   (0 until o.optJSONArray("types").let{it?.length()?:0}).mapNotNull{i->
    o.optJSONArray("types")?.optString(i)?.takeIf{it.isNotBlank()}
   }.toSet())
 }
 @Synchronized fun registerCustomMetric(nameInput:String,reportType:String):String {
  val name=nameInput.trim()
  require(name.isNotBlank()&&name.any{it.code in 0x3400..0x9FFF}){"请输入确认后的中文指标名"}
  require(reportType.isNotBlank()){"检查类型不能为空"}
  val existing=resolveMetricKey(name)
  if(existing!="未识别")return existing
  val values=customMetrics()
  val same=values.filter{it.name.equals(name,true)}
  require(same.size<=1){"存在重名指标，请先核对身份"}
  val candidate=same.singleOrNull()
  val chosen=candidate?:CustomMetric("CUSTOM-"+java.util.UUID.randomUUID().toString().uppercase(),name,emptySet())
  val updated=values.filterNot{it.key==chosen.key}+chosen.copy(types=chosen.types+reportType)
  write("custom_metrics",JSONArray().apply{updated.forEach{item->
   put(JSONObject().put("key",item.key).put("name",item.name)
    .put("types",JSONArray().apply{item.types.sorted().forEach{put(it)}}))
  }})
  return chosen.key
 }
 /** The same canonical identity can belong to several examination types. */
 fun metricPoolForType(reportType:String):List<UnifiedMetricEntry> {
  val observed=reports().filter{it.reportType==reportType}.flatMap{it.results.map{r->canonicalMetricKey(r.metricKey)}}
  val custom=customMetrics().filter{reportType in it.types}.map{it.key}
  val builtIn=UnifiedMetricStandard.keysForType(reportType)
  val keys=(observed+custom+builtIn).toSet()
  return unifiedMetricPool().filter{it.key in keys}
 }
 /** Recognition-only dictionary for newly imported or edited reports.
  * Historical source names must never silently become aliases for future reports.
  * This reads dictionary preferences, never modifies previous report snapshots.
  */
 private fun recognitionMetricCatalogue():Map<String,Set<String>>{
  val names=linkedMapOf<String,MutableSet<String>>()
  ReportParser.builtInMetricKeys().forEach{key->
   names.getOrPut(canonicalMetricKey(key)){linkedSetOf()}.addAll(ReportParser.builtInAliasNames(key))
  }
  UnifiedMetricStandard.keys().forEach{key->
   val item=UnifiedMetricStandard.definition(key)?:return@forEach
   names.getOrPut(canonicalMetricKey(key)){linkedSetOf()}.apply{add(item.name);addAll(item.aliases)}
  }
  customMetrics().forEach{item->names.getOrPut(item.key){linkedSetOf()}.add(item.name)}
  preferredMetricNames().forEach{(key,name)->
   names.getOrPut(canonicalMetricKey(key)){linkedSetOf()}.add(name)
  }
  metricAliases().forEach{(alias,key)->
   names.getOrPut(canonicalMetricKey(key)){linkedSetOf()}.add(alias)
  }
  return names.mapValues{(_,values)->values.filter{it.isNotBlank()}.toSet()}
 }
 /** Unified metric catalogue: built-in and user-defined identities, together with
  * observed historical report names. Legacy hospital templates are not a source. */
 fun metricCatalogue():Map<String,Set<String>>{
  val names=linkedMapOf<String,MutableSet<String>>()
  ReportParser.builtInMetricKeys().forEach{key->
   names.getOrPut(canonicalMetricKey(key)){linkedSetOf()}.addAll(ReportParser.builtInAliasNames(key))
  }
  UnifiedMetricStandard.keys().forEach{key->
   val item=UnifiedMetricStandard.definition(key)?:return@forEach
   names.getOrPut(key){linkedSetOf()}.apply{add(item.name);addAll(item.aliases)}
  }
  customMetrics().forEach{item->names.getOrPut(item.key){linkedSetOf()}.add(item.name)}
  reports().flatMap{it.results}.forEach{result->
   names.getOrPut(canonicalMetricKey(result.metricKey)){linkedSetOf()}.add(result.rawName)
  }
  preferredMetricNames().forEach{(key,name)->
   names.getOrPut(canonicalMetricKey(key)){linkedSetOf()}.add(name)
  }
  metricAliases().forEach{(alias,key)->
   names.getOrPut(canonicalMetricKey(key)){linkedSetOf()}.add(alias)
  }
  return names.mapValues{(_,values)->values.filter{it.isNotBlank()}.toSet()}
 }

 /**
  * One metric identity with its presentation name, recognition-only aliases,
  * preferred unit, and all observed ranges. Source hospital is report metadata,
  * not a key of the metric pool.
  */
 data class UnifiedMetricEntry(
  val key:String,val displayName:String,val aliases:Set<String>,
  val commonUnit:String,val referenceRanges:List<ObservedReferenceRange>,val linkedLegacyKeys:Set<String> = emptySet()
 )
 fun unifiedMetricPool():List<UnifiedMetricEntry> {
  val catalogue=metricCatalogue()
  val customNames=customMetrics().associate{it.key to it.name}
  val preferred=preferredMetricNames()
  return catalogue.keys.sorted().map{key->
   val names=catalogue[key].orEmpty()
   val definition=UnifiedMetricStandard.definition(key)
   val name=preferred[key]?.takeIf{it.isNotBlank()}
    ?:definition?.name
    ?:customNames[key]
    ?:names.filter{candidate->candidate.any{it.code in 0x3400..0x9FFF}}
      .sortedWith(compareByDescending<String>{it.length}.thenBy{it}).firstOrNull()
    ?:key
   val linked=reportIdentityLinks().filter{it.target==key&&it.revokedAt==null}.map{it.source}.toSet()
   val savedRanges=referenceRangeHistory(key)+linked.flatMap{source->
    referenceRangeHistory(source).map{it.copy(metricKey=key)}
   }
   val persistedRanges=standardRanges(key).map{range->
    ObservedReferenceRange(key,range.unit,range.low,range.high,0L,0L,0)
   }
   // Multiple ranges per identity, independent of hospital. The original
   // report (including hospital and exact test-time range) remains unchanged.
   val ranges=(savedRanges+persistedRanges).distinctBy{
    listOf(it.metricKey,it.unit,it.lower?.toString(),it.upper?.toString())
   }
   val units=rememberedUnits(key)
   val common=definition?.unit?.takeIf{it.isNotBlank()}
    ?:units.firstNotNullOfOrNull{source->
     val normalized=UnitNormalizer.normalize(key,1.0,source).second
     normalized.takeIf{it!=source}
    } ?:units.firstOrNull().orEmpty()
   UnifiedMetricEntry(key,name,names.filter{it!=name}.toSet(),common,ranges,linked)
  }
 }
 fun rememberedUnits(key:String):List<String>{
  val canonical=key.takeIf{it.isNotBlank()}
  val reportUnits=reports().flatMap{it.results}.filter{canonical==null||it.metricKey==canonical}.map{it.unitAtTest}
  return reportUnits.map{it.trim()}.filter{it.isNotBlank()}.distinct()
 }
 fun rememberedHospitals():List<String> =
  (reports().map{it.hospitalKey}+entries().map{it.hospital})
   .map{it.trim()}.filter{it.isNotBlank()}.distinct().sorted()
 @Synchronized fun updateResultText(reportId:String,resultId:String,text:String){
  require(LabResultValues.valid(text)){"检测结果无效"}
  val r=reports().first{it.id==reportId}
  require(r.results.any{it.id==resultId}){"找不到指标"}
  saveReport(r.copy(results=r.results.map{if(it.id==resultId)it.withEditedResult(text) else it}))
 }
 @Synchronized fun updateValue(reportId:String,resultId:String,value:Double,displayText:String?=null){require(value.isFinite());val r=reports().first{it.id==reportId};saveReport(r.copy(results=r.results.map{if(it.id==resultId)it.withEditedValue(value,displayText)else it}))}
 private fun storedTemplates()=rows(read("templates"),::templateFromJson)
 @Synchronized fun templates()=storedTemplates().filter{it.confirmed}.groupBy{Triple(it.hospitalKey,it.reportType,it.systemKey)}.values.mapNotNull{it.maxByOrNull(HospitalLabTemplate::version)}.sortedWith(compareBy<HospitalLabTemplate>{it.hospitalKey}.thenBy{it.reportType})
 fun latestTemplate(h:String,t:String,system:String="")=storedTemplates().filter{it.hospitalKey==h.trim()&&it.reportType==t.trim()&&it.systemKey==system.trim()&&it.confirmed}.maxByOrNull{it.version}
 @Synchronized fun confirmTemplate(h:String,t:String,items:List<ParsedLabResult>,system:String="",newVersion:Boolean=false):HospitalLabTemplate {
  require(ReportParser.valid(items));require(h.isNotBlank()&&t.isNotBlank())
  require(items.all{it.metricKey.isNotBlank() && it.metricKey!="未识别" && !it.metricKey.startsWith("待核对:")}&&items.map{canonicalMetricKey(it.metricKey)}.distinct().size==items.size){"模板项目 ID 为空或重复"}
  val old=latestTemplate(h,t,system)
  if(old!=null && !newVersion)return old
  val version=if(old==null)1 else old.version+1
  val template=HospitalLabTemplate(h.trim(),t.trim(),version,true,items.map{LabFieldTemplate(it.metricKey,it.displayName,displayLabUnit(it.unit),it.referenceLow,it.referenceHigh)},system.trim())
  // One current template per hospital/panel/system. Re-confirming replaces only that
  // template record; historical reports keep their own unit/range/name snapshots.
  // Append a new template version; never delete previously confirmed versions.
  // Existing reports retain their original templateVersion and result snapshots.
  val keep=storedTemplates().filterNot{it.hospitalKey==template.hospitalKey&&it.reportType==template.reportType&&it.systemKey==template.systemKey&&it.version==template.version}
  write("templates",JSONArray().apply{keep.forEach{put(templateToJson(it))};put(templateToJson(template))});return template
 }
 @Synchronized fun saveTemplateFields(source:HospitalLabTemplate,fields:List<LabFieldTemplate>):HospitalLabTemplate {
  require(fields.isNotEmpty()&&fields.all{it.metricKey.isNotBlank() && it.metricKey!="未识别" && !it.metricKey.startsWith("待核对:")}&&fields.map{canonicalMetricKey(it.metricKey)}.distinct().size==fields.size){"模板项目 ID 为空或重复"}
  val old=latestTemplate(source.hospitalKey,source.reportType,source.systemKey)?:source
  require(old.version==source.version){"医院模板已更新，请重新打开最新版本后再修改"}
  val template=old.copy(version=old.version+1,confirmed=true,fields=fields.map{it.copy(trendMeaning=it.trendMeaning.trim().ifBlank{metricPurpose(it.metricKey).orEmpty()})})
  // Append a new template version; never delete previously confirmed versions.
  // Existing reports retain their original templateVersion and result snapshots.
  val keep=storedTemplates().filterNot{it.hospitalKey==template.hospitalKey&&it.reportType==template.reportType&&it.systemKey==template.systemKey&&it.version==template.version}
  write("templates",JSONArray().apply{keep.forEach{put(templateToJson(it))};put(templateToJson(template))});return template
 }
 /** Without a confirmed template this remains a conservative fill helper. */
 fun applyTemplate(items:List<ParsedLabResult>,template:HospitalLabTemplate?)=applyRememberedTemplate(items,template)
 fun buildReportFromPool(h:String,t:String,date:Long,uris:List<String>,items:List<ParsedLabResult>,raw:String="",system:String=""):LabReport {
  require(ReportParser.valid(items));val id=newId()
  return LabReport(id,h.trim(),t.trim(),date,null,
   uris.mapIndexed{i,u->ReportImage(u,i,System.currentTimeMillis())},
   items.map{p->val unit=displayLabUnit(p.unit);val n=UnitNormalizer.normalize(p.metricKey,p.value,unit)
    LabResult(newId(),id,h.trim(),t.trim(),null,p.metricKey,p.displayName,p.value,unit,
     p.referenceLow,p.referenceHigh,date,false,p.textValue,p.comparator,p.rawLine,n.first,n.second,p.referenceRaw,p.reportFlag)
   },raw,system.trim())
 }
 fun buildReport(h:String,t:String,date:Long,uris:List<String>,items:List<ParsedLabResult>,template:HospitalLabTemplate,raw:String="",system:String=""):LabReport {
  require(ReportParser.valid(items));val id=newId()
  // The editor has applied the selected version. Snapshot exactly what the user confirmed.
  return LabReport(id,h.trim(),t.trim(),date,template.version,uris.mapIndexed{i,u->ReportImage(u,i,System.currentTimeMillis())},items.map{p->val unit=displayLabUnit(p.unit);val n=UnitNormalizer.normalize(p.metricKey,p.value,unit);LabResult(newId(),id,h.trim(),t.trim(),template.version,p.metricKey,p.displayName,p.value,unit,p.referenceLow,p.referenceHigh,date,false,p.textValue,p.comparator,p.rawLine,n.first,n.second,p.referenceRaw,p.reportFlag)},raw,system.trim())
 }
 @Synchronized fun entries()=rows(read("entries"),::entryFromJson).sortedByDescending{it.occurredAtEpochMillis}
 @Synchronized fun saveEntry(e:HealthEntry){require(e.title.isNotBlank());write("entries",JSONArray().apply{(entries().filterNot{it.id==e.id}+e).forEach{put(entryToJson(it))}})}
 @Synchronized fun deleteEntry(id:String){write("entries",JSONArray().apply{entries().filterNot{it.id==id}.forEach{put(entryToJson(it))}})}
 @Synchronized fun weightRecords()=rows(read("weights")){o->WeightRecord(o.getString("id"),o.getLong("date"),o.getDouble("kg"))}.sortedBy{it.measuredAtEpochMillis}
 @Synchronized fun saveWeight(record:WeightRecord){require(record.kilograms>0&&record.kilograms.isFinite());write("weights",JSONArray().apply{(weightRecords().filterNot{it.id==record.id}+record).sortedBy{it.measuredAtEpochMillis}.forEach{put(JSONObject().put("id",it.id).put("date",it.measuredAtEpochMillis).put("kg",it.kilograms))}})}
 @Synchronized fun deleteWeight(id:String){write("weights",JSONArray().apply{weightRecords().filterNot{it.id==id}.forEach{put(JSONObject().put("id",it.id).put("date",it.measuredAtEpochMillis).put("kg",it.kilograms))}})}
 @Synchronized fun courseRecords()=rows(read("course_records"),::courseRecordFromJson).sortedWith(compareByDescending<CourseRecord>{it.date}.thenByDescending{it.updatedAt})
 @Synchronized fun saveCourseRecord(record:CourseRecord){
  require(record.title.isNotBlank())
  val previous=courseRecords().firstOrNull{it.id==record.id}
  val saved=record.copy(createdAt=previous?.createdAt?:record.createdAt,updatedAt=System.currentTimeMillis())
  write("course_records",JSONArray().apply{(courseRecords().filterNot{it.id==saved.id}+saved).forEach{put(courseRecordToJson(it))}})
 }
 @Synchronized fun deleteCourseRecord(id:String){write("course_records",JSONArray().apply{courseRecords().filterNot{it.id==id}.forEach{put(courseRecordToJson(it))}})}
 @Synchronized fun ensureCourseRecordsThroughToday():Int{
  val zone=java.time.ZoneId.systemDefault();val today=java.time.LocalDate.now(zone);val current=courseRecords()
  if(current.isEmpty()){
   val placeholder=CourseRecord(date=today.atStartOfDay(zone).toInstant().toEpochMilli(),title="")
   write("course_records",JSONArray().apply{put(courseRecordToJson(placeholder))})
   return 1
  }
  val existingDays=current.map{java.time.Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate()}.toSet()
  val historicalDays=existingDays.filter{!it.isAfter(today)}
  val start=historicalDays.minOrNull()?:today
  val span=java.time.temporal.ChronoUnit.DAYS.between(start,today)
  require(span in 0..3660){"病程日期跨度异常，请检查最早病程日期"}
  val missing=generateSequence(start){day->day.plusDays(1).takeIf{!it.isAfter(today)}}.filter{it !in existingDays}.map{day->CourseRecord(date=day.atStartOfDay(zone).toInstant().toEpochMilli(),title="")}.toList()
  if(missing.isNotEmpty())write("course_records",JSONArray().apply{(current+missing).forEach{put(courseRecordToJson(it))}})
  return missing.size
 }
 fun isPrimary(k:String)=if(prefs.contains("primary:$k"))prefs.getBoolean("primary:$k",false)else k in ReportParser.primaryKeys
 fun setPrimary(k:String,value:Boolean){check(prefs.edit().putBoolean("primary:$k",value).commit())}
 fun ownImage(uri:Uri):String {
  if(uri.scheme=="file" && uri.path?.startsWith(File(context.filesDir,"sources").path)==true)return uri.toString()
  val dir=File(context.filesDir,"sources").apply{mkdirs()};val dest=File(dir,newId()+".image");val temp=File(dir,dest.name+".tmp")
  try { context.contentResolver.openInputStream(uri).use{input->requireNotNull(input){"原图无法读取"};temp.outputStream().use{out->input.copyTo(out)}};check(temp.length()>0);check(temp.renameTo(dest));return Uri.fromFile(dest).toString() }finally{temp.delete()}
 }
 /** Deterministic, editable clipboard format. Never fabricate unmeasured fields. */
 fun exportReportAsPastedText(report:LabReport):String = buildString {
  append(report.hospitalKey).append('\n')
  append(report.reportType).append('\n')
  append(dateText(report.testedAtEpochMillis))
  report.results.forEach{row->
   if(row.value==null){
    append('\n').append(listOf(canonicalMetricKey(row.metricKey),preferredMetricName(row.metricKey)?:UnifiedMetricStandard.definition(row.metricKey)?.name?:row.rawName,row.textValue,row.unitAtTest.ifBlank{"-"},row.referenceRaw).joinToString("\t"))
    return@forEach
   }
   append('\n').append(canonicalMetricKey(row.metricKey)).append(' ')
   append(preferredMetricName(row.metricKey)?:UnifiedMetricStandard.definition(row.metricKey)?.name?:row.rawName)
   append(' ').append(row.textValue).append(' ')
   append(row.unitAtTest.ifBlank{"-"})
   if(row.reportFlag.isNotBlank())append(' ').append(row.reportFlag)
   if(row.referenceRaw.isNotBlank()){
    append(' ').append(row.referenceRaw)
   }else if(row.referenceLowAtTest!=null||row.referenceHighAtTest!=null){
    append(' ').append(row.referenceLowAtTest?.toString()?:"")
    append('-').append(row.referenceHighAtTest?.toString()?:"")
   }
  }
 }
 /** Portable dictionary settings; never contains reports, patient names or photos. */
 fun exportMetricDictionary():String=JSONObject().put("schema",1)
  .put("aliases",read("metric_aliases"))
  .put("preferredNames",read("metric_preferred_names"))
  .put("customMetrics",read("custom_metrics"))
  .toString()
 /** Validate the entire user dictionary before any write, rejecting collisions. */
 @Synchronized fun importMetricDictionary(content:String){
  val input=JSONObject(content)
  require(input.optInt("schema")==1){"词典配置版本不支持"}
  val aliases=input.optJSONArray("aliases")?:JSONArray()
  val preferred=input.optJSONArray("preferredNames")?:JSONArray()
  val custom=input.optJSONArray("customMetrics")?:JSONArray()
  val keys=(metricCatalogue().keys+ (0 until custom.length()).map{custom.getJSONObject(it).getString("key")}).toSet()
  val ownership=mutableMapOf<String,String>()
  fun register(name:String,key:String){
   val label=name.trim().lowercase()
   require(label.isNotBlank()&&key in keys){"词典包含无效项目名称或代码"}
   val previous=ownership.putIfAbsent(label,key)
   require(previous==null||previous==key){"词典名称对应多个项目：$name"}
  }
  (0 until aliases.length()).forEach{val x=aliases.getJSONObject(it);register(x.getString("alias"),canonicalMetricKey(x.getString("metricKey")))}
  (0 until preferred.length()).forEach{val x=preferred.getJSONObject(it);register(x.getString("name"),canonicalMetricKey(x.getString("metricKey")))}
  (0 until custom.length()).forEach{val x=custom.getJSONObject(it);register(x.getString("name"),x.getString("key"))}
  // Atomic preference transaction: no reports/templates/course records are touched.
  check(prefs.edit().putString("metric_aliases",aliases.toString())
   .putString("metric_preferred_names",preferred.toString())
   .putString("custom_metrics",custom.toString()).commit()){"词典保存失败"}
 }
 internal fun hasRestoreProtectedConfiguration():Boolean = listOf(
  "templates","custom_metrics","metric_aliases","metric_preferred_names","metric_standard_ranges",
  "report_identity_links","report_replacement_archive","trend_identity_mappings"
 ).any{read(it).length()>0}
 fun exportBackup():String=JSONObject().put("schema",3).put("bloodGroupProfile",bloodGroupProfile().toJson()).put("patientProfile",JSONObject().put("name",patientProfile().name).put("birthDate",patientProfile().birthDate).put("sex",patientProfile().sex).put("note",patientProfile().note).put("bloodType",patientProfile().bloodType)).put("reports",read("reports")).put("reportReplacementArchive",read("report_replacement_archive")).put("templates",read("templates")).put("entries",read("entries")).put("weights",read("weights")).put("courseRecords",read("course_records")).put("standardRanges",read("metric_standard_ranges")).put("metricAliases",read("metric_aliases")).put("customMetrics",read("custom_metrics")).put("reportIdentityLinks",read("report_identity_links")).put("primary",JSONObject().apply{prefs.all.filterKeys{it.startsWith("primary:")}.forEach{(k,v)->put(k,v)}}).toString()
 private fun reportToJson(r:LabReport)=JSONObject().put("id",r.id).put("hospital",r.hospitalKey).put("type",r.reportType).put("date",r.testedAtEpochMillis).put("tv",r.templateVersion).put("ocr",r.rawOcr).put("system",r.systemKey).put("images",JSONArray().apply{r.sourceImages.forEach{put(JSONObject().put("uri",it.uri).put("page",it.pageIndex).put("at",it.importedAtEpochMillis))}}).put("results",JSONArray().apply{r.results.forEach{put(JSONObject().put("id",it.id).put("key",it.metricKey).put("raw",it.rawName).put("value",it.value?:JSONObject.NULL).put("text",it.textValue).put("cmp",it.comparator).put("line",it.rawLine).put("referenceRaw",it.referenceRaw).put("reportFlag",it.reportFlag).put("unit",it.unitAtTest).put("low",it.referenceLowAtTest?:JSONObject.NULL).put("high",it.referenceHighAtTest?:JSONObject.NULL).put("edited",it.editedByUser).put("normalizedValue",it.normalizedValue?:JSONObject.NULL).put("normalizedUnit",it.normalizedUnit))}})
 private fun reportFromJson(o:JSONObject):LabReport {
  val id=o.getString("id");val h=o.getString("hospital");val t=o.getString("type");val date=o.getLong("date");val version=o.intOrNull("tv")
  return LabReport(id,h,t,date,version,rows(o.getJSONArray("images")){x->ReportImage(x.getString("uri"),x.getInt("page"),x.getLong("at"))},rows(o.getJSONArray("results")){x->
   val key=x.getString("key");val value=x.doubleOrNull("value");val unit=x.getString("unit");val normalized=UnitNormalizer.normalize(key,value,unit)
   LabResult(x.getString("id"),id,h,t,version,key,x.getString("raw"),value,unit,x.doubleOrNull("low"),x.doubleOrNull("high"),date,x.optBoolean("edited"),x.optString("text",value?.toString().orEmpty()),x.optString("cmp"),x.optString("line"),normalized.first,normalized.second,x.optString("referenceRaw"),x.optString("reportFlag"))
  },o.optString("ocr"),o.optString("system"))
 }
 private fun replacementToJson(r:ReportReplacement)=JSONObject().put("id",r.id).put("old",reportToJson(r.oldReport)).put("new",reportToJson(r.newReport)).put("at",r.replacedAt).put("restored",r.restoredAt?:JSONObject.NULL)
 private fun templateToJson(t:HospitalLabTemplate)=JSONObject().put("hospital",t.hospitalKey).put("type",t.reportType).put("system",t.systemKey).put("version",t.version).put("confirmed",t.confirmed).put("fields",JSONArray().apply{t.fields.forEach{put(JSONObject().put("key",it.metricKey).put("name",it.displayName).put("unit",it.unit).put("low",it.referenceLow?:JSONObject.NULL).put("high",it.referenceHigh?:JSONObject.NULL).put("trendMeaning",it.trendMeaning))}})
 private fun templateFromJson(o:JSONObject)=HospitalLabTemplate(o.getString("hospital"),o.getString("type"),o.getInt("version"),o.getBoolean("confirmed"),rows(o.getJSONArray("fields")){x->LabFieldTemplate(x.getString("key"),x.getString("name"),displayLabUnit(x.getString("unit")),x.doubleOrNull("low"),x.doubleOrNull("high"),x.optString("trendMeaning").ifBlank{metricPurpose(x.getString("key")).orEmpty()})},o.optString("system"))
 private fun entryToJson(e:HealthEntry)=JSONObject().put("id",e.id).put("kind",e.kind.name).put("title",e.title).put("date",e.occurredAtEpochMillis).put("note",e.note).put("hospital",e.hospital).put("category",e.category).put("severity",e.severity).put("frequency",e.frequency).put("duration",e.duration).put("dose",e.dose).put("route",e.route).put("end",e.endAtEpochMillis?:JSONObject.NULL).put("images",JSONArray(e.images))
 private fun entryFromJson(o:JSONObject)=HealthEntry(o.getString("id"),EntryKind.valueOf(o.getString("kind")),o.getString("title"),o.getLong("date"),o.optString("note"),o.optString("hospital"),o.optString("category"),o.optInt("severity"),o.optString("frequency"),o.optString("duration"),o.optString("dose"),o.optString("route"),if(o.isNull("end"))null else o.getLong("end"),o.optJSONArray("images")?.let{a->(0 until a.length()).map{a.getString(it)}}?:emptyList())
 private fun courseRecordToJson(r:CourseRecord)=JSONObject().put("id",r.id).put("date",r.date).put("phase",r.phase).put("title",r.title).put("symptomText",r.symptomText).put("systolic",r.systolic?:JSONObject.NULL).put("diastolic",r.diastolic?:JSONObject.NULL).put("pressureTime",r.pressureTime).put("pulse",r.pulse?:JSONObject.NULL).put("additionalPressures",JSONArray().apply{r.additionalPressures.forEach{put(JSONObject().put("time",it.time).put("systolic",it.systolic?:JSONObject.NULL).put("diastolic",it.diastolic?:JSONObject.NULL).put("pulse",it.pulse?:JSONObject.NULL))}}).put("symptomImages",JSONArray(r.symptomImages)).put("checkText",r.checkText).put("checkImages",JSONArray(r.checkImages)).put("medicineText",r.medicineText).put("medicineImages",JSONArray(r.medicineImages)).put("noteText",r.noteText).put("noteImages",JSONArray(r.noteImages)).put("createdAt",r.createdAt).put("updatedAt",r.updatedAt)
 private fun courseRecordFromJson(o:JSONObject)=CourseRecord(
  id=o.getString("id"),
  date=o.getLong("date"),
  phase=o.optString("phase","观察"),
  title=o.getString("title"),
  symptomText=o.optString("symptomText"),
  systolic=if(o.isNull("systolic"))null else o.optInt("systolic").takeIf{it>0},
  diastolic=if(o.isNull("diastolic"))null else o.optInt("diastolic").takeIf{it>0},
  pressureTime=o.optString("pressureTime"),
  pulse=if(o.isNull("pulse"))null else o.optInt("pulse").takeIf{it>0},
  additionalPressures=o.optJSONArray("additionalPressures")?.let{a->(0 until a.length()).map{idx->val x=a.getJSONObject(idx);BloodPressureReading(x.optString("time"),if(x.isNull("systolic"))null else x.optInt("systolic").takeIf{it>0},if(x.isNull("diastolic"))null else x.optInt("diastolic").takeIf{it>0},if(x.isNull("pulse"))null else x.optInt("pulse").takeIf{it>0})}}?:emptyList(),
  symptomImages=o.optJSONArray("symptomImages").stringList(),
  checkText=o.optString("checkText"),
  checkImages=o.optJSONArray("checkImages").stringList(),
  medicineText=o.optString("medicineText"),
  medicineImages=o.optJSONArray("medicineImages").stringList(),
  noteText=o.optString("noteText"),
  noteImages=o.optJSONArray("noteImages").stringList(),
  createdAt=o.optLong("createdAt",o.getLong("date")),
  updatedAt=o.optLong("updatedAt",o.getLong("date"))
 )
}
/** A confirmed template fills fields omitted by OCR, while explicit report data always wins. */
fun applyRememberedTemplate(items:List<ParsedLabResult>,template:HospitalLabTemplate?)=items.map{p->
 val field=template?.fields?.firstOrNull{it.metricKey==p.metricKey}
 if(field==null)p else p.copy(
  unit=sanitizeLabUnit(p.unit).ifBlank{sanitizeLabUnit(field.unit)},
  referenceLow=p.referenceLow?:field.referenceLow,
  referenceHigh=p.referenceHigh?:field.referenceHigh
 )
}
internal fun xiapuBiochemistryTemplate()=HospitalLabTemplate("霞浦县中医院","生化",1,true,listOf(
 LabFieldTemplate("TP","TP 总蛋白","g/L",65.0,85.0),
 LabFieldTemplate("ALB","ALB 白蛋白","g/L",40.0,55.0),
 LabFieldTemplate("GLOB","GLOB 球蛋白","g/L",20.0,40.0),
 LabFieldTemplate("A/G","A/G 白球比","",1.5,2.5),
 LabFieldTemplate("TBIL","TBIL 总胆红素","umol/L",3.4,20.6),
 LabFieldTemplate("DBIL","DBIL 直接胆红素","umol/L",null,6.84),
 LabFieldTemplate("IBIL","IBIL 间接胆红素","umol/L",2.0,15.22),
 LabFieldTemplate("ALT","ALT 谷丙转氨酶","U/L",7.0,40.0),
 LabFieldTemplate("AST","AST 谷草转氨酶","U/L",13.0,35.0),
 LabFieldTemplate("GGT","GGT 谷氨酰转肽酶","U/L",7.0,45.0),
 LabFieldTemplate("AST/ALT","AST/ALT 谷草/谷丙","",null,null),
 LabFieldTemplate("ALP","ALP 碱性磷酸酶","U/L",50.0,130.0),
 LabFieldTemplate("CHE","CHE 胆碱酯酶","U/L",5000.0,null),
 LabFieldTemplate("TBA","TBA 总胆汁酸","umol/L",null,10.0),
 LabFieldTemplate("PA","PA 前白蛋白","mg/L",170.0,420.0),
 LabFieldTemplate("UREA","UREA 尿素","mmol/L",1.43,7.14),
 LabFieldTemplate("CREA","CREA 肌酐","umol/L",35.0,80.0),
 LabFieldTemplate("UA","UA 尿酸","umol/L",90.0,357.0)
))
internal fun sanitizeLabUnit(unit:String)=unit.trim().takeUnless{it.toDoubleOrNull()!=null}.orEmpty()
internal fun displayLabUnit(unit:String)=sanitizeLabUnit(unit).replace(Regex("^[×xX]\\s*(?=10\\^)"),"")
private fun JSONObject.doubleOrNull(k:String)=if(isNull(k)||!has(k))null else getDouble(k)
private fun JSONObject.intOrNull(k:String)=if(isNull(k)||!has(k))null else getInt(k)
private fun JSONArray?.stringList():List<String> = if(this==null) emptyList() else (0 until length()).map{getString(it)}
