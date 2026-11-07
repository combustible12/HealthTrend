package com.combustible12.healthtrend

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*

class CourseNavigationTest {
 @get:Rule val compose=createComposeRule()
 private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
 @Before fun clear(){context.getSharedPreferences("healthtrend_store_v1",Context.MODE_PRIVATE).edit().clear().commit()}
 @Test fun processControlShowsCurrentStageAndPlannedCounts(){
  val store=HealthStore(context)
  store.saveCourseProcessSettings(CourseProcessSettings(CourseProcessStage.OBSERVATION,false,false,27,5))
  compose.setContent{MaterialTheme{CourseRecordsPage(androidx.compose.ui.Modifier,emptyList(),store,{},{},{},{_,_->})}}
  compose.onNodeWithText("观察").assertIsDisplayed()
  compose.onNodeWithText("化疗天数关").assertIsDisplayed()
  compose.onNodeWithContentDescription("打开进程总控").performClick()
  compose.onNodeWithText("外照计划次数").assertIsDisplayed()
  compose.onNodeWithText("后装计划次数").assertIsDisplayed()
 }
 @Test fun datedBrachytherapyReminderAppearsOnMatchingCourseCard(){
  val at=java.time.LocalDate.of(2031,5,3).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
  val record=CourseRecord(date=at,title="2化35",titleAuto=false,phase="观察")
  val reminder=TreatmentReminder(radiationNumber=0,text="第二次后装",triggerDate="2031-05-03",purpose=ReminderPurpose.BRACHYTHERAPY,brachytherapyNumber=2)
  compose.setContent{MaterialTheme{CourseRecordsPage(androidx.compose.ui.Modifier,listOf(record),HealthStore(context),{},{},{},{_,_->},reminders=listOf(reminder))}}
  compose.onNodeWithText("2化35-后装2",substring=true).assertIsDisplayed()
  assertTrue(compose.onAllNodesWithText("后装").fetchSemanticsNodes().isNotEmpty())
 }
 @Test fun completingPortMaintenanceRecordsTodayEvenWhenOverdue(){
  val store = HealthStore(context)
  val today = java.time.LocalDate.now()
  store.savePortMaintenanceLastDate(today.minusDays(35).toString())
  compose.setContent{MaterialTheme{CourseRecordsPage(androidx.compose.ui.Modifier,emptyList(),store,{},{},{},{_,_->})}}
  compose.onNodeWithText("已维护").performClick()
  compose.runOnIdle{assertEquals(today.toString(), store.portMaintenanceLastDate())}
 }
 @Test fun portHistoryOpensAndShowsPersistedIntervals(){
  val store=HealthStore(context)
  store.recordPortDate(java.time.LocalDate.parse("2020-01-01"),true)
  store.savePortMaintenanceLastDate("2020-01-29")
  store.savePortMaintenanceLastDate("2020-02-25")
  compose.setContent{MaterialTheme{CourseRecordsPage(androidx.compose.ui.Modifier,emptyList(),HealthStore(context),{},{},{},{_,_->})}}
  compose.onNodeWithContentDescription("港维护记录").performClick()
  for ((label, date, days) in listOf(Triple("冲1","2020-01-29","28天"), Triple("冲2","2020-02-25","27天"))) {
   val cells=listOf(label,date,days).map { compose.onNodeWithText(it,useUnmergedTree=true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot }
   cells.zipWithNext().forEach { (left,right) ->
    assertTrue(left.right <= right.left)
    assertEquals(left.center.y,right.center.y,1f)
   }
  }
 }
 @Test fun entireSymptomModuleOpensAtEditorViewportCenter(){
  val store=HealthStore(context)
  val record=CourseRecord(symptomText="测试")
  compose.setContent{MaterialTheme{CourseRecordEditor(record,store,{},{_,_,_,_->},{},{_,_->},initialSection="symptom")}}
  compose.onNodeWithText("症状").assertIsDisplayed()
  compose.onNodeWithText("相册添加症状图片").assertIsDisplayed()
  val viewport=compose.onNode(hasScrollAction() and hasAnyDescendant(hasText("症状")))
  assertEquals(viewport.fetchSemanticsNode().boundsInRoot.center.y,compose.onNodeWithTag("course-editor-section-symptom").fetchSemanticsNode().boundsInRoot.center.y,compose.density.density*2)
 }
 @Test fun injectionIconAlignsWithCardHeading(){
  val record=CourseRecord(title="对齐",titleAuto=false,growthFactorInjection=true)
  compose.setContent{MaterialTheme{CourseRecordsPage(androidx.compose.ui.Modifier,listOf(record),HealthStore(context),{},{},{},{_,_->})}}
  val title=compose.onNodeWithText(" · 对齐",substring=true,useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
  val icon=compose.onNodeWithContentDescription("当天已打升白针").fetchSemanticsNode().boundsInRoot
  assertEquals(title.center.y,icon.center.y,compose.density.density)
 }
 @Test fun portMaintenanceMatchesCourseCardWidthAndFilterHeight(){
  val initial=CourseRecord(title="布局核对")
  val store=HealthStore(context)
  store.saveCoursePhases(listOf("放疗日"))
  compose.setContent{MaterialTheme{CourseRecordsPage(androidx.compose.ui.Modifier,listOf(initial),store,{},{},{},{_,_->})}}
  compose.onNodeWithContentDescription("按日期查看病程记录").assertIsDisplayed()
  val bar=compose.onNodeWithContentDescription("港维护色块").fetchSemanticsNode().boundsInRoot
  val card=compose.onNodeWithContentDescription("病程卡片").fetchSemanticsNode().boundsInRoot
  val filter=compose.onNode(hasText("放疗日") and hasClickAction()).fetchSemanticsNode().boundsInRoot
  assertEquals(card.left,bar.left,1f)
  assertEquals(card.right,bar.right,1f)
  assertEquals(filter.height,bar.height,1f)
  compose.onNodeWithText("港维护").assertIsDisplayed()
  compose.onNodeWithText("已维护").assertIsDisplayed()
 }
 @Test fun courseCardShowsFourEqualTimePulseAndPressureBlocks(){
  val initial=CourseRecord(title="四块血压",pressureTime="07:58",systolic=142,diastolic=96,pulse=94)
  compose.setContent{MaterialTheme{CourseRecordsPage(androidx.compose.ui.Modifier,listOf(initial),HealthStore(context),{},{},{},{_,_->})}}
  listOf("时间","脉搏","高压","低压").forEach{compose.onNodeWithText(it).assertIsDisplayed()}
  val blocks=listOf("07:58","94","142","96").map{compose.onNodeWithText(it,useUnmergedTree=true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot}
  blocks.zipWithNext().forEach{(left,right)->assertTrue(right.left>left.right);assertEquals(left.top,right.top,1f)}
  compose.onNodeWithText("142/96").assertDoesNotExist()
  compose.onNodeWithText("脉搏 94 次/分").assertDoesNotExist()
 }
 @Test fun pressureSummaryAddButtonAndSymptomsHaveCompactGaps(){
  val initial=CourseRecord(title="血压排版核对",pressureTime="08:30",systolic=118,diastolic=76,pulse=94)
  val store=HealthStore(context);store.saveCourseRecord(initial)
  var saved:CourseRecord?=null
  compose.setContent{MaterialTheme{CourseRecordEditor(initial,store,{},{record,_,_,_->saved=record},{},{_,_->})}}
  val summary=compose.onNodeWithText("脉搏 94 次/分   血压 118/76 mmHg")
  summary.performScrollTo().assertIsDisplayed()
  val add=compose.onNode(hasClickAction() and hasText("添加一组血压-脉搏"))
  val weight=compose.onNodeWithContentDescription("体重模块标题")
  val weightInput=compose.onNodeWithContentDescription("体重输入色块")
  val symptoms=compose.onNodeWithText("症状")
  symptoms.performScrollTo()
  val scale=compose.density.density
  val summaryBounds=summary.fetchSemanticsNode().boundsInRoot
  val addBounds=add.fetchSemanticsNode().boundsInRoot
  val weightBounds=weight.fetchSemanticsNode().boundsInRoot
  val weightInputBounds=weightInput.fetchSemanticsNode().boundsInRoot
  val symptomsBounds=symptoms.fetchSemanticsNode().boundsInRoot
  assertEquals(7f*scale,addBounds.top-summaryBounds.bottom,scale)
  assertEquals(32f*scale,addBounds.height,scale)
  assertTrue(weightBounds.top>addBounds.bottom)
  assertTrue(weightInputBounds.top>weightBounds.bottom)
  assertTrue(symptomsBounds.top>weightInputBounds.bottom)
  compose.onNodeWithText("60–100").assertExists()
  compose.onNodeWithText("<90\n<60").assertExists()
  compose.onNodeWithText("高压/低压（mmHg），按较高风险档位着色").assertDoesNotExist()
  compose.onNodeWithText("保存记录").assertDoesNotExist()
  compose.runOnIdle{assertNull(saved)}
 }
 @Test fun additionalPressureCanBeAddedAndRemovedWithoutChangingFirstReading(){
  val initial=CourseRecord(title="多组血压",pressureTime="08:30",systolic=118,diastolic=76,pulse=94)
  val store=HealthStore(context);store.saveCourseRecord(initial)
  var saved:CourseRecord?=null
  compose.setContent{MaterialTheme{CourseRecordEditor(initial,store,{},{record,_,_,_->saved=record},{},{_,_->})}}
  compose.onNodeWithText("添加一组血压-脉搏").performScrollTo().performClick()
  compose.mainClock.advanceTimeBy(500)
  compose.onNodeWithContentDescription("删除这组血压").performScrollTo().performClick()
  compose.onNodeWithContentDescription("删除这组血压").assertDoesNotExist()
  compose.mainClock.advanceTimeBy(500)
  compose.runOnIdle{assertEquals(initial,saved)}
 }
 @Test fun retrySavesTheCurrentInputAfterAWriteFailure(){
  val store=HealthStore(context)
  val initial=CourseRecord(title="保存重试")
  store.saveCourseRecord(initial)
  var fail=true
  compose.setContent{MaterialTheme{CourseRecordEditor(initial,store,{},{record,_,_,_->
   if(fail)throw IllegalStateException("模拟写入失败")
   store.saveCourseRecord(record)
  },{},{_,_->})}}
  compose.onNodeWithText("升白针").performScrollTo().performClick()
  compose.mainClock.advanceTimeBy(500)
  compose.onNodeWithText("保存失败").assertIsDisplayed()
  compose.runOnIdle{fail=false}
  compose.onNodeWithText("重试").performClick()
  compose.onNodeWithText("已保存").assertIsDisplayed()
  compose.runOnIdle{assertTrue(store.courseRecords().single().growthFactorInjection)}
 }
 @Test fun autoSaveKeepsEditorOpenAndBackPersistsTheLatestChange(){
  val store=HealthStore(context)
  val initial=CourseRecord(title="自动保存核对",titleAuto=false)
  store.saveCourseRecord(initial)
  compose.setContent{MaterialTheme{App()}}
  compose.onNodeWithText("自动保存核对",substring=true).performClick()
  compose.onNodeWithText("升白针").performScrollTo().performClick()
  compose.mainClock.advanceTimeBy(500)
  compose.onNodeWithText("编辑病程记录").assertIsDisplayed()
  compose.runOnIdle{assertTrue(store.courseRecords().single{it.id==initial.id}.growthFactorInjection)}
  compose.onNodeWithText("升白针").performScrollTo().performClick()
  androidx.test.espresso.Espresso.pressBack()
  compose.onNodeWithText("病程记录").assertIsDisplayed()
  compose.runOnIdle{assertFalse(store.courseRecords().single{it.id==initial.id}.growthFactorInjection)}
 }
 @Test fun courseEditorSavesExplicitGrowthFactorInjectionMarker(){
  val initial=CourseRecord(title="升白针标记")
  val store=HealthStore(context);store.saveCourseRecord(initial)
  var saved:CourseRecord?=null
  compose.setContent{MaterialTheme{CourseRecordEditor(initial,store,{},{record,_,_,_->saved=record},{},{_,_->})}}
  compose.onNodeWithText("升白针").performScrollTo().assertIsDisplayed().performClick()
  compose.mainClock.advanceTimeBy(500)
  compose.runOnIdle{assertTrue(saved?.growthFactorInjection==true)}
 }
 @Test fun dietEditorAppearsBetweenMedicineAndNotesWithPhotoActions(){
  val initial=CourseRecord(title="饮食条目")
  val store=HealthStore(context);store.saveCourseRecord(initial)
  compose.setContent{MaterialTheme{CourseRecordEditor(initial,store,{},{_,_,_,_->},{},{_,_->})}}
  compose.onNodeWithText("饮食").performScrollTo().assertIsDisplayed()
  compose.onNodeWithText("相册添加饮食图片").assertIsDisplayed()
  compose.onNodeWithText("备注").performScrollTo().assertIsDisplayed()
  val medicine=compose.onNodeWithText("药品 / 取药").fetchSemanticsNode().boundsInRoot
  val diet=compose.onNodeWithText("饮食").fetchSemanticsNode().boundsInRoot
  val notes=compose.onNodeWithText("备注").fetchSemanticsNode().boundsInRoot
  assertTrue(medicine.top<diet.top)
  assertTrue(diet.top<notes.top)
 }
 @Test fun defaultDietTemplateSeedsOnlyPristineEditorAndTemplateButtonsAreAvailable(){
  val store=HealthStore(context)
  store.saveCourseTextTemplate(CourseTextTemplate(sectionId="diet",name="早中晚",text="早：\n午：\n晚："),true)
  compose.setContent{MaterialTheme{CourseRecordEditor(CourseRecord(title=""),store,{},{_,_,_,_->},{},{_,_->})}}
  compose.onNodeWithText("早：\n午：\n晚：").performScrollTo().assertIsDisplayed()
  assertTrue(compose.onAllNodesWithText("模板").fetchSemanticsNodes().size>=6)
 }
 @Test fun customSectionCanBeAddedInsideEditorWithTextAndPhotoActions(){
  val store=HealthStore(context)
  compose.setContent{MaterialTheme{CourseRecordEditor(CourseRecord(title=""),store,{},{_,_,_,_->},{},{_,_->})}}
  compose.onNodeWithText("添加 / 管理板块").performScrollTo().performClick()
  compose.onNodeWithText("输入新板块标题").performTextInput("睡眠")
  compose.onNodeWithText("添加").performClick()
  compose.onNodeWithText("完成").performClick()
  compose.onNodeWithText("睡眠").performScrollTo().assertIsDisplayed()
  compose.onNodeWithText("相册添加睡眠图片").assertIsDisplayed()
 }
 @Test fun growthFactorInjectionFilterShowsOnlyExplicitlyMarkedCards(){
  val marked=CourseRecord(title="已标记升白针",phase="放疗",growthFactorInjection=true)
  val unmarked=CourseRecord(title="未标记普通卡",phase="观察",date=marked.date-86_400_000L)
  compose.setContent{MaterialTheme{CourseRecordsPage(androidx.compose.ui.Modifier,listOf(marked,unmarked),HealthStore(context),{},{},{},{_,_->})}}
  compose.onNodeWithText("已标记升白针",substring=true).assertExists()
  compose.onNodeWithText("未标记普通卡",substring=true).assertExists()
  val injectionFilter=compose.onNodeWithContentDescription("筛选升白针记录")
  assertTrue(injectionFilter.fetchSemanticsNode().boundsInRoot.left<compose.onNodeWithText("全部").fetchSemanticsNode().boundsInRoot.left)
  injectionFilter.performClick()
  compose.onNodeWithText("已标记升白针",substring=true).assertExists()
  compose.onNodeWithText("未标记普通卡",substring=true).assertDoesNotExist()
 }
 @Test fun courseFiltersExpandWithoutHorizontalScrollingAndShowManagementBelow(){
  val store=HealthStore(context)
  store.saveCoursePhases(listOf("化疗","放疗","观察","住院","复查","恢复"))
  compose.setContent{MaterialTheme{CourseRecordsPage(androidx.compose.ui.Modifier,listOf(CourseRecord(title="展开筛选")),store,{},{},{},{_,_->})}}
  compose.onNodeWithContentDescription("病程分类筛选").assert(hasScrollAction().not())
  compose.onNodeWithText("管理").assertDoesNotExist()
  compose.onNodeWithContentDescription("展开全部分类").performClick()
  listOf("化疗","放疗","观察","住院","复查","恢复").forEach{compose.onNodeWithText(it).assertIsDisplayed()}
  val filterBlocks=listOf(
   compose.onNode(hasContentDescription("筛选升白针记录") and hasClickAction()),
   compose.onNode(hasText("全部") and hasClickAction()),
   compose.onNode(hasText("化疗") and hasClickAction()),
   compose.onNode(hasText("放疗") and hasClickAction()),
   compose.onNode(hasText("观察") and hasClickAction()),
   compose.onNode(hasText("住院") and hasClickAction()),
   compose.onNode(hasText("复查") and hasClickAction()),
   compose.onNode(hasText("恢复") and hasClickAction())
  ).map{it.fetchSemanticsNode().boundsInRoot}
  filterBlocks.drop(1).forEach{bounds->assertEquals(filterBlocks.first().width,bounds.width,1f);assertEquals(filterBlocks.first().height,bounds.height,1f)}
  val scale=compose.density.density
  assertEquals(32f*scale,filterBlocks.first().height,scale)
  assertEquals(8f*scale,filterBlocks[4].top-filterBlocks[0].bottom,scale)
  val lastLabel=compose.onNodeWithText("恢复").fetchSemanticsNode().boundsInRoot
  val management=compose.onNodeWithText("管理").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
  assertTrue(management.top>=lastLabel.bottom)
  assertTrue(management.top-lastLabel.bottom<=4f*scale)
  compose.onNodeWithText("管理").performClick()
  compose.onNodeWithText("管理分类").assertIsDisplayed()
 }
 @Test fun courseEditorWeightIsAboveSymptomsAndSavesAsKilograms(){
  val initial=CourseRecord(title="体重记录")
  val store=HealthStore(context);store.saveCourseRecord(initial);store.savePatientProfile(PatientProfile(heightCm="165"))
  var savedWeight:Double?=null;var savedAt:Long?=null;var savedHeight:Double?=null;var openedTrend=0
  compose.setContent{MaterialTheme{CourseRecordEditor(initial,store,{},{_,weight,at,height->savedWeight=weight;savedAt=at;savedHeight=height},{},{_,_->},{openedTrend++})}}
  val weightTitle=compose.onNodeWithContentDescription("体重模块标题").performScrollTo().assertIsDisplayed()
  val symptoms=compose.onNodeWithText("症状").performScrollTo().assertIsDisplayed()
  assertTrue(weightTitle.fetchSemanticsNode().boundsInRoot.top<symptoms.fetchSemanticsNode().boundsInRoot.top)
  compose.onNode(hasSetTextAction() and hasAnyAncestor(hasContentDescription("体重输入色块")),useUnmergedTree=true).performTextInput("61.5")
  compose.onAllNodesWithText("时间").assertCountEquals(2)
  val timeBlock=compose.onNodeWithContentDescription("体重记录时间").fetchSemanticsNode().boundsInRoot
  val weightBlock=compose.onNodeWithContentDescription("体重输入色块").fetchSemanticsNode().boundsInRoot
  val bsaBlock=compose.onNodeWithContentDescription("体重编辑体表面积色块").fetchSemanticsNode().boundsInRoot
  val bmiBlock=compose.onNodeWithContentDescription("体重编辑BMI色块").fetchSemanticsNode().boundsInRoot
  assertEquals(timeBlock.height,weightBlock.height,1f);assertEquals(weightBlock.height,bsaBlock.height,1f);assertEquals(bsaBlock.height,bmiBlock.height,1f)
  assertEquals(timeBlock.width,weightBlock.width,1f);assertEquals(weightBlock.width,bsaBlock.width,1f);assertEquals(bsaBlock.width,bmiBlock.width,1f)
  compose.onNodeWithText("体重 kg").assertDoesNotExist()
  compose.onNodeWithText("体表 m²").assertIsDisplayed()
  compose.onNodeWithText("1.676").assertIsDisplayed()
  compose.onNodeWithText("BMI").assertIsDisplayed()
  compose.onNodeWithText("22.6").assertIsDisplayed()
  weightTitle.performClick()
  compose.runOnIdle{assertEquals(1,openedTrend)}
  compose.mainClock.advanceTimeBy(500)
  compose.runOnIdle{assertEquals(61.5,savedWeight!!,0.0);assertNotNull(savedAt);assertEquals(165.0,savedHeight!!,0.0)}
 }
 @Test fun savedWeightAppearsOnMatchingCourseCardAndOpensTrend(){
  val at=java.time.LocalDate.of(2026,10,26).atTime(8,20).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
  val initial=CourseRecord(date=at,title="当天病程")
  val store=HealthStore(context);store.savePatientProfile(PatientProfile(heightCm="165"));store.setWeightForCourse(initial.id,at,62.3)
  var openedTrend=0
  compose.setContent{MaterialTheme{CourseRecordsPage(androidx.compose.ui.Modifier,listOf(initial),store,{},{},{},{_,_->},{openedTrend++})}}
  compose.onNodeWithText("62.3").assertIsDisplayed()
  compose.onNodeWithText("kg").assertDoesNotExist()
  compose.onNodeWithText("体表 m²").assertIsDisplayed()
  compose.onNodeWithText("1.685").assertIsDisplayed()
  compose.onNodeWithText("BMI").assertIsDisplayed()
  compose.onNodeWithText("22.9").assertIsDisplayed()
  val timeBlock=compose.onNodeWithContentDescription("体重卡片时间色块",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
  val weightBlock=compose.onNodeWithContentDescription("体重卡片数值色块",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
  val bsaBlock=compose.onNodeWithContentDescription("体重卡片体表面积色块",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
  val bmiBlock=compose.onNodeWithContentDescription("体重卡片BMI色块",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
  assertEquals(timeBlock.width,weightBlock.width,1f);assertEquals(weightBlock.width,bsaBlock.width,1f);assertEquals(bsaBlock.width,bmiBlock.width,1f)
  assertEquals(timeBlock.height,weightBlock.height,1f);assertEquals(weightBlock.height,bsaBlock.height,1f);assertEquals(bsaBlock.height,bmiBlock.height,1f)
  compose.onNodeWithContentDescription("打开体重趋势").performClick()
  compose.runOnIdle{assertEquals(1,openedTrend)}
 }
 @Test fun editorWeightTitleNavigatesThroughAppToExistingWeightTrend(){
  compose.setContent{MaterialTheme{App()}}
  compose.onNodeWithContentDescription("新增病程记录").performClick()
  compose.onNodeWithContentDescription("体重模块标题").performScrollTo().performClick()
  compose.onNodeWithText("+ 记录体重").assertIsDisplayed()
  compose.onNodeWithText("暂无体重记录").assertIsDisplayed()
 }
 @Test fun unopenedCardShowsDietTemplateAndOpensDietEditor(){
  val store=HealthStore(context)
  val meals="早：\n午：\n晚："
  store.saveCourseTextTemplate(CourseTextTemplate(sectionId="diet",name="三餐",text=meals),true)
  compose.setContent{MaterialTheme{App()}}
  compose.onNodeWithText(meals).performClick()
  compose.onNodeWithText("饮食").assertIsDisplayed()
  compose.onNodeWithText("编辑病程记录").assertIsDisplayed()
 }
 @Test fun startupShowsCourseAndAddButtonSharesTitleRow(){
  compose.setContent{MaterialTheme{App()}}
  compose.onNodeWithText("病程记录").assertIsDisplayed()
  val title=compose.onNodeWithText("病程记录").fetchSemanticsNode().boundsInRoot
  val add=compose.onNodeWithContentDescription("新增病程记录").fetchSemanticsNode().boundsInRoot
  assertTrue(add.left>=title.right)
  assertTrue(add.top<title.bottom&&add.bottom>title.top)
  compose.onNodeWithText("导入").performClick()
  compose.onNodeWithText("粘贴报告数据").assertIsDisplayed()
  compose.onNodeWithText("手动录入").assertIsDisplayed()
  compose.onNodeWithText("健康记录").assertDoesNotExist()
  compose.onNodeWithText("最近").assertDoesNotExist()
 }
 @Test fun importPageTreatmentReminderUsesRadiotherapyCountAndCompletes(){
  val store=HealthStore(context)
  store.saveCourseRecord(CourseRecord(title="2化12-放2",titleAuto=false))
  store.saveTreatmentReminder(TreatmentReminder(radiationNumber=2,text="找主任做检查"))
  compose.setContent{MaterialTheme{App()}}
  compose.onNodeWithText("放2提醒").assertIsDisplayed()
  compose.onNodeWithText("找主任做检查").assertIsDisplayed()
  compose.onNodeWithText("已完成").performClick()
  compose.onNodeWithText("放2提醒").assertDoesNotExist()
  compose.runOnIdle{assertNotNull(HealthStore(context).treatmentReminders().single().completedAt)}
 }
 @Test fun categoriesCanBeAddedDeletedAndUsedInEditor(){
  compose.setContent{MaterialTheme{App()}}
  compose.onNodeWithText("管理").performClick()
  compose.onNodeWithText("新分类名称").performTextInput("放疗日")
  compose.onNodeWithText("添加分类").performClick()
  compose.onNodeWithContentDescription("删除分类：化疗日").performClick()
  compose.onNodeWithText("完成").performClick()
  compose.runOnIdle{assertTrue(HealthStore(context).coursePhases().contains("放疗日"));assertFalse(HealthStore(context).coursePhases().contains("化疗日"))}
  compose.onNodeWithText("全部").assertIsDisplayed()
  compose.onNodeWithContentDescription("新增病程记录").performClick()
  compose.onNodeWithText("放疗日").performScrollTo().performClick()
  compose.onNodeWithText("化疗日").assertDoesNotExist()
 }
 @Test fun categoryLabelCanBeRenamedWithoutChangingStoredTreatmentIdentity(){
  val store=HealthStore(context)
  store.saveCourseRecord(CourseRecord(title="分类改名核对",phase="放疗",titleAuto=false))
  compose.setContent{MaterialTheme{App()}}
  compose.onNodeWithText("管理").performClick()
  compose.onNodeWithContentDescription("重命名分类：放疗").performClick()
  compose.onNodeWithText("分类名称").assertIsDisplayed()
  compose.onAllNodes(hasSetTextAction()).onLast().performTextReplacement("照射")
  compose.onNodeWithText("保存").performClick()
  compose.onNodeWithText("完成").performClick()
  compose.onNodeWithText("照射",useUnmergedTree=true).assertIsDisplayed()
  compose.runOnIdle{
   assertEquals("照射",HealthStore(context).coursePhaseLabel("放疗"))
   assertEquals("放疗",HealthStore(context).courseRecords().first{it.title=="分类改名核对"}.phase)
  }
 }
 @Test fun categoriesSupportRepeatedDragBeforeSaving(){
  compose.setContent{MaterialTheme{App()}}
  compose.onNodeWithText("管理").performClick()
  // Short drags cross the adjacent midpoint after touch slop, twice before saving.
  repeat(2){
   compose.onNodeWithContentDescription("拖动排序：化疗日").performTouchInput{
    swipe(center,center+androidx.compose.ui.geometry.Offset(0f,60f*compose.density.density),500)
   }
   compose.waitForIdle()
  }
  compose.onNodeWithText("完成").performClick()
  compose.runOnIdle{assertEquals(listOf("恢复期","观察","化疗日","其他"),HealthStore(context).coursePhases())}
  compose.onNodeWithText("管理").performClick()
  val moved=compose.onNodeWithContentDescription("拖动排序：化疗日").fetchSemanticsNode().boundsInRoot
  val above=compose.onNodeWithContentDescription("拖动排序：观察").fetchSemanticsNode().boundsInRoot
  assertTrue(moved.top>above.top)
 }
 @Test fun photoButtonsStayOnOneLineWithLargeTextAndBothActionsWork(){
  var gallery=0;var camera=0
  compose.setContent{CompositionLocalProvider(LocalDensity provides Density(2.75f,1.3f)){
   MaterialTheme{PhotoInputButtons(PhotoInput({gallery++},{camera++}),galleryLabel="相册导入")}
  }}
  compose.onNodeWithText("相册导入").assertIsDisplayed().performClick()
  compose.onNodeWithText("直接拍照").assertIsDisplayed().performClick()
  compose.runOnIdle{assertEquals(1,gallery);assertEquals(1,camera)}
  val left=compose.onNodeWithText("相册导入").fetchSemanticsNode().boundsInRoot
  val right=compose.onNodeWithText("直接拍照").fetchSemanticsNode().boundsInRoot
  assertEquals(left.top,right.top,1f)
  assertEquals(left.height,right.height,1f)
 }
}

// Midpoint equality must not trigger alternating swaps in one pointer event.
