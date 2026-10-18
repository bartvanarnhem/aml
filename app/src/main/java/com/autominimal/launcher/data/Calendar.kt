package com.autominimal.launcher.data

import java.time.Instant
import java.time.ZoneId

/** One calendar on the device, as listed under Calendar options. */
data class CalendarSource(val id: Long, val name: String, val account: String, val own: Boolean)

/** One occurrence. All-day events run from local midnight to local midnight. */
data class CalendarEvent(
    val id: Long,
    val title: String,
    val start: Long,
    val end: Long,
    val allDay: Boolean,
    /** Every calendar it is in; more than one when duplicates were combined. */
    val calendars: List<Long>,
    val location: String? = null,
    /** "Google Meet", "Zoom" or "Teams" when the event has a call link. */
    val call: String? = null,
) {
    /** Zero-length events still occupy their start moment. */
    val until get() = maxOf(end, start + 1)
}

/**
 * Events from the calendars that are switched on, for [days] days starting at [start] (local midnight today).
 * [calendars] lists every calendar, on or off, so each keeps its colour.
 */
data class Agenda(
    val calendars: List<CalendarSource>,
    val events: List<CalendarEvent>,
    val start: Long,
    val days: Int,
    val zone: ZoneId = ZoneId.systemDefault(),
) {
    fun dayStart(index: Int): Long =
        Instant.ofEpochMilli(start).atZone(zone).toLocalDate().plusDays(index.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()

    /** Events on day [index], all-day ones apart. Timed events that have ended are gone. */
    fun day(index: Int, now: Long): AgendaDay {
        val from = dayStart(index)
        val to = dayStart(index + 1)
        val on = events.filter { it.start < to && it.until > from && (it.allDay || it.end > now) }.sortedBy { it.start }
        val next = (index + 1 until days).firstOrNull { j ->
            val s = dayStart(j)
            val e = dayStart(j + 1)
            events.any { it.start in s until e }
        }
        return AgendaDay(index, on.filter { it.allDay }, on.filterNot { it.allDay }, next)
    }

    /** The first day with something on it, or today. */
    fun firstBusyDay(now: Long): Int = (0 until days).firstOrNull { !day(it, now).isEmpty } ?: 0

    /** Calendars with something on day [index], in list order, for the dots in the week strip. */
    fun calendarsOn(index: Int, now: Long): List<Long> {
        val day = day(index, now)
        val ids = (day.allDay + day.timed).flatMap { it.calendars }.toSet()
        return calendars.map { it.id }.filter { it in ids }
    }

    /** The next timed event that hasn't ended yet. */
    fun next(now: Long): CalendarEvent? = events.filter { !it.allDay && it.end > now }.minByOrNull { it.start }

    /** Position of [calendarId] in [calendars], for its colour. */
    fun colorIndex(calendarId: Long): Int = calendars.indexOfFirst { it.id == calendarId }.coerceAtLeast(0)
}

data class AgendaDay(
    val index: Int,
    val allDay: List<CalendarEvent>,
    val timed: List<CalendarEvent>,
    /** The next day after this one on which something starts, or null within the look-ahead. */
    val nextBusy: Int?,
) {
    val isEmpty get() = allDay.isEmpty() && timed.isEmpty()
}

/** "Now", "in 25 min", "in 8 h"; null a day or more ahead. */
fun startsIn(start: Long, now: Long): String? {
    if (start <= now) return "Now"
    val minutes = (start - now) / 60_000
    return when {
        minutes < 60 -> "in ${minutes.coerceAtLeast(1)} min"
        minutes < 24 * 60 -> "in ${minutes / 60} h"
        else -> null
    }
}

/** Combines events with the same title and times from different calendars, keeping every calendar. */
fun mergeDuplicates(events: List<CalendarEvent>): List<CalendarEvent> =
    events.groupBy { listOf(it.title.trim().lowercase(), it.start, it.end, it.allDay) }.values.map { same ->
        same.first().copy(calendars = same.flatMap { it.calendars }.distinct())
    }

/** The video service an event links to, from its location or description. */
fun callService(vararg texts: String?): String? {
    val all = texts.filterNotNull().joinToString(" ").lowercase()
    return when {
        "meet.google.com" in all -> "Google Meet"
        "zoom.us" in all -> "Zoom"
        "teams.microsoft.com" in all || "teams.live.com" in all -> "Teams"
        else -> null
    }
}
