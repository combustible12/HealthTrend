# 血型四项资料卡片测试记录

检测日期追加要求：血型卡片显示、编辑和保存YYYY-MM-DD检测日期，可空且不默认今天；旧版缺失日期为空，备份无损保留。新增日期有效性/编辑保留测试，最终验证待执行。

基线：2b6ba4f71c0e19e609d890d90cb021ae1d71eee1。

改动：BloodGroupProfile、HealthStore、HealthBackup、Updates。新增独立字段，不改PatientProfile序列化结构，不修改历史报告/病程。

验证：本地真实存储代码及JVM测试实际84/84通过（包括8项模型测试及存储/备份隔离用例）；最终发布流程必须编译Kotlin、运行全部JVM测试和编译设备测试后才构建APK。尚未实机测试。
