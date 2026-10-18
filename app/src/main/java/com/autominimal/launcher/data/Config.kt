package com.autominimal.launcher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * What fills a block on a home screen.
 * [label] is the sort label beside the block's title on home; [sheetTitle] and [sheetHint] describe it
 * in the sort sheet; [choiceTitle] and [choiceHint] describe it when editing the block in settings.
 */
@Serializable
enum class Source(
    val label: String,
    val sheetTitle: String,
    val sheetHint: String,
    val choiceTitle: String = label,
    val choiceHint: String = sheetHint,
) {
    MOST_OPENED("Most opened", "Times opened", "Apps you open most often"),
    SCREEN_TIME("Most time spent", "Time spent", "Apps you spend the most time in"),
    CUSTOM("My order", "My own pick", "You choose the apps and their order", "I'll choose", "Pick the apps and their order yourself"),
    MOST_CALLED("Most called", "Most called", "People you phone most"),
    MOST_MESSAGED("Most messaged", "Most messaged", "WhatsApp, Signal, Telegram and texts"),
    MOST_CONTACTED("Most in touch", "Most in touch", "Calls and messages together"),
    CALENDAR("Calendar", "Calendar", "Shows your next events from your own calendars."),
    WEATHER("Weather", "Weather", "Shows the weather where you are right now, plus the next few hours."),
    NEWS("News", "News", "Headlines for this region.");

    val isPeople get() = this == MOST_CALLED || this == MOST_MESSAGED || this == MOST_CONTACTED
    /** A block filled with something other than a ranked list of apps or people. */
    val isPanel get() = this == CALENDAR || this == WEATHER || this == NEWS
}

/** The period every ranked list uses. Chosen once under Screens. */
@Serializable
enum class RankWindow(val label: String, val explanation: String, val past: String, val span: String) {
    WEEK("Week", "Apps you used most in the past week go to the top. Quick to adapt.", "the past week", "this week"),
    MONTH("Month", "Apps you used most in the past month go to the top.", "the past month", "this month"),
    ALL("Forever", "Apps you've used most since installing go to the top. Very steady.", "everything since installing", "of all time"),
}

@Serializable
data class Group(
    val id: String = newId(),
    val title: String = "New group",
    val source: Source = Source.MOST_OPENED,
    /** Package names, in order, for [Source.CUSTOM]. */
    val custom: List<String> = emptyList(),
    /** Older per-group length. Home lists use [Config.listSize] instead. */
    val size: Int = 8,
    /**
     * Which numbered slot this group fills, starting at 0.
     * -1 means it has never been chosen; load assigns the list order.
     */
    val slot: Int = -1,
) {
    /** Short line under the title in settings, e.g. "Apps · most time spent". */
    val rule: String
        get() = when (source) {
            Source.MOST_OPENED -> "Apps · most opened"
            Source.SCREEN_TIME -> "Apps · most time spent"
            Source.CUSTOM -> "Apps · your own pick"
            Source.MOST_CALLED -> "People · most called"
            Source.MOST_MESSAGED -> "People · most messaged"
            Source.MOST_CONTACTED -> "People · calls and messages"
            Source.CALENDAR -> "Your next events"
            Source.WEATHER -> "Weather where you are"
            Source.NEWS -> "Headlines for this region"
        }

    /** Right-hand label on the home screen. */
    val meta: String get() = source.label
}

/**
 * How a screen arranges its groups.
 * The numbers are how many groups stack on the left and on the right.
 */
@Serializable
enum class ScreenLayout(val label: String, val left: Int, val right: Int) {
    /** One group across the full screen. */
    SINGLE("Single column", 1, 0),
    /** One group full height on the left, one on the right. */
    TWO_COLUMNS("Two columns", 1, 1),
    /** One group full height on the left, two stacked on the right. */
    ONE_BY_TWO("1 × 2", 1, 2),
    /** Two stacked on the left, one group full height on the right. */
    TWO_BY_ONE("2 × 1", 2, 1),
    /** Two stacked on the left and two stacked on the right. */
    TWO_BY_TWO("2 × 2", 2, 2),
}

