package com.combustible12.healthtrend
import androidx.compose.foundation.gestures.scrollBy

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.util.AtomicFile
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

internal data class ImageViewportTransform(
 val fit:Float,val imageLeft:Float,val imageTop:Float,val zoom:Float,val translationX:Float,val translationY:Float
)

internal data class FloatBox(val left:Float,val top:Float,val right:Float,val bottom:Float)

internal fun imageViewportTransform(viewportWidth:Int,viewportHeight:Int,imageWidth:Int,imageHeight:Int,zoom:Float=1f,translationX:Float=0f,translationY:Float=0f):ImageViewportTransform{
 require(viewportWidth>0&&viewportHeight>0&&imageWidth>0&&imageHeight>0)
 val fit=min(viewportWidth.toFloat()/imageWidth,viewportHeight.toFloat()/imageHeight)
 return ImageViewportTransform(fit,(viewportWidth-imageWidth*fit)/2f,(viewportHeight-imageHeight*fit)/2f,zoom,translationX,translationY)
}

internal fun ImageViewportTransform.screenBox(block:SearchTextBlock,viewportWidth:Int,viewportHeight:Int):FloatBox{
 val centerX=viewportWidth/2f;val centerY=viewportHeight/2f
 fun x(source:Int)=(imageLeft+source*fit-centerX)*zoom+centerX+translationX
 fun y(source:Int)=(imageTop+source*fit-centerY)*zoom+centerY+translationY
 return FloatBox(x(block.left),y(block.top),x(block.right),y(block.bottom))
}

internal fun focusTranslation(block:SearchTextBlock,viewportWidth:Int,viewportHeight:Int,imageWidth:Int,imageHeight:Int,zoom:Float):Offset{
 val t=imageViewportTransform(viewportWidth,viewportHeight,imageWidth,imageHeight)
 val sourceCenterX=(block.left+block.right)/2f;val sourceCenterY=(block.top+block.bottom)/2f
 val fittedX=t.imageLeft+sourceCenterX*t.fit;val fittedY=t.imageTop+sourceCenterY*t.fit
 return Offset(-(fittedX-viewportWidth/2f)*zoom,-(fittedY-viewportHeight/2f)*zoom)
}

internal fun transformedTranslation(old:Float,centroidFromViewportCenter:Float,zoomRatio:Float,pan:Float)=
 old*zoomRatio+(1f-zoomRatio)*centroidFromViewportCenter+pan

data class SearchTextBlock(
 val text:String,val left:Int,val top:Int,val right:Int,val bottom:Int
):java.io.Serializable

enum class ImageIndexStatus:java.io.Serializable { READY, FAILED }

data class ImagePage(
 val id:String=newId(),val imageUri:String,val pageIndex:Int,val imageWidth:Int,val imageHeight:Int,
 val fullText:String="",val blocks:List<SearchTextBlock> = emptyList(),val indexStatus:ImageIndexStatus=ImageIndexStatus.READY,val contentHash:String="",val sourceUrl:String="",val rotationDegrees:Int=0
):java.io.Serializable

data class ImageDocument(
 val id:String=newId(),val title:String="未命名图片资料",val createdAt:Long=System.currentTimeMillis(),val pages:List<ImagePage>
):java.io.Serializable

private fun ImagePage.isTextPage()=imageUri.isBlank()
private fun textPage(text:String)=ImagePage(imageUri="",pageIndex=0,imageWidth=0,imageHeight=0,fullText=text,indexStatus=ImageIndexStatus.READY)

data class ImageSearchHit(
 val document:ImageDocument,val page:ImagePage,val blockIndexes:List<Int>,val context:String
)

class ImageDocumentStore(private val context:Context){
 private val file=AtomicFile(File(context.filesDir,"image-documents/index.json"))

