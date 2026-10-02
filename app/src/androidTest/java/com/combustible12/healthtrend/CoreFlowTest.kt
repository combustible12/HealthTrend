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
 @Before fun clear(){context.getSharedPreferences("healthtrend_store_v1",0).edit().clear().commit()}
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
 @Test fun bundledOcrReadsActualBitmap(){
  val b=Bitmap.createBitmap(1800,700,Bitmap.Config.ARGB_8888);val canvas=Canvas(b);canvas.drawColor(Color.WHITE);val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.BLACK;textSize=62f}
  listOf("WBC 3.75  3.5-9.5","HGB 102  113-151","LDH 189  120-250").forEachIndexed{i,s->canvas.drawText(s,70f,120f+i*150,paint)}
  val recognizer=TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build());val latch=CountDownLatch(1);var raw="";var failure:Exception?=null
  recognizer.process(InputImage.fromBitmap(b,0)).addOnSuccessListener{raw=it.text;latch.countDown()}.addOnFailureListener{failure=it;latch.countDown()}
  assertTrue(latch.await(60,TimeUnit.SECONDS));recognizer.close();assertNull(failure)
  val rows=ReportParser.parse(raw);assertTrue("OCR output: $raw",rows.any{it.metricKey=="WBC"&&it.value==3.75});assertTrue(rows.any{it.metricKey=="LDH"})
 }
 @Test fun realUiCreatesAndReopensSymptom(){
  compose.onNodeWithText("症状记录").performClick();compose.onNodeWithText("症状名称").performTextInput("小腿酸痛")
  compose.onNodeWithText("备注 / 详细记录").performScrollTo().performTextInput("晚上明显")
  compose.activityRule.scenario.recreate()
  compose.onNodeWithText("小腿酸痛").assertExists()
  compose.onNodeWithText("晚上明显").assertExists()
  compose.onNodeWithText("保存记录").performClick();compose.waitForIdle()
  compose.onNodeWithText("记录",useUnmergedTree=true).performClick();compose.onNodeWithText("症状记录 · 小腿酸痛").performScrollTo().performClick()
  compose.onNodeWithText("小腿酸痛").assertExists();compose.onNodeWithText("晚上明显").assertExists()
  val file=File(context.getExternalFilesDir(null),"qa-symptom.png");InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let{bitmap->file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}}
  compose.onNodeWithContentDescription("关闭").performClick();assertEquals(1,HealthStore(context).entries().size)
 }
}
