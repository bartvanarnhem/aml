package com.autominimal.launcher.data

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Bundled wallpapers shipped with the app, addressed as asset paths. */
object PredefinedWallpapers {
    const val ASSET_DIR = "wallpapers"
    val names = listOf(
        "cozy_jungle_sunrise.png",
        "vibrant_city_sundown.png",
        "vibrant_mountain_sunrise.png",
        "cyberpunk_mountain_night_moon.png",
        "dreamscape_mountain_sundown.png",
        "dreamscape_city_night_moon.png",
        "minimal_ocean_night_moon.png",
        "minimal_jungle_sundown.png",
    )

    private val labels = mapOf(
        "cozy_jungle_sunrise.png" to "Sunrise jungle",
        "vibrant_city_sundown.png" to "Sunset city",
        "vibrant_mountain_sunrise.png" to "Sunrise mountains",
        "cyberpunk_mountain_night_moon.png" to "Neon mountains",
        "dreamscape_mountain_sundown.png" to "Dreamy mountains",
        "dreamscape_city_night_moon.png" to "Dreamy night town",
        "minimal_ocean_night_moon.png" to "Night ocean",
        "minimal_jungle_sundown.png" to "Sunset jungle",
    )

    fun path(name: String) = "$ASSET_DIR/$name"

    fun label(name: String) = labels[name] ?: name.removeSuffix(".png").replace('_', ' ')

    /** Name shown for the current background, e.g. "Sunset jungle" or "Your photo". */
    fun describe(background: Background): String = when (background.kind) {
        BackgroundKind.NONE -> "Plain"
        BackgroundKind.GALLERY -> "Your photo"
        BackgroundKind.GENERATED -> "Made by you"
        BackgroundKind.PREDEFINED -> background.path?.substringAfterLast('/')?.let(::label) ?: "Plain"
    }
}

/** Background images on disk: gallery imports and Gemini generations. */
class BackgroundRepository(private val context: Context) {
    private val dir = File(context.filesDir, "backgrounds").apply { mkdirs() }

    suspend fun importFromGallery(uri: Uri): File = withContext(Dispatchers.IO) {
        val out = File(dir, "gallery_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)!!.use { input -> out.outputStream().use { input.copyTo(it) } }
        out
    }

    fun delete(path: String) {
        File(path).takeIf { it.parentFile == dir }?.delete()
    }

    /**
     * Makes [path] the phone's home and lock screen wallpaper, undarkened, cropped to the screen
     * from the bottom edge up like the launcher draws it.
     */
    suspend fun setAsSystemWallpaper(path: String) = withContext(Dispatchers.IO) {
        val (width, height) = screenPixels()
        val bitmap = decode(context, path, height) ?: return@withContext
        val scale = maxOf(width / bitmap.width.toFloat(), height / bitmap.height.toFloat())
        val cropWidth = (width / scale).toInt().coerceAtMost(bitmap.width)
        val cropHeight = (height / scale).toInt().coerceAtMost(bitmap.height)
        val left = (bitmap.width - cropWidth) / 2
        val crop = Rect(left, bitmap.height - cropHeight, left + cropWidth, bitmap.height)
        WallpaperManager.getInstance(context)
            .setBitmap(bitmap, crop, true, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
    }

    /** Generates a portrait wallpaper and saves it. Throws with a readable message on failure. */
    suspend fun generate(config: Config): File = withContext(Dispatchers.IO) {
        val prompt = fullPrompt(config.prompt)
        when (config.imageProvider) {
            ImageProvider.POLLINATIONS -> generatePollinations(config.pollinationsKey, prompt)
            ImageProvider.GEMINI -> generateGemini(config.geminiKey, config.geminiModel, prompt)
        }
    }

    /** Portrait pixel size of this phone. Added to the request only, never shown in the prompt field. */
    private fun screenPixels(): Pair<Int, Int> {
        val dm = context.resources.displayMetrics
        return minOf(dm.widthPixels, dm.heightPixels) to maxOf(dm.widthPixels, dm.heightPixels)
    }

    private fun fullPrompt(prompt: String): String {
        val (width, height) = screenPixels()
        return "Phone wallpaper, portrait, ${width}x${height} pixels. ${prompt.trim().trimEnd('.')}. " +
            "No text, no logos, no people. Calm, uncluttered upper third so a large clock stays readable."
    }

    /**
     * Pollinations returns the image itself from a GET. Without a key it allows one image per 45 s
     * per IP; errors come back as JSON with a readable message (e.g. "Free limit reached…").
     */
    private fun generatePollinations(apiKey: String, prompt: String): File {
        val seed = (0..999_999).random()
        val url = URL("https://image.pollinations.ai/prompt/${Uri.encode(prompt)}?width=1080&height=1920&nologo=true&seed=$seed")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 120_000
            if (apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299 || conn.contentType?.startsWith("image/") != true) {
                val text = (conn.errorStream ?: conn.inputStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                val message = runCatching {
                    Json.parseToJsonElement(text).jsonObject["error"]?.let { e ->
                        (e as? JsonObject)?.get("message")?.jsonPrimitive?.content ?: e.jsonPrimitive.content
                    }
                }.getOrNull()
                error(message ?: "Pollinations returned HTTP $code")
            }
            val out = File(dir, "pollinations_${System.currentTimeMillis()}.jpg")
            conn.inputStream.use { input -> out.outputStream().use { input.copyTo(it) } }
            return out
        } finally {
            conn.disconnect()
        }
    }

    private fun generateGemini(apiKey: String, model: String, fullPrompt: String): File {
        require(apiKey.isNotBlank()) { "Add a Gemini API key first." }
        val body = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject { putJsonArray("parts") { addJsonObject { put("text", fullPrompt) } } }
            }
            putJsonObject("generationConfig") {
                putJsonArray("responseModalities") { add("TEXT"); add("IMAGE") }
                putJsonObject("imageConfig") { put("aspectRatio", "9:16") }
            }
        }.toString()

        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/${model.trim()}:generateContent")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 120_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-goog-api-key", apiKey.trim())
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()
            if (code !in 200..299) {
                val message = json?.get("error")?.jsonObject?.get("message")?.jsonPrimitive?.content
                error(message ?: "Gemini returned HTTP $code")
            }
            val parts = json?.get("candidates")?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("content")?.jsonObject?.get("parts") as? JsonArray
            val data = parts?.firstNotNullOfOrNull { part ->
                ((part as? JsonObject)?.get("inlineData") ?: (part as? JsonObject)?.get("inline_data"))
                    ?.jsonObject?.get("data")?.jsonPrimitive?.content
            } ?: error("Gemini returned no image. Try rewording the prompt.")

            val bytes = Base64.decode(data, Base64.DEFAULT)
            val out = File(dir, "gemini_${System.currentTimeMillis()}.png")
            out.writeBytes(bytes)
            return out
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        /** Decodes a file path or bundled asset path, subsampled to roughly [maxPx] on its longer side. */
        fun decode(context: Context, path: String, maxPx: Int): Bitmap? {
            val bytes = if (path.startsWith("/")) null else runCatching { context.assets.open(path).use { it.readBytes() } }.getOrNull()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            if (bytes != null) BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds) else BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0) return null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            return if (bytes != null) BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) else BitmapFactory.decodeFile(path, opts)
        }
    }
}
