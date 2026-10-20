package com.autominimal.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Process
import android.provider.Settings
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class AppInfo(
    val label: String,
    val packageName: String,
    val component: ComponentName,
)

/** Launchable apps of the current user, plus launching helpers. */
class AppRepository(private val context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)

    /** Every launchable app except this launcher, which would only reopen home. */
    fun loadApps(): List<AppInfo> =
        launcherApps.getActivityList(null, Process.myUserHandle())
            .filter { it.applicationInfo.packageName != context.packageName }
            .map { AppInfo(it.label.toString(), it.applicationInfo.packageName, it.componentName) }
            .distinctBy { it.component }
            .sortedBy { it.label.lowercase() }

    /**
     * Apps left out of the usage rankings. Launchers, because returning home would otherwise count
     * as opening them; Settings' fallback home (negative priority) is not a real launcher. Settings
     * and the default phone app, because they would crowd the lists (the phone app has its own
     * shortcut). Without a default dialer, DIAL resolves to the system chooser ("android").
     */
    fun unranked(): Set<String> {
        val launchers = context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .filter { it.priority >= 0 }
            .map { it.activityInfo.packageName }
        val dialer = context.packageManager.resolveActivity(Intent(Intent.ACTION_DIAL), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName?.takeIf { it != "android" }
        return launchers.toSet() + setOfNotNull("com.android.settings", dialer)
    }

    fun launch(app: AppInfo) {
        runCatching { launcherApps.startMainActivity(app.component, Process.myUserHandle(), null, null) }
            .onFailure { launchPackage(app.packageName) }
    }

    fun launchPackage(packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    fun openAppInfo(packageName: String) {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun uninstall(packageName: String) {
        @Suppress("DEPRECATION")
        context.startActivity(
            Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/**
 * Renders app icons as full-bleed squares so the UI can clip them to the design's rounded tile.
 * Adaptive icons are drawn edge to edge (background + foreground) instead of the system mask.
 */
class IconCache(private val context: Context, private val sizePx: Int) {
    private val cache = object : LruCache<String, ImageBitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    fun peek(component: ComponentName): ImageBitmap? = cache.get(component.flattenToShortString())

    fun peekPackage(packageName: String): ImageBitmap? = cache.get("pkg:$packageName")

    /** Launcher icon for a package, used for people-list rows (phone, WhatsApp, Signal, …). */
    fun loadPackage(packageName: String): ImageBitmap? {
        val key = "pkg:$packageName"
        cache.get(key)?.let { return it }
        val drawable = runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull() ?: return null
        val bitmap = render(drawable).asImageBitmap()
        cache.put(key, bitmap)
        return bitmap
    }

    fun load(component: ComponentName): ImageBitmap? {
        val key = component.flattenToShortString()
        cache.get(key)?.let { return it }
        val drawable = runCatching { context.packageManager.getActivityIcon(component) }.getOrNull() ?: return null
        val bitmap = render(drawable).asImageBitmap()
        cache.put(key, bitmap)
        return bitmap
    }

    private fun render(drawable: Drawable): Bitmap {
        if (drawable !is AdaptiveIconDrawable) return drawable.toBitmap(sizePx, sizePx)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // Layers are 108dp with the 72dp centre visible; draw them so the visible part fills the tile.
        val inset = sizePx / 4
        listOfNotNull(drawable.background, drawable.foreground).forEach {
            it.setBounds(-inset, -inset, sizePx + inset, sizePx + inset)
            it.draw(canvas)
        }
        return bitmap
    }
}
