package com.combustible12.healthtrend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageDocumentsTest {
 @Test fun searchIgnoresWhitespaceAndCanLocateTextSplitAcrossOcrLines(){
  val blocks=listOf(
   SearchTextBlock("康复",10,20,80,50),
   SearchTextBlock("新液",82,20,145,50),
   SearchTextBlock("易蒙停",10,70,100,100)
  )
  assertTrue(containsSearchText("准备 压力袜", "压力袜"))
  assertEquals(listOf(0,1),matchingBlockIndexes(blocks,"康复新液"))
  assertEquals(listOf(2),matchingBlockIndexes(blocks,"易蒙停"))
 }

 @Test fun focusedHighlightCenterStaysAtViewportCenter(){
  val block=SearchTextBlock("压力袜",700,500,900,580)
  val viewportWidth=1080;val viewportHeight=1920;val zoom=2.2f
  val translation=focusTranslation(block,viewportWidth,viewportHeight,1536,1152,zoom)
  val transform=imageViewportTransform(viewportWidth,viewportHeight,1536,1152,zoom,translation.x,translation.y)
  val box=transform.screenBox(block,viewportWidth,viewportHeight)
  assertEquals(viewportWidth/2f,(box.left+box.right)/2f,0.01f)
  assertEquals(viewportHeight/2f,(box.top+box.bottom)/2f,0.01f)
 }

 @Test fun highlightAndImageUseTheSameZoomPanTransform(){
  val block=SearchTextBlock("护肝",100,200,260,260)
  val before=imageViewportTransform(1000,1600,1200,1600).screenBox(block,1000,1600)
  val ratio=2f;val centroidX=140f;val centroidY=-210f
  val tx=transformedTranslation(0f,centroidX,ratio,35f);val ty=transformedTranslation(0f,centroidY,ratio,-20f)
  val after=imageViewportTransform(1000,1600,1200,1600,ratio,tx,ty).screenBox(block,1000,1600)
  assertEquals((before.left-500f-centroidX)*ratio+500f+centroidX+35f,after.left,0.01f)
  assertEquals((before.top-800f-centroidY)*ratio+800f+centroidY-20f,after.top,0.01f)
  assertEquals((before.right-before.left)*ratio,after.right-after.left,0.01f)
  assertEquals((before.bottom-before.top)*ratio,after.bottom-after.top,0.01f)
 }
}
