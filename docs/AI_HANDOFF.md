# HealthTrend｜ChatGPT ↔ Codex 开发交接

更新时间：2026-10-14。**这是交接上下文，不是代码已验收证明。**

## 1. 接班时先做什么
1. 在当前工作环境核实 `combustible12/HealthTrend` 的 `main` 最新提交。Codex 本地需检查工作区未提交修改，再拉取；不得直接覆盖本地改动。
2. 阅读本文件、[`DEVELOPMENT_CHECKLIST.md`](./DEVELOPMENT_CHECKLIST.md)（44 项双勾选）、[`UNIFIED_METRIC_SYSTEM_PLAN.md`](./UNIFIED_METRIC_SYSTEM_PLAN.md)（原始规则及历史日志）。
3. 查看涉及文件的最新实现，确认哪些只是提交、哪些已验证。**以仓库实际代码和提交记录为准，不依赖聊天中的“已完成”宣称**。
4. 一次处理相互关联的任务；施工过程中不每次提交就编译。只有用户要求或整个功能模块开发结束后，才集中测试、编译、发布。
5. 每次换班前更新本文件的「接班现场」、剩余任务、关键提交，并同步开发清单的**代码编写**和**验收通过**两个独立状态。编写勾选仅代表有关代码存在，不代表全部行为已实现；验收必须有测试证据。

