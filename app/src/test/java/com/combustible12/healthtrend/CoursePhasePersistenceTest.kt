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
 @Test fun editingLegacyNotePreservesStoredTitlePhaseImagesAndOtherDays(){
  val prefs=context.getSharedPreferences("healthtrend_store_v1",Context.MODE_PRIVATE)
  val first="""{"id":"legacy-a","date":86400000,"phase":"观察","title":"旧标题","titleAuto":true,"symptomText":"原症状","symptomImages":["file:///old.jpg"],"growthFactorInjection":true}"""
  val second="""{"id":"legacy-b","date":259200000,"phase":"放疗","title":"历史手写","titleAuto":false,"noteText":"第二天"}"""
  prefs.edit().putString("course_records","[$first,$second]").commit()
  val store=HealthStore(context)
  val history=store.courseRecords()
  for(original in history){
   val changed=original.copy(noteText="只改备注")
   val saved=courseEditorRecordForDate(changed,changed.date,original,history)
   assertEquals(changed,saved)
   val other=store.courseRecords().single{it.id!=original.id}
   store.saveCourseRecord(saved)
   val reopened=HealthStore(context).courseRecords()
   val actual=reopened.single{it.id==original.id}
   assertEquals(changed.copy(updatedAt=actual.updatedAt,userEdited=true),actual)
   assertEquals(other,reopened.single{it.id==other.id})
  }
 }
 @Test fun datedBrachytherapyAndFollowUpRemindersPreserveTypeAndLeaveHistoryAlone(){
  val store=HealthStore(context)
  val prefs=context.getSharedPreferences("healthtrend_store_v1",Context.MODE_PRIVATE)
  val history="""[{"id":"historical","date":1,"title":"医院"}]"""
  prefs.edit().putString("course_records",history).putString("treatment_reminders","""[{"id":"old","radiationNumber":3,"text":"旧提醒"}]""").commit()
  val after=java.time.LocalDateTime.of(2031,5,3,10,0)
  for(purpose in listOf(ReminderPurpose.BRACHYTHERAPY,ReminderPurpose.FOLLOW_UP)){
   val input=TreatmentReminder(radiationNumber=0,text="检查",triggerDate="2031-05-03",triggerTime="10:00",purpose=purpose,brachytherapyNumber=if(purpose==ReminderPurpose.BRACHYTHERAPY)2 else 0)
   store.saveTreatmentReminder(input)
   val saved=HealthStore(context).treatmentReminders().single{it.id==input.id}
   assertEquals(input,saved)
   assertNull(nextDueTreatmentReminder(listOf(saved),99,now=after.minusMinutes(1)))
   assertEquals(saved,nextDueTreatmentReminder(listOf(saved),0,now=after))
   assertTrue(treatmentReminderTriggerLabel(saved).startsWith(if(purpose==ReminderPurpose.BRACHYTHERAPY)"后装2 " else "复查 "))
  }
  assertEquals(ReminderPurpose.GENERAL,store.treatmentReminders().single{it.id=="old"}.purpose)
  assertEquals(history,prefs.getString("course_records",null))
 }
 @Test fun customPhasesPersistAndEmptyListDoesNotRestoreDeletedDefaults(){
  val store=HealthStore(context)
  assertEquals(listOf("化疗日","恢复期","观察","放疗","放化","其他","复查"),store.coursePhases())
  store.saveCoursePhases(listOf("放疗日"," 恢复期 ","放疗日","全部",""))
  assertEquals(listOf("放疗日","恢复期"),HealthStore(context).coursePhases())
  store.saveCoursePhases(emptyList())
  assertTrue(HealthStore(context).coursePhases().isEmpty())
 }
 @Test fun followUpTagIsAddedOnceWithoutRewritingHistory(){
  val prefs=context.getSharedPreferences("healthtrend_store_v1",Context.MODE_PRIVATE)
  val history="""[{"id":"old","date":1,"title":"医院","phase":"观察"}]"""
  prefs.edit().putString("course_records",history).putString("course_phases","""["观察","住院"]""")
   .putBoolean("course_phase_combined_migrated",true).commit()
  val store=HealthStore(context)
  assertEquals(listOf("观察","住院","复查"),store.coursePhases())
  store.saveCoursePhases(listOf("住院","观察"))
  assertEquals(listOf("住院","观察"),HealthStore(context).coursePhases())
  assertEquals(history,prefs.getString("course_records",null))
 }
 @Test fun existingCustomFollowUpLabelKeepsItsKeyAndOrder(){
  val prefs=context.getSharedPreferences("healthtrend_store_v1",Context.MODE_PRIVATE)
  prefs.edit().putString("course_phases","""["custom","观察"]""")
   .putString("course_phase_labels","""{"custom":"复查"}""").putBoolean("course_phase_combined_migrated",true).commit()
  assertEquals(listOf("custom","观察"),HealthStore(context).coursePhases())
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
 @Test fun dietTextAndImagesPersistWhileLegacyRecordsDefaultToEmpty(){
  val store=HealthStore(context)
  val record=CourseRecord(title="饮食记录",dietText="晚饭：面和鸡蛋",dietImages=listOf("file:///diet-1.jpg","file:///diet-2.jpg"))
  store.saveCourseRecord(record)
  val restored=HealthStore(context).courseRecords().single()
  assertEquals("晚饭：面和鸡蛋",restored.dietText)
  assertEquals(listOf("file:///diet-1.jpg","file:///diet-2.jpg"),restored.dietImages)

  context.getSharedPreferences("healthtrend_store_v1",Context.MODE_PRIVATE).edit().putString("course_records","""[{"id":"legacy-diet","date":1,"phase":"观察","title":"旧病程","symptomImages":[],"checkImages":[],"treatmentImages":[],"medicineImages":[],"noteImages":[]}]""").commit()
  val legacy=HealthStore(context).courseRecords().single()
  assertEquals("",legacy.dietText)
  assertTrue(legacy.dietImages.isEmpty())
 }
 @Test fun customSectionsPersistWithoutChangingLegacyFixedFields(){
  val store=HealthStore(context)
  val definition=store.addCourseSection("睡眠")
  val record=CourseRecord(title="自定义板块",noteText="原备注",customSections=listOf(CourseSectionContent(definition.id,"睡了七小时",listOf("file:///sleep.jpg"))))
  store.saveCourseRecord(record)
  val restored=HealthStore(context).courseRecords().single()
  assertEquals("原备注",restored.noteText)
  assertEquals("睡了七小时",restored.courseSectionText(definition.id))
  assertEquals(listOf("file:///sleep.jpg"),restored.courseSectionImages(definition.id))

  context.getSharedPreferences("healthtrend_store_v1",Context.MODE_PRIVATE).edit().putString("course_records","""[{"id":"legacy-custom","date":1,"phase":"观察","title":"旧病程","symptomImages":[],"checkImages":[],"treatmentImages":[],"medicineImages":[],"dietImages":[],"noteImages":[]}]""").commit()
  assertTrue(HealthStore(context).courseRecords().single().customSections.isEmpty())
 }
 @Test fun postponementSurvivesReopeningForBothReminderTypes(){
  val deadline=java.time.LocalDateTime.of(2026,11,8,9,0)
  val at=deadline.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
  for(date in listOf("", "2026-11-07")){
   val reminder=TreatmentReminder(radiationNumber=5,text="复查",triggerDate=date,snoozedUntil=at)
   HealthStore(context).saveTreatmentReminder(reminder)
   val reopened=HealthStore(context).treatmentReminders().single{it.id==reminder.id}
   assertEquals(at,treatmentReminderTriggerAt(reopened))
   assertNull(nextDueTreatmentReminder(listOf(reopened),5,now=deadline.minusSeconds(1)))
   assertEquals(reopened,nextDueTreatmentReminder(listOf(reopened),5,now=deadline))
   assertNull(nextDueTreatmentReminder(listOf(reopened.copy(completedAt=at)),5,now=deadline))
  }
 }
 @Test fun undatedDraftSurvivesReopeningWithoutCreatingACourseCard(){
  val store=HealthStore(context)
  val draft=CourseEditorDraft(CourseRecord(date=0L,noteText="晚餐后散步"),"","61.","08:00","165")
  store.storeCourseEditorDraft("new",draft)
  val reopened=HealthStore(context)
  assertEquals(draft,reopened.courseEditorDraft("new"))
  assertTrue(reopened.courseRecords().isEmpty())
  reopened.storeCourseEditorDraft("new",null)
  assertNull(store.courseEditorDraft("new"))
 }
 @Test fun courseTextTemplatesKeepOneExplicitDefaultPerSection(){
  val store=HealthStore(context)
  val first=CourseTextTemplate(sectionId="diet",name="早中晚",text="早：\n午：\n晚：")
  val second=CourseTextTemplate(sectionId="diet",name="清淡饮食",text="清淡：")
  store.saveCourseTextTemplate(first,makeDefault=true)
  store.saveCourseTextTemplate(second,makeDefault=true)
  val restored=HealthStore(context).courseTextTemplates("diet")
  assertEquals(1,restored.count{it.isDefault})
  assertEquals(second.id,restored.single{it.isDefault}.id)
  assertEquals("清淡：",HealthStore(context).defaultCourseText("diet"))
  store.deleteCourseTextTemplate(second.id)
  assertEquals("",store.defaultCourseText("diet"))
  assertFalse(store.courseTextTemplates("diet").single().isDefault)
 }
 @Test fun newDayCapturesAllActiveTemplatesBeforeFirstEdit(){
  val store=HealthStore(context)
  val custom=store.addCourseSection("睡眠")
  val diet=CourseTextTemplate(sectionId="diet",name="三餐",text="早：\n午：\n晚：")
  store.saveCourseTextTemplate(diet,true)
  store.saveCourseTextTemplate(CourseTextTemplate(sectionId=custom.id,name="时长",text="小时："),true)
  store.ensureCourseRecordsThroughToday()
  store.saveCourseTextTemplate(diet.copy(text="新模板"),true)
  val card=HealthStore(context).courseRecords().single()
  assertTrue(card.isBlankCourseCard())
  assertTrue(store.removeOrHideCourseSection(custom.id))
  val displayed=card.withDefaultCourseTemplates()
  assertEquals(diet.text,displayed.dietText)
  assertEquals("小时：",displayed.courseSectionText(custom.id))
 }
 @Test fun oldBlankCardsAndBackfilledDaysDoNotAcquireTemplates(){
  val store=HealthStore(context)
  store.saveCourseTextTemplate(CourseTextTemplate(sectionId="diet",name="早中晚",text="早：\n午：\n晚："),makeDefault=true)
  val blank=CourseRecord(title="",titleAuto=true)
  assertEquals("",blank.withDefaultCourseTemplates().dietText)
  val zone=java.time.ZoneId.systemDefault()
  val yesterday=java.time.LocalDate.now().minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
  val backfilled=blank.copy(date=yesterday,initialSectionTemplates=defaultCourseSectionTexts(store))
  assertEquals("",backfilled.withDefaultCourseTemplates().dietText)
  assertEquals("手工补记",backfilled.copy(dietText="手工补记").withDefaultCourseTemplates().dietText)
  val capturedOnDay=backfilled.copy(createdAt=yesterday)
  assertEquals("早：\n午：\n晚：",capturedOnDay.withDefaultCourseTemplates().dietText)
  assertEquals("当天已填写",blank.copy(dietText="当天已填写").withDefaultCourseTemplates().dietText)
  assertEquals("",blank.copy(userEdited=true).withDefaultCourseTemplates().dietText)
 }
 @Test fun customSectionRenameOrderAndHistoricalRemovalAreNonDestructive(){
  val store=HealthStore(context)
  val sleep=store.addCourseSection("睡眠")
  val bowel=store.addCourseSection("排便")
  store.saveCourseSectionDefinitions(listOf(bowel,sleep.copy(title="睡眠情况")))
  assertEquals(listOf("排便","睡眠情况"),HealthStore(context).courseSectionDefinitions().map{it.title})
  store.saveCourseRecord(CourseRecord(customSections=listOf(CourseSectionContent(sleep.id,"七小时"))))
  assertTrue(store.removeOrHideCourseSection(sleep.id))
  assertTrue(store.courseSectionDefinitions().first{it.id==sleep.id}.hidden)
  assertEquals("七小时",store.courseRecords().single().courseSectionText(sleep.id))
  assertFalse(store.removeOrHideCourseSection(bowel.id))
  assertTrue(store.courseSectionDefinitions().none{it.id==bowel.id})
 }
 @Test fun exportBackupIncludesCourseSectionConfigurationAndTemplates(){
  val store=HealthStore(context);val definition=store.addCourseSection("睡眠")
  store.saveCourseTextTemplate(CourseTextTemplate(sectionId=definition.id,name="默认",text="时长："),true)
  val backup=org.json.JSONObject(store.exportBackup())
  assertEquals(4,backup.getInt("schema"))
  assertEquals("睡眠",backup.getJSONArray("courseSectionDefinitions").getJSONObject(0).getString("title"))
  assertEquals("时长：",backup.getJSONArray("courseTextTemplates").getJSONObject(0).getString("text"))
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
