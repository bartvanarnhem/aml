package com.autominimal.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.autominimal.launcher.data.AppInfo
import com.autominimal.launcher.data.BackgroundRepository
import com.autominimal.launcher.data.Channel
import com.autominimal.launcher.data.Group
import com.autominimal.launcher.data.IconCache
import com.autominimal.launcher.data.Person
import com.autominimal.launcher.data.ScreenLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** A Material Symbols glyph from [Sym], drawn [size] dp square regardless of the font scale. */
@Composable
fun SymbolIcon(
    icon: String,
    size: Dp,
    color: Color,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    description: String? = null,
) {
    val fontSize = with(LocalDensity.current) { size.toSp() }
    val semantics = if (description != null) Modifier.semantics { contentDescription = description } else Modifier.clearAndSetSemantics {}
    Box(modifier.size(size).then(semantics), contentAlignment = Alignment.Center) {
        Text(
            icon,
            style = TextStyle(
                fontFamily = if (filled) SymbolsFilled else Symbols,
                fontSize = fontSize,
                lineHeight = fontSize,
                color = color,
                platformStyle = Tight,
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
            ),
        )
    }
}

/** Settings page: a 60 dp header with back arrow and title, then a scrolling column. */
@Composable
fun SettingsPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    top: Dp = 0.dp,
    gap: Dp = 16.dp,
    leading: (@Composable () -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxSize().background(C.Ground).statusBarsPadding().navigationBarsPadding()) {
        PageHeader(title, onBack, leading, trailing)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = top, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(gap),
            content = content,
        )
    }
}

@Composable
fun PageHeader(
    title: String,
    onBack: () -> Unit,
    leading: (@Composable () -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).clickable(onClickLabel = "Back", role = Role.Button, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { SymbolIcon(Sym.ArrowBack, 24.dp, C.Text, description = "Back") }
        leading?.invoke()
        Text(
            title, fontSize = 22.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

/** Small grey heading above a group of options, e.g. "Text size". */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = C.Sub, modifier = modifier)
}

/** Title with a grey line under it. */
@Composable
fun TitleAndHint(title: String, hint: String?, modifier: Modifier = Modifier, size: TextUnit = 16.sp, hintColor: Color = C.Sub, gap: Dp = 2.dp) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        Text(title, fontSize = size, fontWeight = FontWeight.Medium)
        if (hint != null) Text(hint, fontSize = 13.sp, color = hintColor)
    }
}

fun Modifier.card(radius: Dp = 20.dp, color: Color = C.Card) = clip(RoundedCornerShape(radius)).background(color)

/** 44 × 26 switch: accent track when on, dark knob. */
@Composable
fun Toggle(checked: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier.size(44.dp, 26.dp).clip(RoundedCornerShape(13.dp)).background(if (checked) C.Accent else C.Border).padding(3.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.offset(x = if (checked) 18.dp else 0.dp).size(20.dp).background(C.Ground, CircleShape))
    }
}

@Composable
fun RadioMark(selected: Boolean) {
    Box(
        Modifier.size(20.dp).border(2.dp, if (selected) C.Accent else C.Dim, CircleShape),
        contentAlignment = Alignment.Center,
    ) { if (selected) Box(Modifier.size(10.dp).background(C.Accent, CircleShape)) }
}

/** A dark track with the selected option filled light. */
@Composable
fun <T> Segmented(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    track: Color = C.Card,
    height: Dp = 44.dp,
    content: @Composable RowScope.(option: T, color: Color) -> Unit,
) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(track).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val on = option == selected
            Row(
                Modifier.weight(1f).height(height).clip(RoundedCornerShape(10.dp))
                    .background(if (on) C.Text else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(option) },
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) { content(option, if (on) C.Ground else C.Soft) }
        }
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, height: Dp = 38.dp, fontSize: TextUnit = 15.sp, padding: Dp = 16.dp) {
    Box(
        Modifier.height(height).clip(RoundedCornerShape(height / 2)).background(if (selected) C.Text else C.Card)
            .clickable(role = Role.RadioButton, onClick = onClick).padding(horizontal = padding),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontSize = fontSize, color = if (selected) C.Ground else C.Soft) }
}

