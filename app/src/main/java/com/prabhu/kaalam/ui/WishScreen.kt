package com.prabhu.kaalam.ui

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prabhu.kaalam.MainActivity
import com.prabhu.kaalam.data.AppData
import com.prabhu.kaalam.data.DateMath
import com.prabhu.kaalam.data.EventType
import com.prabhu.kaalam.data.Person
import com.prabhu.kaalam.data.Repo
import com.prabhu.kaalam.wish.PhotoStore
import com.prabhu.kaalam.wish.WishCard
import com.prabhu.kaalam.wish.Wishes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Opens the photo picker and saves the chosen picture for a person. */
@Composable
fun rememberPhotoPicker(onPicked: (Uri) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPicked(uri)
    }
    return {
        AppState.allowExternal()
        launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
}

/** Saves a picked photo for an existing person (off the main thread). */
fun savePhoto(ctx: android.content.Context, personId: String, uri: Uri) {
    Repo.scope.launch {
        val v = PhotoStore.save(ctx, personId, uri)
        withContext(Dispatchers.Main) {
            if (v > 0) Repo.setPhoto(personId, v)
            else Toast.makeText(ctx, "Couldn't read that picture", Toast.LENGTH_SHORT).show()
        }
    }
}

// ============================ WISH COMPOSER ============================

