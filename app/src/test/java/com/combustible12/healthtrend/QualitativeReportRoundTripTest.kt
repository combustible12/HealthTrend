package com.combustible12.healthtrend

import org.junit.Assert.*
import org.junit.Test

/** Covers the import -> editable draft -> re-opened draft path without touching real patient data. */
class QualitativeReportRoundTripTest {
 @Test fun clipboardImportPreservesReferenceText() {
  val raw="HIV 人免疫缺陷病毒抗体（定性） 阴性  阴性"
  val parsed=ReportParser.parse(raw)
  assertTrue("qualitative item should be parsed",parsed.isNotEmpty())
  val item=parsed.first()
  assertEquals("阴性",item.textValue)
  assertEquals("阴性",item.referenceRaw)
  val draft=DraftRow.from(item)
  assertEquals("阴性",draft.rangeRaw())
  val reopened=DraftRow.from(draft.parsed())
  assertEquals(draft.text,reopened.text)
  assertEquals(draft.referenceRaw,reopened.referenceRaw)
  assertEquals(ReferenceAgreement.MATCH,LabResultValues.agreement(reopened.text,reopened.referenceRaw))
 }
 @Test fun positiveAntibodyIsNotReplacedByNegativeReference() {
  val parsed=ReportParser.parse("HBsAb 乙肝表面抗体 阳性  阴性").first()
  val draft=DraftRow.from(parsed)
  assertEquals("阳性",draft.parsed().textValue)
  assertEquals("阴性",draft.parsed().referenceRaw)
  assertEquals(ReferenceAgreement.MISMATCH,LabResultValues.agreement(draft.text,draft.referenceRaw))
 }
 @Test fun draftSurvivesSerializationAndKeepsReferenceAndIdentity() {
  val original=ReportDraft(
   hospital="测试医院",type="传染病筛查",date="2026-08-26",
   rows=listOf(
    DraftRow(id="old-1",name="人免疫缺陷病毒抗体（定性）",key="HIV",text="阴性",referenceRaw="阴性"),
    DraftRow(id="old-2",name="乙肝表面抗体",key="HBsAb",text="阳性",referenceRaw="阴性")
   ))
  val output=java.io.ByteArrayOutputStream()
  java.io.ObjectOutputStream(output).use{it.writeObject(original)}
  val restored=java.io.ObjectInputStream(java.io.ByteArrayInputStream(output.toByteArray())).use{it.readObject() as ReportDraft}
  assertEquals(original.rows.map{it.id},restored.rows.map{it.id})
  assertEquals(original.rows.map{it.key},restored.rows.map{it.key})
  assertEquals(original.rows.map{it.text},restored.rows.map{it.text})
  assertEquals(original.rows.map{it.referenceRaw},restored.rows.map{it.referenceRaw})
  assertEquals(original.date,restored.date)
 }
 @Test fun legacyMissingReferenceRemainsMissing() {
  val draft=DraftRow(key="HIV",name="人免疫缺陷病毒抗体（定性）",text="阴性",referenceRaw="")
  assertEquals("",draft.parsed().referenceRaw)
  assertEquals(ReferenceAgreement.UNKNOWN,LabResultValues.agreement(draft.text,draft.referenceRaw))
 }
}
