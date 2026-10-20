package com.autominimal.launcher

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autominimal.launcher.data.Agenda
import com.autominimal.launcher.data.AppInfo
import com.autominimal.launcher.data.Background
import com.autominimal.launcher.data.BackgroundKind
import com.autominimal.launcher.data.Config
import com.autominimal.launcher.data.Feed
import com.autominimal.launcher.data.FeedItem
import com.autominimal.launcher.data.FeedNeed
import com.autominimal.launcher.data.Group
import com.autominimal.launcher.data.WeatherNow
import com.autominimal.launcher.data.Person
import com.autominimal.launcher.data.Channel
import com.autominimal.launcher.data.Screen
import com.autominimal.launcher.data.ScreenLayout
import com.autominimal.launcher.data.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Tile {
    val key: String

    /** Opens, screen time, or calls + messages; for events, when they start. */
    val stat: String?

    /** Usage relative to the top of its list, 0–1, drawn as the bar at the end of the row. */
    val share: Float

    data class App(val app: AppInfo, override val stat: String? = null, override val share: Float = 0f) : Tile {
        override val key get() = app.packageName
    }

    data class Contact(val person: Person, override val stat: String? = null, override val share: Float = 0f) : Tile {
        override val key get() = person.key
    }

    /** A calendar event or a news headline. */
    data class Note(
        val title: String,
        val kind: NoteKind,
        val url: String? = null,
        val eventId: Long? = null,
        override val stat: String? = null,
    ) : Tile {
        override val key get() = url ?: eventId?.toString() ?: title
        override val share get() = 0f
    }
}

/** Why an app is on the home screen, for the hold sheet. Null fields mean usage access is missing. */
data class AppStats(val timeMs: Long?, val opens: Long?, val timeRank: Int?)

enum class NoteKind { CALENDAR, NEWS }

/** "45m", "1h 20m", "<1m". */
fun formatDuration(ms: Long): String {
    val minutes = ms / 60_000
    return when {
        minutes < 1 -> "<1m"
        minutes < 60 -> "${minutes}m"
        minutes % 60 == 0L -> "${minutes / 60}h"
        else -> "${minutes / 60}h ${minutes % 60}m"
    }
}

/** Why a group can't be filled yet, with the action that fixes it. Same words as the Privacy page. */
enum class Missing(val message: String, val action: String) {
    USAGE("To put your most used apps on top", "Allow app usage"),
    CALL_LOG("To show the people you call most", "Allow call history"),
    CONTACTS("To show names and find WhatsApp chats", "Allow contacts"),
    NOTIFICATIONS("Counts messages per chat. Never reads them.", "Allow message counts"),
    CALENDAR("To show your next events", "Allow calendar"),
    LOCATION("To show the weather where you are", "Allow location"),
}

data class GroupContent(
    val tiles: List<Tile> = emptyList(),
    val missing: Missing? = null,
    /** The forecast of a weather block, or the one beside the clock above a full-screen calendar. */
    val weather: WeatherNow? = null,
    val agenda: Agenda? = null,
    val empty: String? = null,
)

