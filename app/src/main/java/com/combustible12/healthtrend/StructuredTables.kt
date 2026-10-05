package com.combustible12.healthtrend
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class StructuredCell(val text:String="",val imageUri:String=""):java.io.Serializable
data class StructuredRow(val cells:List<StructuredCell>):java.io.Serializable
data class StructuredTable(val id:String=newId(),val title:String="未命名资料表",val createdAt:Long=System.currentTimeMillis(),val columns:List<String>,val rows:List<StructuredRow>,val sourceUri:String=""):java.io.Serializable
class StructuredTableStore(private val context:Context){
 private val prefs=context.getSharedPreferences("healthtrend_store_v1",Context.MODE_PRIVATE)
 fun all():List<StructuredTable>{val a=JSONArray(prefs.getString("structured_tables","[]"));return (0 until a.length()).map{fromJson(a.getJSONObject(it))}.sortedByDescending{it.createdAt}}
 fun save(t:StructuredTable){val all=all().filterNot{it.id==t.id}+t;check(prefs.edit().putString("structured_tables",JSONArray().apply{all.forEach{put(toJson(it))}}.toString()).commit())}
 fun own(uri:Uri):String{val dir=File(context.filesDir,"sources").apply{mkdirs()};val dest=File(dir,newId()+".table");context.contentResolver.openInputStream(uri).use{i->requireNotNull(i);dest.outputStream().use{i.copyTo(it)}};return Uri.fromFile(dest).toString()}
 private fun toJson(t:StructuredTable)=JSONObject().put("id",t.id).put("title",t.title).put("at",t.createdAt).put("source",t.sourceUri).put("columns",JSONArray(t.columns)).put("rows",JSONArray().apply{t.rows.forEach{r->put(JSONArray().apply{r.cells.forEach{c->put(JSONObject().put("text",c.text).put("image",c.imageUri))}})}})
 private fun fromJson(o:JSONObject)=StructuredTable(o.getString("id"),o.optString("title","未命名资料表"),o.optLong("at"),(0 until o.getJSONArray("columns").length()).map{o.getJSONArray("columns").getString(it)},(0 until o.getJSONArray("rows").length()).map{i->val a=o.getJSONArray("rows").getJSONArray(i);StructuredRow((0 until a.length()).map{j->val c=a.getJSONObject(j);StructuredCell(c.optString("text"),c.optString("image"))})},o.optString("source"))
}
private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitTable():T=kotlinx.coroutines.suspendCancellableCoroutine{c->addOnSuccessListener{if(c.isActive)c.resume(it)}.addOnFailureListener{if(c.isActive)c.resumeWithException(it)}}
private suspend fun importStructuredTable(context:Context,store:StructuredTableStore,uri:Uri):StructuredTable=withContext(Dispatchers.IO){
 val owned=store.own(uri);val bitmap=decodeReportBitmap(context,Uri.parse(owned),maxDimension=12000);val recognizer=TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
 try{val result=recognizer.process(InputImage.fromBitmap(bitmap,0)).awaitTable();val lines=result.textBlocks.flatMap{it.lines}.mapNotNull{l->l.boundingBox?.let{b->Triple(l.text,b,b.centerY())}}.sortedBy{it.third};require(lines.isNotEmpty()){"没有识别到表格文字"}
  val headerY=lines.firstOrNull{it.first.contains("名称")||it.first.contains("使用时间")}?.third?:lines.minOf{it.third};val body=lines.filter{it.third>headerY+10};val cuts=listOf(.13,.29,.46,.82).map{(it*bitmap.width).toInt()}
  val grouped=mutableListOf<MutableList<Triple<String,android.graphics.Rect,Int>>>();body.forEach{line->val last=grouped.lastOrNull();val cy=last?.map{it.third}?.average();if(cy!=null&&kotlin.math.abs(line.third-cy)<28)last.add(line) else grouped.add(mutableListOf(line))}
  val rows=grouped.map{g->val cells=MutableList(5){StructuredCell()};g.forEach{(text,box,_)->val col=cuts.indexOfFirst{box.centerX()<it}.let{if(it<0)4 else it};cells[col]=cells[col].copy(text=(cells[col].text+" "+text).trim())};StructuredRow(cells)}.filter{r->r.cells.any{it.text.isNotBlank()}}
  StructuredTable(columns=listOf("名称","使用时间","使用次数","目的","图片"),rows=rows,sourceUri=owned)
 }finally{recognizer.close()}
}
@Composable fun StructuredTablesPage(m:Modifier,open:(StructuredTable)->Unit){
 val context=LocalContext.current;val store=remember{StructuredTableStore(context)};var revision by remember{mutableIntStateOf(0)};var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf("")};val scope=rememberCoroutineScope()
 val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null){busy=true;scope.launch{try{val table=importStructuredTable(context,store,uri);store.save(table);revision++;open(table)}catch(ex:Exception){error="表格识别失败："+ex.message}finally{busy=false}}}}
 val tables=remember(revision){store.all()};var query by rememberSaveable{mutableStateOf("")}
 Screen(m,"资料表","保留表格结构，文字可搜索、可编辑"){
  Button({picker.launch(arrayOf("image/*"))},Modifier.fillMaxWidth(),enabled=!busy){Icon(Icons.Outlined.AddPhotoAlternate,null);Spacer(Modifier.width(8.dp));Text(if(busy)"正在识别表格…" else "导入表格图片")}
  OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),label={Text("搜索表格内容")},singleLine=true)
  val shown=tables.filter{table->query.isBlank()||table.title.contains(query,true)||table.rows.any{r->r.cells.any{it.text.contains(query,true)}}}
  if(shown.isEmpty())Paper{Text(if(query.isBlank())"还没有资料表" else "没有找到相关内容")}
  shown.forEach{table->Paper(Modifier.clickable{open(table)}){Text(table.title,fontWeight=FontWeight.Bold);Text(table.rows.size.toString()+" 行 · "+table.columns.size+" 列",color=Muted,fontSize=12.sp);if(query.isNotBlank())table.rows.firstOrNull{r->r.cells.any{it.text.contains(query,true)}}?.let{r->Text(r.cells.joinToString(" · "){it.text}.take(90),fontSize=12.sp)}}}
  if(error.isNotBlank())Text(error,color=Bad)
 }
}
@Composable fun StructuredTableEditor(initial:StructuredTable,onClose:()->Unit,onSaved:(StructuredTable)->Unit,onSource:(String)->Unit){
 var table by remember{mutableStateOf(initial)};var edit by remember{mutableStateOf<Pair<Int,Int>?>(null)};var editText by remember{mutableStateOf("")}
 FullPage("资料表",onClose,bottom={Button({onSaved(table)},Modifier.fillMaxWidth(),enabled=table.title.isNotBlank()){Text("保存")}}){m->Column(m.verticalScroll(rememberScrollState()).padding(horizontal=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
  OutlinedTextField(table.title,{table=table.copy(title=it)},Modifier.fillMaxWidth(),label={Text("标题")},singleLine=true);if(table.sourceUri.isNotBlank())TextButton({onSource(table.sourceUri)}){Text("查看原图")}
  Row(Modifier.fillMaxWidth()){table.columns.take(5).forEachIndexed{i,h->TableCell(h,listOf(.13f,.16f,.17f,.36f,.18f)[i],true){}}}
  table.rows.forEachIndexed{ri,row->Row(Modifier.fillMaxWidth()){row.cells.take(5).forEachIndexed{ci,c->TableCell(c.text,listOf(.13f,.16f,.17f,.36f,.18f)[ci],false){edit=ri to ci;editText=c.text}}}}
 }}
 edit?.let{pos->AlertDialog(onDismissRequest={edit=null},title={Text("编辑单元格")},text={OutlinedTextField(editText,{editText=it},Modifier.fillMaxWidth(),minLines=4)},confirmButton={TextButton({val rows=table.rows.toMutableList();val cells=rows[pos.first].cells.toMutableList();cells[pos.second]=cells[pos.second].copy(text=editText);rows[pos.first]=StructuredRow(cells);table=table.copy(rows=rows);edit=null}){Text("确定")}},dismissButton={TextButton({edit=null}){Text("取消")}})}
}
@Composable private fun RowScope.TableCell(text:String,weight:Float,header:Boolean,onClick:()->Unit){Box(Modifier.weight(weight).heightIn(min=54.dp).border(.5.dp,Color(0xFFB7B7A8)).clickable(onClick=onClick).padding(4.dp),contentAlignment=Alignment.CenterStart){Text(text,fontSize=if(header)11.sp else 10.sp,fontWeight=if(header)FontWeight.Bold else FontWeight.Normal,lineHeight=14.sp)}}
