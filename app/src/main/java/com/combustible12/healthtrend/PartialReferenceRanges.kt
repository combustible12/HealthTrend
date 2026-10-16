package com.combustible12.healthtrend

/**
 * An unreadable digit is represented by '?', never by a guessed digit.
 * Original report arrows are evidence for constraining a bound, not UI data.
 */
internal data class PartialReferenceBound(
 val raw: String,
 val minimum: Double,
 val maximum: Double,
 val exact: Boolean
)

internal data class PartialReferenceInterval(
 val raw: String,
 val lower: PartialReferenceBound,
 val upper: PartialReferenceBound,
 val lowerLimit: Double,
 val upperLimit: Double,
 val exact: Boolean
)

internal object PartialReferenceRanges {
 private val boundPattern = Regex("[-+]?[\\d?]+(?:\\.[\\d?]+)?")
 private val intervalPattern = Regex("^\\s*([-+]?[\\d?]+(?:\\.[\\d?]+)?)\\s*[-–—~～至]+\\s*([-+]?[\\d?]+(?:\\.[\\d?]+)?)\\s*$")

 /** Raw editor endpoints; retain comparison signs in the original text. */
 fun parts(raw:String):Pair<String,String> {
  val interval=intervalPattern.matchEntire(raw)
  if(interval!=null)return interval.groupValues[1] to interval.groupValues[2]
  val text=raw.trim()
  return when {
   text.startsWith("<")||text.startsWith("≤")->"" to text.trimStart('<','=','≤').trim()
   text.startsWith(">")||text.startsWith("≥")->text.trimStart('>','=','≥').trim() to ""
   text.endsWith("--")->text.removeSuffix("--").trim() to ""
   else->"" to ""
  }
 }
 fun bound(raw: String): PartialReferenceBound? {
  val value = raw.trim()
  if (!boundPattern.matches(value) || value.none{it.isDigit()} && '?' !in value) return null
  if ('?' !in value) {
   val exact = value.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
   return PartialReferenceBound(value, exact, exact, true)
  }
  val min = value.replace('?', '0').toDoubleOrNull() ?: return null
  val max = value.replace('?', '9').toDoubleOrNull() ?: return null
  if (!min.isFinite() || !max.isFinite()) return null
  return PartialReferenceBound(value, minOf(min,max), maxOf(min,max), false)
 }

 /**
  * Flag '↑' means result > true upper bound; '↓' means result < true lower bound.
  * Do not turn this evidence into an invented exact reference line.
  */
 fun interval(raw: String, result: Double? = null, reportFlag: Char? = null): PartialReferenceInterval? {
  val match = intervalPattern.matchEntire(raw) ?: return null
  val lower = bound(match.groupValues[1]) ?: return null
  val upper = bound(match.groupValues[2]) ?: return null
  val constrainedUpper = if (reportFlag == '↑' && result != null && result.isFinite())
   strictCandidate(upper, result, false) ?: return null else upper.maximum
  val constrainedLower = if (reportFlag == '↓' && result != null && result.isFinite())
   strictCandidate(lower, result, true) ?: return null else lower.minimum
  if (constrainedLower > lower.maximum || upper.minimum > constrainedUpper ||
   constrainedLower > constrainedUpper) return null
  return PartialReferenceInterval(raw, lower, upper, constrainedLower, constrainedUpper,
   lower.exact && upper.exact)
 }
 /** Digit-wise search keeps every printed digit, including holes such as 0.2?5.
  * The search is bounded by the number of digits, rather than enumerating 10^? values.
  */
 private fun strictCandidate(bound:PartialReferenceBound,result:Double,above:Boolean):Double? {
  val negative=bound.raw.startsWith("-")
  val mask=bound.raw.filter{it.isDigit()||it=='?'}
  val scale=bound.raw.substringAfter('.',"").length
  val ascending=if(negative)!above else above
  val value=java.math.BigDecimal.valueOf(if(negative)-result else result).movePointRight(scale)
  val threshold=if(ascending)value.setScale(0,java.math.RoundingMode.FLOOR).toBigInteger()+java.math.BigInteger.ONE
   else value.setScale(0,java.math.RoundingMode.CEILING).toBigInteger()-java.math.BigInteger.ONE
  val max=java.math.BigInteger.TEN.pow(mask.length)-java.math.BigInteger.ONE
  if(ascending&&threshold>max || !ascending&&threshold.signum()<0)return null
  val target=threshold.coerceIn(java.math.BigInteger.ZERO,max).toString().padStart(mask.length,'0')
  fun search(index:Int,tight:Boolean):String? {
   if(index==mask.length)return ""
   val choices=if(mask[index]=='?') (if(ascending)'0'..'9' else '9' downTo '0').toList() else listOf(mask[index])
   for(digit in choices){
    if(tight && (if(ascending)digit<target[index] else digit>target[index]))continue
    val suffix=search(index+1,tight&&digit==target[index])
    if(suffix!=null)return digit+suffix
   }
   return null
  }
  val digits=search(0,true)?:return null
  return java.math.BigDecimal(java.math.BigInteger(digits),scale).toDouble()*(if(negative)-1 else 1)
 }

}
