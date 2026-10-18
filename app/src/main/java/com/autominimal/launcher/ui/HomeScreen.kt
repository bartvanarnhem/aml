package com.autominimal.launcher.ui

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.MediaStore
import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.autominimal.launcher.GroupContent
import com.autominimal.launcher.Missing
import com.autominimal.launcher.NoteKind
import com.autominimal.launcher.Tile
import com.autominimal.launcher.data.Background
import com.autominimal.launcher.data.BackgroundKind
import com.autominimal.launcher.data.Channel
import com.autominimal.launcher.data.Config
import com.autominimal.launcher.data.CornerShortcut
import com.autominimal.launcher.data.Group
import com.autominimal.launcher.data.IconCache
import com.autominimal.launcher.data.Screen
import com.autominimal.launcher.data.Source
import com.autominimal.launcher.data.WHATSAPP
import com.autominimal.launcher.data.WHATSAPP_BUSINESS
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    config: Config,
    contents: Map<String, GroupContent>,
    icons: IconCache,
    pagerState: PagerState,
    onTile: (Tile) -> Unit,
    onTileLongPress: (Tile) -> Unit,
    onMissing: (Missing) -> Unit,
    onAllApps: () -> Unit,
    onSettings: () -> Unit,
    onSwipeDown: () -> Unit,
    onLaunchPackage: (String) -> Boolean,
    onHeader: (Group) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnAllApps by rememberUpdatedState(onAllApps)
    val currentOnSwipeDown by rememberUpdatedState(onSwipeDown)
    fun shortcut(which: CornerShortcut, fallback: () -> Unit) {
        val pkg = config.shortcutApps[which]
        if (pkg == null || !onLaunchPackage(pkg)) fallback()
    }

    // A scrolling calendar takes the drag; pulling on past either end still opens All apps or the shade.
    val overscroll = remember {
        object : NestedScrollConnection {
            var pulled = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) pulled += available.y
                return Offset.Zero
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                when {
                    pulled < -SWIPE_PX -> currentOnAllApps()
                    pulled > SWIPE_PX -> currentOnSwipeDown()
                }
                pulled = 0f
                return Velocity.Zero
            }
        }
    }

    Box(Modifier.fillMaxSize().background(C.Ground)) {
        BackgroundLayer(config.background)
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().nestedScroll(overscroll)
                // Up opens All apps, down pulls the notification shade. Horizontal drags belong to the pager.
                .pointerInput(Unit) {
                    var pulled = 0f
                    detectVerticalDragGestures(
                        onDragStart = { pulled = 0f },
                        onDragEnd = {
                            when {
                                pulled < -SWIPE_PX -> currentOnAllApps()
                                pulled > SWIPE_PX -> currentOnSwipeDown()
                            }
                        },
                    ) { _, dy -> pulled += dy }
                },
        ) {
            HorizontalPager(pagerState, Modifier.weight(1f).fillMaxWidth(), beyondViewportPageCount = 1) { page ->
                val screen = config.screens.getOrNull(page) ?: return@HorizontalPager
                ScreenPage(screen, config, contents, icons, onTile, onTileLongPress, onMissing, onHeader, onSettings)
            }
            BottomBar(
                shortcuts = config.shortcuts,
                pageCount = config.screens.size,
                current = pagerState.currentPage,
                screenNames = config.screens.map { it.name },
                onPage = { scope.launch { pagerState.animateScrollToPage(it) } },
                onAllApps = onAllApps,
                onSettings = onSettings,
                onPhone = { shortcut(CornerShortcut.PHONE) { context.startSafely(Intent(Intent.ACTION_DIAL)) } },
                onWhatsApp = { shortcut(CornerShortcut.WHATSAPP) { onLaunchPackage(WHATSAPP) || onLaunchPackage(WHATSAPP_BUSINESS) } },
                onCamera = { shortcut(CornerShortcut.CAMERA) { context.startSafely(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)) } },
                onPhotos = { shortcut(CornerShortcut.PHOTOS) { onLaunchPackage(GOOGLE_PHOTOS) } },
            )
        }
    }
}

private const val SWIPE_PX = 120f
const val GOOGLE_PHOTOS = "com.google.android.apps.photos"

fun Context.startSafely(intent: Intent): Boolean =
    runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess

/** The wallpaper, anchored to its bottom edge, under a gradient that darkens towards the bottom. */
@Composable
private fun BackgroundLayer(background: Background) {
    val path = background.path?.takeIf { background.kind != BackgroundKind.NONE } ?: return
    val maxPx = with(LocalConfiguration.current) { maxOf(screenWidthDp, screenHeightDp) } * 3
    Box(Modifier.fillMaxSize().background(Color(0xFF3A2A2E)))
    rememberImageFile(path, maxPx)?.let {
        Image(
            it, contentDescription = null, contentScale = ContentScale.Crop, alignment = Alignment.BottomCenter,
            modifier = Modifier.fillMaxSize(),
        )
    }
    WallpaperDim(background.dim)
}

