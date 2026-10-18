package com.autominimal.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autominimal.launcher.data.AppInfo
import com.autominimal.launcher.data.CornerShortcut
import com.autominimal.launcher.data.IconCache

/** Picks apps for "I'll choose": picked ones on top (drag to sort), everything else below. */
@Composable
fun AppPickerScreen(
    apps: List<AppInfo>,
    picked: List<String>,
    icons: IconCache,
    onChange: (List<String>) -> Unit,
    onDone: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val byPackage = apps.associateBy { it.packageName }
    val pickedApps = picked.mapNotNull { byPackage[it] }
    val rest = apps.filter { it.packageName !in picked && (query.isBlank() || it.label.contains(query.trim(), ignoreCase = true)) }

    SettingsPage("Add apps", onDone, gap = 14.dp) {
        if (pickedApps.isNotEmpty()) {
            FieldLabel(if (pickedApps.size > 1) "Picked · hold the handle to sort" else "Picked")
            Column(Modifier.fillMaxWidth().card(16.dp).padding(horizontal = 14.dp, vertical = 4.dp)) {
                ReorderableColumn(
                    pickedApps, keyOf = { it.packageName },
                    onMove = { from, to -> onChange(picked.filter { it in byPackage }.toMutableList().apply { add(to, removeAt(from)) }) },
                ) { app, handle, _ ->
                    Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(handle.size(24.dp, 44.dp), contentAlignment = Alignment.Center) {
                            SymbolIcon(Sym.DragIndicator, 18.dp, C.Dim, description = "Drag to reorder")
                        }
                        AppIconImage(app, icons, 26.dp, 7.dp)
                        Text(app.label, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Box(
                            Modifier.size(32.dp).clip(RoundedCornerShape(16.dp)).clickable(onClickLabel = "Remove ${app.label}") { onChange(picked - app.packageName) },
                            contentAlignment = Alignment.Center,
                        ) { SymbolIcon(Sym.Close, 18.dp, C.Muted) }
                    }
                }
            }
        }
        SearchField(query, { query = it }, "Search ${apps.size} apps") {}
        Column {
            rest.forEach { app ->
                AppRow(app, icons, "Add ${app.label}", { onChange(picked + app.packageName) }) {
                    SymbolIcon(Sym.Add, 22.dp, C.Accent)
                }
            }
        }
    }
}

/** Chooses the app a bottom shortcut opens. */
@Composable
fun ShortcutAppPicker(
    shortcut: CornerShortcut,
    apps: List<AppInfo>,
    current: String?,
    icons: IconCache,
    onPick: (String?) -> Unit,
    onBack: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown = apps.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
    SettingsPage("${shortcut.label} button opens", onBack, gap = 14.dp) {
        Row(
            Modifier.fillMaxWidth().card(16.dp).clickable(role = Role.RadioButton) { onPick(null) }.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(32.dp).background(C.Raised, RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
                SymbolIcon(shortcut.icon, 20.dp, C.Text)
            }
            TitleAndHint("The usual ${shortcut.label} app", "What this phone uses by default", Modifier.weight(1f), size = 15.sp, gap = 0.dp)
            RadioMark(current == null)
        }
        SearchField(query, { query = it }, "Search ${apps.size} apps") {}
        Column {
            shown.forEach { app ->
                AppRow(app, icons, "Use ${app.label}", { onPick(app.packageName) }) { RadioMark(app.packageName == current) }
            }
        }
    }
}

@Composable
private fun AppRow(app: AppInfo, icons: IconCache, label: String, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(10.dp)).clickable(onClickLabel = label, role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppIconImage(app, icons, 32.dp, 9.dp)
        Text(app.label, fontSize = 16.sp, fontWeight = FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        trailing()
    }
}
