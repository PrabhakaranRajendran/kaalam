package com.prabhu.kaalam.google

import android.content.Context
import com.prabhu.kaalam.data.Event
import com.prabhu.kaalam.data.Person
import com.prabhu.kaalam.data.Repo
import com.prabhu.kaalam.data.Texts
import com.prabhu.kaalam.security.Prefs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.LocalDate
import java.time.ZoneId

/**
 * Google Calendar sync. Every Kaalam event is marked 5 ways:
 *  1. it lives in its own calendar called "Kaalam"
 *  2. that calendar is teal (#0F6E78)
 *  3. the title has an emoji and ends with " · Kaalam"
 *  4. the description ends with "⏳ Created by Kaalam app — edit inside Kaalam"
 *  5. a hidden private property source=kaalam (+ Kaalam's own event id), so syncing again
 *     updates the same event instead of making duplicates.
 */
object CalendarSync {
    private const val BASE = "https://www.googleapis.com/calendar/v3"
    const val CALENDAR_NAME = "Kaalam"
    const val COLOR = "#0F6E78"

    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val lastSync = MutableStateFlow(0L)
    private var pending: Job? = null
    private val mutex = Mutex()

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    fun requestAutoSync(ctx: Context) {
        if (!Prefs.autoSyncCalendar || !GoogleAuth.isConnected(ctx)) return
        pending?.cancel()
        pending = Repo.scope.launch {
            delay(1500)
            runCatching { syncAll(ctx) }
        }
    }

    private suspend fun ensureCalendar(ctx: Context): String {
        Prefs.calendarId?.let { id ->
            try {
                Api.json(ctx, "GET", "$BASE/calendars/${enc(id)}")
                return id
            } catch (e: ApiError) {
                if (e.code !in listOf(403, 404, 410)) throw e
            }
        }
        val items = Api.json(ctx, "GET", "$BASE/users/me/calendarList?minAccessRole=owner").optJSONArray("items") ?: JSONArray()
        var id: String? = null
        for (i in 0 until items.length()) {
            val c = items.getJSONObject(i)
            if (c.optString("summary") == CALENDAR_NAME) { id = c.getString("id"); break }
        }
        if (id == null) {
            val body = JSONObject()
                .put("summary", CALENDAR_NAME)
                .put("description", "Birthdays, anniversaries and events created by the Kaalam app. Edit them inside Kaalam.")
                .put("timeZone", ZoneId.systemDefault().id)
            id = Api.json(ctx, "POST", "$BASE/calendars", body).getString("id")
        }
        // Own colour + make sure it is shown.
        runCatching {
            Api.json(
                ctx, "PATCH", "$BASE/users/me/calendarList/${enc(id)}?colorRgbFormat=true",
                JSONObject().put("backgroundColor", COLOR).put("foregroundColor", "#FFFFFF").put("selected", true),
            )
        }
        Prefs.calendarId = id
        return id
    }

    fun eventBody(p: Person, ev: Event): JSONObject {
        val start: LocalDate = if (ev.yearKnown || !ev.yearly) ev.date else ev.date.withYear(2000)
        val o = JSONObject()
            .put("summary", Texts.calendarTitle(p, ev, Prefs.calendarEmoji, Prefs.calendarTag))
            .put("description", Texts.calendarDescription(p, ev))
            .put("start", JSONObject().put("date", start.toString()))
            .put("end", JSONObject().put("date", start.plusDays(1).toString()))
            .put("transparency", "transparent")
            .put("status", "confirmed")
            .put("source", JSONObject().put("title", "Kaalam app").put("url", "https://github.com/PrabhakaranRajendran/kaalam"))
            .put(
                "extendedProperties", JSONObject().put(
                    "private", JSONObject()
                        .put("source", "kaalam")
                        .put("kaalamEventId", ev.id)
                        .put("kaalamPersonId", p.id),
                ),
            )
        if (ev.yearly) {
            val rule = if (ev.date.monthValue == 2 && ev.date.dayOfMonth == 29)
                "RRULE:FREQ=YEARLY;BYMONTH=2;BYMONTHDAY=-1" else "RRULE:FREQ=YEARLY"
            o.put("recurrence", JSONArray().put(rule))
        }
        val overrides = JSONArray()
        if (Prefs.calendarReminders) {
            // All-day events: minutes counted back from midnight. 1 day before = 9:00 AM the day before.
            ev.remindDays.filter { it >= 1 }.sorted().take(5).forEach { d ->
                overrides.put(JSONObject().put("method", "popup").put("minutes", minOf(40320, d * 1440 - 540)))
            }
        }
        o.put("reminders", JSONObject().put("useDefault", false).put("overrides", overrides))
        return o
    }

    /** Pushes every event to the Kaalam calendar. Returns how many are in the calendar. */
    suspend fun syncAll(ctx: Context): Int = mutex.withLock {
        busy.value = true
        try {
            val calId = ensureCalendar(ctx)
            val events = "$BASE/calendars/${enc(calId)}/events"
            val snapshot = Repo.data.value

            val stillPending = mutableListOf<String>()
            for (id in snapshot.pendingCalendarDeletes.distinct()) {
                try {
                    Api.call(ctx, "DELETE", "$events/${enc(id)}")
                } catch (e: ApiError) {
                    if (e.code !in listOf(404, 410)) stillPending += id
                }
            }

            val results = mutableMapOf<String, String?>()
            val sent = mutableMapOf<String, Event>()
            var count = 0
            for (p in snapshot.people) for (ev in p.events) {
                if (ev.syncCalendar) {
                    sent[ev.id] = ev
                    val body = eventBody(p, ev)
                    var id: String? = null
                    val existing = ev.calendarEventId
                    if (existing != null) {
                        try {
                            Api.json(ctx, "PUT", "$events/${enc(existing)}", body)
                            id = existing
                        } catch (e: ApiError) {
                            if (e.code !in listOf(404, 410)) throw e
                        }
                    }
                    if (id == null) id = Api.json(ctx, "POST", events, body).getString("id")
                    results[ev.id] = id
                    count++
                } else if (ev.calendarEventId != null) {
                    try {
                        Api.call(ctx, "DELETE", "$events/${enc(ev.calendarEventId)}")
                    } catch (e: ApiError) {
                        if (e.code !in listOf(404, 410)) throw e
                    }
                    results[ev.id] = null
                }
            }

            Repo.updateQuiet { d ->
                val newDeletes = d.pendingCalendarDeletes - snapshot.pendingCalendarDeletes.toSet()
                d.copy(
                    pendingCalendarDeletes = stillPending + newDeletes,
                    people = d.people.map { p ->
                        p.copy(events = p.events.map { e ->
                            if (!results.containsKey(e.id)) e
                            else {
                                val calId2 = results[e.id]
                                val before = sent[e.id]
                                // Only mark synced if the event wasn't edited while we were syncing.
                                val unchanged = before != null &&
                                    before.copy(calendarEventId = null, calendarSynced = false) ==
                                    e.copy(calendarEventId = null, calendarSynced = false)
                                e.copy(calendarEventId = calId2, calendarSynced = calId2 != null && unchanged)
                            }
                        })
                    },
                )
            }
            Prefs.lastCalendarSync = System.currentTimeMillis()
            lastSync.value = Prefs.lastCalendarSync
            error.value = null
            count
        } catch (e: Exception) {
            error.value = Api.friendly(e)
            throw e
        } finally {
            busy.value = false
        }
    }
}
