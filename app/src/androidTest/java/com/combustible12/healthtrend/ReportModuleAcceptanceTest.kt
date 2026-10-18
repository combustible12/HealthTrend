package com.combustible12.healthtrend

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.*
import org.junit.Assert.*

/** Current report flow on a real Android runtime, using synthetic records only. */
class ReportModuleAcceptanceTest {
 @get:Rule val compose=createComposeRule()
 private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
 @Before fun clear(){context.getSharedPreferences("healthtrend_store_v1",Context.MODE_PRIVATE).edit().clear().commit()}
 private fun draft()=pastedReportDraft("合成医院\n传染病筛查\n乙肝二对半(定性)\n2001-01-02\nHBSAG 乙肝表面抗原 阴性(-)  阴性(-)\nHBSAB 乙肝表面抗体 阳性(+)  阴性(-)")
 private fun noOverflow(node:SemanticsNodeInteraction){
  val layouts=mutableListOf<TextLayoutResult>()
  node.performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(layouts)}
  compose.runOnIdle{assertTrue(layouts.isNotEmpty());layouts.forEach{assertFalse("Overflow: ${it.layoutInput.text.text}; width=${it.didOverflowWidth}; height=${it.didOverflowHeight}; paragraph=${it.multiParagraph.width}x${it.multiParagraph.height}; ${it.layoutInput.constraints}; ${it.size}",it.hasVisualOverflow);assertFalse((0 until it.lineCount).any{line->it.isLineEllipsized(line)})}}
 }
 private fun screenshot(name:String){
  val instrumentation=InstrumentationRegistry.getInstrumentation()
  val automation=instrumentation.uiAutomation
  // Android 16 emulator Quickstep can ANR between test activities; it is not this app.
  android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("am force-stop com.android.launcher3")).use{it.readBytes()}
  compose.waitForIdle();instrumentation.waitForIdleSync();Thread.sleep(200)
  val image=automation.takeScreenshot() ?: error("Android screenshot unavailable")
  val bytes=java.io.ByteArrayOutputStream().also{image.compress(Bitmap.CompressFormat.PNG,100,it)}.toByteArray()
  val encoded=android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP)
  val chunks=encoded.chunked(2000)
  chunks.forEachIndexed{i,part->android.util.Log.i("REPORT_QA","$name:$i/${chunks.size}:$part")}
 }
 @Test fun reportListDeleteRequiresConfirmationAndRetainsOtherReports(){
  val store=HealthStore(context)
  val target=materializeReportDraft(draft(),store)
  val other=target.copy(id="other-report",hospitalKey="另一合成医院",results=target.results.map{it.copy(id="other-"+it.id,reportId="other-report",hospitalKey="另一合成医院")})
  store.saveReport(target);store.saveReport(other)
  var deletedId:String?=null
  compose.setContent{MaterialTheme{
   val reports=androidx.compose.runtime.remember{androidx.compose.runtime.mutableStateOf(store.reports())}
   Records(Modifier,reports.value,emptyList(),"全部",{},{},{},{},{},{},deleteReport={selected->
    deletedId=selected.id;store.deleteReport(selected.id);reports.value=store.reports()
   })
  }}
  compose.onAllNodesWithText("删除报告").onFirst().performScrollTo().performClick()
  compose.onNodeWithText("取消").performClick()
  compose.runOnIdle{assertEquals(2,HealthStore(context).reports().size)}
  compose.onAllNodesWithText("删除报告").onFirst().performScrollTo().performClick()
  compose.onNodeWithText("确认删除").performClick()
  compose.runOnIdle{
   val remaining=HealthStore(context).reports()
   assertEquals(1,remaining.size)
   assertNotNull(deletedId)
   val expected=if(deletedId==target.id)other else target
   assertEquals(expected,remaining.single())
  }
 }
 @get:Rule val testName=org.junit.rules.TestName()
 @After fun captureFinalState(){screenshot("final-"+testName.methodName)}
 private fun textTrend(scale:Float){
  val store=HealthStore(context);val report=materializeReportDraft(draft(),store)
  val values=listOf("阴性(-)","阳性(+)","++","+++","可疑，建议结合其他检查结果复核")
  val points=values.mapIndexed{i,t->report.copy(id="r$i",testedAtEpochMillis=report.testedAtEpochMillis+i*86400000L) to report.results.first().copy(id="x$i",textValue=t)}
  var clicked=""
  compose.setContent{CompositionLocalProvider(LocalDensity provides Density(1f,scale)){MaterialTheme{Box(Modifier.width(320.dp)){TextResultTrend(points){clicked=it.second.id}}}}}
  compose.onAllNodesWithText("阴性(-)",useUnmergedTree=true).assertCountEquals(0)
  compose.onAllNodesWithText("阳性(+)",useUnmergedTree=true).assertCountEquals(0)
  for(label in listOf("阴性","阳性","++","+++",values.last())){
   compose.onAllNodesWithText(label,useUnmergedTree=true).assertCountEquals(1)
   val node=compose.onAllNodesWithText(label,useUnmergedTree=true)[0]
   node.performScrollTo().assertIsDisplayed();noOverflow(node)
   if(label!=values.last()){
    val layouts=mutableListOf<TextLayoutResult>()
    node.performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(layouts)}
    compose.runOnIdle{layouts.forEach{assertEquals("Short result stays on one line: $label",1,it.lineCount)}}
   }
  }
  compose.onAllNodesWithText("+++",useUnmergedTree=true)[0].performScrollTo().performClick()
  compose.runOnIdle{assertEquals("x3",clicked)}
  screenshot("text-trend-$scale")
 }
 @Test fun qualitativeTrendNormalFont(){textTrend(1f)}
 @Test fun qualitativeTrendEnlargedFont(){textTrend(1.8f)}
 @Test fun longReferenceAndLatestResultWrapAtLargeFont(){
  val ref="参考范围：阴性；可疑结果请结合其他检查结果进行复核，不得以本项单独判断"
  val result="可疑，建议结合其他检查结果复核"
  compose.setContent{CompositionLocalProvider(LocalDensity provides Density(1f,1.8f)){MaterialTheme{Column(Modifier.width(180.dp)){TrendReferenceText(ref);TrendLatestSummary(result,"待判断",Muted);ResultValueUnit(result,"")}}}}
  noOverflow(compose.onNodeWithText(ref));compose.onAllNodesWithText(result,useUnmergedTree=true).assertCountEquals(2)
  for(i in 0..1)noOverflow(compose.onAllNodesWithText(result,useUnmergedTree=true)[i])
  screenshot("long-text-large-font")
 }
 @Test fun longNumericPrecisionAndComplexUnitRemainVisibleAtLargeFont(){
  compose.setContent{CompositionLocalProvider(LocalDensity provides Density(1f,1.8f)){MaterialTheme{Box(Modifier.width(180.dp)){ResultValueUnit("1234567890.000","mOsm/kgH2O")}}}}
  noOverflow(compose.onNodeWithText("1234567890.000"));noOverflow(compose.onNodeWithText("mOsm/kgH2O"))
  screenshot("numeric-complex-unit-large-font")
 }
 @Test fun sourceTypographyDoesNotCollapseSimilarCodesInEditor(){
  val store=HealthStore(context);var saved:LabReport?=null
  val source="合成医院\n生化\n2001-01-02\nCK - MB 2.340 U/L 0-9\nCKMB / CK 0.340 - 0-9\nUREA：CREA 0.050 - 0-9\nAG 2.340 mmol/L 0-9\nA / G 1.500 - 0-9"
  val imported=pastedReportDraft(source,store=store)
  assertTrue(reportValidationProblems(imported).toString(),imported.valid())
  assertEquals(listOf("CKMB","CKMB/CK","UREA/CREA","AG","A/G"),imported.rows.map{it.key})
  compose.setContent{MaterialTheme{ReportEditor(imported,store,{},{d->val r=materializeReportDraft(d,store);store.saveReport(r);saved=r},{},{})}}
  imported.rows.forEach{row->compose.onNodeWithText(row.name,useUnmergedTree=true).performScrollTo().assertIsDisplayed()}
  compose.onNodeWithText("保存",useUnmergedTree=true).assertIsEnabled().performClick()
  compose.runOnIdle{assertNotNull(saved);assertEquals(imported.rows.map{it.key},HealthStore(context).reports().single().results.map{it.metricKey})}
 }
 @Test fun numberedNameCannotReplaceComparatorResultInEditorAndStorage(){
  val store=HealthStore(context);var saved:LabReport?=null
  val imported=pastedReportDraft("合成医院\n肿瘤标志物\n2001-01-02\nCA724 糖类抗原CA72-4 <2.50 U/mL 0.00~15.00 U/mL",store=store)
  assertEquals("CA724",imported.rows.single().key)
  assertEquals("<2.50",imported.rows.single().text)
  compose.setContent{MaterialTheme{ReportEditor(imported,store,{},{d->val r=materializeReportDraft(d,store);store.saveReport(r);saved=r},{},{})}}
  compose.onNodeWithText("<2.50",useUnmergedTree=true).performScrollTo().assertIsDisplayed()
  screenshot("numbered-name-comparator-result")
  compose.onNodeWithText("保存").assertIsEnabled().performClick()
  compose.runOnIdle{
   assertNotNull(saved)
   val reopened=HealthStore(context).reports().single()
   assertEquals("<2.50",reopened.results.single().textValue)
   assertEquals("<2.50",pastedReportDraft(store.exportReportAsPastedText(reopened),store=store).rows.single().text)
  }
 }
 @Test fun ambiguousResultColumnsKeepSaveDisabled(){
  val store=HealthStore(context)
  val imported=pastedReportDraft("合成医院\n肿瘤标志物\n2001-01-02\nCA724 糖类抗原CA72-4 4 <2.50 U/mL 0.00~15.00",store=store)
  assertFalse(imported.valid());assertEquals("",imported.rows.single().text)
  compose.setContent{MaterialTheme{ReportEditor(imported,store,{},{error("Ambiguous result must not save")},{},{})}}
  compose.onNodeWithText("保存").assertIsNotEnabled()
  screenshot("ambiguous-result-save-disabled")
 }
 @Test fun namePunctuationKeepsCompleteIdentityAcrossEditorAndDisk(){
  assertSymbolicReport("肿瘤标志物","CA 72-4 糖类抗原CA72 - 4 <2.50 U/mL 0-9",listOf("CA724"))
 }
 @Test fun ratioAndGreekNamesKeepIdentityAcrossEditorAndDisk(){
  assertSymbolicReport("生化","A/G 白蛋白 ／ 球蛋白 1.250 - 0-9\nGGT Γ－谷氨酰转肽酶 2.500 U/L 0-9",listOf("A/G","GGT"))
 }
 @Test fun parenthesizedNameKeepsIdentityAcrossEditorAndDisk(){
  assertSymbolicReport("凝血功能","FDP 纤维蛋白（原）降解产物 <2.50 - 0-9",listOf("FDP"))
 }
 private fun assertSymbolicReport(type:String,rows:String,keys:List<String>){
  val store=HealthStore(context);var saved:LabReport?=null
  val source="合成医院\n$type\n2001-01-02\n$rows"
  val imported=pastedReportDraft(source,store=store)
  assertTrue(reportValidationProblems(imported).toString(),imported.valid())
  assertEquals(keys,imported.rows.map{it.key})
  compose.setContent{MaterialTheme{ReportEditor(imported,store,{},{d->val r=materializeReportDraft(d,store);store.saveReport(r);saved=r},{},{})}}
  imported.rows.forEach{compose.onNodeWithText(it.name,useUnmergedTree=true).performScrollTo().assertIsDisplayed()}
  compose.onNodeWithText("保存").assertIsEnabled().performClick()
  compose.runOnIdle{
   assertNotNull(saved)
   val reopened=HealthStore(context).reports().single()
   assertEquals(imported.rows.map{it.key},reopened.results.map{it.metricKey})
   assertEquals(imported.rows.map{it.text},reopened.results.map{it.textValue})
   assertEquals(imported.rows.map{it.text},pastedReportDraft(store.exportReportAsPastedText(reopened),store=store).rows.map{it.text})
  }
 }
 @Test fun unknownSymbolicNameCannotEnableSave(){
  val store=HealthStore(context)
  val imported=pastedReportDraft("合成医院\n生化\n2001-01-02\nALB 白蛋白 / UNKNOWN <2.50 g/L 0-9",store=store)
  assertFalse(imported.valid())
  compose.setContent{MaterialTheme{ReportEditor(imported,store,{},{error("Unregistered symbolic name must not save")},{},{})}}
  compose.onNodeWithText("保存").assertIsNotEnabled()
 }
 @Test fun unknownCompoundCannotBecomeKnownMetricOrEnableSave(){
  val store=HealthStore(context)
  val imported=pastedReportDraft("合成医院\n生化\n2001-01-02\nAST / UNKNOWN\t谷草转氨酶\t2.340\t-\t0-9",store=store)
  assertTrue(imported.rows.single().key.startsWith("待核对:"));assertEquals("谷草转氨酶",imported.rows.single().name);assertFalse(imported.valid())
  compose.setContent{MaterialTheme{ReportEditor(imported,store,{},{error("Unknown compound must not save")},{},{})}}
  compose.onNodeWithText("保存").assertIsNotEnabled()
  compose.onNodeWithText("编辑",useUnmergedTree=true).performScrollTo().performClick()
  compose.onNode(hasSetTextAction() and hasText("2.340")).performTextReplacement("3.450")
  compose.onNodeWithText("完成核对").assertIsNotEnabled()
  compose.onNodeWithContentDescription("关闭").performClick()
  compose.onNodeWithText("保存").assertIsNotEnabled()
  screenshot("unknown-compound-blocked")
  compose.onNodeWithText("编辑",useUnmergedTree=true).performScrollTo().performClick()
  compose.onNode(hasSetTextAction() and hasText(imported.rows.single().key)).performTextReplacement("AST")
  compose.onNodeWithText("完成核对").assertIsEnabled().performClick()
  compose.onNodeWithText("保存").assertIsEnabled()
 }
 @Test fun conflictingRatioNameCannotAutoResolveWhenEditorOpens(){
  val store=HealthStore(context)
  val imported=pastedReportDraft("合成医院\n生化\n2001-01-02\nAST/ALT 谷草转氨酶 2.340 - 0-9",store=store)
  assertTrue(imported.rows.single().key.startsWith("待核对:代码名称冲突:"))
  compose.setContent{MaterialTheme{ReportEditor(imported,store,{},{error("Conflicting identity must not save")},{},{})}}
  compose.onNodeWithText("保存").assertIsNotEnabled()
 }
 @Test fun enzymeRatiosRemainSeparateAndChineseThroughEditorSave(){
  val store=HealthStore(context);var saved:LabReport?=null
  val source="合成医院\n生化\n2001-01-02\nAST 24.00 U/L 0-40\nALT 12.00 U/L 0-40\nAST / ALT 2.000\nALT：AST 0.500"
  val imported=pastedReportDraft(source,store=store)
  assertTrue(reportValidationProblems(imported).toString(),imported.valid())
  assertEquals(listOf("AST","ALT","AST/ALT","ALT/AST"),imported.rows.map{it.key})
  compose.setContent{MaterialTheme{ReportEditor(imported,store,{},{d->val r=materializeReportDraft(d,store);store.saveReport(r);saved=r},{},{})}}
  listOf("谷草转氨酶","谷丙转氨酶","谷草/谷丙","谷丙/谷草").forEach{label->
   compose.onNodeWithText(label,useUnmergedTree=true).performScrollTo().assertIsDisplayed()
  }
  compose.onNodeWithText("保存",useUnmergedTree=true).assertIsEnabled().performClick()
  compose.runOnIdle{
   assertNotNull(saved)
   val reopened=HealthStore(context).reports().single()
   assertEquals(listOf("AST","ALT","AST/ALT","ALT/AST"),reopened.results.map{it.metricKey})
   val reimported=pastedReportDraft(store.exportReportAsPastedText(reopened),store=store)
   assertTrue(reimported.valid())
   assertEquals(imported.rows.map{listOf(it.key,it.name,it.text,it.unit,it.rangeRaw())},reimported.rows.map{listOf(it.key,it.name,it.text,it.unit,it.rangeRaw())})
  }
 }
 @Test fun editorSavesAndDetailSearchUsesOriginalDataWhileDisplayingChinese(){
  val store=HealthStore(context);var saved:LabReport?=null
  compose.setContent{MaterialTheme{ReportEditor(draft(),store,{},{d->val r=materializeReportDraft(d,store);store.saveReport(r);saved=r},{},{})}}
  compose.onNodeWithText("保存",useUnmergedTree=true).assertIsEnabled().performClick()
  compose.runOnIdle{assertNotNull(saved);assertEquals(saved,HealthStore(context).reports().single())}
 }
 @Test fun detailKeepsFormalTitleSearchAndRawExport(){
  val store=HealthStore(context);val report=materializeReportDraft(draft(),store);store.saveReport(report)
  compose.setContent{MaterialTheme{ReportDetail(report,store,{},{},{},{},{_,_,_->},{})}}
  compose.onNodeWithText("传染病筛查").assertExists();compose.onNodeWithText("乙肝二对半(定性)").assertExists()
  compose.onAllNodes(hasSetTextAction())[0].performScrollTo().performTextInput("HBSAB")
  compose.onNodeWithText("乙肝表面抗体").performScrollTo().assertIsDisplayed()
  compose.onNodeWithText("阳性").assertIsDisplayed()
  compose.onNodeWithText("阳性(+)").assertDoesNotExist()
  compose.runOnIdle{assertTrue(store.exportReportAsPastedText(report).contains("阳性(+)"));assertEquals(report,HealthStore(context).reports().single())}
  screenshot("detail-search")
 }
 @Test fun actualTrendsOpensOverallAndPointDialogWithoutRedundantSigns(){
  val store=HealthStore(context);val report=materializeReportDraft(draft(),store);store.saveReport(report)
  compose.setContent{MaterialTheme{Trends(Modifier,store,listOf(report),0,{_,_->},{},{_,_,_->},{},{})}}
  compose.onAllNodes(hasSetTextAction())[0].performTextInput("HBSAG")
  compose.onNodeWithText("整体").performScrollTo().performClick()
  compose.onNodeWithText("关闭").assertIsDisplayed()
  compose.onAllNodesWithText("阴性(-)",useUnmergedTree=true).assertCountEquals(0)
  compose.onAllNodes(hasText("阴性") and hasAnyAncestor(isDialog()),useUnmergedTree=true)[0].assertIsDisplayed()
  screenshot("overall-text-trend")
  compose.onAllNodes(hasText("阴性") and hasAnyAncestor(isDialog()),useUnmergedTree=true)[0].performClick()
  compose.onNodeWithText("编辑结果").assertIsDisplayed()
  compose.onNodeWithText("当次参考：阴性 · 符合参考").assertIsDisplayed()
  screenshot("point-dialog")
  compose.onNodeWithText("编辑结果").performClick()
  compose.onNode(hasSetTextAction() and hasText("阴性(-)")).performTextReplacement("+++")
  compose.onNodeWithText("保存").performClick()
  compose.onNode(hasText("+++") and hasAnyAncestor(isDialog())).assertIsDisplayed()
  compose.runOnIdle{assertEquals("+++",HealthStore(context).reports().single().results.first().textValue)}
 }
 @Test fun actualNumericTrendPointPreservesPrecisionAndOpensItsOwnReference(){
  val store=HealthStore(context)
  val reports=listOf("2001-01-02" to "100.000","2001-01-03" to "110.00").map{(day,value)->
   materializeReportDraft(pastedReportDraft("合成医院\n血常规\n$day\nHGB 血红蛋白 $value g/L 90-120"),store).also{store.saveReport(it)}
  }
  compose.setContent{MaterialTheme{Trends(Modifier,store,reports,0,{_,_->},{},{_,_,_->},{},{})}}
  compose.onAllNodes(hasSetTextAction())[0].performTextInput("HGB")
  val description=trendPointContentDescription("HGB",reports.first().testedAtEpochMillis)
  compose.onNodeWithContentDescription(description,useUnmergedTree=true).performScrollTo().performClick()
  compose.onNode(hasText("100.000") and hasAnyAncestor(isDialog())).assertIsDisplayed()
  compose.onNodeWithText("当次参考：90-120 · 范围内").assertIsDisplayed()
  screenshot("numeric-point-dialog")
  compose.onNodeWithText("关闭").performClick()
  compose.onNodeWithText("整体").performScrollTo().performClick()
  compose.onNodeWithText("整体趋势 · 血红蛋白").performScrollTo().assertIsDisplayed()
  screenshot("numeric-overall")
 }
 @Test fun deviceStoreEditReplacementExportReimportAndDeletePreserveIdentity(){
  val store=HealthStore(context);val original=materializeReportDraft(draft(),store);store.saveReport(original)
  store.updateResultText(original.id,original.results.last().id,"+++")
  val edited=HealthStore(context).reports().single();assertEquals(original.results.map{it.id},edited.results.map{it.id});assertEquals("+++",edited.results.last().textValue)
  val replacement=materializeReportDraft(draft().copy(rows=draft().rows.map{it.copy(text="阴性(-)")}),store)
  val event=store.replaceReportWithConfirmed(edited.id,replacement)
  assertEquals(edited,store.archivedReportReplacements().single().oldReport)
  val current=HealthStore(context).reports().single()
  val imported=pastedReportDraft(store.exportReportAsPastedText(current))
  assertEquals(current.results.map{listOf(it.metricKey,it.textValue,it.unitAtTest,it.referenceRaw)},imported.rows.map{listOf(it.key,it.text,it.unit,it.referenceRaw)})
  assertTrue(DataSearch.report("HBSAB 阴性",current));store.restoreArchivedReport(event.id)
  assertEquals(edited,HealthStore(context).reports().single());store.deleteReport(edited.id);assertTrue(HealthStore(context).reports().isEmpty())
 }
}
