package com.combustible12.healthtrend

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Warm=Color(0xFFFAF9F6); private val Ink=Color(0xFF292927); private val Muted=Color(0xFF8D8B85)
private val Accent=Color(0xFFF28B58); private val Good=Color(0xFF56A978); private val Bad=Color(0xFFD9665B)
data class Metric(val title:String,val short:String,val value:String,val unit:String,val range:String,val status:String,val values:List<Float>)

class MainActivity:ComponentActivity(){ override fun onCreate(b:Bundle?){super.onCreate(b);setContent{MaterialTheme(colorScheme=lightColorScheme(background=Warm,surface=Color.White,primary=Accent)){App()}}}}

@Composable fun App(){
 var tab by remember{mutableIntStateOf(0)}
 Scaffold(containerColor=Warm,bottomBar={NavigationBar(containerColor=Color.White){
  listOf("首页" to Icons.Outlined.Home,"趋势" to Icons.Outlined.ShowChart,"记录" to Icons.Outlined.FolderOpen,"我的" to Icons.Outlined.Person).forEachIndexed{i,p->
   NavigationBarItem(tab==i,{tab=i},{Icon(p.second,null)},label={Text(p.first)})
  }}}){pad->when(tab){0->Home(Modifier.padding(pad));1->Trends(Modifier.padding(pad));2->Records(Modifier.padding(pad));else->Mine(Modifier.padding(pad))}}
}

@Composable fun Home(m:Modifier){
 val ctx=LocalContext.current
 var ocrStatus by remember{mutableStateOf("")}
 var parsedResults by remember{mutableStateOf<List<ParsedLabResult>>(emptyList())}
 var showConfirm by remember{mutableStateOf(false)}
 var pendingUri by remember{mutableStateOf<Uri?>(null)}
 var hospital by remember{mutableStateOf("福建省妇幼保健院")}
 val store=remember{HealthStore(ctx)}
 val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
  if(uri!=null){pendingUri=uri;try{ctx.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}catch(_:Exception){}
   ocrStatus="正在识别报告…"
   TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()).process(InputImage.fromFilePath(ctx,uri))
    .addOnSuccessListener{t->parsedResults=ReportParser.parse(t.text);ctx.getSharedPreferences("healthtrend",0).edit().putString("last_report_uri",uri.toString()).putString("last_report_ocr",t.text).apply();ocrStatus="识别完成：识别到 ${parsedResults.size} 个重点指标";showConfirm=true}
    .addOnFailureListener{ocrStatus="识别失败，请重新选择清晰图片"}
  }
 }
 if(showConfirm){AlertDialog(onDismissRequest={showConfirm=false},title={Text("核对识别结果")},text={Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState())){OutlinedTextField(hospital,{hospital=it},label={Text("医院")},modifier=Modifier.fillMaxWidth());val existing=store.latestTemplate(hospital,"血常规");Text(if(existing==null)"首次录入：保存后建立医院模板" else "已找到确认模板：本次沿用原单位与参考范围",color=if(existing==null)Accent else Good,fontSize=12.sp);if(parsedResults.isEmpty())Text("暂未匹配到重点指标，可返回重拍或重新导入。",color=Bad);parsedResults.forEach{r->Card(Modifier.fillMaxWidth().padding(vertical=4.dp),colors=CardDefaults.cardColors(containerColor=Warm)){Column(Modifier.padding(12.dp)){Text("${r.displayName}  ${r.metricKey}",fontWeight=FontWeight.Bold);Text("${r.value} ${r.unit}",fontSize=20.sp);Text("参考 ${r.referenceLow}–${r.referenceHigh}",color=Muted,fontSize=12.sp)}}}}},confirmButton={Button({pendingUri?.let{u->val template=store.latestTemplate(hospital,"血常规")?:store.confirmTemplate(hospital,"血常规",parsedResults);store.saveReport(store.buildReport(hospital,"血常规",System.currentTimeMillis(),u.toString(),parsedResults,template))};showConfirm=false;ocrStatus="已保存到历史记录：${parsedResults.size} 个指标"}){Text("确认保存")}},dismissButton={TextButton({showConfirm=false}){Text("返回")}})}
 Column(m.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
  Spacer(Modifier.height(8.dp));Text("HealthTrend",fontSize=28.sp,fontWeight=FontWeight.Bold,color=Ink)
  Text("把检查、症状和病历放在一条清楚的时间线上",color=Muted)
  Card(shape=RoundedCornerShape(28.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFFFFEEE5))){Column(Modifier.padding(22.dp)){
   Text("今天要记录什么？",fontSize=21.sp,fontWeight=FontWeight.Bold,color=Ink);Spacer(Modifier.height(14.dp))
   Button({picker.launch(arrayOf("image/*"))},Modifier.fillMaxWidth().height(54.dp),shape=RoundedCornerShape(18.dp),colors=ButtonDefaults.buttonColors(containerColor=Accent)){Icon(Icons.Outlined.DocumentScanner,null);Spacer(Modifier.width(8.dp));Text("拍照识别检查报告")}
   TextButton({picker.launch(arrayOf("image/*"))},Modifier.align(Alignment.CenterHorizontally)){Text("或从相册导入",color=Ink)}
   if(ocrStatus.isNotBlank()) Text(ocrStatus,color=if(ocrStatus.contains("失败"))Bad else Good,fontSize=12.sp)
  }}
  Text("健康记录",fontSize=20.sp,fontWeight=FontWeight.Bold,color=Ink)
  Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){Quick("检查指标","趋势与异常",Icons.Outlined.MonitorHeart,Modifier.weight(1f));Quick("症状记录","程度与频率",Icons.Outlined.EditNote,Modifier.weight(1f))}
  Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){Quick("病历资料","报告与影像",Icons.Outlined.Description,Modifier.weight(1f));Quick("用药记录","时间与备注",Icons.Outlined.Medication,Modifier.weight(1f))}
  Text("最近",fontSize=20.sp,fontWeight=FontWeight.Bold,color=Ink)
  Card(shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){Column(Modifier.padding(18.dp)){Text("血常规",fontWeight=FontWeight.Bold);Text("福建省妇幼保健院 · 09/26",color=Muted,fontSize=13.sp);Spacer(Modifier.height(12.dp));Text("WBC  3.75     NEUT#  1.92 ↓     HGB  102 ↓",fontSize=14.sp)}}
 }
}
@Composable fun Quick(t:String,s:String,icon:androidx.compose.ui.graphics.vector.ImageVector,m:Modifier){Card(onClick={},modifier=m,shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){Column(Modifier.padding(18.dp)){Icon(icon,null,tint=Accent);Spacer(Modifier.height(20.dp));Text(t,fontWeight=FontWeight.Bold);Text(s,color=Muted,fontSize=12.sp)}}}