 @Synchronized fun all():List<ImageDocument>{
  if(!file.baseFile.exists())return emptyList()
  val parsed=runCatching{file.openRead().bufferedReader().use{reader->
   val array=JSONArray(reader.readText());(0 until array.length()).map{documentFromJson(array.getJSONObject(it))}
  }}.getOrElse{throw IllegalStateException("图片资料索引无法读取",it)}
  // Keep index entries even when an image file is temporarily unavailable.
  // A failed read must never silently delete medical records or their metadata.
  return parsed.sortedByDescending{it.createdAt}
 }
 @Synchronized fun save(document:ImageDocument){
  require(document.title.isNotBlank());require(document.pages.isNotEmpty())
  write(all().filterNot{it.id==document.id}+document)
 }
 @Synchronized fun reorderPages(documentId:String,orderedPageIds:List<String>):ImageDocument{
  val documents=all()
  val current=documents.firstOrNull{it.id==documentId}?:error("图片资料不存在")
  require(orderedPageIds.size==current.pages.size&&orderedPageIds.toSet()==current.pages.map{it.id}.toSet()){"图片排序数据不完整"}
  val byId=current.pages.associateBy{it.id}
  val updated=current.copy(pages=orderedPageIds.mapIndexed{index,id->requireNotNull(byId[id]).copy(pageIndex=index)})
  write(documents.map{if(it.id==documentId)updated else it})
  return updated
 }
 @Synchronized fun delete(document:ImageDocument){
  write(all().filterNot{it.id==document.id})
  document.pages.forEach(::deleteOwnedImage)
 }
 @Synchronized fun deletePage(document:ImageDocument,pageId:String):ImageDocument?{
  val current=all().firstOrNull{it.id==document.id}?:document
  val target=current.pages.firstOrNull{it.id==pageId}?:return current
  val remaining=current.pages.filterNot{it.id==pageId}.mapIndexed{index,page->page.copy(pageIndex=index)}
  if(remaining.isEmpty()){
   write(all().filterNot{it.id==current.id})
   deleteOwnedImage(target)
   return null
  }
  val updated=current.copy(pages=remaining)
  write(all().filterNot{it.id==current.id}+updated)
  deleteOwnedImage(target)
  return updated
 }
 private fun pageFileExists(page:ImagePage):Boolean{
  if(page.isTextPage())return true
  val uri=Uri.parse(page.imageUri)
  if(uri.scheme!="file")return true
  return uri.path?.let(::File)?.exists()==true
 }
 private fun deleteOwnedImage(page:ImagePage){
  runCatching{
   val imagesDir=File(context.filesDir,"image-documents/images").canonicalFile
   val uri=Uri.parse(page.imageUri)
   val image=uri.path?.let(::File)?.canonicalFile
   if(uri.scheme=="file"&&image!=null&&image.parentFile==imagesDir)image.delete()
  }
 }
 fun ownImage(uri:Uri):String{
  val dir=File(context.filesDir,"image-documents/images").apply{mkdirs()}
  val dest=File(dir,newId()+".image");val temp=File(dir,dest.name+".tmp")
  try{context.contentResolver.openInputStream(uri).use{input->requireNotNull(input){"图片无法读取"};temp.outputStream().use{out->input.copyTo(out)}};check(temp.length()>0);check(temp.renameTo(dest));return Uri.fromFile(dest).toString()}finally{temp.delete()}
 }
 fun search(query:String):List<ImageSearchHit>{
  val q=query.trim();if(q.isBlank())return emptyList()
  return all().flatMap{document->document.pages.mapNotNull{page->
   val indexes=matchingBlockIndexes(page.blocks,q)
   val pageMatches=indexes.isNotEmpty()||containsSearchText(page.fullText,q);val titleMatches=containsSearchText(document.title,q)&&page.pageIndex==0
   if(!pageMatches&&!titleMatches)null
   else ImageSearchHit(document,page,indexes,searchContext(page.fullText,q,indexes.firstOrNull()?.let{page.blocks[it].text}))
  }}
 }
 private fun write(documents:List<ImageDocument>){
  file.baseFile.parentFile?.mkdirs();val stream=file.startWrite()
  try{stream.bufferedWriter().apply{write(JSONArray().apply{documents.forEach{d->put(documentToJson(d))}}.toString());flush()};file.finishWrite(stream)}catch(t:Throwable){file.failWrite(stream);throw t}
 }
 private fun documentToJson(d:ImageDocument)=JSONObject().put("id",d.id).put("title",d.title).put("createdAt",d.createdAt).put("pages",JSONArray().apply{d.pages.forEach{p->put(JSONObject().put("id",p.id).put("uri",p.imageUri).put("page",p.pageIndex).put("width",p.imageWidth).put("height",p.imageHeight).put("text",p.fullText).put("status",p.indexStatus.name).put("hash",p.contentHash).put("sourceUrl",p.sourceUrl).put("rotation",p.rotationDegrees).put("blocks",JSONArray().apply{p.blocks.forEach{b->put(JSONObject().put("text",b.text).put("left",b.left).put("top",b.top).put("right",b.right).put("bottom",b.bottom))}}))}})
 private fun documentFromJson(o:JSONObject)=ImageDocument(o.getString("id"),o.optString("title","未命名图片资料"),o.optLong("createdAt"),o.getJSONArray("pages").let{a->(0 until a.length()).map{i->val p=a.getJSONObject(i);ImagePage(p.getString("id"),p.getString("uri"),p.getInt("page"),p.getInt("width"),p.getInt("height"),p.optString("text"),p.getJSONArray("blocks").let{bs->(0 until bs.length()).map{j->val b=bs.getJSONObject(j);SearchTextBlock(b.getString("text"),b.getInt("left"),b.getInt("top"),b.getInt("right"),b.getInt("bottom"))}},runCatching{ImageIndexStatus.valueOf(p.optString("status"))}.getOrDefault(ImageIndexStatus.FAILED),p.optString("hash"),p.optString("sourceUrl"),p.optInt("rotation",0))}})
}

private fun normalizedSearchText(value:String)=value.filterNot(Char::isWhitespace).lowercase()

internal fun containsSearchText(value:String,query:String)=normalizedSearchText(value).contains(normalizedSearchText(query))

internal fun matchingBlockIndexes(blocks:List<SearchTextBlock>,query:String):List<Int>{
 val needle=normalizedSearchText(query);if(needle.isEmpty())return emptyList()
 val direct=blocks.indices.filter{containsSearchText(blocks[it].text,query)}
 if(direct.isNotEmpty())return direct
 val ranges=mutableListOf<IntRange>();val combined=buildString{
  blocks.forEach{block->val start=length;append(normalizedSearchText(block.text));ranges+=start until length}
 }
 val start=combined.indexOf(needle);if(start<0)return emptyList();val end=start+needle.length-1
 return ranges.indices.filter{index->val range=ranges[index];!range.isEmpty()&&range.last>=start&&range.first<=end}
}

internal fun searchContext(fullText:String,query:String,matchedBlock:String?):String{
 val source=fullText.replace(Regex("\\s+")," ").trim().ifBlank{matchedBlock.orEmpty()};if(source.isBlank())return "标题命中"
 val i=source.indexOf(query,ignoreCase=true).let{if(it>=0)it else matchedBlock?.let{m->source.indexOf(m,ignoreCase=true)}?:0}
 return source.substring((i-24).coerceAtLeast(0),(i+query.length+42).coerceAtMost(source.length)).let{(if(i>24)"…" else "")+it+(if(i+query.length+42<source.length)"…" else "")}
}

private suspend fun <T> Task<T>.awaitImageIndex():T=suspendCancellableCoroutine{c->addOnSuccessListener{if(c.isActive)c.resume(it)}.addOnFailureListener{if(c.isActive)c.resumeWithException(it)}.addOnCanceledListener{c.cancel()}}

private fun originalOrientedSize(context:Context,uri:Uri):IntSize{
 val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};context.contentResolver.openInputStream(uri).use{BitmapFactory.decodeStream(it,null,bounds)};require(bounds.outWidth>0&&bounds.outHeight>0)
 val orientation=runCatching{context.contentResolver.openInputStream(uri).use{ExifInterface(requireNotNull(it)).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL)}}.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
 return if(orientation in listOf(ExifInterface.ORIENTATION_ROTATE_90,ExifInterface.ORIENTATION_ROTATE_270,ExifInterface.ORIENTATION_TRANSPOSE,ExifInterface.ORIENTATION_TRANSVERSE))IntSize(bounds.outHeight,bounds.outWidth)else IntSize(bounds.outWidth,bounds.outHeight)
}

