package com.combustible12.healthtrend

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Complete local snapshot, including all owned files and medical records.
 * Restoration is deliberately limited to an otherwise empty installation:
 * never overwrite meaningful existing patient data.
 */
class HealthBackup(private val context:Context,private val store:HealthStore) {
 data class Summary(val files:Int,val bytes:Long,val reports:Int,val templates:Int,val courseRecords:Int)
 data class Preview(val files:Int,val bytes:Long,val reports:Int,val courseRecords:Int,val createdAt:Long,val version:Int)

 private fun sha256()=MessageDigest.getInstance("SHA-256")
 private fun hex(bytes:ByteArray)=bytes.joinToString(""){"%02x".format(it)}
 private fun digest(file:File):String {
  val md=sha256()
  file.inputStream().buffered().use{input->val buf=ByteArray(32768);while(true){val n=input.read(buf);if(n<0)break;md.update(buf,0,n)}}
  return hex(md.digest())
 }
 private fun entries():List<Pair<String,File>> {
  val prefsDir=File(context.applicationInfo.dataDir,"shared_prefs")
  val prefs=prefsDir.listFiles().orEmpty().filter{
   it.isFile&&it.name.startsWith("healthtrend_")&&it.name.endsWith(".xml")&&it.name!="healthtrend_update_auth.xml"
  }
  val root=context.filesDir.canonicalFile
  val files=root.walkTopDown().filter{it.isFile}.toList()
  return (prefs.map{"shared_prefs/${it.name}" to it}+
   files.map{"files/${it.relativeTo(root).invariantSeparatorsPath}" to it}).sortedBy{it.first}
 }
 fun export(destination:Uri):Summary {
  val selected=entries()
  require(selected.any{it.first=="shared_prefs/healthtrend_store_v1.xml"}){"未找到主数据文件，已取消备份"}
  val files=JSONArray()
  var total=0L
  selected.forEach{(name,file)->files.put(JSONObject().put("path",name).put("size",file.length()).put("sha256",digest(file)));total+=file.length()}
  val manifest=JSONObject().put("format","HealthTrendBackup").put("version",2)
   .put("createdAt",System.currentTimeMillis()).put("filesRoot",context.filesDir.canonicalPath)
   .put("reports",store.reports().size).put("templates",store.templates().size)
   .put("courseRecords",store.courseRecords().size).put("files",files)
  context.contentResolver.openOutputStream(destination,"w").use{out->
   requireNotNull(out){"无法写入备份文件"}
   ZipOutputStream(out.buffered()).use{zip->
    zip.putNextEntry(ZipEntry("manifest.json"));zip.write(manifest.toString().toByteArray(Charsets.UTF_8));zip.closeEntry()
    selected.forEach{(name,file)->zip.putNextEntry(ZipEntry(name));file.inputStream().buffered().use{it.copyTo(zip)};zip.closeEntry()}
   }
  }
  // Reopen and verify every byte before claiming success.
  val preview=inspect(destination)
  check(preview.files==selected.size&&preview.bytes==total){"备份校验失败"}
  return Summary(selected.size,total,store.reports().size,store.templates().size,store.courseRecords().size)
 }

