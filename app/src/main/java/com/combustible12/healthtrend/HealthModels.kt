package com.combustible12.healthtrend

/**
 * Core rules are intentionally independent from OCR:
 * confirmed hospital templates are never overwritten by later recognition.
 */
data class HospitalLabTemplate(
    val hospitalKey: String,
    val reportType: String,
    val version: Int,
    val confirmed: Boolean,
    val fields: List<LabFieldTemplate>
)
data class LabFieldTemplate(
    val metricKey: String,
    val displayName: String,
    val unit: String,
    val referenceLow: Double?,
    val referenceHigh: Double?
)
data class LabResult(
    val hospitalKey: String,
    val reportType: String,
    val templateVersion: Int?,
    val metricKey: String,
    val rawName: String,
    val value: Double,
    val unitAtTest: String,
    val referenceLowAtTest: Double?,
    val referenceHighAtTest: Double?,
    val testedAtEpochMillis: Long,
    val sourceImageUri: String?
)
data class SymptomEntry(val name:String,val severity:Int,val occurredAtEpochMillis:Long,val note:String="")
data class MedicalRecord(val title:String,val hospital:String,val occurredAtEpochMillis:Long,val category:String,val sourceImageUri:String?)
