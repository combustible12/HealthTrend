package com.combustible12.healthtrend

/** Analyzer Y/N is retained internally, never interpreted as an up arrow. */
internal fun sourceReportFlag(source:String):String {
 val arrows=source.filter{it=='↑'||it=='↓'}.toSet().joinToString("")
 val flags=Regex("(?:^|\\s)([YN])(?=\\s|$)",RegexOption.IGNORE_CASE)
  .findAll(source).map{it.groupValues[1].uppercase()}.toSet().joinToString("")
 return if(arrows.isNotEmpty()&&flags.length<=1)arrows else arrows+flags
}

/** Missing/partial bounds cannot prove a Y conflict; never invent the missing bound. */
internal fun sourceFlagConflict(flag:String,value:Double?,low:Double?,high:Double?):Boolean {
 if(value==null)return false
 val outside=(low!=null&&value<low)||(high!=null&&value>high)
 return when(flag){
  "N"->outside
  "Y"->low!=null&&high!=null&&!outside
  else->false
 }
}
