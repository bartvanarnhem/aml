package com.autominimal.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autominimal.launcher.AppStats
import com.autominimal.launcher.NoteKind
import com.autominimal.launcher.Tile
import com.autominimal.launcher.data.Channel
import com.autominimal.launcher.data.Group
import com.autominimal.launcher.data.IconCache
import com.autominimal.launcher.data.Person
import com.autominimal.launcher.data.RankWindow
import com.autominimal.launcher.data.Source
import com.autominimal.launcher.data.WHATSAPP
import com.autominimal.launcher.formatDuration

/** Dims everything behind and rises a rounded card from the bottom. Tapping outside closes it. */
@Composable
fun BottomSheet(onDismiss: () -> Unit, padding: PaddingValues = PaddingValues(20.dp), gap: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    Box(
        Modifier.fillMaxSize().background(C.Scrim)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
            .statusBarsPadding().navigationBarsPadding(),
    ) {
        AnimatedVisibility(shown, Modifier.align(Alignment.BottomCenter), enter = slideInVertically { it / 2 } + fadeIn()) {
            Column(
                Modifier.padding(12.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(C.Card)
                    // Swallow taps so they don't close the sheet.
                    .clickable(remember { MutableInteractionSource() }, indication = null) {}
                    .padding(padding),
                verticalArrangement = Arrangement.spacedBy(gap),
                content = content,
            )
        }
    }
}

/** Icon used for a way of sorting, in the sort sheet and the block editor. */
val Source.icon: String
    get() = when (this) {
        Source.SCREEN_TIME -> Sym.Schedule
        Source.MOST_OPENED -> Sym.TouchApp
        Source.CUSTOM -> Sym.Edit
        Source.MOST_CALLED -> Sym.Call
        Source.MOST_MESSAGED -> Sym.ChatBubble
        Source.MOST_CONTACTED -> Sym.Forum
        Source.CALENDAR -> Sym.CalendarMonth
        Source.WEATHER -> Sym.PartlyCloudyDay
        Source.NEWS -> Sym.Newspaper
    }

val appSources = listOf(Source.SCREEN_TIME, Source.MOST_OPENED, Source.CUSTOM)
val peopleSources = listOf(Source.MOST_CALLED, Source.MOST_MESSAGED, Source.MOST_CONTACTED)

/** "Sort "Apps" by": the ways this list can be ranked, and the period they look at. */
@Composable
fun SortSheet(group: Group, window: RankWindow, onSelect: (Source) -> Unit, onChangeWindow: () -> Unit, onDismiss: () -> Unit) {
    BottomSheet(onDismiss, PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 18.dp), gap = 6.dp) {
        Box(
            Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp)
                .size(36.dp, 4.dp).background(C.Border, RoundedCornerShape(2.dp)),
        )
        Text("Sort \"${group.title}\" by", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 4.dp))
        Text(
            "The list updates by itself as your habits change.", fontSize = 14.sp, color = C.Sub,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 10.dp),
        )
        (if (group.source.isPeople) peopleSources else appSources).forEach { source ->
            val selected = source == group.source
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(if (selected) C.Raised else Color.Transparent)
                    .border(1.dp, if (selected) C.Border else Color.Transparent, RoundedCornerShape(16.dp))
                    .clickable(role = Role.RadioButton) { onSelect(source) }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.size(40.dp).background(C.Raised, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    SymbolIcon(source.icon, 22.dp, C.Accent)
                }
                TitleAndHint(source.sheetTitle, source.sheetHint, Modifier.weight(1f))
                RadioMark(selected)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SymbolIcon(Sym.History, 18.dp, C.Sub)
            Text("Looking at ${window.past}", fontSize = 13.sp, color = C.Sub, modifier = Modifier.weight(1f))
            Text(
                "Change", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = C.Accent,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(role = Role.Button, onClick = onChangeWindow),
            )
        }
    }
}

/**
 * Long-press sheet. Apps show why they are on home and offer app info, hiding and uninstall;
 * people get big Call and chat buttons.
 */
