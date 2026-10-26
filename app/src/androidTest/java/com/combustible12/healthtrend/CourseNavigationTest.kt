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
  var saved:CourseRecord?=null
  compose.setContent{MaterialTheme{CourseRecordEditor(initial,HealthStore(context),{},{record,_->saved=record},{},{_,_->})}}
  val summary=compose.onNodeWithText("脉搏 94 次/分   血压 118/76 mmHg")
  summary.performScrollTo().assertIsDisplayed()
  val add=compose.onNode(hasClickAction() and hasText("添加一组血压-脉搏"))
  val symptoms=compose.onNodeWithText("症状")
  symptoms.performScrollTo()
  val scale=compose.density.density
  val summaryBounds=summary.fetchSemanticsNode().boundsInRoot
  val addBounds=add.fetchSemanticsNode().boundsInRoot
  val symptomsBounds=symptoms.fetchSemanticsNode().boundsInRoot
  assertEquals(7f*scale,addBounds.top-summaryBounds.bottom,scale)
  assertEquals(32f*scale,addBounds.height,scale)
  // Heading text is vertically centered beside its 24dp icon.
  assertTrue(symptomsBounds.top-addBounds.bottom in 6f*scale..11f*scale)
  compose.onNodeWithText("60–100").assertExists()
  compose.onNodeWithText("<90\n<60").assertExists()
  compose.onNodeWithText("高压/低压（mmHg），按较高风险档位着色").assertDoesNotExist()
  compose.onNodeWithText("保存记录").performClick()
  compose.runOnIdle{assertEquals(initial,saved)}
 }
 @Test fun additionalPressureCanBeAddedAndRemovedWithoutChangingFirstReading(){
  val initial=CourseRecord(title="多组血压",pressureTime="08:30",systolic=118,diastolic=76,pulse=94)
  var saved:CourseRecord?=null
  compose.setContent{MaterialTheme{CourseRecordEditor(initial,HealthStore(context),{},{record,_->saved=record},{},{_,_->})}}
  compose.onNodeWithText("添加一组血压-脉搏").performScrollTo().performClick()
  compose.onNodeWithContentDescription("删除这组血压").performScrollTo().performClick()
  compose.onNodeWithContentDescription("删除这组血压").assertDoesNotExist()
  compose.onNodeWithText("保存记录").performClick()
  compose.runOnIdle{assertEquals(initial,saved)}
 }
 @Test fun courseEditorWeightIsAboveSymptomsAndSavesAsKilograms(){
  val initial=CourseRecord(title="体重记录")
  var savedWeight:Double?=null;var openedTrend=0
  compose.setContent{MaterialTheme{CourseRecordEditor(initial,HealthStore(context),{},{_,weight->savedWeight=weight},{},{_,_->},{openedTrend++})}}
  val weightTitle=compose.onNodeWithText("体重").performScrollTo().assertIsDisplayed()
  val symptoms=compose.onNodeWithText("症状").performScrollTo().assertIsDisplayed()
  assertTrue(weightTitle.fetchSemanticsNode().boundsInRoot.top<symptoms.fetchSemanticsNode().boundsInRoot.top)
  compose.onNodeWithContentDescription("体重输入 kg").performTextInput("61.5")
  compose.onNodeWithText("kg").assertIsDisplayed()
  weightTitle.performClick()
  compose.runOnIdle{assertEquals(1,openedTrend)}
  compose.onNodeWithText("保存记录").performClick()
  compose.runOnIdle{assertEquals(61.5,savedWeight!!,0.0)}
 }
 @Test fun savedWeightAppearsOnMatchingCourseCardAndOpensTrend(){
  val initial=CourseRecord(title="当天病程")
  val store=HealthStore(context);store.setWeightForCourse(initial.id,initial.date,62.3)
  var openedTrend=0
  compose.setContent{MaterialTheme{CourseRecordsPage(androidx.compose.ui.Modifier,listOf(initial),store,{},{},{},{_,_->},{openedTrend++})}}
  compose.onNodeWithText("62.3").assertIsDisplayed()
  compose.onNodeWithText("kg").assertIsDisplayed()
  compose.onNodeWithText("体重").performClick()
  compose.runOnIdle{assertEquals(1,openedTrend)}
 }
 @Test fun editorWeightTitleNavigatesThroughAppToExistingWeightTrend(){
  compose.setContent{MaterialTheme{App()}}
  compose.onNodeWithContentDescription("新增病程记录").performClick()
  compose.onNodeWithText("体重").performScrollTo().performClick()
  compose.onNodeWithText("+ 记录体重").assertIsDisplayed()
  compose.onNodeWithText("暂无体重记录").assertIsDisplayed()
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
 @Test fun dueReminderIsOversizedAndCompletingItPersists(){
  val today=java.time.LocalDate.now()
  val at=today.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
  HealthStore(context).saveCourseRecord(CourseRecord(date=at,title="2化12-放2",checkText="抽血化验",reminderText="抽血化验",reminderDate=today.toString()))
  compose.setContent{MaterialTheme{App()}}
  compose.onNodeWithText("今日提醒").assertIsDisplayed()
  compose.onNodeWithContentDescription("到期提醒").assertIsDisplayed()
  val card=compose.onNodeWithContentDescription("病程到期提醒大卡片").fetchSemanticsNode().boundsInRoot
  assertTrue(card.height>400f)
  compose.onNodeWithText("已完成").performClick()
  compose.onNodeWithText("今日提醒").assertDoesNotExist()
  compose.runOnIdle{assertNotNull(HealthStore(context).courseRecords().first{it.title=="2化12-放2"}.reminderCompletedAt)}
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
