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
 @Test fun sameDayDifferentTimesRemainSeparateAndCourseReusesTheSameTimedPoint(){
  val store=HealthStore(context)
  val morning=LocalDate.of(2026,10,26).atTime(8,15).atZone(zone).toInstant().toEpochMilli()
  val evening=LocalDate.of(2026,10,26).atTime(20,45).atZone(zone).toInstant().toEpochMilli()
  store.saveWeight(WeightRecord(measuredAtEpochMillis=morning,kilograms=61.2))
  store.saveWeight(WeightRecord(measuredAtEpochMillis=evening,kilograms=60.8))
  store.setWeightForCourse("course-1",evening,60.5)
  assertEquals(2,store.weightRecords().size)
  assertEquals(60.5,store.weightRecordForCourse("course-1")!!.kilograms,0.0)
  store.setWeightForCourse("course-1",morning,60.3)
  assertEquals(2,store.weightRecords().size)
  assertEquals(60.3,store.weightRecordForCourse("course-1")!!.kilograms,0.0)
  assertEquals(60.5,store.weightRecords().single{it.measuredAtEpochMillis==evening}.kilograms,0.0)
  store.setWeightForCourse("course-1",morning,null)
  assertEquals(2,store.weightRecords().size)
 }
 @Test fun courseOwnedWeightIsRemovedWhenCourseWeightIsCleared(){
  val store=HealthStore(context)
  val at=LocalDate.of(2026,10,26).atTime(12,5).atZone(zone).toInstant().toEpochMilli()
  store.setWeightForCourse("course-only",at,59.4)
  assertEquals(1,store.weightRecords().size)
  store.setWeightForCourse("course-only",at,null)
  assertTrue(store.weightRecords().isEmpty())
 }
 @Test fun heightIsSnapshottedPerWeightAndProfileChangesDoNotRewriteHistory(){
  val store=HealthStore(context)
  val first=LocalDate.of(2026,10,26).atTime(8,15).atZone(zone).toInstant().toEpochMilli()
  val second=LocalDate.of(2026,10,27).atTime(8,15).atZone(zone).toInstant().toEpochMilli()
  store.saveWeight(WeightRecord(measuredAtEpochMillis=first,kilograms=49.4))
  assertNull(store.weightRecords().single().heightCmAtMeasurement)

  store.savePatientProfile(PatientProfile(heightCm="152"))
  assertEquals(152.0,store.weightRecords().single().heightCmAtMeasurement!!,0.0)
  assertEquals("1.439",bodySurfaceAreaText(store.weightRecords().single().heightCmAtMeasurement,49.4))

  store.savePatientProfile(PatientProfile(heightCm="160"))
  assertEquals(152.0,store.weightRecords().single().heightCmAtMeasurement!!,0.0)
  store.saveWeight(WeightRecord(measuredAtEpochMillis=second,kilograms=49.4))
  assertEquals(152.0,store.weightRecords().single{it.measuredAtEpochMillis==first}.heightCmAtMeasurement!!,0.0)
  assertEquals(160.0,store.weightRecords().single{it.measuredAtEpochMillis==second}.heightCmAtMeasurement!!,0.0)
 }
}
