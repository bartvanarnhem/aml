package com.autominimal.launcher.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import android.util.Xml
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.roundToInt

/** One news headline. */
data class FeedItem(val title: String, val detail: String?, val url: String? = null)

/** One column of the hourly forecast: "Now" or "03", the WMO weather code, the temperature and the chance of rain. */
data class WeatherHour(val label: String, val code: Int, val isDay: Boolean, val temp: Int, val rainChance: Int?)

/** One row of the daily forecast: "Today" or "Mon". */
data class WeatherDay(val label: String, val code: Int, val low: Int, val high: Int)

/**
 * Rain over the next three hours in twelve 15-minute [slots] of mm/h, with time [labels] under them
 * ("Now" and every half hour), a [headline] like "Drizzle stops around 23:40" and a [short] "Dry from 23:40".
 */
data class RainOutlook(val slots: List<Float>, val labels: List<String>, val headline: String, val short: String)

/** Current conditions, the next hours and days, and rain in the next three hours. */
data class WeatherNow(
    val temp: Int,
    val feelsLike: Int,
    val summary: String,
    val place: String,
    val code: Int,
    val isDay: Boolean,
    val hours: List<WeatherHour>,
    val days: List<WeatherDay>,
    val rain: RainOutlook?,
    val windKmh: Int,
    /** Compass point the wind blows from, e.g. "SW". */
    val windFrom: String,
    val humidity: Int,
    /** The next of sunrise or sunset, e.g. "Sunrise 08:14". */
    val sun: String?,
)

/** Why a calendar, weather, or news column cannot be filled. */
enum class FeedNeed { CALENDAR, LOCATION }

sealed class Feed {
    data class Items(val items: List<FeedItem>) : Feed()
    data class Forecast(val weather: WeatherNow) : Feed()
    data class Events(val agenda: Agenda) : Feed()
    data class Needs(val need: FeedNeed) : Feed()
    data class Failed(val message: String) : Feed()
}

/** Calendar on the device, plus weather and headlines from the network. */
class FeedRepository(private val context: Context) {
    private var weatherCache: Triple<Long, Pair<Double, Double>, WeatherNow>? = null
    private var newsCache: Pair<Long, List<FeedItem>>? = null

