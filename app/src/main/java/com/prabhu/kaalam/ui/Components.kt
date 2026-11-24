@file:Suppress("DEPRECATION")

package com.prabhu.kaalam.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prabhu.kaalam.data.Person
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

val HeaderBrush = Brush.linearGradient(listOf(K.TealMid, K.TealDark))

@Composable
fun KCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(14.dp),
    color: Color = Color.White,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .shadow(6.dp, shape, ambientColor = K.TealDark.copy(alpha = 0.18f), spotColor = K.TealDark.copy(alpha = 0.18f))
            .clip(shape)
            .background(color)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), color = K.TealDark, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.5.sp, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(action, color = K.Teal, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.clickable { onAction?.invoke() })
        }
    }
}

@Composable
fun Avatar(name: String, colorIndex: Int, size: Dp = 42.dp, corner: Dp = 14.dp, fontSize: Int = 16) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(corner)).background(K.avatar(colorIndex)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().firstOrNull()?.uppercase() ?: "?",
            color = Color.White, fontWeight = FontWeight.Bold, fontSize = fontSize.sp,
        )
    }
}

@Composable
fun PersonAvatar(p: Person, size: Dp = 42.dp) = Avatar(p.name, p.color, size)

@Composable
fun Pill(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier) {
    Text(
        text, color = fg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
        modifier = modifier.clip(RoundedCornerShape(10.dp)).background(bg).padding(horizontal = 9.dp, vertical = 3.dp),
    )
}

@Composable
fun ThinDivider() = HorizontalDivider(color = K.Line, thickness = 1.dp)

@Composable
fun ToggleRow(title: String, subtitle: String? = null, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!checked) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = if (enabled) K.Ink else K.Faint)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = K.Muted)
        }
        Switch(
            checked = checked, onCheckedChange = onChange, enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = K.Teal, uncheckedTrackColor = Color(0xFFCBD7D9), uncheckedThumbColor = Color.White, uncheckedBorderColor = Color.Transparent),
        )
    }
}

@Composable
fun NavRow(icon: ImageVector, iconBg: Color, title: String, subtitle: String? = null, iconTint: Color = K.TealDark, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(iconBg), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = K.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = K.Faint)
    }
}

@Composable
fun ValueRow(title: String, subtitle: String? = null, value: String, valueColor: Color = K.Teal, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = K.Muted)
        }
        Text(value, color = valueColor, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

@Composable
fun SquareIconButton(icon: ImageVector, light: Boolean = false, contentDescription: String? = null, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
            .background(if (light) Color.White.copy(alpha = 0.16f) else Color.White)
            .then(if (light) Modifier else Modifier.border(1.dp, K.Line, RoundedCornerShape(12.dp)))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = if (light) Color.White else K.TealDark, modifier = Modifier.size(20.dp))
    }
}

/** Plain top bar for the light sub-screens. */
@Composable
fun TopBar(title: String, onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit = {}) {
    StatusBarIcons(lightIcons = false)
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) SquareIconButton(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", onClick = onBack)
        else Spacer(Modifier.width(4.dp))
        Text(
            title, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = K.TealDark,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        actions()
    }
}

/** Teal gradient header with rounded bottom, drawn behind the status bar. */
@Composable
fun GradientHeader(bottomPadding: Dp = 24.dp, content: @Composable ColumnScope.() -> Unit) {
    StatusBarIcons(lightIcons = true)
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp))
            .background(HeaderBrush)
            .statusBarsPadding()
            .padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = bottomPadding),
        content = content,
    )
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false, color: Color = K.Teal, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled && !loading, modifier = modifier.height(50.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
        else Text(text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
fun SoftButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier.height(50.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = K.TealLight, contentColor = K.Teal),
        elevation = null,
    ) { Text(text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
}

@Composable
fun KChip(text: String, selected: Boolean, color: Color = K.Teal, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
        color = if (selected) Color.White else K.Muted,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) color else Color.White)
            .border(1.dp, if (selected) color else K.Line, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
fun FieldLabel(text: String) = Text(text, fontSize = 12.sp, color = K.Muted, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 2.dp, top = 6.dp, bottom = 6.dp))

@Composable
fun kFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = K.Teal, unfocusedBorderColor = K.Line,
    focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
    focusedLabelColor = K.Teal,
)

