package com.combustible12.healthtrend

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

class ReportDraftPersistenceTest {
 @Test fun roundTripRetainsUnreviewedRowsAndSourceInformation(){
  val draft=ReportDraft(
   draftId="test-stable-id",
   hospital="测试医院",
   type="血常规",
   date="2026-10-14",
   images=listOf("file:///private/sources/test.image"),
   ocr="待核对的原始文本",
   rows=listOf(
    DraftRow(id="row1",name="红细胞压积",key="HCT",text="32.30",unit="%",low="34",high="45"),
    DraftRow(id="row2",name="未知中文指标",key="待核对:未知中文指标",text="0.000",unit="",raw="未知中文指标 0.000",uncertain=true)
   ),
   uncertain=setOf("date"))
  val bytes=ByteArrayOutputStream()
  ObjectOutputStream(bytes).use{it.writeObject(draft)}
  val restored=ObjectInputStream(ByteArrayInputStream(bytes.toByteArray())).use{it.readObject() as ReportDraft}
  assertEquals(draft,restored)
  assertEquals("32.30",restored.rows[0].text)
  assertEquals("0.000",restored.rows[1].text)
  assertTrue(restored.rows[1].uncertain)
  assertEquals("test-stable-id",restored.draftId)
  assertEquals(draft.images,restored.images)
 }
}
