package com.combustible12.healthtrend

import android.content.Context
import android.util.AtomicFile
import androidx.compose.runtime.saveable.Saver
import java.io.*

/** Save only a small disk reference in the activity Bundle, not whole multi-page OCR reports. */
@Suppress("UNCHECKED_CAST")
fun <T> diskStateSaver(context:Context,slot:String):Saver<T,String> = Saver(
 save={state->
  if(state==null)""else{
   val dir=File(context.filesDir,"ui-drafts").apply{mkdirs()};val file=AtomicFile(File(dir,slot))
   val bytes=ByteArrayOutputStream().also{buffer->ObjectOutputStream(buffer).use{it.writeObject(state)}}.toByteArray()
   var stream:FileOutputStream?=null
   try{stream=file.startWrite();stream.write(bytes);file.finishWrite(stream);slot}catch(e:Exception){file.failWrite(stream);throw e}
  }
 },
 restore={saved->if(saved.isBlank())null else runCatching{ObjectInputStream(File(context.filesDir,"ui-drafts/$saved").inputStream()).use{it.readObject() as T}}.getOrNull()}
)
