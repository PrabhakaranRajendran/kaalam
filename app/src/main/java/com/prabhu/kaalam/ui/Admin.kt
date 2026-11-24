package com.prabhu.kaalam.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.prabhu.kaalam.BuildConfig
import com.prabhu.kaalam.MainActivity
import com.prabhu.kaalam.data.AppData
import com.prabhu.kaalam.data.ContactEvent
import com.prabhu.kaalam.data.ContactsImport
import com.prabhu.kaalam.data.CsvExport
import com.prabhu.kaalam.data.Repo
import com.prabhu.kaalam.data.Texts
import com.prabhu.kaalam.google.GoogleAuth
import com.prabhu.kaalam.security.Bio
import com.prabhu.kaalam.security.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

// ============================ ADMIN DASHBOARD ============================

@Composable
fun AdminScreen(nav: Nav, data: AppData, activity: MainActivity) {
    val ctx = LocalContext.current
    val today = LocalDate.now()
    val events = data.people.flatMap { it.events }
    val syncable = events.filter { it.syncCalendar }
    val thisMonth = Repo.upcoming(data, today).count { YearMonth.from(it.date!!) == YearMonth.from(today) }
    val connected = GoogleAuth.isConnected(ctx)

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(CsvExport.build(Repo.data.value, LocalDate.now()).toByteArray(Charsets.UTF_8)) }
            }.onSuccess { Toast.makeText(ctx, "Exported — opens in Excel or Google Sheets", Toast.LENGTH_LONG).show() }
                .onFailure { Toast.makeText(ctx, "Export failed: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        GradientHeader(bottomPadding = 62.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("🛡️ Admin mode", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
                    Text("Control panel", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                SquareIconButton(Icons.Rounded.Lock, light = true, contentDescription = "Lock admin") {
                    AppState.adminUnlocked = false
                    nav.tab(Route.Home)
                }
            }
        }
        Column(Modifier.padding(horizontal = 16.dp).offset(y = (-48).dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("People", data.people.size.toString(), K.TealDark, Modifier.weight(1f))
                Stat("Events", events.size.toString(), K.TealDark, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("This month", thisMonth.toString(), K.Coral, Modifier.weight(1f))
                Stat(
                    "Calendar synced", "${syncable.count { it.calendarSynced }}", K.Green, Modifier.weight(1f),
                    suffix = "/${syncable.size}",
                )
            }

            SectionTitle("Manage")
            KCard(padding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)) {
                NavRow(Icons.Rounded.Person, Color(0xFFFFF4D6), "My profile", "${data.profile.name.ifBlank { "Your name" }} · ${data.profile.dob?.format(Texts.dMMMyyyy) ?: "date of birth"}") { nav.go(Route.Profile) }
                ThinDivider()
                NavRow(Icons.Rounded.People, K.TealLight, "People & events", "View, open or delete everyone") { nav.go(Route.ManagePeople) }
                ThinDivider()
                NavRow(Icons.Rounded.Star, K.AmberBg, "Custom event types", data.customTypes.take(3).joinToString(", ").ifBlank { "Your own event names" }, iconTint = K.AmberInk) { nav.go(Route.CustomTypes) }
                ThinDivider()
                NavRow(Icons.Rounded.Cloud, Color(0xFFE8F0FE), "Google Drive data", if (connected) "Backed up ${timeAgo(Prefs.lastBackupAt)}" else "Not connected", iconTint = K.Blue) { nav.go(Route.Drive) }
                ThinDivider()
                NavRow(Icons.Rounded.CalendarMonth, K.TealLight, "Calendar sync", if (connected) "Kaalam calendar · ${if (Prefs.autoSyncCalendar) "auto" else "manual"}" else "Not connected") { nav.go(Route.CalendarSettings) }
                ThinDivider()
                NavRow(
                    Icons.Rounded.Security, K.CoralBg, "Security",
                    "${if (Prefs.biometric && Bio.available(ctx)) "Fingerprint on" else "PIN only"} · auto-lock ${lockLabel(Prefs.lockAfterSec).lowercase()}",
                    iconTint = K.CoralInk,
                ) { nav.go(Route.Security) }
            }

            SectionTitle("Import / Export")
            KCard(padding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)) {
                NavRow(Icons.Rounded.Contacts, K.PurpleBg, "Import from contacts", "Birthdays & anniversaries saved in your phone", iconTint = K.PurpleInk) { nav.go(Route.ImportContacts) }
                ThinDivider()
                NavRow(Icons.Rounded.Download, K.GreenBg, "Export to Excel (CSV)", "Everyone with dates and days left", iconTint = K.Green) {
                    AppState.allowExternal()
                    exportLauncher.launch("Kaalam_${LocalDate.now()}.csv")
                }
            }
            Text(
                "Kaalam v${BuildConfig.VERSION_NAME}",
                fontSize = 11.sp, color = K.Faint, modifier = Modifier.fillMaxWidth().padding(top = 18.dp), textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color, modifier: Modifier, suffix: String = "") {
    KCard(modifier, padding = PaddingValues(12.dp)) {
        Text(label, fontSize = 12.sp, color = K.Muted)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = color)
            if (suffix.isNotEmpty()) Text(suffix, fontSize = 13.sp, color = K.Muted, modifier = Modifier.padding(bottom = 5.dp))
        }
    }
}

