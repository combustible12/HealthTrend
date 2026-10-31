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
  assertEquals(listOf("化疗日","恢复期","观察","放疗","放化","其他"),store.coursePhases())
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
 @Test fun treatmentTextAndImagesPersistWhileLegacyRecordsDefaultToEmpty(){
  val store=HealthStore(context)
  val record=CourseRecord(title="治疗记录",treatmentText="输液港换药",treatmentImages=listOf("file:///treatment-1.jpg","file:///treatment-2.jpg"))
  store.saveCourseRecord(record)
  val restored=HealthStore(context).courseRecords().single()
  assertEquals("输液港换药",restored.treatmentText)
  assertEquals(listOf("file:///treatment-1.jpg","file:///treatment-2.jpg"),restored.treatmentImages)

  context.getSharedPreferences("healthtrend_store_v1",Context.MODE_PRIVATE).edit().putString("course_records","""[{"id":"legacy-treatment","date":1,"phase":"观察","title":"旧病程","symptomImages":[],"checkImages":[],"medicineImages":[],"noteImages":[]}]""").commit()
  val legacy=HealthStore(context).courseRecords().single()
  assertEquals("",legacy.treatmentText)
  assertTrue(legacy.treatmentImages.isEmpty())
 }
 @Test fun renamingCategoryChangesOnlyItsDisplayLabelAndKeepsStableTreatmentIdentity(){
  val store=HealthStore(context)
  val record=CourseRecord(title="2化12-放2",phase="放疗",titleAuto=false,noteText="保留")
  store.saveCourseRecord(record)
  val definitions=store.coursePhaseDefinitions().map{if(it.key=="放疗")it.copy(label="照射") else it}
  store.saveCoursePhaseDefinitions(definitions)
  val restored=HealthStore(context)
  assertEquals("照射",restored.coursePhaseLabel("放疗"))
  assertEquals("放疗",restored.courseRecords().single().phase)
  assertEquals("保留",restored.courseRecords().single().noteText)
 }
 @Test fun legacyCourseReminderFieldsStayReadableForBackupCompatibility(){
  val store=HealthStore(context)
  val record=CourseRecord(title="2化12-放2",checkText="抽血化验",reminderText="抽血化验",reminderDate="2026-10-23")
  store.saveCourseRecord(record)
  val saved=HealthStore(context).courseRecords().single()
  assertEquals("抽血化验",saved.reminderText)
  assertEquals("2026-10-23",saved.reminderDate)

  context.getSharedPreferences("healthtrend_store_v1",Context.MODE_PRIVATE).edit().putString("course_records","""[{"id":"old","date":1,"phase":"观察","title":"旧病程","symptomText":"","symptomImages":[],"checkText":"旧检查","checkImages":[],"medicineText":"","medicineImages":[],"noteText":"","noteImages":[]}]""").commit()
  val legacy=HealthStore(context).courseRecords().single()
  assertEquals("",legacy.reminderText);assertEquals("",legacy.reminderDate);assertNull(legacy.reminderCompletedAt)
 }
 @Test fun treatmentReminderTriggersAtOrAfterTargetAndHonorsCompletionAndSnooze(){
  val earlier=TreatmentReminder(id="earlier",radiationNumber=2,text="较早",createdAt=1)
  val later=TreatmentReminder(id="later",radiationNumber=3,text="较晚",createdAt=2)
  assertNull(nextDueTreatmentReminder(listOf(later,earlier),1))
  assertEquals("earlier",nextDueTreatmentReminder(listOf(later,earlier),3)?.id)
  assertEquals("later",nextDueTreatmentReminder(listOf(later,earlier),3,setOf("earlier"))?.id)
  assertNull(nextDueTreatmentReminder(listOf(earlier.copy(completedAt=3),later.copy(completedAt=4)),9))
 }
 @Test fun dateReminderTriggersOnOrAfterItsDateWithoutRadiotherapy(){
  val dated=TreatmentReminder(id="dated",radiationNumber=0,text="普通日期提醒",createdAt=3,triggerDate="2026-10-30",triggerTime="09:15")
  assertNull(nextDueTreatmentReminder(listOf(dated),0,now=java.time.LocalDateTime.of(2026,10,29,18,0)))
  assertNull(nextDueTreatmentReminder(listOf(dated),0,now=java.time.LocalDateTime.of(2026,10,30,9,14)))
  assertEquals("dated",nextDueTreatmentReminder(listOf(dated),0,now=java.time.LocalDateTime.of(2026,10,30,9,15))?.id)
  assertEquals("dated",nextDueTreatmentReminder(listOf(dated),0,now=java.time.LocalDateTime.of(2026,11,1,1,0))?.id)
  assertEquals(
   java.time.ZonedDateTime.of(2026,10,30,9,15,0,0,java.time.ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli(),
   treatmentReminderTriggerAt(dated,java.time.ZoneId.of("Asia/Shanghai"))
  )
 }
 @Test fun duplicateCourseDateIsRejectedWithoutChangingExistingRecord(){
  val store=HealthStore(context)
  val first=CourseRecord(date=java.time.LocalDate.of(2026,10,27).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),title="原记录",titleAuto=false,noteText="保留")
  store.saveCourseRecord(first)
  val duplicate=CourseRecord(date=first.date,title="新记录",titleAuto=false)
  assertThrows(IllegalArgumentException::class.java){store.saveCourseRecord(duplicate)}
  assertEquals(listOf(first.id),store.courseRecords().map{it.id})
  assertEquals("保留",store.courseRecords().single().noteText)
 }
 @Test fun growthFactorInjectionPersistsAndLegacyNoteMigratesOnce(){
  val store=HealthStore(context)
  val marked=CourseRecord(title="升白针记录",growthFactorInjection=true)
  store.saveCourseRecord(marked)
  assertTrue(HealthStore(context).courseRecords().single().growthFactorInjection)

  context.getSharedPreferences("healthtrend_store_v1",Context.MODE_PRIVATE).edit().putString("course_records","""[{"id":"legacy-injection","date":2,"phase":"恢复期","title":"","titleAuto":true,"noteText":"打升白针","symptomImages":[],"checkImages":[],"medicineImages":[],"noteImages":[]}]""").commit()
  val migrated=HealthStore(context).courseRecords().single()
  assertTrue(migrated.growthFactorInjection)
  val stored=context.getSharedPreferences("healthtrend_store_v1",Context.MODE_PRIVATE).getString("course_records","").orEmpty()
  assertTrue(stored.contains("\"growthFactorInjection\":true"))
 }
 @Test fun treatmentReminderPersistsSeparatelyFromCourseRecords(){
  val store=HealthStore(context);val reminder=TreatmentReminder(radiationNumber=12,text="找主任做检查")
  store.saveTreatmentReminder(reminder)
  val dated=TreatmentReminder(radiationNumber=0,text="普通日期事项",triggerDate="2026-10-30",triggerTime="14:20")
  store.saveTreatmentReminder(dated)
  assertTrue(store.courseRecords().isEmpty())
  val restored=HealthStore(context).treatmentReminders()
  assertEquals("找主任做检查",restored.first{it.id==reminder.id}.text)
  assertEquals("2026-10-30",restored.first{it.id==dated.id}.triggerDate)
  assertEquals("14:20",restored.first{it.id==dated.id}.triggerTime)
 }
}
