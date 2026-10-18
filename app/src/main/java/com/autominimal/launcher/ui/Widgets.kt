package com.autominimal.launcher.ui

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import com.autominimal.launcher.NoteKind
import com.autominimal.launcher.Tile
import com.autominimal.launcher.data.Agenda
import com.autominimal.launcher.data.CalendarEvent
import com.autominimal.launcher.data.Group
import com.autominimal.launcher.data.WeatherDay
import com.autominimal.launcher.data.WeatherNow
import com.autominimal.launcher.data.rainHeight
import com.autominimal.launcher.data.startsIn
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** The current time, updated on every minute. */
@Composable
fun rememberMinute(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000 - now % 60_000)
        }
    }
    return now
}

private val HomeHalf = C.Home.copy(alpha = 0.7f)
private val Rule = C.Home.copy(alpha = 0.16f)

@Composable
private fun tabular() = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")

// --- Weather ---

/** W1, beside the clock: condition, temperature and what it feels like. */
@Composable
fun WeatherBesideClock(weather: WeatherNow) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SymbolIcon(weatherIcon(weather.code, weather.isDay), 40.dp, C.Home, description = weather.summary)
        Column(horizontalAlignment = Alignment.End) {
            Text("${weather.temp}°", fontSize = 40.sp, lineHeight = 40.sp, color = C.Home)
            Text("Feels like ${weather.feelsLike}°", fontSize = 12.sp, color = HomeHalf, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** W2, beside the clock: just the condition and temperature. */
@Composable
fun WeatherChip(weather: WeatherNow) {
    val soft = C.Home.copy(alpha = 0.85f)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SymbolIcon(weatherIcon(weather.code, weather.isDay), 22.dp, soft, description = weather.summary)
        Text("${weather.temp}°", fontSize = 14.sp, color = soft)
    }
}

/** W1: rain in the next three hours first, then the coming hours and days, then wind, humidity and the sun. */
@Composable
fun WeatherFull(weather: WeatherNow, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().clipToBounds()) {
        Column(Modifier.padding(start = 28.dp, end = 28.dp, top = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    weather.rain?.headline ?: weather.summary, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = C.Home,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                if (weather.place.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        SymbolIcon(Sym.LocationOn, 14.dp, HomeHalf)
                        Text(weather.place, fontSize = 12.sp, color = HomeHalf, maxLines = 1)
                    }
                }
            }
            weather.rain?.let { rain ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    RainBars(rain.slots, 44.dp, 3.dp, RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp), rain.headline)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        rain.labels.forEach { Text(it, fontSize = 11.sp, color = C.Home.copy(alpha = 0.6f)) }
                    }
                }
            }
            if (weather.hours.isNotEmpty()) {
                Column {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Rule))
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        weather.hours.take(8).forEach { hour ->
                            Column(
                                Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Text(hour.label, fontSize = 11.sp, color = C.HomeMuted, maxLines = 1)
                                SymbolIcon(weatherIcon(hour.code, hour.isDay), 20.dp, C.Home)
                                Text("${hour.temp}°", fontSize = 14.sp, color = C.Home)
                                Text(hour.rainChance?.let { "$it%" }.orEmpty(), fontSize = 10.sp, color = C.Blue, modifier = Modifier.height(12.dp))
                            }
                        }
                    }
                }
            }
            if (weather.days.isNotEmpty()) Box(Modifier.fillMaxWidth().height(1.dp).background(Rule))
        }
        // Short screens drop days from the end; wind, humidity and the sun stay at the bottom.
        val low = (weather.days.minOfOrNull { it.low } ?: 0) - 1
        val high = (weather.days.maxOfOrNull { it.high } ?: 0) + 1
        ItemsAboveFooter(
            weather.days, footerGap = 12.dp,
            modifier = Modifier.weight(1f).padding(start = 28.dp, end = 28.dp, top = 4.dp),
            footer = {
                Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    FooterItem(Sym.Air, "${weather.windFrom} ${weather.windKmh} km/h")
                    FooterItem(Sym.HumidityPercentage, "${weather.humidity}%")
                    weather.sun?.let { FooterItem(Sym.WbTwilight, it) }
                }
            },
        ) { DayRow(it, low, high) }
    }
}

