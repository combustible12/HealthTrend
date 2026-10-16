# 更新缓存重复安装修复：实际结果（2026-10-17）

- 用户报告反复安装1.0.102；实际代码确认缓存优先，validate有当前版本变量但缺少newer判断。已修复而不是只调整显示版本。
- UpdateVersionPolicyTest 7项：同版拒绝、降版拒绝、新版接受、旧缓存不能抢先于最新版、已安装缓存不复用、新版一致缓存可复用、无远端版本参数仍拒绝同版。独立Kotlin2.0.21编译并实际运行7/7通过。
- 更新入口先check，再resumableDownloadedApk(expectedVersion)，最后download/install；check过滤非新版，validate/install入口都必须严格高于已安装版，校验包名和签名保持不变。HTTP缓存禁用且latest查询带独立参数。
- UpdateGuardTest新增installedVersionCacheIsDiscardedInsteadOfOpeningInstallerAgain；已有downloadedApkMustBelongToHealthTrendAndBeNewer再次覆盖原来缺失的严格版本要求。设备测试已编译，未在设备运行，不冒称实机通过。
- 发布提交350fae9d6c57edf1c0487095fd21bef72e0e02b7；[run38949130010](https://github.com/combustible12/HealthTrend/actions/runs/38949130010)成功；compileDebugKotlin、testDebugUnitTest、compileDebugAndroidTestKotlin及assembleRelease通过。XML统计158 tests/0 failures/0 errors/0 skipped。同步守卫38949130018成功，本地同步/OCR边界检查通过。
- 正式版1.0.104（2026101706），包名com.combustible12.healthtrend；[APK](https://github.com/combustible12/HealthTrend/releases/download/v2026101706/HealthTrend.apk)实际完整下载，56868592字节。
- ZIP CRC、manifest和apksig验证通过；SHA256 b52fe51751167ebab58b1aead15a5dd4dd490e30143a77ae92fed29fc644858b匹配GitHub资产。证书SHA256 5acccb7b281fbe2878ff8e62b57e4a1149c82bcf40e3c0b36d8e709c9da95628，与上一版一致。
- 仅处理更新缓存；不改报告、病程、参考范围、course_records或健康数据结构。旧按钮仍循环的用户需通过下载链接直接覆盖本版，保留私有数据；不得建议卸载或清除数据。未实际操作用户手机。
