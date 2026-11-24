package com.prabhu.kaalam.ui

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat
import com.prabhu.kaalam.R
import com.prabhu.kaalam.data.EventType

object K {
    val Teal = Color(0xFF0F6E78)
    val TealMid = Color(0xFF13838E)
    val TealDark = Color(0xFF0A4E56)
    val TealDeep = Color(0xFF083E44)
    val TealLight = Color(0xFFE3F3F4)
    val Bg = Color(0xFFF2F7F8)
    val Ink = Color(0xFF0E2A2E)
    val Muted = Color(0xFF6B8287)
    val Faint = Color(0xFF9AB0B4)
    val Line = Color(0xFFE1EBEC)
    val Coral = Color(0xFFFF7A59)
    val CoralBg = Color(0xFFFFE9E2)
    val CoralInk = Color(0xFFE05A36)
    val Purple = Color(0xFF8B5CF6)
    val PurpleBg = Color(0xFFEFE9FE)
    val PurpleInk = Color(0xFF6D42E0)
    val Amber = Color(0xFFF5A524)
    val AmberBg = Color(0xFFFEF3DC)
    val AmberInk = Color(0xFFB7791F)
    val Green = Color(0xFF1E9E57)
    val GreenBg = Color(0xFFDFF6E8)
    val WhatsApp = Color(0xFF25D366)
    val Red = Color(0xFFD9483B)
    val Gold = Color(0xFFFFC857)
    val Blue = Color(0xFF3B82F6)

    val avatarColors = listOf(
        Coral, Purple, Amber, Color(0xFF14A3A0), Blue, Color(0xFFEC4899), Color(0xFF10B981), Color(0xFF6366F1),
    )

    fun avatar(i: Int): Color = avatarColors[((i % avatarColors.size) + avatarColors.size) % avatarColors.size]
}

fun EventType.color(): Color = when (this) {
    EventType.BIRTHDAY -> K.Coral
    EventType.ANNIVERSARY -> K.Purple
    EventType.CUSTOM -> K.Amber
}

fun EventType.bg(): Color = when (this) {
    EventType.BIRTHDAY -> K.CoralBg
    EventType.ANNIVERSARY -> K.PurpleBg
    EventType.CUSTOM -> K.AmberBg
}

fun EventType.ink(): Color = when (this) {
    EventType.BIRTHDAY -> K.CoralInk
    EventType.ANNIVERSARY -> K.PurpleInk
    EventType.CUSTOM -> K.AmberInk
}

val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_bold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
)

private fun TextStyle.p() = copy(fontFamily = Poppins)

private val typography = Typography().let { t ->
    Typography(
        displayLarge = t.displayLarge.p(), displayMedium = t.displayMedium.p(), displaySmall = t.displaySmall.p(),
        headlineLarge = t.headlineLarge.p(), headlineMedium = t.headlineMedium.p(), headlineSmall = t.headlineSmall.p(),
        titleLarge = t.titleLarge.p(), titleMedium = t.titleMedium.p(), titleSmall = t.titleSmall.p(),
        bodyLarge = t.bodyLarge.p(), bodyMedium = t.bodyMedium.p(), bodySmall = t.bodySmall.p(),
        labelLarge = t.labelLarge.p(), labelMedium = t.labelMedium.p(), labelSmall = t.labelSmall.p(),
    )
}

@Composable
fun KaalamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = K.Teal,
            onPrimary = Color.White,
            primaryContainer = K.TealLight,
            onPrimaryContainer = K.TealDark,
            secondary = K.TealDark,
            secondaryContainer = K.TealLight,
            onSecondaryContainer = K.TealDark,
            background = K.Bg,
            onBackground = K.Ink,
            surface = Color.White,
            onSurface = K.Ink,
            surfaceVariant = K.TealLight,
            onSurfaceVariant = K.Muted,
            surfaceContainer = Color.White,
            surfaceContainerHigh = Color.White,
            surfaceContainerHighest = K.Bg,
            outline = K.Line,
            outlineVariant = K.Line,
            error = K.Red,
        ),
        typography = typography,
        content = content,
    )
}

/** White status-bar icons over teal headers, dark icons over light screens. */
@Composable
fun StatusBarIcons(lightIcons: Boolean) {
    val view = LocalView.current
    DisposableEffect(lightIcons) {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !lightIcons
        }
        onDispose { }
    }
}
