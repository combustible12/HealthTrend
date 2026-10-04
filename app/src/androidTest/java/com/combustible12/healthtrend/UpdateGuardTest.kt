package com.combustible12.healthtrend

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Exercise installed APK metadata and Android Keystore on the emulator. */
class UpdateGuardTest {
 private val instrumentation = InstrumentationRegistry.getInstrumentation()
 private val context = instrumentation.targetContext

 private fun rejects(expected: String, action: () -> Unit) {
  try {
   action()
   fail("Expected update validation to reject: $expected")
  } catch (e: IllegalArgumentException) {
   assertTrue("Unexpected validation error: ${e.message}", e.message.orEmpty().contains(expected))
  } catch (e: IllegalStateException) {
   assertTrue("Unexpected validation error: ${e.message}", e.message.orEmpty().contains(expected))
  }
 }

 @Test fun credentialsAreEncryptedPersistedAndCanBeCleared() {
  val credentials = UpdateCredentials(context)
  val prefs = context.getSharedPreferences("healthtrend_update_auth", 0)
  val fixture = "healthtrend-test-token-not-a-real-credential"
  try {
   credentials.save(fixture)
   assertEquals(fixture, UpdateCredentials(context).load())
   assertTrue(prefs.contains("iv"))
   assertTrue(prefs.contains("value"))
   assertFalse(prefs.all.values.any { it.toString().contains(fixture) })
   credentials.save("")
   assertEquals("", UpdateCredentials(context).load())
   assertFalse(prefs.contains("iv"))
   assertFalse(prefs.contains("value"))
  } finally {
   credentials.save("")
  }
 }

 @Test fun downloadedApkMustBelongToHealthTrendAndBeNewer() {
  val updater = AppUpdater(context)
  val installedApk = File(context.applicationInfo.sourceDir)
  assertTrue(installedApk.exists())
  rejects("下载版本没有高于当前版本") { updater.validate(installedApk) }
  val testPackageApk = File(instrumentation.context.applicationInfo.sourceDir)
  assertTrue(testPackageApk.exists())
  rejects("APK 应用标识不匹配") { updater.validate(testPackageApk) }
 }

 @Test fun newerApkWithTheInstalledSignerPassesValidation() {
  val fixture = File(context.cacheDir, "qa-update-same-signer.apk")
  assertTrue("CI must supply the real newer signed APK", fixture.exists())
  assertTrue(AppUpdater(context).validate(fixture) > BuildConfig.VERSION_CODE.toLong())
 }

 @Test fun newerApkWithAnotherSignerCannotReplaceInstalledData() {
  val fixture = File(context.cacheDir, "qa-update-wrong-signer.apk")
  assertTrue("CI must supply the real APK with a different signing key", fixture.exists())
  rejects("新版签名与当前安装版不一致") { AppUpdater(context).validate(fixture) }
 }

 @Test fun damagedDownloadCannotOpenInstaller() {
  val damaged = File(context.cacheDir, "qa-invalid-update.apk")
  try {
   damaged.writeText("HealthTrend update fixture: not an APK")
   rejects("下载文件不是有效 APK") { AppUpdater(context).validate(damaged) }
  } finally {
   damaged.delete()
  }
 }
}
