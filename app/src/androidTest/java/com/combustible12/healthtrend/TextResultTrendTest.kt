package com.combustible12.healthtrend

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Synthetic UI fixture; does not touch patient storage. Compiled by quick CI. */
class TextResultTrendTest {
 @get:Rule val compose=createComposeRule()
 @Test fun sameDateTextResultsStaySeparateAndClickable(){
  val date=parseDate("2026-01-15")!!
  fun point(id:String,text:String):Pair<LabReport,LabResult>{
   val x=LabResult(id,id,"合成医院","传染病筛查",null,"HBSAG","乙肝表面抗原",null,"",null,null,date,textValue=text,referenceRaw="阴性")
   return LabReport(id,"合成医院","传染病筛查",date,null,emptyList(),listOf(x)) to x
  }
  val points=listOf(point("one","阴性"),point("two","阳性"));var clicked=""
  compose.setContent{MaterialTheme{TextResultTrend(points){clicked=it.second.id}}}
  compose.onAllNodesWithText("01/15",useUnmergedTree=true).assertCountEquals(2)
  compose.onAllNodesWithText("阳性",useUnmergedTree=true).assertCountEquals(1)
  compose.onNodeWithText("阳性",useUnmergedTree=true).performClick()
  compose.runOnIdle{assertEquals("two",clicked)}
  compose.onAllNodesWithText("阴性",useUnmergedTree=true).assertCountEquals(1)
 }
}
