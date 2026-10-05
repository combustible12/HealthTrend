package com.combustible12.healthtrend

import android.content.Context
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Clear
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
 val fullText:String="",val blocks:List<SearchTextBlock> = emptyList(),val indexStatus:ImageIndexStatus=ImageIndexStatus.READY,val contentHash:String=""
):java.io.Serializable

data class ImageDocument(
 val id:String=newId(),val title:String="未命名图片资料",val createdAt:Long=System.currentTimeMillis(),val pages:List<ImagePage>
):java.io.Serializable

data class ImageSearchHit(
 val document:ImageDocument,val page:ImagePage,val blockIndexes:List<Int>,val context:String
)

class ImageDocumentStore(private val context:Context){
 private val file=AtomicFile(File(context.filesDir,"image-documents/index.json"))

 @Synchronized fun all():List<ImageDocument>{
  if(!file.baseFile.exists())return emptyList()
  return runCatching{file.openRead().bufferedReader().use{reader->
   val array=JSONArray(reader.readText());(0 until array.length()).map{documentFromJson(array.getJSONObject(it))}
  }}.getOrElse{throw IllegalStateException("图片资料索引无法读取",it)}.sortedByDescending{it.createdAt}
 }
 @Synchronized fun save(document:ImageDocument){
  require(document.title.isNotBlank());require(document.pages.isNotEmpty())
  write(all().filterNot{it.id==document.id}+document)
 }
 @Synchronized fun delete(document:ImageDocument){
  write(all().filterNot{it.id==document.id})
  val imagesDir=File(context.filesDir,"image-documents/images").canonicalFile
  document.pages.forEach{page->runCatching{val uri=Uri.parse(page.imageUri);val image=uri.path?.let(::File)?.canonicalFile;if(uri.scheme=="file"&&image!=null&&image.parentFile==imagesDir)image.delete()}}
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
 private fun documentToJson(d:ImageDocument)=JSONObject().put("id",d.id).put("title",d.title).put("createdAt",d.createdAt).put("pages",JSONArray().apply{d.pages.forEach{p->put(JSONObject().put("id",p.id).put("uri",p.imageUri).put("page",p.pageIndex).put("width",p.imageWidth).put("height",p.imageHeight).put("text",p.fullText).put("status",p.indexStatus.name).put("hash",p.contentHash).put("blocks",JSONArray().apply{p.blocks.forEach{b->put(JSONObject().put("text",b.text).put("left",b.left).put("top",b.top).put("right",b.right).put("bottom",b.bottom))}}))}})
 private fun documentFromJson(o:JSONObject)=ImageDocument(o.getString("id"),o.optString("title","未命名图片资料"),o.optLong("createdAt"),o.getJSONArray("pages").let{a->(0 until a.length()).map{i->val p=a.getJSONObject(i);ImagePage(p.getString("id"),p.getString("uri"),p.getInt("page"),p.getInt("width"),p.getInt("height"),p.optString("text"),p.getJSONArray("blocks").let{bs->(0 until bs.length()).map{j->val b=bs.getJSONObject(j);SearchTextBlock(b.getString("text"),b.getInt("left"),b.getInt("top"),b.getInt("right"),b.getInt("bottom"))}},runCatching{ImageIndexStatus.valueOf(p.optString("status"))}.getOrDefault(ImageIndexStatus.FAILED),p.optString("hash"))}})
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

private data class ImportedImagePages(val pages:List<ImagePage>,val duplicateCount:Int)

private suspend fun importImagePages(context:Context,store:ImageDocumentStore,uris:List<Uri>,startIndex:Int,existingHashes:Set<String>,progress:(String)->Unit):ImportedImagePages=withContext(Dispatchers.IO){
 val recognizer=TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build());val pages=mutableListOf<ImagePage>()
 val seen=existingHashes.toMutableSet();var duplicates=0
 try{uris.forEachIndexed{i,source->
  progress("正在检查第 ${i+1}/${uris.size} 张…");val hash=imageContentHash(context,source)
  if(!seen.add(hash)){duplicates++;return@forEachIndexed}
  progress("正在保存第 ${i+1}/${uris.size} 张…");val owned=store.ownImage(source);val uri=Uri.parse(owned);val original=originalOrientedSize(context,uri)
  try{
   progress("正在建立第 ${i+1}/${uris.size} 张文字索引…");val bitmap=decodeReportBitmap(context,uri,maxDimension=12000)
   try{
    val result=recognizer.process(InputImage.fromBitmap(bitmap,0)).awaitImageIndex();val sx=original.width.toFloat()/bitmap.width;val sy=original.height.toFloat()/bitmap.height
    val blocks=result.textBlocks.flatMap{it.lines}.mapNotNull{line->line.boundingBox?.let{box->SearchTextBlock(line.text,(box.left*sx).roundToInt(),(box.top*sy).roundToInt(),(box.right*sx).roundToInt(),(box.bottom*sy).roundToInt())}}
    pages+=ImagePage(imageUri=owned,pageIndex=startIndex+pages.size,imageWidth=original.width,imageHeight=original.height,fullText=result.text,indexStatus=if(blocks.isEmpty())ImageIndexStatus.FAILED else ImageIndexStatus.READY,blocks=blocks,contentHash=hash)
   }finally{bitmap.recycle()}
  }catch(t:Throwable){if(t is CancellationException)throw t;pages+=ImagePage(imageUri=owned,pageIndex=startIndex+pages.size,imageWidth=original.width,imageHeight=original.height,indexStatus=ImageIndexStatus.FAILED,contentHash=hash)}
 }}finally{recognizer.close()}
 ImportedImagePages(pages,duplicates)
}

private suspend fun existingImageHashes(context:Context,document:ImageDocument)=withContext(Dispatchers.IO){
 document.pages.mapNotNull{page->page.contentHash.ifBlank{runCatching{imageContentHash(context,Uri.parse(page.imageUri))}.getOrNull()}}.toSet()
}

@Composable fun ImageDocumentsPage(m:Modifier,open:(ImageDocument,Int,List<Int>)->Unit){
 val context=LocalContext.current;val store=remember{ImageDocumentStore(context)};var revision by remember{mutableIntStateOf(0)};var busy by remember{mutableStateOf(false)};var progress by remember{mutableStateOf("")};var error by remember{mutableStateOf("")};var query by rememberSaveable{mutableStateOf("")};val scope=rememberCoroutineScope()
 val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){uris->if(uris.isNotEmpty()&&!busy){busy=true;scope.launch{try{val imported=importImagePages(context,store,uris,0,emptySet()){progress=it};require(imported.pages.isNotEmpty()){"所选图片均已重复"};val d=ImageDocument(pages=imported.pages);store.save(d);revision++;if(imported.duplicateCount>0)android.widget.Toast.makeText(context,"已跳过 ${imported.duplicateCount} 张重复图片",android.widget.Toast.LENGTH_LONG).show();open(d,0,emptyList())}catch(t:Throwable){if(t is CancellationException)throw t;error="图片资料导入失败：${t.message}"}finally{busy=false;progress=""}}}}
 val documents=runCatching{store.all()}.getOrElse{error=it.message.orEmpty();emptyList()};val hits=if(query.isBlank())emptyList()else runCatching{store.search(query)}.getOrDefault(emptyList())
 Screen(m,"图片资料","保存原图，文字仅用于搜索和定位"){
  Button({picker.launch(arrayOf("image/*"))},Modifier.fillMaxWidth(),enabled=!busy){Icon(Icons.Outlined.AddPhotoAlternate,null);Spacer(Modifier.width(8.dp));Text(if(busy)progress else "导入多张图片")}
  OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),label={Text("搜索图片中的文字")},singleLine=true,trailingIcon={if(query.isNotEmpty())IconButton({query=""}){Icon(Icons.Outlined.Clear,"清空搜索")}})
  if(query.isNotBlank()){
   if(hits.isEmpty())Paper{Text("没有找到相关内容")}
   hits.forEach{hit->Paper(Modifier.clickable{open(hit.document,hit.page.pageIndex,hit.blockIndexes)}){Text("${hit.document.title} · 第 ${hit.page.pageIndex+1} 张",fontWeight=FontWeight.Bold);Text(hit.context,fontSize=13.sp,maxLines=3,overflow=TextOverflow.Ellipsis);if(hit.page.indexStatus==ImageIndexStatus.FAILED)Text("本页文字未识别 / 待建立索引",color=Accent,fontSize=12.sp)}}
  }else{
   if(documents.isEmpty())Paper{Text("还没有图片资料")}
   documents.forEach{d->Paper(Modifier.clickable{open(d,0,emptyList())}){Text(d.title,fontWeight=FontWeight.Bold);Text("${d.pages.size} 张 · ${dateText(d.createdAt)}",color=Muted,fontSize=12.sp);val failed=d.pages.count{it.indexStatus==ImageIndexStatus.FAILED};if(failed>0)Text("$failed 张文字未识别 / 待建立索引",color=Accent,fontSize=12.sp)}}
  }
  if(error.isNotBlank())Text(error,color=Bad)
 }
}

