package com.prabhu.kaalam.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prabhu.kaalam.MainActivity
import com.prabhu.kaalam.data.AppData
import com.prabhu.kaalam.data.DateMath
import com.prabhu.kaalam.data.Event
import com.prabhu.kaalam.data.EventType
import com.prabhu.kaalam.data.Person
import com.prabhu.kaalam.data.Repo
import com.prabhu.kaalam.data.Texts
import java.time.LocalDate
import java.time.Period

val RELATIONS = listOf(
    "Mother", "Father", "Wife", "Husband", "Son", "Daughter", "Brother", "Sister",
    "Grandmother", "Grandfather", "Uncle", "Aunt", "Cousin", "Nephew", "Niece",
    "Friend", "Colleague", "Relative", "Neighbour", "Customer", "Other",
)

// ============================ PERSON DETAIL ============================

@Composable
fun PersonScreen(nav: Nav, data: AppData, personId: String) {
    val ctx = LocalContext.current
    val person = data.people.firstOrNull { it.id == personId }
    if (person == null) {
        LaunchedEffect(Unit) { nav.back() }
        return
    }
    val today = LocalDate.now()
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var photoMenu by remember { mutableStateOf(false) }
    val pickPhoto = rememberPhotoPicker { savePhoto(ctx, person.id, it) }
    val occ = person.events.map { DateMath.occurrence(person, it, today) }
        .sortedWith(compareBy({ it.daysLeft == null }, { it.daysLeft ?: Long.MAX_VALUE }))
    val nextOcc = occ.firstOrNull { it.date != null }
    val birthday = person.events.firstOrNull { it.type == EventType.BIRTHDAY && it.yearKnown }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        GradientHeader(bottomPadding = 64.dp) {
            Row {
                SquareIconButton(Icons.AutoMirrored.Rounded.ArrowBack, light = true, contentDescription = "Back") { nav.back() }
                Spacer(Modifier.weight(1f))
                SquareIconButton(Icons.Rounded.Edit, light = true, contentDescription = "Edit") { editing = true }
                Spacer(Modifier.width(8.dp))
                SquareIconButton(Icons.Rounded.DeleteOutline, light = true, contentDescription = "Delete") { confirmDelete = true }
            }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.border(3.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(30.dp)).padding(3.dp).clickable { photoMenu = true }) {
                    PersonAvatar(person, size = 92.dp, corner = 27.dp, fontSize = 36)
                    DropdownMenu(expanded = photoMenu, onDismissRequest = { photoMenu = false }) {
                        DropdownMenuItem(text = { Text(if (person.photo > 0) "Change photo" else "Add photo") }, onClick = { photoMenu = false; pickPhoto() })
                        if (person.photo > 0) DropdownMenuItem(text = { Text("Remove photo") }, onClick = {
                            photoMenu = false; com.prabhu.kaalam.wish.PhotoStore.delete(ctx, person.id); Repo.setPhoto(person.id, 0)
                        })
                    }
                }
                if (person.photo <= 0) Text("Tap to add photo", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                Text(person.name, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                val sub = listOfNotNull(person.relation.ifBlank { null }, person.phone.ifBlank { null }?.let { "📞 $it" }).joinToString(" · ")
                if (sub.isNotBlank()) Text(sub, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
            }
        }
        Column(Modifier.padding(horizontal = 16.dp).offset(y = (-48).dp)) {
            KCard(Modifier.fillMaxWidth()) {
                if (birthday != null && !birthday.date.isAfter(today)) {
                    val p = Period.between(birthday.date, today)
                    Text("Current age", fontSize = 12.sp, color = K.Muted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    Text(
                        "${p.years} yrs  ${p.months} mon  ${p.days} days",
                        fontSize = 22.sp, fontWeight = FontWeight.Bold, color = K.Teal,
                        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                if (nextOcc != null) {
                    val t = nextOcc.event.type
                    val count = Texts.countText(nextOcc)
                    Text(
                        "${t.emoji} ${Texts.eventLabel(nextOcc.event)}${count?.let { " · $it" } ?: ""} · " +
                            "${Texts.daysLong(nextOcc.daysLeft).replaceFirstChar { it.lowercase() }.let { if (nextOcc.daysLeft!! > 1) "in $it" else it }} · " +
                            nextOcc.date!!.format(Texts.eeeDMMM),
                        color = t.ink(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(t.bg()).padding(10.dp),
                    )
                } else {
                    Text("No upcoming events", color = K.Muted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
            }

            SectionTitle("Events (${person.events.size})")
            KCard(padding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)) {
                occ.forEachIndexed { i, o ->
                    if (i > 0) ThinDivider()
                    val ev = o.event
                    Row(
                        Modifier.fillMaxWidth().clickable { nav.go(Route.EditEvent(person.id, ev.id)) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(ev.type.emoji, fontSize = 24.sp)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(Texts.eventLabel(ev), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("${Texts.dateText(ev)} · ${Texts.repeatText(ev)}", fontSize = 12.sp, color = K.Muted)
                            Text("🔔 ${Texts.remindText(ev.remindDays)}", fontSize = 11.sp, color = K.Muted)
                        }
                        when {
                            o.date == null -> Pill("Past", Color(0xFFEEF2F3), K.Muted)
                            !ev.syncCalendar -> Pill("App only", Color(0xFFEEF2F3), K.Muted)
                            ev.calendarSynced -> Pill("📅 Synced", K.TealLight, K.Teal)
                            else -> Pill("Not synced", K.AmberBg, K.AmberInk)
                        }
                    }
                }
            }
            if (person.notes.isNotBlank()) {
                SectionTitle("Notes")
                KCard(Modifier.fillMaxWidth()) { Text(person.notes, fontSize = 13.sp) }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val wishEvent = nextOcc?.event ?: person.events.firstOrNull()
                PrimaryButton("💬 Send wish", Modifier.weight(1f), enabled = wishEvent != null, color = K.WhatsApp) {
                    if (wishEvent != null) nav.go(Route.Wish(person.id, wishEvent.id))
                }
                SoftButton("+ Add event", Modifier.weight(1f)) { nav.go(Route.EditEvent(person.id, null)) }
            }
            Spacer(Modifier.height(40.dp))
        }
    }

    if (editing) PersonDetailsDialog(person, onDismiss = { editing = false }) { name, rel, phone, notes ->
        Repo.editPersonDetails(person.id, name, rel, phone, notes)
        editing = false
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete ${person.name}?") },
        text = { Text("All ${person.events.size} event(s) for this person are removed from Kaalam and from the Kaalam calendar.") },
        confirmButton = {
            TextButton(onClick = { confirmDelete = false; Repo.deletePerson(person.id); nav.back() }) { Text("Delete", color = K.Red) }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}

@Composable
private fun PersonDetailsDialog(p: Person, onDismiss: () -> Unit, onSave: (String, String, String, String) -> Unit) {
    var name by remember { mutableStateOf(p.name) }
    var rel by remember { mutableStateOf(p.relation) }
    var phone by remember { mutableStateOf(p.phone) }
    var notes by remember { mutableStateOf(p.notes) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, colors = kFieldColors(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                RelationField(rel) { rel = it }
                OutlinedTextField(phone, { phone = it }, label = { Text("Phone (for WhatsApp wishes)") }, singleLine = true, colors = kFieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, colors = kFieldColors(), minLines = 2)
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), rel.trim(), phone.trim(), notes.trim()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun RelationField(value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value, onChange, label = { Text("Relation") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp), colors = kFieldColors(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            trailingIcon = { IconButton(onClick = { open = true }) { Icon(Icons.Rounded.ArrowDropDown, "Choose") } },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            RELATIONS.forEach { r -> DropdownMenuItem(text = { Text(r) }, onClick = { onChange(r); open = false }) }
        }
    }
}

// ============================ ADD / EDIT EVENT ============================

@Composable
fun EditEventScreen(nav: Nav, data: AppData, personId: String?, eventId: String?) {
    val person = personId?.let { id -> data.people.firstOrNull { it.id == id } }
    val existing = eventId?.let { id -> person?.events?.firstOrNull { it.id == id } }
    val isNewPerson = personId == null

    var name by rememberSaveable { mutableStateOf("") }
    var relation by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(existing?.type?.key ?: EventType.BIRTHDAY.key) }
    var customName by rememberSaveable { mutableStateOf(existing?.customName ?: "") }
    var dateText by rememberSaveable { mutableStateOf(existing?.date?.toString() ?: "") }
    var yearKnown by rememberSaveable { mutableStateOf(existing?.yearKnown ?: true) }
    var yearly by rememberSaveable { mutableStateOf(existing?.yearly ?: true) }
    var remind by remember { mutableStateOf(existing?.remindDays?.toSet() ?: setOf(0, 1)) }
    var sync by rememberSaveable { mutableStateOf(existing?.syncCalendar ?: true) }
    var showCount by rememberSaveable { mutableStateOf(existing?.showCount ?: true) }
    var picking by remember { mutableStateOf(false) }
    var customDays by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var newPhoto by remember { mutableStateOf<Uri?>(null) }
    val ctx = LocalContext.current
    val pickNewPhoto = rememberPhotoPicker { newPhoto = it }
    val evType = EventType.of(type)
    val date = dateText.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) }

    fun save() {
        error = when {
            isNewPerson && name.isBlank() -> "Enter a name"
            evType == EventType.CUSTOM && customName.isBlank() -> "Enter the event name"
            date == null -> "Pick a date"
            else -> null
        }
        if (error != null || date == null) return
        val ev = (existing ?: Event(date = date)).copy(
            type = evType,
            customName = if (evType == EventType.CUSTOM) customName.trim() else "",
            date = if (yearKnown) date else DateMath.onYear(date, 2000),
            yearKnown = yearKnown,
            yearly = yearly || !yearKnown,
            remindDays = remind.sorted(),
            syncCalendar = sync,
            showCount = showCount,
        )
        if (evType == EventType.CUSTOM) Repo.addCustomType(customName)
        if (isNewPerson) {
            val p = Person(name = name.trim(), relation = relation.trim(), phone = phone.trim(), color = data.people.size % K.avatarColors.size, events = listOf(ev))
            Repo.upsertPerson(p)
            newPhoto?.let { savePhoto(ctx, p.id, it) }
            nav.replace(Route.PersonDetail(p.id))
        } else {
            Repo.upsertEvent(personId!!, ev)
            nav.back()
        }
    }

    Column(Modifier.fillMaxSize().background(K.Bg).imePadding()) {
        TopBar(if (existing != null) "Edit event" else "Add event", onBack = { nav.back() }) {
            TextButton(onClick = { save() }) { Text("Save", color = K.Teal, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            if (isNewPerson) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(58.dp).clip(RoundedCornerShape(18.dp)).background(K.TealLight)
                            .border(1.5.dp, K.Teal, RoundedCornerShape(18.dp)).clickable { pickNewPhoto() },
                        contentAlignment = Alignment.Center,
                    ) { Text(if (newPhoto != null) "✅" else "📷", fontSize = 22.sp) }
                    Spacer(Modifier.width(10.dp))
                    OutlinedTextField(
                        name, { name = it }, label = { Text(if (evType == EventType.ANNIVERSARY) "Name (e.g. Ravi & Meena)" else "Name") },
                        singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = kFieldColors(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    )
                }
                Spacer(Modifier.height(8.dp))
                RelationField(relation, Modifier.fillMaxWidth()) { relation = it }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    phone, { phone = it }, label = { Text("Phone (optional, for WhatsApp wishes)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = kFieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
            } else if (person != null) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PersonAvatar(person, 36.dp)
                    Text(person.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp))
                }
            }

            FieldLabel("Event type")
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, K.Line, RoundedCornerShape(12.dp)).padding(3.dp),
            ) {
                EventType.entries.forEach { t ->
                    val sel = t == evType
                    Text(
                        "${t.emoji} ${if (t == EventType.ANNIVERSARY) "Anniv." else t.label}",
                        textAlign = TextAlign.Center, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        color = if (sel) Color.White else K.Muted,
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(if (sel) t.color() else Color.Transparent)
                            .clickable { type = t.key }.padding(vertical = 9.dp),
                    )
                }
            }

            if (evType == EventType.CUSTOM) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    customName, { customName = it }, label = { Text("Event name (e.g. Graduation day)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = K.Amber, unfocusedBorderColor = K.Amber, focusedContainerColor = Color.White, unfocusedContainerColor = Color.White),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                if (data.customTypes.isNotEmpty()) {
                    FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        data.customTypes.forEach { c -> KChip(c, customName.equals(c, true), K.Amber) { customName = c } }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PickerField(
                    "Date",
                    date?.let { if (yearKnown) it.format(Texts.dMMMyyyy) else it.format(Texts.dMMM) } ?: "Tap to choose",
                    Modifier.weight(1f),
                ) { picking = true }
                PickerField("Repeat", if (yearly || !yearKnown) "Yearly" else "One-time", Modifier.width(120.dp)) {
                    if (yearKnown) yearly = !yearly
                }
            }
            ToggleRow("I know the year", if (yearKnown) "Age / year count is shown" else "Only day & month — repeats yearly", yearKnown) { yearKnown = it }

            FieldLabel("Remind me")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val opts = (listOf(0, 1, 7) + remind).distinct().sorted()
                opts.forEach { d ->
                    KChip(Texts.remindLabel(d), d in remind) { remind = if (d in remind) remind - d else remind + d }
                }
                KChip("+ Custom", false) { customDays = true }
            }

            Spacer(Modifier.height(10.dp))
            KCard(padding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)) {
                ToggleRow("Sync to Google Calendar", "Adds it to your separate “Kaalam” calendar", sync) { sync = it }
                ThinDivider()
                ToggleRow(
                    "Show age / year count",
                    when (evType) { EventType.BIRTHDAY -> "e.g. “turns 64”"; EventType.ANNIVERSARY -> "e.g. “8th anniversary”"; else -> "e.g. “2nd Graduation day”" },
                    showCount && yearKnown, enabled = yearKnown,
                ) { showCount = it }
            }
            ErrorText(error)
            Spacer(Modifier.height(14.dp))
            PrimaryButton("Save event", Modifier.fillMaxWidth()) { save() }
            if (existing != null && personId != null) {
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Text("Delete this event", color = K.Red) }
            }
            Spacer(Modifier.height(30.dp))
        }
    }

    if (picking) KDatePickerDialog(date ?: LocalDate.now(), onDismiss = { picking = false }) { dateText = it.toString() }
    if (customDays) {
        var t by remember { mutableStateOf("3") }
        AlertDialog(
            onDismissRequest = { customDays = false },
            title = { Text("Remind how many days before?") },
            text = {
                OutlinedTextField(t, { v -> t = v.filter { it.isDigit() }.take(2) }, singleLine = true, colors = kFieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), suffix = { Text("days") })
            },
            confirmButton = {
                TextButton(onClick = { t.toIntOrNull()?.let { if (it in 0..60) remind = remind + it }; customDays = false }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { customDays = false }) { Text("Cancel") } },
        )
    }
    if (confirmDelete && personId != null && existing != null) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete this event?") },
        text = { Text("It is also removed from the Kaalam calendar.") },
        confirmButton = {
            TextButton(onClick = {
                confirmDelete = false
                Repo.deleteEvent(personId, existing.id)
                nav.back()
            }) { Text("Delete", color = K.Red) }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}
