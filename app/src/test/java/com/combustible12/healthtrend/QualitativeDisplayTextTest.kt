package com.combustible12.healthtrend

import org.junit.Assert.assertEquals
import org.junit.Test

class QualitativeDisplayTextTest {
 @Test fun redundantSignsAreHiddenOnlyForUnambiguousWholeValues(){
  for(raw in listOf("阳性(+)","阳性（+）","阳性 (+)","+","(+)"))assertEquals("阳性",qualitativeDisplayText(raw))
  for(raw in listOf("阴性(-)","阴性（-）","-","(-)"))assertEquals("阴性",qualitativeDisplayText(raw))
  assertEquals("弱阳性",qualitativeDisplayText("弱阳性（±）"))
 }
 @Test fun gradesUncertaintyDescriptionsAndNumbersRemainExact(){
  for(raw in listOf("++","+++","++++","2+","3+","±","阳性(++)","阴性(+)之疑点","未检出","<0.01","32.30","弱阳性(+)","可疑(±)"))assertEquals(raw,qualitativeDisplayText(raw))
 }
}
