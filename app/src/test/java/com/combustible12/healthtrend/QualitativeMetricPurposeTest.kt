package com.combustible12.healthtrend

import org.junit.Assert.*
import org.junit.Test

class QualitativeMetricPurposeTest {
 @Test fun antibodyPositiveIsNotAutomaticallyDisease() {
  assertTrue(qualitativeMetricPurpose("HBsAb")!!.contains("表面抗体"))
 }
 @Test fun screeningPositiveRequiresConfirmation() {
  assertTrue(qualitativeMetricPurpose("HIV")!!.contains("确证"))
 }
 @Test fun unknownMetricDoesNotGetInventedMedicalAdvice() {
  assertNull(qualitativeMetricPurpose("UNKNOWN-METRIC"))
 }
 @Test fun negativeReferenceIsComparedAsText() {
  assertEquals(ReferenceAgreement.MATCH,LabResultValues.agreement("阴性","阴性"))
  assertEquals(ReferenceAgreement.MISMATCH,LabResultValues.agreement("阳性","阴性"))
 }
}
