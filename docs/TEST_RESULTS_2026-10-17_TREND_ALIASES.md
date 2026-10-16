# 趋势别名按点击显示：实际记录（2026-10-17）

- 用户明确其他别名只在点击项目名时显示，不能常驻在项目名上方。
- MainActivity删除卡片顶部别名/搜索匹配说明；TrendMetricTitle负责点击名称弹出别名，关闭后隐藏。原有aliasesFor搜索仍保留。不改指标数据、曲线/参考范围绘制、身份或病程。
- TrendAliasInteractionTest纯UI测试不使用HealthStore，断言初始无别名、点击标题显示完整列表、关闭后列表消失。已compileDebugAndroidTestKotlin通过，未在设备实际执行，不冒称实机通过。
- 发布提交13496b855df882765580c23a5ae9104891e00832；run38950215405的Kotlin编译、158项JVM测试（失败/错误/跳过均0）、设备测试代码编译、assembleRelease及发布步骤成功；同步守卫38950215383成功。
- APK1.0.105 / 2026101707，[下载](https://github.com/combustible12/HealthTrend/releases/download/v2026101707/HealthTrend.apk)，56868592字节；SHA256 b7d36ee0ae83ed52fa40114c8a6f7ac4d16c884e511d7486f0628fe92962111e。
- APK已实际下载完成，ZIP完整性、manifest和apksig校验通过；证书SHA256 5acccb7b281fbe2878ff8e62b57e4a1149c82bcf40e3c0b36d8e709c9da95628与上一版一致，支持保留资料覆盖安装。未实际操作用户手机。
