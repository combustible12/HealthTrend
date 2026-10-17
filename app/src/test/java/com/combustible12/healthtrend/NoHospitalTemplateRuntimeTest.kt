package com.combustible12.healthtrend

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Prevent current product flows from reconnecting obsolete hospital matching. */
class NoHospitalTemplateRuntimeTest {
 private fun source(name:String):String {
  val root=listOf(File("src/main/java/com/combustible12/healthtrend"),File("app/src/main/java/com/combustible12/healthtrend")).first{it.isDirectory}
  return File(root,name).readText()
 }
 @Test fun currentScreensAndBackupNeverReadHospitalTemplates(){
  for(name in listOf("MainActivity.kt","Updates.kt","HealthBackup.kt","ReportImport.kt")) {
   val text=source(name)
   assertFalse(name,Regex("\\.(latestTemplate|templates|confirmTemplate|applyTemplate|saveTemplateFields)\\s*\\(").containsMatchIn(text))
  }
  assertFalse(source("Updates.kt").contains("套当前模板"))
 }
 @Test fun obsoleteBatchImporterCannotRewriteTemplatesOrReports(){
  val text=source("HealthStore.kt")
  assertFalse(text.contains("fun importHistoryReplacement("))
  assertFalse(text.contains("fun previewHistoryReplacement("))
  assertTrue(text.contains("fun buildReportFromPool("))
 }
}