/** "Mon ☁ 7° ━━━ 12°": the bar spans the day's low to high on a scale shared by all rows. */
@Composable
private fun DayRow(day: WeatherDay, low: Int, high: Int) {
    Row(
        Modifier.fillMaxWidth().height(30.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(day.label, fontSize = 13.sp, color = C.Home, maxLines = 1, modifier = Modifier.width(44.dp))
        Box(Modifier.width(28.dp), contentAlignment = Alignment.CenterStart) { SymbolIcon(weatherIcon(day.code), 18.dp, C.Home) }
        Text("${day.low}°", fontSize = 13.sp, color = C.Home.copy(alpha = 0.6f), textAlign = TextAlign.End, modifier = Modifier.width(30.dp))
        val span = (high - low).coerceAtLeast(1).toFloat()
        Canvas(Modifier.weight(1f).height(4.dp)) {
            val r = CornerRadius(size.height / 2)
            drawRoundRect(C.Home.copy(alpha = 0.14f), cornerRadius = r)
            val left = (day.low - low) / span * size.width
            val right = (day.high - low) / span * size.width
            drawRoundRect(
                Brush.horizontalGradient(listOf(C.Blue, C.Accent), startX = left, endX = right),
                topLeft = Offset(left, 0f), size = Size((right - left).coerceAtLeast(size.height), size.height), cornerRadius = r,
            )
        }
        Text("${day.high}°", fontSize = 13.sp, color = C.Home, modifier = Modifier.width(30.dp))
    }
}

@Composable
private fun FooterItem(icon: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        SymbolIcon(icon, 15.dp, HomeHalf)
        Text(text, fontSize = 12.sp, color = HomeHalf, maxLines = 1)
    }
}

/** One bar per 15 minutes: blue where it rains, a faint stub where it's dry. */
@Composable
private fun RainBars(slots: List<Float>, height: Dp, gap: Dp, shape: RoundedCornerShape, description: String) {
    Row(
        Modifier.fillMaxWidth().height(height).clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        slots.forEach { mm ->
            val f = rainHeight(mm)
            Box(
                Modifier.weight(1f).height(max(2.dp, height * f)).clip(shape)
                    .background(if (f > 0f) C.Blue else C.Home.copy(alpha = 0.22f)),
            )
        }
    }
}

/** W3: condition, temperature, today's high and low, and the rain bars, small enough to sit beside apps. */
@Composable
fun WeatherSmall(weather: WeatherNow) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SymbolIcon(weatherIcon(weather.code, weather.isDay), 30.dp, C.Home, description = weather.summary)
            Text("${weather.temp}°", fontSize = 30.sp, lineHeight = 30.sp, color = C.Home)
            Column {
                weather.days.firstOrNull()?.let { Text("${it.high}° / ${it.low}°", fontSize = 12.sp, color = HomeHalf, maxLines = 1) }
                Text(weather.rain?.short ?: weather.summary, fontSize = 12.sp, color = HomeHalf, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        weather.rain?.let { RainBars(it.slots, 22.dp, 2.dp, RoundedCornerShape(1.dp), it.headline) }
    }
}

// --- Calendar ---

private fun Agenda.date(index: Int) = Instant.ofEpochMilli(dayStart(index)).atZone(zone).toLocalDate()

/** "Today", "Tomorrow", or the weekday. */
fun Agenda.dayTitle(index: Int): String = when (index) {
    0 -> "Today"
    1 -> "Tomorrow"
    else -> date(index).dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
}

/** The date under [dayTitle]: "Monday 19 October", or "21 October" when the title is already the weekday. */
private fun Agenda.daySubtitle(index: Int): String =
    date(index).format(DateTimeFormatter.ofPattern(if (index < 2) "EEEE d MMMM" else "d MMMM", Locale.getDefault()))

@Composable
private fun rememberClockFormat(): (Long) -> String {
    val context = LocalContext.current
    val format = remember { DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm", Locale.getDefault()) }
    return { ms -> Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).format(format) }
}

private fun CalendarEvent.tile() = Tile.Note(title, NoteKind.CALENDAR, eventId = id)

private fun Agenda.color(event: CalendarEvent) = calendarColor(colorIndex(event.calendars.firstOrNull() ?: -1))

