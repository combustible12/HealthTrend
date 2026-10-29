package com.combustible12.healthtrend

internal data class BloodCellCtcaeGrade(
 val grade:Int?,
 val label:String,
 val reason:String?=null,
 val advice:String
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
 fun unable(reason:String)=BloodCellCtcaeGrade(null,"无法判定",reason,bloodCellCtcaeAdvice(key,null))
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

private fun bloodCellCtcaeAdvice(key:String,grade:Int?):String {
 if(grade==null)return "请先核对原报告的数值、单位和正常下限，必要时咨询治疗团队。"
 if(grade==0)return "无需因本项分级额外处理，按原计划复查。"
 val urgent=when(key){
  "WBC","NEUT#"->"体温达到 38℃或出现寒战等感染表现时，立即联系治疗团队或就医。"
  "PLT"->"出现出血不止、黑便、血尿或剧烈头痛时，立即就医。"
  "HGB"->"出现气促、胸痛、明显头晕或晕厥时，立即就医。"
  else->"症状明显或迅速加重时，及时就医。"
 }
 return when(grade){
  1->"轻度降低，按原计划复查。$urgent"
  2->"尽快联系治疗团队，确认复查时间和后续治疗安排。$urgent"
  3->"当天联系治疗团队；是否延迟或调整治疗、采取支持处理，由医生评估。$urgent"
  else->"立即联系治疗团队。$urgent"
 }
}

internal fun bloodCellCtcaeV5Display(result:LabResult):String? =
 bloodCellCtcaeV5Grade(result)?.let{grade->
  "CTCAE v5.0 分级：${grade.label}"+grade.reason?.let{"（$it）"}.orEmpty()
 }
