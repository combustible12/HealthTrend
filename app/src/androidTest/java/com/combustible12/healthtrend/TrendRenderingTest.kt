package com.combustible12.healthtrend

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TrendRenderingTest {
 @get:Rule val compose=createComposeRule()
 private val points=listOf(1L to 3.03,2L to 4.14,3L to 1.79)
 private val colors=listOf(Color(hospitalPaletteArgb(0,true)),Color(hospitalPaletteArgb(1,true)),Color(hospitalPaletteArgb(0,true)))
 @Test fun allMainDotsUseHospitalColorsAndRemainClickable(){
  var clicked=-1
  compose.setContent{MaterialTheme{Spark(points,Color.Black,segmentKeys=listOf("A","B","A"),segmentColors=colors,pointDescriptions=listOf("point0","point1","point2"),onPointClick={clicked=it})}}
  points.indices.forEach{i->
   val node=compose.onNodeWithContentDescription("point$i")
   node.assertExists().performClick();compose.runOnIdle{assertEquals(i,clicked)}
   val pixels=node.captureToImage().toPixelMap()
   assertEquals(colors[i].red,pixels[pixels.width/2,pixels.height/2].red,0.02f)
   assertEquals(colors[i].green,pixels[pixels.width/2,pixels.height/2].green,0.02f)
  }
 }
 @Test fun previewContainsEveryDotIncludingConnectedSegmentEndpoints(){
  compose.setContent{MaterialTheme{TrendPreviewDialog(points,Color.Black,List(3){3.5 to 9.5},listOf("A","A","A"),List(3){colors[0]},"WBC",{})}}
  compose.onNodeWithText("整体趋势 · WBC").assertIsDisplayed()
  val pixels=compose.onRoot().captureToImage().toPixelMap()
  var matching=0
  for(x in 0 until pixels.width)for(y in 0 until pixels.height){val p=pixels[x,y];if(kotlin.math.abs(p.red-colors[0].red)<.02f&&kotlin.math.abs(p.green-colors[0].green)<.02f)matching++}
  assertTrue("Preview must render hospital-colored points and line",matching>20)
 }
}
