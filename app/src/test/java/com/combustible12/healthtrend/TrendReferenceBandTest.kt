package com.combustible12.healthtrend

import org.junit.Assert.*
import org.junit.Test

class TrendReferenceBandTest {
 private fun y(value:Double)=(200-value*100).toFloat()
 @Test fun lowerOnlyRangeFillsUpToCanvasTopWithoutUpperBoundary(){
  val band=referenceBandPixels(0.108,null,200f,::y)!!
  assertEquals(0f,band.first,0f);assertEquals(y(0.108),band.second,0.0001f)
 }
 @Test fun upperOnlyRangeFillsDownToCanvasBottomWithoutLowerBoundary(){
  val band=referenceBandPixels(null,0.25,200f,::y)!!
  assertEquals(y(0.25),band.first,0f);assertEquals(200f,band.second,0f)
 }
 @Test fun completeRangeKeepsBothFiniteEdges(){
  val band=referenceBandPixels(0.19,0.4,200f,::y)!!
  assertEquals(y(0.4),band.first,0f);assertEquals(y(0.19),band.second,0f)
 }
 @Test fun missingBothBoundsDoesNotFabricateNormalArea(){assertNull(referenceBandPixels(null,null,200f,::y))}
 @Test fun reversedBoundsDoNotProduceNormalArea(){assertNull(referenceBandPixels(0.4,0.19,200f,::y))}
 @Test fun finiteEdgesOutsideCanvasAreClipped(){
  assertEquals(0f,referenceBandPixels(4.0,null,200f,::y)!!.second,0f)
  assertEquals(200f,referenceBandPixels(null,-1.0,200f,::y)!!.first,0f)
 }
}
