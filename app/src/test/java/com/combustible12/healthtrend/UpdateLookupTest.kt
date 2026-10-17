package com.combustible12.healthtrend

import org.junit.Assert.*
import org.junit.Test

class UpdateLookupTest {
 private val apiJson="""{"tag_name":"v2026101714","body":"版本代码: 2026101714","assets":[{"name":"HealthTrend.apk","url":"https://api.github.com/repos/combustible12/HealthTrend/releases/assets/1","browser_download_url":"https://github.com/combustible12/HealthTrend/releases/download/v2026101714/HealthTrend.apk"}]}"""
 @Test fun publicReleaseNeverSendsStoredInvalidCredentials(){
  var calls=0
  val u=UpdateLookup.latest("invalid-token"){url,auth->calls++;assertEquals("",auth);UpdateResponse(apiJson,url)}
  assertEquals(1,calls);assertEquals(2026101714L,u.version);assertTrue(u.assetUrl.startsWith("https://github.com/"))
 }
 @Test fun rateLimitedApiFallsBackToVerifiedPublicAsset(){
  val calls=mutableListOf<String>()
  val u=UpdateLookup.latest(""){url,auth->
   calls+=url;assertEquals("",auth)
   when{
    url.contains("api.github.com")->throw UpdateHttpFailure(403,true)
    url.endsWith("/latest")->UpdateResponse("", "https://github.com/combustible12/HealthTrend/releases/tag/v2026101714")
    else->UpdateResponse("<a href=\"/combustible12/HealthTrend/releases/download/v2026101714/HealthTrend.apk\">APK</a>",url)
   }
  }
  assertEquals(3,calls.size);assertEquals(2026101714L,u.version)
  assertFalse(UpdateVersionPolicy.isNewer(u.version,2026101714L))
 }
 @Test fun privateAccessCanUseExplicitCredentialsAfterAnonymousFailure(){
  var calls=0
  val u=UpdateLookup.latest("configured"){url,auth->calls++;if(auth.isEmpty())throw UpdateHttpFailure(404);UpdateResponse(apiJson,url)}
  assertEquals(2,calls);assertTrue(u.assetUrl.startsWith("https://api.github.com/"))
 }
 @Test fun missingApkAndUnknownVersionAreNotReportedAsLatest(){
  listOf("v2026101714","vunknown").forEach{tag->
   try{UpdateLookup.latest(""){url,_->if(url.contains("api.github.com"))throw UpdateHttpFailure(404) else UpdateResponse("no asset","https://github.com/combustible12/HealthTrend/releases/tag/$tag")};fail("Missing release must fail")}
   catch(e:IllegalStateException){assertFalse(e.message.orEmpty().contains("此仓库为私有"))}
  }
 }
 @Test fun errorMessagesDistinguishAuthRateLimitAndMissingResources(){
  assertTrue(UpdateHttpFailure(401).message!!.contains("凭据"))
  assertTrue(UpdateHttpFailure(403,true).message!!.contains("受限"))
  assertTrue(UpdateHttpFailure(404).message!!.contains("404"))
  assertFalse(UpdateHttpFailure(403).message!!.contains("私有仓库"))
 }
}
