#!/usr/bin/env python3
"""Compile actual storage/parser/backup code plus extracted non-Compose functions.
Requires JDK and KOTLIN_TEST_LIBS containing compiler.jar, stdlib.jar, reflect.jar,
script.jar, trove.jar, annotations.jar, json.jar, junit.jar, hamcrest.jar.
No Android runtime assertions: platform IO uses documented JVM doubles.
"""
import os, pathlib, subprocess, tempfile
root=pathlib.Path(__file__).resolve().parents[1]
src=root/'app/src/main/java/com/combustible12/healthtrend'
libs=pathlib.Path(os.environ['KOTLIN_TEST_LIBS'])
work=pathlib.Path(tempfile.mkdtemp(prefix='healthtrend-jvm-'))
def segment(file,start,end):
 text=(src/file).read_text();return text[text.index(start):text.index(end)]
(work/'Timeline.kt').write_text('package com.combustible12.healthtrend\n'+segment('MainActivity.kt','internal fun trendTimelineDates','@Composable fun Spark'))
(work/'Date.kt').write_text('package com.combustible12.healthtrend\nimport java.time.*\nimport java.time.format.DateTimeFormatter\n'+segment('MainActivity.kt','private val stamp=','fun ResultStatus.label'))
text=(src/'ReportImport.kt').read_text()
text=text[text.index('private val pastedMetadataLabel'): ] if 'private val pastedMetadataLabel' in text else text[text.index('private val pasted'):]
(work/'Import.kt').write_text('package com.combustible12.healthtrend\n'+text)
files=[src/(x+'.kt') for x in ['LabResultValues','BloodGroupProfile','PartialReferenceRanges','HealthModels','UnifiedMetricStandard','ReportParser','HealthStore','HistoryReplacement','ReportEditing','HealthBackup','LegacyReportCompatibility']]
files+=list((root/'ci/jvm').glob('*.kt'))+list(work.glob('*.kt'))
tests=[root/'app/src/test/java/com/combustible12/healthtrend'/ (x+'.kt') for x in ['LabTextResultsTest','BloodGroupProfileTest','UnifiedMetricStandardAuditTest','MetricImportRegressionTest','HistoryReplacementTest','PartialReferenceRangesTest','PartialRangeIntegrationTest']]
tests.append(root/'ci/jvm/ReportTakeoverTest.kt')
files.remove(root/'ci/jvm/ReportTakeoverTest.kt')
cp=os.pathsep.join(str(libs/x) for x in ['stdlib.jar','json.jar','junit.jar','hamcrest.jar','annotations.jar'])
compiler=os.pathsep.join(str(libs/x) for x in ['compiler.jar','stdlib.jar','reflect.jar','script.jar','trove.jar','annotations.jar','coroutines.jar'])
subprocess.run(['java','-cp',compiler,'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-jvm-target','17','-cp',cp,'-d',str(work/'tests.jar'),*map(str,files+tests)],check=True)
subprocess.run(['java','-cp',str(work/'tests.jar')+os.pathsep+cp+os.pathsep+str(root/'app/src/test/resources'),'org.junit.runner.JUnitCore',*['com.combustible12.healthtrend.'+p.stem for p in tests], 'com.combustible12.healthtrend.PartialRangeStorageTest'],check=True)
