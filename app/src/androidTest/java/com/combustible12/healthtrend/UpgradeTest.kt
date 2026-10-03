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
  // Upgrading an installation with no patient profile must not synthesize demographics or disturb clinical data.
  assertEquals(PatientProfile(),store.patientProfile())
  store.savePatientProfile(PatientProfile("患者甲","1972-05-06","女","升级后录入"))
  assertEquals("1972-05-06",HealthStore(InstrumentationRegistry.getInstrumentation().targetContext).patientProfile().birthDate)
  assertEquals("2026-09-26",dateText(report.testedAtEpochMillis).substring(0,10))
  store.updateValue(report.id,report.results.single().id,120.0)
  assertEquals(120.0,HealthStore(InstrumentationRegistry.getInstrumentation().targetContext).reports().single().results.single().value!!,0.0)
 }
}
