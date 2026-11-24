package com.prabhu.kaalam.wish

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.prabhu.kaalam.R
import com.prabhu.kaalam.data.DateMath
import com.prabhu.kaalam.data.Event
import com.prabhu.kaalam.data.EventType
import com.prabhu.kaalam.data.Person
import com.prabhu.kaalam.data.Texts
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.random.Random

/** Draws the greeting-card picture that is attached to WhatsApp wishes. */
object WishCard {
    const val W = 1080
    const val H = 1350

    enum class Theme(val label: String, val top: Long, val bottom: Long, val accent: Long) {
        TEAL("Teal", 0xFF17929D, 0xFF083E44, 0xFFFFC857),
        CORAL("Coral", 0xFFFF8A65, 0xFFA8324A, 0xFFFFF1C1),
        PURPLE("Purple", 0xFF9B6BFF, 0xFF34197F, 0xFFFFD6F0),
        GOLD("Gold", 0xFFF5B83D, 0xFF8A4F06, 0xFFFFFFFF),
        NIGHT("Night", 0xFF243B55, 0xFF0B1320, 0xFFFFC857),
    }

    fun defaultTheme(t: EventType): Theme = when (t) {
        EventType.BIRTHDAY -> Theme.TEAL
        EventType.ANNIVERSARY -> Theme.PURPLE
        EventType.CUSTOM -> Theme.GOLD
    }

    private val avatarColors = longArrayOf(0xFFFF7A59, 0xFF8B5CF6, 0xFFF5A524, 0xFF14A3A0, 0xFF3B82F6, 0xFFEC4899, 0xFF10B981, 0xFF6366F1)

    fun headline(ev: Event): String = when (ev.type) {
        EventType.BIRTHDAY -> "Happy Birthday"
        EventType.ANNIVERSARY -> "Happy Anniversary"
        EventType.CUSTOM -> "Happy ${Texts.eventLabel(ev)}"
    }

    fun countLine(ev: Event, next: LocalDate?): String? {
        if (next == null || !ev.yearKnown || !ev.yearly || !ev.showCount) return null
        val n = next.year - ev.date.year
        if (n <= 0) return null
        return when (ev.type) {
            EventType.BIRTHDAY -> "Turning $n 🎉"
            EventType.ANNIVERSARY -> "$n ${if (n == 1) "year" else "years"} together 💕"
            EventType.CUSTOM -> "${DateMath.ordinal(n)} ${Texts.eventLabel(ev)}"
        }
    }

    fun render(ctx: Context, p: Person, ev: Event, next: LocalDate?, theme: Theme, from: String): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val bold: Typeface = ResourcesCompat.getFont(ctx, R.font.poppins_bold) ?: Typeface.DEFAULT_BOLD
        val medium: Typeface = ResourcesCompat.getFont(ctx, R.font.poppins_medium) ?: Typeface.DEFAULT
        val regular: Typeface = ResourcesCompat.getFont(ctx, R.font.poppins_regular) ?: Typeface.DEFAULT
        val accent = theme.accent.toInt()

        // Background
        val bg = Paint().apply { shader = LinearGradient(0f, 0f, W.toFloat(), H.toFloat(), theme.top.toInt(), theme.bottom.toInt(), Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), bg)

        // Confetti (same pattern every time for the same person)
        val rnd = Random(p.id.hashCode())
        val dot = Paint(Paint.ANTI_ALIAS_FLAG)
        repeat(70) {
            dot.color = if (rnd.nextBoolean()) accent else 0xFFFFFFFF.toInt()
            dot.alpha = 40 + rnd.nextInt(110)
            val x = rnd.nextFloat() * W
            val y = rnd.nextFloat() * H
            val r = 4f + rnd.nextFloat() * 10f
            if (rnd.nextInt(3) == 0) {
                c.save(); c.rotate(rnd.nextFloat() * 180f, x, y)
                c.drawRect(x - r, y - r / 2.5f, x + r, y + r / 2.5f, dot); c.restore()
            } else c.drawCircle(x, y, r, dot)
        }

        // Glow behind the photo
        val cx = W / 2f
        val cy = 440f
        val radius = 220f
        c.drawCircle(cx, cy, radius * 1.6f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(cx, cy, radius * 1.6f, 0x55FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        })