## 续工：首批权威医学术语核对（2026-10-14）
- [医学审核台账](./UNIFIED_METRIC_MEDICAL_AUDIT.md) 现记录 **15/110 项血常规指标概念与 LOINC 候选术语**（[88c53a8](https://github.com/combustible12/HealthTrend/commit/88c53a82c5ae427f3b0956e1aefbad4898683fb5)）。其余 95 项仍需外部资料核对；全部 110 项的检测方法、标本、单位等设备条件尚不能认定已完整审定。
- 重要发现：LOINC 的旧 RDW-SD 模糊代码 `21000-5` 已弃用，指向独立 CV/SD 代码 `115741-1` 与 `115742-9`；D-Dimer 的 FEU/DDU 标准不可凭名称自动合并。**候选术语映射不得用于重写历史检验结果。**
- 主清单第 2.4 仍为代码 [ ] / 验收 [ ]；继续按明确来源逐项补充证据，绝不为打勾猜单位或制造临床参考范围。没有修改患者数据或病程。

## 第 2 节医学词典核验进度（2026-10-14）
- 已枚举现有 `UnifiedMetricStandard.kt` 共 110 个定义，生成 [逐项台账](./UNIFIED_METRIC_MEDICAL_AUDIT.md)，记录每项现有身份、中文名、显示单位、别名，并明确每项**还需专业资料逐项审校**。其中 14 项常用单位留空，不得默认填值。
- 新增 `UnifiedMetricStandardAuditTest.kt` 的身份、相似代码、中文名、别名冲突等静态防护；[b4a3d39](https://github.com/combustible12/HealthTrend/commit/b4a3d399dc54cf7f03db4db6ccecee6ee163a65e)、台账 [99f6c0b](https://github.com/combustible12/HealthTrend/commit/99f6c0b7d280a989302c5719acd7ebfa022f224b)。
- LOINC 是专业术语结构参考，必须核对检验对象/测量属性/标本/方法；MedlinePlus 指出不同实验室参考范围差异。**一般指导不能等同于 110 项分别有核验来源。** 因此清单第 2.4 仍保留代码 [ ]、验收 [ ]，不可虚报完成，更不可凭猜测改患者既有数据。
- 如本条 CI 仍在进行，以最终 GitHub Actions 结果为准；不因文档更新额外重复触发构建。

## 最新全表审计快照（2026-10-14，优先于本文后续旧日志）
- **开发清单原 44 项已逐节审计：22 项代码主体已提交，22 项仍需补齐；全部正式验收 0 项。备份专项另有 5 项，其中 3 项代码提交、2 项测试与异常验证待做。** `[x]` 只代表主体代码存在，不代表全功能实现或真实数据安全验收。
- 第 2 节的**医学词典权威来源逐项核验**未完成，已经撤回过早的代码完成勾选。
- 第 3 节的**匹配优先级跨 OCR/粘贴/手工/编辑所有入口**仍需统一实现；主体导入保存和待核对持久草稿已经实现且完成 JVM 编译/回归检查，不再是旧日志中的“待施工”。
- 第 4 节搜索已具备别名和卡片注释的局部实现；**点击名称/注释查看完整别名、趋势/历史/新增指标/指标管理共用搜索规则**尚未实现，不能视为完成。
- 第 5 节**历史指标身份含 CUSTOM-UUID → 统一标准身份的持久只读映射**、跨医院趋势连续性尚未完成；旧版医院模板仅历史遗留存储，绝不参与新报告；病程及历史报告绝不能自动改写。该节原有 3 个过早的完成勾选已撤回。
- [CI 38531351467](https://github.com/combustible12/HealthTrend/actions/runs/38531351467) 已通过 Kotlin 编译及 JVM 回归，不等于 Android 真机验收；无新 APK Release，备份/恢复未做空白环境实测。
- **规则优先级：清单开头最新业务规则 > 清单本轮审计 > 本文件此节 > 此节下面所有历史施工叙述。** 以后每次用户调整全局规则先更新两份文件，避免 Codex 依据过时的“下一步计划”浪费额度。
- 审计提交：[ab57830](https://github.com/combustible12/HealthTrend/commit/ab578307819b06d4754bcba49d06924502316b6c)。当前工作重点以最新 [开发清单](./DEVELOPMENT_CHECKLIST.md) 未完成项目为准。

## 用户最新规则：新报告彻底取消医院模板（2026-10-14）
**此节优先级高于本文旧说明与仓库旧计划。**
- **新业务不再存在“医院模板”。** 每份新报告只从统一指标标准池选择本次实际检测的项目及其对应参考范围，汇总为这份报告独立的记录；未测项目不生成。
- 报告的解析、核对、保存不再依赖医院模板决定指标身份、项目列表、顺序、名称、单位或参考范围。**不得自动创建、更新、升级或要求用户确认建立医院模板。**
- 新指标由用户确认后加入统一标准池；新参考范围先判断可否转换，再去重匹配或入池，支持手动输入和持续持久化扩充。
- 医院仅为报告来源，不参与身份识别。原始报告数据、原图和历史记录保持原样；旧版模板仅作为**不参与新报告业务**的历史遗留数据保留，不删除、不重写。
- **已提交新报告主路径解耦，仍需复查余留入口**：排除 `ReportEditor` / `ReportImport` / `HealthStore` / `MainActivity` 依赖医院模板及自动生成模板的逻辑；保证无医院模板也能完整导入、核对、保存。不要动病程模块。
- **交接纪律**：用户每次修正整体规则时，立即同步更新 `docs/DEVELOPMENT_CHECKLIST.md` 和本文件，再按新规则施工；原文旧规则仅作历史说明。

## 备份与恢复历史施工记录（尚未完成验收，2026-10-14）
- 原仓库已有 `HealthBackup.kt` 导出 ZIP 和 `Updates.kt`「我的 → 数据备份」按钮，但过去没有 ZIP 还原入口。现已提交升级代码：ZIP v2 含文件清单 SHA-256/大小，导出后重新校验，新增 ZIP 预检、**仅空白安装可恢复**的保守入口。历史照片、病程附件、图片资料本地文件及配置均包含在原有私有文件/偏好 XML 快照范围内；访问令牌凭据排除。
- 施工证据：[d10d78b](https://github.com/combustible12/HealthTrend/commit/d10d78b666a42030855ba198e812dccf051358c0)、[e7e4ca7](https://github.com/combustible12/HealthTrend/commit/e7e4ca75ff5555bf181e436c86c055241a6e5e4a)、[af55a0b](https://github.com/combustible12/HealthTrend/commit/af55a0b46e2937c64cfa9157c5ca205b6f1814e8)。
- **目前只是代码施工**，尚未编译、单元测试、独立设备或空白实例恢复验收，也没有新 APK。**不要告知用户当前手机已备份！** 手机数据从未被远程读取或备份。用户要实际备份必须待合格新版 APK，在手机上点「我的 → 数据备份 → 备份到文件」，将 ZIP 保存到手机之外；恢复前先完整预检。
- **特别注意**：恢复核心是写回私有 XML 和文件目录，SharedPreferences 缓存导致必须彻底关闭再重启 App；没有恢复验证前不能引导卸载或清空用户的安装数据。恢复默认拒绝已有患者资料、报告、病程内容及图片，不能直接覆盖。
- 下一步为静态代码审查、测试用例和完整备份-恢复实测，再处理取消医院模板逻辑。完整五条任务在 `DEVELOPMENT_CHECKLIST.md` 新增的备份阻断清单中。

## 2. 不得违反的业务红线
- 仅修改 **HealthTrend**，不要动 `TreatmentVault`。
- **病程 `course_records`、病程图片及历史记录优先保护**。报告施工不能更改这些数据。
- 已人工确认的医院模板和历史检验报告不得在升级时自动重新 OCR、重算并回写、批量迁移、覆盖、删除。原始中文名、检测值与精度、单位、范围、日期、医院、原图及附件必须保留。
- 旧版趋势须通过只读身份兼容层读取历史数据并与新报告衔接；不能因换了词典就丢点、乱连线。不同医院同轴分段断开；单点保留；未测项目不补零；参考范围随时间硬接。
- **报告导入**以用户人工核对后的固定文本粘贴为主要途径；该模块的 OCR 新优化暂停。**图片资料模块 OCR 必须保留**，不可误伤。
- 固定文本行：`医院名`、`检查类型`、`YYYY-MM-DD`，随后逐项 `代码 中文名 结果 单位 参考范围`。本次未测项目不自动生成。
- 统一指标池：同一检验身份对应稳定 ID、一个常用中文标准名、多个识别别名、多个去重的参考范围选项；医院只是原始报告来源，不参与指标身份判定。可换算时检测值和上下限一起换算；不可换算时禁止强制变更单位。新范围/别名在 App 内持久化，不应要求发布新版。
- 报告详情：只有名称发生变化时显示 `原名称：...`，有实际换算时显示 `原始：...，参考范围 ...`；**不显示“名称已统一”“单位已换算”徽章**。
- 比值和相近指标须防止误合并：`AST/ALT` ≠ `ALT/AST`，`NEUT#` ≠ `NEUT%`，`RDW-CV` ≠ `RDW-SD`，常规 `CRP` ≠ `HSCRP`，血小板压积 `PCT` ≠ 降钙素原 `PCTIN`。
- 不把真实患者数据、报告图片、身份信息提交到公开仓库；回归测试仅用合成/脱敏数据。

## 3. 代码位置与目前已有施工
- `app/src/main/java/com/combustible12/healthtrend/UnifiedMetricStandard.kt`：约 110 个内置指标身份、别名及分类。医学外部资料逐项核验**尚未完成**；空白常用单位不可臆造。
- `HealthModels.kt`：`canonicalMetricKey`、`UnitNormalizer`、报告及检验结果模型。
- `HealthStore.kt`：报告持久化、动态参考范围池、用户别名/首选中文名、`CUSTOM-UUID`、`metricPoolForType`、报告标准格式导出及词典 JSON 导入导出（最新提交后尚未全面验证）。
- `ReportParser.kt`、`ReportImport.kt`：解析器、固定格式粘贴及模板匹配。历史遗留模板不得参与新报告的匹配或保存；报告从统一标准池选出本次指标，原有名称、单位和当次范围应保留。
- `Editors.kt`：指标核对、统一指标库筛选、别名与词典管理、报告详情显示与复制。
- `app/src/test/java/com/combustible12/healthtrend/`：`MetricImportRegressionTest.kt`、`ReportParserTest.kt` 等合成测试；**添加测试代码不代表已运行**。

## 4. 早期接班现场（历史记录，以上方最新审计为准）
- GitHub `main` 为两端代码交接唯一基准；网页端曾直接经 GitHub 连接器提交，Codex 本地工作区可能落后，**不得假定已同步**。
- 开发清单 44 项，分别标「代码编写」与「验收通过」；当前验收项没有经过集中验证，请不要擅自勾选。
- 第 2 节 7 条均有代码提交，检查类型管理界面和 110 项词典已写入，但外部检验资料审核和全入口行为仍待验证。
- 第 3 节已经实现部分精确匹配、复制标准格式、词典配置导入导出及行级错误提示；**完整优先级、待核对暂存与恢复、旧数据兼容尚需施工**。参阅清单对应条目。
- 第 5 节要特别验证**旧指标身份与 `CUSTOM-UUID` 新身份的映射兼容**，保证历史趋势可延续，不能自动重写历史数据。
- 最近一批第 3 节提交：`52de9bc`（精确优先与冲突保护）、`6095490`（标准报告导出和词典 JSON）、`26bd589`（复制报告）、`41ceb67`（行级校验）、`e52f81b`（词典导入导出入口）、`fbc1c67`（新增测试用例），清单记录提交 `fdb61a9`。
- 上述为**未经完整 Android 编译、单元测试及实机验收的代码提交**，不等于 APP 已可正常运行。用户暂时不希望编译或发布。

## 5. 早期任务顺序（已过期；以最新全表审计为准）
1. 继续完成第 3 节两处缺口：全入口的准确匹配优先级；待核对数据可暂存、恢复、修改及删除，不能因为一项待核对就造成整份有效数据丢失。
2. 修复交接前提交的潜在问题，先做必要静态核查。例如导出格式与粘贴解析器是否真正往返兼容；导入词典时别名冲突是否跨已有内置/用户词典校验；保存新指标与老趋势是否出现 ID 分裂。
3. 随后按清单推进第 4～6 节。不要单纯为清单打勾而跳过实际实现。
4. 待完整模块施工结束或用户明确要求时，集中执行测试和编译，记录具体命令、结果、Commit SHA；失败先定位原因，不反复空构建。

## 6. 接班更新格式
- 日期与环境（网页 ChatGPT / 本地 Codex）：
- 当前 `main` HEAD：
- 本次修改文件及提交 SHA：
- 对应清单条目与代码编写状态：
- 已验证事项（命令 / 结果；未运行则写未运行）：
- 未解决问题与风险：
- 下一项可直接执行的任务：
- 是否触及历史患者数据/病程（正常应为否）：

## 7. 给接班 AI 的一句话
**先同步 GitHub、读取本交接和 44 项双状态清单，再接着写未完成的功能；不要根据聊天印象重写需求，不要动病程或历史确认数据，不要在每次修改后编译。**

## 2026-10-14 备份版发布阻断记录
- 用户要求先发布备份版 APK 用于实际备份真实手机数据；未进行医院模板业务改造。
- GitHub Actions 集中编译检查：首次 [run 38526966200](https://github.com/combustible12/HealthTrend/actions/runs/38526966200) 失败，`HealthStore.kt:310` Kotlin nullable Pair 属性无法 smart cast；修复提交 [20ade70](https://github.com/combustible12/HealthTrend/commit/20ade70d4a28e72ad2fae7b308ba94e72d394e23)。
- 第二次 [run 38527263756](https://github.com/combustible12/HealthTrend/actions/runs/38527263756) **Kotlin 编译通过，115 个 JVM 测试中 17 个失败**，主要集中于 `ReportParserTest` 和 `HistoryReplacementTest`。因此**尚未触发签名发布，尚无新备份版 APK**；不要向用户声称已发布或能直接用手机备份。
- 下次优先定位上述 17 项失败是否属于过时的模板测试假设、真实回归还是运行环境问题；增加完整 ZIP 备份/恢复的独立真实验证后再发布。严禁为获取绿色结果直接跳过所有回归测试。

## 2026-10-14 统一指标池导入/保存续工状态
- 用户明确解除“先只做备份”的限制，同意继续按全局统一标准池规则施工；病程截图只是用户个人兜底，**不是完整数据备份**，不许据此删除/迁移真实数据。
- 已把新报告保存切换到 `HealthStore.buildReportFromPool`，保存时不查询/生成医院模板；新报告保存本次独立实际项目集合。相关提交 [037b066](https://github.com/combustible12/HealthTrend/commit/037b0664d1b198d6662309b19f9ce6a11dbd7e03)、[9e1d1f3](https://github.com/combustible12/HealthTrend/commit/9e1d1f35fcc54a8685f563e95e4bf1e5a32e96cc)。
- 核对页面不再弹更新模板确认、不再以医院模板锁定本次项目；粘贴/OCR 导入及更换医院不再读模板。[69695b0](https://github.com/combustible12/HealthTrend/commit/69695b038542f3a621ab3eb411c31bcace2e33b2)、[7a012fc](https://github.com/combustible12/HealthTrend/commit/7a012fc75a2f238dfbfdd6eba952a171a989cbc2)、[f1ec957](https://github.com/combustible12/HealthTrend/commit/f1ec957729ef10db45795847b8c387008d47cc6e)。
- 「我的」隐藏旧医院模板管理入口，但没有删除底层数据，[8457097](https://github.com/combustible12/HealthTrend/commit/84570976b6ce5158ce17f798470b570313d1d0e9)。
- [CI 38529695948](https://github.com/combustible12/HealthTrend/actions/runs/38529695948) 编译和 115 个 JVM 回归测试通过（构建源 SHA `d8dd6484`）；界面隐藏提交在该 CI 之后，尚待最新 SHA 检查。
- 病程读写/迁移代码未被此轮直接修改；**无实际设备数据备份/恢复验证，仍不得宣称绝对安全或已发布**。旧报告与标准池的只读身份兼容、端到端持久化草稿功能尚未全面验收。

## 2026-10-14 第 3 节第 6 项：持久待核对草稿
- 新增 `app/src/main/java/com/combustible12/healthtrend/ReportDraftStore.kt`：独立 `filesDir/report-drafts/*.draft` AtomicFile 持久化，按稳定草稿 ID 管理；原始文本、行数据及精度、待核对标记、图片 URI 随草稿序列化保留。该路径属于完整 ZIP 备份里的 files 数据范围。
- `Editors.kt` 中 `ReportDraft` 新增 `draftId`；新报告编辑页顶部“暂存草稿”，退出新报告时自动暂存；正式保存仍以 `reportValidationProblems` 拦截真实无效数据，允许先暂存后继续编辑。
- `MainActivity.kt` 的「记录 → 待核对草稿」可查看列表、继续核对、删除；正式保存后移除对应草稿。未改写 `course_records`，未迁移已保存的历史报告。
- 单元测试 `ReportDraftPersistenceTest.kt` 核查草稿序列化精度及未核对行不丢；[CI 38531351467](https://github.com/combustible12/HealthTrend/actions/runs/38531351467) Kotlin 编译及回归套件通过。主提交 [0b3bcb0](https://github.com/combustible12/HealthTrend/commit/0b3bcb0cf8cab53fac784a0416d064f8c2757188)、[51bf692](https://github.com/combustible12/HealthTrend/commit/51bf6928364fc2ab733636dabcdb5f7212614cfd)。
- 已将开发清单第 3.6 项 **代码编写勾选 [x]**；**验收仍 [ ]**，尚未真机验证重启/异常关闭、备份恢复、草稿图片可访问、删草稿后原图保留等场景，不能宣称完全验收，更未发布 APK。

## 2026-10-15 GitHub 远程续工现场
- 用户明确要求不检查本地工作区；只以 GitHub `main` 为施工基准。启动 HEAD：`1a8a0e85708988211a7c73e09a51bbd680281198`。
- 本轮代码提交：`0a879a0fa1ca6d09309eb22194c5e7d58a22e1e0`（`ReportImport.kt`）。新报告待核对判断不再因为旧医院模板确认而跳过异常范围；粘贴入口显式绕开模板。为避免破坏旧调用和既有测试，暂时保留未使用的模板参数，不代表模板仍可参与新报告。
- 修改范围仅为报告导入核对规则及这两份文档；未修改 `CourseRecords.kt`、`course_records`、历史检验结果或任何真实患者数据。
- **验证状态：未运行单测、未编译、未发布 APK**；清单状态和正式验收勾选保持原样。不能据此宣称无模板全流程已完成。
- 后续：检查新报告所有入口的指标身份冲突优先级与历史模板残余调用；增加合成数据回归测试。只读旧报告兼容映射仍是未完成任务，不得通过迁移改写既有报告。


### 2026-10-15 继续施工：旧模板退出统一指标池数据来源
- 本次 GitHub `main` 增量提交：[7b8c3db](https://github.com/combustible12/HealthTrend/commit/7b8c3db6145a92760e114b6419bd7a5ee1843822)（`HealthStore.kt`）：从 `metricPoolForType` 和 `metricCatalogue` 排除已确认医院模板的读取来源；自定义指标别名合法性不再依靠旧模板存在。旧模板仍保留在历史存储，未迁移、清除或重写。
- [3d241c2](https://github.com/combustible12/HealthTrend/commit/3d241c283419e8fce5352abf6318cd820e2de6f0)（`MetricImportRegressionTest.kt`）：使用合成检验记录增加新报告异常范围无法被旧模板绕过的回归测试。**测试仅写入，未执行**。
- 本轮仅使用远程 GitHub；不处理本地未提交文件。未编译、未发布；不修改病程及历史患者数据。清单正式验收保持未勾选。
- 仍待：统一标准池引用旧历史报告来源别名的只读兼容与潜在身份冲突处理；全入口身份解析优先级；更多回归及真实设备备份恢复验证。


### 2026-10-15 续工：新报告身份识别与历史名称分离
- GitHub 代码提交 [1894e88](https://github.com/combustible12/HealthTrend/commit/1894e8893623fcad0ab33cee2d19c5babf76fc46)：`HealthStore.resolveMetricKey` 使用单独的 `recognitionMetricCatalogue`，以标准术语、用户维护别名和自定义指标为输入；历史报告原始名称不再自动充当新报告识别别名。
- 原 `metricCatalogue` 仍可用于历史展示的名称汇总；旧报告本体完全不改动。未更改病程或 `course_records`。
- 未执行测试、编译或发布，验收状态不变。待解决：导入时显式代码和名称冲突校验；跨 OCR、粘贴、编辑的统一冲突优先级；历史 `CUSTOM-UUID` 到标准指标只读兼容。

### 2026-10-15 录入优先施工
- 用户急需录入新检验数据，后续先闭环新报告粘贴导入→核对→保存→编辑；历史只读映射和医学审核非阻断事项后置。
- 已提交 [e317fce](https://github.com/combustible12/HealthTrend/commit/e317fce74e7bf40329f3b7128786ac6b3b5aa2e5) 补充检查类型不能为空的保存校验；[a9907b1](https://github.com/combustible12/HealthTrend/commit/a9907b120bc9ed34ff682bf6391247a0fac3e20c8) 新增合成回归用例。
- **未运行任何测试、编译或发布**，手机现装 APK 不受 GitHub 代码变化影响。用户先前要求暂不编译发布，仍未更改。历史确认数据和病程未修改。
