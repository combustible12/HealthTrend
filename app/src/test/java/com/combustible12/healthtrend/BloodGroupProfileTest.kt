package com.combustible12.healthtrend

import org.junit.Assert.*
import org.junit.Test

class BloodGroupProfileTest {
 @Test fun oldInstallationStartsEmpty(){assertTrue(BloodGroupProfile.fromJson(null).isEmpty)}
 @Test fun allFourFieldsRoundTrip(){val p=BloodGroupProfile("AB","阳性","阴性","阳性","2026-01-15");assertEquals(p,BloodGroupProfile.fromJson(p.toJson().toString()))}
 @Test fun missingOptionalFieldsStayEmpty(){assertEquals(BloodGroupProfile("O"),BloodGroupProfile.fromJson("{\"abo\":\"O\"}"))}
 @Test fun editingOneFieldPreservesOthers(){val p=BloodGroupProfile("A","阴性","阳性","阴性");assertEquals(p.copy(abo="B"),BloodGroupProfile.fromJson(p.copy(abo="B").toJson().toString()))}
 @Test fun clearingAllFieldsIsAllowed(){assertTrue(BloodGroupProfile().validated().isEmpty)}
 @Test fun whitespaceIsTrimmed(){assertEquals("B",BloodGroupProfile(" B ").validated().abo)}
 @Test(expected=IllegalArgumentException::class) fun invalidAboRejected(){BloodGroupProfile("unknown").validated()}
 @Test(expected=IllegalArgumentException::class) fun invalidRhRejected(){BloodGroupProfile(rhD="2+").validated()}
 @Test fun dateOnlyProfileIsNotEmpty(){assertFalse(BloodGroupProfile(testedDate="2026-01-15").validated().isEmpty)}
 @Test fun editingBloodGroupPreservesDate(){assertEquals("2026-01-15",BloodGroupProfile(testedDate="2026-01-15").copy(abo="O").validated().testedDate)}
 @Test(expected=IllegalArgumentException::class) fun invalidDateRejected(){BloodGroupProfile(testedDate="2026-02-30").validated()}
}
