# 旧模板退出与历史报告接管：验证证据

基线 main：5459dbd1a111f08e4d6cc8420f21babd127ca122。

- 生产核心 Kotlin/JVM 编译：通过。
- 聚焦 JUnit：49/49 通过，`OK (49 tests)`；2.131 秒，不含编译。
- 同步检查器自检：6/6 通过。
- 空白差异检查：通过。
- Android 编译、设备/用户验收、APK 发布：未执行。
- 已知实现待办：本轮静态排查与 JVM 覆盖范围内 0；Android 待验 3 项，详见当前测试台账。

运行：`KOTLIN_TEST_LIBS=<本机依赖目录> python ci/run_report_jvm.py`。
依赖：JDK17，Kotlin 编译器及标准库、JSON、JUnit4、Hamcrest；脚本头注明文件名。只使用合成数据。

| 测试类 | 覆盖 |
|---|---|
| UnifiedMetricStandardAuditTest | 稳定身份、中文名称、歧义别名及CV/SD分离 |
| MetricImportRegressionTest | eae23bf、模板不影响新报告、来源单位/精度、计数/百分比、正反比值、历史PDW保留 |
| HistoryReplacementTest | 显式旧报告修复的历史兼容边界；不自动执行 |
| ci/jvm/ReportTakeoverTest | 新报告无模板保存、旧报告读取不写回、编辑/重新打开、未改字段保留、图片、明确关联/撤销/冲突、实际ZIP独立目录恢复、损坏拦截、模板及快照、已有体重保护 |

JVM 替身实现磁盘/XML及文件URI。实际备份/校验/恢复、生产存储和保存代码均运行；不能代替 Android 原生偏好缓存、进程重启、URI授权及Compose界面验证。

旧模板有意保留的边界：原始持久化 JSON、备份、冻结趋势说明、用户显式选文件的历史修复工具、旧构造器/修复器与隔离兼容解析回归函数。普通新报告没有这些调用。`ReportDraft.newTemplate` 仅保留旧序列化结构，编辑器模板布尔回调已删除。

最终没有失败的聚焦断言。未运行整份 ReportParserTest（包含Compose/趋势依赖）、图片/草稿测试及Android测试；不计通过。规则、台账、进度和交接同批同步。趋势函数/绘制、病程与原始真实患者资料未改动。