/** The editor works on a copy so Cancel can discard it. */
data class GroupDraft(val screenId: String, val group: Group, val isNew: Boolean)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as LauncherApp
    private val store = app.config
    val icons = app.icons
    private val demo = Demo.enabled(app)
    val demoSetup = Demo.setup(app)

    val config: StateFlow<Config> = store.config

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    private val _contents = MutableStateFlow<Map<String, GroupContent>>(emptyMap())
    val contents: StateFlow<Map<String, GroupContent>> = _contents.asStateFlow()

    /** The five apps with the most screen time, shown above the A–Z list. Empty without usage access. */
    private val _topApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val topApps: StateFlow<List<AppInfo>> = _topApps.asStateFlow()

    private val _draft = MutableStateFlow<GroupDraft?>(null)
    val draft: StateFlow<GroupDraft?> = _draft.asStateFlow()

    private var refreshJob: Job? = null

    init {
        reloadApps()
        // Re-rank when anything that affects group contents changes (not on appearance tweaks).
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            config.map { listOf(it.screens, it.hidden, it.listSize, it.hideDuplicateApps, it.calendar) }.distinctUntilChanged().debounce(150).collect { refresh() }
        }
    }

    fun reloadApps() {
        viewModelScope.launch {
            _apps.value = withContext(Dispatchers.IO) { app.apps.loadApps() }.let { all -> if (demo) all.filter { it.packageName in Demo.apps } else all }
            refresh()
        }
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val cfg = config.value
            val apps = _apps.value
            _contents.value = withContext(Dispatchers.IO) {
                cfg.screens.flatMap { screen -> screen.groups.map { it.id to resolve(it, screen, cfg, apps) } }.toMap()
            }
            _topApps.value = withContext(Dispatchers.IO) { mostUsed(cfg, apps) }
        }
    }

    private fun mostUsed(cfg: Config, apps: List<AppInfo>): List<AppInfo> {
        if (!app.usage.hasAccess()) return emptyList()
        val unranked = app.apps.unranked()
        val byPackage = apps.associateBy { it.packageName }
        return app.usage.screenTime(cfg.window).entries
            .filter { it.value > 0 && it.key !in unranked && it.key !in cfg.hidden }
            .sortedByDescending { it.value }
            .mapNotNull { byPackage[it.key] }
            .take(5)
    }

    /** Screen time, opens and screen-time rank of one app in the current window. */
    suspend fun appStats(pkg: String): AppStats = withContext(Dispatchers.IO) {
        if (!app.usage.hasAccess()) return@withContext AppStats(null, null, null)
        val window = config.value.window
        val unranked = app.apps.unranked()
        val time = app.usage.screenTime(window)
        val ranked = time.entries.filter { it.value > 0 && it.key !in unranked }.sortedByDescending { it.value }.map { it.key }
        AppStats(time[pkg] ?: 0L, app.usage.launchCounts(window)[pkg] ?: 0L, ranked.indexOf(pkg).takeIf { it >= 0 })
    }

    private fun resolve(group: Group, screen: Screen, cfg: Config, apps: List<AppInfo>): GroupContent {
        val unranked = app.apps.unranked()
        val byPackage = apps.associateBy { it.packageName }
        val reserved = if (cfg.hideDuplicateApps) screen.appsInOtherCustomLists(group.id) else emptySet()
        fun allowed(key: String) = key !in cfg.hidden
        fun rankedApps(scores: Map<String, Long>, stat: (Long) -> String): List<Tile> {
            val ranked = scores.entries
                .filter { it.value > 0 && it.key !in unranked && allowed(it.key) && it.key !in reserved }
                .sortedByDescending { it.value }
                .mapNotNull { e -> byPackage[e.key]?.let { it to e.value } }
                .take(cfg.listSize.count)
            val top = ranked.firstOrNull()?.second?.toFloat() ?: 1f
            return ranked.map { (info, score) -> Tile.App(info, stat(score), score / top) }
        }
        fun people(list: List<Person>, count: (Person) -> Int): List<Tile> {
            val shown = list.filter { allowed(it.key) }.take(cfg.listSize.count)
            val top = shown.maxOfOrNull(count)?.coerceAtLeast(1)?.toFloat() ?: 1f
            return shown.mapIndexed { i, p ->
                Tile.Contact(if (demo) Demo.person(i, p) else p, count(p).takeIf { n -> n > 0 }?.toString(), count(p) / top)
            }
        }
        fun whatsAppMissing() = when {
            !app.people.hasContacts() -> Missing.CONTACTS
            !app.people.hasNotificationAccess() -> Missing.NOTIFICATIONS
            else -> null
        }

        return when (group.source) {
            Source.MOST_OPENED ->
                if (!app.usage.hasAccess()) GroupContent(missing = Missing.USAGE)
                else GroupContent(rankedApps(app.usage.launchCounts(cfg.window)) { "$it×" })
            Source.SCREEN_TIME ->
                if (!app.usage.hasAccess()) GroupContent(missing = Missing.USAGE)
                else GroupContent(rankedApps(app.usage.screenTime(cfg.window), ::formatDuration))
            Source.CUSTOM -> GroupContent((if (demo && group.custom.isNotEmpty()) Demo.work else group.custom).mapNotNull { byPackage[it] }.take(cfg.listSize.count).map { Tile.App(it) })
            Source.MOST_CALLED ->
                if (!app.people.hasCallLog()) GroupContent(missing = Missing.CALL_LOG)
                else GroupContent(people(app.people.mostCalled(cfg.window)) { it.calls })
            Source.MOST_MESSAGED ->
                GroupContent(people(app.people.mostMessaged(cfg.window)) { it.messages }, whatsAppMissing())
            Source.MOST_CONTACTED -> GroupContent(
                people(app.people.mostContacted(cfg.window)) { it.calls + it.messages },
                if (!app.people.hasCallLog()) Missing.CALL_LOG else whatsAppMissing(),
            )
            Source.CALENDAR -> when (val result = app.feeds.agenda(cfg.calendar)) {
                is Feed.Events -> GroupContent(
                    agenda = (if (demo) Demo.agenda(result.agenda) else result.agenda)
                        .let { it.copy(events = it.events.filterNot { e -> e.id.toString() in cfg.hidden }) },
                    weather = if (screen.layout == ScreenLayout.SINGLE) (weather() as? Feed.Forecast)?.weather else null,
                )
                is Feed.Needs -> GroupContent(missing = Missing.CALENDAR)
                is Feed.Failed -> GroupContent(empty = result.message)
                else -> GroupContent(empty = "No upcoming events")
            }
            Source.WEATHER -> when (val forecast = weather()) {
                is Feed.Forecast -> GroupContent(weather = forecast.weather)
                is Feed.Needs -> GroupContent(missing = Missing.LOCATION)
                is Feed.Failed -> GroupContent(empty = forecast.message)
                else -> GroupContent(empty = "Couldn't load weather")
            }
            Source.NEWS -> feed(app.feeds.news(cfg.listSize.count), NoteKind.NEWS, "No headlines")
        }
    }

    private fun weather(): Feed = app.feeds.weather().let {
        if (demo && it is Feed.Forecast) it.copy(weather = it.weather.copy(place = Demo.PLACE)) else it
    }

    private fun feed(result: Feed, kind: NoteKind, empty: String): GroupContent = when (result) {
        is Feed.Items -> GroupContent(
            tiles = result.items.map { it.toNote(kind) },
            empty = if (result.items.isEmpty()) empty else null,
        )
        is Feed.Needs -> GroupContent(missing = if (result.need == FeedNeed.CALENDAR) Missing.CALENDAR else Missing.LOCATION)
        is Feed.Failed -> GroupContent(empty = result.message)
        else -> GroupContent()
    }

    private fun FeedItem.toNote(kind: NoteKind) = Tile.Note(title, kind, url, stat = detail)

    // --- Actions on tiles ---

    fun open(tile: Tile) = when (tile) {
        is Tile.App -> app.apps.launch(tile.app)
        is Tile.Contact -> if (tile.person.channel == Channel.CALL) app.people.call(tile.person) else app.people.chat(tile.person)
        is Tile.Note -> openNote(tile)
    }

    private fun openNote(tile: Tile.Note) {
        val intent = when {
            tile.url != null -> Intent(Intent.ACTION_VIEW, Uri.parse(tile.url))
            tile.eventId != null -> Intent(Intent.ACTION_VIEW).setData(
                android.content.ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, tile.eventId),
            )
            else -> return
        }
        runCatching { app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun openCalendar() {
        val now = CalendarContract.CONTENT_URI.buildUpon().appendPath("time")
            .also { android.content.ContentUris.appendId(it, System.currentTimeMillis()) }.build()
        val intent = Intent(Intent.ACTION_VIEW, now).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { app.startActivity(intent) }.onFailure {
            runCatching {
                app.startActivity(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    fun call(person: Person) = app.people.call(person)
    fun chat(person: Person) = app.people.chat(person)
    fun launch(info: AppInfo) = app.apps.launch(info)
    fun launchPackage(pkg: String) = app.apps.launchPackage(pkg)
    fun appInfo(pkg: String) = app.apps.openAppInfo(pkg)
    fun uninstall(pkg: String) = app.apps.uninstall(pkg)

    fun toggleHidden(key: String) = store.update {
        it.copy(hidden = if (key in it.hidden) it.hidden - key else it.hidden + key)
    }

    // --- Settings ---

    fun update(transform: (Config) -> Config) = store.update(transform)

    /** Inserts a screen at [at]. The end of the list is used when [at] is past the last screen. */
    fun addScreen(at: Int): String {
        val screen = Screen(name = "Screen ${config.value.screens.size + 1}")
        store.update {
            val list = it.screens.toMutableList()
            list.add(at.coerceIn(0, list.size), screen)
            it.copy(screens = list)
        }
        return screen.id
    }

    fun reorderScreens(from: Int, to: Int) = store.update { cfg ->
        if (from !in cfg.screens.indices || to !in cfg.screens.indices || from == to) cfg
        else {
            val list = cfg.screens.toMutableList()
            list.add(to, list.removeAt(from))
            cfg.copy(screens = list)
        }
    }

    fun renameScreen(id: String, name: String) = updateScreen(id) { it.copy(name = name.trim().ifEmpty { it.name }) }

    fun setScreenLayout(id: String, layout: ScreenLayout) = updateScreen(id) { it.copy(layout = layout) }

    fun removeScreen(id: String) = store.update { cfg ->
        if (cfg.screens.size <= 1 || cfg.screens.any { it.id == id && it.home }) cfg
        else cfg.copy(screens = cfg.screens.filterNot { it.id == id })
    }

    /** Changes how a home-screen list is sorted. Keeps a title the user already chose. */
    fun setGroupSource(groupId: String, source: Source) = store.update { cfg ->
        cfg.copy(screens = cfg.screens.map { screen ->
            screen.copy(groups = screen.groups.map { group ->
                if (group.id != groupId) group
                else group.copy(
                    source = source,
                    title = if (group.title == group.source.label || group.title == "New group") source.label else group.title,
                )
            })
        })
    }

    /** Reorders what fills the slots the current layout shows. Hidden slots stay where they are. */
    fun reorderSlots(screenId: String, from: Int, to: Int) = updateScreen(screenId) { it.reorderVisibleSlots(from, to) }

    /** Opens the group in [slot], or creates one there when the layout slot is empty. */
    fun openSlot(screenId: String, slot: Int) {
        val screen = config.value.screens.find { it.id == screenId } ?: return
        if (slot !in 0 until screen.layout.left + screen.layout.right) return
        val existing = screen.groups.find { it.slot == slot }
        if (existing != null) {
            _draft.value = GroupDraft(screenId, existing, isNew = false)
            return
        }
        val created = Group(title = "Apps", slot = slot)
        updateScreen(screenId) { it.copy(groups = it.groups + created) }
        _draft.value = GroupDraft(screenId, created, isNew = true)
    }

    /** Puts [groupId] in the numbered slot. The group already there takes its previous slot. */
    fun assignGroupSlot(screenId: String, groupId: String, slot: Int) = updateScreen(screenId) { s ->
        val mine = s.groups.find { it.id == groupId } ?: return@updateScreen s
        if (mine.slot == slot) return@updateScreen s
        s.copy(groups = s.groups.map { group ->
            when {
                group.id == groupId -> group.copy(slot = slot)
                group.slot == slot -> group.copy(slot = mine.slot)
                else -> group
            }
        })
    }

    private fun updateScreen(id: String, transform: (Screen) -> Screen) = store.update { cfg ->
        cfg.copy(screens = cfg.screens.map { if (it.id == id) transform(it) else it })
    }

    fun editGroup(screenId: String, group: Group?) {
        val editing = group ?: run {
            val screen = config.value.screens.find { it.id == screenId }
            val taken = screen?.groups?.map { it.slot }?.toSet().orEmpty()
            val count = (screen?.layout?.left ?: 1) + (screen?.layout?.right ?: 0)
            val free = (0 until count).firstOrNull { it !in taken } ?: count
            Group(title = "Apps", slot = free).also { created ->
                updateScreen(screenId) { it.copy(groups = it.groups + created) }
            }
        }
        _draft.value = GroupDraft(screenId, editing, isNew = group == null)
    }

    /** Writes the open group immediately. Back does not ask to save. */
    fun updateDraft(transform: (Group) -> Group) {
        val d = _draft.value ?: return
        val next = transform(d.group)
        _draft.value = d.copy(group = next)
        updateScreen(d.screenId) { s ->
            s.copy(groups = s.groups.map { if (it.id == next.id) next else it })
        }
    }

    fun deleteDraftGroup() {
        val d = _draft.value ?: return
        updateScreen(d.screenId) { s -> s.copy(groups = s.groups.filterNot { it.id == d.group.id }) }
        _draft.value = null
    }

    fun discardDraft() {
        _draft.value = null
    }

    // --- Background ---

    private val _generating = MutableStateFlow(false)
    val generating: StateFlow<Boolean> = _generating.asStateFlow()
    private val _generateError = MutableStateFlow<String?>(null)
    val generateError: StateFlow<String?> = _generateError.asStateFlow()

    fun generate(onDone: (String) -> Unit) {
        if (_generating.value) return
        val cfg = config.value
        _generating.value = true
        _generateError.value = null
        viewModelScope.launch {
            runCatching { app.backgrounds.generate(cfg) }
                .onSuccess { file ->
                    store.update { c ->
                        val kept = (listOf(file.absolutePath) + c.generated)
                        kept.drop(MAX_GENERATED).filter { it != c.background.path }.forEach(app.backgrounds::delete)
                        c.copy(generated = kept.take(MAX_GENERATED))
                    }
                    onDone(file.absolutePath)
                }
                .onFailure { _generateError.value = it.message ?: "Generation failed." }
            _generating.value = false
        }
    }

    fun importFromGallery(uri: Uri, onDone: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { app.backgrounds.importFromGallery(uri) }.onSuccess { onDone(it.absolutePath) }
        }
    }

    fun applyBackground(path: String?, kind: BackgroundKind, dim: Int) {
        val previous = config.value.background.path
        store.update {
            // A replaced gallery import is not listed anywhere else, so drop its file.
            val old = it.background
            if (old.kind == BackgroundKind.GALLERY && old.path != null && old.path != path) app.backgrounds.delete(old.path)
            it.copy(background = Background(if (path == null) BackgroundKind.NONE else kind, path, dim))
        }
        if (path != null && path != previous) {
            viewModelScope.launch { runCatching { app.backgrounds.setAsSystemWallpaper(path) } }
        }
    }

    /** Drops a generated image. If it was showing, the background goes back to plain. */
    fun deleteGenerated(path: String) = store.update { c ->
        app.backgrounds.delete(path)
        val background = if (c.background.kind == BackgroundKind.GENERATED && c.background.path == path) {
            c.background.copy(kind = BackgroundKind.NONE, path = null)
        } else c.background
        c.copy(generated = c.generated.filterNot { it == path }, background = background)
    }

    private companion object {
        const val MAX_GENERATED = 9
    }
}
