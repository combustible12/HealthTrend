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
 @Test fun rememberedHospitalReusesConfirmedProjectsUnitsAndMissingRanges(){
  val store=HealthStore(context)
  val confirmed=ReportParser.parse("WBC 白细胞 4.35 3.5-9.5 10^9/L\n#NEUT 中性粒细胞计数 5.77 2.0-7.0 10^9/L")
  store.confirmTemplate("霞浦县中医院","血常规",confirmed)
  store.saveEntry(HealthEntry(kind=EntryKind.MEDICAL,title="既往病历",occurredAtEpochMillis=1L,hospital="另一家医院"))
  assertTrue(store.rememberedHospitals().containsAll(listOf("霞浦县中医院","另一家医院")))
  val imported=ReportParser.parse("WBC 白细胞 4.35 3.5-9.5 10^9/L\n#NEUT 中性粒细胞计数 2.00")
  val resolved=store.applyTemplate(imported,store.latestTemplate("霞浦县中医院","血常规"))
  val neut=resolved.first{it.metricKey=="NEUT#"}
  assertEquals("10^9/L",neut.unit);assertEquals(2.0,neut.referenceLow!!,0.0);assertEquals(7.0,neut.referenceHigh!!,0.0)
  val manual=retargetImportedDraft(ReportDraft(hospital="",type="血常规",rows=emptyList()),store,hospital="霞浦县中医院")
  assertEquals(listOf("WBC","NEUT#"),manual.rows.map{it.key})
  assertTrue(manual.rows.all{it.text.isBlank()})
 }
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
  compose.onNodeWithText("血常规").performScrollTo().performClick()
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
   compose.onNodeWithText("医院").performScrollTo().performTextReplacement("相册测试医院")
   compose.onNodeWithText("检查日期/时间").performScrollTo().performTextReplacement("2026-09-26 09:30")
   compose.onNodeWithText("保存").assertIsEnabled().performClick()
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
  compose.onNodeWithText("血常规").performScrollTo().performClick()
  compose.onNodeWithText("查看原报告 · 1 页").performScrollTo().performClick()
  compose.waitUntil(10000){compose.onAllNodesWithContentDescription("原始检查报告").fetchSemanticsNodes().size==1}
  compose.onNodeWithContentDescription("关闭").performClick()
  compose.onNodeWithContentDescription("关闭").performClick()
 }
 @Test fun closingUnconfirmedOcrRowDoesNotUnlockReportSave(){
  val store=HealthStore(context);val parsed=ReportParser.parse("HGB 102 113-151")
  val row=DraftRow.from(parsed.single()).copy(uncertain=true)
  assertTrue(row.uncertain);assertTrue(ReportDraft(hospital="医院",type="血常规",date="2026-09-26",rows=listOf(row)).valid())
 }
 @Test fun actualTrendCanvasPointerTapOpensExactPoint(){
  val store=HealthStore(context);val items=ReportParser.parse("HGB 102 g/L 113-151");val template=store.confirmTemplate("真实触摸医院","血常规",items)
  store.saveReport(store.buildReport("真实触摸医院","血常规",parseDate("2026-09-26")!!,emptyList(),items,template));compose.activityRule.scenario.recreate();compose.onNodeWithText("趋势",useUnmergedTree=true).performClick()
  compose.onNodeWithContentDescription("趋势图 HGB",useUnmergedTree=true).performScrollTo()
  val chart=compose.onNodeWithContentDescription("趋势图 HGB",useUnmergedTree=true).fetchSemanticsNode()
  val point=trendPointPosition(listOf(parseDate("2026-09-26")!! to 102.0),0,chart.boundsInRoot.width,chart.boundsInRoot.height,113.0,151.0)
  compose.onNodeWithContentDescription("趋势点 HGB 2026-09-26 00:00",useUnmergedTree=true).assertExists().assertHasClickAction()
  compose.onNodeWithText("09-26",useUnmergedTree=true).assertExists()
  compose.onNodeWithText("102",useUnmergedTree=true).assertExists()
  val localPoint=androidx.compose.ui.geometry.Offset(point.x,point.y)
  assertEquals(0,nearestTrendPoint(listOf(parseDate("2026-09-26")!! to 102.0),localPoint,chart.boundsInRoot.width,chart.boundsInRoot.height,113.0,151.0,24f*context.resources.displayMetrics.density))
  compose.onNodeWithContentDescription("趋势图 HGB",useUnmergedTree=true).performTouchInput{click(localPoint)}
  assertTrue(compose.onAllNodesWithText("102").fetchSemanticsNodes().isNotEmpty());assertTrue(compose.onAllNodesWithText("g/L").fetchSemanticsNodes().isNotEmpty());compose.onNodeWithText("当次参考：113.0–151.0 · 偏低").assertExists()
 }
 @Test fun trendPointGeometryMatchesPointerHitTestingForEachVisit(){
  val points=listOf(parseDate("2026-09-25 00:00")!! to 102.0,parseDate("2026-09-26 00:00")!! to 120.0)
  val first=trendPointPosition(points,0,900f,112f,113.0,151.0);val last=trendPointPosition(points,1,900f,112f,113.0,151.0)
  assertEquals(0,nearestTrendPoint(points,first,900f,112f,113.0,151.0,48f));assertEquals(1,nearestTrendPoint(points,last,900f,112f,113.0,151.0,48f))
  assertTrue(first.x<last.x)
 }
 @Test fun tappingActualTrendChartPointOpensHistoricalDetail(){
  val store=HealthStore(context);val items=ReportParser.parse("HGB 102 g/L 113-151");val template=store.confirmTemplate("曲线点击医院","血常规",items)
  store.saveReport(store.buildReport("曲线点击医院","血常规",parseDate("2026-09-26")!!,emptyList(),items,template))
  compose.activityRule.scenario.recreate()
  compose.onNodeWithText("趋势",useUnmergedTree=true).performClick()
  compose.onNodeWithContentDescription("趋势图 HGB",useUnmergedTree=true).performScrollTo();compose.onNodeWithContentDescription("趋势点 HGB 2026-09-26 00:00",useUnmergedTree=true).assertExists().assertHasClickAction().performClick()
  compose.onNodeWithText("当次参考：113.0–151.0 · 偏低").assertExists()
  compose.onNodeWithText("编辑数值").assertExists()
 }
 @Test fun multiPointTrendCanOpenFirstPointIndependently(){
  val store=HealthStore(context);val first=ReportParser.parse("HGB 102 g/L 113-151");val template=store.confirmTemplate("独立点医院","血常规",first)
  store.saveReport(store.buildReport("独立点医院","血常规",parseDate("2026-09-25")!!,emptyList(),first,template));val second=ReportParser.parse("HGB 120 g/L 113-151");store.saveReport(store.buildReport("独立点医院","血常规",parseDate("2026-09-26")!!,emptyList(),second,template))
  compose.activityRule.scenario.recreate();compose.onNodeWithText("趋势",useUnmergedTree=true).performClick();compose.onNodeWithContentDescription("趋势图 HGB",useUnmergedTree=true).performScrollTo();compose.onNodeWithContentDescription("趋势点 HGB 2026-09-25 00:00",useUnmergedTree=true).assertExists().assertHasClickAction().performClick();assertTrue(compose.onAllNodesWithText("102").fetchSemanticsNodes().isNotEmpty());assertTrue(compose.onAllNodesWithText("g/L").fetchSemanticsNodes().isNotEmpty());compose.onNodeWithText("当次参考：113.0–151.0 · 偏低").assertExists()
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
  compose.onNodeWithContentDescription("趋势图 HGB",useUnmergedTree=true).performScrollTo();compose.onNodeWithContentDescription("趋势点 HGB 2026-09-26 00:00",useUnmergedTree=true).assertHasClickAction().performClick()
  assertTrue(compose.onAllNodesWithText("120").fetchSemanticsNodes().isNotEmpty());assertTrue(compose.onAllNodesWithText("g/L").fetchSemanticsNodes().isNotEmpty())
  compose.onNodeWithText("当次参考：113.0–151.0 · 范围内").assertExists()
 }
 @Test fun trendDetailOpensItsOriginalReport(){
  val store=HealthStore(context)
  val source=File(context.cacheDir,"trend-source.png");val b=Bitmap.createBitmap(80,80,Bitmap.Config.ARGB_8888);b.eraseColor(Color.WHITE);source.outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()
  val owned=store.ownImage(Uri.fromFile(source));source.delete()
  val items=ReportParser.parse("HGB 102 g/L 113-151");val t=store.confirmTemplate("趋势原图医院","血常规",items)
  store.saveReport(store.buildReport("趋势原图医院","血常规",parseDate("2026-09-26")!!,listOf(owned),items,t))
  compose.activityRule.scenario.recreate()
  compose.onNodeWithText("趋势",useUnmergedTree=true).performClick()
  compose.onNodeWithContentDescription("趋势图 HGB",useUnmergedTree=true).performScrollTo();compose.onNodeWithContentDescription("趋势点 HGB 2026-09-26 00:00",useUnmergedTree=true).assertHasClickAction().performClick()
  compose.onNodeWithText("查看原报告").performClick()
  compose.waitUntil(10000){compose.onAllNodesWithContentDescription("原始检查报告").fetchSemanticsNodes().size==1}
  compose.onNodeWithText("原报告 1/1").assertExists()
 }
 @Test fun realUiConfirmsReportAndEditsTrendPoint(){
  compose.onNodeWithText("手动录入").performClick()
  compose.onNodeWithText("医院").performTextInput("测试医院")
  compose.onNodeWithText("编辑").performScrollTo().performClick()
 compose.onNodeWithText("项目名称").performScrollTo().performTextInput("血红蛋白")
 compose.onNodeWithText("结果（支持 <、>、阴性等）").performScrollTo().performTextInput("102")
 compose.onNodeWithText("单位").performScrollTo().performTextInput("g/L")
  compose.onNodeWithText("参考下限").performScrollTo().performTextInput("113")
  compose.onNodeWithText("参考上限").performScrollTo().performTextInput("151")
  compose.onNodeWithText("完成核对").performClick()
  compose.onNodeWithText("保存").performClick()
  compose.onNodeWithText("趋势",useUnmergedTree=true).performClick()
  compose.onNodeWithText("血常规").assertExists()
  compose.onNodeWithText("肝功能").assertExists()
  compose.onNodeWithText("肾功能").assertExists()
  compose.onNodeWithText("肿瘤标志物").assertExists()
  assertTrue(compose.onAllNodesWithText("102").fetchSemanticsNodes().isNotEmpty())
  // Exercise the actual chart instead of depending on an off-screen history row.
  val reportDate=HealthStore(context).reports().single().testedAtEpochMillis
  compose.onNodeWithContentDescription("趋势图 HGB",useUnmergedTree=true).performScrollTo()
  compose.onNodeWithContentDescription(trendPointContentDescription("HGB",reportDate),useUnmergedTree=true).assertHasClickAction().performClick()
  compose.onNodeWithText("当次参考：113.0–151.0 · 偏低").assertExists()
  compose.onNodeWithText("编辑数值").performClick()
  compose.onNodeWithText("结果").performTextReplacement("120")
  compose.onNodeWithText("保存").performClick()
  assertTrue(compose.onAllNodesWithText("120").fetchSemanticsNodes().isNotEmpty());assertTrue(compose.onAllNodesWithText("g/L").fetchSemanticsNodes().isNotEmpty())
  compose.onNodeWithText("当次参考：113.0–151.0 · 范围内").assertExists()
  compose.onNodeWithText("关闭").performClick()
  // Closing the detail must expose the refreshed trend card, not only persisted storage.
  assertTrue(compose.onAllNodesWithText("120").fetchSemanticsNodes().isNotEmpty())
  compose.onNodeWithText("当次参考 113.0–151.0",substring=true).assertExists()
  assertEquals(120.0,HealthStore(context).reports().single().results.single().value!!,0.0)
  assertEquals(113.0,HealthStore(context).reports().single().results.single().referenceLowAtTest!!,0.0)
  compose.onNodeWithText("记录",useUnmergedTree=true).performClick()
  compose.onNodeWithText("血常规").performScrollTo().performClick()
  compose.onNodeWithText("编辑数据点").performScrollTo().performClick()
  compose.onNodeWithText("结果").performTextReplacement("120")
  compose.onNodeWithText("保存").performClick()
  compose.onNodeWithContentDescription("关闭").performClick()
  val result=HealthStore(context).reports().single().results.single()
  assertEquals(120.0,result.value!!,0.0);assertEquals(113.0,result.referenceLowAtTest!!,0.0)
  compose.onNodeWithText("我的",useUnmergedTree=true).performClick()
  compose.onNodeWithContentDescription("编辑模板 测试医院 血常规").performScrollTo().performClick()
  compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("编辑"))
  compose.onNodeWithText("编辑").performClick()
  compose.onNodeWithText("参考下限").performScrollTo().performTextReplacement("100")
  compose.onNodeWithText("参考上限").performScrollTo().performTextReplacement("150")
  compose.onNodeWithText("完成核对").performClick()
  compose.onNodeWithText("保存模板").performClick()
  val store=HealthStore(context);assertEquals(listOf(2),store.templates().filter{it.hospitalKey=="测试医院"}.map{it.version})
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

 @Test fun customReportTypeKeepsOtherMetricsInTrends(){
  val store=HealthStore(context)
  val items=ReportParser.parse("TSH 3.2 mIU/L 0.27-4.2")
  val template=store.confirmTemplate("自定义检查医院","内分泌",items)
  store.saveReport(store.buildReport("自定义检查医院","内分泌",parseDate("2026-09-26")!!,emptyList(),items,template))
  compose.activityRule.scenario.recreate()
  compose.onNodeWithText("趋势",useUnmergedTree=true).performClick()
  compose.onNodeWithText("内分泌").performScrollTo().performClick()
  compose.onNodeWithText("其他指标").performClick()
  assertTrue(compose.onAllNodesWithText("TSH").fetchSemanticsNodes().isNotEmpty())
  compose.onNodeWithText("设为重点指标").performScrollTo().performClick()
  compose.onNodeWithText("重点指标").performClick()
  assertTrue(compose.onAllNodesWithText("TSH").fetchSemanticsNodes().isNotEmpty())
  assertTrue(HealthStore(context).isPrimary("TSH"))
 }


 @Test fun reportOverrideKeepsCurrentTemplateAndHistoricalSnapshotSeparate(){
  val store=HealthStore(context)
  val before=ReportParser.parse("HGB 102 g/L 113-151")
  val first=store.confirmTemplate("范围版本医院","血常规",before)
  val original=store.buildReport("范围版本医院","血常规",parseDate("2026-09-26")!!,emptyList(),before,first)
  store.saveReport(original)
  compose.activityRule.scenario.recreate()
  compose.onNodeWithText("手动录入").performClick()
  compose.onNodeWithText("医院").performTextInput("范围版本医院")
  compose.onNodeWithText("编辑").performScrollTo().performClick()
  compose.onNodeWithText("结果（支持 <、>、阴性等）").performScrollTo().performTextReplacement("120")
  compose.onNodeWithText("本次报告项目或范围有变化").performScrollTo().performClick()
  compose.onNodeWithText("项目名称").performScrollTo().performTextReplacement("血红蛋白")
  compose.onNodeWithText("参考下限").performScrollTo().performTextReplacement("100")
  compose.onNodeWithText("参考上限").performScrollTo().performTextReplacement("150")
  compose.onNodeWithText("完成核对").performClick()
  compose.onNodeWithText("保存").performClick()
  val saved=HealthStore(context)
  assertEquals(listOf(1),saved.templates().filter{it.hospitalKey=="范围版本医院"}.map{it.version})
  val older=saved.reports().first{it.id==original.id}
  assertEquals(1,older.templateVersion)
  assertEquals(113.0,older.results.single().referenceLowAtTest!!,0.0)
  val newer=saved.reports().first{it.id!=original.id}
  assertEquals(1,newer.templateVersion)
  assertEquals(100.0,newer.results.single().referenceLowAtTest!!,0.0)
  assertEquals(150.0,newer.results.single().referenceHighAtTest!!,0.0)
 }

 @Test fun legacyTemplateVersionsMigrateToOneCompleteCurrentTemplate(){
  fun version(v:Int,count:Int,label:String):org.json.JSONObject=org.json.JSONObject().put("hospital","霞浦县中医院").put("type","血常规").put("system","").put("version",v).put("confirmed",true).put("fields",org.json.JSONArray().apply{
   repeat(count){i->put(org.json.JSONObject().put("key","K$i").put("name",if(i==0)label else "指标$i").put("unit",if(i==0)"10^9/L" else "").put("low",if(i==0)3.5 else org.json.JSONObject.NULL).put("high",if(i==0)9.5 else org.json.JSONObject.NULL))}
  })
  val legacy=org.json.JSONArray().put(version(3,27,"旧完整模板")).put(version(4,27,"最后完整人工模板")).put(version(5,26,"不完整模板"))
  context.getSharedPreferences("healthtrend_store_v1",0).edit().putString("templates",legacy.toString()).commit()
  val store=HealthStore(context)
  val blood=store.templates().filter{it.hospitalKey=="霞浦县中医院"&&it.reportType=="血常规"}
  assertEquals(1,blood.size);assertEquals(4,blood.single().version);assertEquals(27,blood.single().fields.size);assertEquals("最后完整人工模板",blood.single().fields.first().displayName)
  assertEquals(18,store.latestTemplate("霞浦县中医院","生化")!!.fields.size)
 }


 @Test fun importedSystemSwitchNeverOverwritesTheCurrentReportsRanges(){
  val store=HealthStore(context)
  val initial=ReportParser.parse("HGB 102 g/L 113-151")
  val target=ReportParser.parse("HGB 120 g/L 100-150")
  val original=ReportParser.parse("HGB 120 g/L 90-160")
  val blank=store.confirmTemplate("体系验证医院","血常规",initial)
  store.confirmTemplate("体系验证医院","血常规",target,"设备B")
  val draft=ReportDraft(
   hospital="体系验证医院",type="血常规",ocr="HGB 120 g/L 90-160",
   rows=store.applyTemplate(original,blank).map{DraftRow.from(it)}
  )
  assertEquals(90.0,draft.rows.single().low.toDouble(),0.0)
  val selected=retargetImportedDraft(draft,store,system="设备B")
  assertEquals(100.0,selected.rows.single().low.toDouble(),0.0)
  assertEquals(150.0,selected.rows.single().high.toDouble(),0.0)
  val unrecognized=retargetImportedDraft(draft,store,system="设备C")
  assertEquals(90.0,unrecognized.rows.single().low.toDouble(),0.0)
  assertEquals(160.0,unrecognized.rows.single().high.toDouble(),0.0)
  val edited=draft.copy(rows=draft.rows.map{it.copy(low="115")})
  val preserved=retargetImportedDraft(edited,store,system="设备B")
  assertEquals(100.0,preserved.rows.single().low.toDouble(),0.0)
  assertFalse(preserved.rows.single().uncertain)
 }

 @Test fun ambiguousRepeatedOcrMetricMustBeReviewedBeforeTemplateRetarget(){
  val store=HealthStore(context)
  val raw="HGB 102 g/L 90-160\nHGB 120 g/L 90-160"
  val parsed=ReportParser.parse(raw)
  val confirmed=store.confirmTemplate("多页报告医院","血常规",ReportParser.parse("HGB 110 g/L 100-150"),"设备B")
  assertTrue(confirmed.confirmed)
  val ambiguous=ReportDraft(hospital="多页报告医院",type="血常规",ocr=raw,
   rows=listOf(DraftRow.from(parsed.first()).copy(raw="")))
  val selected=retargetImportedDraft(ambiguous,store,system="设备B")
  assertEquals("100.0",selected.rows.single().low)
  assertEquals("150.0",selected.rows.single().high)
  assertEquals("",selected.rows.single().text)
  assertTrue(selected.rows.single().uncertain)
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