private fun imageContentHash(context:Context,uri:Uri):String{
 val digest=MessageDigest.getInstance("SHA-256")
 context.contentResolver.openInputStream(uri).use{input->requireNotNull(input){"图片无法读取"};val buffer=ByteArray(64*1024);while(true){val count=input.read(buffer);if(count<0)break;if(count>0)digest.update(buffer,0,count)}}
 return digest.digest().joinToString(""){"%02x".format(it)}
}

private data class IndexedPageText(val fullText:String,val blocks:List<SearchTextBlock>)

private suspend fun recognizePageBitmap(
 recognizer:com.google.mlkit.vision.text.TextRecognizer,
 bitmap:Bitmap,
 original:IntSize
):IndexedPageText{
 val sx=original.width.toFloat()/bitmap.width
 val sy=original.height.toFloat()/bitmap.height
 val tileHeight=2400
 val overlap=96
 val step=(tileHeight-overlap).coerceAtLeast(1)
 val blocks=mutableListOf<SearchTextBlock>()
 val texts=mutableListOf<String>()
 var top=0
 while(top<bitmap.height){
  val height=min(tileHeight,bitmap.height-top)
  val tile=if(top==0&&height==bitmap.height)bitmap else Bitmap.createBitmap(bitmap,0,top,bitmap.width,height)
  try{
   val result=recognizer.process(InputImage.fromBitmap(tile,0)).awaitImageIndex()
   if(result.text.isNotBlank())texts+=result.text
   result.textBlocks.flatMap{it.lines}.forEach{line->
    line.boundingBox?.let{box->
     blocks+=SearchTextBlock(
      line.text,
      (box.left*sx).roundToInt(),
      ((top+box.top)*sy).roundToInt(),
      (box.right*sx).roundToInt(),
      ((top+box.bottom)*sy).roundToInt()
     )
    }
   }
  }finally{
   if(tile!==bitmap&&!tile.isRecycled)tile.recycle()
  }
  if(top+height>=bitmap.height)break
  top+=step
 }
 val uniqueBlocks=blocks.distinctBy{b->"${normalizedSearchText(b.text)}:${b.left/8}:${b.top/8}:${b.right/8}:${b.bottom/8}"}.sortedWith(compareBy<SearchTextBlock>{it.top}.thenBy{it.left})
 return IndexedPageText(
  fullText=uniqueBlocks.joinToString("\n"){it.text}.ifBlank{texts.joinToString("\n")},
  blocks=uniqueBlocks
 )
}

private suspend fun reindexImagePage(context:Context,page:ImagePage):ImagePage=withContext(Dispatchers.IO){
 val uri=Uri.parse(page.imageUri)
 val original=IntSize(page.imageWidth,page.imageHeight)
 val recognizer=TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
 try{
  val bitmap=decodeReportBitmap(context,uri,maxPixels=12_000_000L,maxDimension=16_000)
  try{
   val indexed=recognizePageBitmap(recognizer,bitmap,original)
   page.copy(
    fullText=indexed.fullText,
    blocks=indexed.blocks,
    indexStatus=if(indexed.blocks.isEmpty())ImageIndexStatus.FAILED else ImageIndexStatus.READY
   )
  }finally{if(!bitmap.isRecycled)bitmap.recycle()}
 }finally{recognizer.close()}
}

private data class ImportedImagePages(val pages:List<ImagePage>,val duplicateCount:Int)

private suspend fun importImagePages(context:Context,store:ImageDocumentStore,uris:List<Uri>,startIndex:Int,existingHashes:Set<String>,progress:(String)->Unit):ImportedImagePages=withContext(Dispatchers.IO){
 val recognizer=TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build());val pages=mutableListOf<ImagePage>()
 val seen=existingHashes.toMutableSet();var duplicates=0
 try{uris.forEachIndexed{i,source->
  progress("正在检查第 ${i+1}/${uris.size} 张…");val hash=imageContentHash(context,source)
  if(!seen.add(hash)){duplicates++;return@forEachIndexed}
  progress("正在保存第 ${i+1}/${uris.size} 张…");val owned=store.ownImage(source);val uri=Uri.parse(owned);val original=originalOrientedSize(context,uri)
  try{
   progress("正在建立第 ${i+1}/${uris.size} 张文字索引…");val bitmap=decodeReportBitmap(context,uri,maxPixels=12_000_000L,maxDimension=16_000)
   try{
    val indexed=recognizePageBitmap(recognizer,bitmap,original)
    pages+=ImagePage(imageUri=owned,pageIndex=startIndex+pages.size,imageWidth=original.width,imageHeight=original.height,fullText=indexed.fullText,indexStatus=if(indexed.blocks.isEmpty())ImageIndexStatus.FAILED else ImageIndexStatus.READY,blocks=indexed.blocks,contentHash=hash)
   }finally{bitmap.recycle()}
  }catch(t:Throwable){if(t is CancellationException)throw t;pages+=ImagePage(imageUri=owned,pageIndex=startIndex+pages.size,imageWidth=original.width,imageHeight=original.height,indexStatus=ImageIndexStatus.FAILED,contentHash=hash)}
 }}finally{recognizer.close()}
 ImportedImagePages(pages,duplicates)
}

private suspend fun existingImageHashes(context:Context,document:ImageDocument)=withContext(Dispatchers.IO){
 document.pages.filterNot{it.isTextPage()}.mapNotNull{page->page.contentHash.ifBlank{runCatching{imageContentHash(context,Uri.parse(page.imageUri))}.getOrNull()}}.toSet()
}

