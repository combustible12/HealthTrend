package com.combustible12.healthtrend

import java.time.LocalDate

/**
 * Source-of-truth transcription for the one-time replacement of the six known,
 * previously corrupted CBC reports. This data is not used by normal imports.
 */
internal object VerifiedCbcHistory {
 val metricKeys=listOf(
  "WBC","NEUT#","NEUT%","LYMPH#","LYMPH%","MONO#","MONO%","EOS#","EOS%","BASO#","BASO%",
  "RBC","HGB","HCT","MCV","MCH","MCHC","RDW","RDW-SD","PLT","PCT","MPV","PDW","P-LCR","NRBC%","NRBC#","P-LCC"
 )

 val valuesByDate=linkedMapOf(
  LocalDate.of(2026,9,18) to listOf("7.25","5.77","79.4","1.31","18.1","0.12","1.7","0.05","0.8","0.00","0.0","3.99","113","34.20","85.7","28.3","330","12.8","40","232","0.212","9.1","16.4","21.8","0.00","0.000","51"),
  LocalDate.of(2026,9,23) to listOf("4.35","2.57","59.2","1.50","34.4","0.23","5.2","0.04","0.9","0.01","0.3","3.76","106","32.30","86.0","28.2","328","13.0","41","282","0.255","9.0","16.4","20.8","0.00","0.000","59"),
  LocalDate.of(2026,9,25) to listOf("2.36","0.91","38.4","1.24","52.5","0.18","7.7","0.03","1.2","0.00","0.2","3.54","100","30.30","85.7","28.4","331","12.9","40","262","0.229","8.7","16.3","18.6","0.00","0.000","49"),
  LocalDate.of(2026,9,26) to listOf("3.75","1.92","51.3","1.34","35.6","0.41","11.0","0.07","1.88","0.01","0.3","3.67","102","31.50","85.8","27.8","324","13.1","41","242","0.200","8.3","16.1","16.0","0.00","0.000","39"),
  LocalDate.of(2026,9,29) to listOf("7.41","4.68","63.2","1.89","25.4","0.77","10.4","0.05","0.7","0.02","0.3","3.87","107","33.20","85.7","27.7","323","13.4","42","241","0.190","7.9","16.3","14.9","0.52","0.038","36"),
  LocalDate.of(2026,10,2) to listOf("7.01","4.11","58.8","2.22","31.6","0.62","8.8","0.06","0.8","0.00","0.0","4.20","114","36.00","85.7","27.1","316","13.4","42","239","0.204","8.9","16.4","18.0","0.00","0.000","43")
 )

 init {
  require(metricKeys.size==27 && metricKeys.distinct().size==27)
  require(valuesByDate.size==6 && valuesByDate.values.all{it.size==metricKeys.size})
  require(valuesByDate.values.sumOf{it.size}==162)
  require(valuesByDate.values.flatten().all{it.toDoubleOrNull()!=null})
 }
}
