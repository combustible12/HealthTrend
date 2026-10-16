package com.combustible12.healthtrend

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

/** Checks the requested entry location without starting a network request/install. */
class UpdateEntryTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 @Test fun updateButtonIsAvailableDirectlyOnMineWithoutOpeningAnotherPage() {
  compose.onNodeWithText("我的").performClick()
  compose.onNodeWithText("一键检查并升级").performScrollTo().assertIsDisplayed().assertIsEnabled()
  compose.onNodeWithText("应用更新").assertIsDisplayed()
  compose.onNodeWithText("检查并升级 ›").assertDoesNotExist()
 }
}