@Composable fun Trends(m:Modifier){
 val ms=listOf(Metric("白细胞计数","WBC","3.75","×10⁹/L","3.5–9.5","正常",listOf(7.25f,4.35f,2.36f,3.75f)),Metric("中性粒细胞计数","NEUT#","1.92","×10⁹/L","2.00–7.00","偏低",listOf(5.77f,2.57f,.91f,1.92f)),Metric("血红蛋白","HGB","102","g/L","113–151","偏低",listOf(113f,106f,100f,102f)))
 Column(m.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){Spacer(Modifier.height(8.dp));Text("指标趋势",fontSize=28.sp,fontWeight=FontWeight.Bold);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("血常规","肝功能","肾功能").forEachIndexed{i,s->FilterChip(i==0,{},label={Text(s)})}};ms.forEach{Trend(it)}}
}
@Composable fun Trend(x:Metric){Card(shape=RoundedCornerShape(26.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){Column(Modifier.padding(20.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Column{Text(x.title,fontWeight=FontWeight.Bold);Text(x.short+" · 参考 "+x.range,color=Muted,fontSize=12.sp)};Column(horizontalAlignment=Alignment.End){Text(x.value,fontSize=28.sp,fontWeight=FontWeight.Bold,color=if(x.status=="正常")Good else Bad);Text(x.unit,color=Muted,fontSize=11.sp)}};Spacer(Modifier.height(14.dp));Spark(x.values,if(x.status=="正常")Good else Bad);Spacer(Modifier.height(8.dp));Text("09/18       09/23       09/25       09/26",color=Muted,fontSize=11.sp)}}}
@Composable fun Spark(v:List<Float>,c:Color){Canvas(Modifier.fillMaxWidth().height(92.dp)){val mn=v.min();val mx=v.max();val span=(mx-mn).coerceAtLeast(1f);val p=Path();v.forEachIndexed{i,n->val x=size.width*i/(v.size-1);val y=size.height-(n-mn)/span*size.height*.72f-size.height*.12f;if(i==0)p.moveTo(x,y)else p.lineTo(x,y)};drawPath(p,c,style=Stroke(3.dp.toPx()));v.forEachIndexed{i,n->val x=size.width*i/(v.size-1);val y=size.height-(n-mn)/span*size.height*.72f-size.height*.12f;drawCircle(c,5.dp.toPx(),Offset(x,y))}}}

@Composable fun Records(m:Modifier){Column(m.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){Spacer(Modifier.height(8.dp));Text("记录",fontSize=28.sp,fontWeight=FontWeight.Bold);Text("检查报告、症状记录和病历资料",color=Muted);listOf("检查报告" to "按医院和日期归档","症状记录" to "记录程度、频率与备注","病历资料" to "诊断、影像、出院记录").forEach{Card(shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=Color.White),modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(20.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(it.first,fontWeight=FontWeight.Bold);Text(it.second,color=Muted,fontSize=13.sp)};Icon(Icons.Outlined.ChevronRight,null,tint=Muted)}}}}}
@Composable fun Mine(m:Modifier){Column(m.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){Spacer(Modifier.height(8.dp));Text("我的",fontSize=28.sp,fontWeight=FontWeight.Bold);Text("医院模板",fontSize=19.sp,fontWeight=FontWeight.Bold);Card(shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){Column(Modifier.padding(20.dp)){Text("医院模板",fontWeight=FontWeight.Bold);Text("已确认模板会在同院同类型报告中复用",color=Muted);Spacer(Modifier.height(10.dp));Text("同院报告自动沿用已确认单位与参考范围",color=Good,fontSize=13.sp)}};Text("模板只能由你主动编辑；后续 OCR 不会自动覆盖已确认范围。",color=Muted,fontSize=13.sp);OutlinedButton({},Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp)){Icon(Icons.Outlined.SystemUpdate,null);Spacer(Modifier.width(8.dp));Text("检查更新")}}}
