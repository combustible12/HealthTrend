package com.combustible12.healthtrend

import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
class TrendPalettePersistenceTest {
 @Test fun paletteRegistryReopensWithoutChangingAnyReport(){
  val context=RuntimeEnvironment.getApplication()
  val prefs=context.getSharedPreferences("healthtrend_store_v1",0)
  prefs.edit().clear().putString("reports","[]").commit()
  val store=HealthStore(context)
  val draft=pastedReportDraft("A\n血常规\n2026-10-23\nWBC 白细胞计数 1.79 10^9/L 3.5-9.5",store=store)
  assertTrue(reportValidationProblems(draft).toString(),draft.valid())
  store.saveReport(materializeReportDraft(draft,store))
  val original=store.reports().single()
  val before=prefs.getString("reports",null)
  val keys=HealthStore(context).trendHospitalPaletteKeys(listOf("A","B"))
  assertEquals(keys,HealthStore(context).trendHospitalPaletteKeys(listOf("B","A")))
  assertEquals(keys,HealthStore(context).trendHospitalPaletteKeys(listOf("0")).take(2))
  assertEquals(before,prefs.getString("reports",null))
  val reopened=HealthStore(context).reports().single()
  assertEquals(original,reopened)
  assertEquals(1.79,reopened.results.single().trendPlotValue()!!,0.0)
  assertTrue(trendHasHistoricalAbnormal(reopened.results))
 }
}
