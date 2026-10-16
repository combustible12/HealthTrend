package com.combustible12.healthtrend

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Synthetic profile; verifies pairing and the 2x2 grid without patient storage. */
class BloodGroupLayoutTest {
 @get:Rule val compose=createComposeRule()
 @Test fun labelsAndResultsShareTwoRowsAndDateRemainsVisible(){
  compose.setContent{MaterialTheme{Paper(Modifier.width(360.dp)){BloodGroupSummary(BloodGroupProfile("AB","阳性","阳性","阴性","2026-01-15"))}}}
  compose.onNodeWithText("2026-01-15").assertExists()
  compose.onNodeWithTag("blood-group-ABO").onChildren().filterToOne(hasText("AB型")).assertExists()
  val abo=compose.onNodeWithTag("blood-group-ABO").getUnclippedBoundsInRoot()
  val d=compose.onNodeWithTag("blood-group-Rh(D)").getUnclippedBoundsInRoot()
  val c=compose.onNodeWithTag("blood-group-Rh(C)").getUnclippedBoundsInRoot()
  val e=compose.onNodeWithTag("blood-group-Rh(E)").getUnclippedBoundsInRoot()
  assertEquals(abo.top,d.top);assertEquals(c.top,e.top)
  assertTrue(c.top>abo.bottom);assertEquals(abo.left,c.left);assertEquals(d.left,e.left)
 }
 @Test fun emptyProfileStaysExplicitRatherThanPrepopulatingResults(){
  compose.setContent{MaterialTheme{Paper{BloodGroupSummary(BloodGroupProfile())}}}
  compose.onAllNodesWithText("未填写",useUnmergedTree=true).assertCountEquals(5)
  compose.onNodeWithText("B型").assertDoesNotExist()
 }
}