@Serializable
data class Screen(
    val id: String = newId(),
    val name: String,
    val groups: List<Group> = emptyList(),
    val layout: ScreenLayout = ScreenLayout.TWO_COLUMNS,
    /** The screen the home button returns to. Exactly one screen has it, and it cannot be removed. */
    val home: Boolean = false,
) {
    /**
     * Moves what fills the slots this layout shows.
     * A group in a slot the layout does not show keeps its number, so it returns when the layout grows.
     */
    fun reorderVisibleSlots(from: Int, to: Int): Screen {
        val count = layout.left + layout.right
        if (from !in 0 until count || to !in 0 until count || from == to) return this
        val visible = (0 until count).map { slot -> groups.find { it.slot == slot } }.toMutableList()
        visible.add(to, visible.removeAt(from))
        val assigned = visible.mapIndexedNotNull { index, group -> group?.let { it.id to index } }.toMap()
        return copy(groups = groups.map { group ->
            val slot = assigned[group.id] ?: return@map group
            if (group.slot == slot) group else group.copy(slot = slot)
        })
    }

    /** Packages in other visible custom lists. A list does not count itself. */
    fun appsInOtherCustomLists(groupId: String): Set<String> {
        val visible = layout.left + layout.right
        return groups
            .filter { it.id != groupId && it.source == Source.CUSTOM && it.slot in 0 until visible }
            .flatMap { it.custom }
            .toSet()
    }
}

/** Typeface for the launcher. [SYSTEM] is the launcher's own face (Instrument Sans) and stays first. */
@Serializable
enum class AppFont(val label: String) {
    SYSTEM("System"),
    PLEX("Plex"),
    PLEX_MONO("Plex Mono"),
    SERIF("Serif"),
    CONDENSED("Narrow"),
    MONO("Mono"),
}

/** Bottom-right shortcuts. Each one can be hidden from Settings. */
@Serializable
enum class CornerShortcut(val label: String) {
    PHONE("Phone"),
    WHATSAPP("WhatsApp"),
    CAMERA("Camera"),
    PHOTOS("Photos"),
}

/** How many rows a full home list shows, with the row height, icon size and name size for that text size. */
@Serializable
enum class ListSize(val label: String, val count: Int, val iconDp: Int, val rowDp: Int, val labelSp: Int) {
    COMPACT("Small", 7, 28, 38, 15),
    STANDARD("Medium", 5, 34, 46, 17),
    BIG("Large", 3, 40, 56, 20),
}

/** How many days the calendar block covers, today included. */
@Serializable
enum class LookAhead(val label: String, val days: Int) {
    TOMORROW("Tomorrow", 2),
    THREE_DAYS("3 days", 4),
    WEEK("A week", 7),
}

/** Calendar options, shared by every calendar block. */
@Serializable
data class CalendarOptions(
    /** Calendars switched on or off by hand. The rest are on when they are the user's own. */
    val choices: Map<Long, Boolean> = emptyMap(),
    val lookAhead: LookAhead = LookAhead.WEEK,
    val allDay: Boolean = true,
    /** The same event in several calendars shows once. */
    val mergeDuplicates: Boolean = true,
    val hideDeclined: Boolean = true,
) {
    fun isOn(calendar: CalendarSource) = choices[calendar.id] ?: calendar.own
}

@Serializable
enum class BackgroundKind { NONE, GALLERY, GENERATED, PREDEFINED }

@Serializable
data class Background(
    val kind: BackgroundKind = BackgroundKind.NONE,
    /** Absolute file path, or a bundled asset path for a predefined wallpaper. */
    val path: String? = null,
    /** 0–80: how much to darken the image for readability. */
    val dim: Int = 40,
)

/** Where generated backgrounds come from. */
@Serializable
enum class ImageProvider(val label: String) {
    /** Free and keyless (rate limited per IP); an optional key lifts the limit. */
    POLLINATIONS("Pollinations"),
    GEMINI("Gemini"),
}

/** The words a generated background is built from: one mood, one place and one time of day. */
object PromptWords {
    val moods = listOf("Cozy", "Vibrant", "Dreamy", "Minimal", "Futuristic")
    val places = listOf("City", "Mountains", "Jungle", "Ocean")
    val times = listOf("Sunrise", "Sunset", "Night")

    /** "Cozy jungle at sunset". */
    fun short(mood: String, place: String, time: String) = "$mood ${place.lowercase()} at ${time.lowercase()}"

    /** "A cozy jungle at sunset, calm and peaceful". Plural places drop the "A". */
    fun long(mood: String, place: String, time: String): String {
        val words = "${mood.lowercase()} ${place.lowercase()} at ${time.lowercase()}, calm and peaceful"
        return if (place.endsWith("s")) words.replaceFirstChar { it.uppercase() } else "A $words"
    }
}

const val DEFAULT_GEMINI_MODEL = "gemini-2.5-flash-image"

/** The wallpaper a fresh install starts with. */
const val DEFAULT_WALLPAPER = "wallpapers/minimal_jungle_sundown.png"

