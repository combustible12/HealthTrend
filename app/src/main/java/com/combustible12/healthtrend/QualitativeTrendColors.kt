package com.combustible12.healthtrend

import androidx.compose.ui.graphics.Color

/** A green marker is favorable, not a diagnosis or proof of immunity. */
internal fun qualitativeTrendColor(result:LabResult):Color {
 val key=canonicalMetricKey(result.metricKey).uppercase()
 val positive=result.textValue.trim() in setOf("阳性","阳性(+)","阳性（+）","+")
 // HBsAb is the protective surface antibody. Other HBV antibodies do not share this rule.
 if(key in setOf("HBSAB","ANTI-HBS") && positive)return Good
 return when(result.referenceAgreement()){
  ReferenceAgreement.MATCH -> Good
  ReferenceAgreement.MISMATCH -> Bad
  ReferenceAgreement.UNKNOWN -> Muted
 }
}
