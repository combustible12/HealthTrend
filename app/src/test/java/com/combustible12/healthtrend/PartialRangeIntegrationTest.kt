package com.combustible12.healthtrend

import org.junit.Assert.*
import org.junit.Test

/** Synthetic inputs; parser, editor and chart projection use the production implementations. */
class PartialRangeIntegrationTest {
 private fun parsed(range:String="0.188-0.2??",flag:String="↑",value:String="0.250") =
  ReportParser.parse("PCT 血小板压积 $value $flag % $range").single()
 @Test fun pasteNeverTruncatesUnknownDigitsOrUnit(){
  val p=parsed();assertEquals("0.188-0.2??",p.referenceRaw)
  assertEquals("%",p.unit);assertEquals(0.188,p.referenceLow!!,0.0);assertNull(p.referenceHigh)
  assertEquals("0.250",p.textValue);assertEquals("↑",p.reportFlag)
 }
 @Test fun reviewAndOtherFieldEditPreserveUnknownDigits(){
  val d=DraftRow.from(parsed()).copy(name="血小板压积",unit="%")
  assertTrue(d.valid());assertEquals("0.2??",d.high)
  assertEquals("0.188-0.2??",d.parsed().referenceRaw)
  assertEquals("0.188-0.2??",d.copy(text="0.260").parsed().referenceRaw)
  assertEquals("0.188-0.3??",d.copy(high="0.3??").parsed().referenceRaw)
 }
 private fun result(p:ParsedLabResult)=LabResult("synthetic","synthetic","合成医院","血常规",null,p.metricKey,p.displayName,p.value,p.unit,p.referenceLow,p.referenceHigh,1L,textValue=p.textValue,referenceRaw=p.referenceRaw,reportFlag=p.reportFlag)
 @Test fun completeValueEntersTrendWithoutInventedUpperLine(){
  val r=result(parsed());assertEquals(0.250,r.normalizedValue!!,0.0)
  assertEquals(0.188,r.trendReferenceRange().first!!,0.0);assertNull(r.trendReferenceRange().second)
  assertEquals(ResultStatus.HIGH,r.status())
 }
 @Test fun absentFlagRemainsUncertainAndConflictRequiresReview(){
  assertEquals(ResultStatus.UNKNOWN,result(parsed(flag="")).status())
  val conflict=parsed(value="0.150")
  assertTrue(DraftRow.from(conflict).referenceNeedsReview())
  assertTrue(metricNeedsReview(conflict,null));assertEquals(ResultStatus.UNKNOWN,result(conflict).status())
 }
 @Test fun completeAndLegacyBoundsKeepExistingProjection(){
  val r=result(parsed("0.188-0.280",flag=""))
  assertEquals(0.280,r.trendReferenceRange().second!!,0.0)
  assertEquals(r.trendReferenceRange(),r.copy(referenceRaw="").trendReferenceRange())
 }
 @Test fun lowFlagConstrainsLowerWithoutInventingLine(){
  val r=result(parsed("0.1??-0.400","↓","0.150"))
  assertNull(r.trendReferenceRange().first);assertEquals(ResultStatus.LOW,r.status())
 }
 @Test fun previousReleaseSerializedDraftAndEmbeddedReportRemainReadable() {
  val encoded=javaClass.getResourceAsStream("/legacy-reference-draft.b64")!!.bufferedReader().use{it.readText().trim()}
  val old=java.io.ObjectInputStream(java.util.Base64.getDecoder().decode(encoded).inputStream()).use{it.readObject() as ReportDraft}
  assertEquals("5.00",old.rows.single().text)
  assertEquals("",old.rows.single().referenceRaw)
  assertEquals("3.5-9.5",old.rows.single().parsed().referenceRaw)
  val result=old.existing!!.results.single()
  assertEquals("",result.referenceRaw);assertEquals(ResultStatus.NORMAL,result.status())
  assertEquals("5.00",result.textValue)
 }
}
