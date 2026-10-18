package com.autominimal.launcher

import android.app.Application
import com.autominimal.launcher.data.AppRepository
import com.autominimal.launcher.data.BackgroundRepository
import com.autominimal.launcher.data.ConfigStore
import com.autominimal.launcher.data.FeedRepository
import com.autominimal.launcher.data.IconCache
import com.autominimal.launcher.data.PeopleRepository
import com.autominimal.launcher.data.UsageRepository

class LauncherApp : Application() {
    val config by lazy { ConfigStore(this) }
    val apps by lazy { AppRepository(this) }
    val usage by lazy { UsageRepository(this) }
    val people by lazy { PeopleRepository(this) }
    val feeds by lazy { FeedRepository(this) }
    val backgrounds by lazy { BackgroundRepository(this) }
    val icons by lazy { IconCache(this, (56 * resources.displayMetrics.density).toInt()) }
}
