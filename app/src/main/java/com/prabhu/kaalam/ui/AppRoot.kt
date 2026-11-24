package com.prabhu.kaalam.ui

import android.content.Intent
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.People
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prabhu.kaalam.MainActivity
import com.prabhu.kaalam.data.Repo
import com.prabhu.kaalam.google.GoogleAuth
import com.prabhu.kaalam.security.Prefs
import com.prabhu.kaalam.security.Vault

/** Lock state shared by the activity and the screens. */
object AppState {
    var locked by mutableStateOf(true)
    var adminUnlocked by mutableStateOf(false)
    var backgroundAt = 0L
    var skipLockUntil = 0L

    fun lock() {
        locked = true
        adminUnlocked = false
    }

    /** Call before opening another app/screen (Google sign-in, file picker) so returning doesn't lock. */
    fun allowExternal() {
        skipLockUntil = SystemClock.elapsedRealtime() + 10 * 60 * 1000
    }
}

sealed interface Route {
    data object Home : Route
    data object People : Route
    data object Calendar : Route
    data object Admin : Route
    data class PersonDetail(val id: String) : Route
    data class EditEvent(val personId: String?, val eventId: String?) : Route
    data object CalendarSettings : Route
    data object Drive : Route
    data object Security : Route
    data object ChangePin : Route
    data object Profile : Route
    data object ManagePeople : Route
    data object CustomTypes : Route
    data object ImportContacts : Route
}

class Nav {
    val stack = mutableStateListOf<Route>(Route.Home)
    val current: Route get() = stack.last()
    val isRoot: Boolean get() = stack.size == 1
    fun go(r: Route) { stack.add(r) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun tab(r: Route) { stack.clear(); stack.add(r) }
    /** Replace the current screen (e.g. after creating a person, open their page). */
    fun replace(r: Route) { if (stack.size > 1) stack.removeAt(stack.lastIndex); stack.add(r) }
}

@Composable
fun AppRoot(activity: MainActivity) {
    var hasPin by remember { mutableStateOf(Vault.hasPin()) }
    when {
        !hasPin -> SetupScreen(activity) {
            hasPin = true
            AppState.locked = false
        }
        AppState.locked -> UnlockFlow(activity)
        else -> MainScaffold(activity)
    }
}

@Composable
private fun MainScaffold(activity: MainActivity) {
    val nav = remember { Nav() }
    val data by Repo.data.collectAsState()

    AutoUpdateCheck()
    UpdateDialog()

    BackHandler(enabled = !nav.isRoot) { nav.back() }
    BackHandler(enabled = nav.isRoot && nav.current != Route.Home) { nav.tab(Route.Home) }

    Scaffold(
        containerColor = K.Bg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = { if (nav.isRoot) BottomBar(nav) },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (val r = nav.current) {
                Route.Home -> HomeScreen(nav, data)
                Route.People -> PeopleScreen(nav, data)
                Route.Calendar -> CalendarScreen(nav, data)
                Route.Admin ->
                    if (Prefs.lockAdmin && !AppState.adminUnlocked) AdminGate(activity) { AppState.adminUnlocked = true }
                    else AdminScreen(nav, data, activity)
                is Route.PersonDetail -> PersonScreen(nav, data, r.id)
                is Route.EditEvent -> EditEventScreen(nav, data, r.personId, r.eventId)
                Route.CalendarSettings -> CalendarSettingsScreen(nav, data)
                Route.Drive -> DriveScreen(nav, data)
                Route.Security -> SecurityScreen(nav, activity)
                Route.ChangePin -> ChangePinScreen(nav)
                Route.Profile -> ProfileScreen(nav, data)
                Route.ManagePeople -> ManagePeopleScreen(nav, data)
                Route.CustomTypes -> CustomTypesScreen(nav, data)
                Route.ImportContacts -> ImportContactsScreen(nav, data)
            }
        }
    }
}

@Composable
private fun BottomBar(nav: Nav) {
    val items = listOf(
        Triple(Route.Home, "Home", Icons.Rounded.Home),
        Triple(Route.People, "People", Icons.Rounded.People),
        Triple(Route.Calendar, "Calendar", Icons.Rounded.CalendarMonth),
        Triple(Route.Admin, "Admin", Icons.Rounded.AdminPanelSettings),
    )
    NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
        items.forEach { (route, label, icon: ImageVector) ->
            NavigationBarItem(
                selected = nav.current == route,
                onClick = { nav.tab(route) },
                icon = { Icon(icon, label) },
                label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = K.Teal, selectedTextColor = K.Teal,
                    indicatorColor = K.TealLight, unselectedIconColor = K.Faint, unselectedTextColor = K.Faint,
                ),
            )
        }
    }
}

/**
 * Opens Google's account picker. onDone gets null on success or an error message.
 * forceChooser = sign out first so the account list is always shown (used to verify identity).
 */
@Composable
fun rememberGoogleSignIn(onDone: (email: String?, error: String?) -> Unit): (forceChooser: Boolean) -> Unit {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        try {
            val email = GoogleAuth.handleResult(res.data)
            onDone(email, null)
        } catch (e: Exception) {
            onDone(null, e.message)
        }
    }
    return { force ->
        AppState.allowExternal()
        val client = GoogleAuth.client(ctx)
        if (force) client.signOut().addOnCompleteListener { launcher.launch(client.signInIntent) }
        else launcher.launch(client.signInIntent)
    }
}

/** Starts another app safely (WhatsApp, etc.). */
fun MainActivity.openExternal(intent: Intent) {
    AppState.allowExternal()
    runCatching { startActivity(intent) }
}

@Composable
fun ErrorText(text: String?) {
    if (text != null) {
        Text(
            text, color = K.Red, fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

val CardShape = RoundedCornerShape(18.dp)