    fun has(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** Visible calendars, the user's own first. */
    private fun calendars(): List<CalendarSource> =
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.OWNER_ACCOUNT,
                CalendarContract.Calendars.IS_PRIMARY,
            ),
            "${CalendarContract.Calendars.VISIBLE} = 1",
            null,
            null,
        )?.use { cursor ->
            val out = ArrayList<CalendarSource>()
            while (cursor.moveToNext()) {
                val account = cursor.getString(2).orEmpty()
                // Workspace accounts grant owner access to colleagues' calendars, so access level can't tell them apart;
                // each account's primary calendar is the user's own.
                val own = cursor.getInt(4) == 1
                out += CalendarSource(
                    id = cursor.getLong(0),
                    name = cursor.getString(1)?.takeIf { it.isNotBlank() } ?: account,
                    account = if (own || cursor.getString(3) == account) account else "Shared with you",
                    own = own,
                )
            }
            out.sortedWith(compareByDescending<CalendarSource> { it.own }.thenBy { it.name.lowercase() })
        }.orEmpty()

    fun agenda(options: CalendarOptions): Feed {
        if (!has(Manifest.permission.READ_CALENDAR)) return Feed.Needs(FeedNeed.CALENDAR)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(options.lookAhead.days.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
        val day = 24L * 60 * 60 * 1000
        return runCatching {
            val calendars = calendars()
            val on = calendars.filter(options::isOn).map { it.id }
            val events = if (on.isEmpty()) emptyList() else readEvents(on, start - day, end + day, options)
                .filter { it.start < end && it.until > start }
            Feed.Events(Agenda(calendars, if (options.mergeDuplicates) mergeDuplicates(events) else events, start, options.lookAhead.days, zone))
        }.getOrElse { Feed.Failed("Couldn't read the calendar") }
    }

    private fun readEvents(calendars: List<Long>, from: Long, to: Long, options: CalendarOptions): List<CalendarEvent> {
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            android.content.ContentUris.appendId(it, from)
            android.content.ContentUris.appendId(it, to)
        }.build()
        val where = buildString {
            append("${CalendarContract.Instances.CALENDAR_ID} IN (${calendars.joinToString(",")})")
            if (!options.allDay) append(" AND ${CalendarContract.Instances.ALL_DAY} = 0")
            if (options.hideDeclined) {
                val status = CalendarContract.Instances.SELF_ATTENDEE_STATUS
                append(" AND ($status IS NULL OR $status != ${CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED})")
            }
        }
        val zone = ZoneId.systemDefault()
        // All-day events are stored from UTC midnight to UTC midnight; move those dates to local midnight.
        fun localMidnight(utc: Long) = Instant.ofEpochMilli(utc).atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        return context.contentResolver.query(
            uri,
            arrayOf(
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.CALENDAR_ID,
                CalendarContract.Instances.EVENT_LOCATION,
                CalendarContract.Instances.DESCRIPTION,
            ),
            where,
            null,
            "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { cursor ->
            val out = ArrayList<CalendarEvent>()
            while (cursor.moveToNext()) {
                val title = cursor.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                val allDay = cursor.getInt(4) == 1
                val begin = cursor.getLong(2)
                val finish = cursor.getLong(3)
                val location = cursor.getString(6)?.trim()?.takeIf { it.isNotEmpty() }
                val call = callService(location, cursor.getString(7))
                out += CalendarEvent(
                    id = cursor.getLong(0),
                    title = title,
                    start = if (allDay) localMidnight(begin) else begin,
                    end = if (allDay) localMidnight(finish) else finish,
                    allDay = allDay,
                    calendars = listOf(cursor.getLong(5)),
                    location = location?.takeUnless { it.startsWith("http") },
                    call = call,
                )
            }
            out
        }.orEmpty()
    }

    fun weather(): Feed {
        if (!has(Manifest.permission.ACCESS_COARSE_LOCATION)) return Feed.Needs(FeedNeed.LOCATION)
        val loc = lastLocation() ?: return Feed.Failed("No location yet")
        val cached = weatherCache
        val now = System.currentTimeMillis()
        if (cached != null && now - cached.first < CACHE_MS && near(cached.second, loc)) return Feed.Forecast(cached.third)
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${loc.first}&longitude=${loc.second}" +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,is_day,wind_speed_10m,wind_direction_10m" +
            "&minutely_15=precipitation&past_minutely_15=1&forecast_minutely_15=14" +
            "&hourly=temperature_2m,weather_code,is_day,precipitation_probability&past_hours=1&forecast_hours=10" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset&forecast_days=5&timezone=auto"
        val body = get(url) ?: return Feed.Failed("Couldn't load weather")
        val is24 = android.text.format.DateFormat.is24HourFormat(context)
        val forecast = runCatching { parseWeather(body, place(loc.first, loc.second), is24) }.getOrNull()
            ?: return Feed.Failed("Couldn't load weather")
        weatherCache = Triple(now, loc, forecast)
        return Feed.Forecast(forecast)
    }

    fun news(limit: Int): Feed {
        val cached = newsCache
        val now = System.currentTimeMillis()
        if (cached != null && now - cached.first < CACHE_MS) return Feed.Items(cached.second.take(limit))
        val locale = Locale.getDefault()
        val lang = locale.language.ifBlank { "en" }
        val country = locale.country.ifBlank { lang.uppercase() }
        val url = "https://news.google.com/rss?hl=$lang&gl=$country&ceid=$country:$lang"
        val items = runCatching { parseRss(url).take(limit) }.getOrElse { return Feed.Failed("Couldn't load news") }
        if (items.isEmpty()) return Feed.Failed("Couldn't load news")
        newsCache = now to items
        return Feed.Items(items)
    }

    @SuppressLint("MissingPermission")
    private fun lastLocation(): Pair<Double, Double>? {
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val known = manager.getProviders(true)
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        val fresh = known != null && System.currentTimeMillis() - known.time < 60 * 60 * 1000
        val fix = if (fresh) known else requestFix(manager) ?: known
        return fix?.let { it.latitude to it.longitude }
    }

    @SuppressLint("MissingPermission")
    private fun requestFix(manager: LocationManager): Location? {
        val provider = when {
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> return null
        }
        val latch = CountDownLatch(1)
        var got: Location? = null
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                got = location
                latch.countDown()
            }
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        runCatching { manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper()) }
        latch.await(8, TimeUnit.SECONDS)
        runCatching { manager.removeUpdates(listener) }
        return got
    }

    @Suppress("DEPRECATION")
    private fun place(lat: Double, lon: Double): String = runCatching {
        Geocoder(context, Locale.getDefault()).getFromLocation(lat, lon, 1)?.firstOrNull()?.locality.orEmpty()
    }.getOrDefault("")

    private fun near(a: Pair<Double, Double>, b: Pair<Double, Double>) =
        abs(a.first - b.first) < 0.05 && abs(a.second - b.second) < 0.05

    private companion object {
        const val CACHE_MS = 20 * 60 * 1000L

        fun get(url: String): String? = runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "AutoMinimalLauncher")
            conn.inputStream.bufferedReader().use { it.readText() }
        }.getOrNull()

        /** Open-Meteo's local times, e.g. "2026-10-18T23:15", with `timezone=auto`. */
        fun parseWeather(body: String, place: String, is24: Boolean): WeatherNow {
            val json = JSONObject(body)
            val current = json.getJSONObject("current")
            val now = LocalDateTime.parse(current.getString("time"))
            val nowCode = current.getInt("weather_code")
            val nowDay = current.optInt("is_day", 1) == 1
            val clock = DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm", Locale.getDefault())
            fun JSONArray.times() = List(length()) { LocalDateTime.parse(getString(it)) }

            val hourly = json.getJSONObject("hourly")
            val hourTimes = hourly.getJSONArray("time").times()
            val temps = hourly.getJSONArray("temperature_2m")
            val codes = hourly.optJSONArray("weather_code")
            val days = hourly.optJSONArray("is_day")
            val chances = hourly.optJSONArray("precipitation_probability")
            // Below 10 % the chance is noise and stays blank.
            fun chance(i: Int) = chances?.takeIf { i >= 0 && !it.isNull(i) }?.getInt(i)?.takeIf { it >= 10 }
            val thisHour = hourTimes.indexOfLast { !it.isAfter(now) }
            val hourLabel = DateTimeFormatter.ofPattern(if (is24) "HH" else "ha", Locale.getDefault())
            val hours = listOf(WeatherHour("Now", nowCode, nowDay, current.getDouble("temperature_2m").roundToInt(), chance(thisHour))) +
                hourTimes.indices.filter { hourTimes[it].isAfter(now) }.take(7).map { i ->
                    WeatherHour(
                        hourTimes[i].format(hourLabel).lowercase(),
                        codes?.optInt(i, nowCode) ?: nowCode,
                        (days?.optInt(i, 1) ?: 1) == 1,
                        temps.getDouble(i).roundToInt(),
                        chance(i),
                    )
                }

            val daily = json.getJSONObject("daily")
            val dates = daily.getJSONArray("time")
            val highs = daily.getJSONArray("temperature_2m_max")
            val lows = daily.getJSONArray("temperature_2m_min")
            val dayCodes = daily.getJSONArray("weather_code")
            val forecastDays = List(dates.length()) { i ->
                val date = LocalDate.parse(dates.getString(i))
                WeatherDay(
                    if (i == 0) "Today" else date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    dayCodes.optInt(i, nowCode),
                    lows.getDouble(i).roundToInt(),
                    highs.getDouble(i).roundToInt(),
                )
            }
            val rises = daily.optJSONArray("sunrise")?.times().orEmpty()
            val sets = daily.optJSONArray("sunset")?.times().orEmpty()
            val sun = (rises.map { "Sunrise" to it } + sets.map { "Sunset" to it })
                .filter { it.second.isAfter(now) }.minByOrNull { it.second }
                ?.let { (what, time) -> "$what ${time.format(clock)}" }

            val rain = json.optJSONObject("minutely_15")?.let { minutely ->
                val times = minutely.getJSONArray("time").times()
                val amounts = minutely.getJSONArray("precipitation")
                val first = times.indexOfLast { !it.isAfter(now) }.coerceAtLeast(0)
                // Each value is the rain in the preceding 15 minutes; four of them make mm/h.
                val points = (first until times.size).take(13).map { (amounts.optDouble(it, 0.0) * 4).toFloat() }
                if (points.size < 2) null else rainOutlook(points, times[first], rainWord(nowCode)) { it.format(clock) }
            }

            val windDeg = current.optDouble("wind_direction_10m", 0.0)
            return WeatherNow(
                temp = current.getDouble("temperature_2m").roundToInt(),
                feelsLike = current.optDouble("apparent_temperature", current.getDouble("temperature_2m")).roundToInt(),
                summary = weatherSummary(nowCode),
                place = place,
                code = nowCode,
                isDay = nowDay,
                hours = hours,
                days = forecastDays,
                rain = rain,
                windKmh = current.optDouble("wind_speed_10m", 0.0).roundToInt(),
                windFrom = COMPASS[(((windDeg % 360) + 360 + 22.5) / 45).toInt() % 8],
                humidity = current.optDouble("relative_humidity_2m", 0.0).roundToInt(),
                sun = sun,
            )
        }

        val COMPASS = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

        fun parseRss(url: String): List<FeedItem> {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "AutoMinimalLauncher")
            conn.inputStream.use { stream ->
                val parser = Xml.newPullParser()
                parser.setInput(stream, null)
                val items = ArrayList<FeedItem>()
                var inItem = false
                var title: String? = null
                var link: String? = null
                var tag = ""
                while (parser.eventType != XmlPullParser.END_DOCUMENT && items.size < 12) {
                    when (parser.eventType) {
                        XmlPullParser.START_TAG -> {
                            tag = parser.name
                            if (tag == "item") {
                                inItem = true
                                title = null
                                link = parser.getAttributeValue(null, "href")
                            } else if (inItem && tag == "link") {
                                parser.getAttributeValue(null, "href")?.let { link = it }
                            }
                        }
                        XmlPullParser.TEXT -> if (inItem && !parser.isWhitespace) {
                            val text = parser.text.trim()
                            if (tag == "title" && text.isNotEmpty()) title = text
                            if (tag == "link" && text.startsWith("http")) link = text
                        }
                        XmlPullParser.END_TAG -> if (parser.name == "item") {
                            val headline = title?.substringBeforeLast(" - ")?.trim().orEmpty()
                            if (headline.isNotEmpty()) items += FeedItem(headline, null, link)
                            inItem = false
                        }
                    }
                    parser.next()
                }
                return items
            }
        }
    }
}

