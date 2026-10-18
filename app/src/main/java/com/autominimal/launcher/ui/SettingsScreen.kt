package com.autominimal.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autominimal.launcher.data.AppFont
import com.autominimal.launcher.data.AppInfo
import com.autominimal.launcher.data.Background
import com.autominimal.launcher.data.BackgroundKind
import com.autominimal.launcher.data.Config
import com.autominimal.launcher.data.CornerShortcut
import com.autominimal.launcher.data.IconCache
import com.autominimal.launcher.data.ListSize
import com.autominimal.launcher.data.PredefinedWallpapers

/** Which permissions are granted, re-read whenever the launcher resumes. */
data class PermissionStatus(
    val isDefaultHome: Boolean = false,
    val usage: Boolean = false,
    val callLog: Boolean = false,
    val contacts: Boolean = false,
    val notifications: Boolean = false,
    val calendar: Boolean = false,
    val location: Boolean = false,
) {
    private val checks get() = listOf(isDefaultHome, usage, callLog, contacts, notifications, calendar, location)
    val allGranted get() = checks.all { it }
    val grantedCount get() = checks.count { it }
    val total get() = checks.size

    fun granted(access: Access) = when (access) {
        Access.USAGE -> usage
        Access.CALLS -> callLog
        Access.CONTACTS -> contacts
        Access.MESSAGES -> notifications
        Access.CALENDAR -> calendar
        Access.LOCATION -> location
        Access.HOME -> isDefaultHome
    }
}

/**
 * Everything the launcher can be allowed to see, worded by what it gives you.
 * In the order setup asks: system popups first, then pages switched on by hand, then the default launcher.
 */
enum class Access(val icon: String, val title: String, val hint: String) {
    CALLS(Sym.Call, "Call history", "To show the people you call most"),
    CONTACTS(Sym.Contacts, "Contacts", "To show names and find WhatsApp chats"),
    CALENDAR(Sym.CalendarMonth, "Calendar", "To show your next events"),
    LOCATION(Sym.LocationOn, "Location", "To show the weather where you are"),
    USAGE(Sym.BarChart, "App usage", "To put your most used apps on top"),
    MESSAGES(Sym.Chat, "Message counts", "Counts messages per chat. Never reads them."),
    HOME(Sym.Home, "Default launcher", "Opens when you press home"),
}

val CornerShortcut.icon: String
    get() = when (this) {
        CornerShortcut.PHONE -> Sym.Call
        CornerShortcut.WHATSAPP -> Sym.ChatBubble
        CornerShortcut.CAMERA -> Sym.PhotoCamera
        CornerShortcut.PHOTOS -> Sym.Image
    }

