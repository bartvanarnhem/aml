package com.autominimal.launcher.data

import android.app.Notification
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * These apps have no API for message counts, so incoming message notifications are counted per
 * conversation instead. Only the conversation name and a timestamp are stored, never message text.
 * The stored name is the full chat or group title, not the sender's first name.
 */
class WhatsAppListener : NotificationListenerService() {
    /** Messages already counted, by "package|conversation|sender|time"; apps re-post history on every update. */
    private val seen = LinkedHashSet<String>()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!isMessageApp(sbn.packageName)) return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = n.extras
        val conversation = conversationName(extras) ?: return
        val log = (application as com.autominimal.launcher.LauncherApp).people.waLog
        val pkg = sbn.packageName
        // The conversation shortcut lets a tap on the launcher open this exact chat.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) n.shortcutId?.let {
            (application as com.autominimal.launcher.LauncherApp).people.chatShortcuts.put(pkg, conversation, it)
        }

        @Suppress("DEPRECATION")
        val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)?.filterIsInstance<Bundle>().orEmpty()
        if (messages.isEmpty()) {
            if (remember("$pkg|$conversation|${n.`when`}")) log.record(conversation, n.`when`, pkg = pkg)
            return
        }
        for (m in messages) {
            val time = m.getLong("time")
            val sender = m.getCharSequence("sender")?.toString().orEmpty()
            if (remember("$pkg|$conversation|$sender|$time")) log.record(conversation, time, pkg = pkg)
        }
    }

    private fun isMessageApp(pkg: String): Boolean = when (pkg) {
        WHATSAPP, WHATSAPP_BUSINESS, SIGNAL, TELEGRAM, TELEGRAM_WEB -> true
        else -> pkg == Telephony.Sms.getDefaultSmsPackage(this)
    }

    /**
     * Group chats expose the group title separately from the person who sent the latest message.
     * Use that full title; otherwise the notification title, which is the contact's display name.
     */
    private fun conversationName(extras: Bundle): String? {
        val group = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()?.trim()
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
        val raw = group?.takeIf { it.isNotEmpty() } ?: title ?: return null
        return raw.replace(Regex("""\s*\(\d+ [^)]*\)$"""), "").trim()
            .takeIf { it.isNotEmpty() && it !in APP_TITLES }
    }

    private fun remember(key: String): Boolean {
        if (!seen.add(key)) return false
        if (seen.size > 2000) seen.iterator().run { repeat(500) { next(); remove() } }
        return true
    }

    private companion object {
        val APP_TITLES = setOf("WhatsApp", "WhatsApp Business", "Signal", "Telegram", "Messages")
    }
}
