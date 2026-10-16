# 估计边界与单侧参考区域：实际验证（2026-10-17）

## 当前业务与存储边界

- 用户新授权允许合理绘图估计，覆盖此前绝不画估计线的旧限制。trendReferenceRange继续返回原始精确边界；新增trendPlotReferenceRange仅为绘图包络，trendRangeEstimateText明确显示估计。status、JSON存储、统一范围池、病程未改。
- 0.188–0.2??、结果0.250↑的绘图上限为0.249；无提示为可能包络0.299；0.1??下限配合0.150↓取0.151。保留原文与内部异常证据；矛盾不生成估计阴影。
- 主图和整体趋势使用相同单侧区域几何：低界已知/高界空则从画布顶到低界填绿；高界已知/低界空则从高界到画布底填绿，只画已知/估计的有限边界，不把画布裁切边缘画成参考线。

## 实际运行

- TrendReferenceBandTest六项：只有下限、只有上限、完整范围、双空范围、反向范围、越界裁切。Kotlin2.0.21独立编译、JUnit实际运行6/6通过。
- PartialRangeIntegrationTest保留源上下限不变断言，增加绘图0.249/0.151/0.299、估计文字、原始问号及异常状态不变、矛盾无估计等断言。
- 发布提交57255ec7aba3ab1828c4a7fa26968dca3950d770；[run38952693273](https://github.com/combustible12/HealthTrend/actions/runs/38952693273)成功：compileDebugKotlin、testDebugUnitTest（XML统计164/164，失败0/错误0/跳过0）、compileDebugAndroidTestKotlin、assembleRelease及发布成功。同步守卫38952693051及本地同步/OCR边界检查通过。
- 未实际运行设备画面、手势及用户手机升级，不把几何/模型测试等同像素级UI验收。

## APK

- 1.0.106 / 2026101708；包名com.combustible12.healthtrend。[下载](https://github.com/combustible12/HealthTrend/releases/download/v2026101708/HealthTrend.apk)。
- 已完整下载，56868592字节；SHA256 81782f334456241338302e972660b1f7872709e3133a5a6a41c47c37ddb58f3a，匹配GitHub资产。ZIP CRC、manifest、apksig校验通过。
- 证书SHA256 5acccb7b281fbe2878ff8e62b57e4a1149c82bcf40e3c0b36d8e709c9da95628与上一版一致；可保留原资料直接覆盖安装。保留更新缓存修复、40dp底部按钮、点击名称查看别名功能。
