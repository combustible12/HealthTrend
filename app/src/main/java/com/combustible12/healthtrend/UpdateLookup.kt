package com.combustible12.healthtrend

import org.json.JSONObject

internal class UpdateHttpFailure(val status:Int,val limited:Boolean=false):IllegalStateException(
 when{
  limited||status==429->"GitHub 更新请求暂时受限，请稍后重试或从发布页下载"
  status==401->"GitHub 访问凭据无效"
  status==403->"GitHub 拒绝本次更新请求（HTTP 403）"
  status==404->"未找到更新资源（HTTP 404）"
  else->"更新服务返回 HTTP $status"
 })
internal data class UpdateResponse(val body:String,val finalUrl:String)

/** Anonymous public access first; credentials cannot break public updates. */
internal object UpdateLookup {
 private const val api="https://api.github.com/repos/combustible12/HealthTrend/releases/latest"
 private const val web="https://github.com/combustible12/HealthTrend/releases"
 fun latest(token:String,fetch:(String,String)->UpdateResponse):AvailableUpdate {
  var failure:Exception?=null
  for(auth in listOf("")+listOfNotNull(token.trim().takeIf{it.isNotEmpty()})){
   try{
    val j=JSONObject(fetch(api,auth).body)
    require(!j.optBoolean("draft")&&!j.optBoolean("prerelease")){"没有可用的正式更新"}
    val version=Regex("版本代码[:： ]+(\\d+)").find(j.optString("body"))?.groupValues?.get(1)?.toLongOrNull()
     ?:tagVersion(j.getString("tag_name"))?:error("无法识别发布版本")
    val assets=j.getJSONArray("assets")
    val apk=(0 until assets.length()).map{assets.getJSONObject(it)}.firstOrNull{it.optString("name")=="HealthTrend.apk"}?:error("正式版本尚无 APK")
    // Public browser URL avoids GitHub API asset rate limits; private API remains compatible.
    val url=apk.optString("browser_download_url").takeIf{auth.isBlank()&&it.isNotBlank()}?:apk.getString("url")
    return AvailableUpdate(j.optString("name",j.getString("tag_name")),version,url,j.optString("body"))
   }catch(e:Exception){failure=e}
  }
  try{
   val release=fetch("$web/latest","")
   val tag=java.net.URI(release.finalUrl).path.substringAfter("/releases/tag/","")
   val version=tagVersion(tag)?:error("公开发布页未提供有效版本")
   val assets=fetch("$web/expanded_assets/$tag","").body
   val path="/combustible12/HealthTrend/releases/download/$tag/HealthTrend.apk"
   require(assets.contains("href=\"$path\"")||assets.contains("href='$path'")){"正式版本尚无 APK"}
   return AvailableUpdate(tag,version,"https://github.com$path","")
  }catch(e:Exception){
   throw IllegalStateException("无法检查更新：${failure?.message?:e.message}。可从 GitHub 发布页下载 APK",e)
  }
 }
 private fun tagVersion(tag:String):Long?=Regex("v(\\d{10})").matchEntire(tag)?.groupValues?.get(1)?.toLongOrNull()
}
