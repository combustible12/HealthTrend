package com.combustible12.healthtrend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BodySurfaceAreaTest {
 @Test fun duboisMatchesKnownExampleAndUsesThreeDecimals(){
  assertEquals(1.4389207593,bodySurfaceAreaSquareMeters(152.0,49.4)!!,0.0000000001)
  assertEquals("1.439",bodySurfaceAreaText(152.0,49.4))
 }

 @Test fun missingOrInvalidHeightAndWeightDoNotInventBsa(){
  assertNull(bodySurfaceAreaSquareMeters(null,49.4))
  assertNull(bodySurfaceAreaSquareMeters(152.0,null))
  assertNull(bodySurfaceAreaSquareMeters(0.0,49.4))
  assertNull(bodySurfaceAreaSquareMeters(152.0,0.0))
  assertEquals("—",bodySurfaceAreaText(null,49.4))
 }
}
