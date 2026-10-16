# HealthTrend｜测试逐项审查台账（2026-10-16）

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
