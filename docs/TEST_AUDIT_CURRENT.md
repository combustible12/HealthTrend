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
