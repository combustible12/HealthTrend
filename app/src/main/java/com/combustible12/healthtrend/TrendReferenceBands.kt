package com.combustible12.healthtrend

/** Null denotes an open edge; the canvas edge is a clip, never a reference boundary. */
internal fun referenceBandPixels(low:Double?,high:Double?,height:Float,y:(Double)->Float):Pair<Float,Float>? {
 if(low==null&&high==null)return null
 if(low!=null&&high!=null&&low>high)return null
 val top=high?.let(y)?.coerceIn(0f,height)?:0f
 val bottom=low?.let(y)?.coerceIn(0f,height)?:height
 return if(top<=bottom)top to bottom else null
}