@Composable fun ImageDocumentsPage(m:Modifier,open:(ImageDocument,Int,List<Int>)->Unit){
 val context=LocalContext.current;val store=remember{ImageDocumentStore(context)};var revision by remember{mutableIntStateOf(0)};var busy by remember{mutableStateOf(false)};var progress by remember{mutableStateOf("")};var error by remember{mutableStateOf("")};var query by rememberSaveable{mutableStateOf("")};val scope=rememberCoroutineScope()
 val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){uris->if(uris.isNotEmpty()&&!busy){busy=true;scope.launch{try{val imported=importImagePages(context,store,uris,0,emptySet()){progress=it};require(imported.pages.isNotEmpty()){"所选图片均已重复"};val d=ImageDocument(pages=imported.pages);store.save(d);revision++;if(imported.duplicateCount>0)android.widget.Toast.makeText(context,"已跳过 ${imported.duplicateCount} 张重复图片",android.widget.Toast.LENGTH_LONG).show();open(d,0,emptyList())}catch(t:Throwable){if(t is CancellationException)throw t;error="图片资料导入失败：${t.message}"}finally{busy=false;progress=""}}}}
 val documents=runCatching{store.all()}.getOrElse{error=it.message.orEmpty();emptyList()};val hits=if(query.isBlank())emptyList()else runCatching{store.search(query)}.getOrDefault(emptyList())
 Screen(m,"图片资料",spacing=7.dp){
  Button({picker.launch(arrayOf("image/*"))},Modifier.fillMaxWidth(),enabled=!busy,colors=ButtonDefaults.buttonColors(contentColor=Color.White)){Icon(Icons.Outlined.AddPhotoAlternate,null,tint=Color.White);Spacer(Modifier.width(8.dp));Text(if(busy)progress else "导入多张图片",color=Color.White)}
  BasicTextField(query,{query=it},Modifier.fillMaxWidth().height(32.dp),singleLine=true,textStyle=LocalTextStyle.current.copy(fontSize=14.sp,color=Ink),decorationBox={inner->Row(Modifier.fillMaxSize().background(Color.White,RoundedCornerShape(16.dp)).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.weight(1f)){if(query.isEmpty())Text("搜索图片中的文字",fontSize=14.sp,color=Muted);inner()};if(query.isNotEmpty())Icon(Icons.Outlined.Clear,"清空搜索",Modifier.size(18.dp).clickable{query=""},tint=Muted)}})
  if(query.isNotBlank()){
   if(hits.isEmpty())Paper{Text("没有找到相关内容")}
   hits.forEach{hit->Paper(Modifier.clickable{open(hit.document,hit.page.pageIndex,hit.blockIndexes)}){Text("${hit.document.title} · 第 ${hit.page.pageIndex+1} 张",fontWeight=FontWeight.Bold);Text(hit.context,fontSize=13.sp,maxLines=3,overflow=TextOverflow.Ellipsis);if(hit.page.indexStatus==ImageIndexStatus.FAILED)Text("本页文字未识别 / 待建立索引",color=Accent,fontSize=12.sp)}}
  }else{
   if(documents.isEmpty())Paper{Text("还没有图片资料")}
   documents.forEach{d->Card(Modifier.fillMaxWidth().clickable{open(d,0,emptyList())},shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){Column(Modifier.padding(horizontal=18.dp,vertical=9.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){Text(d.title,fontWeight=FontWeight.Bold);Text("${d.pages.size} 张 · ${dateText(d.createdAt)}",color=Muted,fontSize=12.sp);val failed=d.pages.count{it.indexStatus==ImageIndexStatus.FAILED};if(failed>0)Text("$failed 张文字未识别 / 待建立索引",color=Accent,fontSize=12.sp)}}}
  }
  if(error.isNotBlank())Text(error,color=Bad)
 }
}

