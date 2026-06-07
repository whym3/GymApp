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

@Composable
fun CreateRoutineScreen(
    onBack: () -> Unit,
    onSave: (Routine) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    val exercises = remember { mutableStateListOf<ExerciseLibraryItem>() }
    var showSearch by remember { mutableStateOf(false) }

    val canSave = name.isNotBlank() && exercises.isNotEmpty()

    Box(modifier = Modifier.fillMaxSize().background(BgColor)) {
        Column(modifier = Modifier.fillMaxSize()) {
            DetailTopBar(title = "New Routine", onBack = onBack)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 4.dp, bottom = 16.dp),
            ) {
                Text("Routine name", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SubTextColor)
                Spacer(Modifier.height(8.dp))
                AppTextField(value = name, onValueChange = { name = it }, placeholder = "e.g. Upper Body A", modifier = Modifier.fillMaxWidth())

                Spacer(Modifier.height(22.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Exercises", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextColor)
                    if (exercises.isNotEmpty()) {
                        Text("${exercises.size} added", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = MutedColor)
                    }
                }

                Spacer(Modifier.height(12.dp))

                if (exercises.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, LineColor, RoundedCornerShape(16.dp))
                            .background(CardColor)
                            .padding(vertical = 26.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Rounded.FitnessCenter, null, tint = MutedColor, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.height(10.dp))
                        Text("No exercises yet", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SubTextColor)
                        Text("Add exercises to build your routine", fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = MutedColor, textAlign = TextAlign.Center)
                    }
                } else {
                    exercises.forEachIndexed { i, ex ->
                        val col = muscleColor(ex.group)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.dp, LineColor, RoundedCornerShape(14.dp))
                                .background(CardColor)
                                .padding(horizontal = 13.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(13.dp),
                        ) {
                            Box(
                                modifier = Modifier.size(34.dp).background(col.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Rounded.FitnessCenter, null, tint = col, modifier = Modifier.size(17.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(ex.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextColor)
                                Spacer(Modifier.height(3.dp))
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    Text(ex.group, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = col)
                                    Box(modifier = Modifier.size(3.dp).background(MutedColor, CircleShape))
                                    EquipTag(ex.equip)
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .clickable { exercises.removeAt(i) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Rounded.Close, "Remove", tint = MutedColor, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(13.dp))
                        .border(1.dp, LineStrongColor, RoundedCornerShape(13.dp))
                        .background(SubtleFillColor)
                        .clickable { showSearch = true }
                        .padding(vertical = 13.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Add, null, tint = AccentColor, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("Add Exercise", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextColor)
                }
            }

            HorizontalDivider(color = LineColor, thickness = 1.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgColor)
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            ) {
                Button(
                    onClick = {
                        onSave(
                            Routine(
                                id = System.currentTimeMillis(),
                                name = name.trim(),
                                exercises = exercises.toList(),
                            )
                        )
                    },
                    enabled = canSave,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentColor,
                        disabledContainerColor = CardElevColor,
                    ),
                    contentPadding = PaddingValues(vertical = 15.dp),
                ) {
                    Text(
                        "Save Routine",
                        fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        color = if (canSave) Color.White else MutedColor,
                    )
                }
            }
        }

        if (showSearch) {
            ExerciseSearchSheet(
                onClose = { showSearch = false },
                onAdd = { lib -> if (exercises.none { it.name == lib.name }) exercises.add(lib) },
            )
        }
    }
}