@Composable
fun WishScreen(nav: Nav, data: AppData, personId: String, eventId: String?) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val person = data.people.firstOrNull { it.id == personId }
    val event = person?.events?.firstOrNull { it.id == eventId } ?: person?.let { p ->
        val today = LocalDate.now()
        p.events.minByOrNull { DateMath.next(it, today)?.toEpochDay() ?: Long.MAX_VALUE }
    }
    if (person == null || event == null) {
        LaunchedEffect(Unit) { nav.back() }
        return
    }
    val today = LocalDate.now()
    val next = DateMath.next(event, today)
    val from = data.profile.name.trim().split(" ").firstOrNull().orEmpty()
    val templates = Wishes.templates(event.type)
    val saved = data.wishDefaults[event.type.key]

    var themeName by rememberSaveable { mutableStateOf(WishCard.defaultTheme(event.type).name) }
    val theme = WishCard.Theme.valueOf(themeName)
    var attach by rememberSaveable { mutableStateOf(true) }
    var message by rememberSaveable { mutableStateOf(Wishes.fill(saved ?: templates.first().text, person, event, next, from)) }
    var card by remember { mutableStateOf<Bitmap?>(null) }
    var sending by remember { mutableStateOf(false) }
    val pickPhoto = rememberPhotoPicker { savePhoto(ctx, person.id, it) }

    LaunchedEffect(themeName, person.photo, person.name, event) {
        card = withContext(Dispatchers.Default) { WishCard.render(ctx, person, event, next, theme, from) }
    }

    fun send(otherApps: Boolean) {
        sending = true
        scope.launch {
            val uri = if (attach) card?.let { withContext(Dispatchers.IO) { WishCard.share(ctx, it) } } else null
            (ctx as? MainActivity)?.openExternal(Wishes.intent(ctx, person.phone, message, uri, otherApps))
            sending = false
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        TopBar("Send wish", onBack = { nav.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            // Card preview
            if (attach) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(WishCard.W / WishCard.H.toFloat()).clip(RoundedCornerShape(18.dp)).background(K.TealLight),
                    contentAlignment = Alignment.Center,
                ) {
                    val b = card
                    if (b != null) Image(b.asImageBitmap(), "Greeting card", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    else CircularProgressIndicator()
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    WishCard.Theme.entries.forEach { t ->
                        KChip(t.label, t.name == themeName, Color(t.top.toInt())) { themeName = t.name }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoftButton(if (person.photo > 0) "📷 Change photo" else "📷 Add photo", Modifier.weight(1f)) { pickPhoto() }
                    if (person.photo > 0) SoftButton("Remove photo", Modifier.weight(1f)) {
                        PhotoStore.delete(ctx, person.id); Repo.setPhoto(person.id, 0)
                    }
                }
            }
            KCard(Modifier.fillMaxWidth().padding(top = 12.dp), padding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)) {
                ToggleRow("Attach greeting card picture", "Sent together with your message", attach) { attach = it }
            }

            FieldLabel("Message")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                if (saved != null) KChip("My default", false, K.Teal) { message = Wishes.fill(saved, person, event, next, from) }
                templates.forEach { t ->
                    KChip(t.label, false) { message = Wishes.fill(t.text, person, event, next, from) }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                message, { message = it }, modifier = Modifier.fillMaxWidth(), minLines = 4,
                shape = RoundedCornerShape(12.dp), colors = kFieldColors(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            TextButton(onClick = {
                // Save as a template: put the placeholders back so it works for everyone.
                var t = message.replace(person.name.trim(), "{name}")
                if (from.isNotBlank()) t = t.replace(from, "{from}")
                Repo.setWishDefault(event.type, t)
                Toast.makeText(ctx, "Saved as your default ${event.type.label.lowercase()} message", Toast.LENGTH_SHORT).show()
            }) { Text("☆ Save as my default ${event.type.label.lowercase()} message", color = K.Teal, fontSize = 13.sp) }

            if (person.phone.isBlank()) {
                Text(
                    "No phone number saved for ${person.name}, so WhatsApp will ask you to pick the chat. Add it with ✎ on their page.",
                    fontSize = 12.sp, color = K.Muted,
                )
            }
            Spacer(Modifier.height(20.dp))
        }
        Column(Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding().padding(16.dp)) {
            PrimaryButton(
                if (Wishes.whatsAppPackage(ctx) != null) "💬 Send on WhatsApp" else "Send wish",
                Modifier.fillMaxWidth(), color = K.WhatsApp, loading = sending, enabled = message.isNotBlank(),
            ) { send(false) }
            TextButton(onClick = { send(true) }, modifier = Modifier.fillMaxWidth()) { Text("Share to other apps", color = K.Teal) }
        }
    }
}


// ============================ DEFAULT MESSAGES (Admin) ============================

@Composable
fun WishTemplatesScreen(nav: Nav, data: AppData) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().imePadding()) {
        TopBar("Wish messages", onBack = { nav.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Text(
                "Your default WhatsApp message for each kind of event. You can still change it before sending.\n" +
                    "Use {name}, {age} (e.g. 34th), {event} and {from} (your name).",
                fontSize = 13.sp, color = K.Muted,
            )
            EventType.entries.forEach { t ->
                var text by remember(data.wishDefaults[t.key]) { mutableStateOf(data.wishDefaults[t.key] ?: Wishes.templates(t).first().text) }
                SectionTitle("${t.emoji} ${t.label}")
                OutlinedTextField(
                    text, { text = it }, modifier = Modifier.fillMaxWidth(), minLines = 3,
                    shape = RoundedCornerShape(12.dp), colors = kFieldColors(),
                )
                Row {
                    TextButton(onClick = {
                        Repo.setWishDefault(t, text)
                        Toast.makeText(ctx, "Saved", Toast.LENGTH_SHORT).show()
                    }) { Text("Save", color = K.Teal, fontWeight = FontWeight.SemiBold) }
                    TextButton(onClick = {
                        Repo.setWishDefault(t, null)
                        text = Wishes.templates(t).first().text
                    }) { Text("Reset", color = K.Muted) }
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

/** Avatar that shows the person's photo when they have one. */
@Composable
fun PersonPhotoAvatar(p: Person, size: androidx.compose.ui.unit.Dp, corner: androidx.compose.ui.unit.Dp, fontSize: Int) {
    val ctx = LocalContext.current
    val bmp = remember(p.id, p.photo) { PhotoStore.bitmap(ctx, p) }
    if (bmp != null) {
        Image(
            bmp.asImageBitmap(), p.name, contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(RoundedCornerShape(corner)),
        )
    } else Avatar(p.name, p.color, size, corner, fontSize)
}
