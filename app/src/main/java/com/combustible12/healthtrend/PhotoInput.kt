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
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

/** Camera captures are published in Pictures/HealthTrend on Android 10+. */
internal fun createCameraDestination(context:Context):Uri {
 if(Build.VERSION.SDK_INT>=29){
  val values=ContentValues().apply{
   put(MediaStore.Images.Media.DISPLAY_NAME,"HealthTrend-"+System.currentTimeMillis()+".jpg")
   put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg")
   put(MediaStore.Images.Media.RELATIVE_PATH,Environment.DIRECTORY_PICTURES+"/HealthTrend")
  }
  return requireNotNull(context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)){"无法创建公共相册照片"}
 }
 // Older Android versions keep the original private capture behavior.
 val dir=File(context.cacheDir,"capture").apply{mkdirs()}
 val file=File.createTempFile("ht-photo-",".jpg",dir)
 return FileProvider.getUriForFile(context,context.packageName+".files",file)
}
internal fun discardCameraDestination(context:Context,uri:Uri){
 if(uri.scheme=="content" && uri.authority=="media")context.contentResolver.delete(uri,null,null)
 else if(uri.scheme=="file")File(uri.path.orEmpty()).delete()
}

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
  else if(path!=null)runCatching{discardCameraDestination(context,Uri.parse(path))}
 }
 return PhotoInput(
  gallery={galleryLauncher.launch(arrayOf("image/*"))},
  camera={
   try{
    val uri=createCameraDestination(context)
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