/** S1: whether this is the home app, then four tiles that each show what they open. */
@Composable
fun SettingsHome(
    config: Config,
    permissions: PermissionStatus,
    onBack: () -> Unit,
    onDefaultHome: () -> Unit,
    onScreens: () -> Unit,
    onLook: () -> Unit,
    onShortcuts: () -> Unit,
    onPrivacy: () -> Unit,
) {
    SettingsPage("Settings", onBack, top = 8.dp, gap = 12.dp) {
        Row(
            Modifier.fillMaxWidth().card(18.dp)
                .clickable(enabled = !permissions.isDefaultHome, role = Role.Button, onClick = onDefaultHome)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (permissions.isDefaultHome) {
                SymbolIcon(Sym.CheckCircle, 24.dp, C.Green, filled = true)
                TitleAndHint("This is your home screen app", "Pressing the home button brings you here", Modifier.weight(1f), size = 15.sp, gap = 0.dp)
            } else {
                SymbolIcon(Sym.Home, 24.dp, C.Accent)
                TitleAndHint("Make this your home screen app", "So the home button brings you here", Modifier.weight(1f), size = 15.sp, gap = 0.dp)
                SymbolIcon(Sym.ChevronRight, 20.dp, C.Muted)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val n = config.screens.size
            SettingsTile("Screens", "$n ${if (n == 1) "screen" else "screens"} · what's on them", onScreens, Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    config.screens.take(3).forEach { screen ->
                        Box(
                            Modifier.size(56.dp, 68.dp)
                                .border(if (screen.home) 1.5.dp else 1.dp, if (screen.home) C.Text else C.Border, RoundedCornerShape(8.dp))
                                .padding(4.dp),
                        ) { ScreenRegions(screen, 3.dp, 3.dp, 0.8f, Modifier.fillMaxSize()) }
                    }
                }
            }
            SettingsTile("Look and feel", "Background, text size, font", onLook, Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    WallpaperThumb(config.background, Modifier.size(56.dp, 68.dp), 8.dp)
                    Text("Aa", fontSize = 36.sp, lineHeight = 36.sp)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsTile("Bottom shortcuts", "Quick buttons along the bottom", onShortcuts, Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val enabled = CornerShortcut.entries.filter { it in config.shortcuts }
                    (enabled.map { it.icon }.ifEmpty { listOf(Sym.Apps) }).forEach { icon ->
                        Box(Modifier.size(34.dp).background(C.Raised, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                            SymbolIcon(icon, 20.dp, C.Text)
                        }
                    }
                }
            }
            SettingsTile("Privacy", "What the launcher can see", onPrivacy, Modifier.weight(1f)) {
                val all = permissions.allGranted
                val tint = if (all) C.Green else C.Accent
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(44.dp).background(if (all) C.GreenDeep else C.Raised, CircleShape), contentAlignment = Alignment.Center) {
                        SymbolIcon(Sym.ShieldLock, 24.dp, tint)
                    }
                    Text(
                        if (all) "All ${permissions.total} allowed" else "${permissions.grantedCount} of ${permissions.total} allowed",
                        fontSize = 13.sp, fontWeight = FontWeight.Medium, color = tint,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsTile(title: String, hint: String, onClick: () -> Unit, modifier: Modifier, art: @Composable BoxScope.() -> Unit) {
    Column(
        modifier.height(168.dp).card(20.dp).clickable(role = Role.Button, onClick = onClick).padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(content = art)
        TitleAndHint(title, hint)
    }
}

/** The current wallpaper cropped into a small rectangle; plain backgrounds show the card colour. */
@Composable
fun WallpaperThumb(background: Background, modifier: Modifier, radius: Dp) {
    val path = background.path?.takeIf { background.kind != BackgroundKind.NONE }
    val bitmap = rememberImageFile(path, 320)
    Box(modifier.clip(RoundedCornerShape(radius)).background(C.Raised)) {
        bitmap?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

/** The wallpaper filling a box, darkened evenly by [dim] percent. */
@Composable
fun WallpaperPreview(background: Background, dim: Float, modifier: Modifier, alignment: Alignment = Alignment.BottomCenter, content: @Composable BoxScope.() -> Unit) {
    val path = background.path?.takeIf { background.kind != BackgroundKind.NONE }
    val bitmap = rememberImageFile(path, 900)
    Box(modifier.background(Color(0xFF3A2A2E))) {
        bitmap?.let { Image(it, null, contentScale = ContentScale.Crop, alignment = alignment, modifier = Modifier.fillMaxSize()) }
        Box(Modifier.fillMaxSize().background(Color(0xFF0A0908).copy(alpha = dim.coerceIn(0f, 1f))))
        content()
    }
}

private data class PreviewSize(val row: Dp, val icon: Dp, val text: Int, val segment: Int)

private val ListSize.preview
    get() = when (this) {
        ListSize.COMPACT -> PreviewSize(26.dp, 20.dp, 14, 12)
        ListSize.STANDARD -> PreviewSize(32.dp, 24.dp, 16, 15)
        ListSize.BIG -> PreviewSize(40.dp, 28.dp, 19, 18)
    }

/** S5: a live preview over the wallpaper, then text size, font and background. */
@Composable
fun LookAndFeelPage(
    config: Config,
    previewApps: List<AppInfo>,
    icons: IconCache,
    onBack: () -> Unit,
    onUpdate: ((Config) -> Config) -> Unit,
    onBackground: () -> Unit,
) {
    SettingsPage("Look and feel", onBack, gap = 16.dp) {
        val size = config.listSize.preview
        WallpaperPreview(
            config.background, config.background.dim / 100f,
            Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(18.dp)),
            alignment = BiasAlignment(0f, 0.7f),
        ) {
            LauncherTheme(config.font) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                    val title = config.screens.getOrNull(config.homeIndex)?.groups?.minByOrNull { it.slot }
                        ?.takeIf { !it.source.isPanel }?.title ?: "Apps"
                    Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = C.Home, modifier = Modifier.alpha(0.7f).padding(bottom = 4.dp))
                    previewApps.take(if (config.listSize == ListSize.BIG) 3 else 4).forEach { app ->
                        Row(Modifier.height(size.row), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AppIconImage(app, icons, size.icon, 6.dp)
                            Text(app.label, fontSize = size.text.sp, color = C.Home, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Text(
                "Preview", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = C.Home,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel("Text size")
            Segmented(ListSize.entries, config.listSize, { s -> onUpdate { it.copy(listSize = s) } }) { s, color ->
                Text("A", fontSize = s.preview.segment.sp, fontWeight = FontWeight.SemiBold, color = color)
                Text(s.label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = color)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel("Font")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AppFont.entries.forEach { font ->
                    val selected = font == config.font
                    Column(
                        Modifier.weight(1f).height(64.dp).clip(RoundedCornerShape(12.dp)).background(C.Card)
                            .border(1.dp, if (selected) C.Accent else Color.Transparent, RoundedCornerShape(12.dp))
                            .clickable(onClickLabel = font.label, role = Role.RadioButton) { onUpdate { it.copy(font = font) } },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                    ) {
                        Text("Aa", fontFamily = font.family(), fontSize = 20.sp)
                        Text(font.label, fontSize = 10.sp, color = C.Sub, maxLines = 1)
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().card(16.dp).clickable(role = Role.Button, onClick = onBackground).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            WallpaperThumb(config.background, Modifier.size(36.dp, 44.dp), 6.dp)
            TitleAndHint("Background", PredefinedWallpapers.describe(config.background), Modifier.weight(1f), size = 15.sp, gap = 0.dp)
            SymbolIcon(Sym.ChevronRight, 20.dp, C.Muted)
        }
    }
}

/** S8: each permission with what it gives you. Tapping one asks for it, or opens its system page to turn it off. */
@Composable
fun PrivacyPage(permissions: PermissionStatus, onBack: () -> Unit, onAccess: (Access) -> Unit) {
    SettingsPage("What the launcher can see", onBack, gap = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SymbolIcon(Sym.Lock, 18.dp, C.Green)
            Text("Everything stays on this phone. Nothing is uploaded.", fontSize = 14.sp, color = C.Green)
        }
        Column {
            Access.entries.forEach { access ->
                val granted = permissions.granted(access)
                Column(Modifier.clickable(role = Role.Switch, onClickLabel = access.title) { onAccess(access) }) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(C.Hair))
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 62.dp).padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        IconBox(access.icon, C.Soft)
                        TitleAndHint(access.title, access.hint, Modifier.weight(1f), size = 15.sp, gap = 1.dp)
                        Toggle(granted)
                    }
                }
            }
        }
    }
}

@Composable
private fun IconBox(icon: String, color: Color) {
    Box(Modifier.size(36.dp).background(C.Card, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
        SymbolIcon(icon, 20.dp, color)
    }
}

/** S9: a preview of the bar, then each shortcut with the app it opens. */
@Composable
fun ShortcutsPage(
    config: Config,
    appLabel: (String) -> String?,
    onBack: () -> Unit,
    onUpdate: ((Config) -> Config) -> Unit,
    onChangeApp: (CornerShortcut) -> Unit,
) {
    SettingsPage("Bottom shortcuts", onBack, gap = 18.dp) {
        WallpaperPreview(config.background, 0.45f, Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(18.dp))) {
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(60.dp).padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    SymbolIcon(Sym.Apps, 24.dp, C.Home, Modifier.alpha(0.7f))
                    SymbolIcon(Sym.Settings, 22.dp, C.Home, Modifier.alpha(0.7f))
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.End)) {
                    CornerShortcut.entries.filter { it in config.shortcuts }.forEach { SymbolIcon(it.icon, 24.dp, C.Home) }
                }
            }
        }
        Column {
            Row(Modifier.fillMaxWidth().height(60.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                IconBox(Sym.Apps, C.Muted)
                TitleAndHint("All apps and settings", "Always on the left", Modifier.weight(1f), size = 15.sp, gap = 0.dp)
            }
            CornerShortcut.entries.forEach { shortcut ->
                val on = shortcut in config.shortcuts
                Box(Modifier.fillMaxWidth().height(1.dp).background(C.Hair))
                Row(Modifier.fillMaxWidth().height(60.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconBox(shortcut.icon, C.Text)
                    Column(Modifier.weight(1f)) {
                        Text(
                            config.shortcutApps[shortcut]?.let(appLabel) ?: shortcut.label,
                            fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "Change app", fontSize = 13.sp, color = C.Accent,
                            modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable(role = Role.Button, onClickLabel = "Change app for ${shortcut.label}") { onChangeApp(shortcut) },
                        )
                    }
                    Toggle(
                        on,
                        Modifier.clip(RoundedCornerShape(13.dp)).clickable(role = Role.Switch, onClickLabel = shortcut.label) {
                            onUpdate { c -> c.copy(shortcuts = if (on) c.shortcuts - shortcut else c.shortcuts + shortcut) }
                        },
                    )
                }
            }
        }
    }
}