/** At the default 48 % this is the design's 30 % → 55 % gradient. */
@Composable
fun WallpaperDim(dim: Int, modifier: Modifier = Modifier.fillMaxSize()) {
    val a = dim / 100f
    val tint = Color(0xFF0C0909)
    Box(
        modifier.background(
            Brush.verticalGradient(listOf(tint.copy(alpha = (a * 0.625f).coerceIn(0f, 1f)), tint.copy(alpha = (a * 1.15f).coerceIn(0f, 1f)))),
        ),
    )
}

/**
 * Digital clock on the left with the full date under it. Tapping it opens the alarm app.
 * Above a full-screen weather or calendar it is [compact], with [trailing] at its right.
 */
@Composable
private fun Clock(compact: Boolean = false, trailing: (@Composable () -> Unit)? = null) {
    val context = LocalContext.current
    val now = rememberMinute()
    val time = SimpleDateFormat(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm", Locale.getDefault()).format(Date(now))
    val date = SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(Date(now))
    val size = if (compact) 52.sp else 68.sp
    Row(
        Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, top = if (compact) 14.dp else 22.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            Modifier.clip(RoundedCornerShape(12.dp))
                .clickable(onClickLabel = "Open alarms", role = Role.Button) { context.startSafely(Intent(AlarmClock.ACTION_SHOW_ALARMS)) }
                .semantics { contentDescription = "$time, $date. Open alarms" },
        ) {
            Text(
                time,
                style = TextStyle(
                    fontFamily = LocalAppFont.current, fontWeight = FontWeight.Normal, fontSize = size, lineHeight = size,
                    letterSpacing = if (compact) (-2).sp else (-3).sp, color = C.Home, platformStyle = Tight,
                ),
            )
            Text(
                date, fontSize = if (compact) 13.sp else 14.sp, color = C.HomeSoft,
                modifier = Modifier.padding(top = if (compact) 6.dp else 8.dp),
            )
        }
        trailing?.invoke()
    }
}

/**
 * One screen's blocks. A full-height block shows [com.autominimal.launcher.data.ListSize.count] rows;
 * two stacked blocks show half that each. Rows shrink only when the screen is too short.
 */
@Composable
private fun ScreenPage(
    screen: Screen,
    config: Config,
    contents: Map<String, GroupContent>,
    icons: IconCache,
    onTile: (Tile) -> Unit,
    onTileLongPress: (Tile) -> Unit,
    onMissing: (Missing) -> Unit,
    onHeader: (Group) -> Unit,
    onSettings: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val currentOnSettings by rememberUpdatedState(onSettings)
    val placed = List(screen.layout.left + screen.layout.right) { slot -> screen.groups.firstOrNull { it.slot == slot } }
    // Rows consume their own presses, so this only fires on empty space.
    val page = Modifier.fillMaxSize().pointerInput(Unit) {
        detectTapGestures(onLongPress = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            currentOnSettings()
        })
    }
    val alone = placed.singleOrNull()
    val content = alone?.let { contents[it.id] }
    val weather = content?.weather
    val agenda = content?.agenda
    if (alone?.source == Source.WEATHER && weather != null) {
        Column(page) {
            Clock(compact = true) { WeatherBesideClock(weather) }
            WeatherFull(weather, Modifier.weight(1f))
        }
        return
    }
    if (alone?.source == Source.CALENDAR && agenda != null) {
        Column(page) {
            Clock(compact = true, trailing = weather?.let { { WeatherChip(it) } })
            CalendarFull(alone, agenda, onTile, onTileLongPress, onOpenCalendar = { onHeader(alone) }, modifier = Modifier.weight(1f))
        }
        return
    }
    Column(page) {
        Clock()
        ScreenBlocks(screen, placed, config, contents, icons, onTile, onTileLongPress, onMissing, onHeader)
    }
}

