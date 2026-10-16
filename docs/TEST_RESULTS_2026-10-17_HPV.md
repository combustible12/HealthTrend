# HPV核酸词典扩建测试

基线f0279a96d463331db5b2eb3676ecb036268d9db7。新增三个核酸身份及别名/类别，不修改历史数据。UnifiedMetricStandardAuditTest增加组合与单型隔离、泛称不自动映射的测试。实际运行ci/run_report_jvm.py：90/90通过，包含新增HPV词典身份测试及现有导入/保存/备份回归；git diff --check通过；按用户最新规则只提交代码，不发布APK，release/ready.json保持原样；未运行实机测试。

## 用户明确授权后的正式发布

版本1.0.110 / 2026101712；发布提交b4b4b8ac42e43f66c9ddb4301bfbb8b86b11d98b。run38959452373/job117170539995成功：compileDebugKotlin、196 tests / 0 failures / 0 errors / 0 skipped、compileDebugAndroidTestKotlin、assembleRelease及发布通过。

APK：https://github.com/combustible12/HealthTrend/releases/download/v2026101712/HealthTrend.apk 。实际下载56884976字节，SHA256 2f5c03d18ea6e92930d6f4d9a26829980ed3e021512c574ce6b8428e612b48bf；ZIP CRC、manifest包名com.combustible12.healthtrend/版本、digest、apksig验证通过。证书SHA256 5acccb7b281fbe2878ff8e62b57e4a1149c82bcf40e3c0b36d8e709c9da95628与旧版相同，可覆盖保留资料。未实际手机升级或运行设备UI。此次仅用户明确要求后触发一次正式构建，无空提交/重复触发。
