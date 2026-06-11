package com.example.gymapp

import android.graphics.BitmapFactory
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymapp.ui.theme.*

@Composable
fun MuscleTag(group: String, modifier: Modifier = Modifier) {
    val color = muscleColor(group)
    Row(
        modifier = modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(99.dp))
            .padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(modifier = Modifier.size(6.dp).background(color, CircleShape))
        Text(group, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1, softWrap = false)
    }
}

@Composable
fun EquipTag(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = SubTextColor,
        modifier = modifier
            .background(SubtleFillColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

// ── Bottom Navigation ─────────────────────────────────────────────────────────

enum class NavItem { HOME, WORKOUT, HISTORY, PROGRESS, PROFILE }

private data class NavEntry(val item: NavItem, val icon: ImageVector, val label: String)

private val navEntries = listOf(
    NavEntry(NavItem.HOME,     Icons.Rounded.Home,          "Home"),
    NavEntry(NavItem.WORKOUT,  Icons.Rounded.FitnessCenter, "Workout"),
    NavEntry(NavItem.HISTORY,  Icons.Rounded.History,       "History"),
    NavEntry(NavItem.PROGRESS, Icons.Rounded.TrendingUp,    "Progress"),
    NavEntry(NavItem.PROFILE,  Icons.Rounded.Person,        "Profile"),
)

@Composable
fun BottomNav(active: NavItem, onNav: (NavItem) -> Unit) {
    Surface(
        color = BgElevColor,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawLine(LineColor, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1f)
                }
                .navigationBarsPadding()
                .padding(bottom = 4.dp),
        ) {
            val context = LocalContext.current
            navEntries.forEach { entry ->
                val on = active == entry.item
                // Selection feedback: pill tint + icon color spring in, icon pops
                val pillBg by animateColorAsState(
                    if (on) AccentSoftColor else Color.Transparent, Motion.effects(), label = "navPill",
                )
                val tint by animateColorAsState(
                    if (on) AccentColor else MutedColor, Motion.effects(), label = "navTint",
                )
                val iconScale by animateFloatAsState(
                    if (on) 1f else 0.9f, Motion.popFloat, label = "navScale",
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            if (!on) Haptics.tick(context)
                            onNav(entry.item)
                        }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .background(pillBg, RoundedCornerShape(99.dp))
                            .padding(horizontal = 14.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = entry.icon,
                            contentDescription = entry.label,
                            tint = tint,
                            modifier = Modifier.size(22.dp).scale(iconScale),
                        )
                    }
                    Text(
                        text = entry.label,
                        fontSize = 10.5.sp,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold,
                        color = tint,
                    )
                }
            }
        }
    }
}

// ── Detail top bar (with back button) ──────────────────────────────────────────

@Composable
fun DetailTopBar(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgColor)
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(CardColor)
                .clickable { onBack() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.ArrowBack, "Back", tint = TextColor, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextColor, maxLines = 1)
            if (subtitle != null) {
                Text(subtitle, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = SubTextColor, maxLines = 1)
            }
        }
        if (trailing != null) trailing()
    }
}

// ── Profile avatar (photo or initial) ───────────────────────────────────────────

@Composable
fun ProfileAvatar(
    size: Dp,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    val path = UserStore.photoPath
    val bitmap = remember(path) {
        if (path != null) runCatching { decodeScaledBitmap(path, 256) }.getOrNull() else null
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(AccentGradientColors))
            .border(1.dp, LineStrongColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Profile photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(UserStore.initial, fontSize = fontSize, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

private fun decodeScaledBitmap(path: String, reqPx: Int): androidx.compose.ui.graphics.ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
    var sample = 1
    while (maxDim > 0 && maxDim / sample > reqPx) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
}

// ── Styled text input ──────────────────────────────────────────────────────────

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(CardColor)
            .border(1.dp, LineColor, RoundedCornerShape(13.dp))
            .padding(horizontal = 14.dp, vertical = 15.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = singleLine,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            cursorBrush = SolidColor(AccentColor),
            textStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextColor),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(placeholder, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MutedColor)
                }
                inner()
            },
        )
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

fun fmtTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "$m:${s.toString().padStart(2, '0')}"
}
