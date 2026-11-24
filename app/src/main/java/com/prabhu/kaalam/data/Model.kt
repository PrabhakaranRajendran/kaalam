package com.prabhu.kaalam.data

import java.time.LocalDate
import java.time.Year
import java.time.temporal.ChronoUnit
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(16)

enum class EventType(val key: String, val label: String, val emoji: String) {
    BIRTHDAY("birthday", "Birthday", "🎂"),
    ANNIVERSARY("anniversary", "Anniversary", "💍"),
    CUSTOM("custom", "Custom", "⭐");

    companion object {
        fun of(key: String?): EventType = entries.firstOrNull { it.key == key } ?: CUSTOM
    }
}

data class Event(
    val id: String = newId(),
    val type: EventType = EventType.BIRTHDAY,
    val customName: String = "",
    val date: LocalDate,
    val yearKnown: Boolean = true,
    val yearly: Boolean = true,
    val remindDays: List<Int> = listOf(0, 1),
    val syncCalendar: Boolean = true,
    val showCount: Boolean = true,
    val calendarEventId: String? = null,
    val calendarSynced: Boolean = false,
)

data class Person(
    val id: String = newId(),
    val name: String,
    val relation: String = "",
    val phone: String = "",
    val color: Int = 0,
    val notes: String = "",
    val photo: Long = 0,          // 0 = no photo; otherwise the time it was set (busts image caches)
    val events: List<Event> = emptyList(),
)

data class Profile(
    val name: String = "",
    val dob: LocalDate? = null,
)

data class AppData(
    val profile: Profile = Profile(),
    val people: List<Person> = emptyList(),
    val customTypes: List<String> = listOf("Graduation day", "Puja", "Shop anniversary"),
    val pendingCalendarDeletes: List<String> = emptyList(),
    val wishDefaults: Map<String, String> = emptyMap(),   // EventType.key -> your default WhatsApp message
    val updatedAt: Long = 0,
)

/** One upcoming (or past one-time) occurrence of an event. */
data class Occurrence(
    val person: Person,
    val event: Event,
    val date: LocalDate?,      // null = one-time event already over
    val daysLeft: Long?,
    val count: Int?,           // "turns 64", "8th anniversary"
)

object DateMath {
    /** The same day/month in another year. 29 Feb falls back to 28 Feb in non-leap years. */
    fun onYear(d: LocalDate, year: Int): LocalDate =
        if (d.monthValue == 2 && d.dayOfMonth == 29 && !Year.isLeap(year.toLong()))
            LocalDate.of(year, 2, 28)
        else LocalDate.of(year, d.monthValue, d.dayOfMonth)

    fun next(ev: Event, today: LocalDate): LocalDate? {
        if (!ev.yearly) return if (ev.date.isBefore(today)) null else ev.date
        val thisYear = onYear(ev.date, today.year)
        return if (thisYear.isBefore(today)) onYear(ev.date, today.year + 1) else thisYear
    }

    fun nextBirthday(dob: LocalDate, today: LocalDate): LocalDate {
        val thisYear = onYear(dob, today.year)
        return if (thisYear.isBefore(today)) onYear(dob, today.year + 1) else thisYear
    }

    fun occurrence(p: Person, ev: Event, today: LocalDate): Occurrence {
        val n = next(ev, today)
        val days = n?.let { ChronoUnit.DAYS.between(today, it) }
        val count = if (n != null && ev.yearKnown && ev.yearly) (n.year - ev.date.year).takeIf { it > 0 } else null
        return Occurrence(p, ev, n, days, count)
    }

    fun ordinal(n: Int): String {
        val s = if (n % 100 in 11..13) "th" else when (n % 10) {
            1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th"
        }
        return "$n$s"
    }
}
