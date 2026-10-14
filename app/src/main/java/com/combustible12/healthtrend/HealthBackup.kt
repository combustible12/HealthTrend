package com.combustible12.healthtrend

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Export-only, read-only snapshot. Never touches existing records or templates.
 * The user owns the resulting ZIP via Android's document picker.
 */
class HealthBackup(private val context: Context, private val store: HealthStore) {
 data class Summary(val files:Int,val bytes:Long,val reports:Int,val templates:Int)

 fun export(destination:Uri):Summary {
  val prefsDir=File(context.applicationInfo.dataDir,"shared_prefs")
  val prefs=prefsDir.listFiles().orEmpty().filter {
   it.isFile && it.name.startsWith("healthtrend_") &&
    it.name.endsWith(".xml") && it.name!="healthtrend_update_auth.xml"
  }
  val root=context.filesDir.canonicalFile
  val files=root.walkTopDown().filter{it.isFile}.toList()
  val entries=(prefs.map{"shared_prefs/${it.name}" to it}+
   files.map{"files/${it.relativeTo(root).invariantSeparatorsPath}" to it})
   .sortedBy{it.first}
  require(entries.any{it.first=="shared_prefs/healthtrend_store_v1.xml"}){
   "未找到主数据文件，已取消备份"
  }
  val reportCount=store.reports().size
  val templateCount=store.templates().size
  var bytes=0L
  context.contentResolver.openOutputStream(destination,"w").use{output->
   requireNotNull(output){"无法写入备份文件"}
   ZipOutputStream(output.buffered()).use{zip->
    val manifest=JSONObject()
     .put("format","HealthTrendBackup")
     .put("version",1)
     .put("createdAt",System.currentTimeMillis())
     .put("reports",reportCount)
     .put("templates",templateCount)
     .put("files",entries.size)
     .put("note","包含本机应用文件及非凭据配置；请妥善保管，勿公开分享")
    zip.putNextEntry(ZipEntry("manifest.json"))
    zip.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
    zip.closeEntry()
    entries.forEach{(name,file)->
     zip.putNextEntry(ZipEntry(name))
     file.inputStream().buffered().use{input->bytes+=input.copyTo(zip)}
     zip.closeEntry()
    }
   }
  }
  return Summary(entries.size,bytes,reportCount,templateCount)
 }
}
