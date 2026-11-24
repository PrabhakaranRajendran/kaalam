package com.prabhu.kaalam.security

import android.content.Context
import android.content.SharedPreferences

/** Phone-only settings (not copied to Drive). */
object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        if (!::sp.isInitialized) sp = ctx.applicationContext.getSharedPreferences("kaalam_settings", Context.MODE_PRIVATE)
    }

    private fun str(k: String): String? = sp.getString(k, null)
    private fun setStr(k: String, v: String?) = sp.edit().apply { if (v == null) remove(k) else putString(k, v) }.apply()
    private fun bool(k: String, def: Boolean) = sp.getBoolean(k, def)
    private fun setBool(k: String, v: Boolean) = sp.edit().putBoolean(k, v).apply()

    // ---- password / vault ----
    var pinHash: String? get() = str("pinHash"); set(v) = setStr("pinHash", v)
    var pinSalt: String? get() = str("pinSalt"); set(v) = setStr("pinSalt", v)
    var dataSalt: String? get() = str("dataSalt"); set(v) = setStr("dataSalt", v)
    var wrappedKey: String? get() = str("wrappedKey"); set(v) = setStr("wrappedKey", v)
    var failedAttempts: Int get() = sp.getInt("failed", 0); set(v) = sp.edit().putInt("failed", v).apply()
    var lockoutUntil: Long get() = sp.getLong("lockoutUntil", 0); set(v) = sp.edit().putLong("lockoutUntil", v).apply()
    var pinChangedAt: Long get() = sp.getLong("pinChangedAt", 0); set(v) = sp.edit().putLong("pinChangedAt", v).apply()

    // ---- security ----
    var biometric: Boolean get() = bool("biometric", true); set(v) = setBool("biometric", v)
    var lockAfterSec: Int get() = sp.getInt("lockAfter", 60); set(v) = sp.edit().putInt("lockAfter", v).apply()
    var lockAdmin: Boolean get() = bool("lockAdmin", true); set(v) = setBool("lockAdmin", v)
    var hidePreview: Boolean get() = bool("hidePreview", true); set(v) = setBool("hidePreview", v)
    var blockScreenshots: Boolean get() = bool("blockShots", false); set(v) = setBool("blockShots", v)

    // ---- Google ----
    var googleEmail: String? get() = str("googleEmail"); set(v) = setStr("googleEmail", v)

    // ---- Drive ----
    var autoSave: Boolean get() = bool("autoSave", true); set(v) = setBool("autoSave", v)
    var dailyBackup: Boolean get() = bool("dailyBackup", true); set(v) = setBool("dailyBackup", v)
    var wifiOnly: Boolean get() = bool("wifiOnly", false); set(v) = setBool("wifiOnly", v)
    var lastBackupAt: Long get() = sp.getLong("lastBackupAt", 0); set(v) = sp.edit().putLong("lastBackupAt", v).apply()
    var lastDailyBackup: String? get() = str("lastDailyBackup"); set(v) = setStr("lastDailyBackup", v)

    // ---- Calendar ----
    var calendarId: String? get() = str("calendarId"); set(v) = setStr("calendarId", v)
    var calendarTag: Boolean get() = bool("calTag", true); set(v) = setBool("calTag", v)
    var calendarEmoji: Boolean get() = bool("calEmoji", true); set(v) = setBool("calEmoji", v)
    var autoSyncCalendar: Boolean get() = bool("calAuto", true); set(v) = setBool("calAuto", v)
    var calendarReminders: Boolean get() = bool("calReminders", false); set(v) = setBool("calReminders", v)
    var lastCalendarSync: Long get() = sp.getLong("lastCalSync", 0); set(v) = sp.edit().putLong("lastCalSync", v).apply()

    // ---- Notifications ----
    var reminderHour: Int get() = sp.getInt("reminderHour", 7); set(v) = sp.edit().putInt("reminderHour", v).apply()
    var sentKeys: Set<String>
        get() = sp.getStringSet("sentKeys", emptySet())?.toSet() ?: emptySet()
        set(v) = sp.edit().putStringSet("sentKeys", v).apply()
}