@Serializable
data class Config(
    val screens: List<Screen> = defaultScreens(),
    /** Period used by every ranked group. */
    val window: RankWindow = RankWindow.MONTH,
    /** Ranked lists skip apps that a custom list on the same screen already contains. */
    val hideDuplicateApps: Boolean = true,
    val font: AppFont = AppFont.SYSTEM,
    val listSize: ListSize = ListSize.COMPACT,
    /** Corner shortcuts shown on the home screen. Missing from older configs, so all stay on. */
    val shortcuts: Set<CornerShortcut> = CornerShortcut.entries.toSet(),
    /** Package a shortcut opens instead of its usual app. */
    val shortcutApps: Map<CornerShortcut, String> = emptyMap(),
    val background: Background = Background(BackgroundKind.PREDEFINED, DEFAULT_WALLPAPER, 48),
    val mood: String = "Futuristic",
    val place: String = "City",
    val timeOfDay: String = "Sunrise",
    val imageProvider: ImageProvider = ImageProvider.POLLINATIONS,
    val pollinationsKey: String = "",
    val geminiKey: String = "",
    val geminiModel: String = DEFAULT_GEMINI_MODEL,
    /** Images generated with Gemini, newest first. */
    val generated: List<String> = emptyList(),
    /** Item keys hidden from groups (they still show in All apps). */
    val hidden: Set<String> = emptySet(),
    val calendar: CalendarOptions = CalendarOptions(),
    /** False until first-run setup has been completed or skipped. */
    val onboarded: Boolean = false,
    /** Saved configs older than [LAYOUT_VERSION] are adjusted once on load. */
    val layoutVersion: Int = 0,
) {
    val prompt: String get() = PromptWords.long(mood, place, timeOfDay)

    val homeIndex: Int get() = screens.indexOfFirst { it.home }.coerceAtLeast(0)
}

/** Keeps exactly one home screen: the first marked one, else the one named "Home", else the first. */
fun List<Screen>.withOneHome(): List<Screen> {
    if (isEmpty()) return this
    val index = indexOfFirst { it.home }.takeIf { it >= 0 }
        ?: indexOfFirst { it.name.equals("Home", ignoreCase = true) }.takeIf { it >= 0 }
        ?: 0
    return mapIndexed { i, screen -> if (screen.home == (i == index)) screen else screen.copy(home = i == index) }
}

fun newId(): String = UUID.randomUUID().toString().substring(0, 8)

/**
 * The calendar comes first, then Home with apps on the left and people above an empty Work list on the right,
 * then the weather.
 */
fun defaultScreens() = listOf(
    Screen(
        name = "Calendar",
        layout = ScreenLayout.SINGLE,
        groups = listOf(Group(title = "Calendar", source = Source.CALENDAR, slot = 0)),
    ),
    Screen(
        name = "Home",
        home = true,
        layout = ScreenLayout.ONE_BY_TWO,
        groups = listOf(
            Group(title = "Apps", source = Source.SCREEN_TIME, slot = 0),
            Group(title = "People", source = Source.MOST_CONTACTED, slot = 1),
            Group(title = "Work", source = Source.CUSTOM, slot = 2),
        ),
    ),
    Screen(
        name = "Weather",
        layout = ScreenLayout.SINGLE,
        groups = listOf(Group(title = "Weather", source = Source.WEATHER, slot = 0)),
    ),
)

/** Bumped when saved screens need a one-time fix. Version 4 drops the default Work screen. */
const val LAYOUT_VERSION = 4

/** Persists [Config] as JSON in private SharedPreferences. */
class ConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _config = MutableStateFlow(load())
    val config: StateFlow<Config> = _config.asStateFlow()

    private fun load(): Config {
        val saved = prefs.getString(KEY, null)?.let { runCatching { json.decodeFromString<Config>(it) }.getOrNull() } ?: Config()
        val screens = (if (saved.layoutVersion >= LAYOUT_VERSION) saved.screens
        else saved.screens.filterNot { it.name == "Work" }.ifEmpty { defaultScreens() }).map { screen ->
            screen.copy(groups = screen.groups.mapIndexed { index, group ->
                val title = when (group.title) {
                    "Most opened apps" -> "Most opened"
                    "Most called contacts" -> "Most called"
                    else -> group.title
                }
                val slot = if (group.slot >= 0) group.slot else index
                if (title == group.title && slot == group.slot) group else group.copy(title = title, slot = slot)
            })
        }.withOneHome()
        if (saved.layoutVersion >= LAYOUT_VERSION && screens == saved.screens) return saved
        return saved.copy(screens = screens, layoutVersion = LAYOUT_VERSION)
    }

    fun update(transform: (Config) -> Config) {
        val next = transform(_config.value)
        _config.value = next
        prefs.edit().putString(KEY, json.encodeToString(Config.serializer(), next)).apply()
    }

    private companion object {
        const val KEY = "config"
    }
}
