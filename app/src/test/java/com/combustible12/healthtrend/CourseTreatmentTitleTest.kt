package com.combustible12.healthtrend

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class CourseTreatmentTitleTest {
 private fun day(value:String)=LocalDate.parse(value).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
 private fun record(value:String,phase:String)=CourseRecord(date=day(value),phase=phase,title="",titleAuto=true)

 @Test fun observationDayOmitsRadiotherapyCount(){
  val first=record("2026-10-10","化疗")
  val radiation=record("2026-10-11","放疗")
  val today=record("2026-10-12","观察")
  assertEquals("1化3",automaticCourseTitle(today,listOf(first,radiation,today)))
 }

 @Test fun radiotherapyDayAddsOnlyActualSession(){
  val first=record("2026-10-10","化疗")
  val radiation1=record("2026-10-11","放疗")
  val today=record("2026-10-13","放疗")
  assertEquals("1化4-放2",automaticCourseTitle(today,listOf(first,radiation1,today)))
 }

 @Test fun combinedDayStartsNextChemotherapyAndContinuesRadiotherapy(){
  val first=record("2026-10-10","化疗")
  val radiation1=record("2026-10-11","放疗")
  val radiation2=record("2026-10-13","放疗")
  val today=record("2026-10-17","放化")
  assertEquals("2化1-放3",automaticCourseTitle(today,listOf(first,radiation1,radiation2,today)))
 }

 @Test fun manualTitleIsPreserved(){
  val first=record("2026-10-10","化疗")
  val manual=record("2026-10-12","观察").copy(title="2化18",titleAuto=false)
  assertEquals("2化18",displayCourseTitle(manual,listOf(first,manual)))
 }

 @Test fun portMaintenanceAdvancesByTwentyEightDays(){
  assertEquals("2026-11-11",nextPortMaintenanceDate("2026-10-14"))
 }

 @Test fun manualRadiotherapyNumberBecomesNextAutomaticAnchor(){
  val chemotherapy=record("2026-10-14","化疗").copy(title="2化1",titleAuto=false)
  val corrected=record("2026-10-27","放疗").copy(title="2化14-放6",titleAuto=false)
  val next=record("2026-10-28","放疗")
  assertEquals("2化15-放7",automaticCourseTitle(next,listOf(chemotherapy,corrected,next)))
 }

 @Test fun blankAutoCardHasNoUserContent(){
  assertEquals(true,record("2026-10-28","放疗").copy(title="2化15-放7").isBlankCourseCard())
  assertEquals(false,record("2026-10-28","放疗").copy(title="2化15-放7",noteText="已记录").isBlankCourseCard())
 }
}
