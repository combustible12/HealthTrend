package com.combustible12.healthtrend

/** Display-only hospital normalization matches report entry's trim policy. */
internal fun trendHospitalKey(raw:String)=raw.trim().ifBlank{"医院未录入"}
internal fun hospitalPaletteKeys(previous:List<String>,current:List<String>):List<String> =
 if(previous.isEmpty())current.map(::trendHospitalKey).distinct()
 else (previous.map(::trendHospitalKey).distinct()+current.map(::trendHospitalKey).distinct().sorted()).distinct()

/** Slots never move when reports are reordered/deleted or another hospital is added. */
internal fun hospitalPaletteArgb(slot:Int,abnormal:Boolean):Long {
 val palette=if(abnormal)listOf(0xFFFF918BL,0xFFF04444L,0xFFD91E1EL,0xFFB50F0FL,0xFF8C0808L,0xFF650404L)
 else listOf(0xFF82BEFFL,0xFF3488F0L,0xFF0862C8L,0xFF004A99L,0xFF003774L,0xFF002852L)
 require(slot>=0)
 if(slot<palette.size)return palette[slot]
 // Deterministic extra shades; adding hospitals never rescales existing shades.
 val tone=48+((slot-6)*73L%176).toInt()
 return if(abnormal)0xFF000000L or (tone.toLong() shl 16) or (24L shl 8) or 24L
 else 0xFF000000L or (16L shl 16) or ((tone/2).toLong() shl 8) or tone.toLong()
}
internal data class TrendTimeline(val dates:List<Long>,val pointSlots:List<Int>)
internal fun trendTimeline(points:List<Pair<Long,Double>>,dates:List<Long>,stackEqualDates:Boolean=false):TrendTimeline {
 if(stackEqualDates){
  val timeline=(dates+points.map{it.first}).distinct().sorted()
  val slots=timeline.withIndex().associate{it.value to it.index}
  return TrendTimeline(timeline,points.map{slots.getValue(it.first)})
 }
 val counts=points.groupingBy{it.first}.eachCount()
 val timeline=(dates+points.map{it.first}).distinct().sorted().flatMap{date->List(counts[date]?:1){date}}
 val slots=timeline.withIndex().groupBy({it.value},{it.index})
 val seen=mutableMapOf<Long,Int>()
 return TrendTimeline(timeline,points.map{(date,_)->val n=seen[date]?:0;seen[date]=n+1;slots.getValue(date)[n]})
}
internal data class TrendEventPosition(val epochMillis:Long,val x:Float)
internal fun trendEventPositions(eventDates:List<Long>,timelineDates:List<Long>,centers:List<Float>,zone:java.time.ZoneId=java.time.ZoneId.systemDefault()):List<TrendEventPosition>{
 require(timelineDates.size==centers.size)
 if(timelineDates.isEmpty())return emptyList()
 fun day(value:Long)=java.time.Instant.ofEpochMilli(value).atZone(zone).toLocalDate().toEpochDay()
 val anchors=timelineDates.zip(centers).groupBy{day(it.first)}.map{(date,rows)->date to rows.map{it.second}.average().toFloat()}.sortedBy{it.first}
 if(anchors.isEmpty())return emptyList()
 return eventDates.distinctBy(::day).sortedBy(::day).mapNotNull{event->
  val target=day(event)
  if(target<anchors.first().first||target>anchors.last().first)return@mapNotNull null
  val exact=anchors.firstOrNull{it.first==target}
  if(exact!=null)return@mapNotNull TrendEventPosition(event,exact.second)
  val right=anchors.indexOfFirst{it.first>target}
  if(right<=0)return@mapNotNull null
  val left=anchors[right-1];val next=anchors[right]
  val ratio=(target-left.first).toFloat()/(next.first-left.first).toFloat()
  TrendEventPosition(event,left.second+(next.second-left.second)*ratio)
 }
}

internal fun trendSegments(pointSlots:List<Int>,hospitalKeys:List<String>):List<List<Int>> {
 require(hospitalKeys.isEmpty()||hospitalKeys.size==pointSlots.size)
 val segments=mutableListOf<MutableList<Int>>()
 pointSlots.indices.forEach{i->
  val gap=i>0&&pointSlots[i]!=pointSlots[i-1]+1
  // Hospital changes keep their source palette, but do not interrupt a compatible metric series.
  if(segments.isEmpty()||gap)segments.add(mutableListOf())
  segments.last().add(i)
 }
 return segments
}
internal data class TrendScale(val low:Double,val high:Double){
 fun y(value:Double,height:Float)=(height*.88-(value-low)/(high-low)*height*.76).toFloat()
}
internal fun trendScale(values:List<Double>,bands:List<Pair<Double?,Double?>>):TrendScale {
 val all=(values+bands.flatMap{listOfNotNull(it.first,it.second)}).filter{it.isFinite()}
 val lo=all.minOrNull()?:0.0;val hi=all.maxOrNull()?:1.0
 // Small-valued metrics (for example plateletcrit) must retain their real
 // variation. Only a truly constant series needs a synthetic non-zero span.
 val span=(hi-lo).takeIf{it>0.0}?:kotlin.math.max(kotlin.math.abs(lo)*.1,1e-6)
 return TrendScale(lo-span*.10,hi+span*.10)
}
internal data class TrendCoordinate(val x:Float,val y:Float)
internal data class TrendGeometry(val timeline:TrendTimeline,val scale:TrendScale,val points:List<TrendCoordinate>,val segments:List<List<Int>>)
/** Both canvases and point hit targets consume this exact projection. */
internal fun trendGeometry(points:List<Pair<Long,Double>>,dates:List<Long>,hospitals:List<String>,bands:List<Pair<Double?,Double?>>,centers:List<Float>,height:Float,stackEqualDates:Boolean=false):TrendGeometry {
 val timeline=trendTimeline(points,dates,stackEqualDates)
 require(centers.size==timeline.dates.size)
 val scale=trendScale(points.map{it.second},bands)
 return TrendGeometry(timeline,scale,points.indices.map{i->TrendCoordinate(centers[timeline.pointSlots[i]],scale.y(points[i].second,height))},trendSegments(timeline.pointSlots,hospitals))
}

internal data class WeightTrendSeries(val records:List<WeightRecord>,val points:List<Pair<Long,Double>>,val earliestPointIndices:List<Int>)
internal fun weightTrendSeries(records:List<WeightRecord>,zone:java.time.ZoneId=java.time.ZoneId.systemDefault()):WeightTrendSeries {
 val sorted=records.sortedWith(compareBy<WeightRecord>{it.measuredAtEpochMillis}.thenBy{it.id})
 val points=sorted.map{record->
  val day=java.time.Instant.ofEpochMilli(record.measuredAtEpochMillis).atZone(zone).toLocalDate()
  day.atStartOfDay(zone).toInstant().toEpochMilli() to record.kilograms
 }
 val earliest=points.indices.distinctBy{points[it].first}
 return WeightTrendSeries(sorted,points,earliest)
}

internal fun trendHasHistoricalAbnormal(results:List<LabResult>)=results.any{it.status() in setOf(ResultStatus.HIGH,ResultStatus.LOW)}

/** Apply saved pins first and retain source order for cards not yet ranked. */
internal fun <T> orderTrendCards(cards:List<T>,savedKeys:List<String>,key:(T)->String):List<T> {
 val ranks=savedKeys.distinct().withIndex().associate{it.value to it.index}
 return cards.withIndex().sortedWith(compareBy<IndexedValue<T>>{ranks[key(it.value)]?:Int.MAX_VALUE}.thenBy{it.index}).map{it.value}
}
