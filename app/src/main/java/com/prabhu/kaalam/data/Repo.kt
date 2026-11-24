package com.prabhu.kaalam.data

import android.content.Context
import com.prabhu.kaalam.google.CalendarSync
import com.prabhu.kaalam.google.DriveSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.time.LocalDate

/**
 * The single source of truth. Data lives in a private file on the phone (works offline);
 * every change is saved here first, then copied (encrypted) to Google Drive and
 * pushed to the Kaalam calendar in the background.
 */
object Repo {
    private lateinit var app: Context
    private val _data = MutableStateFlow(AppData())
    val data: StateFlow<AppData> = _data
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var loaded = false
    private val lock = Any()

    fun init(ctx: Context) {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            app = ctx.applicationContext
            _data.value = read()
            loaded = true
        }
    }

    private fun file() = File(app.filesDir, "kaalam_data.json")

    private fun read(): AppData = runCatching {
        val f = file()
        if (f.exists()) Store.fromJson(f.readText()) else AppData()
    }.getOrDefault(AppData())

    private fun write(d: AppData) = synchronized(lock) {
        val tmp = File(app.filesDir, "kaalam_data.json.tmp")
        tmp.writeText(Store.toJson(d))
        if (!tmp.renameTo(file())) {
            file().writeText(tmp.readText()); tmp.delete()
        }
    }

    /** A user change: saves, then backs up to Drive and syncs the calendar (if switched on). */
    fun update(transform: (AppData) -> AppData) {
        val nd = synchronized(lock) {
            transform(_data.value).copy(updatedAt = System.currentTimeMillis()).also { _data.value = it; write(it) }
        }
        DriveSync.requestAutoUpload(app)
        if (nd.pendingCalendarDeletes.isNotEmpty() || nd.people.any { p -> p.events.any { it.syncCalendar && !it.calendarSynced } || p.events.any { !it.syncCalendar && it.calendarEventId != null } }) {
            CalendarSync.requestAutoSync(app)
        }
    }

    /** A background change (sync results): saves without triggering another sync. */
    fun updateQuiet(transform: (AppData) -> AppData) {
        synchronized(lock) {
            val nd = transform(_data.value)
            _data.value = nd; write(nd)
        }
    }

    /** Restore from a Drive backup. */
    fun replaceAll(d: AppData) {
        synchronized(lock) { _data.value = d; write(d) }
    }

    // ---------- people & events ----------

    fun person(id: String): Person? = _data.value.people.firstOrNull { it.id == id }

    fun upsertPerson(p: Person) = update { d ->
        val exists = d.people.any { it.id == p.id }
        d.copy(people = if (exists) d.people.map { if (it.id == p.id) p else it } else d.people + p)
    }

    /** Edits name / relation / phone; marks events for re-sync because the title includes the name. */
    fun editPersonDetails(id: String, name: String, relation: String, phone: String, notes: String) = update { d ->
        d.copy(people = d.people.map { p ->
            if (p.id != id) p else p.copy(
                name = name, relation = relation, phone = phone, notes = notes,
                events = p.events.map { it.copy(calendarSynced = false) },
            )
        })
    }

    fun deletePerson(id: String) = update { d ->
        val gone = d.people.firstOrNull { it.id == id }
        val calIds = gone?.events?.mapNotNull { it.calendarEventId }.orEmpty()
        d.copy(
            people = d.people.filterNot { it.id == id },
            pendingCalendarDeletes = d.pendingCalendarDeletes + calIds,
        )
    }

    fun upsertEvent(personId: String, ev: Event) = update { d ->
        d.copy(people = d.people.map { p ->
            if (p.id != personId) p
            else {
                val e = ev.copy(calendarSynced = false)
                val exists = p.events.any { it.id == ev.id }
                p.copy(events = if (exists) p.events.map { if (it.id == ev.id) e else it } else p.events + e)
            }
        })
    }

    fun deleteEvent(personId: String, eventId: String) = update { d ->
        val ev = d.people.firstOrNull { it.id == personId }?.events?.firstOrNull { it.id == eventId }
        d.copy(
            people = d.people.map { p -> if (p.id != personId) p else p.copy(events = p.events.filterNot { it.id == eventId }) },
            pendingCalendarDeletes = d.pendingCalendarDeletes + listOfNotNull(ev?.calendarEventId),
        )
    }

    fun setProfile(name: String, dob: LocalDate?) = update { d -> d.copy(profile = Profile(name.trim(), dob)) }

    fun addCustomType(name: String) {
        val n = name.trim()
        if (n.isBlank() || _data.value.customTypes.any { it.equals(n, ignoreCase = true) }) return
        update { d -> d.copy(customTypes = d.customTypes + n) }
    }

    fun removeCustomType(name: String) = update { d -> d.copy(customTypes = d.customTypes - name) }

    /** After changing calendar title/reminder settings: every event is pushed again. */
    fun markAllUnsynced() = update { d ->
        d.copy(people = d.people.map { p -> p.copy(events = p.events.map { it.copy(calendarSynced = false) }) })
    }

    // ---------- queries ----------

    fun occurrences(d: AppData, today: LocalDate): List<Occurrence> =
        d.people.flatMap { p -> p.events.map { DateMath.occurrence(p, it, today) } }
            .sortedWith(compareBy<Occurrence> { it.daysLeft == null }.thenBy { it.daysLeft ?: Long.MAX_VALUE }.thenBy { it.person.name.lowercase() })

    fun upcoming(d: AppData, today: LocalDate): List<Occurrence> = occurrences(d, today).filter { it.date != null }
}
