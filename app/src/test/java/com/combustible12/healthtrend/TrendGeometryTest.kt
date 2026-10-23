package com.combustible12.healthtrend

import org.junit.Assert.*
import org.junit.Test

class TrendGeometryTest {
 private val values=listOf(7.11,7.01,8.42,12.32,3.03,4.14,1.79)
 private val dates=listOf(29L,32L,36L,42L,46L,49L,53L)
 private fun geometry(hospitals:List<String>,timeline:List<Long> = dates,height:Float=200f):TrendGeometry {
  val p=dates.zip(values);val slots=trendTimeline(p,timeline)
  return trendGeometry(p,timeline,hospitals,List(7){3.5 to 9.5},slots.dates.indices.map{it*40f},height)
 }
 @Test fun sevenWhiteCellCoordinatesAreLinearAndNeverOvershoot(){
  // Hospitals are deliberately synthetic; actual device ownership is never inferred from dates.
  val g=geometry(listOf("A","A","A","B","A","A","A"))
  assertEquals(listOf(listOf(0,1,2),listOf(3),listOf(4,5,6)),g.segments)
  assertEquals(7,g.points.size)
  values.indices.forEach{i->assertEquals(g.scale.y(values[i],200f),g.points[i].y,0.0001f)}
  assertTrue(g.points[5].y<g.points[4].y);assertTrue(g.points[6].y>g.points[4].y)
  assertEquals((4.14-3.03)/(3.03-1.79),((g.points[4].y-g.points[5].y)/(g.points[6].y-g.points[4].y)).toDouble(),0.00001)
 }
 @Test fun hospitalOwnershipRatherThanDateDeterminesSegments(){
  assertEquals(listOf(listOf(0,1,2,3,4,5,6)),geometry(List(7){"A"}).segments)
  assertEquals(7,geometry(List(7){if(it%2==0)"A" else "B"}).segments.size)
 }
 @Test fun missingMeasurementBreaksBothViewsWithSameScale(){
  val main=geometry(List(7){"A"},dates+47L)
  val preview=geometry(List(7){"A"},dates+47L,400f)
  assertEquals(listOf(listOf(0,1,2,3,4),listOf(5,6)),main.segments)
  assertEquals(main.segments,preview.segments)
  main.points.indices.forEach{i->assertEquals(main.points[i].y*2,preview.points[i].y,0.001f)}
 }
 @Test fun sameTimeRecordsHaveSeparateSlotsAndNoLoss(){
  val p=listOf(1L to 3.0,1L to 4.0,2L to 5.0)
  val t=trendTimeline(p,listOf(1L,2L,3L))
  assertEquals(listOf(0,1,2),t.pointSlots);assertEquals(listOf(1L,1L,2L,3L),t.dates)
  assertEquals(listOf(listOf(0),listOf(1,2)),trendSegments(t.pointSlots,listOf("A","B","B")))
 }
 @Test fun paletteSlotsSurviveReorderingNewHospitalsAndNormalization(){
  val keys=hospitalPaletteKeys(emptyList(),listOf(" B ","A","", "A"))
  assertEquals(listOf("A","B","医院未录入"),keys)
  val after=hospitalPaletteKeys(keys,listOf("0","B","A"))
  assertEquals(keys,after.take(3))
  for(abnormal in listOf(false,true)){
   val colors=keys.withIndex().associate{it.value to hospitalPaletteArgb(it.index,abnormal)}
   assertEquals(colors,after.take(3).withIndex().associate{it.value to hospitalPaletteArgb(it.index,abnormal)})
   assertEquals(3,colors.values.distinct().size)
  }
  assertEquals(0xFFFF918BL,hospitalPaletteArgb(0,true));assertEquals(0xFF82BEFFL,hospitalPaletteArgb(0,false))
  assertEquals(30,(0 until 30).map{hospitalPaletteArgb(it,true)}.distinct().size)
  assertEquals(listOf(listOf(0,1)),trendSegments(listOf(0,1),listOf(" A ","A")))
 }
 @Test fun referenceBoundsAndMissingRangesNeverRemovePoints(){
  for(bands in listOf(emptyList(),listOf(0.108 to null),listOf(null to 12.10),listOf(3.5 to 9.5))){
   val g=trendGeometry(listOf(1L to 1.79,2L to 12.32),listOf(1L,2L),listOf("A","A"),bands,listOf(0f,100f),200f)
   assertEquals(2,g.points.size);assertTrue(g.points[0].y>g.points[1].y)
   assertEquals((12.32-1.79)/(g.scale.high-g.scale.low)*200*.76,(g.points[0].y-g.points[1].y).toDouble(),0.0001)
  }
 }
 private fun row(key:String="HGB",v:Double=12.0,unit:String="g/dL")=LabResult("x","r","A","血常规",null,key,key,v,unit,10.0,15.0,1L)
 @Test fun legacyCachedNumbersCannotBypassUnitConversion(){
  val r=row().copy(normalizedValue=12.0,normalizedUnit="g/L")
  assertEquals(120.0,r.trendPlotValue()!!,0.0)
  assertEquals(100.0,r.trendReferenceRange().first!!,0.0)
  assertTrue(trendUnitsCompatible(r,row(v=120.0,unit="g/L")))
  assertFalse(trendUnitsCompatible(row("RDW-CV",12.0,"%"),row("PDW-CV",12.0,"%")))
  assertFalse(trendUnitsCompatible(row("RDW-CV",12.0,"%"),row("RDW-SD",40.0,"fL")))
  assertEquals(UnitNormalizer.normalize("FDP",1.0,"ug/mL"),UnitNormalizer.normalize("FDP",1.0,"μg/mL"))
 }
}
