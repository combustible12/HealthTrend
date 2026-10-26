package com.combustible12.healthtrend

import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
class WeightCoursePersistenceTest {
 private val context get()=RuntimeEnvironment.getApplication()
 private val zone=ZoneId.systemDefault()
 @Before fun clear(){context.getSharedPreferences("healthtrend_store_v1",Context.MODE_PRIVATE).edit().clear().commit()}
 @Test fun courseAndTrendEditsShareOneWeightPerLocalDay(){
  val store=HealthStore(context)
  val morning=LocalDate.of(2026,10,26).atTime(8,15).atZone(zone).toInstant().toEpochMilli()
  val evening=LocalDate.of(2026,10,26).atTime(20,45).atZone(zone).toInstant().toEpochMilli()
  store.saveWeight(WeightRecord(measuredAtEpochMillis=morning,kilograms=61.2))
  val originalId=store.weightRecords().single().id
  store.setWeightForDay(evening,60.8)
  assertEquals(1,store.weightRecords().size)
  assertEquals(originalId,store.weightRecords().single().id)
  assertEquals(60.8,store.weightRecordOn(morning)!!.kilograms,0.0)
  store.setWeightForDay(morning,null)
  assertTrue(store.weightRecords().isEmpty())
 }
}
