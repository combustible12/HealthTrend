package com.combustible12.healthtrend

import org.json.JSONObject

/** Independent profile information, never a numeric trend or abnormality. */
data class BloodGroupProfile(val abo:String="",val rhD:String="",val rhC:String="",val rhE:String="",val testedDate:String="") {
 val isEmpty:Boolean get()=listOf(abo,rhD,rhC,rhE,testedDate).all{it.isBlank()}
 fun toJson():JSONObject=JSONObject().put("abo",abo).put("rhD",rhD).put("rhC",rhC).put("rhE",rhE).put("testedDate",testedDate)
 fun validated():BloodGroupProfile {
  val p=BloodGroupProfile(abo.trim(),rhD.trim(),rhC.trim(),rhE.trim(),testedDate.trim())
  require(p.abo in listOf("","A","B","AB","O")){"请选择ABO血型"}
  require(listOf(p.rhD,p.rhC,p.rhE).all{it in listOf("","阳性","阴性")}){"请选择Rh结果"}
  require(p.testedDate.isEmpty()||runCatching{java.time.LocalDate.parse(p.testedDate).toString()==p.testedDate}.getOrDefault(false)){"检测日期请填写YYYY-MM-DD"}
  return p
 }
 companion object {
  fun fromJson(raw:String?):BloodGroupProfile {
   if(raw==null)return BloodGroupProfile()
   val o=JSONObject(raw)
   return BloodGroupProfile(o.optString("abo"),o.optString("rhD"),o.optString("rhC"),o.optString("rhE"),o.optString("testedDate"))
  }
 }
}
