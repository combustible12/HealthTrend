package com.combustible12.healthtrend

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TrendPalettePersistenceTest {
 @Test fun paletteRegistryReopensWithoutChangingAnyReport(){
  val context=ApplicationProvider.getApplicationContext<android.content.Context>()
  val prefs=context.getSharedPreferences("healthtrend_store_v1",0)
  prefs.edit().clear().putString("reports","[]").commit()
  val before=prefs.getString("reports",null)
  val keys=HealthStore(context).trendHospitalPaletteKeys(listOf("A","B"))
  assertEquals(keys,HealthStore(context).trendHospitalPaletteKeys(listOf("B","A")))
  assertEquals(keys,HealthStore(context).trendHospitalPaletteKeys(listOf("0")).take(2))
  assertEquals(before,prefs.getString("reports",null))
 }
}
