package com.autominimal.launcher.data

import android.Manifest
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Settings
import android.provider.Telephony
import androidx.core.content.ContextCompat
import java.io.File

/** How a people-list row is reached. The row icon is that channel, not the person's initials. */
enum class Channel { CALL, WHATSAPP, SMS, SIGNAL, TELEGRAM }

data class Person(
    /** Stable key used for hiding: "tel:<digits>", "wa:<name>", "signal:<name>", and so on. */
    val key: String,
    /** Full conversation name from the contact or the notification. Never shortened to a first name. */
    val name: String,
    val channel: Channel,
    val number: String? = null,
    /** ContactsContract.Data row of the WhatsApp chat action, when the contact is synced with WhatsApp. */
    val waDataId: Long? = null,
    val waMime: String? = null,
    /** WhatsApp id from that row, such as `316…@s.whatsapp.net` or a group `@g.us`. */
    val waJid: String? = null,
    /** Calls and messages counted in the ranking window. */
    val calls: Int = 0,
    val messages: Int = 0,
    /** App opened for a message. */
    val appPackage: String? = null,
) {
    val canCall get() = number != null
    val canChat get() = channel != Channel.CALL && (waDataId != null || messages > 0 || number != null || appPackage != null)

    /** A chat with no phone number behind it, such as a WhatsApp group. */
    val isGroup get() = channel != Channel.CALL && number == null && waDataId == null || waJid?.endsWith("@g.us") == true

    /** "B" for a person, "Ba" for a group, "#" for a bare phone number. */
    val initials: String
        get() {
            val first = name.firstOrNull { it.isLetter() } ?: return "#"
            if (name.trimStart().first().let { it == '+' || it.isDigit() }) return "#"
            if (!isGroup) return first.uppercase()
            val word = name.substring(name.indexOf(first)).takeWhile { it.isLetter() }
            return word.take(2).lowercase().replaceFirstChar { it.uppercase() }
        }
}

const val WHATSAPP = "com.whatsapp"
const val WHATSAPP_BUSINESS = "com.whatsapp.w4b"
const val SIGNAL = "org.thoughtcrime.securesms"
const val TELEGRAM = "org.telegram.messenger"
const val TELEGRAM_WEB = "org.telegram.messenger.web"

fun channelForPackage(pkg: String): Channel = when (pkg) {
    WHATSAPP, WHATSAPP_BUSINESS -> Channel.WHATSAPP
    SIGNAL -> Channel.SIGNAL
    TELEGRAM, TELEGRAM_WEB -> Channel.TELEGRAM
    else -> Channel.SMS
}
private const val WA_CHAT_MIME = "vnd.android.cursor.item/vnd.com.whatsapp.profile"
private const val WA_BUSINESS_CHAT_MIME = "vnd.android.cursor.item/vnd.com.whatsapp.w4b.profile"

/** Most-called contacts from the call log and most-messaged WhatsApp chats from [WhatsAppLog]. */
class PeopleRepository(private val context: Context) {
    val waLog = WhatsAppLog(context)
    val chatShortcuts = ChatShortcuts(context)
    private val launcherApps = context.getSystemService(LauncherApps::class.java)

    fun has(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun hasCallLog() = has(Manifest.permission.READ_CALL_LOG) && has(Manifest.permission.READ_CONTACTS)
    fun hasContacts() = has(Manifest.permission.READ_CONTACTS)

    fun hasNotificationAccess(): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
        val me = ComponentName(context, WhatsAppListener::class.java)
        return enabled.split(':').mapNotNull { ComponentName.unflattenFromString(it) }.any { it == me }
    }

