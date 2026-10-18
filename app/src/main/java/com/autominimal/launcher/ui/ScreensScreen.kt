package com.autominimal.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.autominimal.launcher.data.Config
import com.autominimal.launcher.data.Group
import com.autominimal.launcher.data.RankWindow
import com.autominimal.launcher.data.Screen
import com.autominimal.launcher.data.ScreenLayout
import com.autominimal.launcher.data.newId
import kotlin.math.roundToInt

/** The coloured regions of a screen's layout, one per slot. Empty slots are grey. */
@Composable
fun ScreenRegions(
    screen: Screen,
    gap: Dp,
    radius: Dp,
    alpha: Float,
    modifier: Modifier = Modifier,
    content: @Composable (slot: Int, group: Group?, color: Color) -> Unit = { _, _, _ -> },
) {
    LayoutGrid(screen.layout, gap, modifier) { slot, m ->
        val group = screen.groups.find { it.slot == slot }
        val color = if (group == null) C.Dim else regionColor(slot, group)
        Box(m.background(color.copy(alpha = alpha), RoundedCornerShape(radius)), contentAlignment = Alignment.Center) {
            content(slot, group, color)
        }
    }
}

/** S2: every screen as a thumbnail, the ranking period and the duplicate rule. */
@Composable
fun ScreensPage(
    config: Config,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
    onReorder: (Int, Int) -> Unit,
    onUpdate: ((Config) -> Config) -> Unit,
    panelIcon: (Group) -> String = { it.source.icon },
) {
    SettingsPage("Screens", onBack, top = 4.dp, gap = 14.dp) {
        ScreenStrip(config.screens, panelIcon, onOpen, onAdd, onReorder)
        Text("Tap a screen to change it. Hold and drag to reorder.", fontSize = 13.sp, color = C.Muted)
        Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TitleAndHint("Remember what I used over the past…", config.window.explanation)
            Segmented(RankWindow.entries, config.window, { w -> onUpdate { it.copy(window = w) } }, track = C.Ground, height = 40.dp) { w, color ->
                Text(w.label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = color)
            }
        }
        Row(
            Modifier.fillMaxWidth().card().clickable(role = Role.Switch) { onUpdate { it.copy(hideDuplicateApps = !it.hideDuplicateApps) } }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TitleAndHint(
                "Show each app only once per screen",
                "Apps you placed yourself won't also appear in an automatic list.",
                Modifier.weight(1f),
            )
            Toggle(config.hideDuplicateApps)
        }
    }
}

private val ThumbWidth = 104.dp
private val ThumbHeight = 132.dp

