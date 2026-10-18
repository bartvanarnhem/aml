package com.autominimal.launcher.data

import com.autominimal.launcher.formatDuration
import org.junit.Assert.assertEquals
import org.junit.Test

class LogicTest {
    @Test
    fun homeIsTheScreenNamedHomeNotTheFirst() {
        val saved = listOf(Screen(name = "Calendar"), Screen(name = "Home"), Screen(name = "Weather"))
        assertEquals(listOf(false, true, false), saved.withOneHome().map { it.home })
        assertEquals(1, Config(screens = defaultScreens()).homeIndex)
        val twoMarked = listOf(Screen(name = "A", home = true), Screen(name = "Home", home = true))
        assertEquals(listOf(true, false), twoMarked.withOneHome().map { it.home })
    }

    @Test
    fun hiddenSlotsStayPutWhenTheLayoutShrinks() {
        val apps = Group(id = "a", title = "Apps", slot = 0)
        val people = Group(id = "p", title = "People", slot = 1)
        val work = Group(id = "w", title = "Work", slot = 2)
        val narrow = Screen(name = "Home", layout = ScreenLayout.TWO_COLUMNS, groups = listOf(apps, people, work))
        val swapped = narrow.reorderVisibleSlots(0, 1)
        assertEquals(1, swapped.groups.first { it.id == "a" }.slot)
        assertEquals(0, swapped.groups.first { it.id == "p" }.slot)
        assertEquals(2, swapped.groups.first { it.id == "w" }.slot)
        val wide = swapped.copy(layout = ScreenLayout.ONE_BY_TWO)
        assertEquals(2, wide.groups.first { it.id == "w" }.slot)
    }

    @Test
    fun rankedListsSkipAppsFromOtherCustomLists() {
        val custom = Group(id = "c", source = Source.CUSTOM, custom = listOf("maps", "mail"), slot = 0)
        val ranked = Group(id = "r", source = Source.MOST_OPENED, slot = 1)
        val parked = Group(id = "p", source = Source.CUSTOM, custom = listOf("photos"), slot = 2)
        val screen = Screen(
            name = "Home",
            layout = ScreenLayout.TWO_COLUMNS,
            groups = listOf(custom, ranked, parked),
        )
        assertEquals(setOf("maps", "mail"), screen.appsInOtherCustomLists(ranked.id))
        assertEquals(emptySet<String>(), screen.appsInOtherCustomLists(custom.id))
    }

    @Test
    fun groupLabelsMatchTheDesign() {
        val work = Group(source = Source.MOST_OPENED)
        assertEquals("Apps · most opened", work.rule)
        assertEquals("Most opened", work.meta)
        assertEquals("Apps · your own pick", Group(source = Source.CUSTOM).rule)
        assertEquals("People · most called", Group(source = Source.MOST_CALLED).rule)
        assertEquals("Your next events", Group(source = Source.CALENDAR).rule)
        assertEquals("Cozy jungle at sunset", PromptWords.short("Cozy", "Jungle", "Sunset"))
        assertEquals("A cozy jungle at sunset, calm and peaceful", PromptWords.long("Cozy", "Jungle", "Sunset"))
        assertEquals("Dreamy mountains at night, calm and peaceful", PromptWords.long("Dreamy", "Mountains", "Night"))
        assertEquals("Clear", weatherSummary(0))
        assertEquals("Rain", weatherSummary(63))
        assertEquals("Snow", weatherSummary(73))
    }

    @Test
    fun numberKeysMatchLocalAndInternational() {
        assertEquals(PeopleRepository.numberKey("+31 6 1234 5678"), PeopleRepository.numberKey("06-12345678"))
    }

    @Test
    fun durations() {
        assertEquals("<1m", formatDuration(30_000))
        assertEquals("45m", formatDuration(45 * 60_000L))
        assertEquals("2h", formatDuration(120 * 60_000L))
        assertEquals("1h 20m", formatDuration(80 * 60_000L))
    }
}