@Composable
fun TileMenu(
    tile: Tile,
    hidden: Boolean,
    icons: IconCache,
    window: RankWindow,
    whatsAppInstalled: Boolean,
    loadStats: suspend (String) -> AppStats,
    onToggleHidden: () -> Unit,
    onAppInfo: (String) -> Unit,
    onCall: (Person) -> Unit,
    onChat: (Person) -> Unit,
    onUninstall: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    BottomSheet(onDismiss) {
        when (tile) {
            is Tile.App -> {
                val stats by produceState<AppStats?>(null, tile.app.packageName) { value = loadStats(tile.app.packageName) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    AppIconImage(tile.app, icons, 52.dp, 14.dp)
                    SheetTitle(tile.app.label, stats?.let { appReason(it, window) } ?: " ")
                }
                stats?.takeIf { it.timeMs != null }?.let { s ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatBox(formatDuration(s.timeMs ?: 0), "Time spent", Modifier.weight(1f))
                        StatBox(times(s.opens ?: 0), "Opened", Modifier.weight(1f))
                    }
                }
                Actions {
                    Action(Sym.Info, "App info", divider = false) { onAppInfo(tile.app.packageName) }
                    HideAction(hidden, onToggleHidden)
                    Action(Sym.Delete, "Uninstall", color = C.Danger, iconColor = C.Danger) { onUninstall(tile.app.packageName) }
                }
            }
            is Tile.Contact -> {
                val p = tile.person
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    PersonAvatar(p, 52.dp)
                    SheetTitle(p.name, contactSummary(p, window))
                }
                val chatTarget = when {
                    p.canChat -> p
                    p.channel == Channel.CALL && p.number != null && whatsAppInstalled -> p.copy(channel = Channel.WHATSAPP, appPackage = WHATSAPP)
                    else -> null
                }
                if (p.canCall || chatTarget != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (p.canCall) BigButton(Sym.Call, "Call", accent = true, Modifier.weight(1f)) { onCall(p) }
                        chatTarget?.let { BigButton(Sym.ChatBubble, it.channel.appName, accent = false, Modifier.weight(1f)) { onChat(it) } }
                    }
                }
                Actions(compact = true) { HideAction(hidden, onToggleHidden, divider = false, sub = false) }
            }
            is Tile.Note -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(52.dp).background(C.Raised, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                        SymbolIcon(if (tile.kind == NoteKind.CALENDAR) Sym.Event else Sym.Newspaper, 26.dp, C.Soft)
                    }
                    SheetTitle(tile.title, tile.stat ?: if (tile.kind == NoteKind.CALENDAR) "From your calendar" else "Headline")
                }
                Actions(compact = true) { HideAction(hidden, onToggleHidden, divider = false, sub = false) }
            }
        }
    }
}

private val Channel.appName
    get() = when (this) {
        Channel.WHATSAPP, Channel.CALL -> "WhatsApp"
        Channel.SIGNAL -> "Signal"
        Channel.TELEGRAM -> "Telegram"
        Channel.SMS -> "Messages"
    }

private fun ordinal(n: Int) = n.toString() + when {
    n % 100 in 11..13 -> "th"
    n % 10 == 1 -> "st"
    n % 10 == 2 -> "nd"
    n % 10 == 3 -> "rd"
    else -> "th"
}

private fun times(n: Long) = if (n == 1L) "1 time" else "$n times"

private fun appReason(stats: AppStats, window: RankWindow): String {
    val rank = stats.timeRank
    return when {
        stats.timeMs == null -> "Allow app usage to see how much you use it"
        rank == 0 -> "Your most used app ${window.span}"
        rank != null -> "Your ${ordinal(rank + 1)} most used app ${window.span}"
        else -> "Not used ${window.span}"
    }
}

private fun contactSummary(p: Person, window: RankWindow): String {
    val parts = listOfNotNull(
        p.calls.takeIf { it > 0 }?.let { if (it == 1) "1 call" else "$it calls" },
        p.messages.takeIf { it > 0 }?.let { if (it == 1) "1 message" else "$it messages" },
    )
    return if (parts.isEmpty()) "No calls or messages ${window.span}" else "${parts.joinToString(" and ")} ${window.span}"
}

@Composable
private fun SheetTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, fontSize = 13.sp, color = C.Sub, maxLines = 2)
    }
}

@Composable
private fun StatBox(value: String, label: String, modifier: Modifier) {
    Column(
        modifier.background(C.Raised, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        Text(label, fontSize = 12.sp, color = C.Sub)
    }
}

@Composable
private fun BigButton(icon: String, label: String, accent: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val fg = if (accent) C.Card else C.Text
    Row(
        modifier.height(64.dp).clip(RoundedCornerShape(16.dp)).background(if (accent) C.Accent else C.Raised)
            .clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SymbolIcon(icon, 22.dp, fg)
        Text(label, fontSize = 16.sp, fontWeight = if (accent) FontWeight.SemiBold else FontWeight.Medium, color = fg)
    }
}

private class ActionsScope(val compact: Boolean)

@Composable
private fun Actions(compact: Boolean = false, content: @Composable ActionsScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.SheetLine))
        ActionsScope(compact).content()
    }
}

@Composable
private fun ActionsScope.Action(
    icon: String,
    label: String,
    sub: String? = null,
    divider: Boolean = true,
    color: Color = C.Text,
    iconColor: Color = C.Soft,
    onClick: () -> Unit,
) {
    if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(C.SheetLine))
    Row(
        Modifier.fillMaxWidth().height(if (compact) 48.dp else 52.dp).clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SymbolIcon(icon, 22.dp, iconColor)
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 16.sp, color = color)
            if (sub != null) Text(sub, fontSize = 12.sp, color = C.Sub)
        }
    }
}

@Composable
private fun ActionsScope.HideAction(hidden: Boolean, onClick: () -> Unit, divider: Boolean = true, sub: Boolean = true) {
    if (hidden) Action(Sym.Visibility, "Show on home", if (sub) "Put it back in your lists" else null, divider, onClick = onClick)
    else Action(Sym.VisibilityOff, "Don't show on home", if (sub) "Still in your app list and search" else null, divider, onClick = onClick)
}
