# HealthTrend｜测试逐项审查台账（2026-10-16）

## 当前状态：更新按钮位于“我的”页最底部（2026-10-17）

此节覆盖下方历史布局说明。用户最新明确位置：“我的”页最下面、“回到顶部”上方，按钮文案“一键更新并升级”，不再进窗口点击。

- Updates.kt 将现有更新检查/下载/校验/系统安装逻辑原样放入 AppUpdateButton，Mine所有内容之后直接显示独立按钮；MainActivity移除updatesOpen导航状态和AppUpdatePage。
- 不改报告、参考范围、历史数据及病程；不删除更新功能或私有仓库次级访问设置。
- 新增UpdateEntryTest检查“我的”页最底部可直接看到可用按钮、其下方为“回到顶部”、且无“检查并升级 ›”跳转入口。设备测试暂未运行；发布流程将编译Android测试，实际执行全部JVM单测后才构建签名APK。
- 最终目标版本1.0.102 / 2026101704；当前待编译、测试、构建与下载验证，未宣称发布成功。


## 当前状态：不完整参考范围全链路已发布（2026-10-17）

本节为唯一当前状态，下方旧施工状态只用于历史追溯。

- 已完成问号范围解析、原始异常提示内部约束、人工核对/编辑、无损保存重开、导出再导入、标准范围池隔离及趋势图必要适配。未知边界不画虚假精确线/阴影，完整结果正常绘点。
- 功能验证提交 `606fb9e8688a6ce66f17e2b810cdb8048b57b3fc`；发布提交 `1890cf91a0f694447b98aa6aa530423d2305d828`。本地聚焦 JVM **75/75** 通过；Android quick `38943369594` 编译和单测通过；正式发布 run `38943657050` **成功**，最终 XML 统计 **151 tests / 0 failures / 0 errors / 0 skipped**，签名 release APK 构建通过。
- **正式版本 1.0.100 / 2026101702**：[APK](https://github.com/combustible12/HealthTrend/releases/download/v2026101702/HealthTrend.apk)。已实际下载，ZIP完整性/包名/版本/签名均验证；SHA-256 `aa49521ee7ff19d40b5e42de0775c0112c0fba3e03008a2d2278ae3049c21c0d`，大小 56868592 bytes。
- 与上一版 v2026101701 签名证书相同：`5acccb7b281fbe2878ff8e62b57e4a1149c82bcf40e3c0b36d8e709c9da95628`。支持覆盖安装；旧 JSON 无迁移回写，旧 Android Compose 序列化草稿及内嵌报告兼容测试通过。不修改病程/course_records、既有身份或历史结果。
- 必要自动验证及发布待办 **0**。未执行用户手机的实际覆盖安装/界面手势实测，不将上述自动测试冒称真机验收。完整测试证据及此前失败根因见 [本次测试记录](./TEST_RESULTS_2026-10-17_PARTIAL_RANGES.md)。


## 当前施工状态：旧模板清理与历史报告接管（2026-10-16 Work）

本节优先于下方所有历史进度快照。基线 `5459dbd`；只修改 HealthTrend。

- **任务 A 代码已实现**：移除编辑器从模板取检查类型建议；删除无调用的 `migrateCurrentTemplates`、`migrateMaternalRdwReports`、`normalizeMaternalRdw`、`migrateAstAltIdentity`、`TemplateEditor`、`templateNeedsNewVersion`；旧解析帮助函数隔离到 `LegacyReportCompatibility.kt`，仅旧回归测试调用，不接入新报告。保留旧模板原始 JSON、显式历史修复工具、备份读取和冻结趋势说明读取。
- **任务 B 代码已实现、61 项聚焦 JVM 测试通过**：真实保存入口统一调用 `materializeReportDraft`，按结果 ID 保留未修改对象；旧报告来源、时间毫秒、版本、原始结果精度/单位/范围、图片元数据保留。存储读取不再规范化来源单位；普通 PDW 仅保留原身份/名称/单位，不能新建或自动分类。旧报告编辑不再重建所有未修改项目。
- **显式身份关联**：新 `report_identity_links` 事件记录源 ID、目标 ID、确认时间、撤销时间；旧 CUSTOM 不按名称自动合并；编辑器可选择中文指标确认/撤销；统一池附带已确认历史关联和原范围，不反向学习别名、不写旧报告、不接入冻结趋势映射。
- **备份恢复**：实际 ZIP 的报告、原图、身份事件、模板原文及安全快照在独立空目录验证；损坏备份必须在写入前失败；已有报告/图片/体重/自定义词典及关联的环境禁止覆盖。Android 原生 SharedPreferences 缓存及重启仍待验收。
- **验证状态**：聚焦 Kotlin/JVM **61/61** 通过；功能提交 `6ebe57f` 的 Android quick run `38912183186` 已确认 `compileDebugKotlin` 与 `testDebugUnitTest` 均成功；两个最新同步守卫运行成功。未发布 APK、未执行手机用户验收。
- **自动验证待办 0；手机端待验 3 项**：实际替换/撤销 UI、原生备份恢复后重启、旧安装升级后的数据与图片保持。
- 内置词典仍 **112** 项，本轮新增医学来源复审 **0**；不更改既有来源台账结论，不将通用候选等同最终医学验收。趋势函数/绘制、病程模块和 `course_records` 实现未修改；未使用真实患者数据。


> **事实边界**：本次已人工读取并审查下列两个 JVM 测试文件及两个 Android 设备测试文件，不能冒称全仓库测试已逐文件人工审完。现有 GitHub 文件检索接口无法枚举目录。CI 新增的同步关卡在本地检出仓库后以 `git ls-files` 自动识别**全部** Kotlin/Java 单元与设备测试，新增测试不会被漏数。当前阶段用户明确不要求 Android 编译、APK 验收。测试未运行，不报通过。

## 已读取的逐文件审查
| 文件 | 审查结论 | 需要保留的断言 / 风险 | 当前状态 |
|---|---|---|---|
| `app/src/test/java/com/combustible12/healthtrend/UnifiedMetricStandardAuditTest.kt` | 已审，5 个 `@Test` | 中文名称、标准 ID 唯一、模糊别名不自动合并、PDW-CV/SD、RDW-CV/SD、百分比/绝对数不混同；`keys.size >= 100` 是低门槛而非 112 项验证；无全量来源核验能力 | 保留，未运行 |
| `app/src/test/java/com/combustible12/healthtrend/MetricImportRegressionTest.kt` | 已审，逐一核看当前全部 `@Test` 断言 | 测量身份冲突阻止保存、单位与范围的同身份换算、报告自带范围高于旧模板、源数据原样保留、新指标须确认；注意 `reportedUnitIsNeverReplacedByDictionaryDefault` 只验证解析器原文保留；`newReportReviewCannotBeBypassedByLegacyTemplate` 保护报告优先原则；目前缺少 **统一池不同医院相同范围也分别保留来源** 的直接测试 | 保留，未运行 |

| `app/src/androidTest/java/com/combustible12/healthtrend/UpgradeTest.kt` | 已审，1 个 `@Test` | 验证由旧版升级后原报告、原始参考下限、图片文件、模板版本不丢失；属于必要的数据安全断言，不应因当前尚无成功录入数据而删除 | 保留，未运行 |
| `app/src/androidTest/java/com/combustible12/healthtrend/ProductionUpdateTest.kt` | 已审，3 个 `@Test` | 正式更新/发布链路专用；当前用户要求不考虑编译发布，因此保留并注明非当前词典验收门槛，勿因失败反向修改词典业务规则 | 保留，未运行 |

## 尚待实际逐文件审查（不虚报完成）
- 其余 `app/src/test/**`、`app/src/androidTest/**` 和 CI 中的其他脚本/测试：需要基于完整检出仓库的 `git ls-files` 枚举清单逐文件审查后才能勾选。
- 此前的测试失败不能一律判断为“过期测试”。**必须保存实际失败日志，再判定断言与最新用户规则是否冲突**。当前尚无本次运行日志。
- 新的自动同步关卡只检查**文件改动是否同步**，并不自动证明每个测试逻辑正确，也不代替人工医学审核。

## 自动检查与执行入口
- `ci/check_change_sync.py`：按 `git diff` 检查 App 源码、相关测试、用户规则、总进度、交接与本文件是否同步；包含 6 个自检场景。
- `.github/workflows/change-sync.yml`：PR 和 main push 上运行，不需要 Android 编译；测试枚举来源于 Git 跟踪文件，而非手工维护的测试总数。
- **限制**：若只修改测试文件以外的业务代码（例如 `ci/` 自身），应另核对相关规则；当前检查器尚不能评估断言语义是否最新。未经 Workflow 运行证实，不能宣称关卡已经在 GitHub 上通过。

### 2026-10-16 新增回归覆盖
- `MetricImportRegressionTest.unifiedRangeIdentityNeverContainsHospitalOrReportType`：明确禁止医院/检查类型字段重返 `ObservedReferenceRange`，核对统一池范围的指标身份结构。**代码已添加，测试未运行**。尚需实测多来源报告合并相同区间、保留不同区间及原报告不变。

### 2026-10-16 不必要旧逻辑残留排查（新报告取消医院模板）
- 检查 `HealthStore.kt`、`ReportImport.kt`、`Editors.kt`、`MainActivity.kt`。已修复**新安装启动仍自动生成旧医院模板**的矛盾：删除启动时的 `migrateCurrentTemplates()` 调用，旧模板原始存储和兼容读取不删（提交 `9778980`）。该方法及早期模板修复函数仍留存供后续安全分析，不擅自执行迁移。
- 发现的**尚未移除残留**：`MainActivity` 仍加载医院模板并通过 `Mine`/ `TemplateEditor` 显示模板编辑入口；`ReportImport` 仍存 `templateDrivenPastedResults` / `templateDrivenResults` / `matchTemplateRows` 等旧模板解析代码；`Editors` 仍有 `templateNeedsNewVersion` 和 `TemplateEditor`。必须先审查调用链和旧测试依赖、区分遗留数据管理与新业务入口，再安全删除；不直接删旧模板记录。
- `MainActivity` 趋势展示中通过旧模板读取说明的地方涉及用户已冻结的趋势，不在本轮改动。
- 现阶段**仅修启动自动写入**，其余是查实的待清理项；未全仓枚举、未运行自动测试/编译，不能宣称全仓残留清零。历史报告、病程、趋势和图片资料不变。

## 2026-10-16 用户要求处理两项：旧医院模板残留清理与历史数据接管
- **旧医院模板残留清理（已改代码、待运行验收）**：`MainActivity.kt` 移除模板编辑页状态、模板列表加载、模板管理入口；`Updates.kt` 的「我的」去除旧模板编辑参数及未使用卡片；`HealthStore.kt` 的统一指标池不再从旧医院模板抽取参考范围或默认单位，医院提示也不再从模板派生。启动阶段以前已停止自动种模板；**旧 `templates` 数据本身不删除**。提交 `1b163bc`、`1f43d91`、`67fd4cf`、`1b06fc3`。
- **历史数据接管（已有兼容读取代码、接管链路待最终验收）**：`HealthStore.reports()` 读取旧报告存储，`ReportDraft.from(current)` 与 `ReportEditor` 支持编辑旧报告，`saveReport` 保留原报告 ID、原图及旧 `templateVersion` 元数据；新报告使用 `buildReportFromPool` 而不是旧模板。历史原数据不自动迁移或改写；旧 `CUSTOM-UUID` 映射仍需用户逐项确认。保存及备份→恢复必须通过独立测试后才能认定真正安全接管。
- 新增合成测试 `MetricImportRegressionTest.legacyTemplateDoesNotTakeOverNewClipboardReport`（`eae23bf`），**代码已写未执行**。趋势、病程冻结；旧 `latestTemplate` 趋势说明兼容调用暂保留，不为清理残留而改趋势。旧模板匹配帮助函数在非新路径上仍保留供兼容/测试，不能称全仓零残留。
- 当前完成状态：**两项已完成本轮代码改造，历史接管的运行/升级/恢复验收尚未完成**。没有编译、测试或发布 APK。

### 本轮测试范围与执行证据说明
- 可复现命令：`KOTLIN_TEST_LIBS=<本机Kotlin/JUnit/JSON依赖目录> python ci/run_report_jvm.py`。脚本编译实际 `HealthStore`、`HealthBackup`、解析器、统一词典、报告编辑保存函数；日期与粘贴函数从源码提取，不复制业务实现。
- 聚焦套件：`UnifiedMetricStandardAuditTest`、`MetricImportRegressionTest`（含 eae23bf 与本轮历史 PDW 用例）、`HistoryReplacementTest`、`ci/jvm/ReportTakeoverTest`。
- `ci/jvm/Context.kt` 是独立目录、XML 持久化的 JVM 平台替身，`Uri.kt` 提供文件 URI，`Platform.kt` 只替代 UI 说明及空安装图片资料 guard。ZIP、散列、路径迁移、恢复写入、重新读取和业务校验均执行实际生产函数；这不证明 Android 缓存或 URI 授权正确。
- 初次尝试纳入 `ReportParserTest` 整份文件时，宿主编译缺少 Compose `Offset` 和趋势 UI 辅助函数，未形成测试结果；没有删改其断言。该文件和设备测试未纳入本轮聚焦运行，完整 Android 环境后再验。
- 历史模板驱动解析断言只验证隔离的旧兼容函数；不能用于新报告的有效规则。没有为了旧测试恢复模板依赖，没有跳过聚焦套件中的有效断言。
- 额外检查：`python ci/check_change_sync.py --self-test` 六场景通过；`git diff --check` 无空白错误。完整同步关卡在最终提交后以基线检查。

### 最终实际运行结果（2026-10-16 10:29 UTC）
- Kotlin/JVM 生产核心代码编译通过；4 个聚焦测试类共 **49/49 通过**，包括此前未运行的 eae23bf；JUnit 输出 `OK (49 tests)`，运行 2.131 秒（不含编译）。
- 首次宿主编译缺 UI 依赖；补充恢复保护时发现 private API 可见性编译错误，已改为 HealthStore 内部专用状态检查后重跑通过。未删断言、未回退规则。
- 聚焦套件最终失败 **0**。未运行的 ReportParserTest 整体、其他图片/草稿与 Android 设备套件不列为通过；Android UI、原生恢复/重启、升级验收共 **3 项待验证**。
- 报告/图片 ZIP 实际恢复到另一个临时根目录并重新创建上下文读取；模板 XML 原文及安全快照保留；损坏 ZIP、已有报告和已有体重被拒绝覆盖。
- 趋势时间轴/绘制源码和 CourseRecords.kt 与基线逐段比对未变；course_records 读写实现未更改。没有运行患者真实数据导入或迁移，没有 Android 编译、APK 发布或用户验收。

## 2026-10-16 继续施工：完整代码与中文身份冲突拦截
- 修复 `reconcileParsedMetrics`：报告完整代码仍保留为证据，但当中文名称能独立识别且指向不同身份时，生成 `待核对:代码名称冲突:<原代码>`，不能直接保存。覆盖 `AST/ALT`/`ALT/AST`、绝对计数/百分比、CV/SD。代码和名称一致时正常确认。
- 新增纯函数回归与真实粘贴入口合成测试；聚焦 Kotlin/JVM 核心编译及 **52/52** 测试通过。没有使用真实报告。Android 编译、UI 与升级测试仍待执行，不记验收。
- 本轮未修改趋势绘制、趋势映射逻辑、病程或 `course_records`。内置指标仍为 112，本轮医学来源新增核验 0。

### 2026-10-16 Android 集中编译请求
已更新 `ci/compile-trigger.txt`，请求 GitHub Android quick workflow 对提交 `8809b5a` 执行 `compileDebugKotlin` 与 `testDebugUnitTest`。当前仅记录已请求，结果尚未返回，不能记为通过。

### 2026-10-16 同步守卫异常
提交 `033faeb` 的 Android compile check 已启动；同一提交的 change-sync workflow 因工作流文件尾部重复残片而在创建作业前失败，并非测试断言失败。已修复 YAML，修复后的 workflow 尚待远端运行，不能记为通过。

### 2026-10-16 报告 OCR 移除回归范围
- 用户确认报告数据导入只保留固定格式粘贴和手工录入，OCR 仅属于图片资料模块。
- 删除报告导入的 OCR 入口、识别器调用和双栏 OCR 重建测试；新增静态边界检查，要求 `ReportImport.kt` 不引用 ML Kit、`ReportOcr`、相机或相册识别入口，同时确认 `ImageDocuments.kt` 的 OCR 索引实现仍存在。
- 历史 `rawOcr`、报告原图和图片关联的读取/备份恢复测试继续保留。`python3 ci/check_report_ocr_boundary.py` 通过；聚焦 Kotlin/JVM 回归 **52/52** 通过。
- 上一提交 `033faeb` 的 Android `compileDebugKotlin` 已通过；完整 `testDebugUnitTest` 共 135 项、7 项失败，均位于 `ReportParserTest`。失败项目为固定格式行单位/范围旧断言及“检查类型可空”旧断言，尚未逐项按最新规则处理，不能记为完整单元测试通过。本次报告 OCR 移除提交的 Android CI 尚待运行。
- 已通过 `ci/compile-trigger.txt` 请求对 `e4662c9` 运行 Android quick；仅记录已请求，不能提前记为通过。

### 2026-10-16 Android quick 结果与旧断言更新
- Run `38889567764`：`compileDebugKotlin` 通过；`testDebugUnitTest` 共 134 项、7 项失败。
- 失败均在 `ReportParserTest`：2 项仍以报告 OCR 噪声为输入、3 项要求用词典单位覆盖来源单位、1 项把检查类型视为可空、1 项因普通 PDW 冻结校验导致固定格式整份无效。
- 按最新规则更新：删除报告 OCR 专属断言；来源单位照录；检查类型必填；普通 PDW 保留原身份和单位且不自动归类，只有名称明确 CV/SD 与普通身份冲突时拦截。
- 更新后 OCR 边界检查通过，聚焦 Kotlin/JVM 回归 **52/52** 通过；Android 完整测试已请求重新运行，结果尚未返回。

### 2026-10-16 历史指标身份映射专项回归
- 新增/扩展 `ci/jvm/ReportTakeoverTest`：候选盘点不写事件或报告；确认事件包含报告 ID、结果 ID、原名称和原单位快照；活动关联不可覆盖；撤销保留事件并允许再次明确确认；大小写查询不改写原 CUSTOM 身份。
- 既有 ZIP 独立空目录恢复用例继续比较完整 `report_identity_links`，因此同步覆盖新增证据快照的备份/恢复；旧报告内容和图片关联比较保持不变。
- 实际命令：`KOTLIN_TEST_LIBS=/workspace/scratch/5041f88669d1/kotlin-tests python3 ci/run_report_jvm.py`。变基到最新 `main` 后首次执行为 **57 项、1 项失败**，定位为截断 RDW 已按 `%` 归入 CV 后仍被复核层误拦截；修复 `metricNeedsReview` 衔接后重跑，生产核心 Kotlin 编译通过，JUnit **57/57** 通过，失败 0；仅有既有未使用参数警告。
- 未使用真实患者数据。Android 编译及 Compose UI 操作、原生 SharedPreferences 升级/恢复、用户验收尚未完成，不记为通过。

### 2026-10-16 身份映射提交 Android quick 首次结果
- Run `38901628770`：`compileDebugKotlin` **通过**；`testDebugUnitTest` 137 项中 2 项失败。
- 两项均为 `ReportParserTest` 仍期待截断 `RDW/PDW` 加 `%` 后保持普通身份，与同日最新用户规则“已知分布宽度族时 `%`→CV、`fL`→SD”冲突。生产解析和新增专项测试均已按新规则工作，因此同步修正旧断言，不回退代码。
- 修正后复验 run `38902068666`：`compileDebugKotlin` 通过，`testDebugUnitTest` **137/137** 通过；同步守卫 run `38902068470` 通过。未运行模拟器/实机 UI、原生 SharedPreferences 跨版本升级恢复或用户验收，不把单元测试通过扩写为这些项目已验收。
## 2026-10-16 待执行：确认替换封存与更新页

- 新增 JVM 合成回归：确认替换保留旧报告 ID、原精度/单位/范围及新旧图片；旧版本进入封存，撤销后完整恢复。
- 新增失败回滚回归：无效的新报告不能改变活动旧报告，也不能创建封存残片。
- 新增真实 ZIP 的独立空白目录恢复回归：恢复后的活动报告与封存旧版本均存在，图片 URI 可按恢复目录安全重写且文件存在。
- Android 单元测试增加封存事件同时保留新旧版本的结构断言。独立更新页复用既有 `ProductionUpdateTest` 的检查、下载、校验与安装链路；本轮需另以 Android 编译确认 Compose 接线。
- 当前状态：`KOTLIN_TEST_LIBS=/workspace/scratch/5041f88669d1/kotlin-tests python3 ci/run_report_jvm.py` 已实际运行，**61/61 通过**。真实设备的系统安装确认和界面交互仍待验收。
## 2026-10-16 测试范围收紧

V1.0 必测范围限定为：无医院模板导入/保存、身份冲突人工核对、旧报告读取编辑与确认替换/撤销、原始精度/单位/参考范围/图片保持、完整备份恢复、升级读取不自动改写。趋势扩展、全模块别名展示以及没有真实报告触发的医学资料扩审不再作为本轮测试门槛。已有安全回归保留，不为外围展示继续扩充测试矩阵。
