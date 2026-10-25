package com.combustible12.healthtrend

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
