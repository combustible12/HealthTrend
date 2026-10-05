package com.combustible12.healthtrend

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Values are supplied by a private, explicitly selected file, never bundled in the app. */
data class HistoryReplacementBatch(
 val hospital:String,val type:String,val system:String,
 val metricKeys:List<String>,val reports:List<HistoryReplacementRow>
)
data class HistoryReplacementRow(val date:LocalDate,val values:List<String>)

internal fun replaceReportHistory(
 all:List<LabReport>,template:HospitalLabTemplate,batch:HistoryReplacementBatch,
 zone:ZoneId=ZoneId.systemDefault()
):List<LabReport>{
 require(template.confirmed){"请先确认医院模板"}
 require(template.hospitalKey==batch.hospital&&template.reportType==batch.type&&template.systemKey==batch.system){"医院、检查类型或参考系统不一致"}
 val keys=template.fields.map{it.metricKey}
 require(keys.isNotEmpty()&&keys.all{it.isNotBlank()}&&keys.distinct().size==keys.size){"当前模板项目 ID 无效或重复"}
 require(batch.metricKeys==keys){"核对文件的项目 ID 或顺序与当前模板不一致"}
 require(batch.reports.isNotEmpty()&&batch.reports.map{it.date}.distinct().size==batch.reports.size){"核对文件日期为空或重复"}
 require(all.map{it.id}.distinct().size==all.size){"已有报告 ID 重复，未写入任何数据"}
 batch.reports.forEach{row->
  require(row.values.size==keys.size){"${row.date} 项目数量不完整"}
  require(row.values.all{it.toDoubleOrNull()?.isFinite()==true}){"${row.date} 存在空值或无效数值"}
 }
 val dates=batch.reports.map{it.date}.toSet()
 fun date(r:LabReport)=Instant.ofEpochMilli(r.testedAtEpochMillis).atZone(zone).toLocalDate()
 val targets=all.filter{it.hospitalKey==batch.hospital&&it.reportType==batch.type&&it.systemKey==batch.system&&date(it) in dates}
 require(targets.size==dates.size&&targets.groupBy(::date).let{it.keys==dates&&it.values.all{rs->rs.size==1}}){"目标日期存在缺失或重复报告，未写入任何数据"}
 val values=batch.reports.associate{it.date to it.values}
 val replaced=targets.associate{r->
  val texts=values.getValue(date(r))
  val results=template.fields.mapIndexed{i,field->
   val text=texts[i];val value=text.toDouble();val unit=displayLabUnit(field.unit)
   val normalized=UnitNormalizer.normalize(field.metricKey,value,unit)
   LabResult(newId(),r.id,r.hospitalKey,r.reportType,template.version,field.metricKey,
    field.displayName,value,unit,field.referenceLow,field.referenceHigh,r.testedAtEpochMillis,
    true,text,"","",normalized.first,normalized.second)
  }
  r.id to r.copy(templateVersion=template.version,results=results)
 }
 return all.map{replaced[it.id]?:it}
}
