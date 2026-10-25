package com.combustible12.healthtrend

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.sp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test

/** Uses the same explanation/dialog composable called by the real trend cards. */
class MetricPurposeInteractionTest {
 @get:Rule val compose=createComposeRule()
 @Test fun standardQualitativeAndCustomDescriptionsOpenTheirOwnDetails() {
  val key=mutableStateOf("CEA")
  compose.setContent{MaterialTheme{Column{
   TrendPurposeExplanation(key.value,"测试指标")
   TrendLatestSummary("10.540","偏高",Bad)
  }}}
  listOf("CEA","AFP","ALB","HBSAB","PCT","PCTIN","自定义项目").forEach { metric ->
   compose.runOnIdle{key.value=metric}
   val purpose=MetricPurposeCatalogue.forDisplay(metric)
   compose.onNodeWithText(purpose.summary).assertIsDisplayed().performClick()
   compose.onNodeWithText(purpose.detail).assertIsDisplayed()
   compose.onNodeWithText("知道了").performClick()
   compose.onNodeWithText(purpose.detail).assertDoesNotExist()
   compose.onNodeWithText("10.540").assertIsDisplayed()
   compose.onNodeWithText("偏高").assertIsDisplayed()
  }
 }
 @Test fun actualTrendCardKeepsOrangeTextAndOpensCorrectExplanationWithoutChangingReports() {
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  context.getSharedPreferences("healthtrend_store_v1",Context.MODE_PRIVATE).edit().clear().commit()
  val store=HealthStore(context)
  val report=materializeReportDraft(pastedReportDraft("合成医院\n肿瘤标志物\n2001-01-02\nCEA 癌胚抗原 3.10 ng/mL 0-5"),store)
  store.saveReport(report)
  val before=store.reports()
  compose.setContent{MaterialTheme{Trends(Modifier,store,listOf(report),0,{_,_->},{},{_,_,_->},{},{})}}
  compose.onAllNodes(hasSetTextAction())[0].performTextInput("CEA")
  val summary=compose.onNodeWithText(metricPurpose("CEA")).performScrollTo().assertIsDisplayed()
  val layouts=mutableListOf<TextLayoutResult>()
  summary.performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(layouts)}
  compose.runOnIdle{
   assertEquals(Accent,layouts.single().layoutInput.style.color)
   assertEquals(12.sp,layouts.single().layoutInput.style.fontSize)
   assertFalse(layouts.single().hasVisualOverflow)
  }
  val folder=java.io.File(context.getExternalFilesDir(null),"report-qa").apply{mkdirs()}
  compose.onRoot().captureToImage().asAndroidBitmap().let { image ->
   java.io.File(folder,"metric-purpose-card.png").outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)}
  }
  summary.performClick()
  compose.onNodeWithText(metricPurposeDetail("CEA")).assertIsDisplayed()
  compose.onNode(isDialog()).captureToImage().asAndroidBitmap().let { image ->
   java.io.File(folder,"metric-purpose-detail.png").outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)}
  }
  compose.onNodeWithText("知道了").performClick()
  compose.onNodeWithText(metricPurpose("CEA")).assertIsDisplayed()
  compose.runOnIdle{assertEquals(before,HealthStore(context).reports())}
 }
 @Test fun changingIdentityDismissesPreviousMetricsExplanation() {
  val key=mutableStateOf("CEA")
  compose.setContent{MaterialTheme{TrendPurposeExplanation(key.value,"测试指标")}}
  compose.onNodeWithText(metricPurpose("CEA")).performClick()
  compose.onNodeWithText(metricPurposeDetail("CEA")).assertIsDisplayed()
  compose.runOnIdle{key.value="HBSAB"}
  compose.onNodeWithText(metricPurposeDetail("CEA")).assertDoesNotExist()
  compose.onNodeWithText(metricPurpose("HBSAB")).performClick()
  compose.onNodeWithText(metricPurposeDetail("HBSAB")).assertIsDisplayed()
 }
}
