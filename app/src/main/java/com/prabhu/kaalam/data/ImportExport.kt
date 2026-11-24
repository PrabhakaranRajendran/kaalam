package com.prabhu.kaalam.data

import android.content.Context
import android.provider.ContactsContract
import java.time.LocalDate

data class ContactEvent(
    val name: String,
    val type: EventType,
    val date: LocalDate,
    val yearKnown: Boolean,
)

/** Birthdays and anniversaries saved in the phone's contacts. */
object ContactsImport {
    fun read(ctx: Context): List<ContactEvent> {
        val projection = arrayOf(
            ContactsContract.Data.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Event.START_DATE,
            ContactsContract.CommonDataKinds.Event.TYPE,
        )
        val out = mutableListOf<ContactEvent>()
        ctx.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            projection,
            "${ContactsContract.Data.MIMETYPE} = ?",
            arrayOf(ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE),
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(0)?.trim().orEmpty()
                val raw = c.getString(1)?.trim().orEmpty()
                val type = when (c.getInt(2)) {
                    ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY -> EventType.BIRTHDAY
                    ContactsContract.CommonDataKinds.Event.TYPE_ANNIVERSARY -> EventType.ANNIVERSARY
                    else -> null
                } ?: continue
                if (name.isBlank()) continue
                val parsed = parse(raw) ?: continue
                out += ContactEvent(name, type, parsed.first, parsed.second)
            }
        }
        return out.distinctBy { "${it.name.lowercase()}|${it.type}|${it.date.monthValue}-${it.date.dayOfMonth}" }
            .sortedBy { it.name.lowercase() }
    }

    /** "1990-01-15", "--01-15" (no year), "19900115". Returns date + whether the year is real. */
    private fun parse(s: String): Pair<LocalDate, Boolean>? = runCatching {
        when {
            s.startsWith("--") -> {
                val p = s.removePrefix("--").split("-")
                LocalDate.of(2000, p[0].toInt(), p[1].take(2).toInt()) to false
            }
            s.length >= 10 && s[4] == '-' -> LocalDate.parse(s.take(10)) to true
            s.length == 8 && s.all { it.isDigit() } ->
                LocalDate.of(s.take(4).toInt(), s.substring(4, 6).toInt(), s.substring(6, 8).toInt()) to true
            else -> null
        }
    }.getOrNull()

    fun alreadyExists(d: AppData, c: ContactEvent): Boolean = d.people.any { p ->
        p.name.equals(c.name, ignoreCase = true) && p.events.any {
            it.type == c.type && it.date.monthValue == c.date.monthValue && it.date.dayOfMonth == c.date.dayOfMonth
        }
    }

    fun import(list: List<ContactEvent>) = Repo.update { d ->
        var people = d.people
        list.forEach { c ->
            val e = Event(type = c.type, date = c.date, yearKnown = c.yearKnown, yearly = true)
            val existing = people.firstOrNull { it.name.equals(c.name, ignoreCase = true) }
            people = if (existing != null) {
                people.map { if (it.id == existing.id) it.copy(events = it.events + e) else it }
            } else {
                people + Person(name = c.name, color = (people.size % 8), events = listOf(e))
            }
        }
        d.copy(people = people)
    }
}

/** A CSV file that opens in Excel / Google Sheets. */
object CsvExport {
    private fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""

    fun build(d: AppData, today: LocalDate): String {
        val sb = StringBuilder("﻿") // BOM so Excel shows Tamil/emoji correctly
        sb.append("Name,Relation,Phone,Event,Date,Year known,Repeats,Next date,Days left,In calendar\r\n")
        Repo.occurrences(d, today).forEach { o ->
            sb.append(listOf(
                q(o.person.name), q(o.person.relation), q(o.person.phone),
                q(Texts.eventLabel(o.event)),
                q(if (o.event.yearKnown) o.event.date.toString() else o.event.date.format(Texts.dMMM)),
                if (o.event.yearKnown) "Yes" else "No",
                if (o.event.yearly) "Yearly" else "One-time",
                o.date?.toString() ?: "",
                o.daysLeft?.toString() ?: "",
                if (o.event.syncCalendar) "Yes" else "No",
            ).joinToString(",")).append("\r\n")
        }
        return sb.toString()
    }
}
