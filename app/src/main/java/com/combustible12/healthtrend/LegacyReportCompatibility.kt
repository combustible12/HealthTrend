package com.combustible12.healthtrend

/** Historical parser regression compatibility only. No production new-report caller.
 * Never reconnect these template-driven helpers to import, review, or saving.
 * Stored template JSON and the frozen trend explanation reader remain supported.
 */
/** Clipboard values are explicit, unlike noisy OCR. Share template matching but
 * do not apply OCR's independent-result heuristic to typed unit strings. */
internal fun templateDrivenPastedResults(items:List<ParsedLabResult>,template:HospitalLabTemplate):List<ParsedLabResult>{
 val matches=matchTemplateRows(template.fields,items)
 // If multiple clipboard rows have the same canonical identity, never silently
 // choose one value: keep both sources for review rather than binding the first.
 val repeated=items.groupingBy{canonicalMetricKey(it.metricKey)}.eachCount()
 val accepted=matches.filter{(index,source)->
  source.textValue.isNotBlank() &&
   repeated[canonicalMetricKey(source.metricKey)]==1 &&
   canonicalMetricKey(template.fields[index].metricKey)==canonicalMetricKey(source.metricKey)
 }
 val bound=template.fields.mapIndexedNotNull{index,field->
  val source=accepted[index]?:return@mapIndexedNotNull null
  // Template supplies identity and display name only. Printed result, unit, and
  // reference interval remain test-time facts even when the template differs.
  source.copy(metricKey=canonicalMetricKey(field.metricKey),displayName=source.displayName,
   unit=source.unit.ifBlank{field.unit},
   referenceLow=source.referenceLow?:field.referenceLow,
   referenceHigh=source.referenceHigh?:field.referenceHigh,
   primary=field.metricKey in ReportParser.primaryKeys)
 }
 val used=accepted.values.toSet()
 return bound+items.filter{it !in used}

}

/**
 * Once a hospital/panel template is confirmed it owns the fixed row structure.
 * OCR contributes only this visit's value/text/comparator/raw source. A damaged OCR
 * code therefore cannot create a second row or overwrite confirmed unit/range/name.
 */
internal fun templateDrivenResults(items:List<ParsedLabResult>,template:HospitalLabTemplate):List<ParsedLabResult>{
 val matches=matchTemplateRows(template.fields,items)
 // A template is an identity/fixed-field skeleton, not a checklist for every visit.
 // Only metrics that are actually present in this report become visit results.
 val matched=template.fields.mapIndexedNotNull{index,field->
  val source=matches[index] ?: return@mapIndexedNotNull null
  val reliable=templateResultIsIndependent(source,field)
  if(!reliable)return@mapIndexedNotNull null
  ParsedLabResult(
   metricKey=field.metricKey,
   displayName=field.displayName,
   value=source.value,
   unit=displayLabUnit(field.unit),
   referenceLow=field.referenceLow,
   referenceHigh=field.referenceHigh,
   rawLine=source.rawLine,
   primary=field.metricKey in ReportParser.primaryKeys,
   textValue=source.textValue,
   comparator=source.comparator
 )
 }
 // A template candidate rejected by the independent-result check is not
 // consumed: retain its raw source for review rather than silently losing it.
 val consumed=matches.filter{(index,source)->
  templateResultIsIndependent(source,template.fields[index])
 }.values.toSet()
 return matched + items.filter{it !in consumed}.map{it.copy(metricKey=if(it.metricKey.startsWith("待核对:"))it.metricKey else "待核对:"+it.metricKey)}
}

/**
 * Matches the whole OCR panel to the confirmed template in one pass.  Fixed fields are
 * never copied from OCR; matching only identifies which visit value belongs to each
 * template row.  A candidate can be consumed once, so one damaged OCR row cannot fill
 * several similarly named template fields.
 */
