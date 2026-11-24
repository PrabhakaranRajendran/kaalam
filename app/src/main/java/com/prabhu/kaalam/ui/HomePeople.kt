package com.prabhu.kaalam.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prabhu.kaalam.data.AppData
import com.prabhu.kaalam.data.DateMath
import com.prabhu.kaalam.data.EventType
import com.prabhu.kaalam.data.Occurrence
import com.prabhu.kaalam.data.Repo
import com.prabhu.kaalam.data.Texts
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.Locale

private val indian: NumberFormat = NumberFormat.getIntegerInstance(Locale("en", "IN"))

@Composable
fun AddFab(modifier: Modifier = Modifier, onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick, modifier = modifier, containerColor = K.Teal, contentColor = Color.White,
        shape = RoundedCornerShape(18.dp),
    ) { Icon(Icons.Rounded.Add, "Add event", modifier = Modifier.size(28.dp)) }
}

// ============================ HOME ============================

@Composable
fun HomeScreen(nav: Nav, data: AppData) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = LocalDateTime.now() } }
    val today = now.toLocalDate()
    val myDob = data.profile.dob
    var calcDob by remember(myDob) { mutableStateOf(myDob ?: LocalDate.of(1990, 1, 1)) }
    var picking by remember { mutableStateOf(false) }
    val isMine = myDob != null && calcDob == myDob
    val first = data.profile.name.trim().split(" ").firstOrNull().orEmpty().ifBlank { "there" }
    val greeting = when (now.hour) { in 0..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            GradientHeader(bottomPadding = 76.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(greeting, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
                        Text("Hi, $first 👋", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    }
                    SquareIconButton(Icons.Rounded.Notifications, light = true, contentDescription = "Upcoming") { nav.tab(Route.Calendar) }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(K.Gold).clickable { nav.tab(Route.Admin) },
                        contentAlignment = Alignment.Center,
                    ) { Text(first.first().uppercase(), color = K.TealDark, fontWeight = FontWeight.Bold) }
                }
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.14f))
                        .clickable { picking = true }.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (isMine) "Date of birth" else "Calculating for", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(calcDob.format(Texts.dMMMyyyy), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("📅 Change", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                }
            }

            Column(Modifier.padding(horizontal = 16.dp).offset(y = (-60).dp)) {
                AgeCard(calcDob, today, now, isMine)
                if (!isMine && myDob != null) {
                    TextButton(onClick = { calcDob = myDob }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("↺ Back to my date of birth", color = K.Teal, fontWeight = FontWeight.SemiBold)
                    }
                } else Spacer(Modifier.height(12.dp))
                BirthdayCountdown(calcDob, today, now, isMine)

                val upcoming = remember(data, today) { Repo.upcoming(data, today).take(5) }
                SectionTitle("Coming up", action = if (upcoming.isNotEmpty()) "See all ›" else null) { nav.tab(Route.People) }
                if (upcoming.isEmpty()) {
                    KCard { EmptyState("🎉", "Nothing added yet", "Tap + to add a birthday, anniversary or your own event.") }
                } else KCard(padding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
                    upcoming.forEachIndexed { i, o ->
                        if (i > 0) ThinDivider()
                        OccurrenceRow(o, dateLine = o.date?.format(Texts.eeeDMMM), headline = true) { nav.go(Route.PersonDetail(o.person.id)) }
                    }
                }
                Spacer(Modifier.height(90.dp))
            }
        }
        AddFab(Modifier.align(Alignment.BottomEnd).padding(18.dp)) { nav.go(Route.EditEvent(null, null)) }
    }

    if (picking) KDatePickerDialog(calcDob, "Calculate age for", onDismiss = { picking = false }) { calcDob = it }
}

@Composable
private fun AgeCard(dob: LocalDate, today: LocalDate, now: LocalDateTime, isMine: Boolean) {
    val future = dob.isAfter(today)
    val p = if (future) Period.ZERO else Period.between(dob, today)
    val days = if (future) 0 else ChronoUnit.DAYS.between(dob, today)
    val hours = if (future) 0 else days * 24 + now.hour
    KCard(Modifier.fillMaxWidth()) {
        Text(
            "${if (isMine) "Your age" else "Age"} today · ${today.format(Texts.dMMMyyyy)}",
            fontSize = 12.sp, color = K.Muted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
        )
        Row(Modifier.fillMaxWidth().padding(top = 6.dp).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.SpaceEvenly) {
            AgeNumber(p.years, "Years")
            VerticalDivider(color = K.Line)
            AgeNumber(p.months, "Months")
            VerticalDivider(color = K.Line)
            AgeNumber(p.days, "Days")
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(10.dp)).background(K.Bg).padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MiniStat(indian.format(days), "days")
            MiniStat(indian.format(days / 7), "weeks")
            MiniStat(indian.format(hours), "hours")
        }
    }
}

@Composable
private fun AgeNumber(n: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 8.dp)) {
        Text(n.toString().padStart(2, '0'), fontSize = 44.sp, fontWeight = FontWeight.Bold, color = K.Teal, lineHeight = 48.sp)
        Text(label, fontSize = 12.sp, color = K.Muted)
    }
}

@Composable
private fun MiniStat(value: String, label: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, fontWeight = FontWeight.Bold, color = K.TealDark, fontSize = 12.sp)
        Text(" $label", color = K.Muted, fontSize = 11.sp)
    }
}

