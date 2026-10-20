package com.autominimal.launcher

import android.Manifest
import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autominimal.launcher.data.AppInfo
import com.autominimal.launcher.data.Config
import com.autominimal.launcher.data.CornerShortcut
import com.autominimal.launcher.data.Source
import com.autominimal.launcher.data.WHATSAPP
import com.autominimal.launcher.data.WhatsAppListener
import com.autominimal.launcher.ui.Access
import com.autominimal.launcher.ui.AllAppsScreen
import com.autominimal.launcher.ui.AppPickerScreen
import com.autominimal.launcher.ui.BackgroundScreen
import com.autominimal.launcher.ui.CreateBackgroundScreen
import com.autominimal.launcher.ui.GroupEditorScreen
import com.autominimal.launcher.ui.HomeScreen
import com.autominimal.launcher.ui.LauncherTheme
import com.autominimal.launcher.ui.LookAndFeelPage
import com.autominimal.launcher.ui.PermissionStatus
import com.autominimal.launcher.ui.PrivacyPage
import com.autominimal.launcher.ui.ScreenEditor
import com.autominimal.launcher.ui.ScreensPage
import com.autominimal.launcher.ui.SettingsHome
import com.autominimal.launcher.ui.SetupScreen
import com.autominimal.launcher.ui.ShortcutAppPicker
import com.autominimal.launcher.ui.ShortcutsPage
import com.autominimal.launcher.ui.SortSheet
import com.autominimal.launcher.ui.TileMenu
import com.autominimal.launcher.ui.icon
import com.autominimal.launcher.ui.regionColor
import com.autominimal.launcher.ui.weatherIcon
import kotlinx.coroutines.flow.MutableSharedFlow

/** Pages pushed over the home screen. Back pops one. */
private enum class Route { SETTINGS, SCREENS, SCREEN, LOOK, BACKGROUND, CREATE, PRIVACY, SHORTCUTS, SHORTCUT_APP, GROUP_EDITOR, APP_PICKER }

/**
 * First-run setup asks for these in order; each is skipped when already granted.
 * System popups come first, then the settings pages the user switches on by hand, then the default launcher.
 */
private enum class SetupStep { RUNTIME, USAGE, NOTIFICATIONS, HOME }

private const val USAGE_REQUEST = 41

class MainActivity : ComponentActivity() {
    private val vm: LauncherViewModel by viewModels()