@Composable fun ImageDocumentViewer(initial:ImageDocument,initialPage:Int,initialMatches:List<Int>,onClose:()->Unit,onSaved:(ImageDocument)->Unit,onDelete:(ImageDocument)->Unit){
 val context=LocalContext.current;val store=remember{ImageDocumentStore(context)};val scope=rememberCoroutineScope()
 var document by remember{mutableStateOf(initial)};var savedTitle by remember{mutableStateOf(initial.title)};var pageIndex by rememberSaveable{mutableIntStateOf(initialPage.coerceIn(document.pages.indices))};var matches by remember{mutableStateOf(initialMatches)};var matchPosition by rememberSaveable{mutableIntStateOf(0)};var confirmDelete by remember{mutableStateOf(false)};var confirmDiscard by remember{mutableStateOf(false)};var showGrid by rememberSaveable{mutableStateOf(false)};var busy by remember{mutableStateOf(false)};var progress by remember{mutableStateOf("")}
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
 FullPage("图片资料",close,navigationIcon=Icons.Outlined.ArrowBack,bottom={Column(verticalArrangement=Arrangement.spacedBy(4.dp)){
  if(matches.isNotEmpty())Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){TextButton({matchPosition=(matchPosition-1).coerceAtLeast(0)},enabled=matchPosition>0){Text("上一个")};Text("${matchPosition+1}/${matches.size}");TextButton({matchPosition=(matchPosition+1).coerceAtMost(matches.lastIndex)},enabled=matchPosition<matches.lastIndex){Text("下一个")}}
  LazyRow(Modifier.fillMaxWidth().height(72.dp),state=thumbnailState,horizontalArrangement=Arrangement.spacedBy(6.dp),contentPadding=PaddingValues(horizontal=4.dp)){itemsIndexed(document.pages){index,item->ImagePageThumbnail(item,index==pageIndex,{pageIndex=index;matches=emptyList();matchPosition=0},Modifier.width(58.dp).fillMaxHeight())}}
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){TextButton({pageIndex--;matches=emptyList();matchPosition=0},enabled=pageIndex>0){Text("上一张")};TextButton({confirmDelete=true}){Text("删除资料",color=Bad)};TextButton({pageIndex++;matches=emptyList();matchPosition=0},enabled=pageIndex<document.pages.lastIndex){Text("下一张")}}
 }}){m->Column(m.padding(horizontal=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
  OutlinedTextField(document.title,{document=document.copy(title=it)},Modifier.fillMaxWidth(),label={Text("资料标题")},singleLine=true,trailingIcon={TextButton({if(document.title.isNotBlank()){onSaved(document);savedTitle=document.title;android.widget.Toast.makeText(context,"已保存",android.widget.Toast.LENGTH_SHORT).show();onClose()}}){Text("保存")}})
  OutlinedButton({addImages.launch(arrayOf("image/*"))},Modifier.fillMaxWidth(),enabled=!busy){Icon(Icons.Outlined.AddPhotoAlternate,null);Spacer(Modifier.width(8.dp));Text(if(busy)progress else "添加图片")}
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){Text("第 ${pageIndex+1}/${document.pages.size} 张"+(if(page.indexStatus==ImageIndexStatus.FAILED)" · 本页文字未识别 / 待建立索引" else ""),fontSize=12.sp,color=if(page.indexStatus==ImageIndexStatus.FAILED)Accent else Muted);TextButton({showGrid=true}){Text("宫格查看")}}
  HighlightImage(page,currentBlock,Modifier.weight(1f).fillMaxWidth())
 }}
 if(confirmDelete)DeleteConfirmation({confirmDelete=false}){onDelete(document);onClose()}
 if(confirmDiscard)AlertDialog(onDismissRequest={confirmDiscard=false},title={Text("标题尚未保存")},text={Text("确定放弃本次标题修改吗？")},confirmButton={TextButton({confirmDiscard=false;onClose()}){Text("放弃")}},dismissButton={TextButton({confirmDiscard=false}){Text("继续编辑")}})
 if(showGrid)ImageDocumentGrid(document,pageIndex,{showGrid=false}){index->pageIndex=index;matches=emptyList();matchPosition=0;showGrid=false}
}

