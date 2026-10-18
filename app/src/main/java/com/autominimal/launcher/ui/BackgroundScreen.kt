package com.autominimal.launcher.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autominimal.launcher.data.BackgroundKind
import com.autominimal.launcher.data.Config
import com.autominimal.launcher.data.ImageProvider
import com.autominimal.launcher.data.PredefinedWallpapers
import com.autominimal.launcher.data.PromptWords
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class BackgroundTab(val label: String, val icon: String) {
    PICK("Pick one", Sym.GridView),
    PHOTOS("My photos", Sym.PhotoLibrary),
    CREATE("Create", Sym.AutoAwesome),
}

/** The three word groups a generated background is built from, as (label, question, options). */
private data class WordGroup(val label: String, val question: String, val options: List<String>, val get: (Config) -> String, val set: (Config, String) -> Config)

private val wordGroups = listOf(
    WordGroup("Mood", "What feeling?", PromptWords.moods, { it.mood }, { c, v -> c.copy(mood = v) }),
    WordGroup("Place", "Where?", PromptWords.places, { it.place }, { c, v -> c.copy(place = v) }),
    WordGroup("Time", "When?", PromptWords.times, { it.timeOfDay }, { c, v -> c.copy(timeOfDay = v) }),
)

/** S6: darken the picture, then pick one, use a photo, or create one from three words. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackgroundScreen(
    config: Config,
    generating: Boolean,
    error: String?,
    onBack: () -> Unit,
    onUpdate: ((Config) -> Config) -> Unit,
    onGenerate: ((String) -> Unit) -> Unit,
    onImport: (Uri, (String) -> Unit) -> Unit,
    onApply: (path: String?, kind: BackgroundKind, dim: Int) -> Unit,
    onDeleteGenerated: (String) -> Unit,
    onOpenCreate: () -> Unit,
) {
    val current = config.background
    var tab by rememberSaveable {
        mutableStateOf(
            when (current.kind) {
                BackgroundKind.GALLERY -> BackgroundTab.PHOTOS
                else -> BackgroundTab.PICK
            },
        )
    }
    var dim by rememberSaveable { mutableFloatStateOf(current.dim.toFloat()) }
    fun pick(path: String, kind: BackgroundKind) = onApply(path, kind, dim.toInt())
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onImport(uri) { path -> pick(path, BackgroundKind.GALLERY) }
    }

    SettingsPage("Background", onBack, gap = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            WallpaperPreview(current, dim / 100f, Modifier.size(96.dp, 124.dp).clip(RoundedCornerShape(14.dp))) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()), fontSize = 18.sp, color = C.Home)
                    listOf(44, 36, 40).forEach { w ->
                        Box(Modifier.size(w.dp, 3.dp).alpha(0.85f).background(C.Home, RoundedCornerShape(2.dp)))
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("Darken", fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text("${dim.toInt()}%", fontSize = 14.sp, color = C.Sub)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SymbolIcon(Sym.LightMode, 18.dp, C.Muted)
                    AccentSlider(dim, { dim = it }, 0f..80f, Modifier.weight(1f)) {
                        val value = dim.toInt()
                        if (value != config.background.dim) onUpdate { it.copy(background = it.background.copy(dim = value)) }
                    }
                    SymbolIcon(Sym.DarkMode, 18.dp, C.Muted)
                }
                Text("Makes text easier to read on busy pictures.", fontSize = 13.sp, color = C.Sub)
            }
        }

        Segmented(BackgroundTab.entries, tab, { tab = it }, height = 40.dp) { t, color ->
            SymbolIcon(t.icon, 18.dp, color)
            Text(t.label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = color)
        }

        when (tab) {
            BackgroundTab.PICK -> {
                val items = PredefinedWallpapers.names.map { Triple(PredefinedWallpapers.path(it), PredefinedWallpapers.label(it), BackgroundKind.PREDEFINED) } +
                    config.generated.mapIndexed { i, path -> Triple(path, "Made by you ${i + 1}", BackgroundKind.GENERATED) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (path, label, kind) ->
                                Wallpaper(
                                    path, label, selected = current.kind == kind && current.path == path,
                                    modifier = Modifier.weight(1f),
                                    onDelete = if (kind == BackgroundKind.GENERATED) ({ onDeleteGenerated(path) }) else null,
                                ) { pick(path, kind) }
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            BackgroundTab.PHOTOS -> Column(
                Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(16.dp)).dashedBorder(C.Border, 16.dp)
                    .clickable(role = Role.Button) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
            ) {
                SymbolIcon(Sym.PhotoLibrary, 32.dp, C.Soft)
                Text(if (current.kind == BackgroundKind.GALLERY) "Choose another photo" else "Choose a photo", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text("From your gallery", fontSize = 13.sp, color = C.Sub)
            }
            BackgroundTab.CREATE -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                wordGroups.forEach { g ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(g.label, fontSize = 13.sp, color = C.Sub, modifier = Modifier.width(44.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            g.options.forEach { word ->
                                Chip(word, g.get(config) == word, { onUpdate { g.set(it, word) } }, height = 30.dp, fontSize = 13.sp, padding = 12.dp)
                            }
                        }
                    }
                }
                ImageProviderPicker(config, onUpdate)
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "\"${PromptWords.short(config.mood, config.place, config.timeOfDay)}\"", fontSize = 15.sp, color = C.Soft,
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).clickable(onClickLabel = "More options", onClick = onOpenCreate),
                    )
                    AccentButton(
                        if (generating) "Creating" else "Create", { onGenerate { path -> pick(path, BackgroundKind.GENERATED) } },
                        icon = Sym.AutoAwesome, height = 44.dp, fontSize = 15.sp, iconSize = 18.dp, loading = generating,
                        enabled = config.canGenerate,
                    )
                }
                if (error != null) Text(error, fontSize = 13.sp, color = C.Danger)
            }
        }
    }
}

@Composable
private fun Wallpaper(path: String, label: String, selected: Boolean, modifier: Modifier, onDelete: (() -> Unit)?, onClick: () -> Unit) {
    val bitmap = rememberImageFile(path, 360)
    Box(
        modifier.aspectRatio(1f)
            .drawBehind {
                if (selected) {
                    val inset = 3.dp.toPx()
                    drawRoundRect(
                        C.Accent, topLeft = Offset(-inset, -inset), size = Size(size.width + inset * 2, size.height + inset * 2),
                        cornerRadius = CornerRadius(15.dp.toPx()), style = Stroke(2.dp.toPx()),
                    )
                }
            }
            .clip(RoundedCornerShape(12.dp)).background(C.Card)
            .clickable(onClickLabel = label, role = Role.RadioButton, onClick = onClick),
    ) {
        bitmap?.let { Image(it, contentDescription = label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        if (onDelete != null) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f))
                    .clickable(onClickLabel = "Delete $label", role = Role.Button, onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) { SymbolIcon(Sym.Close, 14.dp, Color.White) }
        }
    }
}

private val Config.canGenerate get() = imageProvider != ImageProvider.GEMINI || geminiKey.isNotBlank()

/** Pollinations or Gemini; Gemini needs the user's own API key, asked for right below. */
@Composable
private fun ImageProviderPicker(config: Config, onUpdate: ((Config) -> Config) -> Unit, showModel: Boolean = false) {
    val gemini = config.imageProvider == ImageProvider.GEMINI
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Segmented(ImageProvider.entries, config.imageProvider, { p -> onUpdate { it.copy(imageProvider = p) } }, height = 36.dp) { p, color ->
            Text(
                if (p == ImageProvider.POLLINATIONS) "${p.label} (default)" else p.label,
                fontSize = 14.sp, fontWeight = FontWeight.Medium, color = color,
            )
        }
        if (gemini) {
            TextInput(config.geminiKey, { k -> onUpdate { it.copy(geminiKey = k.trim()) } }, Modifier.fillMaxWidth(), placeholder = "Gemini API key", secret = true)
            if (showModel) TextInput(config.geminiModel, { m -> onUpdate { it.copy(geminiModel = m.trim()) } }, Modifier.fillMaxWidth(), placeholder = "Model")
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SymbolIcon(Sym.Info, 16.dp, C.Muted)
            Text(
                if (gemini) "Uses your own key. Get one free at aistudio.google.com." else "Free, no account needed.",
                fontSize = 13.sp, color = C.Muted,
            )
        }
    }
}