    private val routes = mutableStateListOf<Route>()
    private var allAppsOpen by mutableStateOf(false)
    private var menuTile by mutableStateOf<Tile?>(null)
    /** Group whose "Sort by" sheet is open. */
    private var sortGroupId by mutableStateOf<String?>(null)
    private var selectedScreenId by mutableStateOf<String?>(null)
    private var shortcutTarget by mutableStateOf<CornerShortcut?>(null)
    private var permissions by mutableStateOf(PermissionStatus())
    private val homePressed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Remaining steps while "Continue" on the setup screen is walking through permissions. */
    private var setupQueue: ArrayDeque<SetupStep>? = null
    private var awaitingSetupStep = false
    private var pausedSinceSetupStep = false

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onSetupResult() }

    private val requestHomeRole =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { onSetupResult() }

    private var watchingUsage = false
    private val usageWatcher = AppOpsManager.OnOpChangedListener { _, _ ->
        runOnUiThread {
            if (watchingUsage && (application as LauncherApp).usage.hasAccess()) {
                stopWatchingUsage()
                @Suppress("DEPRECATION")
                finishActivity(USAGE_REQUEST)
            }
        }
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = vm.reloadApps()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_EXPORTED)

        setContent {
            val config by vm.config.collectAsStateWithLifecycle()
            val contents by vm.contents.collectAsStateWithLifecycle()
            val apps by vm.apps.collectAsStateWithLifecycle()
            val topApps by vm.topApps.collectAsStateWithLifecycle()
            val draft by vm.draft.collectAsStateWithLifecycle()
            val pagerState = rememberPagerState(initialPage = config.homeIndex) { config.screens.size }

            LaunchedEffect(Unit) {
                homePressed.collect {
                    val home = vm.config.value.homeIndex
                    if (pagerState.currentPage != home) pagerState.animateScrollToPage(home)
                }
            }

            // Each page keeps its scroll position while a page above it is showing.
            val pageStates = rememberSaveableStateHolder()
            val routeNames = routes.map { it.name }.toSet()
            LaunchedEffect(routeNames) {
                Route.entries.filter { it.name !in routeNames }.forEach { pageStates.removeState(it.name) }
            }

            LauncherTheme(config.font) {
                Box(Modifier.fillMaxSize()) {
                    HomeScreen(
                        config = config,
                        contents = contents,
                        icons = vm.icons,
                        pagerState = pagerState,
                        onTile = vm::open,
                        onTileLongPress = { menuTile = it },
                        onMissing = ::fix,
                        onAllApps = { allAppsOpen = true },
                        onSettings = { routes += Route.SETTINGS },
                        onSwipeDown = ::expandNotifications,
                        onLaunchPackage = vm::launchPackage,
                        onHeader = { if (it.source == Source.CALENDAR) vm.openCalendar() else sortGroupId = it.id },
                    )

                    routes.lastOrNull()?.let { route ->
                        pageStates.SaveableStateProvider(route.name) {
                            Page(route, config, apps, topApps, draft)
                        }
                    }

                    // Above settings pages too, so All apps opened from settings stays on top.
                    AnimatedVisibility(
                        allAppsOpen,
                        enter = slideInVertically { it / 3 } + fadeIn(),
                        exit = slideOutVertically { it / 3 } + fadeOut(),
                    ) {
                        AllAppsScreen(
                            apps = apps,
                            topApps = topApps,
                            hidden = config.hidden,
                            icons = vm.icons,
                            onLaunch = { vm.launch(it) },
                            onLongPress = { menuTile = Tile.App(it) },
                            onPlayStore = ::searchPlayStore,
                            onWebSearch = ::searchWeb,
                            onClose = { allAppsOpen = false },
                        )
                    }

                    if (!config.onboarded || vm.demoSetup) {
                        SetupScreen(
                            permissions = if (vm.demoSetup) PermissionStatus() else permissions,
                            onContinue = ::startSetup,
                            onAccess = ::access,
                            onDone = { vm.update { it.copy(onboarded = true) } },
                        )
                    }

                    sortGroupId?.let { id ->
                        val group = config.screens.flatMap { it.groups }.find { it.id == id }
                        if (group == null) {
                            LaunchedEffect(Unit) { sortGroupId = null }
                            return@let
                        }
                        SortSheet(
                            group = group,
                            window = config.window,
                            onSelect = { source -> sortBy(group.id, source) },
                            onChangeWindow = {
                                sortGroupId = null
                                routes.clear()
                                routes += listOf(Route.SETTINGS, Route.SCREENS)
                            },
                            onDismiss = { sortGroupId = null },
                        )
                    }

                    menuTile?.let { tile ->
                        TileMenu(
                            tile = tile,
                            hidden = tile.key in config.hidden,
                            icons = vm.icons,
                            window = config.window,
                            whatsAppInstalled = apps.any { it.packageName == WHATSAPP },
                            loadStats = vm::appStats,
                            onToggleHidden = { vm.toggleHidden(tile.key); menuTile = null },
                            onAppInfo = { vm.appInfo(it); menuTile = null },
                            onCall = { vm.call(it); menuTile = null },
                            onChat = { vm.chat(it); menuTile = null },
                            onUninstall = { vm.uninstall(it); menuTile = null },
                            onDismiss = { menuTile = null },
                        )
                    }

                    // Back closes the topmost layer; on the bare home screen it does nothing.
                    BackHandler(enabled = menuTile != null || sortGroupId != null || allAppsOpen || routes.isNotEmpty()) {
                        when {
                            menuTile != null -> menuTile = null
                            sortGroupId != null -> sortGroupId = null
                            allAppsOpen -> allAppsOpen = false
                            else -> pop()
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Page(route: Route, config: Config, apps: List<AppInfo>, topApps: List<AppInfo>, draft: GroupDraft?) {
        val contents by vm.contents.collectAsStateWithLifecycle()
        val generating by vm.generating.collectAsStateWithLifecycle()
        val generateError by vm.generateError.collectAsStateWithLifecycle()
        when (route) {
            Route.SETTINGS -> SettingsHome(
                config, permissions, onBack = ::pop,
                onDefaultHome = ::requestDefaultHome,
                onScreens = { routes += Route.SCREENS },
                onLook = { routes += Route.LOOK },
                onShortcuts = { routes += Route.SHORTCUTS },
                onPrivacy = { routes += Route.PRIVACY },
            )
            Route.SCREENS -> ScreensPage(
                config, onBack = ::pop,
                onOpen = { selectedScreenId = it; routes += Route.SCREEN },
                onAdd = { selectedScreenId = vm.addScreen(config.screens.size); routes += Route.SCREEN },
                onReorder = vm::reorderScreens,
                onUpdate = vm::update,
                panelIcon = { group ->
                    vm.contents.value[group.id]?.weather?.takeIf { group.source == Source.WEATHER }?.let { weatherIcon(it.code, it.isDay) }
                        ?: group.source.icon
                },
            )
            Route.SCREEN -> {
                val screen = config.screens.find { it.id == selectedScreenId }
                if (screen == null) LaunchedEffect(Unit) { pop() }
                else ScreenEditor(
                    screen, canRemove = config.screens.size > 1 && !screen.home, onBack = ::pop,
                    onRename = { vm.renameScreen(screen.id, it) },
                    onRemove = { vm.removeScreen(screen.id); pop() },
                    onLayout = { vm.setScreenLayout(screen.id, it) },
                    onReorderSlots = { from, to -> vm.reorderSlots(screen.id, from, to) },
                    onOpenSlot = { slot -> vm.openSlot(screen.id, slot); routes += Route.GROUP_EDITOR },
                )
            }
            Route.GROUP_EDITOR -> draft?.let { d ->
                GroupEditorScreen(
                    group = d.group,
                    isNew = d.isNew,
                    color = regionColor(d.group.slot, d.group),
                    apps = apps,
                    icons = vm.icons,
                    calendarOptions = config.calendar,
                    calendars = contents[d.group.id]?.agenda?.calendars,
                    onCalendarOptions = { change -> vm.update { it.copy(calendar = change(it.calendar)) } },
                    onAllowCalendar = { fix(Missing.CALENDAR) },
                    onChange = vm::updateDraft,
                    onPickApps = { routes += Route.APP_PICKER },
                    onDelete = { vm.deleteDraftGroup(); pop() },
                    onBack = { vm.discardDraft(); pop() },
                )
            } ?: LaunchedEffect(Unit) { pop() }
            Route.APP_PICKER -> draft?.let { d ->
                AppPickerScreen(
                    apps = apps,
                    picked = d.group.custom,
                    icons = vm.icons,
                    onChange = { list -> vm.updateDraft { it.copy(custom = list) } },
                    onDone = ::pop,
                )
            } ?: LaunchedEffect(Unit) { pop() }
            Route.LOOK -> LookAndFeelPage(
                config, previewApps = topApps.ifEmpty { apps }, icons = vm.icons, onBack = ::pop,
                onUpdate = vm::update, onBackground = { routes += Route.BACKGROUND },
            )
            Route.BACKGROUND -> BackgroundScreen(
                config = config,
                generating = generating,
                error = generateError,
                onBack = ::pop,
                onUpdate = vm::update,
                onGenerate = vm::generate,
                onImport = vm::importFromGallery,
                onApply = vm::applyBackground,
                onDeleteGenerated = vm::deleteGenerated,
                onOpenCreate = { routes += Route.CREATE },
            )
            Route.CREATE -> CreateBackgroundScreen(
                config = config,
                generating = generating,
                error = generateError,
                onBack = ::pop,
                onUpdate = vm::update,
                onGenerate = vm::generate,
                onApply = vm::applyBackground,
            )
            Route.PRIVACY -> PrivacyPage(permissions, onBack = ::pop, onAccess = ::access)
            Route.SHORTCUTS -> ShortcutsPage(
                config,
                appLabel = { pkg -> apps.find { it.packageName == pkg }?.label },
                onBack = ::pop,
                onUpdate = vm::update,
                onChangeApp = { shortcutTarget = it; routes += Route.SHORTCUT_APP },
            )
            Route.SHORTCUT_APP -> shortcutTarget?.let { target ->
                ShortcutAppPicker(
                    target, apps, config.shortcutApps[target], vm.icons,
                    onPick = { pkg ->
                        vm.update { c -> c.copy(shortcutApps = if (pkg == null) c.shortcutApps - target else c.shortcutApps + (target to pkg)) }
                        pop()
                    },
                    onBack = ::pop,
                )
            } ?: LaunchedEffect(Unit) { pop() }
        }
    }

    /** From the sort sheet. Picking "My own pick" goes straight to choosing apps. */
    private fun sortBy(groupId: String, source: Source) {
        vm.setGroupSource(groupId, source)
        sortGroupId = null
        if (source != Source.CUSTOM) return
        val screen = vm.config.value.screens.find { s -> s.groups.any { it.id == groupId } } ?: return
        val group = screen.groups.first { it.id == groupId }
        vm.editGroup(screen.id, group)
        selectedScreenId = screen.id
        routes.clear()
        routes += listOf(Route.GROUP_EDITOR, Route.APP_PICKER)
    }

    private fun pop() {
        if (routes.isEmpty()) return
        val popped = routes.removeAt(routes.lastIndex)
        if (popped == Route.GROUP_EDITOR) vm.discardDraft()
    }

    private fun searchPlayStore(query: String) {
        val q = Uri.encode(query)
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=$q&c=apps"))
        if (runCatching { startActivity(market) }.isFailure) {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=$q&c=apps"))) }
        }
    }

    private fun searchWeb(query: String) {
        val search = Intent(Intent.ACTION_WEB_SEARCH).putExtra(android.app.SearchManager.QUERY, query)
        if (runCatching { startActivity(search) }.isFailure) {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}"))) }
        }
    }

    override fun onResume() {
        super.onResume()
        stopWatchingUsage()
        onPermissionsChanged()
        // Back from a settings page opened by setup: move on to the next step.
        if (awaitingSetupStep && pausedSinceSetupStep) {
            awaitingSetupStep = false
            advanceSetup()
        }
    }

    override fun onPause() {
        super.onPause()
        pausedSinceSetupStep = true
    }

    private fun startSetup() {
        setupQueue = ArrayDeque(SetupStep.entries)
        advanceSetup()
    }

    /** Opens the next step that isn't granted yet; a step the user declines is not asked again. */
    private fun advanceSetup() {
        val queue = setupQueue ?: return
        onPermissionsChanged()
        while (queue.isNotEmpty()) {
            val step = queue.removeFirst()
            val granted = when (step) {
                SetupStep.RUNTIME -> permissions.callLog && permissions.contacts && permissions.calendar && permissions.location
                SetupStep.USAGE -> permissions.usage
                SetupStep.NOTIFICATIONS -> permissions.notifications
                SetupStep.HOME -> permissions.isDefaultHome
            }
            if (granted) continue
            awaitingSetupStep = true
            pausedSinceSetupStep = false
            when (step) {
                SetupStep.RUNTIME -> requestPermissions.launch(
                    arrayOf(
                        Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE,
                        Manifest.permission.READ_CALENDAR, Manifest.permission.ACCESS_COARSE_LOCATION,
                    )
                )
                SetupStep.USAGE -> fix(Missing.USAGE)
                SetupStep.NOTIFICATIONS -> fix(Missing.NOTIFICATIONS)
                SetupStep.HOME -> requestDefaultHome()
            }
            return
        }
        setupQueue = null
    }

    /** Result of a permission dialog or the home-role request; arrives even when no dialog was shown. */
    private fun onSetupResult() {
        onPermissionsChanged()
        if (awaitingSetupStep) {
            awaitingSetupStep = false
            advanceSetup()
        }
    }

    override fun onStop() {
        super.onStop()
        // Leaving for an app: come back to a clean home screen.
        allAppsOpen = false
        menuTile = null
        sortGroupId = null
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home pressed while already in the launcher.
        if (intent.hasCategory(Intent.CATEGORY_HOME) || intent.action == Intent.ACTION_MAIN) {
            val wasClean = routes.isEmpty() && !allAppsOpen && menuTile == null && sortGroupId == null
            if (Route.GROUP_EDITOR in routes) vm.discardDraft()
            routes.clear()
            allAppsOpen = false
            menuTile = null
            sortGroupId = null
            if (wasClean) homePressed.tryEmit(Unit)
        }
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        stopWatchingUsage()
        super.onDestroy()
    }

    private fun onPermissionsChanged() {
        val app = application as LauncherApp
        permissions = PermissionStatus(
            isDefaultHome = isDefaultHome(),
            usage = app.usage.hasAccess(),
            callLog = app.people.has(Manifest.permission.READ_CALL_LOG),
            contacts = app.people.hasContacts(),
            notifications = app.people.hasNotificationAccess(),
            calendar = app.feeds.has(Manifest.permission.READ_CALENDAR),
            location = app.feeds.has(Manifest.permission.ACCESS_COARSE_LOCATION),
        )
        vm.refresh()
    }

    /** A Privacy or setup row: asks for it, or when it's already allowed, opens the system page to turn it off. */
    private fun access(access: Access) {
        val granted = permissions.granted(access)
        when (access) {
            Access.USAGE -> fix(Missing.USAGE)
            Access.MESSAGES -> fix(Missing.NOTIFICATIONS)
            Access.HOME -> if (granted) openSettings(Settings.ACTION_HOME_SETTINGS) else requestDefaultHome()
            else -> if (granted) openAppDetails() else when (access) {
                Access.CALLS -> requestPermissions.launch(arrayOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.CALL_PHONE))
                Access.CONTACTS -> fix(Missing.CONTACTS)
                Access.CALENDAR -> fix(Missing.CALENDAR)
                else -> fix(Missing.LOCATION)
            }
        }
    }

    private fun openAppDetails() {
        runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))) }
    }

    /**
     * Opens the launcher's own usage access toggle, or the full list where that page doesn't exist.
     * Turning access on closes the page again so the user lands back in the launcher.
     */
    @Suppress("DEPRECATION")
    private fun openUsageAccess() {
        val direct = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.fromParts("package", packageName, null))
        val opened = runCatching { startActivityForResult(direct, USAGE_REQUEST) }.isSuccess ||
            runCatching { startActivityForResult(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), USAGE_REQUEST) }.isSuccess
        if (!opened) {
            openSettings(Settings.ACTION_SETTINGS)
            return
        }
        if (!watchingUsage && !(application as LauncherApp).usage.hasAccess()) {
            getSystemService(AppOpsManager::class.java)
                .startWatchingMode(AppOpsManager.OPSTR_GET_USAGE_STATS, packageName, usageWatcher)
            watchingUsage = true
        }
    }

    private fun stopWatchingUsage() {
        if (!watchingUsage) return
        getSystemService(AppOpsManager::class.java).stopWatchingMode(usageWatcher)
        watchingUsage = false
    }

    private fun fix(missing: Missing) {
        when (missing) {
            Missing.USAGE -> openUsageAccess()
            Missing.CALL_LOG -> requestPermissions.launch(
                arrayOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE)
            )
            Missing.CONTACTS -> requestPermissions.launch(arrayOf(Manifest.permission.READ_CONTACTS))
            Missing.CALENDAR -> requestPermissions.launch(arrayOf(Manifest.permission.READ_CALENDAR))
            Missing.LOCATION -> requestPermissions.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION))
            Missing.NOTIFICATIONS -> {
                val detail = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                if (detail) {
                    val component = android.content.ComponentName(this, WhatsAppListener::class.java).flattenToString()
                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                        .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component)
                    if (runCatching { startActivity(intent) }.isSuccess) return
                }
                openSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            }
        }
    }

    private fun openSettings(action: String) {
        runCatching { startActivity(Intent(action)) }.onFailure { runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) } }
    }

    private fun isDefaultHome(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.resolveActivity(intent, 0)?.activityInfo?.packageName == packageName
    }

    private fun requestDefaultHome() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roles = getSystemService(RoleManager::class.java)
            if (roles.isRoleAvailable(RoleManager.ROLE_HOME)) {
                requestHomeRole.launch(roles.createRequestRoleIntent(RoleManager.ROLE_HOME))
                return
            }
        }
        openSettings(Settings.ACTION_HOME_SETTINGS)
    }

    /** Swipe down on home pulls down the notification shade, like most launchers. */
    @SuppressLint("WrongConstant")
    private fun expandNotifications() {
        runCatching {
            val service = getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(service)
        }
    }
}
