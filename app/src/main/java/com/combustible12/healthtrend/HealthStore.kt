package com.combustible12.healthtrend

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class HealthStore(context: Context) {
    private val prefs=context.getSharedPreferences("healthtrend_store_v1",Context.MODE_PRIVATE)

    fun reports():List<LabReport>{
        val a=JSONArray(prefs.getString("reports","[]"))
        return (0 until a.length()).mapNotNull{i->runCatching{reportFromJson(a.getJSONObject(i))}.getOrNull()}.sortedByDescending{it.testedAtEpochMillis}
    }
    fun saveReport(report:LabReport){
        val all=reports().filterNot{it.id==report.id}+report
        val a=JSONArray();all.forEach{a.put(reportToJson(it))}
        prefs.edit().putString("reports",a.toString()).apply()
    }
    fun trend(metricKey:String):List<Pair<LabReport,LabResult>> = reports().flatMap { r -> r.results.filter { it.metricKey==metricKey }.map { r to it } }.sortedBy { it.first.testedAtEpochMillis }
    fun updateValue(reportId:String,resultId:String,newValue:Double){
        reports().firstOrNull{it.id==reportId}?.let{r->
            saveReport(r.copy(results=r.results.map{if(it.id==resultId)it.withEditedValue(newValue) else it}))
        }
    }
    fun templates():List<HospitalLabTemplate>{
        val a=JSONArray(prefs.getString("templates","[]"))
        return (0 until a.length()).mapNotNull{i->runCatching{templateFromJson(a.getJSONObject(i))}.getOrNull()}
    }
    fun latestTemplate(hospital:String,type:String)=templates().filter{it.hospitalKey==hospital&&it.reportType==type&&it.confirmed}.maxByOrNull{it.version}
    fun confirmTemplate(hospital:String,type:String,items:List<ParsedLabResult>):HospitalLabTemplate{
        latestTemplate(hospital,type)?.let{return it}
        val t=HospitalLabTemplate(hospital,type,1,true,items.map{LabFieldTemplate(it.metricKey,it.displayName,it.unit,it.referenceLow,it.referenceHigh)})
        val a=JSONArray();templates().forEach{a.put(templateToJson(it))};a.put(templateToJson(t));prefs.edit().putString("templates",a.toString()).apply();return t
    }
    fun buildReport(hospital:String,type:String,testedAt:Long,imageUri:String,parsed:List<ParsedLabResult>,template:HospitalLabTemplate):LabReport{
        val reportId=UUID.randomUUID().toString();val fields=template.fields.associateBy{it.metricKey}
        return LabReport(reportId,hospital,type,testedAt,template.version,listOf(ReportImage(imageUri,0,System.currentTimeMillis())),parsed.map{p->
            val f=fields[p.metricKey]
            LabResult(UUID.randomUUID().toString(),reportId,hospital,type,template.version,p.metricKey,p.displayName,p.value,f?.unit?:p.unit,f?.referenceLow?:p.referenceLow,f?.referenceHigh?:p.referenceHigh,testedAt)
        })
    }
    private fun reportToJson(r:LabReport)=JSONObject().put("id",r.id).put("hospital",r.hospitalKey).put("type",r.reportType).put("date",r.testedAtEpochMillis).put("tv",r.templateVersion).put("images",JSONArray().apply{r.sourceImages.forEach{put(JSONObject().put("uri",it.uri).put("page",it.pageIndex).put("at",it.importedAtEpochMillis))}}).put("results",JSONArray().apply{r.results.forEach{put(JSONObject().put("id",it.id).put("key",it.metricKey).put("raw",it.rawName).put("value",it.value).put("unit",it.unitAtTest).put("low",it.referenceLowAtTest).put("high",it.referenceHighAtTest).put("edited",it.editedByUser))}})
    private fun reportFromJson(o:JSONObject):LabReport{val id=o.getString("id");val h=o.getString("hospital");val t=o.getString("type");val d=o.getLong("date");val tv=if(o.isNull("tv"))null else o.getInt("tv");val ia=o.getJSONArray("images");val ra=o.getJSONArray("results");return LabReport(id,h,t,d,tv,(0 until ia.length()).map{i->val x=ia.getJSONObject(i);ReportImage(x.getString("uri"),x.getInt("page"),x.getLong("at"))},(0 until ra.length()).map{i->val x=ra.getJSONObject(i);LabResult(x.getString("id"),id,h,t,tv,x.getString("key"),x.getString("raw"),x.getDouble("value"),x.getString("unit"),if(x.isNull("low"))null else x.getDouble("low"),if(x.isNull("high"))null else x.getDouble("high"),d,x.optBoolean("edited",false))})}
    private fun templateToJson(t:HospitalLabTemplate)=JSONObject().put("hospital",t.hospitalKey).put("type",t.reportType).put("version",t.version).put("confirmed",t.confirmed).put("fields",JSONArray().apply{t.fields.forEach{put(JSONObject().put("key",it.metricKey).put("name",it.displayName).put("unit",it.unit).put("low",it.referenceLow).put("high",it.referenceHigh))}})
    private fun templateFromJson(o:JSONObject):HospitalLabTemplate{val a=o.getJSONArray("fields");return HospitalLabTemplate(o.getString("hospital"),o.getString("type"),o.getInt("version"),o.getBoolean("confirmed"),(0 until a.length()).map{i->val x=a.getJSONObject(i);LabFieldTemplate(x.getString("key"),x.getString("name"),x.getString("unit"),if(x.isNull("low"))null else x.getDouble("low"),if(x.isNull("high"))null else x.getDouble("high"))})}
}