internal fun matchTemplateRows(fields:List<LabFieldTemplate>,items:List<ParsedLabResult>):Map<Int,ParsedLabResult>{
 // Match exact codes first. Use a Chinese name/alias only when it identifies
 // exactly one field and exactly one source. Never guess by row position.
 fun chinese(name:String)=name.trim().replace(Regex("(?i)^[A-Z][A-Z0-9/#%+.-]*\\s+(?=[\\p{IsHan}])"),"")
 val assigned=mutableMapOf<Int,ParsedLabResult>()
 val used=mutableSetOf<Int>()
 fields.forEachIndexed{index,field->
  val candidates=items.indices.filter{itemIndex->
   itemIndex !in used && !items[itemIndex].metricKey.startsWith("待核对:") &&
    canonicalMetricKey(items[itemIndex].metricKey)==canonicalMetricKey(field.metricKey)
  }
  val competingIdentities=fields.count{other->
   canonicalMetricKey(other.metricKey)==canonicalMetricKey(field.metricKey)
  }
  if(candidates.size==1&&competingIdentities==1){
   assigned[index]=items[candidates.single()]
   used.add(candidates.single())
  }
 }
 fields.forEachIndexed{index,field->
  if(index in assigned)return@forEachIndexed
  val targetName=chinese(field.displayName)
  val candidates=items.indices.filter{itemIndex->
   if(itemIndex in used)return@filter false
   val source=items[itemIndex]
   val sourceName=chinese(source.displayName)
   // Never remap a known explicit code to a different template identity by name.
   val unresolved=source.metricKey.isBlank()||source.metricKey=="未识别"||
    source.metricKey.startsWith("待核对:")
   unresolved && sourceName.isNotBlank()&&sourceName.any{it.code in 0x3400..0x9FFF}&&
    (sourceName==targetName||canonicalMetricKey(ReportParser.key(sourceName))==canonicalMetricKey(field.metricKey))
  }
  // Truncated printed labels are not identities: two template fields may
  // share the same Chinese prefix while their codes, units and results differ.
  // Name-only matching is allowed only for a unique name on BOTH sides and
  // only when the printed unit is compatible with the confirmed field.
  val competingFields=fields.indices.count{other->
   chinese(fields[other].displayName)==targetName
  }
  val sameSourceNames=items.indices.count{itemIndex->
   itemIndex !in used && chinese(items[itemIndex].displayName)==targetName
  }
  val unitCompatible=candidates.singleOrNull()?.let{itemIndex->
   val printed=displayLabUnit(items[itemIndex].unit).trim()
   val expected=displayLabUnit(field.unit).trim()
   printed.isNotBlank() && expected.isNotBlank() && printed.equals(expected,true)
  }==true
  if(candidates.size==1&&competingFields==1&&sameSourceNames==1&&unitCompatible){
   assigned[index]=items[candidates.single()];used.add(candidates.single())
  }
 }
 return assigned
}

/**
 * A template range printed without a readable result must never be promoted to the
 * visit value.  This is the dangerous OCR shape behind "2.00" replacing "2.57".
 */
internal fun templateResultIsIndependent(source:ParsedLabResult,field:LabFieldTemplate):Boolean{
 if(source.textValue.isBlank())return false
 val cleaned=source.rawLine
  .replace(Regex("(?i)[×x]?10\\s*\\^?\\s*[-+]?\\d+\\s*/\\s*[lL]")," ")
  .replace(Regex("^\\s*\\d+[.、]?\\s+(?=[A-Za-z#%\\p{IsHan}])"),"")
 val number=Regex("(?<![A-Za-z\\d.^])(?:[<>≤≥]\\s*)?[-+]?\\d+(?:\\.\\d+)?")
 val first=number.find(cleaned)?:return false
 val twoSided=Regex("[-+]?\\d+(?:\\.\\d+)?\\s*(?:-{1,2}|–|—|~|～|至)\\s*[-+]?\\d+(?:\\.\\d+)?").find(cleaned)
 val oneSided=Regex("(?:<=|>=|[<>≤≥])\\s*[-+]?\\d+(?:\\.\\d+)?|[-+]?\\d+(?:\\.\\d+)?\\s*--(?:\\s|$)").find(cleaned)
 val printedRange=twoSided?:oneSided
 // A result is a separate token before the printed range. If the first number is the
 // range itself, leave the visit value empty rather than copying a boundary.
 return printedRange==null||first.range.last<printedRange.range.first
}

