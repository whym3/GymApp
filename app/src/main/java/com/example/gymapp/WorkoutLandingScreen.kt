package com.example.gymapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymapp.ui.theme.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkoutLandingScreen(
    onStartEmpty: () -> Unit,
    onTemplate: (WorkoutTemplate) -> Unit,
    onRepeat: (SavedWorkout) -> Unit,
    onCreateRoutine: () -> Unit,
    onEditRoutine: (Routine) -> Unit,
) {
    val last = WorkoutRepository.workouts.firstOrNull()
    val routines = RoutineRepository.routines

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 14.dp, bottom = 16.dp),
    ) {
        Text("Start a workout", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextColor)
        Spacer(Modifier.height(2.dp))
        Text("Pick a routine or start from scratch", fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = SubTextColor)

        Spacer(Modifier.height(18.dp))

        // Start empty
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(AccentGradientColors))
                .clickable { onStartEmpty() }
                .padding(18.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    Text("Empty Workout", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Add exercises as you go", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.85f))
                }
                Box(
                    modifier = Modifier.size(40.dp).background(Color.White.copy(alpha = 0.22f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }

        // Repeat last
        if (last != null) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, LineColor, RoundedCornerShape(16.dp))
                    .background(CardColor)
                    .clickable { onRepeat(last) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                Box(
                    modifier = Modifier.size(40.dp).background(AccentSoftColor, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Refresh, null, tint = AccentColor, modifier = Modifier.size(20.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Repeat last workout", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextColor)
                    Text("${last.title} · ${last.exercises.size} exercises", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = MutedColor, maxLines = 1)
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = MutedColor, modifier = Modifier.size(18.dp))
            }
        }

        // ── My Routines ────────────────────────────────────────────────────
        Spacer(Modifier.height(26.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("My routines", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextColor)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(AccentSoftColor)
                    .clickable { onCreateRoutine() }
                    .padding(horizontal = 11.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(Icons.Rounded.Add, null, tint = AccentColor, modifier = Modifier.size(15.dp))
                Text("Create", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = AccentColor)
            }
        }
        Spacer(Modifier.height(12.dp))

        if (routines.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, LineColor, RoundedCornerShape(16.dp))
                    .background(CardColor)
                    .clickable { onCreateRoutine() }
                    .padding(vertical = 22.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Rounded.AddCircleOutline, null, tint = AccentColor, modifier = Modifier.size(28.dp))
                Spacer(Modifier.height(10.dp))
                Text("Create your first routine", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextColor)
                Text("Save your own workout schedules to start in one tap", fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = MutedColor)
            }
        } else {
            routines.forEach { routine ->
                PlanCard(
                    title = routine.name,
                    meta = "${routine.exercises.size} exercises · ${routine.groups.size} group${if (routine.groups.size == 1) "" else "s"}",
                    groups = routine.groups,
                    onClick = { onTemplate(routine.toTemplate()) },
                    onEdit = { onEditRoutine(routine) },
                )
            }
        }

        // ── Built-in templates ─────────────────────────────────────────────
        Spacer(Modifier.height(26.dp))
        Text("Templates", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextColor)
        Spacer(Modifier.height(12.dp))

        mockTemplates.forEach { template ->
            PlanCard(
                title = template.name,
                meta = "${template.count} exercises · ${template.groups.size} groups",
                groups = template.groups,
                onClick = { onTemplate(template) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanCard(
    title: String,
    meta: String,
    groups: List<String>,
    onClick: () -> Unit,
    onEdit: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, LineColor, RoundedCornerShape(16.dp))
            .background(CardColor)
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextColor, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(meta, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = MutedColor, maxLines = 1)
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                groups.forEach { MuscleTag(it) }
            }
        }
        if (onEdit != null) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SubtleFillColor)
                    .clickable { onEdit() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Edit, "Edit routine", tint = SubTextColor, modifier = Modifier.size(16.dp))
            }
        } else {
            Icon(Icons.Rounded.ChevronRight, null, tint = MutedColor, modifier = Modifier.size(20.dp))
        }
    }
}
