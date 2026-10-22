package com.autominimal.launcher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.autominimal.launcher.data.AppInfo
import com.autominimal.launcher.data.IconCache
import kotlinx.coroutines.launch
import java.text.Normalizer

const val PLAY_STORE = "com.android.vending"

private data class Section(val letter: String, val apps: List<AppInfo>)

/** Most used apps on top, then every app A–Z with a letter index. Long-press opens the app sheet. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AllAppsScreen(
    apps: List<AppInfo>,
    topApps: List<AppInfo>,
    hidden: Set<String>,
    icons: IconCache,
    onLaunch: (AppInfo) -> Unit,
    onLongPress: (AppInfo) -> Unit,
    onPlayStore: (String) -> Unit,
    onWebSearch: (String) -> Unit,
    onClose: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val currentOnClose by rememberUpdatedState(onClose)
    // Open with the keyboard up, so typing starts the search right away.
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { searchFocus.requestFocus() }

    val visible = remember(apps, query) {
        val q = query.normalized()
        apps.filter { app -> q.isEmpty() || app.label.normalized().contains(q) }
    }
    val sections = remember(visible) {
        visible.groupBy { it.label.firstOrNull()?.uppercaseChar()?.takeIf(Char::isLetter)?.toString() ?: "#" }
            .map { (letter, list) -> Section(letter, list) }
            .sortedWith(compareBy({ it.letter == "#" }, { it.letter }))
    }

    val listState = rememberLazyListState()
    // Pulling down while the list is already at the top closes the drawer.
    val closeOnPull = remember {
        object : NestedScrollConnection {
            var pulled = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0) {
                    pulled += available.y
                    if (pulled > 220f) { pulled = 0f; currentOnClose() }
                } else if (consumed.y != 0f) pulled = 0f
                return Offset.Zero
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(C.Ground).statusBarsPadding().navigationBarsPadding().imePadding()
            .pointerInput(Unit) {
                var pulled = 0f
                detectVerticalDragGestures(onDragStart = { pulled = 0f }, onDragEnd = { if (pulled > 160f) currentOnClose() }) { _, dy -> pulled += dy }
            },
    ) {
        SearchField(
            query, { query = it }, "Search ${apps.size} apps",
            Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp).focusRequester(searchFocus),
        ) {
            visible.firstOrNull()?.let(onLaunch)
            focus.clearFocus()
        }

        if (visible.isEmpty() && query.isNotBlank()) {
            NoMatch(query.trim(), icons, onPlayStore, onWebSearch)
            return@Column
        }

        if (query.isEmpty() && topApps.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                topApps.take(5).forEach { app ->
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).combinedClickable(
                            onClickLabel = "Open ${app.label}", role = Role.Button,
                            onLongClick = { onLongPress(app) }, onClick = { onLaunch(app) },
                        ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        AppIconImage(app, icons, 44.dp, 13.dp)
                        Text(app.label, fontSize = 12.sp, color = C.Soft, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    }
                }
                repeat(5 - topApps.size.coerceAtMost(5)) { Spacer(Modifier.weight(1f)) }
            }
        }

        Row(Modifier.weight(1f).fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LazyColumn(
                Modifier.weight(1f).fillMaxSize().nestedScroll(closeOnPull),
                state = listState,
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(sections, key = { it.letter }) { section ->
                    Column {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(C.Hair))
                        Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(
                                section.letter, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = C.Accent,
                                modifier = Modifier.width(16.dp).padding(top = 12.dp),
                            )
                            Column(Modifier.weight(1f)) {
                                section.apps.forEach { app ->
                                    Row(
                                        Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(10.dp))
                                            .combinedClickable(
                                                onClickLabel = "Open ${app.label}", role = Role.Button,
                                                onLongClick = { onLongPress(app) }, onClick = { onLaunch(app) },
                                            ),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    ) {
                                        AppIconImage(app, icons, 32.dp, 9.dp)
                                        Text(app.label, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                        if (app.packageName in hidden) {
                                            SymbolIcon(Sym.VisibilityOff, 18.dp, C.Faint, Modifier.padding(end = 8.dp), description = "Not shown on home")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (sections.size > 1) {
                LetterIndex(sections.map { it.letter }) { i -> scope.launch { listState.scrollToItem(i) } }
            }
        }
    }
}

/** Letters down the right edge. Tap or slide along them to jump. */
@Composable
private fun LetterIndex(letters: List<String>, onLetter: (Int) -> Unit) {
    val currentOnLetter by rememberUpdatedState(onLetter)
    BoxWithConstraints(Modifier.width(14.dp)) {
        val cell = min(13.dp, (maxHeight - 6.dp) / letters.size)
        Column(
            Modifier.fillMaxWidth().padding(top = 6.dp)
                .pointerInput(letters, cell) {
                    detectTapGestures { currentOnLetter((it.y / cell.toPx()).toInt().coerceIn(0, letters.lastIndex)) }
                }
                .pointerInput(letters, cell) {
                    detectVerticalDragGestures { change, _ ->
                        change.consume()
                        currentOnLetter((change.position.y / cell.toPx()).toInt().coerceIn(0, letters.lastIndex))
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            letters.forEach { letter ->
                Box(Modifier.height(cell), contentAlignment = Alignment.Center) {
                    Text(letter, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = C.Faint)
                }
            }
        }
    }
}

/** "No apps called …", with a way to look further instead of a dead end. */
@Composable
private fun NoMatch(query: String, icons: IconCache, onPlayStore: (String) -> Unit, onWebSearch: (String) -> Unit) {
    Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("No apps called \"$query\"", fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Text("Try a different spelling, or look further:", fontSize = 14.sp, color = C.Sub)
        }
        Column(Modifier.fillMaxWidth().card(18.dp)) {
            FurtherRow("Find \"$query\" in Play Store", { onPlayStore(query) }) { PackageIcon(PLAY_STORE, icons, 32.dp, 9.dp) }
            Box(Modifier.fillMaxWidth().height(1.dp).background(C.Raised))
            FurtherRow("Search the web for \"$query\"", { onWebSearch(query) }) {
                Box(Modifier.size(32.dp).background(C.Raised, RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
                    SymbolIcon(Sym.Language, 20.dp, C.Soft)
                }
            }
        }
    }
}

@Composable
private fun FurtherRow(label: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(60.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        icon()
        Text(label, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        SymbolIcon(Sym.NorthEast, 20.dp, C.Faint)
    }
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, onGo: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        interactionSource = interaction,
        textStyle = TextStyle(fontFamily = LocalAppFont.current, fontSize = 16.sp, color = C.Text),
        cursorBrush = SolidColor(C.Accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onGo() }),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(16.dp)).background(C.Card)
                    .then(if (focused || value.isNotEmpty()) Modifier.border(1.dp, C.Border, RoundedCornerShape(16.dp)) else Modifier)
                    .padding(start = 16.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SymbolIcon(Sym.Search, 22.dp, C.Muted)
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, fontSize = 16.sp, color = C.Muted, maxLines = 1)
                    inner()
                }
                if (value.isNotEmpty()) {
                    Box(
                        Modifier.size(32.dp).clip(RoundedCornerShape(16.dp)).clickable(onClickLabel = "Clear search") { onChange("") },
                        contentAlignment = Alignment.Center,
                    ) { SymbolIcon(Sym.Close, 20.dp, C.Muted) }
                }
            }
        },
    )
}

private fun String.normalized(): String =
    Normalizer.normalize(lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").trim()
