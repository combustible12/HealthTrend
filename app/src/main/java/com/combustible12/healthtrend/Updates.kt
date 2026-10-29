package com.combustible12.healthtrend

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class UpdateCredentials(context:Context){
 private val prefs=context.getSharedPreferences("healthtrend_update_auth",Context.MODE_PRIVATE)
 private fun key():SecretKey{val ks=KeyStore.getInstance("AndroidKeyStore").apply{load(null)};val alias="healthtrend_update_auth";return (ks.getKey(alias,null) as? SecretKey)?:KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply{init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())}.generateKey()}
 fun save(token:String){if(token.isBlank()){prefs.edit().clear().commit();return};val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());check(prefs.edit().putString("iv",Base64.encodeToString(cipher.iv,Base64.NO_WRAP)).putString("value",Base64.encodeToString(cipher.doFinal(token.trim().toByteArray()),Base64.NO_WRAP)).commit())}
 fun load():String=runCatching{val value=prefs.getString("value",null)?:return "";val iv=Base64.decode(prefs.getString("iv",""),Base64.NO_WRAP);val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,iv));String(cipher.doFinal(Base64.decode(value,Base64.NO_WRAP)))}.getOrDefault("")
}
data class AvailableUpdate(val name:String,val version:Long,val assetUrl:String,val notes:String)
class AppUpdater(private val context:Context){
 private fun connect(url:String,token:String,accept:String="application/vnd.github+json"):HttpURLConnection {
  var next=url
  repeat(6){
   val uri=URI(next);require(uri.scheme=="https"&&uri.host in setOf("api.github.com","github.com","objects.githubusercontent.com","release-assets.githubusercontent.com")){"更新地址不受信任"}
   val c=URL(next).openConnection() as HttpURLConnection;c.instanceFollowRedirects=false;c.useCaches=false;c.setRequestProperty("Cache-Control","no-cache, no-store");c.setRequestProperty("Pragma","no-cache");c.connectTimeout=15000;c.readTimeout=30000;c.setRequestProperty("Accept",accept);c.setRequestProperty("X-GitHub-Api-Version","2022-11-28")
   if(uri.host=="api.github.com"&&token.isNotBlank())c.setRequestProperty("Authorization","Bearer $token")
   val status=c.responseCode
   if(status in 300..399){next=URL(URL(next),c.getHeaderField("Location")?:error("更新重定向无效")).toString();c.disconnect()}else{
    if(status !in 200..299){val limited=c.getHeaderField("X-RateLimit-Remaining")=="0";c.disconnect();throw UpdateHttpFailure(status,limited)};return c
   }
  };error("更新重定向过多")
 }
 fun check(token:String):AvailableUpdate?{
  val update=UpdateLookup.latest(token){url,auth->
   val c=connect(url,auth,if(java.net.URI(url).host=="api.github.com")"application/vnd.github+json" else "text/html")
   try{UpdateResponse(c.inputStream.bufferedReader().use{it.readText()},c.url.toString())}finally{c.disconnect()}
  }
  return update.takeIf{UpdateVersionPolicy.isNewer(it.version,installedVersion())}
 }
 @Suppress("DEPRECATION") private fun installedVersion():Long {
  val installed=context.packageManager.getPackageInfo(context.packageName,0)
  return if(Build.VERSION.SDK_INT>=28)installed.longVersionCode else installed.versionCode.toLong()
 }
 @Suppress("DEPRECATION") fun validate(file:File):Long{
  val pm=context.packageManager;val flags=if(Build.VERSION.SDK_INT>=28)PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
  val archive=pm.getPackageArchiveInfo(file.path,flags)?:error("下载文件不是有效 APK")
  require(archive.packageName==context.packageName){"APK 应用标识不匹配"}
  val installed=pm.getPackageInfo(context.packageName,flags)
  val next=if(Build.VERSION.SDK_INT>=28)archive.longVersionCode else archive.versionCode.toLong()
  val current=if(Build.VERSION.SDK_INT>=28)installed.longVersionCode else installed.versionCode.toLong()
  require(UpdateVersionPolicy.isNewer(next,current)){"下载版本没有高于当前版本"}
  val old=(if(Build.VERSION.SDK_INT>=28)installed.signingInfo?.apkContentsSigners else installed.signatures)?:error("无法读取已安装版本签名")
  val fresh=(if(Build.VERSION.SDK_INT>=28)archive.signingInfo?.apkContentsSigners else archive.signatures)?:error("无法读取新版签名")
  require(old.size==fresh.size&&old.all{o->fresh.any{it==o}}){"新版签名与当前安装版不一致，无法保留数据直接升级。请使用相同签名重新构建。"}
  return next
 }
 fun download(update:AvailableUpdate,token:String,onProgress:(Long,Long)->Unit):File {
  val dir=File(context.cacheDir,"updates").apply{mkdirs()};val temp=File(dir,"healthtrend.pending.apk");val dest=File(dir,"healthtrend.apk")
  val c=connect(update.assetUrl,token,"application/octet-stream")
  try{var count=0L;var lastProgress=0L;c.inputStream.use{input->temp.outputStream().use{out->val buffer=ByteArray(32768);while(true){val n=input.read(buffer);if(n<0)break;out.write(buffer,0,n);count+=n;require(count<250_000_000L){"更新文件过大"};val now=System.currentTimeMillis();if(now-lastProgress>200){onProgress(count,c.contentLengthLong);lastProgress=now}}}};val version=validate(temp);require(version==update.version){"发布信息与 APK 版本不匹配"};check(temp.renameTo(dest));return dest}catch(e:Exception){temp.delete();throw e}finally{c.disconnect()}
 }
 fun install(file:File){validate(file);if(Build.VERSION.SDK_INT>=26&&!context.packageManager.canRequestPackageInstalls()){context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return};val uri=FileProvider.getUriForFile(context,context.packageName+".files",file);context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))}
 fun resumableDownloadedApk(expectedVersion:Long?=null):File? {
  val file=File(context.cacheDir,"updates/healthtrend.apk")
  if(!file.isFile)return null
  val version=runCatching{validate(file)}.getOrNull()
  if(version==null || !UpdateVersionPolicy.canReuse(version,installedVersion(),expectedVersion)){
   file.delete();return null
  }
  return file
 }

}
private fun URI(s:String)=java.net.URI(s)
@Composable fun Mine(m:Modifier,store:HealthStore,error:(String)->Unit,changed:()->Unit={},records:()->Unit={}){
 val ctx=LocalContext.current;var patient by remember{mutableStateOf(store.patientProfile())};var editPatient by remember{mutableStateOf(false)};val scope=rememberCoroutineScope()
 var archiveRevision by remember{mutableIntStateOf(0)}
 var archiveOpen by rememberSaveable{mutableStateOf(false)}
 var restoreEvent by remember{mutableStateOf<HealthStore.ReportReplacement?>(null)}
 if(restoreEvent!=null)AlertDialog(onDismissRequest={restoreEvent=null},title={Text("恢复封存的旧报告？")},
  text={Text("恢复后，当前重新导入的报告将退出活动列表，封存的原旧报告重新进入报告列表和趋势。")},
  confirmButton={TextButton({try{store.restoreArchivedReport(restoreEvent!!.id);archiveRevision++;changed();restoreEvent=null}catch(e:Exception){error(e.message?:"恢复失败")}}){Text("确认恢复")}},
  dismissButton={TextButton({restoreEvent=null}){Text("取消")}})
 var backupBusy by remember{mutableStateOf(false)}
 var backupStatus by remember{mutableStateOf("")}
 val backupLauncher=rememberLauncherForActivityResult(
  ActivityResultContracts.CreateDocument("application/zip")
 ){uri->
  if(uri!=null){
   backupBusy=true;backupStatus="正在备份报告、模板和原始图片…"
   scope.launch {
    try{
     val summary=withContext(Dispatchers.IO){HealthBackup(ctx,store).export(uri)}
     backupStatus="备份完成：${summary.reports} 份报告、${summary.courseRecords} 条病程、${summary.files} 个文件。请妥善保存 ZIP。"
    }catch(e:Exception){backupStatus="备份失败：${e.message ?: "无法写入文件"}，请删除不完整的 ZIP 后重试"}
    finally{backupBusy=false}
   }
  }
 }
 var restorePreview by remember{mutableStateOf<HealthBackup.Preview?>(null)}
 var restoreUri by remember{mutableStateOf<Uri?>(null)}
 var restoreConfirm by remember{mutableStateOf(false)}
 val restoreLauncher=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
  if(uri!=null){
   backupBusy=true;backupStatus="正在核验备份文件…"
   scope.launch{
    try{
     val preview=withContext(Dispatchers.IO){HealthBackup(ctx,store).inspect(uri)}
     restoreUri=uri;restorePreview=preview;restoreConfirm=true
     backupStatus="备份核验成功：${preview.reports} 份报告、${preview.courseRecords} 条病程、${preview.files} 个文件。"
    }catch(e:Exception){restoreUri=null;restorePreview=null;backupStatus="备份校验失败：${e.message}"}
    finally{backupBusy=false}
   }
  }
 }
 if(restoreConfirm&&restorePreview!=null){
  AlertDialog(onDismissRequest={restoreConfirm=false},title={Text("恢复完整备份？")},
   text={Text("已验证备份中有 ${restorePreview!!.reports} 份报告、${restorePreview!!.courseRecords} 条病程。只允许在没有现有医疗记录的安装环境中恢复；不会与当前记录合并。恢复成功后必须关闭并重新打开 App 才能读取数据。")},
   confirmButton={TextButton({
    val uri=restoreUri
    if(uri!=null){
     restoreConfirm=false;backupBusy=true;backupStatus="正在恢复备份，请勿关闭应用…"
     scope.launch{
      try{
       val restored=withContext(Dispatchers.IO){HealthBackup(ctx,store).restoreIntoEmpty(uri)}
       backupStatus="恢复完成：${restored.reports} 份报告。请完全关闭并重新打开 App，重新打开前不要编辑任何数据。"
      }catch(e:Exception){backupStatus="恢复未完成：${e.message}"}
      finally{backupBusy=false}
     }
    }
   },enabled=!backupBusy){Text("确认安全恢复")}},
   dismissButton={TextButton({restoreConfirm=false}){Text("取消")}})
 }
 if(archiveOpen){ArchivedReports(store,archiveRevision,{archiveOpen=false},{restoreEvent=it});return}
 Screen(m,"我的","本地记录 · HealthTrend ${BuildConfig.VERSION_NAME}"){
  Paper{if(editPatient){
   @Composable fun PatientField(label:String,value:String,onChange:(String)->Unit,readOnly:Boolean=false){AppInputField(value,onChange,label=label,readOnly=readOnly,singleLine=label!="备注",minLines=if(label=="备注")2 else 1,containerColor=Warm)}
   PatientField("姓名",patient.name,{patient=patient.copy(name=it)})
   PatientField("出生日期（YYYY-MM-DD）",patient.birthDate,{patient=patient.copy(birthDate=it)})
   val age=runCatching{java.time.Period.between(java.time.LocalDate.parse(patient.birthDate),java.time.LocalDate.now()).years.toString()}.getOrDefault("")
   PatientField("年龄",age,{},true)
   PatientField("性别",patient.sex,{patient=patient.copy(sex=it)})
   PatientField("身高 cm",patient.heightCm,{patient=patient.copy(heightCm=sanitizeProfileHeight(it))})
   PatientField("血型（ABO / Rh，例如 A型 Rh阳性）",patient.bloodType,{patient=patient.copy(bloodType=it)})
   PatientField("备注",patient.note,{patient=patient.copy(note=it)})
   Row{TextButton({patient=store.patientProfile();editPatient=false}){Text("取消")};Button({try{store.savePatientProfile(patient);patient=store.patientProfile();editPatient=false}catch(e:Exception){error(e.message?:"患者资料保存失败")}}){Text("保存患者资料",color=Color.White)}}
  }else{Text(patient.name.ifBlank{"未填写姓名"},fontWeight=FontWeight.Bold);val age=runCatching{java.time.Period.between(java.time.LocalDate.parse(patient.birthDate),java.time.LocalDate.now()).years}.getOrNull();Text(listOfNotNull(patient.sex.takeIf{it.isNotBlank()},patient.birthDate.takeIf{it.isNotBlank()},age?.let{"${it}岁"},patient.heightCm.takeIf{it.isNotBlank()}?.let{"身高 ${it} cm"},patient.bloodType.takeIf{it.isNotBlank()}).joinToString(" · ").ifBlank{"可填写姓名、出生日期、性别和身高"},color=Muted);if(patient.note.isNotBlank())Text(patient.note,color=Muted);TextButton({editPatient=true}){Text("编辑患者资料")}}}
  BloodGroupCard(store,error,changed)
  Card(onClick=records,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){
   Row(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=14.dp),horizontalArrangement=Arrangement.SpaceBetween){Text("记录",fontWeight=FontWeight.SemiBold);Text("查看报告 ›",color=Muted)}
  }
  Card(modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){
   Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
    Text("数据备份",fontWeight=FontWeight.SemiBold)
    Text("备份全部本地健康数据、图片资料、报告原图和设置为 ZIP，并校验每个文件。备份包含个人医疗信息，请保存在可信位置。",color=Muted,fontSize=13.sp)
    Button({backupLauncher.launch("HealthTrend_备份_${java.time.LocalDate.now()}.zip")},enabled=!backupBusy,modifier=Modifier.fillMaxWidth()){
     Text(if(backupBusy)"正在备份…" else "备份到文件")
    }
    if(backupBusy)LinearProgressIndicator(Modifier.fillMaxWidth())
    Button({restoreLauncher.launch(arrayOf("application/zip","application/octet-stream"))},enabled=!backupBusy,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Color.White,contentColor=Accent)){Text("选择备份 ZIP 并验证 / 恢复")}
    Text("恢复仅限空白安装，不覆盖已有报告或病程；请把 ZIP 再复制一份到其他安全位置。",color=Muted,fontSize=12.sp)
    if(backupStatus.isNotBlank())Text(backupStatus,color=Muted,fontSize=12.sp)
   }
  }
  Card(onClick={archiveOpen=true},modifier=Modifier.fillMaxWidth().testTag("archived-reports-entry"),shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){
   Row(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=14.dp),horizontalArrangement=Arrangement.SpaceBetween){Text("封存的旧报告",fontWeight=FontWeight.SemiBold);Text("查看报告 ›",color=Muted)}
  }
  AppUpdateButton(error)
 }

}

