package com.autominimal.launcher.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process

/** Start of a ranking window, in epoch millis. */
fun RankWindow.start(now: Long = System.currentTimeMillis()): Long = when (this) {
    RankWindow.WEEK -> now - 7 * DAY
    RankWindow.MONTH -> now - 30 * DAY
    RankWindow.ALL -> 0L
}

private const val DAY = 24 * 60 * 60 * 1000L

/** Launch counts and foreground time per package, read from [UsageStatsManager]. */
class UsageRepository(private val context: Context) {
    private val usm = context.getSystemService(UsageStatsManager::class.java)

    fun hasAccess(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        @Suppress("DEPRECATION")
        val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Packages ranked by how often they were brought to the foreground. */
    fun launchCounts(window: RankWindow): Map<String, Long> = replay(window).launches

    /** Packages ranked by foreground milliseconds. */
    fun screenTime(window: RankWindow): Map<String, Long> {
        // Aggregated stats outlive the raw event log, so prefer them for long windows.
        val now = System.currentTimeMillis()
        val stats = runCatching { usm.queryAndAggregateUsageStats(window.start(now), now) }.getOrNull().orEmpty()
        val aggregated = stats.mapValues { it.value.totalTimeInForeground }.filterValues { it > 0 }
        return aggregated.ifEmpty { replay(window).time }
    }

    private class Replay(val launches: Map<String, Long>, val time: Map<String, Long>)

    /**
     * Walks foreground/background events. A resume of the same package shortly after it paused is an
     * activity switch inside one app, not a new launch.
     */
    private fun replay(window: RankWindow): Replay {
        val now = System.currentTimeMillis()
        val launches = HashMap<String, Long>()
        val time = HashMap<String, Long>()
        val events = runCatching { usm.queryEvents(window.start(now), now) }.getOrNull()
            ?: return Replay(launches, time)

        var current: String? = null
        var resumedAt = 0L
        var lastPaused: String? = null
        var pausedAt = 0L
        val event = UsageEvents.Event()

        fun addTime(pkg: String, from: Long, to: Long) {
            val ms = to - from
            if (ms > 0) time[pkg] = (time[pkg] ?: 0) + ms
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            @Suppress("DEPRECATION")
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    val continuation = pkg == lastPaused && event.timeStamp - pausedAt < 2_000
                    if (!continuation && pkg != current) {
                        launches[pkg] = (launches[pkg] ?: 0) + 1
                    }
                    current?.let { if (it != pkg) addTime(it, resumedAt, event.timeStamp) }
                    if (current != pkg) resumedAt = event.timeStamp
                    current = pkg
                }
                UsageEvents.Event.MOVE_TO_BACKGROUND -> if (pkg == current) {
                    addTime(pkg, resumedAt, event.timeStamp)
                    lastPaused = pkg
                    pausedAt = event.timeStamp
                    current = null
                }
            }
        }
        current?.let { addTime(it, resumedAt, now) }
        return Replay(launches, time)
    }
}
