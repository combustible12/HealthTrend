package com.combustible12.healthtrend

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

@Volatile var debugLog:String=""

/** Keep v1 preference names and fields so existing installations migrate in place. */
class HealthStore(private val context:Context) {
 private val prefs=context.getSharedPreferences("healthtrend_store_v1",Context.MODE_PRIVATE)
 init { migrateCurrentTemplates(); migrateAstAltIdentity() }
 private fun read(key:String)=JSONArray(prefs.getString(key,"[]"))
 private fun write(key:String,a:JSONArray){check(prefs.edit().putString(key,a.toString()).putInt("schema",2).commit()){ "记录保存失败，请检查设备存储空间" }}
 private fun <T> rows(a:JSONArray,fn:(JSONObject)->T)= (0 until a.length()).map{fn(a.getJSONObject(it))}
 private fun migrateCurrentTemplates(){
  val raw=runCatching{rows(read("templates"),::templateFromJson)}.getOrDefault(emptyList())
  val current=raw.groupBy{Triple(it.hospitalKey.trim(),it.reportType.trim(),it.systemKey.trim())}.values.mapNotNull{versions->
   versions.withIndex().maxWithOrNull(compareBy<IndexedValue<HospitalLabTemplate>>{it.value.confirmed}
    .thenBy{it.value.version}.thenBy{it.index})?.value
  }.map(::repairXiacuBiochemistryTemplate).toMutableList()
  if(current.none{it.hospitalKey=="霞浦县中医院"&&it.reportType=="生化"&&it.systemKey.isBlank()})current+=xiapuBiochemistryTemplate()
  val changed=raw.size!=current.size || raw.zip(current).any{(a,b)->a!=b}
  if(changed)write("templates",JSONArray().apply{current.forEach{put(templateToJson(it))}})
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
 private fun migrateAstAltIdentity(){
  val raw=read("reports");var changed=false
  for(i in 0 until raw.length()){
   val report=raw.getJSONObject(i);val results=report.optJSONArray("results")?:continue
   val remove=mutableListOf<Int>()
   for(j in 0 until results.length()){
    val row=results.getJSONObject(j)
    if(row.optString("key")!="AST")continue
    val evidence=listOf(row.optString("raw"),row.optString("line")).joinToString(" ")
    // Historical repair is allowed only with explicit AST/ALT code evidence.
    // Never infer identity from the Chinese display name.
    val explicitAstAlt=Regex("[（(]\\s*AST/ALT\\s*[）)]",RegexOption.IGNORE_CASE).containsMatchIn(evidence) ||
     Regex("(?i)(?:^|\\s)AST/ALT(?:\\s|$)").containsMatchIn(evidence)
    if(!explicitAstAlt)continue
    val already=(0 until results.length()).any{k->k!=j&&results.getJSONObject(k).optString("key")=="AST/ALT"}
    if(already){remove+=j;changed=true;continue}
    row.put("key","AST/ALT").put("raw","谷草/谷丙").put("normalizedUnit","");changed=true
   }
   remove.sortedDescending().forEach{results.remove(it)}
  }
  if(changed)write("reports",raw)
 }
 fun patientProfile():PatientProfile { val raw=prefs.getString("patient_profile",null)?:return PatientProfile();return runCatching{val o=JSONObject(raw);PatientProfile(o.optString("name"),o.optString("birthDate"),o.optString("sex"),o.optString("note"))}.getOrDefault(PatientProfile()) }
 @Synchronized fun savePatientProfile(p:PatientProfile){val o=JSONObject().put("name",p.name.trim()).put("birthDate",p.birthDate.trim()).put("sex",p.sex.trim()).put("note",p.note.trim());check(prefs.edit().putString("patient_profile",o.toString()).commit())}
 @Synchronized fun reports():List<LabReport>{
  val reports=rows(read("reports"),::reportFromJson).sortedByDescending{it.testedAtEpochMillis}
  return reports
 }
 @Synchronized fun saveReport(r:LabReport){
  require(r.results.isNotEmpty());require(r.hospitalKey.isNotBlank())
  require(r.results.all{it.metricKey.isNotBlank()&&it.reportId==r.id&&it.hospitalKey==r.hospitalKey&&it.reportType==r.reportType}){"报告项目身份无效"}
  require(r.results.map{it.metricKey}.distinct().size==r.results.size){"报告存在重复项目 ID"}
  latestTemplate(r.hospitalKey,r.reportType,r.systemKey)?.let{template->
   val expected=template.fields.map{it.metricKey}
   val actual=r.results.map{it.metricKey}
   require(expected.size==expected.distinct().size){"当前模板存在重复项目 ID"}
   require(actual.size==actual.distinct().size){"报告存在重复项目 ID"}
   require(actual.all{it in expected}){"报告包含当前模板之外的项目，请重新核对"}
   require(actual==expected.filter{it in actual.toSet()}){"报告项目顺序与当前模板不一致，请重新核对"}
  }
  write("reports",JSONArray().apply{(reports().filterNot{it.id==r.id}+r).forEach{put(reportToJson(it))}})
 }
 @Synchronized fun deleteReport(id:String){write("reports",JSONArray().apply{reports().filterNot{it.id==id}.forEach{put(reportToJson(it))}})}
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
 }

