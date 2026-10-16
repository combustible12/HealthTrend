# HPV核酸词典扩建测试

基线f0279a96d463331db5b2eb3676ecb036268d9db7。新增三个核酸身份及别名/类别，不修改历史数据。UnifiedMetricStandardAuditTest增加组合与单型隔离、泛称不自动映射的测试。实际运行ci/run_report_jvm.py：90/90通过，包含新增HPV词典身份测试及现有导入/保存/备份回归；git diff --check通过；按用户最新规则只提交代码，不发布APK，release/ready.json保持原样；未运行实机测试。
