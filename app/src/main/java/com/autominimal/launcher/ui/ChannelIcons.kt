package com.autominimal.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.autominimal.launcher.data.Channel
import com.autominimal.launcher.data.Person

/** Stroke marks for how a person is reached, drawn on a 24 × 24 grid. */
private object ChannelGlyph {
    private fun circle(cx: Float, cy: Float, r: Float) =
        "M${cx - r},${cy}a$r,$r 0 1,0 ${2 * r},0a$r,$r 0 1,0 ${-2 * r},0"

    private fun icon(name: String, vararg paths: String) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach {
                addPath(
                    PathParser().parsePathString(it).toNodes(),
                    fill = null, stroke = SolidColor(Color.White), strokeLineWidth = 1.5f,
                    strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val Phone = icon("phone", "M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2")
    val WhatsApp = icon(
        "whatsapp",
        "M3 21l1.7-4a9 9 0 1 1 3.3 2.9L3 21z",
        "M9 8h1.7l.8 2.1-.9.6a4.6 4.6 0 0 0 2.1 2.1l.7-.9 2.1.8v1.7a.8.8 0 0 1-.8.8A6.7 6.7 0 0 1 8.2 8.8a.8.8 0 0 1 .8-.8",
    )
    val Message = icon("message", "M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z")
    val Signal = icon(
        "signal",
        "M6 5h10a4 4 0 0 1 4 4v5a4 4 0 0 1-4 4H10l-4 4v-4H6a4 4 0 0 1-4-4V9a4 4 0 0 1 4-4z",
        circle(12f, 11.5f, 1.3f),
    )
    val Plane = icon("plane", "M3.5 11.2L20.5 4.2 12.8 20.2 10.6 13.2z", "M10.6 13.2L20.5 4.2")
}

/** A round mark with a monochrome channel glyph: phone, WhatsApp, messages, Signal, or Telegram. */
@Composable
fun PersonIcon(person: Person, size: Dp) {
    val glyph = when (person.channel) {
        Channel.CALL -> ChannelGlyph.Phone
        Channel.WHATSAPP -> ChannelGlyph.WhatsApp
        Channel.SMS -> ChannelGlyph.Message
        Channel.SIGNAL -> ChannelGlyph.Signal
        Channel.TELEGRAM -> ChannelGlyph.Plane
    }
    Box(
        Modifier.size(size).clip(CircleShape).background(C.Sunken).border(1.dp, C.Border, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(glyph, contentDescription = null, tint = C.Text, modifier = Modifier.size(size * 0.52f))
    }
}
