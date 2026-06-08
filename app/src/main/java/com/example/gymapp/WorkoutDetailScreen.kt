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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymapp.ui.theme.*

@Composable
fun WorkoutDetailScreen(
    workout: SavedWorkout,
    onBack: () -> Unit,
    onDelete: (SavedWorkout) -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(BgColor)) {
        DetailTopBar(
            title = workout.title,
            subtitle = formatWorkoutDate(workout.dateMillis),
            onBack = onBack,
            trailing = {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(CardColor)
                        .clickable { showDeleteConfirm = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.DeleteOutline, "Delete", tint = MuscleChest, modifier = Modifier.size(19.dp))
                }
            },
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = 4.dp, bottom = 20.dp),
        ) {
            // Stats row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.dp, LineColor, RoundedCornerShape(18.dp))
                    .background(CardColor),
            ) {
                DetailStat(Icons.Rounded.AccessTime, formatDuration(workout.durationSec), "Duration", MuscleBack, Modifier.weight(1f))
                Box(modifier = Modifier.width(1.dp).height(70.dp).background(LineColor).align(Alignment.CenterVertically))
                DetailStat(Icons.Rounded.Layers, "${workout.totalSets}", "Sets", AccentColor, Modifier.weight(1f))
                Box(modifier = Modifier.width(1.dp).height(70.dp).background(LineColor).align(Alignment.CenterVertically))
                DetailStat(Icons.Rounded.FitnessCenter, formatVolume(workout.totalVolumeKg), "Volume", GoodColor, Modifier.weight(1f))
            }

            workout.avgHeartRate?.let { hr ->
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, LineColor, RoundedCornerShape(14.dp))
                        .background(CardColor)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier.size(36.dp).background(MuscleChest.copy(alpha = 0.13f), RoundedCornerShape(11.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.MonitorHeart, null, tint = MuscleChest, modifier = Modifier.size(19.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Avg heart rate", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SubTextColor)
                        Text("Recorded from Health Connect", fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = MutedColor)
                    }
                    Text("$hr bpm", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextColor)
                }
            }

            Spacer(Modifier.height(22.dp))
            Text("Exercises", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextColor)
            Spacer(Modifier.height(12.dp))

            workout.exercises.forEach { ex ->
                val col = muscleColor(ex.group)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, LineColor, RoundedCornerShape(16.dp))
                        .background(CardColor)
                        .padding(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                        Box(
                            modifier = Modifier.size(36.dp).background(col.copy(alpha = 0.12f), RoundedCornerShape(11.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.FitnessCenter, null, tint = col, modifier = Modifier.size(18.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(ex.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextColor)
                            Spacer(Modifier.height(4.dp))
                            MuscleTag(ex.group)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    ex.sets.forEachIndexed { i, s ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                modifier = Modifier.size(24.dp).background(SubtleFillColor, RoundedCornerShape(7.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("${i + 1}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SubTextColor)
                            }
                            Text(
                                if (s.weight.isBlank() && s.reps.isBlank()) "—"
                                else "${s.weight.ifBlank { "0" }} kg × ${s.reps.ifBlank { "0" }} reps",
                                fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = TextColor,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = CardElevColor,
            titleContentColor = TextColor,
            textContentColor = SubTextColor,
            title = { Text("Delete workout?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete this workout? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete(workout) }) {
                    Text("Delete", color = MuscleChest, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = SubTextColor, fontWeight = FontWeight.SemiBold)
                }
            },
        )
    }
}

@Composable
private fun DetailStat(icon: ImageVector, value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(vertical = 14.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(19.dp))
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextColor)
        Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = MutedColor)
    }
}