@Composable fun ImageDocumentViewer(initial:ImageDocument,initialPage:Int,initialMatches:List<Int>,onClose:()->Unit,onSaved:(ImageDocument)->Unit,onDelete:(ImageDocument)->Unit){
 val context=LocalContext.current;val store=remember{ImageDocumentStore(context)};val scope=rememberCoroutineScope()
 var document by remember{mutableStateOf(initial)};var immersive by rememberSaveable{mutableStateOf(false)};var savedTitle by remember{mutableStateOf(initial.title)};var pageIndex by rememberSaveable{mutableIntStateOf(initialPage.coerceIn(document.pages.indices))};var matches by remember{mutableStateOf(initialMatches)};var matchPosition by rememberSaveable{mutableIntStateOf(0)};var confirmDeletePage by remember{mutableStateOf(false)};var confirmDeleteDocument by remember{mutableStateOf(false)};var confirmDiscard by remember{mutableStateOf(false)};var showGrid by rememberSaveable{mutableStateOf(false)};var showLinkEditor by remember{mutableStateOf(false)};var linkDraft by remember{mutableStateOf("")};var showNoteEditor by remember{mutableStateOf(false)};var noteDraft by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)};var reindexing by remember{mutableStateOf(false)};var progress by remember{mutableStateOf("")}
 val thumbnailState=rememberLazyListState()
 LaunchedEffect(pageIndex,document.pages.size){if(document.pages.isNotEmpty())thumbnailState.animateScrollToItem(pageIndex)}
 val close={if(document.title!=savedTitle)confirmDiscard=true else onClose()}
 val addImages=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){uris->if(uris.isNotEmpty()&&!busy){busy=true;scope.launch{try{
  val hashes=existingImageHashes(context,document);val imported=importImagePages(context,store,uris,document.pages.size,hashes){progress=it}
  if(imported.pages.isNotEmpty()){val firstNew=document.pages.size;document=document.copy(pages=document.pages+imported.pages);onSaved(document);savedTitle=document.title;pageIndex=firstNew;matches=emptyList();matchPosition=0}
  val message=when{imported.duplicateCount>0&&imported.pages.isNotEmpty()->"已添加 ${imported.pages.size} 张，跳过 ${imported.duplicateCount} 张重复图片";imported.duplicateCount>0->"所选图片均已存在，无需重复添加";else->"已添加 ${imported.pages.size} 张图片"}
  android.widget.Toast.makeText(context,message,android.widget.Toast.LENGTH_LONG).show()
 }catch(t:Throwable){if(t is CancellationException)throw t;android.widget.Toast.makeText(context,"添加图片失败：${t.message}",android.widget.Toast.LENGTH_LONG).show()}finally{busy=false;progress=""}}}}
 val page=document.pages[pageIndex];val currentBlock=matches.getOrNull(matchPosition)?.let{page.blocks.getOrNull(it)}
 val previous={if(pageIndex>0){pageIndex--;matches=emptyList();matchPosition=0}}
 val next={if(pageIndex<document.pages.lastIndex){pageIndex++;matches=emptyList();matchPosition=0}}
 FullPage("图片资料",close,hidden=immersive,navigationIcon=Icons.Outlined.ArrowBack,bottom={if(!immersive)Column{
  if(matches.isNotEmpty())Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){TextButton({matchPosition=(matchPosition-1).coerceAtLeast(0)},enabled=matchPosition>0){Text("上一个")};Text("${matchPosition+1}/${matches.size}");TextButton({matchPosition=(matchPosition+1).coerceAtMost(matches.lastIndex)},enabled=matchPosition<matches.lastIndex){Text("下一个")}}
  LazyRow(Modifier.fillMaxWidth().height(58.dp),state=thumbnailState,horizontalArrangement=Arrangement.spacedBy(5.dp),contentPadding=PaddingValues(horizontal=2.dp)){itemsIndexed(document.pages){index,item->ImagePageThumbnail(item,index==pageIndex,{pageIndex=index;matches=emptyList();matchPosition=0},Modifier.width(47.dp).fillMaxHeight())}}
  Row(Modifier.fillMaxWidth().padding(top=18.dp,bottom=6.dp),verticalAlignment=Alignment.CenterVertically){
   Text(if(page.isTextPage())"删说明" else "删当前图",fontSize=14.sp,color=Bad,modifier=Modifier.clickable{confirmDeletePage=true}.padding(horizontal=3.dp,vertical=1.dp))
   if(!page.isTextPage()){
    Spacer(Modifier.width(20.dp))
    Text("旋转",fontSize=14.sp,color=Accent,modifier=Modifier.clickable{
     val updatedPage=page.copy(rotationDegrees=(page.rotationDegrees+90)%360)
     document=document.copy(pages=document.pages.map{if(it.id==page.id)updatedPage else it})
     onSaved(document);savedTitle=document.title
    }.padding(horizontal=3.dp,vertical=1.dp))
   }
   Spacer(Modifier.width(20.dp))
   Text("删整份",fontSize=13.sp,color=Bad.copy(alpha=.78f),modifier=Modifier.clickable{confirmDeleteDocument=true}.padding(horizontal=3.dp,vertical=1.dp))
   Spacer(Modifier.weight(1f))
   if(!page.isTextPage())Box(
    Modifier.size(40.dp)
     .background(if(page.sourceUrl.isBlank())Color(0xFFF0F0F0) else Color(0xFFEAF3FF),RoundedCornerShape(12.dp))
     .clickable{linkDraft=page.sourceUrl;showLinkEditor=true},
    contentAlignment=Alignment.Center
   ){
    Icon(
     Icons.Outlined.Link,
     if(page.sourceUrl.isBlank())"添加链接" else "图片来源链接",
     tint=if(page.sourceUrl.isBlank())Color(0xFF8A8A8A) else Color(0xFF5B8FD9),
     modifier=Modifier.size(20.dp)
    )
   }
  }
 }}){m->Column(m.padding(horizontal=12.dp)){
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
   Row(
    Modifier.weight(1f).height(44.dp).background(Color.White,RoundedCornerShape(12.dp)).padding(horizontal=12.dp),
    verticalAlignment=Alignment.CenterVertically
   ){
    BasicTextField(
     value=document.title,
     onValueChange={document=document.copy(title=it)},
     modifier=Modifier.weight(1f),
     singleLine=true,
     textStyle=LocalTextStyle.current.copy(fontSize=15.sp,color=Ink)
    )
    Text("保存",fontSize=12.sp,color=Accent,modifier=Modifier.clickable{if(document.title.isNotBlank()){onSaved(document);savedTitle=document.title;android.widget.Toast.makeText(context,"已保存",android.widget.Toast.LENGTH_SHORT).show()}}.padding(start=8.dp,top=5.dp,bottom=5.dp))
   }
   OutlinedButton({addImages.launch(arrayOf("image/*"))},Modifier.height(40.dp),enabled=!busy,contentPadding=PaddingValues(horizontal=10.dp,vertical=0.dp)){
    Icon(Icons.Outlined.AddPhotoAlternate,null,Modifier.size(18.dp));Spacer(Modifier.width(4.dp));Text(if(busy)progress.ifBlank{"处理中"} else "添加图片",fontSize=13.sp,maxLines=1)
   }
  }
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){
   TextButton({noteDraft=document.pages.firstOrNull{it.isTextPage()}?.fullText.orEmpty();showNoteEditor=true}){
    Icon(Icons.Outlined.Clear,null,Modifier.size(18.dp));Spacer(Modifier.width(4.dp));Text(if(document.pages.any{it.isTextPage()})"编辑说明" else "添加说明")
   }
  }
  Spacer(Modifier.height(4.dp))
  Row(Modifier.fillMaxWidth().heightIn(min=18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
   Text("第 ${pageIndex+1}/${document.pages.size} 页"+(if(!page.isTextPage()&&page.indexStatus==ImageIndexStatus.FAILED)" · 本页文字未识别 / 待建立索引" else ""),fontSize=12.sp,lineHeight=16.sp,color=if(!page.isTextPage()&&page.indexStatus==ImageIndexStatus.FAILED)Accent else Muted,modifier=Modifier.weight(1f))
   Row(verticalAlignment=Alignment.CenterVertically){
    if(!page.isTextPage()&&page.indexStatus==ImageIndexStatus.FAILED)Text(
     if(reindexing)"识别中…" else "重新识别",
     fontSize=12.sp,lineHeight=16.sp,color=if(reindexing)Muted else Accent,
     modifier=Modifier.clickable(enabled=!reindexing){
      reindexing=true
      scope.launch{
       try{
        val refreshed=reindexImagePage(context,page)
        document=document.copy(pages=document.pages.map{if(it.id==refreshed.id)refreshed else it})
        onSaved(document);savedTitle=document.title
        android.widget.Toast.makeText(context,if(refreshed.indexStatus==ImageIndexStatus.READY)"本页文字索引已建立" else "仍未识别到文字",android.widget.Toast.LENGTH_LONG).show()
       }catch(t:Throwable){
        if(t is CancellationException)throw t
        android.widget.Toast.makeText(context,"重新识别失败：${t.message}",android.widget.Toast.LENGTH_LONG).show()
       }finally{reindexing=false}
      }
     }.padding(horizontal=6.dp,vertical=1.dp)
    )
    Text("宫格",fontSize=12.sp,lineHeight=16.sp,color=Accent,modifier=Modifier.clickable{showGrid=true}.padding(horizontal=4.dp,vertical=1.dp))
   }
  }
  Spacer(Modifier.height(4.dp))
  if(page.isTextPage())TextDocumentPage(page.fullText,previous,next,Modifier.weight(1f).fillMaxWidth())
  else HighlightImage(page,currentBlock,{showGrid=true},previous,next,{immersive=!immersive},Modifier.weight(1f).fillMaxWidth())
 }}
 if(immersive&&!page.isTextPage())Box(Modifier.fillMaxSize().background(Warm).windowInsetsPadding(WindowInsets.safeDrawing)){
  HighlightImage(page,currentBlock,{showGrid=true},previous,next,{immersive=false},Modifier.fillMaxSize())
 }
 if(showNoteEditor)AlertDialog(
  onDismissRequest={showNoteEditor=false},
  title={Text("资料说明")},
  text={Column(Modifier.fillMaxWidth().background(Color.White,RoundedCornerShape(12.dp)).padding(horizontal=14.dp,vertical=12.dp)){Text("说明文字",fontSize=12.sp,color=Muted);BasicTextField(noteDraft,{noteDraft=it},Modifier.fillMaxWidth().padding(top=6.dp),minLines=6,maxLines=12,textStyle=LocalTextStyle.current.copy(fontSize=16.sp,color=Ink))}},
  confirmButton={TextButton({
   val value=noteDraft.trim();val old=document.pages.firstOrNull{it.isTextPage()}
   val pages=when{value.isBlank()->document.pages.filterNot{it.isTextPage()};old==null->listOf(textPage(value))+document.pages;else->document.pages.map{if(it.id==old.id)it.copy(fullText=value)else it}}
    .mapIndexed{index,item->item.copy(pageIndex=index)}
   document=document.copy(pages=pages);onSaved(document);savedTitle=document.title;pageIndex=if(value.isBlank())pageIndex.coerceAtMost(pages.lastIndex) else 0;matches=emptyList();matchPosition=0;showNoteEditor=false
   android.widget.Toast.makeText(context,if(value.isBlank())"说明已删除" else "说明已保存为第一页",android.widget.Toast.LENGTH_SHORT).show()
  }){Text("保存")}},
  dismissButton={TextButton({showNoteEditor=false}){Text("取消")}}
 )
 if(showLinkEditor)AlertDialog(
  onDismissRequest={showLinkEditor=false},
  title={Text(if(page.sourceUrl.isBlank())"添加文章链接" else "图片来源链接")},
  text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
   Column(Modifier.fillMaxWidth().background(Color.White,RoundedCornerShape(12.dp)).padding(horizontal=14.dp,vertical=12.dp)){
    Text("公众号文章 / 网页链接",fontSize=12.sp,color=Muted)
    BasicTextField(value=linkDraft,onValueChange={linkDraft=it},modifier=Modifier.fillMaxWidth().padding(top=6.dp),singleLine=true,textStyle=LocalTextStyle.current.copy(fontSize=16.sp,color=Ink),decorationBox={inner->Box{if(linkDraft.isBlank())Text("https://mp.weixin.qq.com/...",color=Muted);inner()}})
   }
   if(linkDraft.isNotBlank()&&!linkDraft.trim().let{it.startsWith("https://")||it.startsWith("http://")})Text("请输入 http:// 或 https:// 开头的链接",fontSize=12.sp,color=Bad)
  }},
  confirmButton={TextButton({
   val value=linkDraft.trim()
   if(value.isBlank()||value.startsWith("https://")||value.startsWith("http://")){
    val updatedPage=page.copy(sourceUrl=value)
    document=document.copy(pages=document.pages.map{if(it.id==page.id)updatedPage else it})
    onSaved(document);savedTitle=document.title;showLinkEditor=false
    android.widget.Toast.makeText(context,if(value.isBlank())"链接已删除" else "链接已保存",android.widget.Toast.LENGTH_SHORT).show()
   }else android.widget.Toast.makeText(context,"链接格式不正确",android.widget.Toast.LENGTH_SHORT).show()
  }){Text("保存")}},
  dismissButton={Row{
   if(page.sourceUrl.isNotBlank())TextButton({
    runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(page.sourceUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}
     .onFailure{android.widget.Toast.makeText(context,"无法打开这个链接",android.widget.Toast.LENGTH_SHORT).show()}
   }){Text("打开原文")}
   TextButton({showLinkEditor=false}){Text("取消")}
  }}
 )
 if(confirmDeletePage)AlertDialog(
  onDismissRequest={confirmDeletePage=false},
  title={Text(if(page.isTextPage())"删除说明？" else "删除当前图片？")},
  text={Text(if(document.pages.size==1)"这是最后一页，删除后整份资料也会移除。" else "只删除当前第 ${pageIndex+1} 页，其余内容保留。")},
  confirmButton={TextButton({
   confirmDeletePage=false
   val removedId=document.pages[pageIndex].id
   val updated=store.deletePage(document,removedId)
   if(updated==null){onClose()}else{
    document=updated
    pageIndex=pageIndex.coerceAtMost(updated.pages.lastIndex)
    matches=emptyList();matchPosition=0
    onSaved(updated)
   }
  }){Text(if(page.isTextPage())"删除说明" else "删除当前图片",color=Bad)}},
  dismissButton={TextButton({confirmDeletePage=false}){Text("取消")}}
 )
 if(confirmDeleteDocument)AlertDialog(
  onDismissRequest={confirmDeleteDocument=false},
  title={Text("删除整份图片资料？")},
  text={Text("会删除“${document.title}”中的全部 ${document.pages.size} 张原图，删除后无法在应用内恢复。")},
  confirmButton={TextButton({confirmDeleteDocument=false;onDelete(document);onClose()}){Text("删除整份",color=Bad)}},
  dismissButton={TextButton({confirmDeleteDocument=false}){Text("取消")}}
 )
 if(confirmDiscard)AlertDialog(onDismissRequest={confirmDiscard=false},title={Text("标题尚未保存")},text={Text("确定放弃本次标题修改吗？")},confirmButton={TextButton({confirmDiscard=false;onClose()}){Text("放弃")}},dismissButton={TextButton({confirmDiscard=false}){Text("继续编辑")}})
 if(showGrid)ImageDocumentGrid(document,pageIndex,{showGrid=false},{index->pageIndex=index;matches=emptyList();matchPosition=0;showGrid=false}){from,to->
  val pages=document.pages.toMutableList();val moved=pages.removeAt(from);pages.add(to,moved)
  val updated=store.reorderPages(document.id,pages.map{it.id})
  document=updated;onSaved(updated);savedTitle=updated.title;pageIndex=to
 }
}