/** Filled accent button with an optional icon; shows a spinner while [loading]. */
@Composable
fun AccentButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: String? = null,
    height: Dp = 52.dp,
    fontSize: TextUnit = 16.sp,
    iconSize: Dp = 20.dp,
    enabled: Boolean = true,
    loading: Boolean = false,
    padding: Dp = 18.dp,
) {
    Row(
        modifier.height(height).clip(RoundedCornerShape(height / 2))
            .background(if (enabled) C.Accent else C.Accent.copy(alpha = 0.5f))
            .clickable(enabled = enabled && !loading, role = Role.Button, onClick = onClick)
            .padding(horizontal = padding),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(iconSize - 2.dp), color = C.Card, strokeWidth = 2.dp)
        else if (icon != null) SymbolIcon(icon, iconSize, C.Card)
        Text(text, fontSize = fontSize, fontWeight = FontWeight.SemiBold, color = C.Card)
    }
}

/** Thin track with an accent fill and a round thumb. [onDone] fires when the finger lifts. */
@Composable
fun AccentSlider(
    value: Float,
    onChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    onDone: () -> Unit = {},
) {
    val currentOnChange by rememberUpdatedState(onChange)
    val currentOnDone by rememberUpdatedState(onDone)
    val span = range.endInclusive - range.start
    val fraction = ((value - range.start) / span).coerceIn(0f, 1f)
    Canvas(
        modifier.height(28.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value, range)
                setProgress { v -> currentOnChange(v.coerceIn(range)); currentOnDone(); true }
            }
            .pointerInput(range) {
                val thumb = 8.dp.toPx()
                fun at(x: Float) = range.start + ((x - thumb) / (size.width - thumb * 2)).coerceIn(0f, 1f) * span
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    currentOnChange(at(down.position.x))
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        currentOnChange(at(change.position.x))
                    }
                    currentOnDone()
                }
            },
    ) {
        val thumb = 8.dp.toPx()
        val track = 4.dp.toPx()
        val y = size.height / 2
        val x = thumb + fraction * (size.width - thumb * 2)
        drawRoundRect(C.Border, Offset(0f, y - track / 2), Size(size.width, track), CornerRadius(track / 2))
        drawRoundRect(C.Accent, Offset(0f, y - track / 2), Size(x, track), CornerRadius(track / 2))
        drawCircle(C.Accent, thumb, Offset(x, y))
    }
}

fun Modifier.dashedBorder(color: Color, radius: Dp, width: Dp = 1.5.dp) = drawBehind {
    drawRoundRect(
        color = color, cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(width = width.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
    )
}

/** Single-line field on a card background. */
@Composable
fun TextInput(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    secret: Boolean = false,
) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(fontFamily = LocalAppFont.current, fontSize = 16.sp, color = C.Text),
        cursorBrush = SolidColor(C.Accent),
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = modifier,
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(16.dp)).background(C.Card)
                    .border(1.dp, C.Border, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty() && placeholder != null) Text(placeholder, fontSize = 16.sp, color = C.Muted)
                inner()
            }
        },
    )
}

@Composable
fun AppIconImage(app: AppInfo, icons: IconCache, size: Dp, radius: Dp = size * 0.29f) {
    val bitmap = rememberBitmap(app.component, initial = icons.peek(app.component)) { icons.load(app.component) }
    IconBitmap(bitmap, size, radius)
}

/** Launcher icon of a package, e.g. Play Store. */
@Composable
fun PackageIcon(packageName: String, icons: IconCache, size: Dp, radius: Dp = size * 0.29f) {
    val bitmap = rememberBitmap(packageName, initial = icons.peekPackage(packageName)) { icons.loadPackage(packageName) }
    IconBitmap(bitmap, size, radius)
}

@Composable
private fun IconBitmap(bitmap: ImageBitmap?, size: Dp, radius: Dp) {
    val shape = RoundedCornerShape(radius)
    if (bitmap != null) Image(bitmap, contentDescription = null, modifier = Modifier.size(size).clip(shape))
    else Box(Modifier.size(size).background(C.Raised, shape))
}

/** Loads a bitmap off the main thread, keyed by [key]; [initial] skips loading when already cached. */
@Composable
fun rememberBitmap(key: Any?, initial: ImageBitmap? = null, load: () -> ImageBitmap?): ImageBitmap? {
    var bitmap by remember(key) { mutableStateOf(initial) }
    LaunchedEffect(key) { if (bitmap == null) bitmap = withContext(Dispatchers.IO) { load() } }
    return bitmap
}

/** An image file or bundled wallpaper decoded to roughly [maxPx] on its longer side, or null while loading. */
@Composable
fun rememberImageFile(path: String?, maxPx: Int): ImageBitmap? {
    val context = LocalContext.current
    return rememberBitmap(path to maxPx) { path?.let { BackgroundRepository.decode(context, it, maxPx)?.asImageBitmap() } }
}

