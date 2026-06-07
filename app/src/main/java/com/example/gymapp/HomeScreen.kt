package com.example.gymapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymapp.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    today: TodayStats,
    hcConnected: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onStartEmpty: () -> Unit,
    onTemplate: (WorkoutTemplate) -> Unit,
    onOpenWorkout: (SavedWorkout) -> Unit,
    onProfile: () -> Unit,
    onSeeAllHistory: () -> Unit,
    onHeartRate: () -> Unit,
    onSteps: () -> Unit,
) {
    val recent = WorkoutRepository.workouts.take(4)
    val streak = WorkoutRepository.currentStreak()
    val todayLabel = remember { SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date()) }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize().background(BgColor),
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(bottom = 16.dp),
    ) {
        // ── Greeting header ──────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(todayLabel, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SubTextColor)
                Spacer(Modifier.height(2.dp))
                Text("Hey, ${UserStore.displayName} 👋", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextColor)
            }
            Box(modifier = Modifier.clickable { onProfile() }) {
                ProfileAvatar(size = 46.dp, fontSize = 17.sp)
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .align(Alignment.TopEnd)
                        .background(AccentColor, CircleShape)
                        .border(2.dp, BgColor, CircleShape),
                )
            }
        }

        // ── Streak badge ─────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .background(AccentSoftColor, RoundedCornerShape(99.dp))
                .border(1.dp, AccentColor.copy(alpha = 0.2f), RoundedCornerShape(99.dp))
                .padding(horizontal = 13.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Rounded.LocalFireDepartment, null, tint = AccentColor, modifier = Modifier.size(16.dp))
            if (streak >= 1) {
                Text("$streak day streak", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AccentColor)
                Text("· keep it going", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SubTextColor)
            } else {
                Text("Start your streak", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AccentColor)
                Text("· train ${WEEKLY_GOAL_DAYS}x this week", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SubTextColor)
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Start Workout CTA ─────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(AccentGradientColors))
                .clickable { onStartEmpty() }
                .padding(horizontal = 18.dp, vertical = 17.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Start Workout", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(
                        "Empty session · log as you go",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.White.copy(alpha = 0.22f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Today stats ───────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Today", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextColor)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(modifier = Modifier.size(6.dp).background(if (hcConnected) GoodColor else MutedColor, CircleShape))
                Text(
                    if (hcConnected) "Health Connect" else "Not connected",
                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MutedColor,
                )
            }
        }

        Spacer(Modifier.height(11.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatCard(
                icon = Icons.Rounded.DirectionsWalk, label = "Steps", value = today.steps, unit = "",
                accent = MuscleBack, modifier = Modifier.weight(1f), onClick = onSteps,
            )
            StatCard(
                icon = Icons.Rounded.LocalFireDepartment, label = "Active", value = today.calories, unit = "kcal",
                accent = MuscleArms, modifier = Modifier.weight(1f),
            )
            StatCard(
                icon = Icons.Rounded.MonitorHeart, label = "Heart rate", value = today.heartRate, unit = "bpm",
                accent = MuscleChest, modifier = Modifier.weight(1f), onClick = onHeartRate,
            )
        }

        Spacer(Modifier.height(22.dp))

        // ── Quick-start templates ─────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Quick start", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextColor)
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            mockTemplates.forEach { template ->
                TemplateCard(template = template, onClick = { onTemplate(template) })
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── Recent workouts ───────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Recent workouts", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextColor)
            if (recent.isNotEmpty()) {
                Text("See all", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = AccentColor, modifier = Modifier.clickable { onSeeAllHistory() })
            }
        }

        Spacer(Modifier.height(12.dp))

        if (recent.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, LineColor, RoundedCornerShape(16.dp))
                    .background(CardColor)
                    .padding(vertical = 26.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Rounded.FitnessCenter, null, tint = MutedColor, modifier = Modifier.size(26.dp))
                Spacer(Modifier.height(10.dp))
                Text("No workouts logged yet", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SubTextColor)
                Text("Tap Start Workout to log your first session", fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = MutedColor)
            }
        } else {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                recent.forEach { w -> RecentWorkoutCard(w, onClick = { onOpenWorkout(w) }) }
            }
        }
    }
    }
}

@Composable
private fun StatCard(
    icon: ImageVector, label: String, value: String, unit: String,
    accent: Color, modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    // Subtle tint of the metric's accent fading into the card surface — works in both themes.
    val gradient = Brush.verticalGradient(listOf(accent.copy(alpha = 0.16f), CardColor))
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(gradient, RoundedCornerShape(18.dp))
            .border(1.dp, LineColor, RoundedCornerShape(18.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(13.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier.size(30.dp).background(accent.copy(alpha = 0.13f), RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(17.dp))
            }
            if (onClick != null) {
                Icon(Icons.Rounded.ChevronRight, null, tint = MutedColor, modifier = Modifier.size(15.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(value, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = TextColor)
            if (unit.isNotEmpty())
                Text(unit, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = SubTextColor, modifier = Modifier.padding(bottom = 2.dp))
        }
        Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = MutedColor)
    }
}

@Composable
private fun TemplateCard(template: WorkoutTemplate, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(138.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(cardGradientColors))
            .border(1.dp, LineColor, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            template.groups.forEach { group ->
                Box(modifier = Modifier.size(8.dp).background(muscleColor(group), CircleShape))
            }
        }
        Spacer(Modifier.height(26.dp))
        Text(template.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextColor)
        Text("${template.count} exercises", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MutedColor)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecentWorkoutCard(w: SavedWorkout, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardColor)
            .border(1.dp, LineColor, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column {
                Text(w.title, fontSize = 15.5.sp, fontWeight = FontWeight.Bold, color = TextColor)
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(formatWorkoutDate(w.dateMillis), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = MutedColor)
                    Box(modifier = Modifier.size(3.dp).background(MutedColor, CircleShape))
                    Icon(Icons.Rounded.AccessTime, null, tint = MutedColor, modifier = Modifier.size(13.dp))
                    Text(formatDuration(w.durationSec), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = MutedColor)
                }
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = MutedColor, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(11.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            w.muscleGroups.forEach { group -> MuscleTag(group) }
        }
    }
}
