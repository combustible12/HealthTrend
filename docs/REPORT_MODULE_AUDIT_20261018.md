# 检验报告模块核查：2026-10-18

## 目标与根因
用户要求整模块核查。此次不是扩大医学词典或改写患者数据，核查对象是同一输入穿过不同业务入口时，名称、身份、结果和来源是否保持一致。

| 环节 | 核查依据 | 结论与处理 |
|---|---|---|
| 文本预处理及分行 | ReportImport、ReportParser.normalizeSourceCodes/segments | 只清理前置检验标记与空白；原始全文保留；代码行解析失败保留待核对行及输入位置 |
| 名称/结果边界 | StructuredReportRows、ReportParser三个解析分支 | 共用名称前置标记处理，保留内部横杠/括号/数字；完整名称先于结果识别 |
| 词典与身份 | UnifiedMetricStandard、HealthModels、HealthStore.reconcileParsedMetrics | 代码/源名称/标准显示名独立；符号完整性策略共用；冲突不清空独立读出的字段，不靠单位猜身份 |
| 核对及手工编辑 | Editors、ReportEditing、retargetImportedDraft | 待核对仍阻止保存，原字段可见，必要时显示原始行；更正身份无需重填正确结果；重匹配不能抹掉冲突 |
| 来源元数据 | ReportMetadata、ReportNaming、ReportCategoryPolicy | 多医院不选最短；多日期冲突不猜选；保持既有报告/采样日期优先策略，不从无原图的文本猜错日期 |
| 保存及替换 | HealthStore.saveReport/validateReplacementReport、HistoryReplacement | 新/改身份行共用名称校验；保留ID、来源字段和图片；失败不覆盖旧报告；既有兼容读取不自动迁移 |
| 导出与重开 | HealthStore.exportReportAsPastedText、ReportDraft.from | 优先源名称；无名称才回填词典；结果精度、单位、范围随记录保存，不受标准显示名覆盖 |
| 历史与备份 | ReportDraftStore序列化、HealthBackup、LegacyReportCompatibility | 不变更存储模型/序列化UID；旧模板不参与新报告；不执行批量重写；原有恢复/替换回归继续运行 |
| 删除与列表 | Records、ReportDeleteDialog、HealthStore.deleteReport | 确认目标后只删除目标ID；取消不改记录；不删除其他报告 |

## 本地验证
115项聚焦JVM通过，7308种标记与名称排版组合通过；148项带标记词典项目逐项保存、编辑、重开及回导通过。新增缺失代码行保留顺序、混合医院/日期拦截、身份冲突保留原字段及手工/存储同一规则测试。原有代码/符号/比较符/原单位/参考范围/旧报告兼容测试继续交给Android全量运行。

## 验收边界
本地存储验证使用ci/jvm磁盘Android替身，不冒称设备测试。最终Android全量/页面操作结果另行记录；当前尚未发布此整模块版本。没有将用户照片真实数值、患者信息或原图提交公开仓库。标准词典不是医院方法认证，遇到无法确定医学身份仍需人工核对。
