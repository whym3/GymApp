package com.example.gymapp

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymapp.ui.theme.*

@Composable
fun ActiveWorkoutScreen(
    exercises: List<WorkoutExercise>,
    elapsed: Int,
    running: Boolean,
    heartRate: Int?,
    rest: Int?,
    onFinish: () -> Unit,
    onTogglePause: () -> Unit,
    onOpenSearch: () -> Unit,
    onToggleOpen: (Int) -> Unit,
    onField: (exerciseIdx: Int, setIdx: Int, key: String, value: String) -> Unit,
    onToggleSet: (exerciseIdx: Int, setIdx: Int) -> Unit,
    onAddSet: (Int) -> Unit,
    onRemoveSet: (exerciseIdx: Int, setIdx: Int) -> Unit,
    onAddRest: () -> Unit,
    onSkipRest: () -> Unit,
) {
    val totalSets = exercises.sumOf { it.sets.size }
    val doneSets  = exercises.sumOf { ex -> ex.sets.count { it.done } }

    Box(modifier = Modifier.fillMaxSize().background(BgColor)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Top bar ──────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgColor)
                    .statusBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            if (running) "ACTIVE WORKOUT" else "PAUSED",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (running) MutedColor else AccentColor,
                            letterSpacing = 0.4.sp,
                        )
                        Spacer(Modifier.height(3.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(if (running) AccentColor else MutedColor, CircleShape),
                            )
                            Text(
                                fmtTime(elapsed),
                                fontSize = 25.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextColor,
                            )
                        }
                        if (heartRate != null) {
                            Spacer(Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier
                                    .background(MuscleChest.copy(alpha = 0.12f), RoundedCornerShape(99.dp))
                                    .padding(horizontal = 9.dp, vertical = 3.dp),
                            ) {
                                Icon(Icons.Rounded.Favorite, null, tint = MuscleChest, modifier = Modifier.size(12.dp))
                                Text("$heartRate bpm", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MuscleChest)
                            }
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(CardColor)
                                .border(1.dp, LineColor, CircleShape)
                                .clickable { onTogglePause() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (running) "Pause" else "Resume",
                                tint = TextColor,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Button(
                            onClick = onFinish,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 11.dp),
                        ) {
                            Text("Finish", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }

            // progress strip
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(SubtleFillColor),
            ) {
                if (totalSets > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(doneSets.toFloat() / totalSets)
                            .background(AccentColor),
                    )
                }
            }

            HorizontalDivider(color = LineColor, thickness = 1.dp)

            // ── Scrollable content ───────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .padding(bottom = if (rest != null) 96.dp else 0.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Search/add bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(13.dp))
                        .background(CardColor)
                        .border(1.dp, LineColor, RoundedCornerShape(13.dp))
                        .clickable { onOpenSearch() }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Rounded.Search, null, tint = MutedColor, modifier = Modifier.size(18.dp))
                    Text("Search & add exercise…", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MutedColor)
                }

                // Exercise cards
                exercises.forEachIndexed { ei, ex ->
                    ExerciseCard(
                        exercise = ex,
                        onToggleOpen = { onToggleOpen(ei) },
                        onField = { si, key, value -> onField(ei, si, key, value) },
                        onToggleSet = { si -> onToggleSet(ei, si) },
                        onAddSet = { onAddSet(ei) },
                        onRemoveSet = { si -> onRemoveSet(ei, si) },
                    )
                }

                // Add exercise button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(13.dp))
                        .border(1.dp, LineStrongColor, RoundedCornerShape(13.dp))
                        .background(SubtleFillColor)
                        .clickable { onOpenSearch() }
                        .padding(vertical = 13.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Add, null, tint = AccentColor, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("Add Exercise", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextColor)
                }
            }
        }

        // ── Rest timer pill ───────────────────────────────────────────────
        AnimatedVisibility(
            visible = rest != null,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit  = slideOutVertically(targetOffsetY  = { it }) + fadeOut(),
        ) {
            if (rest != null) {
                RestTimerPill(rest = rest, onAddRest = onAddRest, onSkipRest = onSkipRest)
            }
        }
    }
}