    fun mostCalled(window: RankWindow): List<Person> {
        if (!hasCallLog()) return emptyList()
        data class Tally(var count: Int, val number: String, var name: String?)
        val tallies = LinkedHashMap<String, Tally>()
        val cursor = runCatching {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME),
                "${CallLog.Calls.DATE} >= ?", arrayOf(window.start().toString()),
                "${CallLog.Calls.DATE} DESC",
            )
        }.getOrNull() ?: return emptyList()
        cursor.use {
            while (it.moveToNext()) {
                val number = it.getString(0)?.takeIf { n -> n.isNotBlank() } ?: continue
                val key = numberKey(number) ?: continue
                val t = tallies.getOrPut(key) { Tally(0, number, null) }
                t.count++
                if (t.name == null) t.name = it.getString(1)?.takeIf { n -> n.isNotBlank() }
            }
        }
        val dialer = dialerPackage()
        return tallies.entries.sortedByDescending { it.value.count }.map { (key, t) ->
            Person(
                "tel:$key", t.name ?: lookupName(t.number) ?: t.number, Channel.CALL,
                number = t.number, calls = t.count, appPackage = dialer,
            )
        }
    }

    fun mostMessaged(window: RankWindow): List<Person> {
        val counts = waLog.counts(window.start())
        val waContacts = whatsAppContacts()
        val byName = waContacts.associateBy { it.name.trim().lowercase() }
        val ranked = counts.map { hit ->
            val channel = channelForPackage(hit.appPackage)
            val known = if (channel == Channel.WHATSAPP) byName[hit.name.trim().lowercase()] else null
            val person = known ?: Person(
                channelKey(channel, hit.name), hit.name, channel,
                number = lookupNumberByName(hit.name), appPackage = hit.appPackage,
            )
            person.copy(messages = hit.count, channel = channel, appPackage = hit.appPackage)
        }
        // With no message history yet, fall back to starred WhatsApp contacts.
        return ranked.ifEmpty {
            val starred = starredWaKeys()
            waContacts.filter { it.key in starred }
        }
    }

    /**
     * Calls and WhatsApp messages merged per person (matched on phone number, else on name). A tap
     * uses whichever channel is used more with that person.
     */
    fun mostContacted(window: RankWindow): List<Person> {
        val merged = LinkedHashMap<String, Person>()
        val called = mostCalled(window)
        called.forEach { merged[it.key] = it }
        val calledByName = called.associateBy { it.name }
        mostMessaged(window).filter { it.messages > 0 }.forEach { m ->
            val telKey = m.number?.let(::numberKey)?.let { "tel:$it" }
            val match = telKey?.let { merged[it] } ?: calledByName[m.name]?.let { merged[it.key] }
            if (match != null) {
                val messages = match.messages + m.messages
                val preferMessage = messages > match.calls
                merged[match.key] = match.copy(
                    messages = messages,
                    number = match.number ?: m.number,
                    waDataId = m.waDataId ?: match.waDataId, waMime = m.waMime ?: match.waMime,
                    waJid = m.waJid ?: match.waJid,
                    channel = if (preferMessage) m.channel else Channel.CALL,
                    appPackage = if (preferMessage) m.appPackage else match.appPackage,
                )
            } else {
                val key = telKey ?: m.key
                merged[key] = m.copy(key = key)
            }
        }
        return merged.values.sortedByDescending { it.calls + it.messages }
    }

    private fun starredWaKeys(): Set<String> {
        if (!hasContacts()) return emptySet()
        val names = HashSet<String>()
        runCatching {
            context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI, arrayOf(ContactsContract.Contacts.DISPLAY_NAME),
                "${ContactsContract.Contacts.STARRED} = 1", null, null,
            )
        }.getOrNull()?.use { while (it.moveToNext()) it.getString(0)?.let(names::add) }
        return names.map { "wa:$it" }.toSet()
    }

    /** Contacts that WhatsApp synced into the address book, with their chat action row. */
    private fun whatsAppContacts(): List<Person> {
        if (!hasContacts()) return emptyList()
        val out = LinkedHashMap<String, Person>()
        runCatching {
            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data._ID, ContactsContract.Data.DISPLAY_NAME, ContactsContract.Data.DATA1, ContactsContract.Data.MIMETYPE),
                "${ContactsContract.Data.MIMETYPE} IN (?, ?)", arrayOf(WA_CHAT_MIME, WA_BUSINESS_CHAT_MIME),
                null,
            )
        }.getOrNull()?.use {
            while (it.moveToNext()) {
                val name = it.getString(1) ?: continue
                if (name in out) continue
                val jid = it.getString(2)?.takeIf { j -> j.isNotBlank() }
                val number = jid?.substringBefore('@')?.takeIf { n -> n.all(Char::isDigit) }?.let { n -> "+$n" }
                val mime = it.getString(3)
                val pkg = if (mime == WA_BUSINESS_CHAT_MIME) WHATSAPP_BUSINESS else WHATSAPP
                out[name] = Person(
                    "wa:$name", name, Channel.WHATSAPP, number, it.getLong(0), mime,
                    waJid = jid, appPackage = pkg,
                )
            }
        }
        return out.values.toList()
    }

    private fun lookupName(number: String): String? {
        if (!hasContacts()) return null
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        return runCatching {
            context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull()
    }

    fun call(person: Person) {
        val number = person.number ?: return
        val action = if (has(Manifest.permission.CALL_PHONE)) Intent.ACTION_CALL else Intent.ACTION_DIAL
        context.startActivity(Intent(action, Uri.parse("tel:${Uri.encode(number)}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun chat(person: Person) {
        waLog.record(person.name, outgoing = true, pkg = person.appPackage ?: WHATSAPP)
        if (person.channel != Channel.CALL && openChatShortcut(person)) return
        val intent = when (person.channel) {
            Channel.CALL -> return call(person)
            Channel.WHATSAPP -> whatsAppIntent(person)
            Channel.SIGNAL -> signalIntent(person)
            Channel.TELEGRAM -> telegramIntent(person)
            Channel.SMS -> smsIntent(person)
        }
        val opened = intent?.let {
            runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        } == true
        if (!opened) person.appPackage?.let { pkg ->
            context.packageManager.getLaunchIntentForPackage(pkg)?.let {
                runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        }
    }

    /**
     * Opens the exact chat through the app's conversation shortcut, which also works for groups and
     * contacts without a number. Only the default home app may start other apps' shortcuts.
     */
    private fun openChatShortcut(person: Person): Boolean {
        val pkg = person.appPackage ?: return false
        if (!runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false)) return false
        val user = Process.myUserHandle()
        fun start(id: String) = runCatching { launcherApps.startShortcut(pkg, id, null, null, user) }.isSuccess
        val known = chatShortcuts.get(pkg, person.name)
        if (known != null && start(known)) return true
        val found = findChatShortcut(pkg, person.name) ?: return false
        if (found == known || !start(found)) return false
        chatShortcuts.put(pkg, person.name, found)
        return true
    }

    /** The app's published shortcut whose label is this chat name. */
    private fun findChatShortcut(pkg: String, name: String): String? {
        var flags = LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) flags = flags or LauncherApps.ShortcutQuery.FLAG_MATCH_CACHED
        val query = LauncherApps.ShortcutQuery().setPackage(pkg).setQueryFlags(flags)
        val shortcuts = runCatching { launcherApps.getShortcuts(query, Process.myUserHandle()) }.getOrNull().orEmpty()
        val target = name.trim()
        return shortcuts.firstOrNull { s ->
            s.isEnabled && listOf(s.shortLabel, s.longLabel).any { it?.toString()?.trim().equals(target, ignoreCase = true) }
        }?.id
    }

    /**
     * Opens that person's WhatsApp thread. A phone jid uses the official chat link; a group, or a
     * contact we only know as a synced row, uses WhatsApp's own chat action on that contact.
     */
    private fun whatsAppIntent(person: Person): Intent? {
        val pkg = person.appPackage?.takeIf { it == WHATSAPP || it == WHATSAPP_BUSINESS } ?: WHATSAPP
        val linked = if (person.waDataId != null || person.waJid != null) person else findWhatsAppContact(person.name)
        val jid = linked?.waJid ?: person.waJid
        val jidDigits = jid?.takeIf { it.endsWith("@s.whatsapp.net") }
            ?.substringBefore('@')?.filter(Char::isDigit)?.takeIf { it.length >= 8 }
        if (jidDigits != null) return waChatLink(jidDigits, pkg)
        val dataId = linked?.waDataId ?: person.waDataId
        if (dataId != null) {
            return Intent(Intent.ACTION_VIEW)
                .setDataAndType(
                    ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, dataId),
                    linked?.waMime ?: person.waMime ?: WA_CHAT_MIME,
                )
                .setPackage(pkg)
        }
        val digits = (person.number ?: linked?.number)?.filter(Char::isDigit)?.takeIf { it.length >= 8 }
        return digits?.let { waChatLink(it, pkg) }
    }

    private fun waChatLink(digits: String, pkg: String) =
        Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits")).setPackage(pkg)

    private fun findWhatsAppContact(name: String): Person? =
        whatsAppContacts().firstOrNull { it.name.trim().equals(name.trim(), ignoreCase = true) }

    private fun signalIntent(person: Person): Intent? {
        val digits = person.number?.filter(Char::isDigit)?.takeIf { it.isNotEmpty() } ?: return null
        return Intent(Intent.ACTION_VIEW, Uri.parse("https://signal.me/#p/+$digits")).setPackage(person.appPackage ?: SIGNAL)
    }

    private fun telegramIntent(person: Person): Intent? {
        val digits = person.number?.filter(Char::isDigit)?.takeIf { it.isNotEmpty() } ?: return null
        return Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?phone=$digits")).setPackage(person.appPackage ?: TELEGRAM)
    }

    private fun smsIntent(person: Person): Intent {
        val number = person.number?.filter { it.isDigit() || it == '+' }.orEmpty()
        return Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).setPackage(person.appPackage)
    }

    private fun dialerPackage(): String? =
        context.packageManager.resolveActivity(Intent(Intent.ACTION_DIAL), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName

    /** Phone number saved under this exact display name, so a tap can open the right chat. */
    private fun lookupNumberByName(name: String): String? {
        if (!hasContacts()) return null
        return runCatching {
            context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.HAS_PHONE_NUMBER),
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} = ?", arrayOf(name), null,
            )?.use { contact ->
                if (!contact.moveToFirst() || contact.getInt(1) == 0) return@use null
                val id = contact.getString(0)
                context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                    "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?", arrayOf(id), null,
                )?.use { phone -> if (phone.moveToFirst()) phone.getString(0) else null }
            }
        }.getOrNull()
    }

    private fun channelKey(channel: Channel, name: String) = when (channel) {
        Channel.WHATSAPP -> "wa:$name"
        Channel.SIGNAL -> "signal:$name"
        Channel.TELEGRAM -> "tg:$name"
        Channel.SMS -> "sms:$name"
        Channel.CALL -> "tel:$name"
    }

    companion object {
        /** Last 9 digits: enough to match "+31 6 1234 5678" with "06-12345678". */
        fun numberKey(number: String): String? = number.filter(Char::isDigit).takeLast(9).ifEmpty { null }
    }
}

