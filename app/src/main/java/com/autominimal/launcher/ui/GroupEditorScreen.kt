package com.autominimal.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autominimal.launcher.data.AppInfo
import com.autominimal.launcher.data.CalendarOptions
import com.autominimal.launcher.data.CalendarSource
import com.autominimal.launcher.data.Group
import com.autominimal.launcher.data.IconCache
import com.autominimal.launcher.data.LookAhead
import com.autominimal.launcher.data.Source

/** What a block can show. Within Apps and People, the ranking is picked below. */
private enum class Fill(val label: String, val icon: String) {
    APPS("Apps", Sym.Apps),
    PEOPLE("People", Sym.Group),
    CALENDAR("Calendar", Sym.CalendarMonth),
    WEATHER("Weather", Sym.PartlyCloudyDay),
    ;

    fun matches(source: Source) = when (this) {
        APPS -> !source.isPeople && !source.isPanel
        PEOPLE -> source.isPeople
        CALENDAR -> source == Source.CALENDAR
        WEATHER -> source == Source.WEATHER
    }

    fun apply(group: Group): Group {
        val source = when (this) {
            APPS -> if (matches(group.source)) group.source else Source.SCREEN_TIME
            PEOPLE -> if (matches(group.source)) group.source else Source.MOST_CONTACTED
            CALENDAR -> Source.CALENDAR
            WEATHER -> Source.WEATHER
        }
        return group.copy(source = source, title = keptTitle(group.title, source))
    }
}

/** Titles the editor itself wrote. A name the user typed is left alone. */
private val automaticTitles = setOf(
    "New group", "New slot", "New block", "Apps", "People",
    "Most opened", "Most opened apps", "Opened most", "Most screen time", "Custom order",
    "Most called", "Most called contacts", "Most messaged", "Most contacted",
    "# opened", "screen time", "custom", "# called", "# messaged", "# contacted",
    "Calendar", "Weather", "News",
)

private fun keptTitle(current: String, source: Source): String {
    if (current !in automaticTitles) return current
    return when {
        source.isPeople -> "People"
        source.isPanel -> source.label
        else -> "Apps"
    }
}