@Composable
private fun ScreenBlocks(
    screen: Screen,
    placed: List<Group?>,
    config: Config,
    contents: Map<String, GroupContent>,
    icons: IconCache,
    onTile: (Tile) -> Unit,
    onTileLongPress: (Tile) -> Unit,
    onMissing: (Missing) -> Unit,
    onHeader: (Group) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(start = 28.dp, end = 28.dp, top = 30.dp)) {
        if (placed.all { it == null }) {
            Text("Nothing here yet. Hold an empty spot to open settings.", fontSize = 14.sp, color = C.HomeMuted)
            return@BoxWithConstraints
        }
        val size = config.listSize
        val count = size.count
        val half = count / 2
        val row = min(size.rowDp.dp, (maxHeight - HeaderHeight) / count).coerceAtLeast(1.dp)
        // A lower block's gap plus header takes exactly one row, so its rows line up with a full-height list beside it.
        val stackGap = (row - HeaderHeight).coerceAtLeast(0.dp)
        val stackedTop = HeaderHeight + row * half
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                listOf(placed.take(screen.layout.left), placed.drop(screen.layout.left)).filter { it.isNotEmpty() }.forEach { column ->
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(stackGap)) {
                        column.forEachIndexed { i, group ->
                            if (group == null) {
                                Spacer(Modifier.height(stackedTop))
                            } else {
                                Block(
                                    group, contents[group.id], icons, config,
                                    maxRows = if (column.size > 1) half else count,
                                    row = row,
                                    onTile = onTile, onTileLongPress = onTileLongPress, onMissing = onMissing, onHeader = onHeader,
                                    modifier = if (i < column.lastIndex) Modifier.heightIn(min = stackedTop) else Modifier,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val HeaderHeight = 24.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Block(
    group: Group,
    content: GroupContent?,
    icons: IconCache,
    config: Config,
    maxRows: Int,
    row: Dp,
    onTile: (Tile) -> Unit,
    onTileLongPress: (Tile) -> Unit,
    onMissing: (Missing) -> Unit,
    onHeader: (Group) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        val agenda = content?.agenda?.takeIf { group.source == Source.CALENDAR }
        val day = agenda?.let { it.dayTitle(it.firstBusyDay(rememberMinute())) }
        BlockHeader(group, day) { onHeader(group) }
        if (group.source == Source.WEATHER) content?.weather?.let {
            WeatherSmall(it)
            return@Column
        }
        agenda?.let {
            CalendarSmall(it, maxRows, onTile, onTileLongPress)
            return@Column
        }
        val tiles = content?.tiles.orEmpty().take(maxRows)
        val missing = content?.missing
        if (tiles.isEmpty()) {
            when {
                missing != null -> MissingRow(missing, row) { onMissing(missing) }
                content == null -> Spacer(Modifier.height(row))
                else -> Text(
                    content.empty ?: when (group.source) {
                        Source.CUSTOM -> "No apps picked yet"
                        Source.CALENDAR -> "No upcoming events"
                        Source.NEWS -> "No headlines"
                        else -> "Nothing to show yet"
                    },
                    fontSize = 13.sp, color = C.HomeMuted, modifier = Modifier.padding(vertical = 10.dp),
                )
            }
            return@Column
        }
        val size = config.listSize
        tiles.forEach { tile ->
            Row(
                Modifier.fillMaxWidth().height(row).clip(RoundedCornerShape(8.dp))
                    .combinedClickable(
                        onClickLabel = tile.actionLabel, role = Role.Button,
                        onLongClick = { onTileLongPress(tile) }, onClick = { onTile(tile) },
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val icon = size.iconDp.dp
                when (tile) {
                    is Tile.App -> AppIconImage(tile.app, icons, icon, icon * 0.29f)
                    is Tile.Contact -> PersonIcon(tile.person, icon)
                    is Tile.Note -> Box(Modifier.size(icon).background(C.HomeTrack, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                        SymbolIcon(if (tile.kind == NoteKind.CALENDAR) Sym.Event else Sym.Newspaper, icon * 0.57f, C.Home)
                    }
                }
                Text(
                    tile.name, fontSize = size.labelSp.sp, color = C.Home, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                when {
                    tile is Tile.Note -> tile.stat?.let { Text(it, fontSize = 12.sp, color = C.HomeMuted, maxLines = 1) }
                    tile.stat != null -> UsageBar(tile.share)
                }
            }
        }
        // People lists can rank with a permission missing; offer it under the rows when there is room.
        if (missing != null && tiles.size < maxRows) MissingRow(missing, row) { onMissing(missing) }
    }
}

/**
 * "Apps · Most time spent ⌄". Tapping it opens the sort sheet for app and people lists, or the calendar app.
 * [day] is the day a calendar block shows, e.g. "Tomorrow".
 */
@Composable
private fun BlockHeader(group: Group, day: String?, onClick: () -> Unit) {
    val sortable = !group.source.isPanel
    val label = when {
        sortable -> "Change how ${group.title} is sorted"
        group.source == Source.CALENDAR -> "Open calendar"
        else -> null
    }
    Row(
        Modifier.fillMaxWidth().height(HeaderHeight).padding(bottom = 4.dp).clip(RoundedCornerShape(6.dp))
            .then(if (label != null) Modifier.clickable(onClickLabel = label, role = Role.Button, onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            group.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = C.Home, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(end = 8.dp),
        )
        if (sortable) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(group.meta, fontSize = 12.sp, color = C.HomeMuted, maxLines = 1)
                SymbolIcon(Sym.ExpandMore, 16.dp, C.HomeMuted)
            }
        } else if (day != null) {
            Text(day, fontSize = 12.sp, color = C.HomeMuted, maxLines = 1)
        }
    }
}

/** Quiet 28 × 3 bar: how much this row is used compared with the top of its list. */
@Composable
private fun UsageBar(share: Float) {
    Box(Modifier.size(28.dp, 3.dp).clip(RoundedCornerShape(2.dp)).background(C.HomeTrack)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(share.coerceIn(0f, 1f)).background(C.Home.copy(alpha = 0.8f)))
    }
}

@Composable
private fun MissingRow(missing: Missing, row: Dp, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(maxOf(row, 44.dp)).clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(28.dp).background(C.HomeTrack, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            SymbolIcon(Sym.Lock, 16.dp, C.Accent)
        }
        Column(Modifier.weight(1f)) {
            Text(missing.action, fontSize = 15.sp, color = C.Home, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(missing.message, fontSize = 12.sp, color = C.HomeMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Material Symbols glyph for a WMO weather code. */
fun weatherIcon(code: Int, isDay: Boolean = true): String = when (code) {
    0 -> if (isDay) Sym.Sunny else Sym.ClearNight
    1, 2 -> if (isDay) Sym.PartlyCloudyDay else Sym.PartlyCloudyNight
    45, 48 -> Sym.Foggy
    51, 53, 55, 56, 57, 61, 66, 80 -> Sym.RainyLight
    in 62..67, 81, 82 -> Sym.Rainy
    in 71..77, 85, 86 -> Sym.WeatherSnowy
    95, 96, 99 -> Sym.Thunderstorm
    else -> Sym.Cloud
}

@Composable
private fun BottomBar(
    shortcuts: Set<CornerShortcut>,
    pageCount: Int,
    current: Int,
    screenNames: List<String>,
    onPage: (Int) -> Unit,
    onAllApps: () -> Unit,
    onSettings: () -> Unit,
    onPhone: () -> Unit,
    onWhatsApp: () -> Unit,
    onCamera: () -> Unit,
    onPhotos: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    // Touch boxes are wider than the glyphs; the outer padding puts the glyphs 24 dp from the edges.
    Row(
        Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row {
            @OptIn(ExperimentalFoundationApi::class)
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).combinedClickable(
                    onClickLabel = "All apps", role = Role.Button,
                    onLongClickLabel = "Launcher settings",
                    onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onSettings() },
                    onClick = onAllApps,
                ),
                contentAlignment = Alignment.Center,
            ) { SymbolIcon(Sym.Apps, 24.dp, C.Home, description = "All apps") }
            BarIcon(Sym.Settings, "Launcher settings", onSettings)
        }

        Row(
            Modifier.semantics { contentDescription = "Screen ${current + 1} of $pageCount" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (pageCount > 1) repeat(pageCount) { i ->
                Box(
                    Modifier.height(44.dp).clickable(onClickLabel = "Go to ${screenNames[i]}") { onPage(i) }.padding(horizontal = 3.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size(if (i == current) 16.dp else 4.dp, 4.dp)
                            .background(if (i == current) C.Home else C.Home.copy(alpha = 0.45f), RoundedCornerShape(2.dp)),
                    )
                }
            }
        }

        Row {
            if (CornerShortcut.PHONE in shortcuts) BarIcon(Sym.Call, "Phone", onPhone)
            if (CornerShortcut.WHATSAPP in shortcuts) BarIcon(Sym.ChatBubble, "WhatsApp", onWhatsApp)
            if (CornerShortcut.CAMERA in shortcuts) BarIcon(Sym.PhotoCamera, "Camera", onCamera)
            if (CornerShortcut.PHOTOS in shortcuts) BarIcon(Sym.Image, "Photos", onPhotos)
        }
    }
}

@Composable
private fun BarIcon(icon: String, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(42.dp, 44.dp).clip(RoundedCornerShape(21.dp)).clickable(onClickLabel = label, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { SymbolIcon(icon, 22.dp, C.Home, description = label) }
}

val Tile.name: String
    get() = when (this) {
        is Tile.App -> app.label
        is Tile.Contact -> person.name
        is Tile.Note -> title
    }

private val Tile.actionLabel: String
    get() = when (this) {
        is Tile.App -> "Open ${app.label}"
        is Tile.Contact -> if (person.channel == Channel.CALL) "Call ${person.name}" else "Message ${person.name}"
        is Tile.Note -> "Open $title"
    }
