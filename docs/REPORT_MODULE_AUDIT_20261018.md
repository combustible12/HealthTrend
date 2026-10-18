## 2026-10-18 本轮整模块核查完成：1.0.126已发布并核验

本节为当前状态，覆盖下方“仅本地/验收中/未发布”历史描述。源码d0fdf56f873060aa0cdb1c152930990ca3e4c696；Android完整验收39107694821（job117617004577）成功。已实际下载artifact12129316879读取HTML/XML：281项JVM、29项Android16设备测试全部通过，0失败0错误0忽略/跳过；7308种名称/标记/排版输入矩阵也在该完整运行中0失败。新增148项带标记词典项目存储/编辑/重开/导出回导及既有报告保持相等验证通过。本地115项替身回归只作补充，不替代Android结果。

本轮按文本预处理、完整名称与结果边界、词典身份、核对编辑、普通保存/确认替换、来源元数据、列表删除、重开与导出、历史和备份边界审查。根因：入口间名称标记处理不一致，冲突保护清空独立读出的字段，无法解析的代码行可被漏掉，多医院候选被猜选。已统一前置标记及符号校验；真正身份冲突只保持待核对状态，不清空原名称/结果/比较符/单位/范围；缺行保留原位置并禁止保存；多医院来源不猜选。人工纠正完整名称和代码后可直接保存原结果，设备操作已通过。旧模板不参与新导入，没有新增存储迁移或批量改写历史记录；未改TreatmentVault。

已从设备日志恢复并查看thyroid-star-names截图：游离-T3、游离-T4、促甲状腺素(III代)名称、原结果和参考范围保留，保存按钮可用。用户原始真实数值/照片未提交仓库，测试全为合成数据。手机实际覆盖升级未直接操作，不冒称已实测。

正式发布提交c9015ff270553b4e77745db46259747a856d8022；Publish HealthTrend运行39108121632成功，完成最终回归及签名构建。已下载56,917,744字节APK；ZIP完整性、包名com.combustible12.healthtrend、versionCode2026101810、versionName1.0.126均通过；SHA256=bc597f9fee8ad8014d66b2ee2b178ebb2558534ef08a359d7c1ad074c2fa0656，与GitHub asset digest一致。apksig实际验签通过，证书SHA256=5acccb7b281fbe2878ff8e62b57e4a1149c82bcf40e3c0b36d8e709c9da95628，与旧版一致，可覆盖升级。

下载：https://github.com/combustible12/HealthTrend/releases/download/v2026101810/HealthTrend.apk

更新后请重新粘贴旧版生成的错误草稿；不会自动重算或猜改已保存历史报告。原始文本本身写错日期，仍需对照原图人工纠正。详细审查范围见REPORT_MODULE_AUDIT_20261018.md；本轮验收覆盖已列输入和业务路径，不代表所有未来格式无需适配。

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