/** Initials in a solid circle, used in the person sheet. */
@Composable
fun PersonAvatar(person: Person, size: Dp) {
    Box(Modifier.size(size).background(C.Avatar, CircleShape), contentAlignment = Alignment.Center) {
        Text(
            person.initials, fontSize = (size.value * 0.385f).sp,
            fontWeight = FontWeight.SemiBold, color = C.Text, maxLines = 1,
        )
    }
}

/** Colour linking a block to its place in the layout. Calendar and weather blocks are blue. */
fun regionColor(slot: Int, group: Group?): Color = when {
    group?.source?.isPanel == true -> C.Blue
    else -> C.Regions[slot.coerceIn(0, C.Regions.lastIndex)]
}

/** Colour of the calendar at [index] in the calendar list, for dots and event bars. */
fun calendarColor(index: Int): Color = C.Calendars[index.mod(C.Calendars.size)]

/** Lays out the cells of a screen layout. Slots count down the left column, then down the right. */
@Composable
fun LayoutGrid(layout: ScreenLayout, gap: Dp, modifier: Modifier = Modifier, cell: @Composable (slot: Int, modifier: Modifier) -> Unit) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(gap)) {
        val column = Modifier.weight(1f).fillMaxHeight()
        @Composable
        fun Stack(first: Int, count: Int) {
            if (count == 1) cell(first, column)
            else Column(column, verticalArrangement = Arrangement.spacedBy(gap)) {
                repeat(count) { cell(first + it, Modifier.weight(1f).fillMaxWidth()) }
            }
        }
        Stack(0, layout.left)
        if (layout.right > 0) Stack(layout.left, layout.right)
    }
}

/** Text centred in a box, for tile labels like "Aa". */
@Composable
fun CenteredText(text: String, size: TextUnit, modifier: Modifier = Modifier, color: Color = C.Text, weight: FontWeight = FontWeight.Normal) {
    Text(text, fontSize = size, color = color, fontWeight = weight, textAlign = TextAlign.Center, modifier = modifier, maxLines = 1)
}

/**
 * A column whose rows can be reordered by dragging the handle each row receives as a modifier.
 * [onMove] is called with (from, to) as soon as a row crosses its neighbour's midpoint.
 */
@Composable
fun <T> ReorderableColumn(
    items: List<T>,
    keyOf: (T) -> Any,
    onMove: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    dragColor: Color = C.Raised,
    row: @Composable (item: T, handle: Modifier, dragging: Boolean) -> Unit,
) {
    var draggingKey by remember { mutableStateOf<Any?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<Any, Int>() }
    val currentItems by rememberUpdatedState(items)
    val currentOnMove by rememberUpdatedState(onMove)

    Column(modifier) {
        items.forEach { item ->
            val k = keyOf(item)
            // Keyed so a row keeps its gesture state while it moves to a new index mid-drag.
            key(k) {
                val dragging = draggingKey == k
                val handle = Modifier.pointerInput(k) {
                    detectDragGestures(
                        onDragStart = { draggingKey = k; offset = 0f },
                        onDragEnd = { draggingKey = null; offset = 0f },
                        onDragCancel = { draggingKey = null; offset = 0f },
                    ) { change, drag ->
                        change.consume()
                        offset += drag.y
                        val list = currentItems
                        val i = list.indexOfFirst { keyOf(it) == k }
                        if (i < 0) return@detectDragGestures
                        if (offset > 0 && i < list.lastIndex) {
                            val next = heights[keyOf(list[i + 1])] ?: return@detectDragGestures
                            if (offset > next / 2f) { currentOnMove(i, i + 1); offset -= next }
                        } else if (offset < 0 && i > 0) {
                            val prev = heights[keyOf(list[i - 1])] ?: return@detectDragGestures
                            if (-offset > prev / 2f) { currentOnMove(i, i - 1); offset += prev }
                        }
                    }
                }
                Box(
                    Modifier.onSizeChanged { heights[k] = it.height }
                        .zIndex(if (dragging) 1f else 0f)
                        .offset { IntOffset(0, if (dragging) offset.roundToInt() else 0) }
                        .graphicsLayer { if (dragging) shadowElevation = 12f }
                        .background(if (dragging) dragColor else Color.Transparent)
                ) { row(item, handle, dragging) }
            }
        }
    }
}
