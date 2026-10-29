## 2026-10-28 化疗日期趋势事件层（待验证、未发布）

- CourseRecords.kt统一识别“化疗/化疗日”及明确的“X化1”记录，导出实际化疗日期。
- 连续自然日的化疗事件合并为一段，只导出首日供趋势图标记；间隔后的下一段重新标记。
- TrendGeometry.kt按日历日期在现有检验时间槽间插值事件位置，不把事件插入趋势时间槽。
- MainActivity.kt：普通卡显示低透明度灰色日期、半透明灰三角和细灰竖线；整体趋势只显示细灰竖线，底轴补灰色化疗日期。
- 新增事件日期识别、位置插值、普通卡及整体横轴回归；等待轻量编译验证，未发布。

## 2026-10-28 整体趋势横屏显示（待验证、未发布）

- 根因修正：独立Android Dialog拥有与Activity页面不同的窗口坐标系，内部Alignment.Center只能相对偏移后的Dialog居中。数值整体预览状态提升到App根Box，移除该预览的Dialog窗口，直接在Activity全屏遮罩中以Alignment.Center放置Card。
- 横屏Column取消verticalScroll，Canvas用weight占满剩余高度；右下角关闭按钮恢复并与标题、完整图表、日期轴同时可见。
- MainActivity.kt：整体趋势标题保留横屏/退出横屏图标；横屏卡片固定为页面88%×82%，Canvas使用剩余高度，关闭、系统返回及遮罩空白处均恢复原屏幕方向。
- AndroidManifest.xml：MainActivity接管方向与屏幕尺寸变化，确保切换横屏时不重建并丢失当前趋势弹窗。
- TrendRenderingTest：新增标题、横屏与退出横屏按钮及回调断言。未改趋势数据和绘图规则；等待云端快速编译验证，未发布。

## 2026-10-28 港维护色块布局调整（未发布）

- 用户确认：港维护使用白底，与下方病程卡片左右边缘对齐；高度与上方放疗日筛选按钮一致。
- CourseRecords.kt：复用时间线的横向偏移、尾部留白和22dp轴列缩进；港维护与分类筛选共用32dp高度，移除维护行上下撑高的padding。日期计算、持久化与已维护动作不变。
- CourseNavigationTest 增加实际页面的左右边缘和筛选按钮等高断言；已完成源码与尺寸链审查，尚未运行Android设备测试，不宣称实机验收。未修改发布配置。

## 2026-10-28 输入控件硬约束修复

- 统一输入体系改为直接基于 `BasicTextField` 构建，不再使用 Material `TextField` 及透明指示线；标题固定在输入区外，正常、聚焦、错误和禁用状态都不生成边框。
- MainActivity、CourseRecords、Editors、Updates、ImageDocuments 的普通输入、搜索、紧凑数值和行内编辑均接入同一基础组件；清除描边按钮，并补齐 FilterChip 的 `border=null`。
- 静态脚本、JVM 回归和 Compose UI 用例同步覆盖；主分支检查和正式发布会按文件与行号拒绝禁用组件、业务层直接 BasicTextField、描边按钮及筛选边框。
- 病程标题栏新增日期查看入口：原生日期选择器按本地日期匹配记录，命中后重置筛选并滚动到对应卡片；未命中仅提示，不触发新增流程。
- 删除 CourseReminderEditor、病程提醒摘要和 CourseReminderDialog 到期链路；旧 CourseRecord reminder 字段保留序列化兼容但完全休眠。导入页 TreatmentReminder 继续按最高“放N”触发，并用统一选择函数确定最早到期项。
- TreatmentReminder 增加可选 triggerDate：旧 JSON 缺失时继续作为放疗次数提醒；日期提醒不要求“放N”，到期或逾期仍有效。编辑器以无描边筛选项切换“放疗次数 / 日期”。
- TreatmentReminder 日期模式同时保存 triggerTime（旧数据默认 09:00）；HealthStore 成功落盘后统一同步 AlarmManager，删除、完成、恢复均由同一入口取消或重建任务。系统通知接收器只读取仍存在且未完成的日期提醒，启动、重启、系统时间/时区改变及应用更新后统一重建。Android 13+ 的 POST_NOTIFICATIONS 权限只在保存日期提醒时请求，不使用精确闹钟特殊权限。
- CourseRecord 增加 growthFactorInjection，旧 JSON 首次读取时仅从明确已执行的“升白针”备注迁移并立即持久化，后续只使用正式布尔字段。编辑器提供无描边纯色 FilterChip；标记本身使自动卡不再被视为空卡。
- 趋势事件参数统一为 TrendEventLayer；化疗与升白针共用日期插值和同一浅灰 1dp 竖线。化疗三角与升白针圆点直接使用同一 TrendEventShapeColor，颜色与透明度均不再分开设置，只以形状区分。growthFactorInjectionEventDates 合并连续自然日并只保留首日；升白针不写入横轴日期。
## 2026-10-29 病程体重三色块与体表面积（施工中，未发布）

- `PatientProfile` 与既有 JSON 患者资料增加向后兼容的 `heightCm`；“我的”可编辑和显示身高，完整备份继续随主偏好文件保存，空环境恢复保护也将身高视为患者资料。
- `WeightRecord` 增加可空的测量时身高快照；新体重取当前资料身高，编辑旧体重保留原快照，修改患者身高不回写历史。旧记录缺快照时在首次保存身高后补齐一次。
- 病程卡片与编辑页均已改为时间、体重、体表面积三个等宽色块，移除 `kg`；卡片固定 26dp，编辑页复用原 25.33dp 紧凑输入高度。
- BSA 由每条记录的冻结身高按 DuBois 公式派生，缺快照/体重不计算。已补公式、三位小数、无效输入、快照历史不变、升级持久化及 Compose 三块等宽等高回归。
- 主提交 `146f656715155838ae20ca35f93e524111b5b077`：同步守卫 run 40906803906 通过；Android quick run 40906803894 的 `compileDebugKotlin`、全量 JVM 回归和 `compileDebugAndroidTestKotlin` 均通过。准备发布 1.0.179（2026102911）。