/** A tappable box that looks like a text field. */
@Composable
fun PickerField(label: String, value: String, modifier: Modifier = Modifier, borderColor: Color = K.Line, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(Color.White)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(label, fontSize = 11.sp, color = K.Muted)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
fun Long.utcMillisToDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@Composable
fun KDatePickerDialog(initial: LocalDate?, title: String = "Pick a date", onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = (initial ?: LocalDate.now()).toUtcMillis(),
        yearRange = 1900..2100,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(it.utcMillisToDate()) }
                onDismiss()
            }) { Text("OK", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state, title = { Text(title, modifier = Modifier.padding(start = 24.dp, top = 16.dp)) })
    }
}

// ---------------- PIN ----------------

@Composable
fun PinDots(filled: Int, total: Int = 6, error: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(total) { i ->
            val on = i < filled
            Box(
                Modifier.size(15.dp).clip(CircleShape)
                    .background(if (on) (if (error) K.Red else K.Teal) else Color.Transparent)
                    .border(2.dp, if (on) Color.Transparent else Color(0xFFC8D6D8), CircleShape),
            )
        }
    }
}

@Composable
fun PinPad(onDigit: (Char) -> Unit, onBackspace: () -> Unit, onFingerprint: (() -> Unit)? = null) {
    val rows = listOf("123", "456", "789")
    Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { r ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                r.forEach { c -> PadKey(c.toString()) { onDigit(c) } }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Box(Modifier.size(72.dp).clip(CircleShape).clickable(enabled = onFingerprint != null) { onFingerprint?.invoke() }, contentAlignment = Alignment.Center) {
                if (onFingerprint != null) Icon(Icons.Rounded.Fingerprint, "Fingerprint", tint = K.Teal, modifier = Modifier.size(32.dp))
            }
            PadKey("0") { onDigit('0') }
            Box(Modifier.size(72.dp).clip(CircleShape).clickable(onClick = onBackspace), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Backspace, "Delete", tint = K.Muted)
            }
        }
    }
}

@Composable
private fun PadKey(label: String, onClick: () -> Unit) {
    Box(Modifier.size(72.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, fontSize = 26.sp, fontWeight = FontWeight.Medium, color = K.Ink)
    }
}

// ---------------- Logo ----------------

/** The Kaalam mark: a 3/4 ring (time) around a birthday cake. */
@Composable
fun AppLogo(size: Dp, background: Boolean = true, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        if (background) {
            drawRoundRect(
                brush = Brush.linearGradient(listOf(Color(0xFF17929D), K.TealDark)),
                cornerRadius = CornerRadius(s * 0.29f, s * 0.29f),
            )
        }
        val r = s * 0.29f
        val c = Offset(s / 2, s * 0.52f)
        val sw = s * 0.06f
        drawCircle(Color.White.copy(alpha = 0.25f), radius = r, center = c, style = Stroke(sw))
        drawArc(
            Color.White, startAngle = -90f, sweepAngle = 270f, useCenter = false,
            topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2), style = Stroke(sw, cap = StrokeCap.Round),
        )
        // cake
        val cw = s * 0.24f; val ch = s * 0.15f
        val top = c.y - s * 0.0f
        drawRoundRect(Color.White, topLeft = Offset(c.x - cw / 2, top), size = Size(cw, ch), cornerRadius = CornerRadius(s * 0.035f))
        drawRect(K.TealMid, topLeft = Offset(c.x - cw / 2, top + ch * 0.32f), size = Size(cw, ch * 0.18f))
        // candle
        drawRect(Color.White, topLeft = Offset(c.x - s * 0.014f, top - s * 0.1f), size = Size(s * 0.028f, s * 0.1f))
        // flame
        val f = Path().apply {
            moveTo(c.x, top - s * 0.18f)
            cubicTo(c.x + s * 0.04f, top - s * 0.14f, c.x + s * 0.03f, top - s * 0.11f, c.x, top - s * 0.105f)
            cubicTo(c.x - s * 0.03f, top - s * 0.11f, c.x - s * 0.04f, top - s * 0.14f, c.x, top - s * 0.18f)
            close()
        }
        drawPath(f, K.Gold)
    }
}

@Composable
fun EmptyState(emoji: String, title: String, text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 44.sp)
        Spacer(Modifier.height(8.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = K.TealDark, textAlign = TextAlign.Center)
        Text(text, fontSize = 13.sp, color = K.Muted, textAlign = TextAlign.Center)
    }
}

fun timeAgo(millis: Long): String {
    if (millis <= 0) return "never"
    val diff = (System.currentTimeMillis() - millis) / 1000
    return when {
        diff < 60 -> "just now"
        diff < 3600 -> "${diff / 60} min ago"
        diff < 86400 -> "${diff / 3600} h ago"
        else -> java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault())
            .toLocalDate().format(com.prabhu.kaalam.data.Texts.dMMMyyyy)
    }
}
