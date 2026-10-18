package com.autominimal.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class WidgetLogicTest {
    private val zone = ZoneId.of("Europe/Amsterdam")
    private val start = LocalDateTime.of(2026, 10, 18, 23, 10)
    private val hhmm: (LocalDateTime) -> String = { it.format(DateTimeFormatter.ofPattern("HH:mm")) }

    private fun at(day: Int, hour: Int, minute: Int = 0) =
        LocalDateTime.of(2026, 10, 18 + day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun rainThatStopsSaysWhen() {
        val outlook = rainOutlook(listOf(0.4f, 0.4f) + List(11) { 0f }, start, "Drizzle", hhmm)
        assertEquals(12, outlook.slots.size)
        assertEquals(listOf("Now", "23:40", "00:10", "00:40", "01:10", "01:40", "02:10"), outlook.labels)
        assertEquals("Drizzle stops around 23:40", outlook.headline)
        assertEquals("Dry from 23:40", outlook.short)
    }

    @Test
    fun dryThenRainSaysWhenItStarts() {
        val outlook = rainOutlook(listOf(0f, 0f, 0f, 2f, 2f, 2f, 0f, 0f, 0f), start, null, hhmm)
        assertEquals("Rain from around 23:55", outlook.headline)
        assertEquals("No rain for the next 3 hours", rainOutlook(List(13) { 0f }, start, null, hhmm).headline)
    }

    @Test
    fun rainBarsGrowWithIntensity() {
        assertEquals(0f, rainHeight(0.05f))
        assertEquals(0.5f, rainHeight(1f), 0.001f)
        assertEquals(1f, rainHeight(50f))
    }

    private val personal = CalendarSource(1, "Personal", "you@gmail.com", own = true)
    private val team = CalendarSource(3, "Team out of office", "Shared with you", own = false)

    private fun agenda(vararg events: CalendarEvent) =
        Agenda(listOf(personal, team), events.toList(), at(0, 0), 7, zone)

    @Test
    fun theFirstBusyDayIsShownAndTheNextOneIsNamed() {
        val pool = CalendarEvent(10, "Pool", at(1, 7, 15), at(1, 8), false, listOf(1))
        val sofia = CalendarEvent(11, "Sofia", at(1, 0), at(3, 0), true, listOf(3))
        val office = CalendarEvent(12, "Office", at(3, 0), at(4, 0), true, listOf(1))
        val a = agenda(pool, sofia, office)
        val now = at(0, 23, 8)
        assertEquals(1, a.firstBusyDay(now))
        val monday = a.day(1, now)
        assertEquals(listOf(sofia), monday.allDay)
        assertEquals(listOf(pool), monday.timed)
        // Sofia is still away on Tuesday, but nothing new starts until Wednesday.
        assertEquals(3, monday.nextBusy)
        assertEquals(listOf(3L), a.calendarsOn(2, now))
        assertEquals(pool, a.next(now))
    }

    @Test
    fun endedEventsDropOffToday() {
        val lunch = CalendarEvent(1, "Lunch", at(0, 12), at(0, 13), false, listOf(1))
        assertEquals(listOf(lunch), agenda(lunch).day(0, at(0, 12, 30)).timed)
        assertEquals(emptyList<CalendarEvent>(), agenda(lunch).day(0, at(0, 13, 30)).timed)
    }

    @Test
    fun duplicatesCombineButKeepEveryCalendar() {
        val a = CalendarEvent(1, "Office", at(1, 0), at(2, 0), true, listOf(1))
        val b = a.copy(id = 2, title = "office ", calendars = listOf(3))
        val merged = mergeDuplicates(listOf(a, b))
        assertEquals(1, merged.size)
        assertEquals(listOf(1L, 3L), merged.single().calendars)
    }

    @Test
    fun theNextEventCountsDown() {
        val now = at(0, 23, 8)
        assertEquals("in 8 h", startsIn(at(1, 7, 15), now))
        assertEquals("in 22 min", startsIn(at(0, 23, 30), now))
        assertEquals("Now", startsIn(now, now))
        assertNull(startsIn(at(2, 9), now))
    }

    @Test
    fun callLinksAreRecognised() {
        assertEquals("Google Meet", callService(null, "Join: https://meet.google.com/abc-defg-hij"))
        assertEquals("Zoom", callService("https://us02web.zoom.us/j/123", null))
        assertNull(callService("De Bosuil", "Bring a towel"))
    }
}
