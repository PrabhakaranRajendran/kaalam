@file:Suppress("DEPRECATION")

package com.prabhu.kaalam.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prabhu.kaalam.MainActivity
import com.prabhu.kaalam.security.Prefs
import com.prabhu.kaalam.update.Updater
import kotlinx.coroutines.launch

/** "App updates" section on the Admin page — same as Wallpaper Studio's Settings. */
@Composable
fun AppUpdatesSection() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var auto by remember { mutableStateOf(Prefs.autoUpdateCheck) }

    val rel = Updater.available
    if (rel != null) {
        Spacer(Modifier.height(14.dp))
        KCard(Modifier.fillMaxWidth(), color = K.TealLight) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(K.Teal), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Download, null, tint = Color.White)
                }
                Column(Modifier.padding(start = 12.dp)) {
                    Text("New version available", fontWeight = FontWeight.Bold, color = K.TealDark)
                    Text("Version ${rel.version} on GitHub · you have ${Updater.currentVersion}", fontSize = 12.sp, color = K.TealDark)
                }
            }
            if (rel.notes.isNotBlank()) Text(rel.notes.take(500), fontSize = 13.sp, color = K.TealDark, modifier = Modifier.padding(top = 10.dp))
            val p = Updater.downloadProgress
            if (p != null) {
                Text("Downloading… ${(p * 100).toInt()}%", fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            }
            Row(Modifier.align(Alignment.End).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { Updater.available = null }) { Text("Later") }
                Button(
                    enabled = p == null,
                    colors = ButtonDefaults.buttonColors(containerColor = K.Teal),
                    onClick = { AppState.allowExternal(); scope.launch { Updater.downloadAndInstall(ctx, rel) } },
                ) { Text("Update now") }
            }
        }
    }

    SectionTitle("App updates")
    KCard(padding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Check for updates", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(
                    "Version ${Updater.currentVersion}" + if (Prefs.lastUpdateCheck > 0) " · checked ${timeAgo(Prefs.lastUpdateCheck)}" else "",
                    fontSize = 12.sp, color = K.Muted,
                )
            }
            OutlinedButton(
                enabled = !Updater.checking,
                shape = RoundedCornerShape(12.dp),
                onClick = {
                    Updater.dialogDismissed = true
                    scope.launch { Updater.check(quiet = false) }
                },
            ) { Text(if (Updater.checking) "Checking…" else "Check now", color = K.Teal) }
        }
        Updater.message?.let { Text(it, fontSize = 13.sp, color = K.Teal, modifier = Modifier.padding(bottom = 8.dp)) }
        ThinDivider()
        ToggleRow("Check automatically", "Tell me when a new version is on GitHub", auto) { auto = it; Prefs.autoUpdateCheck = it }
        ThinDivider()
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable {
                (ctx as? MainActivity)?.openExternal(Intent(Intent.ACTION_VIEW, Uri.parse(Updater.repoUrl + "/releases")))
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Update source", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(Updater.repoUrl.removePrefix("https://"), fontSize = 12.sp, color = K.Muted)
            }
            Icon(Icons.Rounded.OpenInNew, null, tint = K.Muted)
        }
    }
}

/** Pop-up when a newer version is found at start-up. */
@Composable
fun UpdateDialog() {
    val rel = Updater.available ?: return
    if (Updater.dialogDismissed) return
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val progress = Updater.downloadProgress
    AlertDialog(
        onDismissRequest = { if (progress == null) Updater.dialogDismissed = true },
        title = { Text("New version available") },
        text = {
            Column {
                Text("Version ${rel.version} is on GitHub. You have ${Updater.currentVersion}.")
                if (rel.notes.isNotBlank()) Text(rel.notes.take(400), modifier = Modifier.padding(top = 12.dp), fontSize = 13.sp, color = K.Muted)
                if (progress != null) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
                Updater.message?.let { Text(it, color = K.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            TextButton(enabled = progress == null, onClick = {
                AppState.allowExternal()
                scope.launch { Updater.downloadAndInstall(ctx, rel) }
            }) { Text("Update now") }
        },
        dismissButton = { TextButton(onClick = { Updater.dialogDismissed = true }) { Text("Later") } },
    )
}

/** Quiet check when the app is unlocked (at most every 6 hours). */
@Composable
fun AutoUpdateCheck() {
    LaunchedEffect(Unit) {
        if (Prefs.autoUpdateCheck && System.currentTimeMillis() - Prefs.lastUpdateCheck > 6 * 3_600_000L) {
            Updater.check(quiet = true)
        }
    }
}
