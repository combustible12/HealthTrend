package com.combustible12.healthtrend
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
/** CI installs v1, seeds its original preference format, then performs adb install -r of this APK. */
class UpgradeTest{
 @Test fun installedUpgradeRetainsOriginalRecordsAndTemplates(){
  val store=HealthStore(InstrumentationRegistry.getInstrumentation().targetContext)
  val report=store.reports().single();assertEquals("upgrade-v1",report.id);assertEquals(102.0,report.results.single().value!!,0.0);assertEquals(113.0,report.results.single().referenceLowAtTest!!,0.0)
  assertTrue(java.io.File(android.net.Uri.parse(report.sourceImages.single().uri).path!!).exists())
  assertEquals(1,store.latestTemplate("原医院","血常规")!!.version)
  store.updateValue(report.id,report.results.single().id,120.0)
  assertEquals(120.0,HealthStore(InstrumentationRegistry.getInstrumentation().targetContext).reports().single().results.single().value!!,0.0)
 }
}
