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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
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
   val c=URL(next).openConnection() as HttpURLConnection;c.instanceFollowRedirects=false;c.connectTimeout=15000;c.readTimeout=30000;c.setRequestProperty("Accept",accept);c.setRequestProperty("X-GitHub-Api-Version","2022-11-28")
   if(uri.host=="api.github.com"&&token.isNotBlank())c.setRequestProperty("Authorization","Bearer $token")
   val status=c.responseCode
   if(status in 300..399){next=URL(URL(next),c.getHeaderField("Location")?:error("更新重定向无效")).toString();c.disconnect()}else{
    if(status==401||status==403||status==404){c.disconnect();error("无法读取更新。此仓库为私有仓库，请配置有权访问 HealthTrend 的 GitHub 令牌；也可能尚未发布正式版本。")}
    if(status !in 200..299){c.disconnect();error("更新服务返回 HTTP $status")};return c
   }
  };error("更新重定向过多")
 }
 fun check(token:String):AvailableUpdate?{
  val c=connect("https://api.github.com/repos/combustible12/HealthTrend/releases/latest",token)
  val json=try{c.inputStream.bufferedReader().use{JSONObject(it.readText())}}finally{c.disconnect()}
  if(json.optBoolean("draft")||json.optBoolean("prerelease"))return null
  val version=Regex("版本代码[:： ]+(\\d+)").find(json.optString("body"))?.groupValues?.get(1)?.toLongOrNull()?:return null
  if(version<=BuildConfig.VERSION_CODE)return null
  val a=json.getJSONArray("assets");val apk=(0 until a.length()).map{a.getJSONObject(it)}.firstOrNull{it.getString("name").endsWith(".apk") }?:return null
  return AvailableUpdate(json.optString("name",json.getString("tag_name")),version,apk.getString("url"),json.optString("body"))
 }
 @Suppress("DEPRECATION") fun validate(file:File):Long{
  val pm=context.packageManager;val flags=if(Build.VERSION.SDK_INT>=28)PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
  val archive=pm.getPackageArchiveInfo(file.path,flags)?:error("下载文件不是有效 APK")
  require(archive.packageName==context.packageName){"APK 应用标识不匹配"}
  val installed=pm.getPackageInfo(context.packageName,flags)
  val next=if(Build.VERSION.SDK_INT>=28)archive.longVersionCode else archive.versionCode.toLong()
  val current=if(Build.VERSION.SDK_INT>=28)installed.longVersionCode else installed.versionCode.toLong()
  require(next>current){"下载版本没有高于当前版本"}
  val old=if(Build.VERSION.SDK_INT>=28)installed.signingInfo!!.apkContentsSigners else installed.signatures
  val fresh=if(Build.VERSION.SDK_INT>=28)archive.signingInfo!!.apkContentsSigners else archive.signatures
  require(old.size==fresh.size&&old.all{o->fresh.any{it==o}}){"新版签名与当前安装版不一致，无法保留数据直接升级。请使用相同签名重新构建。"}
  return next
 }
 fun download(update:AvailableUpdate,token:String,onProgress:(Long,Long)->Unit):File {
  val dir=File(context.cacheDir,"updates").apply{mkdirs()};val temp=File(dir,"healthtrend.tmp");val dest=File(dir,"healthtrend.apk")
  val c=connect(update.assetUrl,token,"application/octet-stream")
  try{var count=0L;c.inputStream.use{input->temp.outputStream().use{out->val buffer=ByteArray(32768);while(true){val n=input.read(buffer);if(n<0)break;out.write(buffer,0,n);count+=n;require(count<250_000_000L){"更新文件过大"};onProgress(count,c.contentLengthLong)}}};val version=validate(temp);require(version==update.version){"发布信息与 APK 版本不匹配"};check(temp.renameTo(dest));return dest}catch(e:Exception){temp.delete();throw e}finally{c.disconnect()}
 }
 fun install(file:File){validate(file);if(Build.VERSION.SDK_INT>=26&&!context.packageManager.canRequestPackageInstalls()){context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:${context.packageName}")));return};val uri=FileProvider.getUriForFile(context,context.packageName+".files",file);context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))}
}
private fun URI(s:String)=java.net.URI(s)
@Composable fun Mine(m:Modifier,templates:List<HospitalLabTemplate>,edit:(HospitalLabTemplate)->Unit,store:HealthStore,error:(String)->Unit){
 val ctx=LocalContext.current;val updater=remember{AppUpdater(ctx)};val credentials=remember{UpdateCredentials(ctx)};val scope=rememberCoroutineScope()
 var token by remember{mutableStateOf(credentials.load())};var showAuth by remember{mutableStateOf(false)};var busy by remember{mutableStateOf(false)};var status by remember{mutableStateOf("")};var available by remember{mutableStateOf<AvailableUpdate?>(null)};var downloaded by remember{mutableStateOf<File?>(null)}
 Screen(m,"我的","本地记录 · HealthTrend ${BuildConfig.VERSION_NAME}"){
  Text("医院模板",fontSize=androidx.compose.ui.unit.TextUnit.Unspecified)
  if(templates.isEmpty())Paper{Text("还没有已确认模板");Text("首次核对报告后建立；同院同类型可复用。",color=Muted)}
  templates.sortedWith(compareBy<HospitalLabTemplate>{it.hospitalKey}.thenBy{it.reportType}.thenByDescending{it.version}).forEach{t->Paper{Text(t.hospitalKey);Text("${t.reportType} · ${t.systemKey} · v${t.version}",color=Muted);Text("${t.fields.size} 个指标");TextButton({edit(t)}){Text("查看 / 主动编辑为新版")}}}
  Paper{
   Text("应用更新");Text("仅检查正式发布版本。升级保留现有记录和原图。",color=Muted)
   OutlinedButton({busy=true;status="正在检查…";scope.launch{try{available=withContext(Dispatchers.IO){updater.check(credentials.load())};status=if(available==null)"当前没有可用的正式更新"else"发现 ${available!!.name}"}catch(e:Exception){status=e.message?:"更新检查失败"}finally{busy=false}}},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("检查更新")}
   if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
   if(status.isNotBlank())Text(status,color=Muted)
   available?.let{update->Text(update.notes);Button({busy=true;scope.launch{try{val f=withContext(Dispatchers.IO){updater.download(update,credentials.load()){n,total->scope.launch{status=if(total>0)"正在下载 ${(n*100/total)}%"else"已下载 ${n/1024} KB"}}};downloaded=f;status="下载校验通过，请确认安装";updater.install(f)}catch(e:Exception){status=e.message?:"更新失败"}finally{busy=false}}},enabled=!busy){Text("下载并安装")}}
   downloaded?.let{f->TextButton({try{updater.install(f)}catch(e:Exception){error(e.message?:"安装失败")}}){Text("继续安装已下载版本")}}
   TextButton({showAuth=!showAuth}){Text("私有仓库访问设置")}
   if(showAuth){OutlinedTextField(token,{token=it},label={Text("GitHub 访问令牌")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth(),singleLine=true);Text("仅用于读取此私有仓库的正式版本，加密保存在本机。",color=Muted);TextButton({try{credentials.save(token);status="访问设置已保存";showAuth=false}catch(e:Exception){error("访问设置保存失败")}}){Text("保存设置")}}
  }
 }
}
