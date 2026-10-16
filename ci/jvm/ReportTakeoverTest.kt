package com.combustible12.healthtrend

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.URI

/** Synthetic records only. Runs with ci/jvm disk-backed Android platform doubles. */
class ReportTakeoverTest {
 @Test fun bloodGroupSaveReopenExportAndBackupPreserveAllFields(){
  val c=Context();val s=HealthStore(c);val prefs=c.getSharedPreferences("healthtrend_store_v1",0)
  prefs.edit().putString("course_records","[]").putString("reports","[]").commit()
  s.savePatientProfile(PatientProfile(name="合成资料",bloodType="旧血型原文"))
  val before=prefs.getString("reports",null)
  val p=BloodGroupProfile("AB","阳性","阴性","阳性","2026-01-15")
  s.saveBloodGroupProfile(p)
  assertEquals(p,HealthStore(Context(c.root)).bloodGroupProfile())
  assertEquals("旧血型原文",s.patientProfile().bloodType)
  assertEquals(before,prefs.getString("reports",null));assertEquals("[]",prefs.getString("course_records",null))
  assertEquals(p,BloodGroupProfile.fromJson(JSONObject(s.exportBackup()).getJSONObject("bloodGroupProfile").toString()))
  val zip=File(c.cacheDir,"blood.zip");HealthBackup(c,s).export(Uri.fromFile(zip))
  val dest=Context();HealthBackup(dest,HealthStore(dest)).restoreIntoEmpty(Uri.fromFile(zip))
  assertEquals(p,HealthStore(Context(dest.root)).bloodGroupProfile())
  s.saveBloodGroupProfile(BloodGroupProfile());assertTrue(s.bloodGroupProfile().isEmpty)
 }
 @Test fun productionReconciliationMarksExplicitCodeNameConflictsForReview(){
  val store=HealthStore(Context())
  val input="合成医院\n血常规\n2026-10-16\nAST/ALT 谷丙/谷草 0.82"
  val draft=pastedReportDraft(input,store=store)
  assertTrue(draft.rows.single().key.startsWith("待核对:代码名称冲突:AST/ALT"))
  assertFalse(draft.valid())
 }
 private fun seed(context:Context):HealthStore {
  val results=JSONArray()
  listOf(Triple("CUSTOM-synthetic","白细胞计数","10^9/L"),Triple("PDW","血小板分布宽度","fL"),Triple("HCT","红细胞压积","%"))
   .forEachIndexed{i,(key,name,unit)->results.put(JSONObject().put("id","result-$i").put("key",key).put("raw",name)
    .put("value",if(i==2)32.30 else 5.0).put("text",if(i==2)"32.30" else "5.00")
    .put("unit",unit).put("low",1.25).put("high",50.75).put("edited",false))}
  val report=JSONObject().put("id","legacy-report").put("hospital","合成医院").put("type","血常规")
   .put("date",1760000000123L).put("tv",7).put("ocr","synthetic source").put("system","legacy")
   .put("images",JSONArray().put(JSONObject().put("uri",Uri.fromFile(File(context.filesDir,"sources/source.image")).toString()).put("page",0).put("at",123L)))
   .put("results",results)
  val image=File(context.filesDir,"sources/source.image");image.parentFile.mkdirs();image.writeBytes(byteArrayOf(3,1,4,1,5))
  context.getSharedPreferences("healthtrend_store_v1",0).edit()
   .putString("reports",JSONArray().put(report).toString()).putString("templates","[]")
   .putString("course_records","[]").commit()
  return HealthStore(context)
 }
 @Test fun readSearchAndReopenNeverRewriteRawPreferences(){
  val c=Context();val s=seed(c);val prefs=c.getSharedPreferences("healthtrend_store_v1",0)
  val before=prefs.getString("reports",null)
  repeat(3){s.reports();s.unifiedMetricPool();HealthStore(c).reports()}
  assertEquals(before,prefs.getString("reports",null));assertEquals("[]",prefs.getString("templates",null))
  assertEquals("CUSTOM-synthetic",s.reportPoolIdentity("CUSTOM-synthetic"))
 }
 @Test fun editorPreservesEveryUntouchedFieldAndEditsOnlyChosenResult(){
  val c=Context();val s=seed(c);val original=s.reports().single()
  val noOp=materializeReportDraft(ReportDraft.from(original),s)
  assertEquals(original,noOp)
  val draft=ReportDraft.from(original).let{d->d.copy(rows=d.rows.map{if(it.key=="HCT")it.copy(text="33.00")else it})}
  s.saveReport(materializeReportDraft(draft,s))
  val saved=HealthStore(c).reports().single()
  assertEquals(original.id,saved.id);assertEquals(original.sourceImages,saved.sourceImages)
  assertEquals(original.templateVersion,saved.templateVersion);assertEquals(original.testedAtEpochMillis,saved.testedAtEpochMillis)
  assertEquals(original.results.take(2),saved.results.take(2));assertEquals("33.00",saved.results.last().textValue)
  assertEquals(original.results.last().unitAtTest,saved.results.last().unitAtTest)
  assertEquals(original.results.last().referenceLowAtTest,saved.results.last().referenceLowAtTest)
  assertEquals(original.results.last().referenceHighAtTest,saved.results.last().referenceHighAtTest)
 }
 @Test fun identityLinksAreExplicitAuditedReversibleAndLeaveHistoryUntouched(){
  val c=Context();val s=seed(c);val before=s.reports()
  val initial=s.reportIdentityCandidates().single()
  assertEquals("CUSTOM-synthetic",initial.source);assertEquals(listOf("白细胞计数"),initial.sourceNames)
  assertEquals(listOf("10^9/L"),initial.sourceUnits);assertNull(initial.activeTarget)
  assertTrue(s.reportIdentityLinks().isEmpty());assertEquals(before,s.reports())
  assertEquals("CUSTOM-synthetic",s.reportPoolIdentity("CUSTOM-synthetic"))
  s.confirmReportIdentityLink("CUSTOM-synthetic","WBC")
  assertEquals("WBC",s.reportPoolIdentity("CUSTOM-synthetic"));assertEquals(before,s.reports())
  assertTrue(s.unifiedMetricPool().single{it.key=="WBC"}.linkedLegacyKeys.contains("CUSTOM-synthetic"))
  val event=s.reportIdentityLinks().single();assertTrue(event.confirmedAt>0)
  assertEquals(listOf(before.single().id),event.reportIds);assertEquals(listOf(before.single().results.first().id),event.resultIds)
  assertEquals(listOf("白细胞计数"),event.sourceNames);assertEquals(listOf("10^9/L"),event.sourceUnits)
  assertEquals("WBC",s.reportIdentityCandidates().single().activeTarget)
  s.revokeReportIdentityLink(event.id)
  assertEquals("CUSTOM-synthetic",s.reportPoolIdentity("CUSTOM-synthetic"));assertNotNull(s.reportIdentityLinks().single().revokedAt)
  assertNull(s.reportIdentityCandidates().single().activeTarget);assertEquals(before,s.reports())
  assertTrue(runCatching{s.confirmReportIdentityLink("CUSTOM-synthetic","PDW")}.isFailure)
  assertTrue(runCatching{s.confirmReportIdentityLink("PDW","PDW-SD")}.isFailure)
 }
 @Test fun identityLinkCanBeReconfirmedOnlyAfterRevocationAndKeepsEventHistory(){
  val s=seed(Context());s.confirmReportIdentityLink("CUSTOM-synthetic","WBC")
  assertTrue(runCatching{s.confirmReportIdentityLink("CUSTOM-synthetic","HGB")}.isFailure)
  s.revokeReportIdentityLink(s.reportIdentityLinks().single().id)
  s.confirmReportIdentityLink("CUSTOM-synthetic","WBC")
  assertEquals(2,s.reportIdentityLinks().size);assertNotNull(s.reportIdentityLinks().first().revokedAt)
  assertNull(s.reportIdentityLinks().last().revokedAt);assertEquals("WBC",s.reportPoolIdentity("custom-synthetic"))
 }
 @Test fun confirmedReimportAtomicallyArchivesReplacesAndRestoresOldReport(){
  val c=Context();val s=seed(c);val old=s.reports().single()
  val parsed=ReportParser.parse("WBC 白细胞计数 6.20 10^9/L 3.5-9.5")
  val fresh=s.buildReportFromPool("合成医院","血常规",old.testedAtEpochMillis,emptyList(),parsed,"confirmed reimport")
  assertEquals(listOf(old.id),s.replacementCandidates(fresh).map{it.id})
  val event=s.replaceReportWithConfirmed(old.id,fresh)
  val active=s.reports().single()
  assertEquals(old.id,active.id);assertEquals("6.20",active.results.single().textValue)
  assertEquals(old.sourceImages,active.sourceImages)
  assertEquals(old,s.archivedReportReplacements().single().oldReport)
  assertNull(event.restoredAt)
  s.restoreArchivedReport(event.id)
  assertEquals(old,s.reports().single());assertNotNull(s.archivedReportReplacements().single().restoredAt)
 }
 @Test fun failedReplacementLeavesOldReportAndArchiveUntouched(){
  val c=Context();val s=seed(c);val old=s.reports().single()
  val invalid=old.copy(id="new",results=old.results.map{it.copy(reportId="new",metricKey="未识别")})
  assertTrue(runCatching{s.replaceReportWithConfirmed(old.id,invalid)}.isFailure)
  assertEquals(old,s.reports().single());assertTrue(s.archivedReportReplacements().isEmpty())
 }
 @Test fun backupAndEmptyRestoreKeepReplacementArchive(){
  val c=Context();val s=seed(c);val old=s.reports().single()
  val fresh=s.buildReportFromPool("合成医院","血常规",old.testedAtEpochMillis,emptyList(),ReportParser.parse("WBC 白细胞计数 6.20 10^9/L 3.5-9.5"))
  s.replaceReportWithConfirmed(old.id,fresh)
  val zip=File.createTempFile("replacement-archive-",".zip");HealthBackup(c,s).export(Uri.fromFile(zip))
  val destination=Context();HealthBackup(destination,HealthStore(destination)).restoreIntoEmpty(Uri.fromFile(zip))
  val reopened=HealthStore(Context(destination.root))
  val archived=reopened.archivedReportReplacements().single().oldReport
  assertEquals(old.copy(sourceImages=archived.sourceImages),archived)
  assertTrue(archived.sourceImages.all{File(URI(it.uri)).exists()})
  assertEquals("6.20",reopened.reports().single().results.single().textValue)
  zip.delete()
 }
 @Test fun actualZipBackupRestoresIntoIndependentEmptyDirectory(){
  val c=Context();val s=seed(c);s.confirmReportIdentityLink("CUSTOM-synthetic","WBC")
  val before=s.reports().single();val zip=File.createTempFile("synthetic-backup-",".zip")
  val uri=Uri.fromFile(zip);val exported=HealthBackup(c,s).export(uri);assertTrue(exported.files>=2)
  val destination=Context();val empty=HealthStore(destination)
  assertTrue(empty.reports().isEmpty());HealthBackup(destination,empty).restoreIntoEmpty(uri)
  // Android requires process restart; a fresh double drops its preference cache here.
  val reopened=HealthStore(Context(destination.root));val restored=reopened.reports().single()
  assertEquals(before.copy(sourceImages=restored.sourceImages),restored)
  assertEquals(s.reportIdentityLinks(),reopened.reportIdentityLinks())
  assertArrayEquals(byteArrayOf(3,1,4,1,5),File(Uri.parse(restored.sourceImages.single().uri).path!!).readBytes())
  assertEquals(before.sourceImages.single().importedAtEpochMillis,restored.sourceImages.single().importedAtEpochMillis)
  assertTrue(runCatching{HealthBackup(c,s).restoreIntoEmpty(uri)}.isFailure)
  zip.delete()
 }
 @Test fun restoreNeverOverwritesAnExistingWeightRecord(){
  val source=Context();val s=seed(source);val zip=File.createTempFile("weight-guard-synthetic-",".zip")
  HealthBackup(source,s).export(Uri.fromFile(zip))
  val destination=Context();val store=HealthStore(destination);val weight=WeightRecord(kilograms=60.25)
  store.saveWeight(weight)
  assertTrue(runCatching{HealthBackup(destination,store).restoreIntoEmpty(Uri.fromFile(zip))}.isFailure)
  assertEquals(listOf(weight),store.weightRecords());zip.delete()
 }
 @Test fun damagedBackupFailsBeforeWritingIntoEmptyDestination(){
  val c=Context();val s=seed(c);val good=File.createTempFile("good-synthetic-",".zip")
  HealthBackup(c,s).export(Uri.fromFile(good))
  val bad=File.createTempFile("bad-synthetic-",".zip")
  java.util.zip.ZipInputStream(good.inputStream()).use{input->
   java.util.zip.ZipOutputStream(bad.outputStream()).use{out->
    while(true){val entry=input.nextEntry?:break;val bytes=input.readBytes()
     out.putNextEntry(java.util.zip.ZipEntry(entry.name));out.write(if(entry.name.endsWith("source.image"))byteArrayOf(9)else bytes);out.closeEntry()
    }
   }
  }
  val destination=Context();val empty=HealthStore(destination)
  assertTrue(runCatching{HealthBackup(destination,empty).restoreIntoEmpty(Uri.fromFile(bad))}.isFailure)
  assertTrue(empty.reports().isEmpty());assertFalse(File(destination.filesDir,"sources/source.image").exists())
  good.delete();bad.delete()
 }
 @Test fun backupRetainsRawLegacyTemplatesAndSafetySnapshots(){
  val c=Context();val s=seed(c);val prefs=c.getSharedPreferences("healthtrend_store_v1",0)
  val field=JSONObject().put("key","WBC").put("name","合成旧名称").put("unit","arbitrary").put("low",999).put("high",1000)
  val templates=JSONArray().put(JSONObject().put("hospital","合成医院").put("type","血常规").put("version",3).put("confirmed",true).put("fields",JSONArray().put(field))).toString()
  prefs.edit().putString("templates",templates).commit()
  val d=ReportDraft.from(s.reports().single());s.saveReport(materializeReportDraft(d.copy(hospital="合成来源二"),s))
  val safety=c.getSharedPreferences("healthtrend_safety_snapshots",0).getString("reports:previous",null)
  assertNotNull(safety);assertEquals(templates,prefs.getString("templates",null))
  val zip=File.createTempFile("legacy-synthetic-",".zip");HealthBackup(c,s).export(Uri.fromFile(zip))
  val destination=Context();HealthBackup(destination,HealthStore(destination)).restoreIntoEmpty(Uri.fromFile(zip))
  val reopened=Context(destination.root)
  assertEquals(templates,reopened.getSharedPreferences("healthtrend_store_v1",0).getString("templates",null))
  assertEquals(safety!!.replace(c.filesDir.canonicalPath,destination.filesDir.canonicalPath),reopened.getSharedPreferences("healthtrend_safety_snapshots",0).getString("reports:previous",null))
  zip.delete()
 }
 @Test fun explicitDifferentMeasurementNameCannotBeLinked(){
  for((name,target) in listOf("中性粒细胞百分比" to "NEUT#","谷丙/谷草" to "AST/ALT","红细胞分布宽度SD" to "RDW-CV")){
   val c=Context();val s=seed(c);val prefs=c.getSharedPreferences("healthtrend_store_v1",0)
   val raw=JSONArray(prefs.getString("reports","[]"));raw.getJSONObject(0).getJSONArray("results").getJSONObject(0).put("raw",name)
   prefs.edit().putString("reports",raw.toString()).commit()
   assertTrue("$name -> $target",runCatching{s.confirmReportIdentityLink("CUSTOM-synthetic",target)}.isFailure)
  }
 }
 @Test fun newClipboardReportNeedsNoTemplateAndDoesNotCreateOne(){
  val c=Context();val s=HealthStore(c)
  val d=pastedReportDraft("合成医院\n血常规\n2026-10-16\nWBC 白细胞计数 5.00 10^9/L 1.25-9.75",store=s)
  s.saveReport(materializeReportDraft(d,s));assertEquals("5.00",s.reports().single().results.single().textValue)
  assertTrue(s.templates().isEmpty());assertNull(s.reports().single().templateVersion)
 }
}