/** "45 min", "1 h 30 min", "2 h". */
private fun duration(ms: Long): String {
    val minutes = (ms / 60_000).coerceAtLeast(1)
    return when {
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0L -> "${minutes / 60} h"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }
}

/**
 * W2: a week strip with a dot per calendar, then one day: all-day items as chips and timed events as cards.
 * The next event counts down. Tapping a day in the strip shows that day.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun CalendarFull(
    group: Group,
    agenda: Agenda,
    onEvent: (Tile) -> Unit,
    onEventLongPress: (Tile) -> Unit,
    onOpenCalendar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val now = rememberMinute()
    val clock = rememberClockFormat()
    var picked by rememberSaveable(group.id) { mutableStateOf<Int?>(null) }
    val selected = (picked ?: agenda.firstBusyDay(now)).coerceIn(0, agenda.days - 1)
    val day = agenda.day(selected, now)
    val next = agenda.next(now)

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, top = 18.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(7) { i ->
                val inRange = i < agenda.days
                val date = agenda.date(i)
                val today = i == 0
                Column(
                    Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(12.dp))
                        .background(
                            when {
                                today -> C.Home
                                i == selected -> C.Home.copy(alpha = 0.14f)
                                else -> Color.Transparent
                            },
                        )
                        .clickable(enabled = inRange, role = Role.Tab, onClickLabel = "Show ${agenda.daySubtitle(i)}") { picked = i },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                ) {
                    val fg = when {
                        today -> C.Card
                        inRange -> C.Home
                        else -> C.Home.copy(alpha = 0.35f)
                    }
                    Text(
                        date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()), fontSize = 11.sp,
                        color = fg.copy(alpha = fg.alpha * 0.75f), maxLines = 1,
                    )
                    Text("${date.dayOfMonth}", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = fg)
                    Row(Modifier.height(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (inRange) agenda.calendarsOn(i, now).forEach { id ->
                            Box(Modifier.size(4.dp).background(calendarColor(agenda.colorIndex(id)), CircleShape))
                        }
                    }
                }
            }
        }

        Row(
            Modifier.padding(start = 28.dp, end = 28.dp, top = 16.dp).clip(RoundedCornerShape(6.dp))
                .clickable(onClickLabel = "Open calendar", role = Role.Button, onClick = onOpenCalendar),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(agenda.dayTitle(selected), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = C.Home)
            Text(agenda.daySubtitle(selected), fontSize = 13.sp, color = C.HomeMuted)
        }

        // A busy day scrolls under the day title; each day starts at the top.
        val scroll = remember(selected) { ScrollState(0) }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll, enabled = scroll.maxValue > 0)
                .padding(start = 28.dp, end = 28.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            if (day.allDay.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    day.allDay.forEach { event ->
                        val last = Instant.ofEpochMilli(event.until - 1).atZone(agenda.zone).toLocalDate()
                        val until = if (last.isAfter(agenda.date(selected))) {
                            "until " + last.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                        } else null
                        Row(
                            Modifier.height(28.dp).clip(RoundedCornerShape(14.dp)).background(C.Home.copy(alpha = 0.12f))
                                .combinedClickable(
                                    onClickLabel = "Open ${event.title}", role = Role.Button,
                                    onLongClick = { onEventLongPress(event.tile()) }, onClick = { onEvent(event.tile()) },
                                )
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(Modifier.size(6.dp).background(agenda.color(event), CircleShape))
                            Text(event.title, fontSize = 13.sp, color = C.Home, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            until?.let { Text(it, fontSize = 13.sp, color = C.Home.copy(alpha = 0.55f), maxLines = 1) }
                        }
                    }
                }
            }

            if (day.timed.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    day.timed.forEachIndexed { i, event ->
                        val countdown = if (event.id == next?.id && event.start == next.start) startsIn(event.start, now) else null
                        EventCard(agenda, event, first = i == 0, countdown, clock, onEvent, onEventLongPress)
                    }
                }
            }

            val nextDay = day.nextBusy?.let { if (it == 1) "tomorrow" else agenda.dayTitle(it) }
            val rest = when {
                nextDay != null -> (if (day.isEmpty) "Nothing planned until " else "Nothing else until ") + nextDay
                day.isEmpty -> "Nothing planned"
                else -> "Nothing else coming up"
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SymbolIcon(Sym.EventAvailable, 16.dp, C.HomeMuted)
                Text(rest, fontSize = 13.sp, color = C.HomeMuted)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EventCard(
    agenda: Agenda,
    event: CalendarEvent,
    first: Boolean,
    countdown: String?,
    clock: (Long) -> String,
    onEvent: (Tile) -> Unit,
    onEventLongPress: (Tile) -> Unit,
) {
    val (icon, meta) = when {
        event.location != null -> Sym.LocationOn to event.location
        event.call != null -> Sym.Videocam to event.call
        else -> Sym.Schedule to duration(event.end - event.start)
    }
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(16.dp))
            .background(C.Home.copy(alpha = if (first) 0.14f else 0.06f))
            .combinedClickable(
                onClickLabel = "Open ${event.title}", role = Role.Button,
                onLongClick = { onEventLongPress(event.tile()) }, onClick = { onEvent(event.tile()) },
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(agenda.color(event), RoundedCornerShape(2.dp)))
        Column(Modifier.width(46.dp)) {
            Text(clock(event.start), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = C.Home, style = tabular(), maxLines = 1)
            Text(clock(event.end), fontSize = 12.sp, color = C.Home.copy(alpha = 0.55f), style = tabular(), maxLines = 1)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(event.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = C.Home, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SymbolIcon(icon, 14.dp, C.HomeMuted)
                Text(meta, fontSize = 12.sp, color = C.HomeMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        countdown?.let {
            Text(
                it, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = C.Accent, maxLines = 1,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
    }
}

/** As many whole [items] as fit above [footer], which sits at the bottom of the space. */
@Composable
private fun <T> ItemsAboveFooter(
    items: List<T>,
    footerGap: Dp,
    footer: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    item: @Composable (item: T) -> Unit,
) {
    SubcomposeLayout(modifier) { constraints ->
        val width = Constraints(minWidth = constraints.maxWidth, maxWidth = constraints.maxWidth)
        val foot = subcompose("footer", footer).map { it.measure(width.copy(minWidth = 0)) }
        val footTop = constraints.maxHeight - (foot.maxOfOrNull { it.height } ?: 0)
        val rows = items.mapIndexed { i, it -> subcompose(i) { item(it) }.map { m -> m.measure(width) } }
        var y = 0
        val shown = rows.takeWhile { row ->
            y += row.maxOfOrNull { it.height } ?: 0
            y + footerGap.roundToPx() <= footTop
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            var top = 0
            shown.forEach { row ->
                row.forEach { it.placeRelative(0, top) }
                top += row.maxOfOrNull { it.height } ?: 0
            }
            foot.forEach { it.placeRelative(0, footTop) }
        }
    }
}