@Composable
private fun ScreenStrip(
    screens: List<Screen>,
    panelIcon: (Group) -> String,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
    onReorder: (Int, Int) -> Unit,
) {
    var draggingId by remember { mutableStateOf<String?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val widths = remember { mutableStateMapOf<String, Int>() }
    val currentScreens by rememberUpdatedState(screens)
    val currentReorder by rememberUpdatedState(onReorder)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        screens.forEach { screen ->
            key(screen.id) {
                val dragging = draggingId == screen.id
                val home = screen.home
                Column(
                    Modifier
                        .onSizeChanged { widths[screen.id] = it.width }
                        .zIndex(if (dragging) 1f else 0f)
                        .offset { IntOffset(if (dragging) offset.roundToInt() else 0, 0) }
                        .graphicsLayer { if (dragging) { scaleX = 1.04f; scaleY = 1.04f } }
                        .pointerInput(screen.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { draggingId = screen.id; offset = 0f },
                                onDragEnd = { draggingId = null; offset = 0f },
                                onDragCancel = { draggingId = null; offset = 0f },
                            ) { change, drag ->
                                change.consume()
                                offset += drag.x
                                val list = currentScreens
                                val i = list.indexOfFirst { it.id == screen.id }
                                if (i < 0) return@detectDragGesturesAfterLongPress
                                if (offset > 0 && i < list.lastIndex) {
                                    val next = widths[list[i + 1].id] ?: return@detectDragGesturesAfterLongPress
                                    if (offset > next / 2f) { currentReorder(i, i + 1); offset -= next + 14.dp.toPx() }
                                } else if (offset < 0 && i > 0) {
                                    val prev = widths[list[i - 1].id] ?: return@detectDragGesturesAfterLongPress
                                    if (-offset > prev / 2f) { currentReorder(i, i - 1); offset += prev + 14.dp.toPx() }
                                }
                            }
                        }
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(role = Role.Button, onClickLabel = "Change ${screen.name}") { onOpen(screen.id) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        Modifier.size(ThumbWidth, ThumbHeight).background(C.Card, RoundedCornerShape(14.dp))
                            .border(if (home) 2.dp else 1.dp, if (home) C.Accent else C.Outline, RoundedCornerShape(14.dp))
                            .padding(8.dp),
                    ) {
                        ScreenRegions(screen, 5.dp, 6.dp, 0.22f, Modifier.fillMaxSize()) { _, group, color ->
                            if (group != null && group.source.isPanel && screen.layout == ScreenLayout.SINGLE) {
                                SymbolIcon(panelIcon(group), 24.dp, color)
                            }
                        }
                    }
                    Row(Modifier.width(ThumbWidth), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                        if (home) SymbolIcon(Sym.Home, 16.dp, C.Accent, filled = true)
                        Text(
                            screen.name, fontSize = 13.sp, fontWeight = if (home) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (home) C.Accent else C.Soft, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        Column(
            Modifier.clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClick = onAdd),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier.size(ThumbWidth, ThumbHeight).dashedBorder(C.Border, 14.dp),
                contentAlignment = Alignment.Center,
            ) { SymbolIcon(Sym.Add, 28.dp, C.Muted) }
            Text("Add screen", fontSize = 13.sp, color = C.Muted)
        }
    }
}

/** S3: the layout of one screen, and what fills each block. */
@Composable
fun ScreenEditor(
    screen: Screen,
    canRemove: Boolean,
    onBack: () -> Unit,
    onRename: (String) -> Unit,
    onRemove: () -> Unit,
    onLayout: (ScreenLayout) -> Unit,
    onReorderSlots: (Int, Int) -> Unit,
    onOpenSlot: (Int) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmRemove by rememberSaveable { mutableStateOf(false) }

    SettingsPage(screen.name, onBack, gap = 0.dp, trailing = {
        Box {
            Box(
                Modifier.size(38.dp).clip(CircleShape).clickable(onClickLabel = "More options", role = Role.Button) { menu = true },
                contentAlignment = Alignment.Center,
            ) { SymbolIcon(Sym.MoreVert, 22.dp, C.Soft, description = "More options") }
            DropdownMenu(menu, { menu = false }, containerColor = C.Raised, shape = RoundedCornerShape(14.dp)) {
                DropdownMenuItem({ Text("Rename", fontSize = 15.sp) }, { menu = false; renaming = true })
                if (canRemove) DropdownMenuItem({ Text("Remove screen", fontSize = 15.sp, color = C.Danger) }, { menu = false; confirmRemove = true })
            }
        }
    }) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(Modifier.size(150.dp, 190.dp).background(C.Card, RoundedCornerShape(16.dp)).padding(10.dp)) {
                LayoutGrid(screen.layout, 6.dp, Modifier.fillMaxSize()) { slot, m ->
                    val group = screen.groups.find { it.slot == slot }
                    val color = if (group == null) C.Dim else regionColor(slot, group)
                    Box(
                        m.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.15f)).border(1.dp, color, RoundedCornerShape(8.dp))
                            .clickable(onClickLabel = "Change ${group?.title ?: "empty block"}") { onOpenSlot(slot) }.padding(6.dp),
                        contentAlignment = Alignment.BottomStart,
                    ) {
                        Text(group?.title ?: "Empty", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FieldLabel("Layout")
                ScreenLayout.entries.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { layout ->
                            val selected = layout == screen.layout
                            Box(
                                Modifier.weight(1f).height(58.dp).clip(RoundedCornerShape(10.dp))
                                    .border(if (selected) 2.dp else 1.dp, if (selected) C.Accent else C.Outline, RoundedCornerShape(10.dp))
                                    .clickable(onClickLabel = layout.label, role = Role.RadioButton) { onLayout(layout) }
                                    .padding(5.dp),
                            ) {
                                LayoutGrid(layout, 3.dp, Modifier.fillMaxSize()) { slot, m ->
                                    Box(m.background(regionColor(slot, null).copy(alpha = 0.55f), RoundedCornerShape(3.dp)))
                                }
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }

        FieldLabel("On this screen", Modifier.padding(top = 20.dp, bottom = 6.dp))
        val count = screen.layout.left + screen.layout.right
        val occupants = (0 until count).map { slot -> screen.groups.find { it.slot == slot } }
        var rowKeys by remember(screen.id, count) { mutableStateOf(emptyList<String>()) }
        val synced = slotRowKeys(rowKeys, occupants)
        if (synced != rowKeys) rowKeys = synced
        val rows = occupants.mapIndexed { index, group -> SlotRow(synced[index], index, group) }
        ReorderableColumn(rows, keyOf = { it.key }, dragColor = C.Card, onMove = { from, to ->
            rowKeys = rowKeys.toMutableList().apply { add(to, removeAt(from)) }
            onReorderSlots(from, to)
        }) { row, handle, _ ->
            val group = row.group
            Column(Modifier.clickable(onClickLabel = "Change ${group?.title ?: "empty block"}") { onOpenSlot(row.slot) }) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(C.Hair))
                Row(
                    Modifier.fillMaxWidth().height(58.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(Modifier.size(12.dp).background(if (group == null) C.Dim else regionColor(row.slot, group), RoundedCornerShape(4.dp)))
                    Column(Modifier.weight(1f)) {
                        Text(group?.title ?: "Empty", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = if (group == null) C.Muted else C.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(group?.rule ?: "Tap to choose what goes here", fontSize = 13.sp, color = C.Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (count > 1) {
                        Box(handle.size(32.dp, 58.dp), contentAlignment = Alignment.Center) {
                            SymbolIcon(Sym.DragIndicator, 20.dp, C.Dim, description = "Drag to reorder")
                        }
                    }
                    SymbolIcon(Sym.ChevronRight, 20.dp, C.Muted)
                }
            }
        }
    }

    if (renaming) {
        var name by rememberSaveable { mutableStateOf(screen.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            containerColor = C.Card,
            shape = RoundedCornerShape(24.dp),
            title = { Text("Rename screen", fontSize = 19.sp, fontWeight = FontWeight.SemiBold) },
            text = { TextInput(name, { name = it }, Modifier.fillMaxWidth()) },
            confirmButton = { TextButton({ onRename(name); renaming = false }) { Text("Save", color = C.Accent, fontWeight = FontWeight.Medium) } },
            dismissButton = { TextButton({ renaming = false }) { Text("Cancel", color = C.Soft) } },
        )
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            containerColor = C.Card,
            shape = RoundedCornerShape(24.dp),
            title = { Text("Remove ${screen.name}?", fontSize = 19.sp, fontWeight = FontWeight.SemiBold) },
            text = { Text("Its blocks are removed too. Your apps stay installed.", fontSize = 14.sp, color = C.Sub) },
            confirmButton = { TextButton({ confirmRemove = false; onRemove() }) { Text("Remove", color = C.Danger, fontWeight = FontWeight.Medium) } },
            dismissButton = { TextButton({ confirmRemove = false }) { Text("Cancel", color = C.Soft) } },
        )
    }
}

private data class SlotRow(val key: String, val slot: Int, val group: Group?)

/** Keeps a stable key for each visible row, including slots the layout leaves empty. */
private fun slotRowKeys(previous: List<String>, occupants: List<Group?>): List<String> {
    if (previous.size != occupants.size) return occupants.map { it?.id ?: newId() }
    return occupants.mapIndexed { index, group ->
        if (group != null) group.id
        else previous[index].takeIf { key -> key.isNotEmpty() && occupants.none { it?.id == key } } ?: newId()
    }
}
