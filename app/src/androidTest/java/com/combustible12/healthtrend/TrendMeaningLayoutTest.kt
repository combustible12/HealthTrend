package com.combustible12.healthtrend

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TrendMeaningLayoutTest {
 @get:Rule val compose=createComposeRule()
 private val explanation="↑ 细菌感染/炎症 / ↓ 感染防御不足"
 private fun verify(fontScale:Float){
  var clicked=false
  compose.setContent{
   CompositionLocalProvider(LocalDensity provides Density(1f,fontScale)){
    MaterialTheme{Box(Modifier.width(140.dp)){TrendMeaningText(explanation){clicked=true}}}
   }
  }
  val node=compose.onNodeWithText(explanation).assertIsDisplayed()
  val layouts=mutableListOf<TextLayoutResult>()
  node.performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(layouts)}
  compose.runOnIdle{
   val layout=layouts.single()
   assertEquals(explanation,layout.layoutInput.text.text)
   assertTrue(layout.lineCount>1)
   assertFalse(layout.hasVisualOverflow)
   assertFalse((0 until layout.lineCount).any{layout.isLineEllipsized(it)})
  }
  node.performClick()
  compose.runOnIdle{assertTrue(clicked)}
 }
 @Test fun latestSummaryKeepsPrecisionAndStatusWithoutRepeatingUnit(){
  compose.setContent{MaterialTheme{TrendLatestSummary("10.540","偏高",Bad)}}
  compose.onNodeWithText("10.540").assertIsDisplayed()
  compose.onNodeWithText("偏高").assertIsDisplayed()
  compose.onNodeWithText("10^9/L").assertDoesNotExist()
 }
 @Test fun narrowExplanationWrapsWithoutEllipsisAndKeepsClick(){verify(1f)}
 @Test fun enlargedFontStillDisplaysWholeExplanation(){verify(1.8f)}
}