/** W3: the next day's timed events as rows, with its all-day items in one line under them. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CalendarSmall(agenda: Agenda, maxRows: Int, onEvent: (Tile) -> Unit, onEventLongPress: (Tile) -> Unit) {
    val now = rememberMinute()
    val clock = rememberClockFormat()
    val day = agenda.day(agenda.firstBusyDay(now), now)
    val summary = day.allDay.takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = "All day: ") { it.title }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (day.isEmpty) Text("Nothing planned", fontSize = 13.sp, color = C.HomeMuted)
        day.timed.take((maxRows - if (summary != null) 1 else 0).coerceAtLeast(1)).forEach { event ->
            Row(
                Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(8.dp))
                    .combinedClickable(
                        onClickLabel = "Open ${event.title}", role = Role.Button,
                        onLongClick = { onEventLongPress(event.tile()) }, onClick = { onEvent(event.tile()) },
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.size(3.dp, 26.dp).background(agenda.color(event), RoundedCornerShape(2.dp)))
                Text(
                    clock(event.start), fontSize = 13.sp, color = C.Home.copy(alpha = 0.75f), style = tabular(), maxLines = 1,
                    modifier = Modifier.width(40.dp),
                )
                Text(event.title, fontSize = 14.sp, color = C.Home, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
        }
        summary?.let { Text(it, fontSize = 12.sp, color = C.Home.copy(alpha = 0.6f), maxLines = 2, overflow = TextOverflow.Ellipsis) }
    }
}