        // Photo (or initial)
        val photo = PhotoStore.bitmap(ctx, p)
        if (photo != null) {
            val shader = BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val s = (radius * 2) / photo.width
            shader.setLocalMatrix(Matrix().apply { setScale(s, s); postTranslate(cx - radius, cy - radius) })
            c.drawCircle(cx, cy, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader })
        } else {
            val col = avatarColors[((p.color % avatarColors.size) + avatarColors.size) % avatarColors.size].toInt()
            c.drawCircle(cx, cy, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col })
            val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); textSize = 200f; typeface = bold; textAlign = Paint.Align.CENTER }
            c.drawText(p.name.trim().firstOrNull()?.uppercase() ?: "?", cx, cy - (t.descent() + t.ascent()) / 2, t)
        }
        c.drawCircle(cx, cy, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 14f; color = 0xFFFFFFFF.toInt() })

        // Emoji badge
        val bx = cx + radius * 0.72f
        val by = cy + radius * 0.72f
        c.drawCircle(bx, by, 78f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() })
        val emo = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 92f; textAlign = Paint.Align.CENTER }
        c.drawText(ev.type.emoji, bx, by - (emo.descent() + emo.ascent()) / 2, emo)

        // Texts
        var y = cy + radius + 150f
        y = drawFit(c, headline(ev), y, bold, 96f, 0xFFFFFFFF.toInt())
        y = drawFit(c, p.name.trim(), y + 18f, bold, 74f, accent)
        countLine(ev, next)?.let { y = drawFit(c, it, y + 22f, medium, 48f, 0xEEFFFFFF.toInt()) }
        next?.let {
            val f = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH)
            y = drawFit(c, it.format(f), y + 14f, regular, 36f, 0xB3FFFFFF.toInt())
        }

        // Footer
        if (from.isNotBlank()) {
            val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent; strokeWidth = 4f; alpha = 180 }
            c.drawLine(cx - 70f, H - 190f, cx + 70f, H - 190f, line)
            drawFit(c, "With love, ${from.trim()}", H - 120f, medium, 44f, 0xFFFFFFFF.toInt())
        }
        return bmp
    }

    /** Draws centred text, shrinking it to fit the width. Returns the baseline used. */
    private fun drawFit(c: Canvas, text: String, baseline: Float, tf: Typeface, size: Float, color: Int): Float {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; typeface = tf; textSize = size; textAlign = Paint.Align.CENTER }
        val maxW = W - 120f
        while (p.measureText(text) > maxW && p.textSize > 30f) p.textSize -= 2f
        val b = baseline + p.textSize * 0.2f
        c.drawText(text, W / 2f, b, p)
        return b + p.textSize * 0.35f
    }

    /** Saves the picture where WhatsApp can read it. */
    fun share(ctx: Context, bmp: Bitmap): Uri {
        val dir = File(ctx.cacheDir, "wishes").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val f = File(dir, "kaalam_wish_${System.currentTimeMillis()}.jpg")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        return FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
    }
}

/** WhatsApp message templates and sending. */
object Wishes {
    data class Template(val label: String, val text: String)

    fun templates(t: EventType): List<Template> = when (t) {
        EventType.BIRTHDAY -> listOf(
            Template("Warm", "Happy Birthday {name}! 🎂🎉 Wishing you a wonderful year ahead filled with health and happiness."),
            Template("Blessings", "Many happy returns of the day, {name}! 🎂 May God bless you with joy, good health and success. — {from}"),
            Template("தமிழ்", "இனிய பிறந்தநாள் நல்வாழ்த்துகள் {name}! 🎂🎉 எல்லா வளமும் நலமும் பெற்று வாழ்க!"),
            Template("Short", "Happy {age} birthday, {name}! 🥳"),
        )
        EventType.ANNIVERSARY -> listOf(
            Template("Warm", "Happy Anniversary {name}! 💍✨ Wishing you many more years of love and togetherness."),
            Template("Blessings", "Happy {age} wedding anniversary, {name}! 💐 May your bond grow stronger every year. — {from}"),
            Template("தமிழ்", "இனிய திருமண நாள் நல்வாழ்த்துகள் {name}! 💍💐"),
            Template("Short", "Happy Anniversary, {name}! 💕"),
        )
        EventType.CUSTOM -> listOf(
            Template("Warm", "Happy {event}, {name}! ⭐ Best wishes from {from}."),
            Template("Congrats", "Congratulations {name} on your {event}! 🎉"),
            Template("Short", "Happy {event}! 🎉"),
        )
    }

    /** Fills {name}, {age} (e.g. 34th), {event}, {from}. */
    fun fill(text: String, p: Person, ev: Event, next: LocalDate?, from: String): String {
        val n = if (next != null && ev.yearKnown && ev.yearly) (next.year - ev.date.year).takeIf { it > 0 } else null
        var t = text
        t = if (n != null) t.replace("{age}", DateMath.ordinal(n)) else t.replace("{age} ", "").replace("{age}", "")
        t = t.replace("{name}", p.name.trim()).replace("{event}", Texts.eventLabel(ev))
        t = if (from.isNotBlank()) t.replace("{from}", from.trim()) else t.replace(" — {from}", "").replace(" from {from}", "").replace("{from}", "")
        return t
    }

    private fun installed(ctx: Context, pkg: String): Boolean = try {
        ctx.packageManager.getPackageInfo(pkg, 0); true
    } catch (_: PackageManager.NameNotFoundException) { false }

    fun whatsAppPackage(ctx: Context): String? = listOf("com.whatsapp", "com.whatsapp.w4b").firstOrNull { installed(ctx, it) }

    private fun digits(phone: String): String {
        var d = phone.filter { it.isDigit() }
        if (d.length == 10) d = "91$d"
        if (d.length == 11 && d.startsWith("0")) d = "91" + d.drop(1)
        return d
    }

    /**
     * Builds the intent that opens WhatsApp with the picture + message.
     * With a phone number it tries to open that person's chat directly.
     * toOtherApps = show Android's share sheet instead (Telegram, SMS, …).
     */
    fun intent(ctx: Context, phone: String, message: String, image: Uri?, toOtherApps: Boolean = false): Intent {
        val pkg = if (toOtherApps) null else whatsAppPackage(ctx)
        val num = digits(phone)
        if (image == null && !toOtherApps && num.isNotEmpty()) {
            return Intent(Intent.ACTION_VIEW, Uri.parse(Texts.whatsAppUrl(phone, message)))
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = if (image != null) "image/jpeg" else "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
            if (image != null) {
                putExtra(Intent.EXTRA_STREAM, image)
                clipData = ClipData.newRawUri("wish", image)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            if (pkg != null) {
                setPackage(pkg)
                if (num.isNotEmpty()) putExtra("jid", "$num@s.whatsapp.net")
            }
        }
        return if (pkg != null) send else Intent.createChooser(send, "Send wish")
    }
}
