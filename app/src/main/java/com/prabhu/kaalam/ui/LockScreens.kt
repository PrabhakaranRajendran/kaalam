package com.prabhu.kaalam.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.prabhu.kaalam.MainActivity
import com.prabhu.kaalam.data.Repo
import com.prabhu.kaalam.data.Texts
import com.prabhu.kaalam.google.DriveSync
import com.prabhu.kaalam.google.GoogleAuth
import com.prabhu.kaalam.security.Bio
import com.prabhu.kaalam.security.Prefs
import com.prabhu.kaalam.security.Vault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private const val PIN_LEN = 6
private const val MAX_TRIES = 5
private const val LOCKOUT_MS = 30_000L

// ======================= Lock screen =======================

@Composable
fun UnlockFlow(activity: MainActivity) {
    var forgot by remember { mutableStateOf(false) }
    if (forgot) ForgotPinScreen(onCancel = { forgot = false }, onDone = { AppState.locked = false })
    else LockScreen(
        activity = activity,
        title = "Kaalam",
        greeting = Repo.data.value.profile.name.takeIf { it.isNotBlank() }?.let { "Welcome back, ${it.split(" ").first()}" } ?: "Welcome back",
        onUnlocked = { AppState.locked = false },
        onForgot = { forgot = true },
    )
}

@Composable
fun AdminGate(activity: FragmentActivity, onUnlocked: () -> Unit) {
    LockScreen(activity, title = "Admin", greeting = "Confirm it's you to open Admin", onUnlocked = onUnlocked, onForgot = null, admin = true)
}

