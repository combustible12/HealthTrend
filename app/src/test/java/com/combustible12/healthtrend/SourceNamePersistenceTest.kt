package com.combustible12.healthtrend
import org.junit.Test
import org.junit.Assert.*
class SourceNamePersistenceTest {
 @Test fun completeNamesRemainIndependentFromIdentity(){
  val cases=listOf("FT3" to "游离-T3","FT4" to "游离-T4","TSH" to "促甲状腺素（III代）","CA724" to "糖类抗原CA72-4","GGT" to "γ-谷氨酰转肽酶","A/G" to "白蛋白/球蛋白","FDP" to "纤维蛋白（原）降解产物","AST/ALT" to "谷草/谷丙比值")
  cases.forEach{(key,name)->
   val raw="$key\t$name\t2.340\t-\t1.0-3.0"
   val row=ReportParser.parse(ReportParser.bindExplicitLeadingIdentities(raw)).single()
   assertEquals(raw,key,row.metricKey);assertEquals(raw,name,row.displayName)
   assertEquals("2.340",row.textValue)
  }
 }
 @Test fun qualitativeSourceNameKeepsItsParenthesizedMethod(){
  val row=ReportParser.parse("TPPA\t梅毒螺旋体抗体（TPPA）\t阴性\t-\t阴性").single()
  assertEquals("TPPA",row.metricKey);assertEquals("梅毒螺旋体抗体（TPPA）",row.displayName)
 }
 @Test fun conflictingDatesOnOneLineCannotSilentlyChooseFirst(){
  val raw="检查日期 2001-01-02 报告日期 2001-01-03"
  val meta=ReportMetadata.extract(raw,emptyList())
  assertEquals("",meta.date);assertTrue("date" in meta.uncertain)
 }
 @Test fun explicitPastedDateIsPreservedWithoutGuessingFromAnalytes(){
  val raw="合成医院\n甲状腺功能\n2001-01-02\nFT3 游离-T3 4.20 pmol/L 3-6"
  val draft=pastedReportDraft(raw)
  assertEquals("2001-01-02",draft.date);assertEquals("游离-T3",draft.rows.single().name)
 }
}
