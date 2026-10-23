package com.combustible12.healthtrend

/** Null denotes an open edge; the canvas edge is a clip, never a reference boundary. */
internal fun referenceBandPixels(low:Double?,high:Double?,height:Float,y:(Double)->Float):Pair<Float,Float>? {
 // An open-ended range has one real boundary but no finite normal area.
 if(low==null||high==null||low>high)return null
 val top=y(high).coerceIn(0f,height)
 val bottom=y(low).coerceIn(0f,height)
 return if(top<=bottom)top to bottom else null
}
