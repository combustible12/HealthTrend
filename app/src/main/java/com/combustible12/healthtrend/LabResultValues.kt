package com.combustible12.healthtrend

/** Computed from preserved source text; no migration or fake numeric values. */
enum class LabValueKind { NUMBER, QUALITATIVE, SEMIQUANTITATIVE, DESCRIPTION }
enum class ReferenceAgreement { MATCH, MISMATCH, UNKNOWN }
object LabResultValues {
 private const val comparisonPattern="(?:<=|>=|[<>≤≥])"
 private const val magnitudePattern="[-+]?\\d+(?:\\.\\d+)?(?:[eE][-+]?\\d+)?"
 private val number=Regex("$comparisonPattern?$magnitudePattern")
 internal val sourceNumberPattern="$comparisonPattern?\\s*$magnitudePattern"
 private val comparison=Regex("^$comparisonPattern")
 fun comparator(text:String):String=comparison.find(text.trim())?.value.orEmpty()
 fun numeric(text:String):Double?=text.trim().replace(" ","").takeIf{number.matches(it)}?.replace(comparison,"")?.toDoubleOrNull()?.takeIf{it.isFinite()}
 fun valid(text:String):Boolean=text.isNotBlank()&&text.trim().lowercase() !in setOf("nan","infinity","+infinity","-infinity","∞","+∞","-∞","/","／",":","：","(",")","（","）","~","～","*","%","％","#","＃","=")&&(!number.matches(text.trim())||numeric(text)!=null)
 fun kind(text:String):LabValueKind {
  if(numeric(text)!=null)return LabValueKind.NUMBER
  val t=compact(text)
  if(t.contains("弱阳")||Regex("[1-4]?\\+{1,4}|±").matches(t))return LabValueKind.SEMIQUANTITATIVE
  if(t.contains("阴性")||t.contains("阳性")||t in setOf("未检出","检出","可疑","未见","正常","异常","-"))return LabValueKind.QUALITATIVE
  return LabValueKind.DESCRIPTION
 }
 private fun compact(t:String)=t.trim().replace(" ","").replace('（','(').replace('）',')')
 private fun token(raw:String):String? {
  val t=compact(raw)
  if(t.contains("弱")||t.contains("可疑")||t.contains("不确定")||t.contains("±")||t.contains('?')||t in setOf("待复查","无法判断"))return null
  return when(t){
   "阴性","阴性(-)","(-)","-"->"negative"
   "阳性","阳性(+)","(+)","+"->"positive"
   "未检出","未见"->"absent"
   "检出"->"detected"
   "正常"->"normal"
   "异常"->"abnormal"
   else->null
  }
 }
 fun agreement(result:String,reference:String):ReferenceAgreement {
  if(reference.isBlank())return ReferenceAgreement.UNKNOWN
  if(listOf(result,reference).any{it.contains("弱")||it.contains("可疑")||it.contains("不确定")||it.contains("无法判断")||it.contains("待复查")||it.contains("±")||it.contains('?')})return ReferenceAgreement.UNKNOWN
  val a=token(result);val b=token(reference)
  if(a!=null&&b!=null){
   val absent=setOf("negative","absent");val present=setOf("positive","detected")
   if(a in absent+present&&b in absent+present)return if((a in absent&&b in absent)||(a in present&&b in present))ReferenceAgreement.MATCH else ReferenceAgreement.MISMATCH
   if(a in setOf("normal","abnormal")&&b in setOf("normal","abnormal"))return if(a==b)ReferenceAgreement.MATCH else ReferenceAgreement.MISMATCH
   return ReferenceAgreement.UNKNOWN
  }
  // Graded positives disagree with an explicitly negative reference, without a fake magnitude.
  if(Regex("(?:[1-4]\\+|\\+{2,4})").matches(compact(result))&&b in setOf("negative","absent"))return ReferenceAgreement.MISMATCH
  if(a!=null&&b==null)return ReferenceAgreement.UNKNOWN
  if(result.trim()==reference.trim()&&kind(result)==LabValueKind.DESCRIPTION)return ReferenceAgreement.MATCH
  return ReferenceAgreement.UNKNOWN
 }
}
fun LabResult.resultKind()=LabResultValues.kind(textValue)
fun LabResult.referenceAgreement()=LabResultValues.agreement(textValue,referenceRaw)
fun LabResult.resultStatusLabel():String=if(value!=null)when(status()){
 ResultStatus.LOW->"偏低";ResultStatus.HIGH->"偏高";ResultStatus.NORMAL->"范围内";else->"待判断"
}else when(referenceAgreement()){
 ReferenceAgreement.MATCH->"符合参考";ReferenceAgreement.MISMATCH->"不符合参考";ReferenceAgreement.UNKNOWN->"待判断"
}
fun LabResult.withEditedResult(text:String):LabResult {
 require(LabResultValues.valid(text)){"检测结果不能为空或无效"}
 val clean=text.trim();val number=LabResultValues.numeric(clean)
 val normalized=UnitNormalizer.normalize(metricKey,number,unitAtTest)
 return copy(value=number,textValue=clean,comparator=if(number!=null)LabResultValues.comparator(clean) else "",normalizedValue=normalized.first,normalizedUnit=normalized.second,editedByUser=true)
}

