# 白球比例识别别名

最终验证补记：A/G别名随最终源码8b2bfa73ca104a7dcf34d3f9b8609a538c7150ae在quick39001882155的全部JVM、Kotlin及设备测试代码编译中通过，未发布；此前基线换行错误已修复，见TEST_RESULTS_2026-10-17_CATALOGUE_ADAPTATION.md。下方待执行内容为历史记录。

基线45fc78ead61709e00e31b560add955c17f027a6e。UnifiedMetricStandard的A/G新增别名“白球比例”；不新增指标、不调整标准范围、单位、历史报告或病程。

UnifiedMetricStandardAuditTest新增合成数据回归：中文别名/代码均解析为A/G，保留结果1.750、无单位和范围1.0-2.5，保持与转氨酶比值分离。git diff --check通过；Android Kotlin/JVM单测/设备测试代码编译待运行。不发布。