/**
 * Conversation shortcut id per chat, taken from message notifications, so a tap opens that exact
 * chat. Lines are "<package>\t<conversation>\t<shortcut id>"; the latest id per chat wins.
 */
class ChatShortcuts(context: Context) {
    private val file = File(context.filesDir, "chat_shortcuts.tsv")
    private val ids: HashMap<String, String> by lazy {
        val map = HashMap<String, String>()
        if (file.exists()) file.forEachLine { line ->
            val parts = line.split('\t')
            if (parts.size == 3) map[key(parts[0], parts[1])] = parts[2]
        }
        map
    }

    @Synchronized
    fun get(pkg: String, conversation: String): String? = ids[key(pkg, conversation)]

    @Synchronized
    fun put(pkg: String, conversation: String, id: String) {
        val clean = conversation.replace('\t', ' ').replace('\n', ' ').trim()
        if (clean.isEmpty() || id.contains('\t') || id.contains('\n')) return
        if (ids.put(key(pkg, clean), id) == id) return
        file.writeText(ids.entries.joinToString("") { (k, v) -> "$k\t$v\n" })
    }

    private fun key(pkg: String, conversation: String) = "$pkg\t${conversation.trim().lowercase()}"
}

data class ConversationCount(val appPackage: String, val name: String, val count: Int)

