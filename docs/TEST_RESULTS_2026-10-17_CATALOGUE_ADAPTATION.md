# 词典与导入整体适配验证记录

## 当前结果：截图所列词典与识别缺口已完成自动验证（2026-10-17，未发布）

- 实现提交：9141e024d7ccfdfa342073dafe6731ef8835c5f6；修复提交85fcd41fc4835a3b40887af788034270ca01d8bb、最终源码8b2bfa73ca104a7dcf34d3f9b8609a538c7150ae。
- 已补FDP、HCVAB、HIVAGAB、TPPA四个独立身份及分类/中文/代码识别；HIV联合与单独抗体、TPPA与泛称TPAB区分，TP仍总蛋白。增加平均RBC等别名及A/G白球比例，不预设通用参考范围。
- 计数单位10E9/L、10E12/L保留原文，内部对应10^9/L、10^12/L，结果不乘10；HTML比较符可解析，原始行保留。Y/N仅内部保存和核对，Y不等于升高，矛盾需核对，不新增界面标签；不完整边界不凭Y补全。
- 泛称RDW/PDW新导入按%/fL区分CV/SD，并贯通词典核对入口；明确CV/SD冲突仍核对，历史身份不自动改写。FDP“(原)”等普通括号不再遮蔽明确项目代码。文字结果省略单位时保留参考结果，编辑不会用旧数值边界替换文字参考。
- 实际通过：quick run39001882155 / job117306364833：compileDebugKotlin、testDebugUnitTest、compileDebugAndroidTestKotlin；同步守卫39001882174通过；git diff --check通过。成功日志未输出测试总数，不虚报最终数量。此前211中2失败、212中1失败均已定位修复，未跳过测试，执行历史保留如下。
- ReportCatalogueAdaptationTest覆盖词典/代码/括号、单位/科学记数、别名、CV/SD和冲突、HTML/Y/N、文字参考；CatalogueStorageRoundTripTest用Robolectric执行生产pastedReportDraft→HealthStore保存/新实例重读→编辑→导出再导入→数值/文字趋势入口，旧报告完整相等及course_records原文相等。A/G已有回归随全部JVM通过。
- 没有存储结构迁移或历史批量重写；未修改TreatmentVault、病程模块、发布配置或趋势UI。仅合成测试数据；不上传患者报告。未运行手机/模拟器UI、手势、安装升级或真实设备数据库，自动验证不等同这些实测。本次未构建/发布APK。
- 本节为唯一当前结果；以下旧“当前/待执行/失败”均为历史追溯。详细证据见TEST_RESULTS_2026-10-17_CATALOGUE_ADAPTATION.md。

## 历史执行记录（非当前状态）

2026-10-17第二轮实际结果：85fcd41fc4835a3b40887af788034270ca01d8bb / quick39001681167，Kotlin通过，212项中1项生产入口测试失败。定位为FDP名称含“(原)”导致key()误把最后一组括号当指标代码，遮蔽前面的明确(FDP)。已改为只接受词典中已知的括号代码，增加原始行与绑定代码行双路径断言；未跳过失败测试，继续重跑。

2026-10-17验证修复：9141e024d7ccfdfa342073dafe6731ef8835c5f6 / quick39001445392，Kotlin编译通过，211项JVM中2项失败（原有文字结果省略空单位列时参考“阴性”误作单位）。已修正解析，不改测试预期；补省略/显式空单位回归。真实粘贴入口另补词典核对衔接：带单位泛称分布宽度解析身份保持，TPPA明确方法码与泛称梅毒抗体不冲突；泛称TPAB不会反推TPPA。生产存储测试改为走pastedReportDraft入口。设备测试编译被上游失败阻断，未报通过；修复后重跑，不发布。

基线601ac074b1fda2c176f883c54763a4320d21e3c6。仅合成数据。

## 当前任务：检验报告词典与识别整体适配（2026-10-17，未发布）

- 用户已授权处理截图全部缺项：FDP、HCV抗体、HIV抗原/抗体联合、TPPA专用身份；HIV抗体/联合及TPAB/TPPA分开，TP仍总蛋白。无通用参考值，不改历史身份。
- 补平均RBC等别名；10E9/L、10E12/L仅在计数单位语境归一为10^9/L、10^12/L，保留原单位和结果精度；比较符HTML实体识别，原始行保留。白球比例为已有A/G别名，无单位，不补%。
- Y/N仅内部辅助：Y表示超范围而非升高，N表示范围内；高低由本次边界判断，矛盾标核对。不增加普通界面标记。未知边界不靠Y猜具体上限。
- 新导入分布宽度按名称和%/fL区分CV/SD；显式CV/SD冲突需核对，不批量重写历史PDW/RDW。现有趋势UI和时间/医院规则不重做。
- 修复基线ReportEditing.kt错误字面换行，保持文字参考不被旧数字边界替换。此前601ac07 quick run38998260019失败，不能记为通过。
- 新增ReportCatalogueAdaptationTest和Robolectric生产存储测试CatalogueStorageRoundTripTest，覆盖保存重读、编辑、导出再导入、趋势入口、历史及course_records隔离；旧词典7项分类断言更新为10项，保留旧身份区别断言。
- 编译/JVM/设备测试代码编译待运行；未运行手机UI/升级；不触发发布。最终结果见TEST_RESULTS_2026-10-17_CATALOGUE_ADAPTATION.md。下方旧状态均为历史追溯。

检查：git diff --check通过。待提交触发必要quick编译与全部JVM；不改ready.json，不发布。Robolectric验证Android JVM存储，不等同手机UI或升级实测。
