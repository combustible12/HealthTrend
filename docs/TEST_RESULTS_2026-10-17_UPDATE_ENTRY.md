# “我的”页底部一键升级：实际验证（2026-10-17）

## 追加：按钮高度三分之二（1.0.103）

- 用户截图确认高度过粗，源码仅将按钮60dp改40dp，位置、宽度、字号及功能不变。
- 提交4b24b63d73b2b53dcf414236e7f82f7376bb54a0；发布run38948116068成功；Kotlin编译、151/151 JVM测试（零失败/错误/跳过）、设备测试代码编译和assembleRelease通过。位置和点击设备用例仅编译，未实际运行。
- APK v2026101705，versionName1.0.103，56868592字节；SHA256 9130490e0d82449854c161f13e5cfef68d4a2f7d5b110c3f6281b57f90161f68。
- 已实际下载完整APK并验证ZIP CRC、manifest和apksig，均通过；签名仍为5acccb7b281fbe2878ff8e62b57e4a1149c82bcf40e3c0b36d8e709c9da95628，支持覆盖安装，未做手机升级实测。


用户最终要求：所有内容之后、“回到顶部”之前，独立按钮文案“一键更新并升级”，不进入另一个窗口再次点击。

- 发布提交13570497d91fa5a51c90d6ad94e46196585ebe71；[工作流38946516776](https://github.com/combustible12/HealthTrend/actions/runs/38946516776)成功。
- 实际运行gradle :app:compileDebugKotlin :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin，编译全部通过；XML统计151项JVM单测，0失败/0错误/0跳过。
- 新增UpdateEntryTest验证直接入口、相对“回到顶部”的位置、无二级跳转；仅编译验证，未运行设备测试。既有图片OCR设备测试删除了已退役ReportOcr依赖，改为图片识别不写报告的隔离验证；不恢复报告导入OCR。
- assembleRelease成功，持久密钥恢复和签名验证通过。同步守卫38946517081及本地同步检查/OCR边界检查通过。
- 版本1.0.102 / 2026101704，包名com.combustible12.healthtrend。
- [APK](https://github.com/combustible12/HealthTrend/releases/download/v2026101704/HealthTrend.apk)实际下载完成；大小56868592字节，ZIP全部CRC、manifest、apksig校验通过。
- APK SHA256：91f40e74db34fbefd7129a544ef902513d23c2c097a78de4d8b411a72486349a，匹配GitHub资产digest。
- 签名证书SHA256：5acccb7b281fbe2878ff8e62b57e4a1149c82bcf40e3c0b36d8e709c9da95628，与上一版一致。支持直接覆盖安装；未执行用户手机上的覆盖安装。
- 不修改报告、参考范围、病程、course_records或历史数据结构。

首次构建阻碍：run38946010522在compileDebugAndroidTestKotlin发现CoreFlowTest仍调用已删除的ReportOcr。未跳过失败检查；按当前报告OCR禁用规则修正过期测试。最终工作流已全量编译和运行JVM测试通过。
