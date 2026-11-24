package com.prabhu.kaalam.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Converts AppData to and from JSON (the same text that is encrypted into Google Drive). */
object Store {
    const val FORMAT = 1

    fun toJson(d: AppData): String {
        val o = JSONObject()
        o.put("format", FORMAT)
        o.put("updatedAt", d.updatedAt)
        o.put("profile", JSONObject().apply {
            put("name", d.profile.name)
            put("dob", d.profile.dob?.toString() ?: "")
        })
        o.put("customTypes", JSONArray(d.customTypes))
        o.put("pendingCalendarDeletes", JSONArray(d.pendingCalendarDeletes))
        o.put("wishDefaults", JSONObject().apply { d.wishDefaults.forEach { (k, v) -> put(k, v) } })
        o.put("people", JSONArray().apply { d.people.forEach { put(person(it)) } })
        return o.toString()
    }

    private fun person(p: Person) = JSONObject().apply {
        put("id", p.id)
        put("name", p.name)
        put("relation", p.relation)
        put("phone", p.phone)
        put("color", p.color)
        put("notes", p.notes)
        put("photo", p.photo)
        put("events", JSONArray().apply { p.events.forEach { put(event(it)) } })
    }

    private fun event(e: Event) = JSONObject().apply {
        put("id", e.id)
        put("type", e.type.key)
        put("customName", e.customName)
        put("date", e.date.toString())
        put("yearKnown", e.yearKnown)
        put("yearly", e.yearly)
        put("remindDays", JSONArray(e.remindDays))
        put("syncCalendar", e.syncCalendar)
        put("showCount", e.showCount)
        put("calendarEventId", e.calendarEventId ?: "")
        put("calendarSynced", e.calendarSynced)
    }

    fun fromJson(text: String): AppData {
        val o = JSONObject(text)
        val prof = o.optJSONObject("profile")
        val dob = prof?.optString("dob").orEmpty().takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) }
        return AppData(
            profile = Profile(prof?.optString("name").orEmpty(), dob),
            people = o.optJSONArray("people").objects().map { readPerson(it) },
            customTypes = o.optJSONArray("customTypes").strings(),
            pendingCalendarDeletes = o.optJSONArray("pendingCalendarDeletes").strings(),
            wishDefaults = o.optJSONObject("wishDefaults")?.let { w -> w.keys().asSequence().associateWith { w.optString(it) } } ?: emptyMap(),
            updatedAt = o.optLong("updatedAt", 0),
        )
    }

    private fun readPerson(o: JSONObject) = Person(
        id = o.optString("id").ifBlank { newId() },
        name = o.optString("name"),
        relation = o.optString("relation"),
        phone = o.optString("phone"),
        color = o.optInt("color", 0),
        notes = o.optString("notes"),
        photo = o.optLong("photo", 0),
        events = o.optJSONArray("events").objects().mapNotNull { readEvent(it) },
    )

    private fun readEvent(o: JSONObject): Event? {
        val date = runCatching { LocalDate.parse(o.optString("date")) }.getOrNull() ?: return null
        val remind = o.optJSONArray("remindDays")
        return Event(
            id = o.optString("id").ifBlank { newId() },
            type = EventType.of(o.optString("type")),
            customName = o.optString("customName"),
            date = date,
            yearKnown = o.optBoolean("yearKnown", true),
            yearly = o.optBoolean("yearly", true),
            remindDays = if (remind == null) listOf(0, 1) else (0 until remind.length()).map { remind.optInt(it) },
            syncCalendar = o.optBoolean("syncCalendar", true),
            showCount = o.optBoolean("showCount", true),
            calendarEventId = o.optString("calendarEventId").ifBlank { null },
            calendarSynced = o.optBoolean("calendarSynced", false),
        )
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { optString(it) }.filter { it.isNotBlank() }
}
