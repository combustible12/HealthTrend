package com.combustible12.healthtrend

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
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
   // Capture before click: the Material indication briefly darkens the dot
   // while pressed and must not be mistaken for its resting hospital color.
   node.assertExists()
   val pixels=node.captureToImage().toPixelMap()
   assertEquals(colors[i].red,pixels[pixels.width/2,pixels.height/2].red,0.02f)
   assertEquals(colors[i].green,pixels[pixels.width/2,pixels.height/2].green,0.02f)
   node.performClick();compose.runOnIdle{assertEquals(i,clicked)}
  }
 }
 @Test fun previewHeaderOffersLandscapeAndPortraitActions(){
  var toggled=0
  compose.setContent{MaterialTheme{TrendPreviewHeader("WBC",false){toggled++}}}
  compose.onNodeWithText("整体趋势 · WBC").assertIsDisplayed()
  compose.onNodeWithContentDescription("横屏显示").assertIsDisplayed().performClick()
  compose.runOnIdle{assertEquals(1,toggled)}
  compose.setContent{MaterialTheme{TrendPreviewHeader("WBC",true){toggled++}}}
  compose.onNodeWithContentDescription("退出横屏").assertIsDisplayed().performClick()
  compose.runOnIdle{assertEquals(2,toggled)}
 }
 @Test fun landscapePreviewCardIsCenteredWithSpaceOnEveryEdge(){
  compose.setContent{MaterialTheme{
   Box(Modifier.requiredSize(320.dp,180.dp).semantics{contentDescription="横屏范围"}){
    TrendPreviewDialogFrame(true){Box(Modifier.trendPreviewCardSize(true).semantics{contentDescription="横屏卡片"})}
   }
  }}
  val frame=compose.onNodeWithContentDescription("横屏范围").fetchSemanticsNode().boundsInRoot
  val card=compose.onNodeWithContentDescription("横屏卡片").fetchSemanticsNode().boundsInRoot
  val left=card.left-frame.left;val right=frame.right-card.right
  val top=card.top-frame.top;val bottom=frame.bottom-card.bottom
  assertTrue(left>0f&&right>0f&&top>0f&&bottom>0f)
  assertEquals(left,right,1.5f)
  assertEquals(top,bottom,1.5f)
 }
 @Test fun previewContainsEveryDotIncludingConnectedSegmentEndpoints(){
  compose.setContent{MaterialTheme{TrendPreviewDialog(points,Color.Black,List(3){3.5 to 9.5},listOf("A","A","A"),List(3){colors[0]},"WBC",{})}}
  compose.onNodeWithText("整体趋势 · WBC").assertIsDisplayed()
  compose.onNodeWithText("关闭").assertIsDisplayed()
  val pixels=compose.onRoot().captureToImage().toPixelMap()
  var matching=0
  for(x in 0 until pixels.width)for(y in 0 until pixels.height){val p=pixels[x,y];if(kotlin.math.abs(p.red-colors[0].red)<.02f&&kotlin.math.abs(p.green-colors[0].green)<.02f)matching++}
  assertTrue("Preview must render hospital-colored points and line",matching>20)
 }
 @Test fun cardActionsAreBorderlessIconsAndDispatchExactActions(){
  var moved=0;var pinned=0
  compose.setContent{MaterialTheme{TrendCardActions(primary=true,onMove={moved++},onPin={pinned++})}}
  compose.onNodeWithContentDescription("移到其他指标").assertIsDisplayed().performClick()
  compose.onNodeWithContentDescription("置于最上方").assertIsDisplayed().performClick()
  compose.runOnIdle{assertEquals(1,moved);assertEquals(1,pinned)}
 }
 @Test fun chemotherapyMarkersUseGrayDateOverlayAndOverallAxis(){
  val zone=java.time.ZoneId.systemDefault()
  fun day(value:String)=java.time.LocalDate.parse(value).atStartOfDay(zone).toInstant().toEpochMilli()
  val datedPoints=listOf(day("2026-01-01") to 3.0,day("2026-01-11") to 2.0)
  val treatment=day("2026-01-06")
  compose.setContent{MaterialTheme{Spark(datedPoints,Color.Black,eventLayers=listOf(TrendEventLayer(TrendEventKind.CHEMOTHERAPY,listOf(treatment))))}}
  compose.onNodeWithContentDescription("化疗日期 01/06").assertIsDisplayed()
  compose.onNodeWithText("01/06").assertIsDisplayed()
  compose.setContent{MaterialTheme{TrendPreviewAxis(datedPoints,datedPoints.map{it.first},listOf(TrendEventLayer(TrendEventKind.CHEMOTHERAPY,listOf(treatment))))}}
  compose.onNodeWithContentDescription("整体趋势化疗日期 01/06").assertIsDisplayed()
  compose.onNodeWithText("01/06").assertIsDisplayed()
 }

 @Test fun growthFactorMarkerUsesItsOwnOverlayWithoutAddingAxisDate(){
  val zone=java.time.ZoneId.systemDefault()
  fun day(value:String)=java.time.LocalDate.parse(value).atStartOfDay(zone).toInstant().toEpochMilli()
  val datedPoints=listOf(day("2026-01-01") to 3.0,day("2026-01-11") to 2.0)
  val injection=day("2026-01-06")
  assertEquals(TreatmentMarkerGray,TrendEventLineColor)
  assertEquals(.28f,TrendEventLineAlpha)
  assertNotEquals(GrowthFactorMarker,TrendEventLineColor)
  compose.setContent{MaterialTheme{Spark(datedPoints,Color.Black,eventLayers=listOf(TrendEventLayer(TrendEventKind.GROWTH_FACTOR_INJECTION,listOf(injection))))}}
  compose.onNodeWithContentDescription("升白针日期 01/06").assertIsDisplayed()
  compose.onNodeWithText("01/06").assertDoesNotExist()
 }

}