private fun sanitizeProfileHeight(input:String):String{
 val filtered=input.replace(',','.').replace('，','.').filter{it.isDigit()||it=='.'}
 val dot=filtered.indexOf('.')
 return if(dot<0)filtered.take(3) else filtered.take(dot).take(3)+"."+filtered.drop(dot+1).filter(Char::isDigit).take(1)
}

/** Browse immutable replacement snapshots, never resolve them through the active report ID. */
@Composable internal fun ArchivedReports(store:HealthStore,revision:Int,close:()->Unit,restore:(HealthStore.ReportReplacement)->Unit){
 val archived=remember(revision){store.archivedReportReplacements().filter{it.restoredAt==null}}
 var selectedId by rememberSaveable{mutableStateOf<String?>(null)}
 var viewer by remember{mutableStateOf<List<String>?>(null)}
 val selected=archived.firstOrNull{it.id==selectedId}
 if(selected!=null){
  CompositionLocalProvider(LocalPageVisible provides (viewer==null)){
   ReportDetail(selected.oldReport,store,{selectedId=null},{},{viewer=it},{},{_,_,_->},{},readOnly=true)
  }
  viewer?.let{uris->SourceViewer(uris,{viewer=null})}
 }else{
  FullPage("封存的旧报告",close){m->LazyColumn(m.padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
   if(archived.isEmpty())item{Text("暂无封存的旧报告",color=Muted)}
   items(archived,key={it.id}){event->
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)){
     ReportCard(event.oldReport,{selectedId=event.id})
     TextButton({restore(event)},modifier=Modifier.align(Alignment.End)){Text("恢复旧报告")}
    }
   }
  }}
 }
}

