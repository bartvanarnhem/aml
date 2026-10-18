package com.autominimal.launcher

import android.content.Context
import android.content.pm.ApplicationInfo
import com.autominimal.launcher.data.Agenda
import com.autominimal.launcher.data.CalendarEvent
import com.autominimal.launcher.data.CalendarSource
import com.autominimal.launcher.data.Channel
import com.autominimal.launcher.data.Person
import java.io.File

/**
 * Placeholder people, events and apps for README screenshots. Only in debuggable builds, while
 * `files/demo` exists: `adb shell run-as com.autominimal.launcher touch files/demo`.
 * Writing "setup" into the file also shows the first-run screen with nothing allowed yet.
 */
object Demo {
    private fun marker(context: Context) = File(context.filesDir, "demo")

    fun enabled(context: Context): Boolean =
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 && marker(context).exists()

    fun setup(context: Context): Boolean = enabled(context) && runCatching { "setup" in marker(context).readText() }.getOrDefault(false)

    val apps = setOf(
        "com.android.chrome", "com.whatsapp", "com.spotify.music", "com.google.android.youtube",
        "com.google.android.apps.maps", "com.google.android.gm", "com.google.android.apps.photos",
        "com.google.android.calendar", "com.Slack", "org.thoughtcrime.securesms", "org.telegram.messenger",
        "com.google.android.keep", "com.linkedin.android", "com.booking", "com.google.android.apps.youtube.music",
        "com.google.android.apps.messaging", "com.google.android.dialer", "com.google.android.contacts",
        "com.android.deskclock", "com.google.android.calculator", "com.google.android.apps.docs",
        "com.google.android.apps.translate", "com.android.vending", "com.android.settings", "com.mediatek.camera",
        "com.google.android.apps.walletnfcrel", "com.kobobooks.android", "com.ubercab.eats", "com.google.android.apps.fitness",
    )

    val work = listOf("com.Slack", "com.google.android.gm", "com.google.android.calendar", "com.google.android.apps.docs")

    private val names = listOf(
        "Emma Johnson", "Liam Smith", "Family", "Olivia Brown", "Noah Williams", "Book club",
        "Sophia Miller", "Lucas Davis", "Mia Wilson", "Ethan Moore", "Ava Taylor", "James Anderson",
    )

    /** The real ranking with placeholder names; "Family" becomes a WhatsApp group so a chat shows up too. */
    fun person(index: Int, real: Person): Person {
        val name = names[index % names.size]
        return real.copy(name = name, channel = if (name == "Family") Channel.WHATSAPP else real.channel)
    }

    const val PLACE = "Amsterdam"

    /** A made-up week in place of the real calendars, from today's midnight in [real]. */
    fun agenda(real: Agenda): Agenda {
        val calendars = listOf(
            CalendarSource(9001, "Personal", "you@example.com", own = true),
            CalendarSource(9002, "Work", "you@work.example.com", own = true),
            CalendarSource(9003, "Family", "you@example.com", own = false),
        )
        val hour = 3_600_000L
        fun at(day: Int, h: Double) = real.dayStart(day) + (h * hour).toLong()
        var id = 9100L
        fun event(day: Int, from: Double, to: Double, title: String, calendar: Long, location: String? = null, call: String? = null) =
            CalendarEvent(id++, title, at(day, from), at(day, to), false, listOf(calendar), location, call)
        fun allDay(day: Int, title: String, calendar: Long) =
            CalendarEvent(id++, title, real.dayStart(day), real.dayStart(day + 1), true, listOf(calendar))
        val events = listOf(
            allDay(0, "Emma's birthday", 9003),
            event(0, 9.0, 9.25, "Team standup", 9002, call = "Google Meet"),
            event(0, 12.5, 13.5, "Lunch with Liam", 9001, location = "Café de Jaren"),
            event(0, 16.0, 17.0, "Dentist", 9001, location = "Main Street 12"),
            event(0, 19.5, 21.0, "Yoga", 9001),
            event(1, 10.0, 11.0, "Design review", 9002, call = "Zoom"),
            event(1, 18.0, 19.0, "Swimming lessons", 9003),
            event(2, 9.0, 9.25, "Team standup", 9002, call = "Google Meet"),
            event(2, 20.0, 22.0, "Book club", 9001),
            event(4, 19.0, 22.0, "Dinner at Olivia's", 9003),
            allDay(5, "Weekend in Utrecht", 9001),
        ).filter { it.end > real.start && it.start < real.dayStart(real.days) }
        return real.copy(calendars = calendars, events = events)
    }
}
