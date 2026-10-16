package com.combustible12.healthtrend

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test

/** Pure UI fixture; does not read or modify any health data. */
class TrendAliasInteractionTest {
 @get:Rule val compose=createComposeRule()
 @Test fun aliasesOnlyAppearAfterTappingMetricNameAndDisappearWhenClosed(){
  compose.setContent{MaterialTheme{TrendMetricTitle("血小板计数",listOf("血小板计数","血小板","PLT"))}}
  compose.onNodeWithText("其他别名").assertDoesNotExist()
  compose.onNodeWithText("血小板、PLT").assertDoesNotExist()
  compose.onNodeWithText("血小板计数").performClick()
  compose.onNodeWithText("其他别名").assertIsDisplayed()
  compose.onNodeWithText("血小板、PLT").assertIsDisplayed()
  compose.onNodeWithText("关闭").performClick()
  compose.onNodeWithText("其他别名").assertDoesNotExist()
  compose.onNodeWithText("血小板、PLT").assertDoesNotExist()
 }
}