@Composable fun AppUpdateButton(error:(String)->Unit){
 val ctx=LocalContext.current
 val updater=remember{AppUpdater(ctx)}
 val credentials=remember{UpdateCredentials(ctx)}
 val scope=rememberCoroutineScope()
 var busy by remember{mutableStateOf(false)}
 var status by remember{mutableStateOf("")}
 var token by remember{mutableStateOf(credentials.load())}
 var showAuth by remember{mutableStateOf(false)}
 Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)){
   if(busy)LinearProgressIndicator(Modifier.fillMaxWidth(),color=Ink)
   if(status.isNotBlank())Text(status,color=Muted,fontSize=13.sp)
   if(token.isNotBlank()||status.contains("令牌")||showAuth){
    TextButton({showAuth=!showAuth}){Text("私有仓库访问设置",color=Muted)}
   }
   if(showAuth){
    AppInputField(token,{token=it},label="GitHub 访问令牌",visualTransformation=PasswordVisualTransformation(),singleLine=true)
    TextButton({try{credentials.save(token);status="访问设置已保存";showAuth=false}catch(e:Exception){error("访问设置保存失败")}}){Text("保存设置")}
   }
   Button({
    busy=true;status="正在检查更新…"
    scope.launch{
     try{
      val update=withContext(Dispatchers.IO){updater.check(credentials.load())}
      if(update==null)status="当前已经是最新正式版本"
      else{
       val cached=withContext(Dispatchers.IO){updater.resumableDownloadedApk(update.version)}
       val file=if(cached!=null)cached else{
        status="发现 ${update.name}，正在下载…"
        withContext(Dispatchers.IO){updater.download(update,credentials.load()){n,total->
         scope.launch{status=if(total>0)"正在下载 ${(n*100/total)}%"else"已下载 ${n/1024} KB"}
        }}
       }
       status="下载校验通过，请在系统界面确认安装"
       updater.install(file)
      }
     }catch(e:Exception){status=e.message?:"更新失败";error(status)}finally{busy=false}
    }
   },enabled=!busy,modifier=Modifier.fillMaxWidth().height(40.dp),shape=RoundedCornerShape(30.dp),colors=ButtonDefaults.buttonColors(containerColor=Ink,contentColor=Color.White)){
    Icon(Icons.Outlined.SystemUpdateAlt,null);Spacer(Modifier.width(8.dp));Text(if(busy)"正在处理…" else "一键更新并升级",fontSize=17.sp,fontWeight=FontWeight.SemiBold)
   }
 }
}