/** S7: tap three words instead of writing a prompt. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateBackgroundScreen(
    config: Config,
    generating: Boolean,
    error: String?,
    onBack: () -> Unit,
    onUpdate: ((Config) -> Config) -> Unit,
    onGenerate: ((String) -> Unit) -> Unit,
    onApply: (path: String?, kind: BackgroundKind, dim: Int) -> Unit,
) {
    SettingsPage("Create a background", onBack, gap = 18.dp) {
        wordGroups.forEach { g ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel(g.question)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    g.options.forEach { word -> Chip(word, g.get(config) == word, { onUpdate { g.set(it, word) } }) }
                }
            }
        }
        Column(Modifier.fillMaxWidth().card(16.dp).padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("We'll ask for", fontSize = 12.sp, color = C.Sub)
            Text(config.prompt, fontSize = 17.sp)
        }
        ImageProviderPicker(config, onUpdate, showModel = true)
        AccentButton(
            if (generating) "Creating background" else "Create background",
            { onGenerate { path -> onApply(path, BackgroundKind.GENERATED, config.background.dim) } },
            Modifier.fillMaxWidth(), icon = Sym.AutoAwesome,
            enabled = config.canGenerate, loading = generating,
        )
        if (error != null) Text(error, fontSize = 13.sp, color = C.Danger)
    }
}
