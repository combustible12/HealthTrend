# 不完整参考范围：实际测试与发布记录（2026-10-17）

## 最终结果

- 基线：ea68502883a41d44502b043d237c4e4b93376cc1。
- 最终功能修复：606fb9e8688a6ce66f17e2b810cdb8048b57b3fc。
- 发布提交：1890cf91a0f694447b98aa6aa530423d2305d828。
- 本地实际执行 `KOTLIN_TEST_LIBS=/tmp/ht-kotlin2 python ci/run_report_jvm.py`，Kotlin 2.0.21 聚焦 JVM 75/75 通过。存储/备份部分使用明确标记的磁盘型 Android doubles，不冒称真机。
- [Android compile check 38943369594](https://github.com/combustible12/HealthTrend/actions/runs/38943369594)：compileDebugKotlin 和 testDebugUnitTest 成功。
- [正式发布 38943657050](https://github.com/combustible12/HealthTrend/actions/runs/38943657050)：最终 testDebugUnitTest XML 统计 151/151，失败0、错误0、跳过0；assembleRelease 成功，发布成功。变更同步守卫38943657053成功。
- `ci/check_report_ocr_boundary.py`、同步守卫自测及本地变更同步检查实际通过。

## 覆盖与限制

- PartialReferenceRangesTest：完整范围、问号、无箭头、↑约束、矛盾、按小数位收紧到0.249、非连续未知位、负数/整数未知位。
- PartialRangeIntegrationTest：真实解析/编辑原文保留、单位隔离、完整结果和确定端点进入趋势、不生成未知上限、无提示不误判、↓约束、矛盾需核对、旧精确边界兼容、Android Compose 旧草稿及嵌入报告反序列化。
- ci/jvm/PartialRangeStorageTest：真实HealthStore保存、从独立Context重读、编辑、文本导出再导入、原精度及箭头内部证据保持、标准范围池不污染、course_records和读取原文不改写。
- 原有ReportTakeoverTest/HistoryReplacementTest等覆盖历史报告只读接管、字段/图片保留、备份恢复、替换/撤销及旧存储兼容；原有趋势相关单测随Android全量测试运行。
- 未执行真实手机覆盖安装、Compose渲染截图/手势实测或患者手机数据检查；趋势绘制安全性的自动证据来自边界投影与原有逻辑回归，不冒称完整像素级UI验收。

## 失败处理（均已修复，没有跳过）

1. 首轮本地编译发现辅助方法误放到ReportDraft及String.distinct调用错误，修正后重新编译。
2. 首轮Android quick38942327864编译成功，150项中2项旧断言强制把42.00改42.0、9/50改9.0/50.0，与保留来源精度冲突；只更新对应预期，保留原测试行为。
3. Android quick38942726674编译成功，151项中旧草稿兼容测试失败。先核对Kotlin1.9/2.0，确认单独编译UID一致；进一步确认Compose插件添加public $stable字段导致Android默认UID变化。以基线源码+Kotlin2.0.21+同版Compose插件重新生成合成样本，明确保存原Android UID并对新增字段提供readResolve空默认值；最终Android测试通过。
4. 同步守卫曾因仅更换资源样本未更新Kotlin用例文件而失败，已同步用例名称及样本来源说明，未禁用守卫。

## APK验证

- 版本：1.0.100，versionCode2026101702；包名com.combustible12.healthtrend。
- [下载APK](https://github.com/combustible12/HealthTrend/releases/download/v2026101702/HealthTrend.apk)，56868592字节，已实际下载成功。
- SHA-256：aa49521ee7ff19d40b5e42de0775c0112c0fba3e03008a2d2278ae3049c21c0d，匹配GitHub资产digest。
- APK ZIP全量CRC验证通过，manifest版本/包名验证通过，apksig实际验证签名通过。
- 旧版v2026101701与新版证书SHA-256均为5acccb7b281fbe2878ff8e62b57e4a1149c82bcf40e3c0b36d8e709c9da95628；支持原安装直接覆盖，保留原私有数据。没有执行卸载、清数据或历史批量迁移。