/**
 * Append-only log of messages per conversation: incoming ones from [WhatsAppListener], outgoing ones
 * when a chat is opened from the launcher. Lines are "<millis>\t<conversation>\t<in|out>\t<package>".
 * Older lines have no package and are treated as WhatsApp.
 */
class WhatsAppLog(context: Context) {
    private val file = File(context.filesDir, "whatsapp_log.tsv")

    @Synchronized
    fun record(conversation: String, time: Long = System.currentTimeMillis(), outgoing: Boolean = false, pkg: String = WHATSAPP) {
        val clean = conversation.replace('\t', ' ').replace('\n', ' ').trim()
        if (clean.isEmpty()) return
        file.appendText("$time\t$clean\t${if (outgoing) "out" else "in"}\t$pkg\n")
        if (file.length() > MAX_BYTES) trim()
    }

    @Synchronized
    fun counts(since: Long): List<ConversationCount> {
        if (!file.exists()) return emptyList()
        val counts = HashMap<String, Int>()
        // The listener's in-memory dedupe resets when it restarts, so identical lines count once.
        val unique = HashSet<String>()
        file.forEachLine { line ->
            if (!unique.add(line)) return@forEachLine
            val parts = line.split('\t')
            val time = parts.getOrNull(0)?.toLongOrNull() ?: return@forEachLine
            val name = parts.getOrNull(1) ?: return@forEachLine
            if (time < since) return@forEachLine
            val pkg = parts.getOrNull(3)?.takeIf { it.isNotBlank() } ?: WHATSAPP
            val key = "$pkg\t$name"
            counts[key] = (counts[key] ?: 0) + 1
        }
        return counts.map { (key, count) ->
            val pkg = key.substringBefore('\t')
            ConversationCount(pkg, key.substringAfter('\t'), count)
        }.sortedByDescending { it.count }
    }

    /** Drops the oldest lines once the file is over the size cap, so all-time still has the rest. */
    private fun trim() {
        val lines = file.readLines()
        var bytes = lines.sumOf { it.length + 1L }
        var start = 0
        while (start < lines.size && bytes > MAX_BYTES) {
            bytes -= lines[start].length + 1L
            start++
        }
        val keep = lines.subList(start, lines.size)
        file.writeText(keep.joinToString("\n", postfix = if (keep.isEmpty()) "" else "\n"))
    }

    private companion object {
        const val MAX_BYTES = 512 * 1024
    }
}
