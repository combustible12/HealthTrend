package com.combustible12.healthtrend

/** Short educational labels for qualitative lab results; never diagnoses. */
internal fun qualitativeMetricPurpose(metricKey:String):String? = when(canonicalMetricKey(metricKey).uppercase()){
 "HBSAG" -> "阳性提示可能存在乙肝病毒感染"
 "HBSAB", "ANTI-HBS" -> "阳性提示存在乙肝表面抗体"
 "HBEAG" -> "阳性可提示乙肝病毒复制较活跃"
 "HBEAB", "ANTI-HBE" -> "阳性提示存在乙肝e抗体，需结合其他指标"
 "HBCAB", "ANTI-HBC" -> "阳性提示曾经或目前感染过乙肝病毒"
 "HIV", "HIV-AB", "ANTI-HIV" -> "筛查阳性需要进一步确证"
 "TP-AB", "TPAB", "SYPHILIS", "ANTI-TP" -> "阳性需结合其他梅毒检查判断"
 else -> null
}
