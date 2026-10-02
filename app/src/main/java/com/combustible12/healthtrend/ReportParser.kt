package com.combustible12.healthtrend

data class ParsedLabResult(
    val metricKey: String,
    val displayName: String,
    val value: Double,
    val unit: String,
    val referenceLow: Double?,
    val referenceHigh: Double?,
    val rawLine: String,
    val primary: Boolean
)

object ReportParser {
    private data class Spec(val key:String,val name:String,val aliases:List<String>,val unit:String,val low:Double?,val high:Double?,val primary:Boolean)
    private val specs=listOf(
        Spec("WBC","白细胞计数",listOf("WBC","白细胞计数","白细胞"),"×10^9/L",3.5,9.5,true),
        Spec("NEUT#","中性粒细胞计数",listOf("NEUT#","NEUT","中性粒细胞计数","中性粒细胞绝对值"),"×10^9/L",2.0,7.0,true),
        Spec("HGB","血红蛋白",listOf("HGB","血红蛋白"),"g/L",113.0,151.0,true),
        Spec("PLT","血小板计数",listOf("PLT","血小板计数","血小板"),"×10^9/L",100.0,300.0,true),
        Spec("ALT","丙氨酸氨基转移酶",listOf("ALT","丙氨酸氨基转移酶","谷丙转氨酶"),"U/L",7.0,40.0,true),
        Spec("AST","天门冬氨酸氨基转移酶",listOf("AST","天门冬氨酸氨基转移酶","谷草转氨酶"),"U/L",13.0,35.0,true),
        Spec("TBIL","总胆红素",listOf("TBIL","总胆红素"),"μmol/L",3.4,20.6,true),
        Spec("ALB","白蛋白",listOf("ALB","白蛋白"),"g/L",40.0,55.0,true),
        Spec("CREA","肌酐",listOf("CREA","CRE","肌酐"),"μmol/L",35.0,80.0,true),
        Spec("UREA","尿素",listOf("UREA","尿素"),"mmol/L",1.43,7.14,true),
        Spec("UA","尿酸",listOf("UA","尿酸"),"μmol/L",90.0,357.0,true)
    )
    private val number=Regex("""[-+]?\d+(?:\.\d+)?""")
    fun parse(text:String):List<ParsedLabResult>{
        val lines=text.lines().map{it.trim()}.filter{it.isNotEmpty()}
        return specs.mapNotNull{s->
            val line=lines.firstOrNull{ln->s.aliases.any{a->ln.contains(a,ignoreCase=true)}}?:return@mapNotNull null
            val value=number.findAll(line).mapNotNull{it.value.toDoubleOrNull()}.firstOrNull()?:return@mapNotNull null
            ParsedLabResult(s.key,s.name,value,s.unit,s.low,s.high,line,s.primary)
        }.distinctBy{it.metricKey}
    }
}
