package com.combustible12.healthtrend

import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Release-only checks use the actual private GitHub release API and APK asset. */
class ProductionUpdateTest {
 @get:org.junit.Rule val compose=androidx.compose.ui.test.junit4.createAndroidComposeRule<MainActivity>()
 private val context = InstrumentationRegistry.getInstrumentation().targetContext
 private fun release() = JSONObject(File(context.cacheDir,"qa-production-release.json").readText())
 private fun withToken(action:(String)->Unit) {
  val file=File(context.cacheDir,"qa-production-token")
  assertTrue("Release workflow must supply ephemeral read credentials",file.exists())
  try { action(file.readText()) } finally { file.delete() }
 }

 @Test fun downloadActualReleaseAndPreservePendingInstall() = withToken { token ->
  val config=release()
  val store=HealthStore(context)
  val items=ReportParser.parse("HGB 102 g/L 113-151")
  val template=store.confirmTemplate("升级验证医院","血常规",items)
  val image=File(context.cacheDir,"qa-production-source.png")
  val bitmap=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888)
  bitmap.eraseColor(android.graphics.Color.WHITE)
  image.outputStream().use{assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))}
  bitmap.recycle()
  val source=store.ownImage(Uri.fromFile(image));image.delete()
  store.saveReport(store.buildReport("升级验证医院","血常规",1750000000000L,listOf(source),items,template,"升级前完整原文"))
  store.saveEntry(HealthEntry(id="production-symptom",kind=EntryKind.SYMPTOM,title="升级前症状",occurredAtEpochMillis=1750000000000L,severity=4,note="保留备注"))
  store.setPrimary("LDH",true)
  UpdateCredentials(context).save("qa-retained-setting-not-a-real-token")
  val updater=AppUpdater(context)
  val downloaded=updater.download(AvailableUpdate("Release verification",config.getLong("version"),config.getString("assetUrl"),""),token){_,_->}
  assertTrue(downloaded.exists())
  assertEquals(config.getLong("version"),updater.validate(downloaded))
  assertFalse(context.packageManager.canRequestPackageInstalls())
  Intents.init()
  try {
   Intents.intending(hasAction(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)).respondWith(android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_CANCELED,null))
   updater.install(downloaded)
   assertTrue("Unknown-source permission handoff was not requested",Intents.getIntents().any{it.action==android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES})
   assertTrue("Pending APK survives installer permission handoff",downloaded.exists())
   assertEquals(downloaded.absolutePath,updater.resumableDownloadedApk()?.absolutePath)
  } finally { Intents.release() }
 }

 @Test fun publishedReleaseIsOfferedAndInstallerReceivesOwnedApk() = withToken { token ->
  val config=release()
  val updater=AppUpdater(context)
  val available=checkNotNull(updater.check(token)){"Published release must be offered to the preceding version"}
  assertEquals(config.getLong("version"),available.version)
  assertEquals(config.getString("assetUrl"),available.assetUrl)
  assertTrue(context.packageManager.canRequestPackageInstalls())
  val file=File(context.cacheDir,"updates/healthtrend.apk")
  assertEquals(config.getLong("version"),updater.validate(file))
  Intents.init()
  try {
   Intents.intending(hasAction(Intent.ACTION_VIEW)).respondWith(android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_OK,null))
   updater.install(file)
   val sent=requireNotNull(Intents.getIntents().lastOrNull{it.action==Intent.ACTION_VIEW&&it.type=="application/vnd.android.package-archive"}){"APK installer handoff was not requested"}
   assertEquals("content",sent.data!!.scheme)
   assertEquals(context.packageName+".files",sent.data!!.authority)
   assertTrue((sent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
  } finally { Intents.release() }
 }
}

class ProductionUpgradeTest {
 @Test fun installedReleaseRetainsAllSeededData() {
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val config=JSONObject(File(context.cacheDir,"qa-production-release.json").readText())
  assertEquals(config.getLong("version"),context.packageManager.getPackageInfo(context.packageName,0).longVersionCode)
  val store=HealthStore(context)
  val report=store.reports().single()
  assertEquals("升级验证医院",report.hospitalKey)
  assertEquals("升级前完整原文",report.rawOcr)
  assertEquals(102.0,report.results.single().value!!,0.0)
  assertEquals(113.0,report.results.single().referenceLowAtTest!!,0.0)
  assertEquals(151.0,report.results.single().referenceHighAtTest!!,0.0)
  assertTrue(File(Uri.parse(report.sourceImages.single().uri).path!!).exists())
  assertEquals(1,store.latestTemplate("升级验证医院","血常规")!!.version)
  assertEquals("保留备注",store.entries().single().note)
  assertEquals(4,store.entries().single().severity)
  assertTrue(store.isPrimary("LDH"))
  assertEquals("qa-retained-setting-not-a-real-token",UpdateCredentials(context).load())
  assertFalse(File(context.cacheDir,"qa-production-token").exists())
 }
}
