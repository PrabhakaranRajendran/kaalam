package com.prabhu.kaalam.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** All the wording used across screens, notifications and Google Calendar. */
object Texts {
    private val en = Locale.ENGLISH
    val dMMM: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", en)
    val dMMMyyyy: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", en)
    val eeeDMMM: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM", en)
    val eeeeDMMMMyyyy: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", en)
    val monthYear: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", en)
    val month: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM", en)
    val eee: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE", en)

    fun possessive(name: String): String = if (name.endsWith("s") || name.endsWith("S")) "$name'" else "$name's"

    /** "Birthday", "Anniversary" or the custom name. */
    fun eventLabel(ev: Event): String = when (ev.type) {
        EventType.CUSTOM -> ev.customName.ifBlank { "Event" }
        else -> ev.type.label
    }

    fun dateText(ev: Event): String =
        if (ev.yearKnown) ev.date.format(dMMMyyyy) else ev.date.format(dMMM)

    fun repeatText(ev: Event): String = if (ev.yearly) "repeats yearly" else "one-time"

    /** e.g. "turns 64", "8th anniversary", "2nd" — or null when not shown. */
    fun countText(o: Occurrence): String? {
        val c = o.count ?: return null
        if (!o.event.showCount) return null
        return when (o.event.type) {
            EventType.BIRTHDAY -> "turns $c"
            EventType.ANNIVERSARY -> "${DateMath.ordinal(c)} anniversary"
            EventType.CUSTOM -> DateMath.ordinal(c)
        }
    }

    /** List subtitle: "🎂 Birthday · 3 Dec · turns 64" */
    fun subtitle(o: Occurrence): String {
        val parts = mutableListOf("${o.event.type.emoji} ${eventLabel(o.event)}")
        parts += (o.date ?: o.event.date).format(dMMM)
        countText(o)?.let { parts += it }
        if (!o.event.yearly) parts += if (o.date == null) "past" else "one-time"
        return parts.joinToString(" · ")
    }

    /** Short headline: "Amma · 🎂 turns 64" */
    fun headline(o: Occurrence): String {
        val c = countText(o)
        val tail = when {
            c != null && o.event.type == EventType.BIRTHDAY -> "${o.event.type.emoji} $c"
            c != null && o.event.type == EventType.ANNIVERSARY -> "${o.event.type.emoji} ${DateMath.ordinal(o.count!!)}"
            o.event.type == EventType.CUSTOM -> "${o.event.type.emoji} ${eventLabel(o.event)}"
            else -> "${o.event.type.emoji} ${eventLabel(o.event)}"
        }
        return "${o.person.name} · $tail"
    }

    fun daysBadge(days: Long?): String = when {
        days == null -> "Past"
        days == 0L -> "Today"
        days == 1L -> "Tomorrow"
        else -> "$days d"
    }

    fun daysLong(days: Long?): String = when {
        days == null -> "Past"
        days == 0L -> "Today"
        days == 1L -> "Tomorrow"
        else -> "$days days"
    }

    fun remindText(days: List<Int>): String =
        if (days.isEmpty()) "No reminders" else days.sorted().joinToString(", ") { remindLabel(it) }

    fun remindLabel(d: Int): String = when (d) {
        0 -> "On the day"
        1 -> "1 day before"
        7 -> "1 week before"
        14 -> "2 weeks before"
        else -> "$d days before"
    }

    // ---------- Google Calendar ----------

    fun calendarTitle(p: Person, ev: Event, emoji: Boolean, tag: Boolean): String {
        val base = when (ev.type) {
            EventType.BIRTHDAY -> "${possessive(p.name)} Birthday"
            EventType.ANNIVERSARY -> "${possessive(p.name)} Anniversary"
            EventType.CUSTOM ->
                if (p.name.isBlank() || p.name.equals(ev.customName, ignoreCase = true)) eventLabel(ev)
                else "${eventLabel(ev)} – ${p.name}"
        }
        return (if (emoji) ev.type.emoji + " " else "") + base + (if (tag) " · Kaalam" else "")
    }

    fun calendarDescription(p: Person, ev: Event): String {
        val first = when (ev.type) {
            EventType.BIRTHDAY -> "Birthday of ${p.name}" + if (ev.yearKnown) " · born ${ev.date.format(dMMMyyyy)}" else ""
            EventType.ANNIVERSARY -> "Anniversary of ${p.name}" + if (ev.yearKnown) " · since ${ev.date.format(dMMMyyyy)}" else ""
            EventType.CUSTOM -> eventLabel(ev) + (if (p.name.isNotBlank()) " · ${p.name}" else "") +
                if (ev.yearKnown) " · ${ev.date.format(dMMMyyyy)}" else ""
        }
        val lines = mutableListOf(first)
        if (p.relation.isNotBlank()) lines += "Relation: ${p.relation}"
        if (p.phone.isNotBlank()) lines += "📞 ${p.phone}"
        lines += ""
        lines += "⏳ Created by Kaalam app — edit inside Kaalam"
        return lines.joinToString("\n")
    }

    // ---------- Notifications & wishes ----------

    fun notificationTitle(p: Person, ev: Event, next: LocalDate, daysBefore: Int): String {
        val whenText = when (daysBefore) {
            0 -> "today"
            1 -> "tomorrow"
            else -> "in $daysBefore days"
        }
        val count = if (ev.yearKnown && ev.yearly && ev.showCount) (next.year - ev.date.year).takeIf { it > 0 } else null
        return when (ev.type) {
            EventType.BIRTHDAY ->
                if (count != null) "🎂 ${p.name} turns $count $whenText!" else "🎂 ${possessive(p.name)} birthday is $whenText!"
            EventType.ANNIVERSARY ->
                if (count != null) "💍 ${possessive(p.name)} ${DateMath.ordinal(count)} anniversary is $whenText!"
                else "💍 ${possessive(p.name)} anniversary is $whenText!"
            EventType.CUSTOM ->
                "⭐ ${eventLabel(ev)}" + (if (p.name.isNotBlank() && !p.name.equals(ev.customName, true)) " – ${p.name}" else "") + " is $whenText"
        }
    }

    fun wishMessage(p: Person, ev: Event): String {
        val first = p.name.trim()
        return when (ev.type) {
            EventType.BIRTHDAY -> "Happy Birthday $first! 🎂🎉 Wishing you a wonderful year ahead filled with health and happiness."
            EventType.ANNIVERSARY -> "Happy Anniversary $first! 💍✨ Wishing you many more years of love and togetherness."
            EventType.CUSTOM -> "Happy ${eventLabel(ev)}, $first! ⭐ Best wishes from me."
        }
    }

    /** WhatsApp link. 10-digit numbers get India's +91 added. */
    fun whatsAppUrl(phone: String, message: String): String {
        var digits = phone.filter { it.isDigit() }
        if (digits.length == 10) digits = "91$digits"
        if (digits.length == 11 && digits.startsWith("0")) digits = "91" + digits.drop(1)
        val text = android.net.Uri.encode(message)
        return if (digits.isNotEmpty()) "https://wa.me/$digits?text=$text" else "https://wa.me/?text=$text"
    }
}