@Composable
private fun BirthdayCountdown(dob: LocalDate, today: LocalDate, now: LocalDateTime, isMine: Boolean) {
    val next = DateMath.nextBirthday(dob, today)
    val turning = next.year - dob.year
    KCard(Modifier.fillMaxWidth(), color = Color(0xFFF6FBFB)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🎂 Next birthday", fontWeight = FontWeight.Bold, color = K.TealDark, fontSize = 14.sp, modifier = Modifier.weight(1f))
            if (turning > 0) Pill("Turning $turning", K.CoralBg, K.CoralInk)
        }
        if (next == today) {
            Text(
                if (isMine) "🎉 Happy birthday! Have a wonderful day." else "🎉 It's the birthday today!",
                fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = K.CoralInk,
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp), textAlign = TextAlign.Center,
            )
        } else {
            val d = Duration.between(now, next.atStartOfDay())
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                CountUnit(d.toDays(), "Days")
                CountUnit(d.toHours() % 24, "Hours")
                CountUnit(d.toMinutes() % 60, "Minutes")
                CountUnit(d.seconds % 60, "Seconds")
            }
        }
        Text(next.format(Texts.eeeeDMMMMyyyy), fontSize = 12.sp, color = K.Muted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}

@Composable
private fun CountUnit(v: Long, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(v.toString().padStart(2, '0'), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = K.TealDark)
        Text(label, fontSize = 11.sp, color = K.Muted)
    }
}

/** One row: avatar, name + subtitle, days badge. */
@Composable
fun OccurrenceRow(o: Occurrence, dateLine: String? = null, headline: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        PersonAvatar(o.person, if (headline) 38.dp else 42.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                if (headline) Texts.headline(o) else o.person.name,
                fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(dateLine ?: Texts.subtitle(o), fontSize = 12.sp, color = K.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DaysBadge(o)
    }
}

@Composable
fun DaysBadge(o: Occurrence) {
    val t = o.event.type
    val (bg, fg) = when {
        o.daysLeft == null -> Color(0xFFEEF2F3) to K.Muted
        o.daysLeft <= 31 -> t.bg() to t.ink()
        else -> K.TealLight to K.Teal
    }
    Pill(if (o.daysLeft != null && o.daysLeft <= 31) Texts.daysLong(o.daysLeft) else Texts.daysBadge(o.daysLeft), bg, fg)
}

// ============================ PEOPLE ============================

@Composable
fun PeopleScreen(nav: Nav, data: AppData) {
    val today = LocalDate.now()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("all") }
    var az by rememberSaveable { mutableStateOf(false) }
    val all = remember(data, today) { Repo.occurrences(data, today) }
    val counts = remember(all) { all.groupingBy { it.event.type }.eachCount() }

    val shown = all.filter { o ->
        (filter == "all" || o.event.type.key == filter) &&
            (query.isBlank() || listOf(o.person.name, o.person.relation, Texts.eventLabel(o.event))
                .any { it.contains(query.trim(), ignoreCase = true) })
    }
    val sections: List<Pair<String, List<Occurrence>>> = if (az) {
        shown.sortedBy { it.person.name.lowercase() }.groupBy { it.person.name.trim().firstOrNull()?.uppercase() ?: "#" }
            .toList()
    } else {
        val thisMonth = YearMonth.from(today)
        val upcoming = shown.filter { it.date != null }.groupBy { YearMonth.from(it.date!!) }.toSortedMap().map { (ym, list) ->
            val label = when {
                ym == thisMonth -> "This month · ${ym.atDay(1).format(Texts.month)}"
                ym.year == today.year -> ym.atDay(1).format(Texts.month)
                else -> ym.atDay(1).format(Texts.monthYear)
            }
            label to list
        }
        val past = shown.filter { it.date == null }
        if (past.isEmpty()) upcoming else upcoming + ("Past one-time events" to past)
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopBar("People", onBack = null) {
                SquareIconButton(if (az) Icons.Rounded.Today else Icons.Rounded.SortByAlpha, contentDescription = "Sort") { az = !az }
            }
            Column(Modifier.padding(horizontal = 16.dp)) {
                OutlinedTextField(
                    query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search name, relation or event", fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Rounded.Search, null, tint = K.Faint) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear") } },
                    shape = RoundedCornerShape(14.dp), colors = kFieldColors(),
                )
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KChip("All ${all.size}", filter == "all") { filter = "all" }
                    KChip("🎂 ${counts[EventType.BIRTHDAY] ?: 0}", filter == "birthday", K.Coral) { filter = "birthday" }
                    KChip("💍 ${counts[EventType.ANNIVERSARY] ?: 0}", filter == "anniversary", K.Purple) { filter = "anniversary" }
                    KChip("⭐ ${counts[EventType.CUSTOM] ?: 0}", filter == "custom", K.Amber) { filter = "custom" }
                }
            }
            if (sections.isEmpty()) {
                EmptyState(
                    "👥",
                    if (all.isEmpty()) "No one added yet" else "No matches",
                    if (all.isEmpty()) "Tap + to add a birthday, anniversary or your own event — or import birthdays from your contacts in Admin." else "Try a different search or filter.",
                    Modifier.padding(top = 40.dp),
                )
            } else LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp)) {
                sections.forEach { (title, list) ->
                    item(key = "h-$title") { SectionTitle(title) }
                    item(key = "c-$title") {
                        KCard(padding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)) {
                            list.forEachIndexed { i, o ->
                                if (i > 0) ThinDivider()
                                OccurrenceRow(o) { nav.go(Route.PersonDetail(o.person.id)) }
                            }
                        }
                    }
                }
            }
        }
        AddFab(Modifier.align(Alignment.BottomEnd).padding(18.dp)) { nav.go(Route.EditEvent(null, null)) }
    }
}