/** Real storage and clipboard roundtrip with disk-backed platform doubles. */
class PartialRangeStorageTest {
 @Test fun saveReopenEditExportAndUpgradePreserveEvidenceAndCourse(){
  val context=Context();val store=HealthStore(context)
  val prefs=context.getSharedPreferences("healthtrend_store_v1",0)
  prefs.edit().putString("course_records","[]").commit()
  val draft=pastedReportDraft("合成医院\n血常规\n2026-10-16\nPCT 血小板压积 0.250 ↑ % 0.188-0.2??",store=store)
  assertTrue(reportValidationProblems(draft).toString(),draft.valid())
  store.saveReport(materializeReportDraft(draft,store))
  val reopened=HealthStore(Context(context.root)).reports().single()
  assertEquals("0.188-0.2??",reopened.results.single().referenceRaw)
  assertEquals("↑",reopened.results.single().reportFlag)
  assertTrue(store.standardRanges("PCT").isEmpty())
  assertTrue(store.referenceRangeHistory("PCT").isEmpty())
  val edited=ReportDraft.from(reopened).let{it.copy(rows=it.rows.map{r->r.copy(text="0.260")})}
  store.saveReport(materializeReportDraft(edited,store))
  val saved=HealthStore(context).reports().single()
  assertEquals("0.188-0.2??",saved.results.single().referenceRaw)
  val imported=pastedReportDraft(store.exportReportAsPastedText(saved),store=store)
  assertEquals("0.188-0.2??",imported.rows.single().parsed().referenceRaw)
  assertEquals("↑",imported.rows.single().reportFlag)
  assertEquals("[]",prefs.getString("course_records",null))
  val before=prefs.getString("reports",null)
  HealthStore(context).reports();assertEquals(before,prefs.getString("reports",null))
 }
}
