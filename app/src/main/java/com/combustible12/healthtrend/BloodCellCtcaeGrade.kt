package com.combustible12.healthtrend

internal data class BloodCellCtcaeGrade(
 val grade:Int?,
 val label:String,
 val reason:String?=null
)

/**
 * Display-only CTCAE v5.0 grading for the four blood-count values supported by
 * the trend point dialog. It never changes the report, its status, or treatment
 * guidance. Grade 4 anemia is clinical and therefore cannot be inferred here.
 */
internal fun bloodCellCtcaeV5Grade(result:LabResult):BloodCellCtcaeGrade? {
 val key=canonicalMetricKey(result.metricKey)
 val expectedUnit=when(key){
  "WBC","NEUT#","PLT"->"10^9/L"
  "HGB"->"g/L"
  else->return null
 }
 if(result.comparator.isNotBlank())return BloodCellCtcaeGrade(null,"无法判定","结果带比较符号")
 if('?' in result.referenceRaw||result.reportFlag.length>1)return BloodCellCtcaeGrade(null,"无法判定","参考范围不完整")
 val normalized=UnitNormalizer.normalize(key,result.value,result.unitAtTest)
 val value=normalized.first
 if(value==null||!value.isFinite())return BloodCellCtcaeGrade(null,"无法判定","缺少数值")
 if(normalized.second!=expectedUnit)return BloodCellCtcaeGrade(null,"无法判定","单位无法换算")
 val lower=UnitNormalizer.normalize(key,result.referenceLowAtTest,result.unitAtTest).first
  ?:return BloodCellCtcaeGrade(null,"无法判定","缺少正常下限")
 if(value>=lower)return BloodCellCtcaeGrade(0,"未达到 1 级")
 val grade=when(key){
  "WBC"->when{value<1.0->4;value<2.0->3;value<3.0->2;else->1}
  "NEUT#"->when{value<0.5->4;value<1.0->3;value<1.5->2;else->1}
  "PLT"->when{value<25.0->4;value<50.0->3;value<75.0->2;else->1}
  // CTCAE v5.0 grade 4 anemia requires life-threatening consequences and
  // urgent intervention, which a laboratory value alone cannot establish.
  "HGB"->when{value<80.0->3;value<100.0->2;else->1}
  else->return null
 }
 return BloodCellCtcaeGrade(grade,"G$grade")
}

internal fun bloodCellCtcaeV5Display(result:LabResult):String? =
 bloodCellCtcaeV5Grade(result)?.let{grade->
  "CTCAE v5.0 分级：${grade.label}"+grade.reason?.let{"（$it）"}.orEmpty()
 }
