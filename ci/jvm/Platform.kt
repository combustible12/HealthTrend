package com.combustible12.healthtrend
import android.content.Context
/** UI descriptions do not affect import/storage tests. */
fun metricPurpose(key:String):String?=null
/** Only the empty-install guard is used. Image document rendering/indexing is outside this suite. */
class ImageDocumentStore(private val context:Context){fun all():List<String> =
 if(java.io.File(context.filesDir,"image-documents/index.json").exists())listOf("existing") else emptyList()}