/** WMO weather code as a short condition. */
fun weatherSummary(code: Int): String = when (code) {
    0 -> "Clear"
    1, 2 -> "Partly cloudy"
    3 -> "Overcast"
    45, 48 -> "Fog"
    51, 53, 55, 56, 57 -> "Drizzle"
    61, 63, 65, 66, 67, 80, 81, 82 -> "Rain"
    71, 73, 75, 77, 85, 86 -> "Snow"
    95, 96, 99 -> "Thunderstorm"
    else -> "Cloudy"
}

/** What to call rain that is falling now, from its WMO code; null leaves it to the intensity. */
fun rainWord(code: Int): String? = when (code) {
    51, 53, 55, 56, 57 -> "Drizzle"
    71, 73, 75, 77, 85, 86 -> "Snow"
    else -> null
}

private const val WET_MM_H = 0.1f

/** Three hours of 15-minute steps. */
private const val RAIN_SLOTS = 12

/** 0 when dry, otherwise 0.1–1 on a log scale: 0.1 mm/h is a trace, 10 mm/h fills the bar. */
fun rainHeight(mmPerHour: Float): Float =
    if (mmPerHour < WET_MM_H) 0f else ((log10(mmPerHour) + 1f) / 2f).coerceIn(0.1f, 1f)

