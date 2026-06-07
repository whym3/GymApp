package com.example.gymapp

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymapp.ui.theme.*
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private const val STEP_GOAL = 10_000L

@Composable
fun StepsScreen(
    manager: HealthConnectManager,
    onBack: () -> Unit,
) {
    var daily by remember { mutableStateOf<List<Pair<LocalDate, Long>>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        daily = manager.readDailySteps(7)
        loaded = true
    }

    val today = daily.lastOrNull()?.second ?: 0L
    val goalPct = (today.toFloat() / STEP_GOAL).coerceIn(0f, 1f)

    Column(modifier = Modifier.fillMaxSize().background(BgColor)) {
        DetailTopBar(title = "Steps", subtitle = "Today", onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 4.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Hero
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    modifier = Modifier.size(54.dp).background(MuscleBack.copy(alpha = 0.13f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.DirectionsWalk, null, tint = MuscleBack, modifier = Modifier.size(28.dp))
                }
                Column {
                    Text(
                        if (daily.isEmpty() && !loaded) "—" else String.format(Locale.US, "%,d", today),
                        fontSize = 34.sp, fontWeight = FontWeight.Bold, color = TextColor,
                    )
                    Text("steps today", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MutedColor)
                }
            }

            // Goal progress
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, LineColor, RoundedCornerShape(16.dp))
                    .background(CardColor)
                    .padding(16.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Daily goal", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SubTextColor)
                    Text("${(goalPct * 100).toInt()}% of ${String.format(Locale.US, "%,d", STEP_GOAL)}", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = MutedColor)
                }
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(99.dp)).background(LineColor),
                ) {
                    Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(goalPct).background(MuscleBack, RoundedCornerShape(99.dp)))
                }
            }

            // Info
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, LineColor, RoundedCornerShape(16.dp))
                    .background(CardColor)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("About steps", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MutedColor, letterSpacing = 0.3.sp)
                Text(
                    "Steps are counted by your phone or watch and synced through Health Connect. " +
                        "Around 10,000 steps a day is a common target for general health, but any " +
                        "consistent increase over your own baseline is progress.",
                    fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = SubTextColor, lineHeight = 20.sp,
                )
            }

            // 7-day history
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.dp, LineColor, RoundedCornerShape(18.dp))
                    .background(CardColor)
                    .padding(16.dp),
            ) {
                Text("Last 7 days", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = TextColor)
                Spacer(Modifier.height(14.dp))
                if (daily.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (!loaded) "Loading…" else "No step data in Health Connect",
                            fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MutedColor, textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    StepsBarChart(daily)
                    Spacer(Modifier.height(10.dp))
                    val avg = if (daily.isNotEmpty()) daily.sumOf { it.second } / daily.size else 0L
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(modifier = Modifier.size(6.dp).background(GoodColor, CircleShape))
                        Text("Daily average ${String.format(Locale.US, "%,d", avg)} steps", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = MutedColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun StepsBarChart(daily: List<Pair<LocalDate, Long>>) {
    val maxVal = (daily.maxOfOrNull { it.second } ?: 1L).coerceAtLeast(1L)
    val animate by animateFloatAsState(targetValue = 1f, animationSpec = tween(800), label = "stepsBars")

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        daily.forEach { (day, steps) ->
            val frac = ((steps.toFloat() / maxVal) * animate).coerceIn(0.02f, 1f)
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (steps >= 1000) String.format(Locale.US, "%.1fk", steps / 1000f) else "$steps",
                    fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = SubTextColor, maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                // Fixed-height bar area so fillMaxHeight(frac) is bounded.
                Box(
                    modifier = Modifier.fillMaxWidth().height(90.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(frac)
                            .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                            .background(MuscleBack),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MutedColor,
                )
            }
        }
    }
}