@Composable
fun LockScreen(
    activity: FragmentActivity,
    title: String,
    greeting: String,
    onUnlocked: () -> Unit,
    onForgot: (() -> Unit)?,
    admin: Boolean = false,
) {
    val bioOn = Prefs.biometric && Bio.available(activity)
    var showPin by remember { mutableStateOf(!bioOn) }
    val askFinger = {
        Bio.prompt(activity, if (admin) "Open Admin" else "Unlock Kaalam", onSuccess = onUnlocked, onFallback = { showPin = true })
    }
    LaunchedEffect(Unit) { if (bioOn) { delay(250); askFinger() } }

    if (!showPin) {
        StatusBarIcons(lightIcons = true)
        Box(
            Modifier.fillMaxSize().background(Brush.linearGradient(listOf(K.TealMid, K.TealDark, K.TealDeep))),
        ) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(48.dp))
                Box(Modifier.size(84.dp).clip(RoundedCornerShape(26.dp)).background(Color.White.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    AppLogo(56.dp, background = false)
                }
                Spacer(Modifier.height(16.dp))
                Text(title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(greeting, color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(190.dp).border(1.5.dp, Color.White.copy(alpha = 0.15f), CircleShape).padding(22.dp)
                        .border(1.5.dp, Color.White.copy(alpha = 0.25f), CircleShape).padding(22.dp)
                        .clip(CircleShape).background(Color.White.copy(alpha = 0.95f)).clickable { askFinger() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Fingerprint, "Fingerprint", tint = K.Teal, modifier = Modifier.size(64.dp))
                }
                Spacer(Modifier.height(24.dp))
                Text("Touch the fingerprint sensor", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(if (admin) "to open Admin" else "to unlock your events", color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                OutlinedButton(
                    onClick = { showPin = true },
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    shape = RoundedCornerShape(24.dp),
                ) { Text("🔑  Use password instead") }
                Spacer(Modifier.height(24.dp))
            }
        }
    } else {
        PinUnlock(
            title = if (admin) "Admin password" else "Enter app password",
            subtitle = if (bioOn) "Fingerprint not working? Use your PIN" else "Enter your 6-digit PIN",
            onFingerprint = if (bioOn) askFinger else null,
            onBack = if (bioOn) ({ showPin = false }) else null,
            onUnlocked = onUnlocked,
            onForgot = onForgot,
        )
    }
}

@Composable
private fun PinUnlock(
    title: String,
    subtitle: String,
    onFingerprint: (() -> Unit)?,
    onBack: (() -> Unit)?,
    onUnlocked: () -> Unit,
    onForgot: (() -> Unit)?,
) {
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(500) } }
    val lockedOut = Prefs.lockoutUntil > now

    fun submit(p: String) {
        checking = true
        scope.launch {
            val ok = withContext(Dispatchers.Default) { Vault.checkPin(p) }
            checking = false
            if (ok) {
                Prefs.failedAttempts = 0
                onUnlocked()
            } else {
                val tries = Prefs.failedAttempts + 1
                Prefs.failedAttempts = tries
                pin = ""
                if (tries >= MAX_TRIES) {
                    Prefs.failedAttempts = 0
                    Prefs.lockoutUntil = System.currentTimeMillis() + LOCKOUT_MS
                    error = null
                } else error = "Wrong password · ${MAX_TRIES - tries} tries left"
            }
        }
    }

    StatusBarIcons(lightIcons = false)
    Column(
        Modifier.fillMaxSize().background(Color.White).statusBarsPadding().navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp)) {
            if (onBack != null) SquareIconButton(Icons.AutoMirrored.Rounded.ArrowBack, onClick = onBack) else Spacer(Modifier.height(38.dp))
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.size(62.dp).clip(RoundedCornerShape(20.dp)).background(K.TealLight), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Lock, null, tint = K.Teal, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = K.TealDark)
        Text(subtitle, fontSize = 13.sp, color = K.Muted)
        Spacer(Modifier.height(26.dp))
        PinDots(pin.length, PIN_LEN, error = error != null && pin.isEmpty())
        Box(Modifier.height(40.dp), contentAlignment = Alignment.Center) {
            when {
                lockedOut -> Text("Too many tries. Wait ${(Prefs.lockoutUntil - now) / 1000 + 1}s", color = K.Red, fontSize = 13.sp)
                checking -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                error != null -> Text(error!!, color = K.Red, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.weight(1f))
        PinPad(
            onDigit = { c ->
                if (!lockedOut && !checking && pin.length < PIN_LEN) {
                    pin += c
                    error = null
                    if (pin.length == PIN_LEN) submit(pin)
                }
            },
            onBackspace = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
            onFingerprint = onFingerprint,
        )
        Spacer(Modifier.height(12.dp))
        if (onForgot != null) {
            TextButton(onClick = onForgot) {
                Text("Forgot password? Verify with Google account", color = K.Teal, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** Enter a new PIN twice. */
@Composable
fun NewPinEntry(title: String, onBack: (() -> Unit)?, onDone: (String) -> Unit) {
    var first by remember { mutableStateOf<String?>(null) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    StatusBarIcons(lightIcons = false)
    Column(
        Modifier.fillMaxSize().background(Color.White).statusBarsPadding().navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp)) {
            if (onBack != null) SquareIconButton(Icons.AutoMirrored.Rounded.ArrowBack, onClick = { if (first != null) { first = null; pin = "" } else onBack() })
            else Spacer(Modifier.height(38.dp))
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.size(62.dp).clip(RoundedCornerShape(20.dp)).background(K.TealLight), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Lock, null, tint = K.Teal, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(if (first == null) title else "Confirm password", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = K.TealDark)
        Text(if (first == null) "Choose a 6-digit PIN" else "Enter the same 6 digits again", fontSize = 13.sp, color = K.Muted)
        Spacer(Modifier.height(26.dp))
        PinDots(pin.length, PIN_LEN)
        Box(Modifier.height(40.dp), contentAlignment = Alignment.Center) {
            if (error != null) Text(error!!, color = K.Red, fontSize = 13.sp)
        }
        Spacer(Modifier.weight(1f))
        PinPad(
            onDigit = { c ->
                if (pin.length < PIN_LEN) {
                    pin += c
                    error = null
                    if (pin.length == PIN_LEN) {
                        val f = first
                        when {
                            f == null -> {
                                if (pin.toSet().size == 1 || pin == "123456" || pin == "654321") {
                                    error = "Too easy to guess — try another"
                                    pin = ""
                                } else { first = pin; pin = "" }
                            }
                            f == pin -> onDone(pin)
                            else -> { error = "Didn't match. Start again."; first = null; pin = "" }
                        }
                    }
                }
            },
            onBackspace = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
        )
        Spacer(Modifier.height(32.dp))
    }
}

// ======================= Forgot password =======================

@Composable
private fun ForgotPinScreen(onCancel: () -> Unit, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val linked = Prefs.googleEmail
    var verified by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val signIn = rememberGoogleSignIn { email, err ->
        when {
            err != null -> error = err
            email != null && email.equals(linked, ignoreCase = true) -> verified = true
            else -> error = "That's a different Google account. Use $linked."
        }
    }

    if (verified) {
        if (saving) {
            Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else NewPinEntry(title = "Set a new password", onBack = onCancel) { pin ->
            saving = true
            scope.launch {
                withContext(Dispatchers.Default) { Vault.setPin(pin) }
                // Re-encrypt the Drive copy with the new password.
                runCatching { DriveSync.uploadNow(ctx, auto = true) }
                onDone()
            }
        }
        return
    }

    Column(Modifier.fillMaxSize().background(K.Bg)) {
        TopBar("Forgot password", onBack = onCancel)
        Column(Modifier.padding(16.dp)) {
            KCard {
                if (linked != null) {
                    Text("Verify with Google", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = K.TealDark)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Sign in with the Google account linked to Kaalam ($linked). Then you can set a new 6-digit password. Your events stay as they are.",
                        fontSize = 13.sp, color = K.Muted,
                    )
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton("Verify with Google", Modifier.fillMaxWidth()) { error = null; signIn(true) }
                    ErrorText(error)
                } else {
                    Text("No Google account linked", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = K.TealDark)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "The password can only be reset through the Google account connected in Admin › Google Drive data. " +
                            "This phone has none, so the only way back in is Android Settings › Apps › Kaalam › Clear storage — which deletes the events saved on this phone.",
                        fontSize = 13.sp, color = K.Muted,
                    )
                }
            }
        }
    }
}

// ======================= Change password =======================

@Composable
fun ChangePinScreen(nav: Nav) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(0) } // 0 = current, 1 = new, 2 = saving
    when (step) {
        0 -> PinUnlock(
            title = "Current password", subtitle = "Enter your current 6-digit PIN",
            onFingerprint = null, onBack = { nav.back() }, onUnlocked = { step = 1 }, onForgot = null,
        )
        1 -> NewPinEntry(title = "New password", onBack = { nav.back() }) { pin ->
            step = 2
            scope.launch {
                withContext(Dispatchers.Default) { Vault.setPin(pin) }
                runCatching { DriveSync.uploadNow(ctx, auto = true) }
                android.widget.Toast.makeText(ctx, "Password changed", android.widget.Toast.LENGTH_SHORT).show()
                nav.back()
            }
        }
        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
}

// ======================= First-time setup =======================

@Composable
fun SetupScreen(activity: MainActivity, onFinished: () -> Unit) {
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    var dobText by rememberSaveable { mutableStateOf("") }
    var pin by remember { mutableStateOf<String?>(null) }
    var useFinger by remember { mutableStateOf(Bio.available(activity)) }
    var saving by remember { mutableStateOf(false) }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun finish() {
        val p = pin ?: return
        saving = true
        scope.launch {
            withContext(Dispatchers.Default) { Vault.setPin(p) }
            Prefs.biometric = useFinger
            Repo.setProfile(name, dobText.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) })
            if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            onFinished()
        }
    }

    when (step) {
        0 -> {
            StatusBarIcons(lightIcons = true)
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(K.TealMid, K.TealDark, K.TealDeep)))) {
                Column(
                    Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.weight(1f))
                    AppLogo(110.dp)
                    Spacer(Modifier.height(18.dp))
                    Text("Kaalam", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                    Text("Every age. Every moment.", color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp)
                    Spacer(Modifier.height(28.dp))
                    listOf(
                        "🎂  Birthdays, anniversaries & your own events",
                        "🔒  Fingerprint + password protected",
                        "📅  Syncs to its own Google Calendar",
                        "☁️  Encrypted backup in your Google Drive",
                    ).forEach { Text(it, color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = { step = 1 }, modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = K.TealDark),
                    ) { Text("Get started", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                }
            }
        }
        1 -> {
            var picking by remember { mutableStateOf(false) }
            Column(Modifier.fillMaxSize().background(K.Bg).imePadding().navigationBarsPadding()) {
                TopBar("About you", onBack = { step = 0 })
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
                    Text("Kaalam shows your age and counts down to your birthday on the home screen.", fontSize = 13.sp, color = K.Muted)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        name, { name = it }, label = { Text("Your name") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = kFieldColors(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    )
                    Spacer(Modifier.height(10.dp))
                    PickerField(
                        "Your date of birth",
                        dobText.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it).format(Texts.dMMMyyyy) } ?: "Tap to choose",
                        Modifier.fillMaxWidth(),
                    ) { picking = true }
                }
                PrimaryButton("Next", Modifier.fillMaxWidth().padding(16.dp), enabled = name.isNotBlank() && dobText.isNotBlank()) { step = 2 }
            }
            if (picking) KDatePickerDialog(
                initial = dobText.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) } ?: LocalDate.of(1990, 1, 1),
                title = "Date of birth",
                onDismiss = { picking = false },
            ) { dobText = it.toString() }
        }
        2 -> NewPinEntry(title = "Create app password", onBack = { step = 1 }) { pin = it; step = 3 }
        else -> {
            Column(Modifier.fillMaxSize().background(K.Bg).navigationBarsPadding()) {
                TopBar("Almost done", onBack = null)
                Column(Modifier.weight(1f).padding(16.dp)) {
                    KCard {
                        ToggleRow(
                            "👆 Unlock with fingerprint",
                            if (Bio.available(activity)) "Faster than typing your PIN" else "No fingerprint set up on this phone",
                            checked = useFinger, enabled = Bio.available(activity),
                        ) { useFinger = it }
                    }
                    Spacer(Modifier.height(12.dp))
                    KCard {
                        Text("Next steps (optional)", fontWeight = FontWeight.Bold, color = K.TealDark)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "• Admin › Google Drive data — connect Google to back up (or restore from another phone)\n" +
                                "• Admin › Calendar sync — put events into a separate “Kaalam” calendar\n" +
                                "• Admin › Import from contacts — bring in saved birthdays",
                            fontSize = 13.sp, color = K.Muted, lineHeight = 20.sp,
                        )
                    }
                }
                PrimaryButton("Start using Kaalam", Modifier.fillMaxWidth().padding(16.dp), loading = saving) { finish() }
            }
        }
    }
}
