package com.combustible12.healthtrend

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CoreFlowTest{
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 private val context:Context get()=InstrumentationRegistry.getInstrumentation().targetContext
 @Before fun clear(){context.getSharedPreferences("healthtrend_store_v1",0).edit().clear().commit();compose.activityRule.scenario.recreate()}
 @Test fun persistenceTemplatesImagesAndHistoricalReferences(){
  val store=HealthStore(context);val items=ReportParser.parse("WBC 3.75 ×10^9/L 3.5-9.5\nLDH 189 U/L 120-250")
  val t=store.confirmTemplate("测试医院","血常规",items)
  val source=File(context.cacheDir,"fixture.png");val b=Bitmap.createBitmap(500,500,Bitmap.Config.ARGB_8888);b.eraseColor(Color.WHITE);source.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)}
  val owned=store.ownImage(Uri.fromFile(source));source.delete()
  val report=store.buildReport("测试医院","血常规",100L,listOf(owned),items,t,"完整原文")
  store.saveReport(report);val changed=items.map{it.copy(referenceLow=4.0,referenceHigh=10.0)}
  assertEquals(1,store.confirmTemplate("测试医院","血常规",changed).version)
  val next=store.confirmTemplate("测试医院","血常规",changed,newVersion=true);assertEquals(2,next.version)
  store.saveReport(store.buildReport("测试医院","血常规",200L,listOf(owned),store.applyTemplate(items,next),next))
  store.updateValue(report.id,report.results[0].id,5.0)
  val reloaded=HealthStore(context);assertEquals(2,reloaded.reports().size);assertEquals(2,reloaded.templates().size)
  val historic=reloaded.reports().first{it.id==report.id};assertEquals(3.5,historic.results[0].referenceLowAtTest!!,0.0);assertEquals("完整原文",historic.rawOcr);assertTrue(File(Uri.parse(historic.sourceImages[0].uri).path!!).exists());assertTrue(historic.results[0].editedByUser)
  assertEquals(listOf(100L,200L),reloaded.trend("WBC").map{it.first.testedAtEpochMillis})
  assertFalse(reloaded.isPrimary("LDH"));reloaded.setPrimary("LDH",true);assertTrue(HealthStore(context).isPrimary("LDH"))
  EntryKind.entries.forEach{k->store.saveEntry(HealthEntry(kind=k,title=k.title,occurredAtEpochMillis=300L,note="持久化",images=listOf(owned)))}
  assertEquals(3,HealthStore(context).entries().size);reloaded.deleteEntry(reloaded.entries().first().id);assertEquals(2,HealthStore(context).entries().size)
 }
 @Test fun existingV1DataSurvivesNewStore(){
  val json="""[{"id":"old","hospital":"医院","type":"血常规","date":1000,"tv":1,"images":[],"results":[{"id":"point","key":"HGB","raw":"血红蛋白","value":102,"unit":"g/L","low":113,"high":151,"edited":false}]}]"""
  context.getSharedPreferences("healthtrend_store_v1",0).edit().putString("reports",json).commit()
  val store=HealthStore(context);val r=store.reports().single();assertEquals("102.0",r.results.single().textValue);store.updateValue("old","point",120.0);assertEquals(113.0,HealthStore(context).reports().single().results.single().referenceLowAtTest!!,0.0)
 }
 @Test fun largePhotosAreBoundedAndExifOrientationIsPreserved(){
  val compressed=java.io.ByteArrayOutputStream()
  java.util.zip.DeflaterOutputStream(compressed).use{z->val row=ByteArray(10001);repeat(6000){z.write(row)}}
  val large=File(context.cacheDir,"large-report.png")
  java.io.DataOutputStream(large.outputStream()).use{out->
   out.write(byteArrayOf(137.toByte(),80,78,71,13,10,26,10))
   fun chunk(type:String,data:ByteArray){val name=type.toByteArray(Charsets.US_ASCII);out.writeInt(data.size);out.write(name);out.write(data);val crc=java.util.zip.CRC32();crc.update(name);crc.update(data);out.writeInt(crc.value.toInt())}
   val header=java.io.ByteArrayOutputStream();java.io.DataOutputStream(header).use{it.writeInt(10000);it.writeInt(6000);it.write(byteArrayOf(8,0,0,0,0))}
   chunk("IHDR",header.toByteArray());chunk("IDAT",compressed.toByteArray());chunk("IEND",byteArrayOf())
  }
  val originalSize=large.length();val preview=decodeReportBitmap(context,Uri.fromFile(large));assertTrue(preview.width.toLong()*preview.height<=8_000_000L);assertTrue(maxOf(preview.width,preview.height)<=4096);assertEquals(originalSize,large.length());preview.recycle()
  val photo=File(context.cacheDir,"oriented.jpg");Bitmap.createBitmap(320,160,Bitmap.Config.ARGB_8888).also{b->photo.outputStream().use{b.compress(Bitmap.CompressFormat.JPEG,95,it)};b.recycle()}
  android.media.ExifInterface(photo.path).apply{setAttribute(android.media.ExifInterface.TAG_ORIENTATION,android.media.ExifInterface.ORIENTATION_ROTATE_90.toString());saveAttributes()}
  val oriented=decodeReportBitmap(context,Uri.fromFile(photo));assertEquals(160,oriented.width);assertEquals(320,oriented.height);assertEquals(android.media.ExifInterface.ORIENTATION_ROTATE_90,android.media.ExifInterface(photo.path).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,0));oriented.recycle()
 }
 @Test fun bundledOcrReadsActualBitmap(){
  val b=Bitmap.createBitmap(1800,1000,Bitmap.Config.ARGB_8888);val canvas=Canvas(b);canvas.drawColor(Color.WHITE);val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.BLACK;textSize=62f}
  listOf("白细胞计数 3.75  3.5-9.5","中性粒细胞百分比 51.2 % 40-75","HGB 102  113-151","LDH 189  120-250").forEachIndexed{i,s->canvas.drawText(s,70f,120f+i*150,paint)}
  val recognizer=TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build());val latch=CountDownLatch(1);var raw="";var failure:Exception?=null
  val ocrSource=File(context.cacheDir,"ocr-source.png");ocrSource.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)}
  recognizer.process(InputImage.fromBitmap(decodeReportBitmap(context,Uri.fromFile(ocrSource)),0)).addOnSuccessListener{raw=ReportOcr.tableText(it);latch.countDown()}.addOnFailureListener{failure=it;latch.countDown()}
  assertTrue(latch.await(60,TimeUnit.SECONDS));recognizer.close();assertNull(failure)
  val rows=ReportParser.parse(raw);assertTrue("OCR output: $raw",rows.any{it.metricKey=="WBC"&&it.value==3.75});assertTrue(rows.any{it.metricKey=="LDH"})
  val store=HealthStore(context);val source=store.ownImage(Uri.fromFile(ocrSource));val template=store.confirmTemplate("OCR测试医院","血常规",rows)
  store.saveReport(store.buildReport("OCR测试医院","血常规",System.currentTimeMillis(),listOf(source,source),rows,template,raw))
  compose.activityRule.scenario.recreate()
  compose.onNodeWithText("查看指标和原报告 →").performScrollTo().performClick()
  compose.onNodeWithText("查看原报告 · 2 页").performScrollTo().performClick()
  compose.waitUntil(10000){compose.onAllNodesWithContentDescription("原始检查报告").fetchSemanticsNodes().size==1}
  compose.onNodeWithText("原报告 1/2").assertExists()
  compose.onNodeWithText("下一页").performClick();compose.onNodeWithText("原报告 2/2").assertExists()
  compose.activityRule.scenario.recreate();compose.onNodeWithText("原报告 2/2").assertExists()
  compose.onNodeWithText("重置缩放").performClick()
  compose.onNodeWithText("上一页").performClick();compose.onNodeWithText("原报告 1/2").assertExists()
  compose.onNodeWithContentDescription("关闭").performClick()
  compose.onNodeWithText("查看原报告 · 2 页").assertExists()
  compose.onNodeWithContentDescription("关闭").performClick()

 }
 @Test fun galleryEntryOwnsImageRecognizesConfirmsAndReopensReport(){
  val bitmap=Bitmap.createBitmap(1800,800,Bitmap.Config.ARGB_8888)
  val canvas=Canvas(bitmap);canvas.drawColor(Color.WHITE)
  val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.BLACK;textSize=62f}
  listOf("白细胞计数 3.75  3.5-9.5","HGB 102  113-151","LDH 189  120-250").forEachIndexed{i,line->canvas.drawText(line,70f,120f+i*150,paint)}
  val resolver=context.contentResolver
  val values=android.content.ContentValues().apply{
   put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,"gallery-report-fixture.png")
   put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/png")
   put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,"Pictures/HealthTrendQA/")
   put(android.provider.MediaStore.Images.Media.IS_PENDING,1)
  }
  val selected=checkNotNull(resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values))
  resolver.openOutputStream(selected).use{checkNotNull(it);assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))}
  bitmap.recycle()
  resolver.update(selected,android.content.ContentValues().apply{put(android.provider.MediaStore.Images.Media.IS_PENDING,0)},null,null)
  androidx.test.espresso.intent.Intents.init()
  try{
   // Only the system document picker's selection is supplied by the test.
   // Ownership copying, bundled OCR, draft creation and confirmation run in the app.
   val response=android.content.Intent().setData(selected).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
   androidx.test.espresso.intent.Intents.intending(androidx.test.espresso.intent.matcher.IntentMatchers.hasAction(android.content.Intent.ACTION_OPEN_DOCUMENT)).respondWith(android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_OK,response))
   compose.onNodeWithText("相册导入").performClick()
   compose.activityRule.scenario.recreate()
   compose.waitUntil(60000){compose.onAllNodesWithText("核对检查报告").fetchSemanticsNodes().size==1}
   compose.onNode(hasText("确认保存",substring=true)).assertIsNotEnabled()
   compose.onNodeWithText("医院").performScrollTo().performTextReplacement("相册测试医院")
   compose.onNodeWithText("检查类型").performScrollTo().performTextReplacement("血常规")
   compose.onNodeWithText("检查时间 YYYY-MM-DD HH:mm").performScrollTo().performTextReplacement("2026-09-26 09:30")
   // OCR rows with missing units/ranges must be explicitly reviewed before save.
   while(compose.onAllNodes(hasText("编辑指标 · 尚未完成核对")).fetchSemanticsNodes().isNotEmpty()){
    compose.onAllNodes(hasText("编辑指标 · 尚未完成核对"))[0].performScrollTo().performClick()
    // OCR fixture intentionally omits units. Explicit review must complete missing required
    // fields instead of bypassing production validation.
    val unit=compose.onNodeWithText("单位").performScrollTo()
    unit.performTextReplacement("待核对")
    compose.onNodeWithText("完成核对").performClick()
   }
   compose.onNode(hasText("确认保存",substring=true)).assertIsEnabled().performClick()
  }finally{androidx.test.espresso.intent.Intents.release()}
  val report=HealthStore(context).reports().single()
  assertEquals("相册测试医院",report.hospitalKey)
  assertEquals(parseDate("2026-09-26 09:30"),report.testedAtEpochMillis)
  assertTrue(report.results.any{it.metricKey=="WBC"&&it.value==3.75})
  assertTrue(report.results.any{it.metricKey=="LDH"})
  assertTrue(report.rawOcr.isNotBlank());assertEquals(1,report.sourceImages.size)
  val owned=File(Uri.parse(report.sourceImages.single().uri).path!!)
  assertTrue(owned.exists());assertTrue(owned.length()>0)
  resolver.delete(selected,null,null)
  assertTrue("Deleting the selected provider image must not delete the preserved report",owned.exists())
  compose.onNodeWithText("查看指标和原报告 →").performScrollTo().performClick()
  compose.onNodeWithText("查看原报告 · 1 页").performScrollTo().performClick()
  compose.waitUntil(10000){compose.onAllNodesWithContentDescription("原始检查报告").fetchSemanticsNodes().size==1}
  compose.onNodeWithContentDescription("关闭").performClick()
  compose.onNodeWithContentDescription("关闭").performClick()
 }
 @Test fun tappingActualTrendChartPointOpensHistoricalDetail(){
  val store=HealthStore(context);val items=ReportParser.parse("HGB 102 g/L 113-151");val template=store.confirmTemplate("曲线点击医院","血常规",items)
  store.saveReport(store.buildReport("曲线点击医院","血常规",parseDate("2026-09-26")!!,emptyList(),items,template))
  compose.activityRule.scenario.recreate()
  compose.onNodeWithText("趋势",useUnmergedTree=true).performClick()
  compose.onNodeWithContentDescription("趋势图 HGB").performClick()
  compose.onNodeWithText("当次参考：113.0–151.0 · 偏低").assertExists()
  compose.onNodeWithText("编辑数值").assertExists()
 }
 @Test fun trendChartAccessibilitySelectsLatestPointInMultiPointSeries(){
  val store=HealthStore(context)
  val first=ReportParser.parse("HGB 102 g/L 113-151")
  val template=store.confirmTemplate("多点曲线医院","血常规",first)
  store.saveReport(store.buildReport("多点曲线医院","血常规",parseDate("2026-09-25")!!,emptyList(),first,template))
  val second=ReportParser.parse("HGB 120 g/L 113-151")
  store.saveReport(store.buildReport("多点曲线医院","血常规",parseDate("2026-09-26")!!,emptyList(),second,template))
  compose.activityRule.scenario.recreate()
  compose.onNodeWithText("趋势",useUnmergedTree=true).performClick()
  compose.onNodeWithContentDescription("趋势图 HGB").performClick()
  compose.onNodeWithText("120.0 g/L").assertExists()
  compose.onNodeWithText("当次参考：113.0–151.0 · 正常").assertExists()
 }
 @Test fun trendDetailOpensItsOriginalReport(){
  val store=HealthStore(context)
  val source=File(context.cacheDir,"trend-source.png");val b=Bitmap.createBitmap(80,80,Bitmap.Config.ARGB_8888);b.eraseColor(Color.WHITE);source.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()
  val owned=store.ownImage(Uri.fromFile(source));source.delete()
  val items=ReportParser.parse("HGB 102 g/L 113-151");val t=store.confirmTemplate("趋势原图医院","血常规",items)
  store.saveReport(store.buildReport("趋势原图医院","血常规",parseDate("2026-09-26")!!,listOf(owned),items,t))
  compose.activityRule.scenario.recreate()
  compose.onNodeWithText("趋势",useUnmergedTree=true).performClick()
  compose.onNodeWithContentDescription("趋势图 HGB").performClick()
  compose.onNodeWithText("查看原报告").performClick()
  compose.waitUntil(10000){compose.onAllNodesWithContentDescription("原始检查报告").fetchSemanticsNodes().size==1}
  compose.onNodeWithText("原报告 1/1").assertExists()
 }
 @Test fun realUiConfirmsReportAndEditsTrendPoint(){
  compose.onNodeWithText("手动录入").performClick()
  compose.onNodeWithText("医院").performTextInput("测试医院")
  compose.onNodeWithText("编辑指标 · 尚未完成核对").performScrollTo().performClick()
  compose.onNodeWithText("项目名称").performScrollTo().performTextInput("血红蛋白")
  compose.onNodeWithText("结果（支持 <、>、阴性等）").performScrollTo().performTextInput("102")
  compose.onNodeWithText("单位").performScrollTo().performTextInput("g/L")
  compose.onNodeWithText("参考下限").performScrollTo().performTextInput("113")
  compose.onNodeWithText("参考上限").performScrollTo().performTextInput("151")
  compose.onNodeWithText("完成核对").performClick()
  compose.onNodeWithText("确认保存 · 1 个项目").performClick()
  compose.onNodeWithText("趋势",useUnmergedTree=true).performClick()
  compose.onNodeWithText("血常规").assertExists()
  compose.onNodeWithText("肝功能").assertExists()
  compose.onNodeWithText("肾功能").assertExists()
  compose.onNodeWithText("肿瘤标志物").assertExists()
  compose.onNodeWithText("102").assertExists()
  // Exercise the actual chart instead of depending on an off-screen history row.
  compose.onNodeWithContentDescription("趋势图 HGB").performClick()
  compose.onNodeWithText("当次参考：113.0–151.0 · 偏低").assertExists()
  compose.onNodeWithText("编辑数值").performClick()
  compose.onNodeWithText("结果").performTextReplacement("120")
  compose.onNodeWithText("保存").performClick()
  compose.onNodeWithText("120.0 g/L").assertExists()
  compose.onNodeWithText("关闭").performClick()
  // Closing the detail must expose the refreshed trend card, not only persisted storage.
  compose.onNodeWithText("120.0").assertExists()
  assertEquals(120.0,HealthStore(context).reports().single().results.single().value!!,0.0)
  assertEquals(113.0,HealthStore(context).reports().single().results.single().referenceLowAtTest!!,0.0)
  compose.onNodeWithText("记录",useUnmergedTree=true).performClick()
  compose.onNodeWithText("查看指标和原报告 →").performScrollTo().performClick()
  compose.onNodeWithText("编辑数据点").performScrollTo().performClick()
  compose.onNodeWithText("结果").performTextReplacement("120")
  compose.onNodeWithText("保存").performClick()
  compose.onNodeWithContentDescription("关闭").performClick()
  val result=HealthStore(context).reports().single().results.single()
  assertEquals(120.0,result.value!!,0.0);assertEquals(113.0,result.referenceLowAtTest!!,0.0)
  compose.onNodeWithText("我的",useUnmergedTree=true).performClick()
  compose.onNodeWithText("查看 / 主动编辑为新版").performScrollTo().performClick()
  compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("编辑指标"))
  compose.onNodeWithText("编辑指标").performClick()
  compose.onNodeWithText("参考下限").performScrollTo().performTextReplacement("100")
  compose.onNodeWithText("参考上限").performScrollTo().performTextReplacement("150")
  compose.onNodeWithText("完成核对").performClick()
  compose.onNodeWithText("主动确认新版模板").performClick()
  val store=HealthStore(context);assertEquals(listOf(1,2),store.templates().map{it.version}.sorted())
  assertEquals(100.0,store.latestTemplate("测试医院","血常规")!!.fields.single().referenceLow!!,0.0)
  assertEquals(113.0,store.reports().single().results.single().referenceLowAtTest!!,0.0)

 }
 @Test fun realUiCreatesMedicalAndMedication(){
  compose.onNodeWithText("病历资料").performClick()
  compose.onNodeWithText("病历标题").performTextInput("复诊记录")
  compose.onNodeWithText("医院").performTextInput("测试医院")
  compose.onNodeWithText("保存记录").performClick()
  compose.onNodeWithText("用药记录").performClick()
  compose.onNodeWithText("药品名称").performTextInput("测试药品")
  compose.onNodeWithText("每次剂量（注明单位）").performScrollTo().performTextInput("1片")
  compose.onNodeWithText("用药频率 / 时间").performScrollTo().performTextInput("每天一次")
  compose.onNodeWithText("保存记录").performClick()
  compose.onNodeWithText("记录",useUnmergedTree=true).performClick()
  compose.onNodeWithText("用药记录 · 测试药品").performScrollTo().performClick()
  compose.onNodeWithText("1片").assertExists()
  compose.onNodeWithText("每天一次").assertExists()
  compose.onNodeWithContentDescription("关闭").performClick()
  assertEquals(setOf(EntryKind.MEDICAL,EntryKind.MEDICATION),HealthStore(context).entries().map{it.kind}.toSet())
 }
 @Test fun realUiCreatesAndReopensSymptom(){
  compose.onNodeWithText("症状记录").performClick();compose.onNodeWithText("症状名称").performTextInput("小腿酸痛")
  compose.onNodeWithText("备注 / 详细记录").performScrollTo().performTextInput("晚上明显")
  val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
  automation.serviceInfo=checkNotNull(automation.serviceInfo).apply{flags=flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS}
  compose.onNode(hasSetTextAction() and hasText("晚上明显")).performClick()
  compose.waitForIdle()
  captureSymptomPage()
  compose.onNodeWithText("保存记录").assertIsDisplayed()
  assertSaveControlInsideSystemArea()
  automation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
  compose.waitForIdle()
  compose.activityRule.scenario.recreate()
  compose.onNodeWithText("小腿酸痛").assertExists()
  compose.onNode(hasSetTextAction() and hasText("晚上明显")).assertExists()
  compose.onNodeWithText("保存记录").performClick();compose.waitForIdle()
  compose.onNodeWithText("记录",useUnmergedTree=true).performClick();compose.onNodeWithText("症状记录 · 小腿酸痛").performScrollTo().performClick()
  compose.onNodeWithText("小腿酸痛").assertExists();compose.onNode(hasSetTextAction() and hasText("晚上明显")).assertExists()
  compose.onNodeWithText("保存记录").assertIsDisplayed()
  assertSaveControlInsideSystemArea()
  compose.onNodeWithContentDescription("关闭").performClick();assertEquals(1,HealthStore(context).entries().size)
  compose.onNodeWithText("症状报告").performScrollTo().performClick()
  compose.onNodeWithText("期间共 1 次记录").performScrollTo().assertIsDisplayed()
  compose.onNodeWithText("分享症状报告").performScrollTo().assertIsDisplayed()
 }
 private fun assertSaveControlInsideSystemArea(){
  compose.onNodeWithText("保存记录").assertIsDisplayed()
  compose.onNodeWithContentDescription("关闭").assertIsDisplayed()
  val save=compose.onNodeWithText("保存记录").fetchSemanticsNode().boundsInRoot
  val close=compose.onNodeWithContentDescription("关闭").fetchSemanticsNode().boundsInRoot
  // Do not infer a single "main" Compose root: dialogs/IME/owners can legitimately
  // expose more than one. Assert the controls' own visible geometry instead.
  assertTrue("Save control has no visible size: $save",save.width>0f&&save.height>0f)
  assertTrue("Close control has no visible size: $close",close.width>0f&&close.height>0f)
  assertTrue("Save control has invalid bounds: $save",save.left>=0f&&save.top>=0f&&save.right>save.left&&save.bottom>save.top)
  assertTrue("Close control has invalid bounds: $close",close.left>=0f&&close.top>=0f&&close.right>close.left&&close.bottom>close.top)
 }

 private var qaImage:Uri?=null
 private fun captureSymptomPage(){
  val bitmap=checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
  val values=android.content.ContentValues().apply{put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,"qa-symptom.png");put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/png");put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,"Pictures/HealthTrendQA/");put(android.provider.MediaStore.Images.Media.IS_PENDING,1)}
  val uri=qaImage?:checkNotNull(context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)).also{qaImage=it}
  context.contentResolver.openOutputStream(uri,"wt").use{checkNotNull(it);bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
  context.contentResolver.update(uri,android.content.ContentValues().apply{put(android.provider.MediaStore.Images.Media.IS_PENDING,0)},null,null)
 }

}
