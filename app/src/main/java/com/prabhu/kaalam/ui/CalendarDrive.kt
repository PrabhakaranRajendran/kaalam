package com.prabhu.kaalam.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prabhu.kaalam.data.AppData
import com.prabhu.kaalam.data.Repo
import com.prabhu.kaalam.data.Store
import com.prabhu.kaalam.data.Texts
import com.prabhu.kaalam.google.Api
import com.prabhu.kaalam.google.CalendarSync
import com.prabhu.kaalam.google.DriveSync
import com.prabhu.kaalam.google.GoogleAuth
import com.prabhu.kaalam.security.Prefs
import com.prabhu.kaalam.security.Vault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

// ============================ CALENDAR TAB ============================

@Composable
fun CalendarScreen(nav: Nav, data: AppData) {
    val ctx = LocalContext.current
    val today = LocalDate.now()
    val connected = GoogleAuth.isConnected(ctx)
    val lastSync by CalendarSync.lastSync.collectAsState()
    val syncable = data.people.flatMap { it.events }.filter { it.syncCalendar }
    val synced = syncable.count { it.calendarSynced }
    val upcoming = remember(data, today) { Repo.upcoming(data, today) }
    val byMonth = upcoming.groupBy { YearMonth.from(it.date!!) }.toSortedMap()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { TopBar("Calendar", onBack = null) }
        item {
            KCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), onClick = { nav.go(Route.CalendarSettings) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(K.TealLight), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.CalendarMonth, null, tint = K.Teal)
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        if (connected) {
                            Text("Kaalam calendar", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("$synced of ${syncable.size} events synced · ${timeAgo(maxOf(lastSync, Prefs.lastCalendarSync))}", fontSize = 12.sp, color = K.Muted)
                        } else {
                            Text("Connect Google Calendar", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Put your events in a separate “Kaalam” calendar", fontSize = 12.sp, color = K.Muted)
                        }
                    }
                    Pill(if (connected) "● On" else "Set up", if (connected) K.GreenBg else K.TealLight, if (connected) K.Green else K.Teal)
                }
            }
        }
        if (upcoming.isEmpty()) item { EmptyState("📅", "No upcoming events", "Events you add show up here month by month.") }
        byMonth.forEach { (ym, list) ->
            item(key = "m$ym") {
                Text(
                    ym.atDay(1).format(Texts.monthYear), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = K.TealDark,
                    modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 6.dp),
                )
            }
            list.groupBy { it.date!! }.forEach { (day, items) ->
                item(key = "d$day") {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Column(Modifier.width(44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(day.format(Texts.eee).uppercase(), fontSize = 11.sp, color = if (day == today) K.Teal else K.Muted)
                            Text(day.dayOfMonth.toString(), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = if (day == today) K.Teal else K.Ink)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items.forEach { o ->
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(K.Teal)
                                        .clickable { nav.go(Route.PersonDetail(o.person.id)) },
                                ) {
                                    Box(Modifier.width(5.dp).height(52.dp).background(o.event.type.color()))
                                    Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                                        Text(Texts.calendarTitle(o.person, o.event, emoji = true, tag = false), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
                                        Text(
                                            listOfNotNull(Texts.countText(o), Texts.daysLong(o.daysLeft).let { if ((o.daysLeft ?: 0) > 1) "in $it" else it }).joinToString(" · "),
                                            color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================ GOOGLE ACCOUNT CARD ============================

@Composable
fun GoogleAccountCard(onConnected: (String) -> Unit = {}) {
    val ctx = LocalContext.current
    var connected by remember { mutableStateOf(GoogleAuth.isConnected(ctx)) }
    var email by remember { mutableStateOf(GoogleAuth.email(ctx)) }
    var error by remember { mutableStateOf<String?>(null) }
    val signIn = rememberGoogleSignIn { e, err ->
        if (err != null) error = err
        else {
            connected = GoogleAuth.isConnected(ctx); email = e; error = null
            if (e != null) onConnected(e)
        }
    }
    KCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, K.Line, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Text("G", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = K.Blue)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("Google account", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(if (connected) email ?: "Connected" else "Not connected", fontSize = 12.sp, color = K.Muted)
            }
            if (connected) Pill("● Connected", K.GreenBg, K.Green)
        }
        if (!connected) {
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Connect Google account", Modifier.fillMaxWidth()) { error = null; signIn(false) }
            Text(
                "Kaalam asks for two permissions: files it creates in your Drive (not your other files) and your calendars.",
                fontSize = 11.sp, color = K.Muted, modifier = Modifier.padding(top = 8.dp),
            )
        }
        ErrorText(error)
    }
}

// ============================ CALENDAR SETTINGS ============================

@Composable
fun CalendarSettingsScreen(nav: Nav, data: AppData) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val busy by CalendarSync.busy.collectAsState()
    val err by CalendarSync.error.collectAsState()
    val last by CalendarSync.lastSync.collectAsState()
    var connected by remember { mutableStateOf(GoogleAuth.isConnected(ctx)) }
    var tag by remember { mutableStateOf(Prefs.calendarTag) }
    var emoji by remember { mutableStateOf(Prefs.calendarEmoji) }
    var auto by remember { mutableStateOf(Prefs.autoSyncCalendar) }
    var reminders by remember { mutableStateOf(Prefs.calendarReminders) }
    val count = data.people.sumOf { p -> p.events.count { it.syncCalendar } }
    val sample = data.people.firstOrNull { it.events.isNotEmpty() }
    val preview = if (sample != null) Texts.calendarTitle(sample, sample.events.first(), emoji, tag)
    else (if (emoji) "🎂 " else "") + "Amma's Birthday" + (if (tag) " · Kaalam" else "")

    fun syncNow() = scope.launch {
        try {
            val n = CalendarSync.syncAll(ctx)
            Toast.makeText(ctx, "Synced $n events to the Kaalam calendar", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) { }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("Calendar sync", onBack = { nav.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            GoogleAccountCard { connected = true; syncNow() }

            SectionTitle("Sync into")
            KCard(Modifier.fillMaxWidth().border(2.dp, K.Teal, CardShape)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(16.dp).clip(RoundedCornerShape(5.dp)).background(K.Teal))
                    Text("  Kaalam", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("  (separate calendar)", fontSize = 12.sp, color = K.Muted, modifier = Modifier.weight(1f))
                    Text("✓", color = K.Teal, fontWeight = FontWeight.Bold)
                }
                Text(
                    "Created automatically in your Google Calendar, in teal. Untick it in Google Calendar to hide all Kaalam events at once, without touching your other events.",
                    fontSize = 12.sp, color = K.Muted, modifier = Modifier.padding(top = 6.dp),
                )
            }

            SectionTitle("Event title preview")
            Text(
                preview, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(K.Teal).padding(14.dp),
            )
            Spacer(Modifier.height(10.dp))
            KCard(padding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)) {
                ToggleRow("Add “· Kaalam” tag to title", null, tag) { tag = it; Prefs.calendarTag = it; Repo.markAllUnsynced() }
                ThinDivider()
                ToggleRow("Emoji by event type", "🎂 💍 ⭐", emoji) { emoji = it; Prefs.calendarEmoji = it; Repo.markAllUnsynced() }
                ThinDivider()
                ToggleRow("Auto-sync on save / edit / delete", null, auto) { auto = it; Prefs.autoSyncCalendar = it }
                ThinDivider()
                ToggleRow("Calendar reminders too", "Off = only Kaalam notifies you (no double alerts)", reminders) {
                    reminders = it; Prefs.calendarReminders = it; Repo.markAllUnsynced()
                }
            }
            Text(
                "Every Kaalam event also says “⏳ Created by Kaalam app” in its description and carries a hidden Kaalam tag, so syncing again never makes duplicates.",
                fontSize = 11.sp, color = K.Muted, modifier = Modifier.padding(top = 8.dp, start = 4.dp),
            )
            Spacer(Modifier.height(14.dp))
            PrimaryButton("⟳  Sync now · $count events", Modifier.fillMaxWidth(), enabled = connected, loading = busy) { syncNow() }
            Text(
                "Last synced ${timeAgo(maxOf(last, Prefs.lastCalendarSync))}",
                fontSize = 11.sp, color = K.Muted, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), textAlign = TextAlign.Center,
            )
            ErrorText(err)
            Spacer(Modifier.height(30.dp))
        }
    }
}

// ============================ GOOGLE DRIVE ============================

@Composable
fun DriveScreen(nav: Nav, data: AppData) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val busy by DriveSync.busy.collectAsState()
    val err by DriveSync.error.collectAsState()
    val last by DriveSync.lastBackup.collectAsState()
    var connected by remember { mutableStateOf(GoogleAuth.isConnected(ctx)) }
    var autoSave by remember { mutableStateOf(Prefs.autoSave) }
    var daily by remember { mutableStateOf(Prefs.dailyBackup) }
    var wifi by remember { mutableStateOf(Prefs.wifiOnly) }
    var backups by remember { mutableStateOf<List<DriveSync.Remote>?>(null) }
    var loadingList by remember { mutableStateOf(false) }
    var chosen by remember { mutableStateOf<DriveSync.Remote?>(null) }
    var offerRestore by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }
    val lastAt = maxOf(last, Prefs.lastBackupAt)

    fun backupNow() = scope.launch {
        try {
            DriveSync.uploadNow(ctx)
            Toast.makeText(ctx, "Backed up to Google Drive", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) { }
    }

    fun openRestore() = scope.launch {
        loadingList = true
        info = null
        try {
            backups = DriveSync.listBackups(ctx)
            if (backups.isNullOrEmpty()) { info = "No Kaalam backup found in this Google Drive yet."; backups = null }
        } catch (e: Exception) {
            info = Api.friendly(e)
        }
        loadingList = false
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("Google Drive data", onBack = { nav.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            GoogleAccountCard { _ ->
                connected = true
                scope.launch {
                    // New phone? If Drive already has a backup and this phone is empty, offer to restore it.
                    val has = runCatching { DriveSync.hasBackup(ctx) }.getOrDefault(false)
                    if (has && Repo.data.value.people.isEmpty()) offerRestore = true
                    else runCatching { DriveSync.uploadNow(ctx) }
                }
            }
            if (connected) {
                Spacer(Modifier.height(12.dp))
                KCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (err == null) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff, null, tint = if (err == null) K.Green else K.Red)
                        Text(
                            if (busy) "  Backing up…" else "  Backed up ${timeAgo(lastAt)}",
                            fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                        )
                    }
                    ErrorText(err)
                }

                SectionTitle("Storage location")
                KCard(Modifier.fillMaxWidth()) {
                    Text("📁  My Drive / ${DriveSync.FOLDER} /", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(
                        "    📄 ${DriveSync.FILE}  — latest copy\n    🗂️ ${DriveSync.BACKUPS}/  — one copy per day, last 7 kept",
                        fontSize = 12.sp, color = K.Muted, lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        "🔐 Encrypted with your Kaalam password — unreadable if opened directly in Drive. Remember the password: you need it to restore on a new phone.",
                        fontSize = 11.sp, color = Color(0xFF1E7A47),
                        modifier = Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(K.GreenBg).padding(8.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                KCard(padding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)) {
                    ToggleRow("Auto-save to Drive", "After every change", autoSave) { autoSave = it; Prefs.autoSave = it }
                    ThinDivider()
                    ToggleRow("Daily backup copy", "Keeps the last 7 days", daily) { daily = it; Prefs.dailyBackup = it }
                    ThinDivider()
                    ToggleRow("Wi-Fi only", "Automatic backups wait for Wi-Fi", wifi) { wifi = it; Prefs.wifiOnly = it }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton("☁️ Back up now", Modifier.weight(1f), loading = busy) { backupNow() }
                    SoftButton(if (loadingList) "Loading…" else "⟲ Restore", Modifier.weight(1f), enabled = !loadingList) { openRestore() }
                }
                ErrorText(info)
                Text(
                    "New phone? Install Kaalam → set it up → Admin › Google Drive data → connect → Restore.",
                    fontSize = 11.sp, color = K.Muted, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = { GoogleAuth.signOut(ctx) { connected = false } }, modifier = Modifier.fillMaxWidth()) {
                    Text("Disconnect Google account", color = K.Red)
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }

    if (offerRestore) AlertDialog(
        onDismissRequest = { offerRestore = false },
        title = { Text("Backup found in Drive") },
        text = { Text("This Google account already has Kaalam data. Restore it to this phone?") },
        confirmButton = { TextButton(onClick = { offerRestore = false; openRestore() }) { Text("Choose backup") } },
        dismissButton = { TextButton(onClick = { offerRestore = false }) { Text("Not now") } },
    )

    backups?.let { list ->
        AlertDialog(
            onDismissRequest = { backups = null },
            title = { Text("Restore which copy?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    list.forEach { r ->
                        val label = if (r.name == DriveSync.FILE) "Latest copy" else r.name.removePrefix("kaalam_").removeSuffix(".enc").let {
                            runCatching { LocalDate.parse(it).format(Texts.eeeeDMMMMyyyy) }.getOrDefault(it)
                        }
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { chosen = r; backups = null }.padding(10.dp),
                        ) {
                            Text(label, fontWeight = FontWeight.SemiBold)
                            Text(r.modified.take(16).replace("T", " ") + " UTC", fontSize = 11.sp, color = K.Muted)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { backups = null }) { Text("Cancel") } },
        )
    }

    chosen?.let { r ->
        RestoreDialog(r, onDismiss = { chosen = null }) { restoredPeople ->
            chosen = null
            Toast.makeText(ctx, "Restored $restoredPeople people", Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
private fun RestoreDialog(r: DriveSync.Remote, onDismiss: () -> Unit, onDone: (Int) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text("Enter backup password") },
        text = {
            Column {
                Text("Type the Kaalam PIN that was in use when this backup was made. The data on this phone will be replaced, and this PIN becomes your app password here.", fontSize = 13.sp, color = K.Muted)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    pin, { v -> pin = v.filter { it.isDigit() }.take(6) }, singleLine = true, label = { Text("6-digit PIN") },
                    visualTransformation = PasswordVisualTransformation(), colors = kFieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                ErrorText(error)
                if (working) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 10.dp))
            }
        },
        confirmButton = {
            TextButton(enabled = pin.length == 6 && !working, onClick = {
                working = true; error = null
                scope.launch {
                    try {
                        val text = DriveSync.download(ctx, r.id)
                        val json = withContext(Dispatchers.Default) {
                            try { Vault.decryptFromDrive(text, pin) } catch (e: javax.crypto.AEADBadTagException) { throw Exception("Wrong password for this backup") }
                        }
                        val restored = Store.fromJson(json)
                        withContext(Dispatchers.Default) { Vault.setPin(pin) } // this phone now uses the backup's password
                        Repo.replaceAll(restored.copy(people = restored.people.map { p -> p.copy(events = p.events.map { it.copy(calendarSynced = false) }) }))
                        runCatching { DriveSync.uploadNow(ctx) }
                        CalendarSync.requestAutoSync(ctx)
                        onDone(restored.people.size)
                    } catch (e: Exception) {
                        error = if (e.message?.contains("Wrong password") == true || e is javax.crypto.AEADBadTagException) "Wrong password for this backup" else Api.friendly(e)
                    }
                    working = false
                }
            }) { Text("Restore") }
        },
        dismissButton = { TextButton(enabled = !working, onClick = onDismiss) { Text("Cancel") } },
    )
}
