package com.combustible12.healthtrend

/** Legacy detailed-purpose API; content is owned exclusively by the catalogue. */
internal fun qualitativeMetricPurpose(metricKey:String):String? =
 MetricPurposeCatalogue.find(metricKey)?.takeIf {
  MetricPurposeCatalogue.resolveKey(metricKey) in setOf("HBSAG","HBSAB","HBEAG","HBEAB","HBCAB","HIVAB","HIVAGAB","HCVAB","TPPA","TPAB","HPV16-DNA","HPV18-DNA","HPV-OTHER12-DNA")
 }?.detail