@Composable private fun BloodGroupCard(store:HealthStore,error:(String)->Unit,changed:()->Unit) {
 var saved by remember{mutableStateOf(store.bloodGroupProfile())}
 var draft by remember{mutableStateOf(saved)}
 var editing by remember{mutableStateOf(false)}
 Paper {
  Text("血型四项",fontWeight=FontWeight.Bold)
  if(editing){
   @Composable fun Choice(label:String,value:String,options:List<String>,onSelect:(String)->Unit){
    Text(label,color=Muted,fontSize=13.sp)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)){
     options.forEach{option->FilterChip(selected=value==option,onClick={onSelect(option)},border=null,colors=FilterChipDefaults.filterChipColors(containerColor=Color.White,selectedContainerColor=if(label=="ABO")SelectedTint else Warm),label={Text(option.ifEmpty{"未填写"},fontWeight=if(value==option)FontWeight.SemiBold else FontWeight.Normal)})}
    }
   }
   AppInputField(value=draft.testedDate,onValueChange={draft=draft.copy(testedDate=it)},placeholder={Text("检测日期 YYYY-MM-DD")},singleLine=true,containerColor=Warm)
   Choice("ABO",draft.abo,listOf("","A","B","AB","O")){draft=draft.copy(abo=it)}
   Choice("Rh(D)",draft.rhD,listOf("","阳性","阴性")){draft=draft.copy(rhD=it)}
   Choice("Rh(C)",draft.rhC,listOf("","阳性","阴性")){draft=draft.copy(rhC=it)}
   Choice("Rh(E)",draft.rhE,listOf("","阳性","阴性")){draft=draft.copy(rhE=it)}
   Row {
    TextButton({draft=saved;editing=false}){Text("取消")}
    Button({try{store.saveBloodGroupProfile(draft);saved=store.bloodGroupProfile();editing=false;changed()}catch(e:Exception){error(e.message?:"血型保存失败")}}){Text("保存血型",color=Color.White)}
   }
  }else{
   BloodGroupSummary(saved)
   TextButton({draft=saved;editing=true}){Text("编辑血型")}
  }
 }
}


