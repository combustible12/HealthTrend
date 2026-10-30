package com.combustible12.healthtrend

import androidx.compose.ui.graphics.Color
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

 @Test fun bmiUsesTheSameSnapshottedHeightAndOneDecimal(){
  assertEquals(21.38157894736842,bodyMassIndex(152.0,49.4)!!,0.00000000000001)
  assertEquals("21.4",bodyMassIndexText(152.0,49.4))
  assertNull(bodyMassIndex(null,49.4))
  assertEquals("—",bodyMassIndexText(152.0,null))
 }

 @Test fun bmiHealthFillUsesTheSameGreenAndPinkAsPulseStatus(){
  val neutral=Color(0xFFE6E6E6)
  assertEquals(Color(0xFFCFF3E0),healthStatusFillColor(true,neutral))
  assertEquals(Color(0xFFFFDCE2),healthStatusFillColor(false,neutral))
  assertEquals(neutral,healthStatusFillColor(null,neutral))

  assertEquals(false,idealAdultBmiStatus(100.0,18.49))
  assertEquals(true,idealAdultBmiStatus(100.0,18.5))
  assertEquals(true,idealAdultBmiStatus(100.0,23.99))
  assertEquals(false,idealAdultBmiStatus(100.0,24.0))
  assertNull(idealAdultBmiStatus(null,49.4))
  assertEquals(Color(0xFFCFF3E0),bmiValueColor(152.0,49.4,neutral))
  assertEquals(Color(0xFFFFDCE2),bmiValueColor(152.0,60.0,neutral))
  assertEquals(neutral,bmiValueColor(null,49.4,neutral))
 }
}