@Composable private fun ImageDocumentGrid(document:ImageDocument,selected:Int,onClose:()->Unit,onSelect:(Int)->Unit,onMove:(Int,Int)->Unit){
 val gridState=rememberLazyGridState();val scope=rememberCoroutineScope()
 var draggingId by remember{mutableStateOf<String?>(null)};var dragOffset by remember{mutableStateOf(Offset.Zero)}
 var pendingFrom by remember{mutableIntStateOf(-1)};var pendingTo by remember{mutableIntStateOf(-1)}
 FullPage("全部图片 · ${document.pages.size} 张",onClose,navigationIcon=Icons.Outlined.ArrowBack){m->
  LazyVerticalGrid(columns=GridCells.Fixed(3),state=gridState,modifier=m.padding(horizontal=12.dp),contentPadding=PaddingValues(vertical=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
   gridItemsIndexed(document.pages,key={_,item->item.id}){index,item->Column(verticalArrangement=Arrangement.spacedBy(4.dp),modifier=Modifier.graphicsLayer{if(draggingId==item.id){translationX=dragOffset.x;translationY=dragOffset.y;alpha=.88f}}){
    val dragModifier=if(item.isTextPage())Modifier else Modifier.pointerInput(item.id,document.pages.size){
     detectDragGesturesAfterLongPress(
      onDragStart={draggingId=item.id;dragOffset=Offset.Zero;pendingFrom=index;pendingTo=index},
      onDragCancel={draggingId=null;dragOffset=Offset.Zero;pendingFrom=-1;pendingTo=-1},
      onDragEnd={if(pendingFrom>=0&&pendingTo>=0&&pendingFrom!=pendingTo)onMove(pendingFrom,pendingTo);draggingId=null;dragOffset=Offset.Zero;pendingFrom=-1;pendingTo=-1}
     ){change,amount->
      change.consume();dragOffset+=amount
      val visible=gridState.layoutInfo.visibleItemsInfo
      val dragged=visible.firstOrNull{it.key==item.id}
      if(dragged!=null){
       val centerY=dragged.offset.y+dragOffset.y+dragged.size.height/2f
       val centerX=dragged.offset.x+dragOffset.x+dragged.size.width/2f
       val target=visible.minByOrNull{v->val dx=(v.offset.x+v.size.width/2f)-centerX;val dy=(v.offset.y+v.size.height/2f)-centerY;dx*dx+dy*dy}
       if(target!=null){val minIndex=if(document.pages.firstOrNull()?.isTextPage()==true)1 else 0;pendingTo=target.index.coerceIn(minIndex,document.pages.lastIndex)}
       val top=gridState.layoutInfo.viewportStartOffset+80
       val bottom=gridState.layoutInfo.viewportEndOffset-80
       when{centerY<top->scope.launch{gridState.scrollBy(-36f)};centerY>bottom->scope.launch{gridState.scrollBy(36f)}}
      }
     }
    }
    ImagePageThumbnail(item,index==selected,{if(draggingId==null)onSelect(index)},Modifier.fillMaxWidth().aspectRatio(.78f).then(dragModifier))
    Text("第 ${index+1} 张",fontSize=12.sp,color=if(index==selected)Accent else Muted,modifier=Modifier.align(Alignment.CenterHorizontally))
   }}
  }
 }
}

@Composable private fun ImagePageThumbnail(page:ImagePage,selected:Boolean,onClick:()->Unit,modifier:Modifier=Modifier){
 if(page.isTextPage()){
  Box(modifier.background(Color(0xFFFFFBF7),RoundedCornerShape(8.dp)).border(if(selected)3.dp else 1.dp,if(selected)Accent else Color(0xFFD0D0C8),RoundedCornerShape(8.dp)).clickable(onClick=onClick).padding(6.dp),contentAlignment=Alignment.Center){
   Text(page.fullText,fontSize=8.sp,lineHeight=10.sp,maxLines=6,overflow=TextOverflow.Ellipsis,color=Ink)
  }
  return
 }
 val context=LocalContext.current
 val bitmap by produceState<android.graphics.Bitmap?>(null,page.imageUri){value=withContext(Dispatchers.IO){runCatching{decodeReportBitmap(context,Uri.parse(page.imageUri),maxPixels=300_000,maxDimension=600)}.getOrNull()}}
 Box(modifier.clipToBounds().border(if(selected)3.dp else 1.dp,if(selected)Accent else Color(0xFFD0D0C8),RoundedCornerShape(8.dp)).clickable(onClick=onClick),contentAlignment=Alignment.Center){
  if(bitmap!=null)Image(bitmap!!.asImageBitmap(),null,Modifier.fillMaxSize().graphicsLayer{rotationZ=page.rotationDegrees.toFloat()},contentScale=ContentScale.Crop) else Text("…",color=Muted)
 }
}

@Composable private fun TextDocumentPage(text:String,onSwipePrevious:()->Unit,onSwipeNext:()->Unit,modifier:Modifier=Modifier){
 var swipe by remember(text){mutableFloatStateOf(0f)}
 Card(modifier.padding(10.dp).pointerInput(text){
  detectHorizontalDragGestures(
   onDragStart={swipe=0f},
   onHorizontalDrag={change,amount->change.consume();swipe+=amount},
   onDragEnd={if(swipe>90f)onSwipePrevious() else if(swipe< -90f)onSwipeNext();swipe=0f}
  )
 },shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){
  ScrollablePageColumn(Modifier.fillMaxSize(),PaddingValues(24.dp)){
   Text("说明",fontSize=13.sp,color=Accent,fontWeight=FontWeight.Medium)
   Spacer(Modifier.height(14.dp))
   Text(text,fontSize=18.sp,lineHeight=30.sp,color=Ink)
  }
 }
}

@Composable private fun HighlightImage(
 page:ImagePage,
 highlight:SearchTextBlock?,
 onPinchIn:()->Unit,
 onSwipePrevious:()->Unit,
 onSwipeNext:()->Unit,
 onSingleTap:()->Unit,
 modifier:Modifier=Modifier
){
 val context=LocalContext.current
 var viewport by remember{mutableStateOf(IntSize.Zero)}
 var zoom by rememberSaveable(page.id){mutableFloatStateOf(1f)}
 var x by rememberSaveable(page.id){mutableFloatStateOf(0f)}
 var y by rememberSaveable(page.id){mutableFloatStateOf(0f)}
 var swipeX by remember(page.id){mutableFloatStateOf(0f)}
 var pinchScale by remember(page.id){mutableFloatStateOf(1f)}
 var freePan by rememberSaveable(page.id){mutableStateOf(false)}
 val loaded by produceState<Pair<android.graphics.Bitmap?,String?>>(null to null,page.imageUri){
  value=withContext(Dispatchers.IO){
   val longNarrow=page.imageWidth<=2_000&&page.imageHeight>=8_000
   val maxPixels=if(longNarrow)28_000_000L else 16_000_000L
   val maxDimension=if(longNarrow)24_000 else 8_192
   runCatching{decodeReportBitmap(context,Uri.parse(page.imageUri),maxPixels=maxPixels,maxDimension=maxDimension)}
    .fold({it to null},{null to "原图无法读取：${it.message}"})
  }
 }
 LaunchedEffect(highlight,viewport){if(highlight!=null&&viewport.width>0&&viewport.height>0){zoom=2.2f;val focused=focusTranslation(highlight,viewport.width,viewport.height,page.imageWidth,page.imageHeight,zoom);x=focused.x;y=focused.y}}
 Box(
  modifier.clipToBounds().onSizeChanged{viewport=it}
   .pointerInput(page.id,viewport){
    detectTapGestures(onTap={onSingleTap()},onDoubleTap={tap->
     if(viewport.width<=0||viewport.height<=0||page.imageWidth<=0||page.imageHeight<=0)return@detectTapGestures
     if(zoom>1.05f){
      zoom=1f;x=0f;y=0f;swipeX=0f;freePan=false
     }else{
      val fit=min(viewport.width.toFloat()/page.imageWidth,viewport.height.toFloat()/page.imageHeight)
      val fittedWidth=page.imageWidth*fit
      val widthFill=(viewport.width/fittedWidth).coerceAtLeast(1f)
      val target=widthFill.coerceAtMost(32f)
      if(target>1.01f){
       val ratio=target/zoom
       val cy=tap.y-viewport.height/2f
       zoom=target
       freePan=false
       x=0f
       y=transformedTranslation(y,cy,ratio,0f)
       swipeX=0f
      }
     }
    })
   }
   .pointerInput(page.id,viewport){
   detectTransformGestures{centroid,pan,scale,_->
    val fit=if(viewport.width>0&&viewport.height>0&&page.imageWidth>0&&page.imageHeight>0)min(viewport.width.toFloat()/page.imageWidth,viewport.height.toFloat()/page.imageHeight) else 1f
    val fittedWidth=(page.imageWidth*fit).coerceAtLeast(1f)
    val widthFill=(viewport.width/fittedWidth).coerceAtLeast(1f)
    val maxZoom=maxOf(8f,widthFill*1.5f).coerceAtMost(32f)
    val next=(zoom*scale).coerceIn(1f,maxZoom)
    val isPinching=abs(scale-1f)>=.015f
    val horizontallyOverflowing=fittedWidth*next>viewport.width+1f
    if(isPinching&&scale>1.005f&&horizontallyOverflowing)freePan=true
    if(isPinching&&!horizontallyOverflowing)freePan=false
    if(zoom<=1.01f&&scale<.995f){
     pinchScale*=scale
     swipeX=0f
     if(pinchScale<=.82f){pinchScale=1f;onPinchIn()}
    }else{
     if(scale>=1f)pinchScale=1f
     val horizontal=abs(pan.x)>abs(pan.y)*1.15f
     // Once pinch-zoom makes the image wider than the viewport, one-finger drag pans the image instead of paging.
     if(horizontal&&!isPinching&&!freePan){
      swipeX+=pan.x
      val threshold=(viewport.width*.16f).coerceIn(56f,120f)
      when{
       swipeX>=threshold->{swipeX=0f;onSwipePrevious()}
       swipeX<=-threshold->{swipeX=0f;onSwipeNext()}
      }
     }else{
      swipeX=0f
      val ratio=next/zoom
      val cx=centroid.x-viewport.width/2f
      val cy=centroid.y-viewport.height/2f
      if(freePan){
       x=transformedTranslation(x,cx,ratio,pan.x)
       y=transformedTranslation(y,cy,ratio,pan.y)
      }else{
       // Double-tap width-fill reading is vertically locked.
       x=0f
       y=transformedTranslation(y,cy,ratio,pan.y)
      }
      zoom=next
     }
    }
   }
  },
  contentAlignment=Alignment.Center
 ){
  val bitmap=loaded.first
  if(bitmap!=null&&viewport.width>0&&viewport.height>0)Canvas(Modifier.fillMaxSize().graphicsLayer{scaleX=zoom;scaleY=zoom;translationX=x;translationY=y}){
   val quarterTurn=page.rotationDegrees%180!=0
   val visualWidth=if(quarterTurn)page.imageHeight else page.imageWidth
   val visualHeight=if(quarterTurn)page.imageWidth else page.imageHeight
   val fit=min(size.width/visualWidth,size.height/visualHeight)
   val dw=(page.imageWidth*fit).roundToInt();val dh=(page.imageHeight*fit).roundToInt();val left=((size.width-dw)/2f).roundToInt();val top=((size.height-dh)/2f).roundToInt()
   withTransform({rotate(page.rotationDegrees.toFloat(),Offset(size.width/2f,size.height/2f))}){
    drawImage(bitmap.asImageBitmap(),dstOffset=IntOffset(left,top),dstSize=IntSize(dw,dh))
    highlight?.let{b->val rectTop=top+b.top*fit;val rectLeft=left+b.left*fit;val rectWidth=(b.right-b.left)*fit;val rectHeight=(b.bottom-b.top)*fit;drawRect(Color(0x55FFD54F),Offset(rectLeft,rectTop),Size(rectWidth,rectHeight));drawRect(Color(0xFFFFA000),Offset(rectLeft,rectTop),Size(rectWidth,rectHeight),style=Stroke(width=(2f/zoom).coerceAtLeast(.5f)))}
   }
  }else Text(loaded.second?:"正在读取原图…")
 }
}
