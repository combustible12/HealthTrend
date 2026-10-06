package com.combustible12.healthtrend

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable fun HistoryReplacementImport(store:HealthStore,changed:()->Unit,error:(String)->Unit){
 val context=LocalContext.current;val scope=rememberCoroutineScope()
 var file by remember{mutableStateOf<String?>(null)}
 var summary by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)}
 var status by remember{mutableStateOf("")}
 val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
  if(uri!=null){busy=true;scope.launch{
   try{
    val text=withContext(Dispatchers.IO){context.contentResolver.openInputStream(uri).use{input->
     requireNotNull(input){"核对文件无法读取"}
     val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
     while(true){val n=input.read(buffer);if(n<0)break;require(output.size()+n<=1024*1024){"核对文件过大"};output.write(buffer,0,n)}
     output.toString("UTF-8")
    }}
    summary=withContext(Dispatchers.IO){store.previewHistoryReplacement(text)};file=text
   }catch(e:Exception){error(e.message?:"核对文件读取失败")}finally{busy=false}
  }}
 }
 Paper{
  Text("历史报告核对")
  OutlinedButton({picker.launch(arrayOf("application/json","text/plain","application/octet-stream"))},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("导入核对文件")}
  if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
  if(status.isNotBlank())Text(status,color=Muted)
 }
 if(file!=null)AlertDialog(onDismissRequest={if(!busy)file=null},title={Text("替换已核对的报告？")},text={Text("$summary\n\n保留日期、原图和其他记录。替换前自动保存备份。",modifier=Modifier.verticalScroll(rememberScrollState()))},confirmButton={
  TextButton({val text=file?:return@TextButton;busy=true;scope.launch{
   try{val result=withContext(Dispatchers.IO){store.importHistoryReplacement(text)};file=null;status=result;changed()}
   catch(e:Exception){error(e.message?:"报告替换失败")}
   finally{busy=false}
  }},enabled=!busy){Text("确认替换")}
 },dismissButton={TextButton({file=null},enabled=!busy){Text("取消")}})
}
