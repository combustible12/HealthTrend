package com.combustible12.healthtrend

/** Curated display labels and common units; aliases are recognition-only.
 * Reference intervals are NOT universal constants and are collected from saved reports.
 * The registry is additive: never rewrites a user's confirmed name or measurements.
 */
object UnifiedMetricStandard {
 data class Definition(val name:String,val unit:String,val aliases:Set<String>)
 private val definitions:Map<String,Definition> = listOf(
  Triple("WBC","白细胞计数","10^9/L" to "白细胞|白细胞数|白细胞数目"),
  Triple("RBC","红细胞计数","10^12/L" to "红细胞|红细胞数|红细胞数目"),
  Triple("HGB","血红蛋白","g/L" to "血红蛋白浓度|血色素"),
  Triple("HCT","红细胞压积","%" to "血细胞比容|红细胞比容"),
  Triple("PLT","血小板计数","10^9/L" to "血小板|血小板数目"),
  Triple("MCV","平均红细胞体积","fL" to "红细胞平均体积"),
  Triple("MCH","平均红细胞血红蛋白量","pg" to "平均血红蛋白含量"),
  Triple("MCHC","平均红细胞血红蛋白浓度","g/L" to "平均血红蛋白浓度"),
  Triple("RDW-CV","红细胞分布宽度CV","%" to "红细胞体积分布宽度CV"),
  Triple("RDW-SD","红细胞分布宽度SD","fL" to "红细胞体积分布宽度SD"),
  Triple("NEUT#","中性粒细胞计数","10^9/L" to "中性粒细胞绝对值|中性粒细胞数目"),
  Triple("NEUT%","中性粒细胞百分比","%" to "中性粒细胞比率|中性粒细胞比例"),
  Triple("LYMPH#","淋巴细胞计数","10^9/L" to "淋巴细胞绝对值|淋巴细胞数目"),
  Triple("LYMPH%","淋巴细胞百分比","%" to "淋巴细胞比率|淋巴细胞比例"),
  Triple("MONO#","单核细胞计数","10^9/L" to "单核细胞绝对值"),
  Triple("MONO%","单核细胞百分比","%" to "单核细胞比率"),
  Triple("EOS#","嗜酸性粒细胞计数","10^9/L" to "嗜酸性粒细胞绝对值"),
  Triple("EOS%","嗜酸性粒细胞百分比","%" to "嗜酸性粒细胞比率"),
  Triple("BASO#","嗜碱性粒细胞计数","10^9/L" to "嗜碱性粒细胞绝对值"),
  Triple("BASO%","嗜碱性粒细胞百分比","%" to "嗜碱性粒细胞比率"),
  Triple("NRBC#","有核红细胞计数","10^9/L" to "有核红细胞绝对值"),
  Triple("NRBC%","有核红细胞百分比","%" to "有核红细胞比率"),
  Triple("MPV","平均血小板体积","fL" to "血小板平均体积"),
  Triple("PDW","血小板分布宽度","%" to "血小板体积分布宽度"),
  Triple("PCT","血小板压积","%" to "血小板比容"),
  Triple("P-LCR","大型血小板比率","" to "大血小板比率"),
  Triple("P-LCC","大型血小板计数","10^9/L" to "大血小板数目"),
  Triple("TP","总蛋白","g/L" to "血清总蛋白"),
  Triple("ALB","白蛋白","g/L" to "血清白蛋白"),
  Triple("GLOB","球蛋白","g/L" to "总球蛋白"),
  Triple("A/G","白蛋白/球蛋白","" to "白球比"),
  Triple("ALT","谷丙转氨酶","U/L" to "丙氨酸氨基转移酶|谷丙氨酸氨基转移酶"),
  Triple("AST","谷草转氨酶","U/L" to "天门冬氨酸氨基转移酶|天冬氨酸氨基转移酶"),
  Triple("AST/ALT","谷草/谷丙","" to "谷草谷丙比"),
  Triple("ALT/AST","谷丙/谷草","" to "谷丙谷草比"),
  Triple("GGT","γ-谷氨酰转肽酶","U/L" to "谷氨酰转移酶|γ-谷氨酰转移酶"),
  Triple("ALP","碱性磷酸酶","U/L" to "血清碱性磷酸酶"),
  Triple("TBIL","总胆红素","μmol/L" to "胆红素总量"),
  Triple("DBIL","直接胆红素","μmol/L" to "结合胆红素"),
  Triple("IBIL","间接胆红素","μmol/L" to "非结合胆红素"),
  Triple("TBA","总胆汁酸","μmol/L" to "血清总胆汁酸"),
  Triple("CHE","胆碱酯酶","U/L" to "血清胆碱酯酶"),
  Triple("PA","前白蛋白","mg/L" to "转甲状腺素蛋白"),
  Triple("GLU","葡萄糖","mmol/L" to "血糖|血清葡萄糖"),
  Triple("UREA","尿素","mmol/L" to "血尿素"),
  Triple("CREA","肌酐","μmol/L" to "血肌酐|血清肌酐"),
  Triple("UA","尿酸","μmol/L" to "血尿酸"),
  Triple("UREA/CREA","尿素/肌酐","" to "尿素肌酐比"),
  Triple("K","钾","mmol/L" to "血钾|钾离子"),
  Triple("NA","钠","mmol/L" to "血钠|钠离子"),
  Triple("CL","氯","mmol/L" to "血氯|氯离子"),
  Triple("CA","钙","mmol/L" to "血钙|总钙"),
  Triple("MG","镁","mmol/L" to "血镁"),
  Triple("PHOS","磷","mmol/L" to "无机磷|无机磷酸盐"),
  Triple("HCO3","碳酸氢根","mmol/L" to "碳酸氢盐"),
  Triple("AG","阴离子间隙","mmol/L" to "血清阴离子间隙"),
  Triple("OSM","渗透压","" to "渗透浓度"),
  Triple("LDH","乳酸脱氢酶","U/L" to "乳酸脱氢酶总活性"),
  Triple("CK","肌酸激酶","U/L" to "肌酸磷酸激酶"),
  Triple("CKMB","肌酸激酶MB同工酶","U/L" to "肌酸激酶MB亚型|肌酸激酶同工酶"),
  Triple("CKMB/CK","肌酸激酶同工酶/肌酸激酶","" to "肌酸激酶同工酶比"),
  Triple("TG","甘油三酯","mmol/L" to "三酰甘油"),
  Triple("CHOL","总胆固醇","mmol/L" to "胆固醇"),
  Triple("HDLC","高密度脂蛋白胆固醇","mmol/L" to "高密度胆固醇"),
  Triple("LDLC","低密度脂蛋白胆固醇","mmol/L" to "低密度胆固醇"),
  Triple("APOA1","载脂蛋白A1","g/L" to "载脂蛋白-A1"),
  Triple("APOB","载脂蛋白B","g/L" to "载脂蛋白-B"),
  Triple("SCC","鳞状细胞癌相关抗原","ng/mL" to "鳞癌相关抗原|鳞状细胞癌抗原"),
  Triple("AFP","甲胎蛋白","ng/mL" to "血清甲胎蛋白"),
  Triple("CEA","癌胚抗原","ng/mL" to "癌胚抗原测定"),
  Triple("CA125","糖类抗原125","U/mL" to "糖类抗原12-5"),
  Triple("CA153","糖类抗原153","U/mL" to "糖类抗原15-3"),
  Triple("CA199","糖类抗原199","U/mL" to "糖类抗原19-9"),
  Triple("CA724","糖类抗原724","U/mL" to "糖类抗原72-4"),
  Triple("HE4","人附睾蛋白4","pmol/L" to "附睾蛋白4"),
  Triple("NSE","神经元特异性烯醇化酶","ng/mL" to "神经元特异性烯醇酶"),
  Triple("CYFRA21-1","细胞角蛋白19片段","ng/mL" to "细胞角蛋白19片段抗原"),
 ).associate{(key,name,rest)->
  key to Definition(name,rest.first,rest.second.split('|').filter{it.isNotBlank()}.toSet())
 }
 fun definition(key:String):Definition?=definitions[canonicalMetricKey(key)]
 /** Exact-name recognition shared by parser and saved report import.
  * Ambiguous aliases never silently choose a metric.
  */
 private val identities:Map<String,Set<String>> = buildMap {
  definitions.forEach{(key,definition)->
   (definition.aliases+definition.name).forEach{name->
    val normalized=name.trim().replace('／','/').replace('：',':')
    if(normalized.isNotBlank())put(normalized,(get(normalized).orEmpty()+key))
   }
  }
 }
 fun resolveName(name:String):String? {
  val normalized=name.trim().replace('／','/').replace('：',':')
  return identities[normalized]?.singleOrNull()
 }
 fun keys():Set<String> = definitions.keys
}