/** Presentation only: never use this text for persistence, export, or agreement. */
internal fun qualitativeDisplayText(raw:String):String {
 val token=raw.trim().filterNot(Char::isWhitespace).replace('（','(').replace('）',')')
 return when(token){
  "阳性(+)","阳性","+","(+)"->"阳性"
  "阴性(-)","阴性","-","(-)"->"阴性"
  "弱阳性(±)"->"弱阳性"
  else->raw
 }
}

/** Compare the possible result interval, never its chart coordinate. No stored fields change. */
internal fun comparisonResultStatus(result:LabResult):ResultStatus {
 val n=result.value?.takeIf{it.isFinite()}?:return ResultStatus.UNKNOWN
 val low=result.referenceLowAtTest;val high=result.referenceHighAtTest
 if('?' in result.referenceRaw || (low==null&&high==null) || (low!=null&&high!=null&&low>high) || result.reportFlag.length>1)return ResultStatus.UNKNOWN
 // Confirmed concentration markers have a nonnegative physical domain. Do not infer
 // this from a reference lower bound: other numeric metrics can legitimately be negative.
 val nonnegative=canonicalMetricKey(result.metricKey) in setOf("CA125","CA153","CA199","CA724","SCC","AFP","CEA","HE4","NSE","CYFRA21-1")
 val c=result.comparator
 val status=when(c){
  "<","<=","≤" -> when {
   nonnegative && (n<0 || (n==0.0&&c=="<"))->ResultStatus.UNKNOWN
   low!=null && (n<low || (n==low&&c=="<"))->ResultStatus.LOW
   (low==null || (nonnegative&&low<=0.0)) && (high==null || n<=high)->ResultStatus.NORMAL
   else->ResultStatus.UNKNOWN
  }
  ">",">=","≥" -> when {
   high!=null && (n>high || (n==high&&c==">"))->ResultStatus.HIGH
   high==null && (low==null || n>=low)->ResultStatus.NORMAL
   else->ResultStatus.UNKNOWN
  }
  else->ResultStatus.UNKNOWN
 }
 return if((result.reportFlag=="Y"&&status==ResultStatus.NORMAL)||(result.reportFlag=="N"&&status in setOf(ResultStatus.LOW,ResultStatus.HIGH)))ResultStatus.UNKNOWN else status
}

/**
 * Threshold coordinate only; source text and comparator remain untouched.
 * Legacy rows can contain a real numeric result without a persisted normalized
 * value. Recompute that display-only coordinate instead of silently dropping
 * the point from the trend.
 */
internal fun LabResult.trendPlotValue():Double? {
 val stored=normalizedValue?.takeIf{it.isFinite()}
 if(stored!=null)return stored
 val numeric=value?.takeIf{it.isFinite()}?:LabResultValues.numeric(textValue)?:return null
 return UnitNormalizer.normalize(metricKey,numeric,unitAtTest).first?.takeIf{it.isFinite()}
}
internal fun LabResult.uncertainComparisonPoint()=comparator.isNotEmpty()&&status()==ResultStatus.UNKNOWN