/** S4: what a block shows, then how its list is chosen. */
@Composable
fun GroupEditorScreen(
    group: Group,
    isNew: Boolean,
    color: Color,
    apps: List<AppInfo>,
    icons: IconCache,
    calendarOptions: CalendarOptions,
    /** Null until the calendar can be read. */
    calendars: List<CalendarSource>?,
    onCalendarOptions: ((CalendarOptions) -> CalendarOptions) -> Unit,
    onAllowCalendar: () -> Unit,
    onChange: ((Group) -> Group) -> Unit,
    onPickApps: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    var renaming by rememberSaveable { mutableStateOf(false) }
    val source = group.source

    SettingsPage(
        group.title, onBack, gap = 18.dp,
        leading = { Box(Modifier.size(12.dp).background(color, RoundedCornerShape(4.dp))) },
        trailing = {
            Row(
                Modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) { renaming = true }.padding(horizontal = 6.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SymbolIcon(Sym.Edit, 18.dp, C.Soft)
                Text("Rename", fontSize = 14.sp, color = C.Soft)
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FieldLabel("Show")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Fill.entries.forEach { fill ->
                    val on = fill.matches(source)
                    Column(
                        Modifier.weight(1f).height(84.dp).clip(RoundedCornerShape(16.dp))
                            .background(if (on) C.Raised else C.Sunken)
                            .border(1.dp, if (on) C.Accent else Color.Transparent, RoundedCornerShape(16.dp))
                            .clickable(role = Role.RadioButton, onClickLabel = fill.label) { onChange { fill.apply(it) } },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
                    ) {
                        SymbolIcon(fill.icon, 26.dp, if (on) C.Accent else C.Soft)
                        Text(fill.label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        if (!source.isPanel) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel(if (source.isPeople) "Which people?" else "Which apps?")
                (if (source.isPeople) peopleSources else appSources).forEach { option ->
                    val selected = option == source
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(if (selected) C.Raised else Color.Transparent)
                            .border(1.dp, if (selected) C.Border else Color.Transparent, RoundedCornerShape(16.dp))
                            .clickable(role = Role.RadioButton) { onChange { it.copy(source = option, title = keptTitle(it.title, option)) } }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        SymbolIcon(option.icon, 22.dp, C.Accent)
                        TitleAndHint(option.choiceTitle, option.choiceHint, Modifier.weight(1f), size = 15.sp, gap = 0.dp)
                        RadioMark(selected)
                    }
                }
            }
        } else if (source == Source.CALENDAR) {
            CalendarOptionsSection(calendarOptions, calendars, onCalendarOptions, onAllowCalendar)
        } else {
            Row(
                Modifier.fillMaxWidth().card(16.dp).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SymbolIcon(source.icon, 28.dp, C.Blue)
                Text(source.sheetHint, fontSize = 15.sp, color = C.Soft, modifier = Modifier.weight(1f))
            }
        }

        if (source == Source.CUSTOM) {
            val byPackage = apps.associateBy { it.packageName }
            val picked = group.custom.mapNotNull { byPackage[it] }
            Column(Modifier.fillMaxWidth().card(16.dp).padding(horizontal = 14.dp, vertical = 4.dp)) {
                ReorderableColumn(
                    picked, keyOf = { it.packageName }, dragColor = C.Raised,
                    onMove = { from, to -> onChange { g -> g.copy(custom = g.custom.filter { it in byPackage }.toMutableList().apply { add(to, removeAt(from)) }) } },
                ) { app, handle, _ ->
                    Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(handle.size(24.dp, 44.dp), contentAlignment = Alignment.Center) {
                            SymbolIcon(Sym.DragIndicator, 18.dp, C.Dim, description = "Drag to reorder")
                        }
                        AppIconImage(app, icons, 26.dp, 7.dp)
                        Text(app.label, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Box(
                            Modifier.size(32.dp).clip(RoundedCornerShape(16.dp))
                                .clickable(onClickLabel = "Remove ${app.label}") { onChange { g -> g.copy(custom = g.custom - app.packageName) } },
                            contentAlignment = Alignment.Center,
                        ) { SymbolIcon(Sym.Close, 18.dp, C.Muted) }
                    }
                }
                if (picked.isNotEmpty()) Box(Modifier.fillMaxWidth().height(1.dp).background(C.Raised))
                Row(
                    Modifier.fillMaxWidth().height(44.dp).clickable(role = Role.Button, onClick = onPickApps),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SymbolIcon(Sym.Add, 20.dp, C.Accent)
                    Text("Add apps", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = C.Accent)
                }
            }
        }

        Text(
            if (isNew) "Leave this block empty" else "Remove this block",
            fontSize = 14.sp, color = if (isNew) C.Muted else C.Danger,
            modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = onDelete)
                .padding(vertical = 8.dp),
        )
    }

    if (renaming) {
        var name by rememberSaveable { mutableStateOf(group.title) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            containerColor = C.Card,
            shape = RoundedCornerShape(24.dp),
            title = { Text("Rename block", fontSize = 19.sp, fontWeight = FontWeight.SemiBold) },
            text = { TextInput(name, { name = it }, Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton({
                    val t = name.trim()
                    if (t.isNotEmpty()) onChange { it.copy(title = t) }
                    renaming = false
                }) { Text("Save", color = C.Accent, fontWeight = FontWeight.Medium) }
            },
            dismissButton = { TextButton({ renaming = false }) { Text("Cancel", color = C.Soft) } },
        )
    }
}

/** W4: which calendars, how far ahead, and what to leave out. Shared by every calendar block. */
@Composable
private fun CalendarOptionsSection(
    options: CalendarOptions,
    calendars: List<CalendarSource>?,
    onChange: ((CalendarOptions) -> CalendarOptions) -> Unit,
    onAllow: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel("Show events from")
        Column(Modifier.fillMaxWidth().card(16.dp).padding(horizontal = 14.dp)) {
            when {
                calendars == null -> Row(
                    Modifier.fillMaxWidth().height(54.dp).clickable(role = Role.Button, onClick = onAllow),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SymbolIcon(Sym.Lock, 18.dp, C.Accent)
                    Text("Allow calendar to choose", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = C.Accent)
                }
                calendars.isEmpty() -> Box(Modifier.fillMaxWidth().height(54.dp), contentAlignment = Alignment.CenterStart) {
                    Text("No calendars on this phone", fontSize = 15.sp, color = C.Sub)
                }
                else -> calendars.forEachIndexed { i, calendar ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(C.Raised))
                    val on = options.isOn(calendar)
                    Row(
                        Modifier.fillMaxWidth().height(54.dp)
                            .clickable(role = Role.Switch) { onChange { it.copy(choices = it.choices + (calendar.id to !on)) } },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.size(12.dp).background(calendarColor(i), CircleShape))
                        Column(Modifier.weight(1f)) {
                            Text(calendar.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(calendar.account, fontSize = 12.sp, color = C.Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Toggle(on)
                    }
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel("Look ahead")
        Segmented(LookAhead.entries, options.lookAhead, { ahead -> onChange { it.copy(lookAhead = ahead) } }, height = 40.dp) { ahead, color ->
            Text(ahead.label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = color)
        }
    }

    val flags = listOf(
        Triple("Show all-day events", "Holidays, out of office, where you work", options.allDay) to { o: CalendarOptions -> o.copy(allDay = !o.allDay) },
        Triple("Combine duplicates", "The same event in several calendars shows once", options.mergeDuplicates) to
            { o: CalendarOptions -> o.copy(mergeDuplicates = !o.mergeDuplicates) },
        Triple("Hide declined events", "Meetings you said no to", options.hideDeclined) to { o: CalendarOptions -> o.copy(hideDeclined = !o.hideDeclined) },
    )
    Column(Modifier.fillMaxWidth().card(16.dp).padding(horizontal = 14.dp)) {
        flags.forEachIndexed { i, (text, flip) ->
            val (title, hint, on) = text
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(C.Raised))
            Row(
                Modifier.fillMaxWidth().heightIn(min = 58.dp).clickable(role = Role.Switch) { onChange(flip) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(hint, fontSize = 12.sp, color = C.Sub)
                }
                Toggle(on)
            }
        }
    }
}
