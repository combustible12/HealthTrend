package com.combustible12.healthtrend

internal data class BloodCellCtcaeGrade(
 val grade:Int?,
 val label:String,
 val reason:String?=null,
 val advice:String?=null
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
 fun unable(reason:String)=BloodCellCtcaeGrade(null,"暂时无法分级",reason)
 if(result.comparator.isNotBlank())return unable("结果带比较符号")
 if('?' in result.referenceRaw||result.reportFlag.length>1)return unable("参考范围不完整")
 val normalized=UnitNormalizer.normalize(key,result.value,result.unitAtTest)
 val value=normalized.first
 if(value==null||!value.isFinite())return unable("缺少数值")
 if(normalized.second!=expectedUnit)return unable("单位无法换算")
 val lower=UnitNormalizer.normalize(key,result.referenceLowAtTest,result.unitAtTest).first
  ?:return unable("缺少正常下限")
 if(value>=lower)return BloodCellCtcaeGrade(0,"未达到 1 级",advice=bloodCellCtcaeAdvice(key,0))
 val grade=when(key){
  "WBC"->when{value<1.0->4;value<2.0->3;value<3.0->2;else->1}
  "NEUT#"->when{value<0.5->4;value<1.0->3;value<1.5->2;else->1}
  "PLT"->when{value<25.0->4;value<50.0->3;value<75.0->2;else->1}
  // CTCAE v5.0 grade 4 anemia requires life-threatening consequences and
  // urgent intervention, which a laboratory value alone cannot establish.
  "HGB"->when{value<80.0->3;value<100.0->2;else->1}
  else->return null
 }
 return BloodCellCtcaeGrade(grade,"G$grade",advice=bloodCellCtcaeAdvice(key,grade))
}

private fun bloodCellCtcaeAdvice(key:String,grade:Int):String {
 if(grade==0)return "建议：无"
 if(grade==1)return when(key){
  "WBC","NEUT#"->"建议：轻度降低。38℃或寒战等，就医。"
  "PLT"->"建议：轻度降低。出血不止、黑便、血尿或剧烈头痛时，就医。"
  "HGB"->"建议：轻度降低。出现气促、胸痛、明显头晕或晕厥时，就医。"
  else->"建议：轻度降低。"
 }
 if(grade==2)return "处理：复查血常规。"
 return when(key){
  "WBC","NEUT#"->"可能处理：升白针、抗感染或调整治疗。"
  "PLT"->"可能处理：输注血小板或调整治疗。"
  "HGB"->if(grade==3)"可能处理：输血。" else "急救。"
  else->""
 }
}

internal fun bloodCellCtcaeV5Display(result:LabResult):String? =
 bloodCellCtcaeV5Grade(result)?.let{grade->
  "CTCAE v5.0 分级：${grade.label}"+grade.reason?.let{"（$it）"}.orEmpty()
 }
