package com.petcare.companionlite.data

import com.petcare.companionlite.data.model.*
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class CareScheduleTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val date = LocalDate.of(2026, 10, 7)
    private fun time(hour: Int, minute: Int = 0) = date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
    private fun reminder(id: Long, hour: Int, minute: Int = 0, petId: Long = 1) = Reminder(id, petId, TaskType.FEED, hour, minute, emptySet())
    private fun log(id: Long, hour: Int, minute: Int = 0, petId: Long = 1) = PetActivity(id, petId, TaskType.FEED, time(hour, minute), null)

    @Test fun fiveMinutesEarlyCompletesBreakfast() {
        assertEquals(1, computeTimeline(listOf(reminder(1,8)), listOf(log(1,7,55)), time(8), zone).completedCount)
    }
    @Test fun oneLogCannotCompleteBothMeals() {
        val result = computeTimeline(listOf(reminder(1,8),reminder(2,12)),listOf(log(1,12)), time(15), zone)
        assertEquals(1,result.completedCount)
        assertNull(result.items.first().matchedActivity)
        assertNotNull(result.items.last().matchedActivity)
    }
    @Test fun unrelatedPetCannotCompleteTask() {
        assertEquals(0,computeTimeline(listOf(reminder(1,8)),listOf(log(1,8,petId=2)),time(9),zone).completedCount)
    }
    @Test fun outsideEarlyWindowDoesNotCount() {
        assertEquals(0,computeTimeline(listOf(reminder(1,8)),listOf(log(1,6,29)),time(9),zone).completedCount)
    }
    @Test fun earlyBoundaryCounts() {
        assertEquals(1,computeTimeline(listOf(reminder(1,8)),listOf(log(1,6,30)),time(9),zone).completedCount)
    }
    @Test fun duplicateReminderTimesConsumeOneLogOnly() {
        assertEquals(1,computeTimeline(listOf(reminder(1,8),reminder(2,8)),listOf(log(1,8)),time(9),zone).completedCount)
    }
    @Test fun futureActivityDoesNotCompleteToday() {
        assertEquals(0,computeTimeline(listOf(reminder(1,8)),listOf(log(1,8)),time(7),zone).completedCount)
    }
    @Test fun explicitCompletionDoesNotCompleteAnotherReminder() {
        val activity=log(1,12).copy(reminderId=1,scheduledAt=time(8))
        val result=computeTimeline(listOf(reminder(1,8),reminder(2,12)),listOf(activity),time(13),zone)
        assertNotNull(result.items.first().matchedActivity)
        assertNull(result.items.last().matchedActivity)
    }
    @Test fun nextTriggerSkipsCurrentTimeAndHonorsWeekdays() {
        val friday=reminder(1,8).copy(repeatDays=setOf(5))
        assertEquals(date.plusDays(2).atTime(8,0).atZone(zone).toInstant().toEpochMilli(),nextTriggerAt(friday,time(8),zone))
    }
    @Test fun dstGapSchedulesAtValidLocalTime() {
        val ny=ZoneId.of("America/New_York")
        val start=LocalDate.of(2026,3,8).atStartOfDay(ny).toInstant().toEpochMilli()
        val next=nextTriggerAt(reminder(1,2,30),start,ny)!!
        assertEquals(3,java.time.Instant.ofEpochMilli(next).atZone(ny).hour)
    }
    @Test fun streakPreservesMoreThanThirtyDaysAndSurvivesMorning() {
        val days=(1L..400L).map{date.minusDays(it)}.toSet()
        assertEquals(400,countCareStreakFromDays(days,date))
        assertEquals(401,countCareStreakFromDays(days+date,date))
        assertEquals(0,countCareStreakFromDays(days-date.minusDays(1),date))
    }
    @Test fun missingAgeAndWeightStayUnknown() {
        val entity=PetProfile(name="Luna").toEntity()!!
        assertNull(entity.ageYears);assertNull(entity.weightKg)
    }
    @Test fun invalidWeightOrFutureBirthDateIsRejected() {
        assertNull(PetProfile(name="Luna",weightKg="NaN").toEntity())
        assertNull(PetProfile(name="Luna",weightKg="-3").toEntity())
        assertNull(PetProfile(name="Luna",birthDate=LocalDate.now().plusDays(1).toString()).toEntity())
    }
    @Test fun kittenAgeUsesMonths() { assertEquals(3L,PetProfile(birthDate="2026-07-07").ageMonths(date)) }
    @Test fun newReminderDoesNotInventMissedTasksBeforeCreation() {
        val created=reminder(1,8).copy(createdAt=time(10))
        assertTrue(computeTimeline(listOf(created),emptyList(),time(12),zone).items.isEmpty())
        val tomorrow=date.plusDays(1)
        assertEquals(1,computeTimeline(listOf(created),emptyList(),time(12)+86_400_000,zone,tomorrow).items.size)
    }
}
