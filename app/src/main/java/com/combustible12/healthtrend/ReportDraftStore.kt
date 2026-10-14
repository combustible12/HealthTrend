package com.combustible12.healthtrend

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

/** Separate, durable draft snapshots. Never edits confirmed reports or course records. */
class ReportDraftStore(context:Context) {
 private val folder=File(context.filesDir,"report-drafts").apply{mkdirs()}
 private fun file(id:String):File {
  require(id.matches(Regex("[A-Za-z0-9_-]{1,100}"))){"草稿身份不合法"}
  return File(folder,"$id.draft")
 }
 @Synchronized fun save(draft:ReportDraft){
  val atomic=AtomicFile(file(draft.draftId))
  val stream=atomic.startWrite()
  try{
   ObjectOutputStream(stream).use{it.writeObject(draft);it.flush()}
   atomic.finishWrite(stream)
  }catch(e:Exception){atomic.failWrite(stream);throw e}
 }
 @Synchronized fun all():List<ReportDraft> =
  folder.listFiles().orEmpty().filter{it.isFile&&it.name.endsWith(".draft")}
   .sortedByDescending{it.lastModified()}.mapNotNull{f->
    runCatching{ObjectInputStream(f.inputStream().buffered()).use{it.readObject() as ReportDraft}}
     .getOrNull()
   }
 @Synchronized fun delete(id:String) {
  val target=file(id)
  if(target.exists())check(target.delete()){"无法删除草稿"}
 }
}
