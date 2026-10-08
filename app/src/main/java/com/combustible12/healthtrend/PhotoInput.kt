package com.combustible12.healthtrend

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File

/** Shared gallery/camera entry. Camera writes full resolution into a temporary app-owned file. */
internal class PhotoInput(val gallery:()->Unit,val camera:()->Unit)

@Composable internal fun rememberPhotoInput(onImages:(List<Uri>)->Unit,onError:(String)->Unit):PhotoInput{
 val context=LocalContext.current
 val latestImages by rememberUpdatedState(onImages)
 val latestError by rememberUpdatedState(onError)
 var pending by rememberSaveable{mutableStateOf<String?>(null)}
 val galleryLauncher=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){uris->
  if(uris.isNotEmpty())latestImages(uris)
 }
 val cameraLauncher=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()){success->
  val path=pending
  pending=null
  if(success&&path!=null)latestImages(listOf(Uri.parse(path)))
  else if(path!=null)runCatching{File(Uri.parse(path).path.orEmpty()).delete()}
 }
 return PhotoInput(
  gallery={galleryLauncher.launch(arrayOf("image/*"))},
  camera={
   try{
    val dir=File(context.cacheDir,"capture").apply{mkdirs()}
    val file=File.createTempFile("ht-photo-",".jpg",dir)
    val uri=FileProvider.getUriForFile(context,context.packageName+".files",file)
    pending=uri.toString()
    cameraLauncher.launch(uri)
   }catch(e:Exception){pending=null;latestError("无法启动相机：${e.message}")}
  }
 )
}

@Composable internal fun PhotoInputButtons(input:PhotoInput,enabled:Boolean=true,galleryLabel:String="相册选择"){
 Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
  OutlinedButton(input.gallery,Modifier.weight(1f),enabled=enabled){Text(galleryLabel)}
  OutlinedButton(input.camera,Modifier.weight(1f),enabled=enabled){Text("直接拍照")}
 }
}
