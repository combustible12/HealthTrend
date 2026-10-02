package com.combustible12.healthtrend

/**
 * Confirmed hospital templates are never overwritten by later OCR.
 * Imported report images remain attached to the report and can be reopened.
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

/** One imported report owns its original images and all recognized values. */
data class LabReport(
    val id: String,
    val hospitalKey: String,
    val reportType: String,
    val testedAtEpochMillis: Long,
    val templateVersion: Int?,
    val sourceImages: List<ReportImage>,
    val results: List<LabResult>
)
data class ReportImage(
    val uri: String,
    val pageIndex: Int,
    val importedAtEpochMillis: Long
)
data class LabResult(
    val id: String,
    val reportId: String,
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
    val editedByUser: Boolean = false
) {
    fun status(): ResultStatus = when {
        referenceLowAtTest != null && value < referenceLowAtTest -> ResultStatus.LOW
        referenceHighAtTest != null && value > referenceHighAtTest -> ResultStatus.HIGH
        else -> ResultStatus.NORMAL
    }

    /** Editing a value never mutates the hospital template or historical reference range. */
    fun withEditedValue(newValue: Double): LabResult =
        copy(value = newValue, editedByUser = true)
}
enum class ResultStatus { LOW, NORMAL, HIGH }

data class SymptomEntry(val name:String,val severity:Int,val occurredAtEpochMillis:Long,val note:String="")
data class MedicalRecord(val title:String,val hospital:String,val occurredAtEpochMillis:Long,val category:String,val sourceImageUri:String?)