/**
 * Rain in the next three hours from Open-Meteo's 15-minute [points] (mm/h, starting at [start]), one slot each,
 * with the sentence above them. [nowWord] names rain that is already falling, e.g. "Drizzle".
 */
fun rainOutlook(points: List<Float>, start: LocalDateTime, nowWord: String?, format: (LocalDateTime) -> String): RainOutlook {
    val slots = points.take(RAIN_SLOTS)
    fun at(k: Int) = format(start.plusMinutes(15L * k))
    fun wet(v: Float) = v >= WET_MM_H
    fun word(stretch: List<Float>): String {
        val peak = stretch.maxOrNull() ?: 0f
        return when {
            peak < 1f -> "Light rain"
            peak < 4f -> "Rain"
            else -> "Heavy rain"
        }
    }
    val labels = listOf("Now") + (1..RAIN_SLOTS / 2).map { at(it * 2) }
    return if (wet(slots[0])) {
        val stop = slots.indexOfFirst { !wet(it) }
        val kind = nowWord ?: word(if (stop < 0) slots else slots.take(stop))
        if (stop < 0) RainOutlook(slots, labels, "$kind for the next 3 hours", "$kind for 3 hours")
        else RainOutlook(slots, labels, "$kind stops around ${at(stop)}", "Dry from ${at(stop)}")
    } else {
        val begin = slots.indexOfFirst(::wet)
        if (begin < 0) RainOutlook(slots, labels, "No rain for the next 3 hours", "Dry for 3 hours")
        else {
            val kind = word(slots.drop(begin).takeWhile(::wet))
            RainOutlook(slots, labels, "$kind from around ${at(begin)}", "$kind from ${at(begin)}")
        }
    }
}
