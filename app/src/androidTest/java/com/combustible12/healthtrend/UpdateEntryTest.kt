package com.combustible12.healthtrend

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue

/** Checks the requested entry location without starting a network request/install. */
class UpdateEntryTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 @Test fun updateButtonIsAtMineBottomImmediatelyBeforeBackToTop() {
  compose.onNodeWithText("我的").performClick()
  compose.onNodeWithText("一键更新并升级").performScrollTo().assertIsDisplayed().assertIsEnabled()
  compose.onNodeWithText("回到顶部").performScrollTo().assertIsDisplayed()
  val update=compose.onNodeWithText("一键更新并升级").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
  val top=compose.onNodeWithText("回到顶部").fetchSemanticsNode().boundsInRoot
  assertTrue("Update button must precede Back to top",update.bottom<=top.top)
  compose.onNodeWithText("应用更新").assertDoesNotExist()
  compose.onNodeWithText("检查并升级 ›").assertDoesNotExist()
 }
}
