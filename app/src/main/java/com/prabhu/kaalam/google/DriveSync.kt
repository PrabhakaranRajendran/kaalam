package com.prabhu.kaalam.google

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.prabhu.kaalam.data.Repo
import com.prabhu.kaalam.data.Store
import com.prabhu.kaalam.security.Prefs
import com.prabhu.kaalam.security.Vault
import com.prabhu.kaalam.wish.PhotoStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.time.LocalDate

/**
 * Google Drive storage:
 *   My Drive / Kaalam / kaalam_data.enc           ← always the latest (encrypted)
 *   My Drive / Kaalam / backups / kaalam_YYYY-MM-DD.enc  ← one per day, last 7 kept
 */
object DriveSync {
    private const val API = "https://www.googleapis.com/drive/v3/files"
    private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3/files"
    private const val FOLDER_MIME = "application/vnd.google-apps.folder"
    const val FOLDER = "Kaalam"
    const val FILE = "kaalam_data.enc"
    const val BACKUPS = "backups"
    private const val KEEP_DAILY = 7

    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val lastBackup = MutableStateFlow(0L)
    private var pending: Job? = null
    private val mutex = Mutex()

    data class Remote(val id: String, val name: String, val modified: String, val size: Long)

    fun requestAutoUpload(ctx: Context) {
        if (!Prefs.autoSave || !Vault.hasPin() || !GoogleAuth.isConnected(ctx)) return
        pending?.cancel()
        pending = Repo.scope.launch {
            delay(2500)
            runCatching { uploadNow(ctx, auto = true) }
        }
    }

    fun onWifi(ctx: Context): Boolean {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private suspend fun find(ctx: Context, query: String): List<Remote> {
        val url = "$API?q=${enc(query)}&spaces=drive&pageSize=100&orderBy=${enc("name desc")}" +
            "&fields=${enc("files(id,name,modifiedTime,size)")}"
        val arr = Api.json(ctx, "GET", url).optJSONArray("files") ?: JSONArray()
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Remote(o.getString("id"), o.optString("name"), o.optString("modifiedTime"), o.optString("size", "0").toLongOrNull() ?: 0)
        }
    }

    private suspend fun folder(ctx: Context, name: String, parent: String?): String {
        val q = "name = '$name' and mimeType = '$FOLDER_MIME' and trashed = false" +
            (parent?.let { " and '$it' in parents" } ?: "")
        find(ctx, q).firstOrNull()?.let { return it.id }
        val meta = JSONObject().put("name", name).put("mimeType", FOLDER_MIME)
        if (parent != null) meta.put("parents", JSONArray().put(parent))
        return Api.json(ctx, "POST", "$API?fields=id", meta).getString("id")
    }

    private suspend fun createFile(ctx: Context, name: String, parent: String, content: ByteArray): String {
        val boundary = "kaalam${System.currentTimeMillis()}"
        val meta = JSONObject().put("name", name).put("parents", JSONArray().put(parent))
            .put("mimeType", "application/octet-stream")
            .put("description", "Kaalam app data (encrypted with your Kaalam password)")
        val out = ByteArrayOutputStream()
        out.write(("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n" +
            "--$boundary\r\nContent-Type: application/octet-stream\r\n\r\n").toByteArray())
        out.write(content)
        out.write("\r\n--$boundary--\r\n".toByteArray())
        val res = Api.call(ctx, "POST", "$UPLOAD?uploadType=multipart&fields=id", out.toByteArray(), "multipart/related; boundary=$boundary")
        return JSONObject(res).getString("id")
    }

    private suspend fun updateFile(ctx: Context, id: String, content: ByteArray) {
        Api.call(ctx, "PATCH", "$UPLOAD/$id?uploadType=media&fields=id", content, "application/octet-stream")
    }

    /** Uploads the latest data now. auto = background save (respects Wi-Fi only, never throws to UI). */
    suspend fun uploadNow(ctx: Context, auto: Boolean = false) = mutex.withLock {
        if (auto && Prefs.wifiOnly && !onWifi(ctx)) return@withLock
        busy.value = true
        try {
            val d = Repo.data.value
            val json = JSONObject(Store.toJson(d)).put("photos", PhotoStore.exportAll(ctx, d)).toString()
            val payload = Vault.encryptForDrive(json).toByteArray(Charsets.UTF_8)
            val folderId = folder(ctx, FOLDER, null)
            val existing = find(ctx, "name = '$FILE' and '$folderId' in parents and trashed = false").firstOrNull()
            if (existing != null) updateFile(ctx, existing.id, payload) else createFile(ctx, FILE, folderId, payload)
            dailyIfDue(ctx, folderId, payload)
            Prefs.lastBackupAt = System.currentTimeMillis()
            lastBackup.value = Prefs.lastBackupAt
            error.value = null
        } catch (e: Exception) {
            error.value = Api.friendly(e)
            if (!auto) throw e
        } finally {
            busy.value = false
        }
    }

    private suspend fun dailyIfDue(ctx: Context, folderId: String, payload: ByteArray) {
        if (!Prefs.dailyBackup) return
        val today = LocalDate.now().toString()
        if (Prefs.lastDailyBackup == today) return
        val b = folder(ctx, BACKUPS, folderId)
        val name = "kaalam_$today.enc"
        val same = find(ctx, "name = '$name' and '$b' in parents and trashed = false").firstOrNull()
        if (same != null) updateFile(ctx, same.id, payload) else createFile(ctx, name, b, payload)
        Prefs.lastDailyBackup = today
        val all = find(ctx, "'$b' in parents and trashed = false").sortedByDescending { it.name }
        all.drop(KEEP_DAILY).forEach { runCatching { Api.call(ctx, "DELETE", "$API/${it.id}") } }
    }

    /** The latest file plus the daily copies, newest first. */
    suspend fun listBackups(ctx: Context): List<Remote> {
        val folderId = find(ctx, "name = '$FOLDER' and mimeType = '$FOLDER_MIME' and trashed = false").firstOrNull()?.id
            ?: return emptyList()
        val main = find(ctx, "name = '$FILE' and '$folderId' in parents and trashed = false")
        val b = find(ctx, "name = '$BACKUPS' and mimeType = '$FOLDER_MIME' and '$folderId' in parents and trashed = false").firstOrNull()?.id
        val daily = if (b != null) find(ctx, "'$b' in parents and trashed = false").sortedByDescending { it.name } else emptyList()
        return main + daily
    }

    suspend fun hasBackup(ctx: Context): Boolean = listBackups(ctx).isNotEmpty()

    suspend fun download(ctx: Context, id: String): String = Api.call(ctx, "GET", "$API/$id?alt=media")
}
