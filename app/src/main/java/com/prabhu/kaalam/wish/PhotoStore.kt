package com.prabhu.kaalam.wish

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Base64
import android.util.LruCache
import com.prabhu.kaalam.data.AppData
import com.prabhu.kaalam.data.Person
import org.json.JSONObject
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * People's photos: square 512 px JPEGs in the app's private files.
 * They travel inside the encrypted Drive backup (as base64), so a restore brings them back.
 */
object PhotoStore {
    private const val SIZE = 512
    private val cache = LruCache<String, Bitmap>(48)

    private fun dir(ctx: Context) = File(ctx.filesDir, "photos").apply { mkdirs() }
    fun file(ctx: Context, personId: String) = File(dir(ctx), "$personId.jpg")

    /** Reads, centre-crops to a square, shrinks and saves. Returns a new version stamp, or 0 on failure. */
    fun save(ctx: Context, personId: String, uri: Uri): Long = runCatching {
        val src = ImageDecoder.createSource(ctx.contentResolver, uri)
        val decoded = ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val w = info.size.width
            val h = info.size.height
            val scale = (SIZE * 1.25f) / min(w, h)
            if (scale < 1f) decoder.setTargetSize(max(1, (w * scale).toInt()), max(1, (h * scale).toInt()))
        }
        val side = min(decoded.width, decoded.height)
        val square = Bitmap.createBitmap(decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side)
        val out = if (side > SIZE) Bitmap.createScaledBitmap(square, SIZE, SIZE, true) else square
        file(ctx, personId).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        cache.evictAll()
        System.currentTimeMillis()
    }.getOrDefault(0L)

    fun delete(ctx: Context, personId: String) {
        file(ctx, personId).delete()
        cache.evictAll()
    }

    fun bitmap(ctx: Context, p: Person): Bitmap? {
        if (p.photo <= 0) return null
        val key = "${p.id}:${p.photo}"
        cache.get(key)?.let { return it }
        val f = file(ctx, p.id)
        if (!f.exists()) return null
        val b = BitmapFactory.decodeFile(f.absolutePath) ?: return null
        cache.put(key, b)
        return b
    }

    /** For the Drive backup: personId -> base64 JPEG. */
    fun exportAll(ctx: Context, d: AppData): JSONObject {
        val o = JSONObject()
        d.people.filter { it.photo > 0 }.forEach { p ->
            val f = file(ctx, p.id)
            if (f.exists()) o.put(p.id, Base64.encodeToString(f.readBytes(), Base64.NO_WRAP))
        }
        return o
    }

    fun importAll(ctx: Context, o: JSONObject?) {
        if (o == null) return
        o.keys().forEach { id ->
            runCatching { file(ctx, id).writeBytes(Base64.decode(o.getString(id), Base64.NO_WRAP)) }
        }
        cache.evictAll()
    }
}