/** Two rows of paired labels/results; Rh fill shares the actual page-background token. */
@Composable fun BloodGroupSummary(profile:BloodGroupProfile) {
 Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){
  Text("检测日期",color=Muted,fontSize=13.sp)
  Text(profile.testedDate.ifBlank{"未填写"},color=Muted,fontSize=13.sp)
 }
 @Composable fun Item(label:String,value:String,abo:Boolean,modifier:Modifier){
  Row(modifier.testTag("blood-group-$label"),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){
   Text(label,color=Muted,fontSize=14.sp,maxLines=1)
   Box(Modifier.background(if(abo)SelectedTint else Warm,RoundedCornerShape(50)).padding(horizontal=12.dp,vertical=8.dp),contentAlignment=Alignment.Center){
    Text(value.ifBlank{"未填写"},color=Ink,fontSize=14.sp,fontWeight=FontWeight.SemiBold,maxLines=1)
   }
  }
 }
 Column(Modifier.fillMaxWidth().padding(vertical=4.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
   Item("ABO",profile.abo.takeIf{it.isNotBlank()}?.let{"${it}型"}.orEmpty(),true,Modifier.weight(1f))
   Item("Rh(D)",profile.rhD,false,Modifier.weight(1f))
  }
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
   Item("Rh(C)",profile.rhC,false,Modifier.weight(1f))
   Item("Rh(E)",profile.rhE,false,Modifier.weight(1f))
  }
 }
}
