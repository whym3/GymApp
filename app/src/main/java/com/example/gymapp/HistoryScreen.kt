package com.example.gymapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymapp.ui.theme.*

@Composable
fun HistoryScreen(onOpenWorkout: (SavedWorkout) -> Unit) {
    val workouts = WorkoutRepository.workouts

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("History", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = TextColor)
            if (workouts.isNotEmpty()) {
                Text(
                    "${workouts.size} workout${if (workouts.size == 1) "" else "s"}",
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MutedColor,
                )
            }
        }

        if (workouts.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier.size(64.dp).background(CardColor, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.History, null, tint = MutedColor, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text("No workouts yet", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextColor)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Finish and save a workout and it'll show up here for you to browse.",
                    fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = SubTextColor, textAlign = TextAlign.Center, lineHeight = 20.sp,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(workouts, key = { it.id }) { w ->
                    HistoryCard(w, onClick = { onOpenWorkout(w) })
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryCard(w: SavedWorkout, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, LineColor, RoundedCornerShape(16.dp))
            .background(CardColor)
            .clickable { onClick() }
            .padding(15.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column {
                Text(w.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextColor)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(formatWorkoutDate(w.dateMillis), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = MutedColor)
                    Box(modifier = Modifier.size(3.dp).background(MutedColor, CircleShape))
                    Icon(Icons.Rounded.AccessTime, null, tint = MutedColor, modifier = Modifier.size(13.dp))
                    Text(formatDuration(w.durationSec), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = MutedColor)
                    Box(modifier = Modifier.size(3.dp).background(MutedColor, CircleShape))
                    Text(formatVolume(w.totalVolumeKg), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = MutedColor)
                }
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = MutedColor, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(11.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            w.muscleGroups.forEach { MuscleTag(it) }
        }
    }
}
