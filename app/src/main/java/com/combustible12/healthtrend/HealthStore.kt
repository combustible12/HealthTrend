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
 // Existing confirmed reports, trends and course records are immutable on app startup.
 // Legacy repairs must be opt-in after an external backup, never automatic.
 init {
  if(!prefs.contains("templates") && !prefs.contains("reports") &&
     !prefs.contains("course_records")) migrateCurrentTemplates()
 }
 private fun read(key:String)=JSONArray(prefs.getString(key,"[]"))
 private fun write(key:String,a:JSONArray){
  // A durable rollback checkpoint is written BEFORE changing critical datasets.
  // Never restore automatically: doing so could overwrite newer confirmed data.
  if(key in setOf("reports","course_records","templates","metric_standard_ranges")){
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
 /** The maternal CBC formerly used RDW for CV. Resolve only an unambiguous % result. */
 private fun normalizeMaternalRdw(report:LabReport):LabReport {
  if(report.hospitalKey.trim()!="福建省妇幼保健院" || report.reportType.trim()!="血常规" || report.systemKey.isNotBlank())return report
  if(report.results.any{it.metricKey=="RDW-CV"})return report
  val legacy=report.results.filter{it.metricKey=="RDW" && displayLabUnit(it.unitAtTest)=="%"}
  if(legacy.size!=1)return report
  return report.copy(results=report.results.map{if(it.id==legacy.single().id)it.copy(metricKey="RDW-CV")else it})
 }
 private fun migrateMaternalRdwReports(){
  val raw=read("reports")
  var changed=false
  val migrated=JSONArray()
  for(i in 0 until raw.length()){
   val original=raw.getJSONObject(i)
   val report=reportFromJson(original)
   val repaired=normalizeMaternalRdw(report)
   if(repaired!=report)changed=true
   migrated.put(if(repaired==report)original else reportToJson(repaired))
  }
  if(changed)write("reports",migrated)
 }
 private fun migrateCurrentTemplates(){
  val raw=runCatching{rows(read("templates"),::templateFromJson)}.getOrDefault(emptyList())
  val current=raw.groupBy{Triple(it.hospitalKey.trim(),it.reportType.trim(),it.systemKey.trim())}.values.mapNotNull{versions->
   versions.withIndex().maxWithOrNull(compareBy<IndexedValue<HospitalLabTemplate>>{it.value.confirmed}
    .thenBy{it.value.version}.thenBy{it.index})?.value
  }.map(::repairXiacuBiochemistryTemplate).map(::ensureMaternalCbcRdwFields).map(::correctMaternalBiochemistryAstAlt).toMutableList()
  if(current.none{it.hospitalKey=="霞浦县中医院"&&it.reportType=="生化"&&it.systemKey.isBlank()})current+=xiapuBiochemistryTemplate()
  if(current.none{it.hospitalKey=="福建省肿瘤医院"&&it.reportType=="肿瘤标志物"&&it.systemKey.isBlank()})current+=HospitalLabTemplate("福建省肿瘤医院","肿瘤标志物",1,true,listOf(
   LabFieldTemplate("CEA","癌胚抗原","ng/mL",0.0,5.0),
   LabFieldTemplate("AFP","甲胎蛋白","ng/mL",0.0,7.0),
   LabFieldTemplate("CA199","糖类抗原19-9","U/mL",0.0,30.0),
   LabFieldTemplate("CA125","糖类抗原12-5","U/mL",0.0,25.0),
   LabFieldTemplate("SCC","鳞状细胞癌相关抗原","ng/mL",0.0,1.5)
  ))
  if(current.none{it.hospitalKey=="福建省妇幼保健院"&&it.reportType=="肿瘤标志物"&&it.systemKey.isBlank()})current+=HospitalLabTemplate("福建省妇幼保健院","肿瘤标志物",1,true,listOf(
   LabFieldTemplate("AFP","甲胎蛋白","ng/mL",0.0,8.8),
   LabFieldTemplate("CEA","癌胚抗原","ng/mL",0.0,5.0),
   LabFieldTemplate("CA125","CA-125","U/mL",0.0,35.0),
   LabFieldTemplate("CA153","CA-153","U/mL",0.0,31.3),
   LabFieldTemplate("CA199","CA-199","U/mL",0.0,37.0),
   LabFieldTemplate("SCC","鳞癌相关抗原测定","ng/mL",0.0,1.5)
  ))
  if(current.none{it.hospitalKey=="福建省肿瘤医院"&&it.reportType=="电解质及肾功能"&&it.systemKey.isBlank()})current+=HospitalLabTemplate("福建省肿瘤医院","电解质及肾功能",1,true,listOf(
   LabFieldTemplate("UREA","尿素","mmol/L",2.6,7.5),
   LabFieldTemplate("CREA","肌酐","μmol/L",41.0,73.0),
   LabFieldTemplate("UREA/CREA","尿素/肌酐","",null,null),
   LabFieldTemplate("UA","尿酸","μmol/L",154.7,357.0),
   LabFieldTemplate("GLU","葡萄糖","mmol/L",3.7,6.1),
   LabFieldTemplate("K","钾","mmol/L",3.5,5.5),
   LabFieldTemplate("NA","钠","mmol/L",135.0,145.0),
   LabFieldTemplate("CL","氯","mmol/L",99.0,110.0),
   LabFieldTemplate("HCO3","碳酸氢根","mmol/L",22.0,34.0),
   LabFieldTemplate("CA","钙","mmol/L",2.1,2.7),
   LabFieldTemplate("MG","镁","mmol/L",0.7,1.1),
   LabFieldTemplate("PHOS","无机磷酸盐","mmol/L",0.85,1.51),
   LabFieldTemplate("AG","阴离子间隙","mmol/L",8.0,16.0),
   LabFieldTemplate("OSM","渗透压","mOsm/L",null,null),
   LabFieldTemplate("CK","肌酸激酶","U/L",40.0,200.0),
   LabFieldTemplate("CKMB","肌酸激酶MB亚型","U/L",0.0,24.0)
  ))
  if(current.none{it.hospitalKey=="福建省妇幼保健院"&&it.reportType=="生化"&&it.systemKey.isBlank()})current+=HospitalLabTemplate("福建省妇幼保健院","生化",1,true,listOf(
   LabFieldTemplate("K","钾","mmol/L",3.5,5.3),
   LabFieldTemplate("NA","钠","mmol/L",137.0,147.0),
   LabFieldTemplate("CL","氯","mmol/L",99.0,110.0),
   LabFieldTemplate("CA","钙","mmol/L",2.11,2.52),
   LabFieldTemplate("HCO3","血清碳酸氢盐HCO3","mmol/L",22.0,29.0),
   LabFieldTemplate("MG","镁","mmol/L",0.65,1.25),
   LabFieldTemplate("PHOS","磷","mmol/L",0.85,1.51),
   LabFieldTemplate("UREA","尿素","mmol/L",2.6,7.5),
   LabFieldTemplate("CREA","肌酐","μmol/L",41.0,73.0),
   LabFieldTemplate("UREA/CREA","尿素:肌酐","",10.0,20.0),
   LabFieldTemplate("UA","尿酸","μmol/L",155.0,357.0),
   LabFieldTemplate("ALT","谷丙转氨酶","U/L",7.0,40.0),
   LabFieldTemplate("AST","谷草转氨酶","U/L",13.0,35.0),
   LabFieldTemplate("AST/ALT","谷草/谷丙","",null,null),
   LabFieldTemplate("GGT","γ-谷氨酰转肽酶","U/L",7.0,45.0),
   LabFieldTemplate("ALP","碱性磷酸酶","U/L",50.0,135.0),
   LabFieldTemplate("TP","总蛋白","g/L",65.0,85.0),
   LabFieldTemplate("ALB","白蛋白","g/L",40.0,55.0),
   LabFieldTemplate("GLOB","球蛋白","g/L",20.0,40.0),
   LabFieldTemplate("A/G","白蛋白/球蛋白","",1.2,2.4),
   LabFieldTemplate("TBIL","总胆红素","μmol/L",0.0,21.0),
   LabFieldTemplate("DBIL","直接胆红素","μmol/L",0.0,8.0),
   LabFieldTemplate("IBIL","非结合胆红素","μmol/L",1.7,17.0),
   LabFieldTemplate("GLU","葡萄糖","mmol/L",3.6,6.1),
   LabFieldTemplate("TG","甘油三酯","mmol/L",0.0,1.7),
   LabFieldTemplate("CHOL","总胆固醇","mmol/L",0.0,5.2),
   LabFieldTemplate("APOA1","载脂蛋白-A1","g/L",1.2,1.6),
   LabFieldTemplate("APOB","载脂蛋白-B","g/L",0.6,1.2),
   LabFieldTemplate("HDLC","高密度脂蛋白胆固醇","mmol/L",1.29,1.55),
   LabFieldTemplate("LDLC","低密度脂蛋白胆固醇","mmol/L",0.0,3.12),
   LabFieldTemplate("LDH","乳酸脱氢酶","U/L",120.0,250.0),
   LabFieldTemplate("CK","肌酸激酶","U/L",40.0,200.0),
   LabFieldTemplate("CKMB","肌酸激酶同工酶","U/L",0.0,25.0),
   LabFieldTemplate("CKMB/CK","肌酸激酶同工酶/肌酸激酶","",null,null),
   LabFieldTemplate("AG","阴离子间隙","",8.0,16.0),
   LabFieldTemplate("OSM","渗透压","",280.0,320.0)
  ))
  if(current.none{it.hospitalKey=="福建省妇幼保健院"&&it.reportType=="血常规"&&it.systemKey.isBlank()})current+=HospitalLabTemplate("福建省妇幼保健院","血常规",1,true,listOf(
   LabFieldTemplate("WBC","白细胞计数","10^9/L",3.5,9.5),
   LabFieldTemplate("NEUT%","中性粒细胞%","%",40.0,75.0),
   LabFieldTemplate("LYMPH%","淋巴细胞%","%",20.0,50.0),
   LabFieldTemplate("MONO%","单核细胞%","%",3.0,10.0),
   LabFieldTemplate("EOS%","嗜酸性粒细胞%","%",0.4,8.0),
   LabFieldTemplate("BASO%","嗜碱性粒细胞%","%",0.0,1.0),
   LabFieldTemplate("NEUT#","中性粒细胞绝对数","10^9/L",1.8,6.3),
   LabFieldTemplate("LYMPH#","淋巴细胞绝对数","10^9/L",1.1,3.2),
   LabFieldTemplate("MONO#","单核细胞绝对数","10^9/L",0.13,0.76),
   LabFieldTemplate("EOS#","嗜酸性粒细胞绝对数","10^9/L",0.02,0.52),
   LabFieldTemplate("BASO#","嗜碱性粒细胞绝对数","10^9/L",0.0,0.05),
   LabFieldTemplate("RBC","红细胞计数","10^12/L",3.8,5.1),
   LabFieldTemplate("HGB","血红蛋白","g/L",115.0,150.0),
   LabFieldTemplate("HCT","红细胞压积","%",35.0,45.0),
   LabFieldTemplate("MCV","平均红细胞体积","fL",82.0,100.0),
   LabFieldTemplate("MCH","平均血红蛋白含量","pg",27.0,34.0),
   LabFieldTemplate("MCHC","平均血红蛋白浓度","g/L",316.0,354.0),
   LabFieldTemplate("RDW-CV","红细胞分布宽度CV","%",12.2,15.0),
   LabFieldTemplate("RDW-SD","红细胞分布宽度SD","fL",42.0,53.6),
   LabFieldTemplate("PLT","血小板计数","10^9/L",125.0,350.0),
   LabFieldTemplate("MPV","平均血小板体积","fL",9.2,12.1),
   LabFieldTemplate("PCT","血小板压积","%",0.19,0.4),
   LabFieldTemplate("PDW","血小板分布宽度","fL",9.6,15.2),
   LabFieldTemplate("P-LCR","大血小板","%",19.6,42.6)
  ))
  val changed=raw.size!=current.size || raw.zip(current).any{(a,b)->a!=b}
  if(changed)write("templates",JSONArray().apply{current.forEach{put(templateToJson(it))}})
 }
 /** Resolve maternal CBC identities by measurement unit, not mutable display names. */
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
 fun patientProfile():PatientProfile { val raw=prefs.getString("patient_profile",null)?:return PatientProfile();return runCatching{val o=JSONObject(raw);PatientProfile(o.optString("name"),o.optString("birthDate"),o.optString("sex"),o.optString("note"),o.optString("bloodType"))}.getOrDefault(PatientProfile()) }
 @Synchronized fun savePatientProfile(p:PatientProfile){val o=JSONObject().put("name",p.name.trim()).put("birthDate",p.birthDate.trim()).put("sex",p.sex.trim()).put("note",p.note.trim()).put("bloodType",p.bloodType.trim());check(prefs.edit().putString("patient_profile",o.toString()).commit())}
 @Synchronized fun reports():List<LabReport>{
  val reports=rows(read("reports"),::reportFromJson).sortedByDescending{it.testedAtEpochMillis}
  return reports
 }
 /**
  * Preserve every reference interval observed in reports. A hospital may use
  * multiple intervals for the same test; none replaces earlier test-time bounds.
  * No analyzer identifier is required or inferred.
  */
 data class ObservedReferenceRange(
  val hospitalKey:String,val reportType:String,
  val metricKey:String,val unit:String,val lower:Double?,val upper:Double?,
  val firstObservedAt:Long,val lastObservedAt:Long,val reportCount:Int
 )
 @Synchronized fun referenceRangeHistory(metricKey:String):List<ObservedReferenceRange> {
  val identity=canonicalMetricKey(metricKey)
  data class Evidence(val report:LabReport,val result:LabResult)
  val evidence=reports().flatMap{report->report.results
   .filter{canonicalMetricKey(it.metricKey)==identity &&
    (it.referenceLowAtTest!=null||it.referenceHighAtTest!=null)}
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
   e.report.hospitalKey.trim(),e.report.reportType.trim(),
   identity,e.result.unitAtTest,e.result.referenceLowAtTest?.toString().orEmpty(),
   e.result.referenceHighAtTest?.toString().orEmpty()
  )}.values.map{group->
   val first=group.first()
   ObservedReferenceRange(first.report.hospitalKey,first.report.reportType,
    identity,first.result.unitAtTest,
    first.result.referenceLowAtTest,first.result.referenceHighAtTest,
    group.minOf{it.report.testedAtEpochMillis},
    group.maxOf{it.report.testedAtEpochMillis},
    group.map{it.report.id}.distinct().size)
  }.sortedWith(compareBy<ObservedReferenceRange>{it.firstObservedAt}
   .thenBy{it.hospitalKey}.thenBy{it.reportType})
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
  // Report-local metric order and data are independent of legacy hospital templates.
  val ordered=r
  write("reports",JSONArray().apply{(reports().filterNot{it.id==r.id}+ordered).forEach{put(reportToJson(it))}})
  // A report contributes only its observed, validated intervals. Existing choices
  // are reused. Changes to the pool never rewrite historical report snapshots.
  ordered.results.forEach{result->
   if(result.referenceLowAtTest!=null||result.referenceHighAtTest!=null){
    addStandardRange(result.metricKey,result.unitAtTest,
     result.referenceLowAtTest,result.referenceHighAtTest)
   }
  }
  // Both newly created and edited reports must persist their source images.
  val stored=reports().firstOrNull{it.id==r.id}?:error("报告保存验证失败：重新读取不到报告")
  check(stored.results.size==ordered.results.size &&
   stored.results.map{it.id}==ordered.results.map{it.id} &&
   stored.results.map{it.metricKey}==ordered.results.map{it.metricKey} &&
   stored.results.map{it.value}==ordered.results.map{it.value} &&
   stored.results.map{it.textValue}==ordered.results.map{it.textValue} &&
   stored.results.map{it.unitAtTest}==ordered.results.map{it.unitAtTest} &&
   stored.results.map{it.referenceLowAtTest}==ordered.results.map{it.referenceLowAtTest} &&
   stored.results.map{it.referenceHighAtTest}==ordered.results.map{it.referenceHighAtTest}){
   "报告保存验证失败：重新读取的数据与保存内容不一致"
  }
  check(r.sourceImages.all{image->stored.sourceImages.any{it.uri==image.uri}}){
   "报告保存验证失败：原图关联未持久化"
  }
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

 fun trend(key:String)=reports().flatMap{r->r.results.filter{canonicalMetricKey(it.metricKey)==canonicalMetricKey(key) && it.value!=null && it.comparator.isEmpty()}.map{r to it}}.sortedWith(compareBy<Pair<LabReport,LabResult>>{it.first.testedAtEpochMillis}.thenBy{it.second.id})
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
  val catalogue=metricCatalogue()
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
  * Only unresolved identities can be filled. A known explicit code always wins. */
 fun reconcileParsedMetrics(rows:List<ParsedLabResult>):List<ParsedLabResult> =
  rows.map{row->
   if(row.metricKey.isNotBlank() && row.metricKey!="未识别" &&
      !row.metricKey.startsWith("待核对:"))row
   else{
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
  val commonUnit:String,val referenceRanges:List<ObservedReferenceRange>
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
   val savedRanges=referenceRangeHistory(key)
   val templateRanges=templates().flatMap{template->
    template.fields.filter{canonicalMetricKey(it.metricKey)==key &&
     (it.referenceLow!=null||it.referenceHigh!=null)}.map{field->
     val low=UnitNormalizer.normalize(key,field.referenceLow,field.unit)
     val high=UnitNormalizer.normalize(key,field.referenceHigh,field.unit)
     ObservedReferenceRange(template.hospitalKey,template.reportType,key,
      if(field.referenceLow!=null)low.second else high.second,low.first,high.first,
      0L,0L,0)
    }
   }
   val persistedRanges=standardRanges(key).map{range->
    ObservedReferenceRange("", "",key,range.unit,range.low,range.high,0L,0L,0)
   }
   val ranges=(persistedRanges+savedRanges+templateRanges).distinctBy{
    listOf(it.unit,it.lower?.toString(),it.upper?.toString())
   }
   val units=rememberedUnits(key)
   val common=definition?.unit?.takeIf{it.isNotBlank()}
    ?:units.firstNotNullOfOrNull{source->
     val normalized=UnitNormalizer.normalize(key,1.0,source).second
     normalized.takeIf{it!=source}
    } ?:units.firstOrNull().orEmpty()
   UnifiedMetricEntry(key,name,names.filter{it!=name}.toSet(),common,ranges)
  }
 }
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
     p.referenceLow,p.referenceHigh,date,false,p.textValue,p.comparator,p.rawLine,n.first,n.second)
   },raw,system.trim())
 }
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
   append('\n').append(canonicalMetricKey(row.metricKey)).append(' ')
   append(preferredMetricName(row.metricKey)?:UnifiedMetricStandard.definition(row.metricKey)?.name?:row.rawName)
   append(' ').append(row.textValue).append(' ')
   append(row.unitAtTest.ifBlank{"-"}) 
   if(row.referenceLowAtTest!=null||row.referenceHighAtTest!=null){
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
 fun exportBackup():String=JSONObject().put("schema",3).put("patientProfile",JSONObject().put("name",patientProfile().name).put("birthDate",patientProfile().birthDate).put("sex",patientProfile().sex).put("note",patientProfile().note).put("bloodType",patientProfile().bloodType)).put("reports",read("reports")).put("templates",read("templates")).put("entries",read("entries")).put("weights",read("weights")).put("courseRecords",read("course_records")).put("standardRanges",read("metric_standard_ranges")).put("metricAliases",read("metric_aliases")).put("customMetrics",read("custom_metrics")).put("primary",JSONObject().apply{prefs.all.filterKeys{it.startsWith("primary:")}.forEach{(k,v)->put(k,v)}}).toString()
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
