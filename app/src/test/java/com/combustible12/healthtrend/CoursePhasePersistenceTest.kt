package com.combustible12.healthtrend

import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
class CoursePhasePersistenceTest {
 private val context get()=RuntimeEnvironment.getApplication()
 @Before fun clear(){context.getSharedPreferences("healthtrend_store_v1",Context.MODE_PRIVATE).edit().clear().commit()}
 @Test fun customPhasesPersistAndEmptyListDoesNotRestoreDeletedDefaults(){
  val store=HealthStore(context)
  assertEquals(listOf("化疗日","恢复期","观察","其他"),store.coursePhases())
  store.saveCoursePhases(listOf("放疗日"," 恢复期 ","放疗日","全部",""))
  assertEquals(listOf("放疗日","恢复期"),HealthStore(context).coursePhases())
  store.saveCoursePhases(emptyList())
  assertTrue(HealthStore(context).coursePhases().isEmpty())
 }
 @Test fun removingCategoryPreservesEveryStoredRecordField(){
  val store=HealthStore(context)
  val record=CourseRecord(title="合成记录",phase="化疗日",noteText="原始备注")
  store.saveCourseRecord(record)
  val prefs=context.getSharedPreferences("healthtrend_store_v1",Context.MODE_PRIVATE)
  val original=prefs.getString("course_records",null)
  store.saveCoursePhases(listOf("放疗日"))
  assertEquals(original,prefs.getString("course_records",null))
  assertEquals("化疗日",HealthStore(context).courseRecords().single().phase)
 }
}
