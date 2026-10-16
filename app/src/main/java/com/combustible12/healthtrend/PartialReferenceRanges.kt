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
 private val boundPattern = Regex("[-+]?\\d+(?:\\.[\\d?]+)?")
 private val intervalPattern = Regex("^\\s*([-+]?\\d+(?:\\.[\\d?]+)?)\\s*[-–—~～至]+\\s*([-+]?\\d+(?:\\.[\\d?]+)?)\\s*$")

 fun bound(raw: String): PartialReferenceBound? {
  val value = raw.trim()
  if (!boundPattern.matches(value)) return null
  if ('?' !in value) {
   val exact = value.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
   return PartialReferenceBound(value, exact, exact, true)
  }
  val min = value.replace('?', '0').toDoubleOrNull() ?: return null
  val max = value.replace('?', '9').toDoubleOrNull() ?: return null
  if (!min.isFinite() || !max.isFinite()) return null
  return PartialReferenceBound(value, min, max, false)
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
   minOf(upper.maximum, Math.nextDown(result)) else upper.maximum
  val constrainedLower = if (reportFlag == '↓' && result != null && result.isFinite())
   maxOf(lower.minimum, Math.nextUp(result)) else lower.minimum
  if (constrainedLower > lower.maximum || upper.minimum > constrainedUpper ||
   lower.minimum > constrainedUpper) return null
  return PartialReferenceInterval(raw, lower, upper, constrainedLower, constrainedUpper,
   lower.exact && upper.exact)
 }
}