 fun trend(key:String)=reports().flatMap{r->r.results.filter{it.metricKey==key && it.value!=null && it.comparator.isEmpty()}.map{r to it}}.sortedWith(compareBy<Pair<LabReport,LabResult>>{it.first.testedAtEpochMillis}.thenBy{it.second.id})
 fun rememberedUnits(key:String):List<String>{
  val canonical=key.takeIf{it.isNotBlank()}
  val reportUnits=reports().flatMap{it.results}.filter{canonical==null||it.metricKey==canonical}.map{it.unitAtTest}
  val templateUnits=templates().flatMap{it.fields}.filter{canonical==null||it.metricKey==canonical}.map{it.unit}
  return (reportUnits+templateUnits).map{it.trim()}.filter{it.isNotBlank()}.distinct()
 }
 fun rememberedHospitals():List<String> =
  (reports().map{it.hospitalKey}+templates().map{it.hospitalKey}+entries().map{it.hospital})
   .map{it.trim()}.filter{it.isNotBlank()}.distinct().sorted()
 @Synchronized fun updateValue(reportId:String,resultId:String,value:Double,displayText:String?=null){require(value.isFinite());val r=reports().first{it.id==reportId};saveReport(r.copy(results=r.results.map{if(it.id==resultId)it.withEditedValue(value,displayText)else it}))}
 private fun storedTemplates()=rows(read("templates"),::templateFromJson)
 @Synchronized fun templates()=storedTemplates().filter{it.confirmed}.groupBy{Triple(it.hospitalKey,it.reportType,it.systemKey)}.values.mapNotNull{it.maxByOrNull(HospitalLabTemplate::version)}.sortedWith(compareBy<HospitalLabTemplate>{it.hospitalKey}.thenBy{it.reportType})
 fun latestTemplate(h:String,t:String,system:String="")=storedTemplates().filter{it.hospitalKey==h.trim()&&it.reportType==t.trim()&&it.systemKey==system.trim()&&it.confirmed}.maxByOrNull{it.version}
 @Synchronized fun confirmTemplate(h:String,t:String,items:List<ParsedLabResult>,system:String="",newVersion:Boolean=false):HospitalLabTemplate {
  require(ReportParser.valid(items));require(h.isNotBlank()&&t.isNotBlank())
  require(items.all{it.metricKey.isNotBlank()}&&items.map{it.metricKey}.distinct().size==items.size){"模板项目 ID 为空或重复"}
  val old=latestTemplate(h,t,system)
  if(old!=null && !newVersion)return old
  val version=if(old==null)1 else old.version+1
  val template=HospitalLabTemplate(h.trim(),t.trim(),version,true,items.map{LabFieldTemplate(it.metricKey,it.displayName,displayLabUnit(it.unit),it.referenceLow,it.referenceHigh)},system.trim())
  // One current template per hospital/panel/system. Re-confirming replaces only that
  // template record; historical reports keep their own unit/range/name snapshots.
  val keep=storedTemplates().filterNot{it.hospitalKey==template.hospitalKey&&it.reportType==template.reportType&&it.systemKey==template.systemKey}
  write("templates",JSONArray().apply{keep.forEach{put(templateToJson(it))};put(templateToJson(template))});return template
 }
 @Synchronized fun saveTemplateFields(source:HospitalLabTemplate,fields:List<LabFieldTemplate>):HospitalLabTemplate {
  require(fields.isNotEmpty()&&fields.all{it.metricKey.isNotBlank()}&&fields.map{it.metricKey}.distinct().size==fields.size){"模板项目 ID 为空或重复"}
  val old=latestTemplate(source.hospitalKey,source.reportType,source.systemKey)?:source
  val template=old.copy(version=old.version+1,confirmed=true,fields=fields.map{it.copy(trendMeaning=it.trendMeaning.trim().ifBlank{metricPurpose(it.metricKey).orEmpty()})})
  val keep=storedTemplates().filterNot{it.hospitalKey==template.hospitalKey&&it.reportType==template.reportType&&it.systemKey==template.systemKey}
  write("templates",JSONArray().apply{keep.forEach{put(templateToJson(it))};put(templateToJson(template))});return template
 }
 /** Without a confirmed template this remains a conservative fill helper. */
 fun applyTemplate(items:List<ParsedLabResult>,template:HospitalLabTemplate?)=applyRememberedTemplate(items,template)
 fun buildReport(h:String,t:String,date:Long,uris:List<String>,items:List<ParsedLabResult>,template:HospitalLabTemplate,raw:String="",system:String=""):LabReport {
  require(ReportParser.valid(items));val id=newId()
  // The editor has applied the selected version. Snapshot exactly what the user confirmed.
  return LabReport(id,h.trim(),t.trim(),date,template.version,uris.mapIndexed{i,u->ReportImage(u,i,System.currentTimeMillis())},items.map{p->val unit=displayLabUnit(p.unit);val n=UnitNormalizer.normalize(p.metricKey,p.value,unit);LabResult(newId(),id,h.trim(),t.trim(),template.version,p.metricKey,p.displayName,p.value,unit,p.referenceLow,p.referenceHigh,date,false,p.textValue,p.comparator,p.rawLine,n.first,n.second)},raw,system.trim())
 }
 @Synchronized fun entries()=rows(read("entries"),::entryFromJson).sortedByDescending{it.occurredAtEpochMillis}
 @Synchronized fun saveEntry(e:HealthEntry){require(e.title.isNotBlank());write("entries",JSONArray().apply{(entries().filterNot{it.id==e.id}+e).forEach{put(entryToJson(it))}})}
 @Synchronized fun deleteEntry(id:String){write("entries",JSONArray().apply{entries().filterNot{it.id==id}.forEach{put(entryToJson(it))}})}
 @Synchronized fun weightRecords()=rows(read("weights")){o->WeightRecord(o.getString("id"),o.getLong("date"),o.getDouble("kg"))}.sortedBy{it.measuredAtEpochMillis}
 @Synchronized fun saveWeight(record:WeightRecord){require(record.kilograms>0&&record.kilograms.isFinite());write("weights",JSONArray().apply{(weightRecords().filterNot{it.id==record.id}+record).sortedBy{it.measuredAtEpochMillis}.forEach{put(JSONObject().put("id",it.id).put("date",it.measuredAtEpochMillis).put("kg",it.kilograms))}})}
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
 fun exportBackup():String=JSONObject().put("schema",3).put("patientProfile",JSONObject().put("name",patientProfile().name).put("birthDate",patientProfile().birthDate).put("sex",patientProfile().sex).put("note",patientProfile().note)).put("reports",read("reports")).put("templates",read("templates")).put("entries",read("entries")).put("weights",read("weights")).put("courseRecords",read("course_records")).put("primary",JSONObject().apply{prefs.all.filterKeys{it.startsWith("primary:")}.forEach{(k,v)->put(k,v)}}).toString()
 private fun reportToJson(r:LabReport)=JSONObject().put("id",r.id).put("hospital",r.hospitalKey).put("type",r.reportType).put("date",r.testedAtEpochMillis).put("tv",r.templateVersion).put("ocr",r.rawOcr).put("system",r.systemKey).put("images",JSONArray().apply{r.sourceImages.forEach{put(JSONObject().put("uri",it.uri).put("page",it.pageIndex).put("at",it.importedAtEpochMillis))}}).put("results",JSONArray().apply{r.results.forEach{put(JSONObject().put("id",it.id).put("key",it.metricKey).put("raw",it.rawName).put("value",it.value?:JSONObject.NULL).put("text",it.textValue).put("cmp",it.comparator).put("line",it.rawLine).put("unit",it.unitAtTest).put("low",it.referenceLowAtTest?:JSONObject.NULL).put("high",it.referenceHighAtTest?:JSONObject.NULL).put("edited",it.editedByUser).put("normalizedValue",it.normalizedValue?:JSONObject.NULL).put("normalizedUnit",it.normalizedUnit))}})
 private fun reportFromJson(o:JSONObject):LabReport {
  val id=o.getString("id");val h=o.getString("hospital");val t=o.getString("type");val date=o.getLong("date");val version=o.intOrNull("tv")
  return LabReport(id,h,t,date,version,rows(o.getJSONArray("images")){x->ReportImage(x.getString("uri"),x.getInt("page"),x.getLong("at"))},rows(o.getJSONArray("results")){x->
   val key=x.getString("key");val value=x.doubleOrNull("value");val unit=displayLabUnit(x.getString("unit"));val normalized=UnitNormalizer.normalize(key,value,unit)
   LabResult(x.getString("id"),id,h,t,version,key,x.getString("raw"),value,unit,x.doubleOrNull("low"),x.doubleOrNull("high"),date,x.optBoolean("edited"),x.optString("text",value?.toString().orEmpty()),x.optString("cmp"),x.optString("line"),normalized.first,normalized.second)
  },o.optString("ocr"),o.optString("system"))
 }
 private fun templateToJson(t:HospitalLabTemplate)=JSONObject().put("hospital",t.hospitalKey).put("type",t.reportType).put("system",t.systemKey).put("version",t.version).put("confirmed",t.confirmed).put("fields",JSONArray().apply{t.fields.forEach{put(JSONObject().put("key",it.metricKey).put("name",it.displayName).put("unit",it.unit).put("low",it.referenceLow?:JSONObject.NULL).put("high",it.referenceHigh?:JSONObject.NULL).put("trendMeaning",it.trendMeaning))}})
 private fun templateFromJson(o:JSONObject)=HospitalLabTemplate(o.getString("hospital"),o.getString("type"),o.getInt("version"),o.getBoolean("confirmed"),rows(o.getJSONArray("fields")){x->LabFieldTemplate(x.getString("key"),x.getString("name"),displayLabUnit(x.getString("unit")),x.doubleOrNull("low"),x.doubleOrNull("high"),x.optString("trendMeaning").ifBlank{metricPurpose(x.getString("key")).orEmpty()})},o.optString("system"))
 private fun entryToJson(e:HealthEntry)=JSONObject().put("id",e.id).put("kind",e.kind.name).put("title",e.title).put("date",e.occurredAtEpochMillis).put("note",e.note).put("hospital",e.hospital).put("category",e.category).put("severity",e.severity).put("frequency",e.frequency).put("duration",e.duration).put("dose",e.dose).put("route",e.route).put("end",e.endAtEpochMillis?:JSONObject.NULL).put("images",JSONArray(e.images))
 private fun entryFromJson(o:JSONObject)=HealthEntry(o.getString("id"),EntryKind.valueOf(o.getString("kind")),o.getString("title"),o.getLong("date"),o.optString("note"),o.optString("hospital"),o.optString("category"),o.optInt("severity"),o.optString("frequency"),o.optString("duration"),o.optString("dose"),o.optString("route"),if(o.isNull("end"))null else o.getLong("end"),o.optJSONArray("images")?.let{a->(0 until a.length()).map{a.getString(it)}}?:emptyList())
 private fun courseRecordToJson(r:CourseRecord)=JSONObject().put("id",r.id).put("date",r.date).put("phase",r.phase).put("title",r.title).put("symptomText",r.symptomText).put("checkText",r.checkText).put("checkImages",JSONArray(r.checkImages)).put("medicineText",r.medicineText).put("medicineImages",JSONArray(r.medicineImages)).put("noteText",r.noteText).put("createdAt",r.createdAt).put("updatedAt",r.updatedAt)
 private fun courseRecordFromJson(o:JSONObject)=CourseRecord(o.getString("id"),o.getLong("date"),o.optString("phase","观察"),o.getString("title"),o.optString("symptomText"),o.optString("checkText"),o.optJSONArray("checkImages").stringList(),o.optString("medicineText"),o.optJSONArray("medicineImages").stringList(),o.optString("noteText"),o.optLong("createdAt",o.getLong("date")),o.optLong("updatedAt",o.getLong("date")))
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
