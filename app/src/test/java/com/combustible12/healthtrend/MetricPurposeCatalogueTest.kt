package com.combustible12.healthtrend

import org.junit.Assert.*
import org.junit.Test

class MetricPurposeCatalogueTest {
 @Test fun allRegisteredMetricPurposesAreConcise() {
  val keys=listOf("UR-GLU","UR-BIL","UR-KET","UR-SG","UR-PH","UR-PRO","UR-URO","UR-NIT","UR-BLD","UR-LEU","ST-COLOR","ST-CONSISTENCY","ST-ASCARIS","ST-TRICHURIS","ST-HOOKWORM","ST-PINWORM","ST-AMOEBA-T","ST-AMOEBA-C","ST-FUNGI","ST-WBC","ST-RBC","ST-FAT","HPV16-DNA","HPV18-DNA","HPV-OTHER12-DNA","HBSAG","HBSAB","HBEAG","HBEAB","HBCAB","HIVAB","HCVAB","HIVAGAB","TPPA","TPAB","WBC","RBC","HGB","HCT","PLT","MCV","MCH","MCHC","RDW-CV","RDW-SD","NEUT#","NEUT%","LYMPH#","LYMPH%","MONO#","MONO%","EOS#","EOS%","BASO#","BASO%","NRBC#","NRBC%","MPV","PDW-CV","PDW-SD","PCT","P-LCR","P-LCC","TP","ALB","GLOB","A/G","ALT","AST","AST/ALT","ALT/AST","GGT","ALP","TBIL","DBIL","IBIL","TBA","CHE","PA","GLU","UREA","CREA","UA","UREA/CREA","K","NA","CL","CA","MG","PHOS","HCO3","AG","OSM","LDH","CK","CKMB","CKMB/CK","TG","CHOL","HDLC","LDLC","APOA1","APOB","SCC","AFP","CEA","CA125","CA153","CA199","CA724","HE4","NSE","CYFRA21-1","ESR","CRP","HSCRP","PCTIN","FERR","FE","TRANSFERRIN","TIBC","UIBC","B12","FOLATE","DDIMER","FDP","FIB","PT","APTT","TT","INR","TSH","FT3","FT4","T3","T4","TPOAB","TGAB","HBA1C","CPEP","INS","BNP","NT-PROBNP","CTNI","CTNT","MYO","VITD25")
  assertEquals(147,keys.size)
  keys.forEach{key->
   val summary=MetricPurposeCatalogue.short(key)
   assertNotNull("Missing description for $key",summary)
   assertTrue("Too long: $key",summary!!.length<=20)
  }
 }
 @Test fun tumorMarkersDoNotClaimDiagnosis() {
  listOf("CEA","AFP","SCC","CA125","CA153","CA199","CA724","HE4").forEach{
   val summary=MetricPurposeCatalogue.short(it)!!
   assertFalse(summary.contains("确诊"))
   assertFalse(summary.contains("患癌"))
  }
 }
}
