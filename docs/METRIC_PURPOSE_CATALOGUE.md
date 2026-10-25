# 指标说明库维护规则

统一指标池实际147项，另保留RDW、PDW、AMY三项历史说明。说明只读，不修改身份识别、单位、报告或存储。

- `MetricPurposeCatalogue.Entry` 同时维护短说明与详细解释；趋势卡片和点击弹窗共享同一条目。`metricPurpose()`、`metricPurposeDetail()`及旧定性API均从这里读取。
- 1.0.155及更早版本已经展示的短说明属于兼容内容，必须原文保留；补充新指标时只能填写原先缺失的条目，禁止以“统一措辞”为由批量改写旧说明。
- 旧版原文固定保存在 `LegacyMetricPurpose.kt`，运行时优先覆盖说明库中的同名条目；该文件只允许校验和读取，不作为后续措辞优化对象。
- 原先没有说明的指标沿用旧版表达行为：定量项目直接写明升高或降低可能关联的情况，定性项目直接写明阳性、阴性或检出的含义。禁止使用只重复检查分类的占位文案。
- 短说明优先6～12汉字，不以凑字数牺牲准确性。橙色12sp、17sp行高、无边框无背景、自动换行，沿用原来样式。
- 说明表示一般用途；↑/↓为条件性关联，不根据正常结果断言疾病；肿瘤标志物不能等同癌症诊断。
- 代码先使用既有`canonicalMetricKey`；中文标准名及别名只使用原有注册表的完整、唯一匹配，禁止前缀猜测。保留HIV/SYPHILIS原说明别名，不写入身份系统。
- 历史RDW/PDW不强行归为CV或SD。未登记自定义项显示“指标说明待补充”，点击解释未核对状态，不编造医学意义；标准指标缺失由覆盖测试阻断。
- 新增标准指标必须同步维护短说明、具体详细解释、审核清单及测试。测试从`UnifiedMetricStandard.keys()`读取实际集合；独立TSV审核清单锁定代码/名称/短文/详细解释，领域断言验证易混项目。
- 使用当前趋势卡片调用的`TrendPurposeExplanation`验证实际点击、切换指标、关闭弹窗；保留原字体、大字及窄屏测试和相关模块回归。

## 医学内容核对来源（2026-10-25）

用途描述为科普概括，不是个体诊断；原有准确详细解释保留。以下为逐组核对依据：

- [MedlinePlus 血常规](https://medlineplus.gov/lab-tests/complete-blood-count-cbc/)、[血小板](https://medlineplus.gov/lab-tests/platelet-tests/)
- [综合代谢](https://medlineplus.gov/lab-tests/comprehensive-metabolic-panel-cmp/)、[肝功能](https://medlineplus.gov/lab-tests/liver-function-tests/)
- [尿液检查](https://medlineplus.gov/ency/article/003579.htm)、[粪便寄生虫](https://medlineplus.gov/lab-tests/ova-and-parasite-test/)
- [CDC 乙肝检测解释](https://www.cdc.gov/mmwr/volumes/72/rr/rr7201a1.htm)、[肝炎检测](https://medlineplus.gov/lab-tests/hepatitis-testing/)
- [肿瘤标志物](https://medlineplus.gov/lab-tests/tumor-marker-tests/)、[甲状腺抗体](https://medlineplus.gov/lab-tests/thyroid-antibody-tests/)
- [铁代谢](https://medlineplus.gov/lab-tests/iron-tests/)、[降钙素原](https://medlineplus.gov/lab-tests/procalcitonin-test/)
- [D-二聚体](https://medlineplus.gov/lab-tests/d-dimer-test/)、[PT/INR](https://medlineplus.gov/lab-tests/prothrombin-time-test-and-inr-ptinr/)、[APTT](https://medlineplus.gov/lab-tests/partial-thromboplastin-time-ptt-test/)
- [肌酸激酶](https://medlineplus.gov/lab-tests/creatine-kinase/)、[肌钙蛋白](https://medlineplus.gov/lab-tests/troponin-test/)
- [糖化血红蛋白](https://medlineplus.gov/lab-tests/hemoglobin-a1c-hba1c-test/)、[C肽](https://medlineplus.gov/lab-tests/c-peptide-test/)、[胰岛素](https://medlineplus.gov/lab-tests/insulin-in-blood-test/)、[淀粉酶](https://medlineplus.gov/lab-tests/amylase-test/)