fun lockLabel(sec: Int): String = when (sec) {
    0 -> "Immediately"
    30 -> "30 seconds"
    60 -> "1 minute"
    300 -> "5 minutes"
    else -> "$sec seconds"
}

// ============================ SECURITY ============================

@Composable
fun SecurityScreen(nav: Nav, activity: MainActivity) {
    val ctx = LocalContext.current
    val bioAvailable = Bio.available(ctx)
    var bio by remember { mutableStateOf(Prefs.biometric) }
    var lockAfter by remember { mutableIntStateOf(Prefs.lockAfterSec) }
    var lockAdmin by remember { mutableStateOf(Prefs.lockAdmin) }
    var hide by remember { mutableStateOf(Prefs.hidePreview) }
    var shots by remember { mutableStateOf(Prefs.blockScreenshots) }
    var menu by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf(if (GoogleAuth.isConnected(ctx)) GoogleAuth.email(ctx) else null) }

    Column(Modifier.fillMaxSize()) {
        TopBar("Security", onBack = { nav.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Row(
                Modifier.fillMaxWidth().clip(CardShape).background(HeaderBrush).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🛡️", fontSize = 28.sp)
                Column(Modifier.padding(start = 12.dp)) {
                    Text("Your data is protected", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(
                        listOfNotNull(if (bio && bioAvailable) "Fingerprint" else null, "PIN", if (email != null) "encrypted Drive file" else null).joinToString(" + "),
                        color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp,
                    )
                }
            }
            SectionTitle("Unlock")
            KCard(padding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)) {
                ToggleRow(
                    "👆 Fingerprint unlock",
                    if (bioAvailable) "Use the phone's fingerprint sensor" else "Add a fingerprint in Android settings first",
                    bio && bioAvailable, enabled = bioAvailable,
                ) { bio = it; Prefs.biometric = it }
                ThinDivider()
                NavRow(Icons.Rounded.Key, K.TealLight, "Change app password", "Last changed ${timeAgo(Prefs.pinChangedAt)}") { nav.go(Route.ChangePin) }
                ThinDivider()
                ValueRow("Password type", "Numbers only", "6-digit PIN", K.Muted) { }
            }
            SectionTitle("Auto-lock")
            KCard(padding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)) {
                Box {
                    ValueRow("Lock after", "When you leave the app", "${lockLabel(lockAfter)} ▾") { menu = true }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        listOf(0, 30, 60, 300).forEach { s ->
                            DropdownMenuItem(text = { Text(lockLabel(s)) }, onClick = { lockAfter = s; Prefs.lockAfterSec = s; menu = false })
                        }
                    }
                }
                ThinDivider()
                ToggleRow("Lock Admin separately", "Ask again before opening Admin", lockAdmin) { lockAdmin = it; Prefs.lockAdmin = it }
                ThinDivider()
                ToggleRow("Hide app preview", "Blank screen in recent apps", hide) { hide = it; Prefs.hidePreview = it; activity.applySecureFlags() }
                ThinDivider()
                ToggleRow("Block screenshots", null, shots) { shots = it; Prefs.blockScreenshots = it; activity.applySecureFlags() }
            }
            SectionTitle("Account")
            KCard(padding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)) {
                if (email != null) {
                    ValueRow("Signed in with Google", email, "Sign out", K.Red) { GoogleAuth.signOut(ctx) { email = null } }
                } else {
                    NavRow(Icons.Rounded.Cloud, Color(0xFFE8F0FE), "Google account", "Not connected — needed to reset a forgotten password", iconTint = K.Blue) { nav.go(Route.Drive) }
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

// ============================ PROFILE ============================

@Composable
fun ProfileScreen(nav: Nav, data: AppData) {
    var name by remember { mutableStateOf(data.profile.name) }
    var dob by remember { mutableStateOf(data.profile.dob) }
    var picking by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().imePadding()) {
        TopBar("My profile", onBack = { nav.back() })
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            OutlinedTextField(
                name, { name = it }, label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp), colors = kFieldColors(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
            Spacer(Modifier.height(10.dp))
            PickerField("Your date of birth", dob?.format(Texts.dMMMyyyy) ?: "Tap to choose", Modifier.fillMaxWidth()) { picking = true }
            Text("Used for the age calculator and countdown on the home screen.", fontSize = 12.sp, color = K.Muted, modifier = Modifier.padding(top = 8.dp))
        }
        PrimaryButton("Save", Modifier.fillMaxWidth().padding(16.dp), enabled = name.isNotBlank()) {
            Repo.setProfile(name, dob); nav.back()
        }
    }
    if (picking) KDatePickerDialog(dob ?: LocalDate.of(1990, 1, 1), "Date of birth", onDismiss = { picking = false }) { dob = it }
}

// ============================ MANAGE PEOPLE ============================

@Composable
fun ManagePeopleScreen(nav: Nav, data: AppData) {
    var toDelete by remember { mutableStateOf<com.prabhu.kaalam.data.Person?>(null) }
    val people = data.people.sortedBy { it.name.lowercase() }
    Column(Modifier.fillMaxSize()) {
        TopBar("People & events (${people.size})", onBack = { nav.back() })
        if (people.isEmpty()) EmptyState("👥", "No one yet", "Add people from the Home or People tab, or import from contacts.")
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(people, key = { it.id }) { p ->
                KCard(padding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), onClick = { nav.go(Route.PersonDetail(p.id)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PersonAvatar(p, 38.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(p.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                (listOf(p.relation).filter { it.isNotBlank() } + p.events.map { "${it.type.emoji} ${Texts.eventLabel(it)}" }).joinToString(" · "),
                                fontSize = 12.sp, color = K.Muted, maxLines = 1,
                            )
                        }
                        IconButton(onClick = { toDelete = p }) { Icon(Icons.Rounded.DeleteOutline, "Delete", tint = K.Red) }
                    }
                }
            }
        }
    }
    toDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete ${p.name}?") },
            text = { Text("Removes ${p.events.size} event(s) from Kaalam and from the Kaalam calendar.") },
            confirmButton = { TextButton(onClick = { Repo.deletePerson(p.id); toDelete = null }) { Text("Delete", color = K.Red) } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } },
        )
    }
}

