package com.combustible12.healthtrend

import org.junit.Assert.*
import org.junit.Test

class PartialReferenceRangesTest {
 @Test fun obscuredUpperBoundUsesPrintedHighFlagAsConstraint() {
  val range = PartialReferenceRanges.interval("0.188-0.2??", 0.250, '↑')!!
  assertEquals("0.188-0.2??", range.raw)
  assertEquals(0.200, range.upper.minimum, 0.0000001)
  assertTrue(range.upperLimit < 0.250)
  assertFalse(range.exact)
 }
 @Test fun missingFlagNeverInventsUpperBound() {
  val range = PartialReferenceRanges.interval("0.188-0.2??")!!
  assertEquals(0.299, range.upper.maximum, 0.0000001)
  assertFalse(range.exact)
 }
 @Test fun exactBoundsRemainExact() {
  val range = PartialReferenceRanges.interval("0.188-0.250")!!
  assertTrue(range.exact)
  assertEquals(0.250, range.upperLimit, 0.0000001)
 }
 @Test fun contradictoryEvidenceIsNotSilentlyAccepted() {
  assertNull(PartialReferenceRanges.interval("0.188-0.2??", 0.150, '↑'))
 }
 @Test fun constrainedLimitsRespectDigitPrecisionAndKnownHoles() {
  assertEquals(0.249,PartialReferenceRanges.interval("0.188-0.2??",0.250,'↑')!!.upperLimit,0.0)
  assertEquals(0.245,PartialReferenceRanges.interval("0.188-0.2?5",0.250,'↑')!!.upperLimit,0.0)
  assertEquals(0.151,PartialReferenceRanges.interval("0.1??-0.400",0.150,'↓')!!.lowerLimit,0.0)
 }
 @Test fun unknownIntegerDigitsAndNegativeBoundsKeepKnownDigits() {
  assertEquals(199.0,PartialReferenceRanges.bound("1??")!!.maximum,0.0)
  assertEquals(-0.299,PartialReferenceRanges.bound("-0.2??")!!.minimum,0.0)
  assertNull(PartialReferenceRanges.interval("0.3??-0.2??"))
 }
}
