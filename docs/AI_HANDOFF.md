## 2026-10-28 化疗日期趋势事件层交接（待验证、未发布）

- 实际化疗日来自病程分类“化疗/化疗日”或明确标题“X化1”。普通数值趋势叠加浅灰日期、半透明灰三角和细灰竖线；整体趋势只画细灰竖线，并在底轴增加灰色化疗日期。
- 连续自然日的化疗视为同一段，趋势事件层只保留首日，不显示“化疗”文字；日期中断后再出现化疗则保留新的首日。
- trendEventPositions按LocalDate在相邻检验槽中心间插值；事件日期未加入timelineDates，不能导致检验曲线断开。
- 已补CourseTreatmentTitleTest、TrendGeometryTest和TrendRenderingTest；需运行轻量编译，发布配置未改。

## 2026-10-28 整体趋势横屏显示交接（待验证、未发布）

- 横屏右偏根因是独立Android Dialog窗口坐标系本身不与Activity页面重合；禁止继续在Dialog内部用inset、百分比或offset补偿。数值整体预览状态已提升到App根Box，使用Activity全屏遮罩和Alignment.Center直接定位。
- 横屏Column无verticalScroll，Canvas用weight自适应剩余高度，标题、完整曲线、日期轴和右下角关闭按钮一次显示。
- 数值型趋势卡点击“整体”后，根遮罩卡片标题右侧提供横屏图标；横屏Card为页面88%×82%，Canvas使用剩余高度，退出横屏、右下角关闭、系统返回及遮罩空白处均恢复原方向。
- 方向变化由MainActivity通过configChanges承接，保留当前previewSeries与弹窗状态。曲线、参考范围、医院颜色、断点、单位和报告数据未修改。
- TrendRenderingTest新增可见按钮和准确回调断言；需检查云端快速编译结果，发布配置未改。

## 2026-10-28 港维护色块布局调整（未发布）

- 用户确认：港维护使用白底，与下方病程卡片左右边缘对齐；高度与上方放疗日筛选按钮一致。
- CourseRecords.kt：复用时间线的横向偏移、尾部留白和22dp轴列缩进；港维护与分类筛选共用32dp高度，移除维护行上下撑高的padding。日期计算、持久化与已维护动作不变。
- CourseNavigationTest 增加实际页面的左右边缘和筛选按钮等高断言；已完成源码与尺寸链审查，尚未运行Android设备测试，不宣称实机验收。未修改发布配置。

## 2026-10-28 输入控件全局规则交接

- 用户已多次确认：任何页面不得出现线框输入框、焦点描边、底部指示线或文字压在边框上的浮动标签。
- 所有业务输入必须调用 `AppInputField`、`AppSearchField`、`AppCompactInput` 或 `AppInlineInput`；这些组件统一落到 `AppInputField.kt` 内唯一的 `BasicTextField` 基础实现。禁止业务层直接使用任何 TextField 变体。
- 禁止 OutlinedButton、OutlinedCard、BorderStroke、Modifier.border；FilterChip 必须 `border=null`。原生 DatePickerDialog/TimePickerDialog 是功能型系统选择器的明确例外。
- `ci/check_ui_design_rules.py` 已接入变更检查和发布流程并报告文件/行号；`UiDesignRuleTest` 与 `AppInputFieldTest` 同步验证源码约束及普通、聚焦、错误、禁用输入行为。
- 病程记录标题右侧的日历按钮使用 `courseRecordForDate` 精确按本地日期查找；命中后清空搜索/分类过滤并滚动到卡片，未命中显示提示且不创建记录。