// ============================ CUSTOM TYPES ============================

@Composable
fun CustomTypesScreen(nav: Nav, data: AppData) {
    var newName by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().imePadding()) {
        TopBar("Custom event types", onBack = { nav.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Text("These appear as quick picks when you add a ⭐ Custom event.", fontSize = 13.sp, color = K.Muted)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    newName, { newName = it }, label = { Text("New type (e.g. Housewarming)") }, singleLine = true,
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = kFieldColors(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                Spacer(Modifier.width(8.dp))
                PrimaryButton("Add", enabled = newName.isNotBlank()) { Repo.addCustomType(newName); newName = "" }
            }
            Spacer(Modifier.height(12.dp))
            if (data.customTypes.isNotEmpty()) KCard(padding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)) {
                data.customTypes.forEachIndexed { i, t ->
                    if (i > 0) ThinDivider()
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("⭐  $t", modifier = Modifier.weight(1f), fontSize = 14.sp)
                        IconButton(onClick = { Repo.removeCustomType(t) }) { Icon(Icons.Rounded.Close, "Remove", tint = K.Muted) }
                    }
                }
            }
        }
    }
}

// ============================ IMPORT CONTACTS ============================

@Composable
fun ImportContactsScreen(nav: Nav, data: AppData) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
    }
    var found by remember { mutableStateOf<List<ContactEvent>?>(null) }
    var selected by remember { mutableStateOf(setOf<Int>()) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    LaunchedEffect(granted) {
        if (granted && found == null) {
            val list = withContext(Dispatchers.IO) { runCatching { ContactsImport.read(ctx) }.getOrDefault(emptyList()) }
            found = list
            selected = list.indices.filter { !ContactsImport.alreadyExists(data, list[it]) }.toSet()
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("Import from contacts", onBack = { nav.back() })
        when {
            !granted -> Column(Modifier.padding(16.dp)) {
                KCard {
                    Text("Kaalam reads only the birthday and anniversary dates saved in your contacts. Nothing is uploaded except to your own Drive backup.", fontSize = 13.sp, color = K.Muted)
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton("Allow access to contacts", Modifier.fillMaxWidth()) { permission.launch(Manifest.permission.READ_CONTACTS) }
                }
            }
            found == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            found!!.isEmpty() -> EmptyState("📇", "No dates found", "None of your contacts have a birthday or anniversary saved.")
            else -> {
                val list = found!!
                Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${selected.size} of ${list.size} selected", fontSize = 13.sp, color = K.Muted, modifier = Modifier.weight(1f))
                    TextButton(onClick = { selected = if (selected.size == list.size) emptySet() else list.indices.toSet() }) {
                        Text(if (selected.size == list.size) "Select none" else "Select all")
                    }
                }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp)) {
                    items(list.size) { i ->
                        val c = list[i]
                        val exists = ContactsImport.alreadyExists(data, c)
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                                selected = if (i in selected) selected - i else selected + i
                            }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = i in selected, onCheckedChange = { selected = if (it) selected + i else selected - i })
                            Column(Modifier.weight(1f)) {
                                Text(c.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(
                                    "${c.type.emoji} ${c.type.label} · ${if (c.yearKnown) c.date.format(Texts.dMMMyyyy) else c.date.format(Texts.dMMM)}" +
                                        if (exists) " · already in Kaalam" else "",
                                    fontSize = 12.sp, color = K.Muted,
                                )
                            }
                        }
                    }
                }
                PrimaryButton("Import ${selected.size}", Modifier.fillMaxWidth().padding(16.dp), enabled = selected.isNotEmpty()) {
                    scope.launch {
                        ContactsImport.import(selected.sorted().map { list[it] })
                        Toast.makeText(ctx, "Imported ${selected.size}", Toast.LENGTH_SHORT).show()
                        nav.back()
                    }
                }
            }
        }
    }
}