 private data class Staged(val folder:File,val manifest:JSONObject,val entries:List<Pair<String,File>>,val bytes:Long)
 private fun legalPath(name:String):Boolean =
  name.startsWith("files/")||name.startsWith("shared_prefs/")
 private fun safeTarget(name:String):File {
  require(legalPath(name)&&!name.startsWith("/")&&!name.split('/').any{it==".."||it=="."||it.isEmpty()}){"备份文件路径无效"}
  val root=if(name.startsWith("files/"))context.filesDir.canonicalFile
   else File(context.applicationInfo.dataDir,"shared_prefs").canonicalFile
  val child=name.substringAfter('/')
  val target=File(root,child).canonicalFile
  require(target.path.startsWith(root.path+File.separator)){"备份文件路径越界"}
  require(!name.startsWith("shared_prefs/")||child.startsWith("healthtrend_")&&child.endsWith(".xml")&&child!="healthtrend_update_auth.xml"){"包含不允许恢复的设置"}
  return target
 }
 private fun stage(uri:Uri):Staged {
  val directory=File(context.cacheDir,"restore-check-${System.nanoTime()}").apply{mkdirs()}
  try{
   var manifest:JSONObject?=null
   val output=mutableListOf<Pair<String,File>>()
   var bytes=0L
   val names=mutableSetOf<String>()
   context.contentResolver.openInputStream(uri).use{stream->
    requireNotNull(stream){"无法读取备份文件"}
    ZipInputStream(stream.buffered()).use{zip->
     while(true){
      val entry=zip.nextEntry?:break
      require(!entry.isDirectory&&names.add(entry.name)){"备份包含重复项目或目录"}
      require(names.size<=15000){"备份文件数量异常"}
      if(entry.name=="manifest.json"){
       val content=zip.readBytes()
       require(content.size<=2_000_000){"备份清单过大"}
       manifest=JSONObject(String(content,Charsets.UTF_8))
      }else{
       safeTarget(entry.name)
       val out=File(directory,"entry-${output.size}")
       out.outputStream().buffered().use{f->
        val buffer=ByteArray(32768)
        while(true){val n=zip.read(buffer);if(n<0)break;bytes+=n;require(bytes<=4_000_000_000L){"备份超出安全大小限制"};f.write(buffer,0,n)}
       }
       output+=entry.name to out
      }
      zip.closeEntry()
     }
    }
   }
   val m=requireNotNull(manifest){"缺少备份清单"}
   require(m.getString("format")=="HealthTrendBackup"&&m.optInt("version")==2){"备份版本不兼容，请使用新版导出"}
   val declared=m.getJSONArray("files")
   require(declared.length()==output.size){"备份缺少文件"}
   val actual=output.toMap()
   val declaredNames=mutableSetOf<String>()
   for(i in 0 until declared.length()){
    val row=declared.getJSONObject(i);val name=row.getString("path")
    require(declaredNames.add(name)){"备份清单存在重复文件名"}
    val file=actual[name]?:error("备份缺少文件：$name")
    require(file.length()==row.getLong("size")&&digest(file)==row.getString("sha256")){"备份文件校验失败：$name"}
   }
   require(declaredNames==actual.keys){"备份清单和实际文件不匹配"}
   require(actual.containsKey("shared_prefs/healthtrend_store_v1.xml")){"缺少健康记录数据库"}
   return Staged(directory,m,output,bytes)
  }catch(e:Exception){directory.deleteRecursively();throw e}
 }
 fun inspect(uri:Uri):Preview {
  val s=stage(uri)
  try{return Preview(s.entries.size,s.bytes,s.manifest.optInt("reports"),
   s.manifest.optInt("courseRecords"),s.manifest.optLong("createdAt"),s.manifest.optInt("version"))}
  finally{s.folder.deleteRecursively()}
 }
 /**
  * Restore only into an installation with no meaningful existing records.
  * Returns after committing files; UI must request an app process restart,
  * since Android caches SharedPreferences in memory.
  */
 @Synchronized fun restoreIntoEmpty(uri:Uri):Preview {
  val existing=store.reports().size+store.entries().size+store.courseRecords().count{
   it.title.isNotBlank()||it.symptomText.isNotBlank()||it.checkText.isNotBlank()||
    it.medicineText.isNotBlank()||it.noteText.isNotBlank()||
    it.symptomImages.isNotEmpty()||it.checkImages.isNotEmpty()||
    it.medicineImages.isNotEmpty()||it.noteImages.isNotEmpty()
  }
  val patient=store.patientProfile()
  val hasProfile=patient.name.isNotBlank()||patient.birthDate.isNotBlank()||patient.note.isNotBlank()||patient.sex.isNotBlank()||patient.bloodType.isNotBlank()
  val hasOwnedImages=listOf(File(context.filesDir,"sources"),File(context.filesDir,"image-documents/images")).any{dir->dir.walkTopDown().any{it.isFile}}
  require(existing==0&&!hasProfile&&!hasOwnedImages&&ImageDocumentStore(context).all().isEmpty()){"当前设备已有报告或病程资料。为防止覆盖，禁止直接恢复；请在空白安装环境中恢复"}
  val staged=stage(uri)
  try{
   val oldRoot=staged.manifest.optString("filesRoot")
   require(oldRoot.isNotBlank()){"备份缺少原始文件位置"}
   val currentRoot=context.filesDir.canonicalPath
   val restoreSnapshot=File(context.cacheDir,"restore-rollback-${System.nanoTime()}").apply{mkdirs()}
   val saved=mutableListOf<Pair<File,File?>>()
   try{
    staged.entries.forEachIndexed{index,(name,source)->
     val dest=safeTarget(name)
     dest.parentFile?.mkdirs()
     val previous=if(dest.exists())File(restoreSnapshot,"old-$index").also{dest.copyTo(it,true)} else null
     saved+=dest to previous
     val tmp=File(dest.parentFile,dest.name+".restore-tmp")
     try{
      if(name.endsWith(".xml")||name=="files/image-documents/index.json"){
       val text=source.readText()
       tmp.writeText(text.replace(oldRoot,currentRoot))
      }else source.copyTo(tmp,true)
      // Keep the original until the replacement has been copied.
      check(tmp.renameTo(dest)||run{tmp.copyTo(dest,true);tmp.delete();true}){"恢复写入失败：$name"}
     }finally{tmp.delete()}
    }
   }catch(e:Exception){
    saved.asReversed().forEach{(dest,old)->runCatching{if(old!=null)old.copyTo(dest,true)else dest.delete()}}
    throw e
   }finally{restoreSnapshot.deleteRecursively()}
   return Preview(staged.entries.size,staged.bytes,staged.manifest.optInt("reports"),
    staged.manifest.optInt("courseRecords"),staged.manifest.optLong("createdAt"),2)
  }finally{staged.folder.deleteRecursively()}
 }
}