@Composable
private fun RestTimerPill(rest: Int, onAddRest: () -> Unit, onSkipRest: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardElevColor)
            .border(1.dp, AccentColor.copy(alpha = 0.33f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Rounded.Timer, null, tint = AccentColor, modifier = Modifier.size(38.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("REST TIMER", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = SubTextColor, letterSpacing = 0.3.sp)
            Text(fmtTime(rest), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextColor)
        }
        OutlinedButton(
            onClick = onAddRest,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextColor),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text("+15s", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Button(
            onClick = onSkipRest,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text("Skip", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
private fun ExerciseCard(
    exercise: WorkoutExercise,
    onToggleOpen: () -> Unit,
    onField: (setIdx: Int, key: String, value: String) -> Unit,
    onToggleSet: (Int) -> Unit,
    onAddSet: () -> Unit,
    onRemoveSet: (Int) -> Unit,
) {
    val muscleCol = muscleColor(exercise.group)
    val doneCount = exercise.sets.count { it.done }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, LineColor, RoundedCornerShape(18.dp))
            .background(CardColor),
    ) {
        // Card header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleOpen() }
                .padding(13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(muscleCol.copy(alpha = 0.12f), RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.FitnessCenter, null, tint = muscleCol, modifier = Modifier.size(19.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(exercise.name, fontSize = 15.5.sp, fontWeight = FontWeight.Bold, color = TextColor)
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    MuscleTag(exercise.group)
                    Text("$doneCount/${exercise.sets.size} sets", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MutedColor)
                }
            }
            Icon(
                if (exercise.open) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.ChevronRight,
                null,
                tint = SubTextColor,
                modifier = Modifier.size(18.dp),
            )
        }

        // Sets table
        if (exercise.open) {
            Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp)) {
                // Column headers
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Set",      textAlign = TextAlign.Center, modifier = Modifier.width(34.dp), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = MutedColor, letterSpacing = 0.6.sp)
                    Text("Previous",textAlign = TextAlign.Center, modifier = Modifier.weight(1f),   fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = MutedColor, letterSpacing = 0.6.sp)
                    Text("Kg",       textAlign = TextAlign.Center, modifier = Modifier.weight(1f),   fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = MutedColor, letterSpacing = 0.6.sp)
                    Text("Reps",     textAlign = TextAlign.Center, modifier = Modifier.weight(1f),   fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = MutedColor, letterSpacing = 0.6.sp)
                    Spacer(Modifier.width(66.dp)) // checkmark(30) + gap(8) + remove(28)
                }

                exercise.sets.forEachIndexed { si, set ->
                    SetRow(
                        index = si,
                        set = set,
                        canRemove = exercise.sets.size > 1,
                        onField = { key, value -> onField(si, key, value) },
                        onToggle = { onToggleSet(si) },
                        onRemove = { onRemoveSet(si) },
                    )
                }

                // Add Set row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, LineStrongColor, RoundedCornerShape(10.dp))
                        .clickable { onAddSet() }
                        .padding(vertical = 9.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Add, null, tint = SubTextColor, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add Set", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SubTextColor)
                }
            }
        }
    }
}

@Composable
private fun SetRow(
    index: Int,
    set: SetData,
    canRemove: Boolean,
    onField: (key: String, value: String) -> Unit,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Set number badge
        Box(
            modifier = Modifier
                .width(34.dp)
                .height(26.dp)
                .background(
                    if (set.done) AccentColor.copy(alpha = 0.13f) else SubtleFillColor,
                    RoundedCornerShape(8.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${index + 1}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (set.done) AccentColor else SubTextColor,
            )
        }

        // Previous
        Text(
            set.prev.ifEmpty { "—" },
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = MutedColor,
        )

        // Weight input
        SetInput(
            value = set.weight,
            placeholder = "0",
            done = set.done,
            onValueChange = { onField("weight", it) },
            modifier = Modifier.weight(1f),
        )

        // Reps dropdown (5–20)
        RepsDropdown(
            value = set.reps,
            done = set.done,
            onSelect = { onField("reps", it) },
            modifier = Modifier.weight(1f),
        )

        // Done checkmark
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (set.done) GoodColor else Color.Transparent)
                .border(
                    if (set.done) 0.dp else 1.5.dp,
                    if (set.done) Color.Transparent else LineStrongColor,
                    RoundedCornerShape(9.dp),
                )
                .clickable { onToggle() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Check,
                null,
                tint = if (set.done) BgColor else MutedColor,
                modifier = Modifier.size(16.dp),
            )
        }

        // Remove set button — hidden when only one set remains
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .then(if (canRemove) Modifier.clickable { onRemove() } else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = "Remove set",
                tint = if (canRemove) MutedColor else Color.Transparent,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

@Composable
private fun RepsDropdown(
    value: String,
    done: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (done) Color.Transparent else SubtleFillColor)
                .clickable { expanded = true },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                value.ifEmpty { "0" },
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                    value.isEmpty() -> MutedColor
                    done -> SubTextColor
                    else -> TextColor
                },
            )
            Icon(Icons.Rounded.ArrowDropDown, null, tint = MutedColor, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            (5..20).forEach { n ->
                DropdownMenuItem(
                    text = { Text("$n reps", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextColor) },
                    onClick = { onSelect(n.toString()); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun SetInput(
    value: String,
    placeholder: String,
    done: Boolean,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = { onValueChange(it.filter { c -> c.isDigit() || c == '.' }) },
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (done) Color.Transparent else SubtleFillColor),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        cursorBrush = SolidColor(AccentColor),
        textStyle = TextStyle(
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Bold,
            color = if (done) SubTextColor else TextColor,
            textAlign = TextAlign.Center,
        ),
        decorationBox = { inner ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MutedColor,
                        textAlign = TextAlign.Center,
                    )
                }
                inner()
            }
        },
    )
}
