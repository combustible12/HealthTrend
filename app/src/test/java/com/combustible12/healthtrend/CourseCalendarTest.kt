package com.combustible12.healthtrend

import org.junit.Test
import org.junit.Assert.*

class CourseCalendarTest {
    @Test fun navigationTrianglesShareTheInjectionMarkerTintAndInlineGraphic() {
        assertEquals(TrendEventShapeColor, SolidArrows.Tint)
        val label = SolidArrows.inlineLabel("左 ", " 右")
        assertEquals("左 ▸ 右", label.text)
        val annotation = label.getStringAnnotations(2, 3).single()
        assertEquals(SolidArrows.inlineContent.keys.single(), annotation.item)
    }

    @Test fun simultaneousTreatmentsKeepAllFourKindsWithoutDuplicates() {
        val record = CourseRecord(title = "3化1-放18", titleAuto = false, phase = "放化")
        val afterloading = TreatmentReminder(radiationNumber = 0, text = "", purpose = ReminderPurpose.BRACHYTHERAPY)
        val followUp = TreatmentReminder(radiationNumber = 0, text = "", purpose = ReminderPurpose.FOLLOW_UP)
        assertEquals(CalendarTreatment.values().toList(), calendarTreatments(record, listOf(afterloading, afterloading, followUp), true))
    }
    @Test fun ordinaryNotesAndGeneralRemindersDoNotCreateTreatmentDots() {
        val record = CourseRecord(title = "", phase = "观察", noteText = "记录饮食", titleAuto = false)
        assertTrue(calendarTreatments(record, listOf(TreatmentReminder(radiationNumber = 0, text = "带报告")), false).isEmpty())
    }
    @Test fun chemotherapyElapsedDaysDoNotMarkEveryDayAsTreatment() {
        assertTrue(calendarTreatments(CourseRecord(title = "3化18", phase = "观察"), emptyList(), false).isEmpty())
        assertTrue(calendarTreatments(CourseRecord(title = "3化18-放19", phase = "观察"), emptyList(), false).isEmpty())
        assertEquals(listOf(CalendarTreatment.RADIOTHERAPY), calendarTreatments(CourseRecord(title = "3化18-放19", phase = "放疗"), emptyList(), false))
    }
    @Test fun movingScheduledTreatmentsRemovesTheirOldDateDots() {
        val reminder = TreatmentReminder(radiationNumber = 0, text = "", purpose = ReminderPurpose.FOLLOW_UP)
        assertEquals(listOf(CalendarTreatment.FOLLOW_UP), calendarTreatments(null, listOf(reminder), false))
        assertTrue(calendarTreatments(null, emptyList(), false).isEmpty())
    }
}