@Composable private fun ImageDocumentGrid(document:ImageDocument,selected:Int,onClose:()->Unit,onSelect:(Int)->Unit){
 FullPage("全部图片 · ${document.pages.size} 张",onClose,navigationIcon=Icons.Outlined.ArrowBack){m->
  LazyVerticalGrid(columns=GridCells.Fixed(3),modifier=m.padding(horizontal=12.dp),contentPadding=PaddingValues(vertical=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
   gridItemsIndexed(document.pages){index,item->Column(verticalArrangement=Arrangement.spacedBy(4.dp)){
    ImagePageThumbnail(item,index==selected,{onSelect(index)},Modifier.fillMaxWidth().aspectRatio(.78f))
    Text("第 ${index+1} 张",fontSize=12.sp,color=if(index==selected)Accent else Muted,modifier=Modifier.align(Alignment.CenterHorizontally))
   }}
  }
 }
}

@Composable private fun ImagePageThumbnail(page:ImagePage,selected:Boolean,onClick:()->Unit,modifier:Modifier=Modifier){
 val context=LocalContext.current
 val bitmap by produceState<android.graphics.Bitmap?>(null,page.imageUri){value=withContext(Dispatchers.IO){runCatching{decodeReportBitmap(context,Uri.parse(page.imageUri),maxPixels=300_000,maxDimension=600)}.getOrNull()}}
 Box(modifier.clipToBounds().border(if(selected)3.dp else 1.dp,if(selected)Accent else Color(0xFFD0D0C8),RoundedCornerShape(8.dp)).clickable(onClick=onClick),contentAlignment=Alignment.Center){
  if(bitmap!=null)Image(bitmap!!.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop) else Text("…",color=Muted)
 }
}

@Composable private fun HighlightImage(page:ImagePage,highlight:SearchTextBlock?,modifier:Modifier=Modifier){
 val context=LocalContext.current;var viewport by remember{mutableStateOf(IntSize.Zero)};var zoom by rememberSaveable(page.id){mutableFloatStateOf(1f)};var x by rememberSaveable(page.id){mutableFloatStateOf(0f)};var y by rememberSaveable(page.id){mutableFloatStateOf(0f)}
 val loaded by produceState<Pair<android.graphics.Bitmap?,String?>>(null to null,page.imageUri){value=withContext(Dispatchers.IO){runCatching{decodeReportBitmap(context,Uri.parse(page.imageUri))}.fold({it to null},{null to "原图无法读取：${it.message}"})}}
 LaunchedEffect(highlight,viewport){if(highlight!=null&&viewport.width>0&&viewport.height>0){zoom=2.2f;val focused=focusTranslation(highlight,viewport.width,viewport.height,page.imageWidth,page.imageHeight,zoom);x=focused.x;y=focused.y}}
 Box(modifier.clipToBounds().onSizeChanged{viewport=it}.pointerInput(page.id,viewport){detectTransformGestures{centroid,pan,scale,_->val next=(zoom*scale).coerceIn(1f,8f);val ratio=next/zoom;val cx=centroid.x-viewport.width/2f;val cy=centroid.y-viewport.height/2f;x=transformedTranslation(x,cx,ratio,pan.x);y=transformedTranslation(y,cy,ratio,pan.y);zoom=next}},contentAlignment=Alignment.Center){
  val bitmap=loaded.first
  if(bitmap!=null&&viewport.width>0&&viewport.height>0)Canvas(Modifier.fillMaxSize().graphicsLayer{scaleX=zoom;scaleY=zoom;translationX=x;translationY=y}){
   val fit=min(size.width/page.imageWidth,size.height/page.imageHeight);val dw=(page.imageWidth*fit).roundToInt();val dh=(page.imageHeight*fit).roundToInt();val left=((size.width-dw)/2f).roundToInt();val top=((size.height-dh)/2f).roundToInt()
   drawImage(bitmap.asImageBitmap(),dstOffset=IntOffset(left,top),dstSize=IntSize(dw,dh))
   highlight?.let{b->val rectTop=top+b.top*fit;val rectLeft=left+b.left*fit;val rectWidth=(b.right-b.left)*fit;val rectHeight=(b.bottom-b.top)*fit;drawRect(Color(0x55FFD54F),Offset(rectLeft,rectTop),Size(rectWidth,rectHeight));drawRect(Color(0xFFFFA000),Offset(rectLeft,rectTop),Size(rectWidth,rectHeight),style=Stroke(width=(2f/zoom).coerceAtLeast(.5f)))}
  }else Text(loaded.second?:"正在读取原图…")
 }
}
