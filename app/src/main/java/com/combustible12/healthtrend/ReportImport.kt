package com.combustible12.healthtrend

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.*
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ImportActions(val camera:()->Unit,val gallery:()->Unit,val manual:()->Unit,val busy:Boolean,val message:String)
private suspend fun <T> Task<T>.result():T=suspendCancellableCoroutine{c->addOnSuccessListener{if(c.isActive)c.resume(it)}.addOnFailureListener{if(c.isActive)c.resumeWithException(it)}.addOnCanceledListener{c.cancel()}}
@Composable fun rememberReportImport(store:HealthStore,ready:(ReportDraft)->Unit,error:(String)->Unit):ImportActions {
 val context=LocalContext.current;val scope=rememberCoroutineScope();var busy by remember{mutableStateOf(false)};var message by remember{mutableStateOf("")};var cameraUri by rememberSaveable{mutableStateOf<Uri?>(null)}
 fun recognize(uris:List<Uri>){if(uris.isEmpty()||busy)return;busy=true;message="正在保存原图和识别报告…"
  scope.launch{
   val owned=mutableListOf<String>();val texts=mutableListOf<String>();val failures=mutableListOf<String>()
   try{
    val recognizer=TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    try{uris.forEachIndexed{i,uri->
     val saved=withContext(Dispatchers.IO){store.ownImage(uri)};owned.add(saved)
     message="正在识别第 ${i+1}/${uris.size} 页…"
     try{
      val input=withContext(Dispatchers.IO){InputImage.fromFilePath(context,Uri.parse(saved))}
      val t=recognizer.process(input).result()
      // Merge fragmented table columns by the line baselines, then restore left-to-right order.
      val lines=t.textBlocks.flatMap{it.lines}.filter{it.boundingBox!=null}.sortedBy{it.boundingBox!!.centerY()}
      val groups=mutableListOf<MutableList<com.google.mlkit.vision.text.Text.Line>>()
      lines.forEach{line->val box=line.boundingBox!!;val group=groups.lastOrNull();val base=group?.firstOrNull()?.boundingBox
       if(base!=null && kotlin.math.abs(box.centerY()-base.centerY())<=minOf(box.height(),base.height())*.5)group.add(line)else groups.add(mutableListOf(line))}
      texts.add(groups.joinToString("\n"){row->row.sortedBy{it.boundingBox!!.left}.joinToString("  "){it.text}})
     }catch(e:Exception){failures.add("第 ${i+1} 页识别失败，原图已保留，可手动补录。")}
    }}finally{recognizer.close()}
    val raw=texts.joinToString("\n\n");val parsed=ReportParser.parse(raw)
    val hospital=raw.lines().firstOrNull{it.length<70&&(it.contains("医院")||it.contains("保健院"))}.orEmpty()
    val date=Regex("(20\\d{2})[-/年.](\\d{1,2})[-/月.](\\d{1,2})").find(raw)?.let{m->"${m.groupValues[1]}-${m.groupValues[2].padStart(2,'0')}-${m.groupValues[3].padStart(2,'0')}"}.orEmpty()
    val type=when{raw.contains("肝功能")->"肝功能";raw.contains("肾功能")->"肾功能";raw.contains("血常规")->"血常规";else->"检查报告"}
    val template=store.latestTemplate(hospital,type)
    ready(ReportDraft(hospital,type,date=date,images=owned,ocr=raw,rows=store.applyTemplate(parsed,template).map{DraftRow.from(it)}))
    message=(failures+"已保留 ${owned.size} 页原图，识别 ${parsed.size} 个项目，请逐项核对。").joinToString("\n")
   }catch(e:Exception){error("报告导入未完成：${e.message}")}finally{busy=false}
  }
 }
 val gallery=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){recognize(it)}
 val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()){success->val uri=cameraUri;if(success&&uri!=null)recognize(listOf(uri));cameraUri=null}
 return ImportActions(camera={if(!busy)try{val dir=File(context.cacheDir,"capture").apply{mkdirs()};val file=File.createTempFile("report-",".jpg",dir);val uri=FileProvider.getUriForFile(context,context.packageName+".files",file);cameraUri=uri;camera.launch(uri)}catch(e:Exception){error("无法启动相机：${e.message}")}},gallery={if(!busy)gallery.launch(arrayOf("image/*"))},manual={ready(ReportDraft(rows=listOf(DraftRow())))},busy=busy,message=message)
}
