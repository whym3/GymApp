package com.example.gymapp

import android.os.SystemClock
import androidx.compose.animation.*
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymapp.ui.theme.*

private val WarmupAmber = Color(0xFFF5A524)
private val FailureRed = Color(0xFFE5484D)

/** Live HR mapped to an intensity tint for the header. Resting → brand accent. */
private fun hrZoneColor(bpm: Int?): Color = when {
    bpm == null -> AccentColor
    bpm < 100 -> AccentColor
    bpm < 125 -> GoodColor
    bpm < 150 -> WarmupAmber
    else -> MuscleChest
}

@Composable
fun ActiveWorkoutScreen(
    exercises: List<WorkoutExercise>,
    running: Boolean,
    heartRate: Int?,
    steps: Long?,
    calories: Double?,
    restDeadlineMs: Long?,
    restTotalMs: Long?,
    onFinish: () -> Unit,
    onTogglePause: () -> Unit,
    onOpenSearch: () -> Unit,
    onToggleOpen: (Int) -> Unit,
    onField: (exerciseIdx: Int, setIdx: Int, key: String, value: String) -> Unit,
    onToggleSet: (exerciseIdx: Int, setIdx: Int) -> Unit,
    onSetType: (exerciseIdx: Int, setIdx: Int, type: SetType) -> Unit,
    onAddSet: (Int) -> Unit,
    onRemoveSet: (exerciseIdx: Int, setIdx: Int) -> Unit,
    onAddRest: () -> Unit,
    onSkipRest: () -> Unit,
) {
    // Warmup sets don't count toward workout progress.
    val totalSets = exercises.sumOf { ex -> ex.sets.count { it.type != SetType.WARMUP } }
    val doneSets  = exercises.sumOf { ex -> ex.sets.count { it.done && it.type != SetType.WARMUP } }
    val resting = restDeadlineMs != null

    Box(modifier = Modifier.fillMaxSize().background(BgColor)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Top bar — tinted by live HR intensity ────────────────────
            val zone by animateColorAsState(hrZoneColor(heartRate), Motion.drift(), label = "hrZone")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(listOf(zone.copy(alpha = 0.13f), Color.Transparent)),
                    )
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
                        // The 1 Hz clock lives in its own composable reading
                        // WorkoutTimer directly, so ticks repaint only this text.
                        SessionClock(running = running)
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
                if (heartRate != null || steps != null || calories != null) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (heartRate != null) {
                            LiveStatChip(Icons.Rounded.Favorite, "$heartRate bpm", MuscleChest)
                        }
                        if (steps != null) {
                            LiveStatChip(Icons.Rounded.DirectionsWalk, "$steps steps", MuscleBack)
                        }
                        if (calories != null) {
                            LiveStatChip(Icons.Rounded.LocalFireDepartment, "${calories.toInt()} kcal", WarmupAmber)
                        }
                    }
                }
            }

            // Progress strip — fraction springs to its new value on each set done
            val progress by animateFloatAsState(
                if (totalSets > 0) doneSets.toFloat() / totalSets else 0f,
                Motion.spatialFloat,
                label = "progress",
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(SubtleFillColor),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .background(AccentColor),
                )
            }

            HorizontalDivider(color = LineColor, thickness = 1.dp)

            // ── Exercise list — lazy + keyed, so timer ticks and keystrokes
            //    only touch the rows that actually changed ─────────────────
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = 14.dp,
                    bottom = if (resting) 110.dp else 14.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "search") {
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
                }

                itemsIndexed(exercises, key = { _, ex -> ex.id }) { ei, ex ->
                    ExerciseCard(
                        exercise = ex,
                        onToggleOpen = { onToggleOpen(ei) },
                        onField = { si, key, value -> onField(ei, si, key, value) },
                        onToggleSet = { si -> onToggleSet(ei, si) },
                        onSetType = { si, type -> onSetType(ei, si, type) },
                        onAddSet = { onAddSet(ei) },
                        onRemoveSet = { si -> onRemoveSet(ei, si) },
                        modifier = Modifier.animateItem(
                            fadeInSpec = Motion.effectsFloat,
                            placementSpec = Motion.spatialOffset,
                            fadeOutSpec = Motion.effectsFloat,
                        ),
                    )
                }

                item(key = "add") {
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
        }

        // ── Rest timer pill ───────────────────────────────────────────────
        AnimatedVisibility(
            visible = resting,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            enter = slideInVertically(Motion.spatialOffset) { it } + fadeIn(Motion.effectsFloat),
            exit  = slideOutVertically(Motion.spatialOffset) { it } + fadeOut(Motion.effectsFloat),
        ) {
            if (restDeadlineMs != null && restTotalMs != null) {
                RestTimerPill(
                    deadlineMs = restDeadlineMs,
                    totalMs = restTotalMs,
                    onAddRest = onAddRest,
                    onSkipRest = onSkipRest,
                )
            }
        }
    }
}

/**
 * The big elapsed-time readout plus its status dot. Reads [WorkoutTimer]
 * directly so the once-a-second tick invalidates only this composable, not the
 * whole screen — the rest of the UI recomposes only on real state changes.
 */
@Composable
private fun SessionClock(running: Boolean) {
    val timer by WorkoutTimer.state.collectAsState()
    val pulse = rememberInfiniteTransition(label = "pulse")
    val dotAlpha by pulse.animateFloat(
        initialValue = 1f, targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "dotAlpha",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .alpha(if (running) dotAlpha else 1f)
                .background(if (running) AccentColor else MutedColor, CircleShape),
        )
        Text(
            fmtTime(timer.elapsedSec),
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold,
            color = TextColor,
        )
    }
}

/**
 * Rest countdown with a draining ring. Ticks itself per frame from the
 * deadline (the parent passes stable anchors, so the countdown never forces
 * the exercise list to recompose), and gives 3-2-1 haptic ticks at the end.
 */
@Composable
private fun RestTimerPill(
    deadlineMs: Long,
    totalMs: Long,
    onAddRest: () -> Unit,
    onSkipRest: () -> Unit,
) {
    val context = LocalContext.current
    var nowMs by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(deadlineMs) {
        while (true) {
            withFrameMillis { nowMs = SystemClock.elapsedRealtime() }
        }
    }
    val remainingMs = (deadlineMs - nowMs).coerceAtLeast(0L)
    val fraction = if (totalMs > 0) (remainingMs.toFloat() / totalMs).coerceIn(0f, 1f) else 0f
    val remainingSec = ((remainingMs + 999) / 1000L).toInt()

    // Countdown haptics for the last three seconds — the lifter shouldn't have
    // to stare at the phone to know the rest is over.
    LaunchedEffect(remainingSec) {
        if (remainingSec in 1..3) Haptics.tick(context)
        if (remainingSec == 0) Haptics.repComplete(context)
    }

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
        Box(modifier = Modifier.size(42.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                drawArc(
                    color = SubtleFillColor,
                    startAngle = -90f, sweepAngle = 360f, useCenter = false, style = stroke,
                )
                drawArc(
                    color = AccentColor,
                    startAngle = -90f, sweepAngle = -360f * fraction, useCenter = false, style = stroke,
                )
            }
            Icon(Icons.Rounded.Timer, null, tint = AccentColor, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("REST TIMER", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = SubTextColor, letterSpacing = 0.3.sp)
            Text(fmtTime(remainingSec), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextColor)
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
    onSetType: (setIdx: Int, type: SetType) -> Unit,
    onAddSet: () -> Unit,
    onRemoveSet: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val muscleCol = muscleColor(exercise.group)
    val warmupCount = exercise.sets.count { it.type == SetType.WARMUP }
    val workingTotal = exercise.sets.count { it.type != SetType.WARMUP }
    val doneCount = exercise.sets.count { it.done && it.type != SetType.WARMUP }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, LineColor, RoundedCornerShape(18.dp))
            .background(CardColor),
    ) {
        // Card header
        val chevron by animateFloatAsState(if (exercise.open) 90f else 0f, Motion.spatialFloat, label = "chevron")
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
                    Text(
                        buildString {
                            if (warmupCount > 0) append("${warmupCount}W · ")
                            append("$doneCount/$workingTotal sets")
                        },
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MutedColor,
                    )
                }
            }
            Icon(
                Icons.Rounded.ChevronRight,
                null,
                tint = SubTextColor,
                modifier = Modifier.size(18.dp).rotate(chevron),
            )
        }

        // Sets table — springs open/closed instead of snapping
        AnimatedVisibility(
            visible = exercise.open,
            enter = expandVertically(Motion.spatial()) + fadeIn(Motion.effectsFloat),
            exit = shrinkVertically(Motion.spatial()) + fadeOut(Motion.effectsFloat),
        ) {
            Column(
                modifier = Modifier
                    .padding(start = 14.dp, end = 14.dp, bottom = 12.dp)
                    .animateContentSize(Motion.spatial()),   // smooth growth on add/remove set
            ) {
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

                var workingNum = 0
                exercise.sets.forEachIndexed { si, set ->
                    if (set.type != SetType.WARMUP) workingNum++
                    SetRow(
                        displayNumber = workingNum,
                        set = set,
                        canRemove = exercise.sets.size > 1,
                        onField = { key, value -> onField(si, key, value) },
                        onToggle = { onToggleSet(si) },
                        onSetType = { type -> onSetType(si, type) },
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
    displayNumber: Int,
    set: SetData,
    canRemove: Boolean,
    onField: (key: String, value: String) -> Unit,
    onToggle: () -> Unit,
    onSetType: (SetType) -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    var showTypeSheet by remember { mutableStateOf(false) }

    // Badge appearance per set type. Warmup → "W" (amber), failure → number (red),
    // normal → number (accent when done). Tapping it opens the type picker.
    val badgeText = if (set.type == SetType.WARMUP) "W" else "$displayNumber"
    val badgeColor = when (set.type) {
        SetType.WARMUP  -> WarmupAmber
        SetType.FAILURE -> FailureRed
        SetType.NORMAL  -> if (set.done) AccentColor else SubTextColor
    }
    val badgeBg by animateColorAsState(
        when (set.type) {
            SetType.WARMUP  -> WarmupAmber.copy(alpha = 0.16f)
            SetType.FAILURE -> FailureRed.copy(alpha = 0.14f)
            SetType.NORMAL  -> if (set.done) AccentColor.copy(alpha = 0.13f) else SubtleFillColor
        },
        Motion.effects(),
        label = "badgeBg",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Set number badge — tap to choose Warmup / Normal / Failure
        Box(
            modifier = Modifier
                .width(34.dp)
                .height(26.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(badgeBg)
                .clickable {
                    Haptics.tick(context)
                    showTypeSheet = true
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                badgeText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = badgeColor,
            )
        }

        if (showTypeSheet) {
            SetTypeSheet(
                current = set.type,
                onSelect = { type ->
                    Haptics.tick(context)
                    onSetType(type)
                    showTypeSheet = false
                },
                onDismiss = { showTypeSheet = false },
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
            onValueChange = { onField("weight", it.filter { c -> c.isDigit() || c == '.' }) },
            modifier = Modifier.weight(1f),
        )

        SetInput(
            value = set.reps,
            placeholder = "0",
            done = set.done,
            onValueChange = { onField("reps", it.filter { c -> c.isDigit() }) },
            modifier = Modifier.weight(1f),
            keyboardType = KeyboardType.Number,
        )

        // Done checkmark — fills with a springy pop when toggled on
        val checkBg by animateColorAsState(
            if (set.done) GoodColor else Color.Transparent, Motion.effects(), label = "checkBg",
        )
        val checkScale by animateFloatAsState(
            if (set.done) 1f else 0.78f, Motion.popFloat, label = "checkScale",
        )
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(checkBg)
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
                modifier = Modifier.size(16.dp).scale(checkScale),
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

/** Bottom sheet that slides up to pick a set's type. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetTypeSheet(
    current: SetType,
    onSelect: (SetType) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CardElevColor,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                "SET TYPE",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = MutedColor,
                letterSpacing = 0.6.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
            )
            SetTypeOption(
                badge = "W", accent = WarmupAmber,
                title = "Warmup set", subtitle = "Not counted in volume, sets or PRs",
                selected = current == SetType.WARMUP,
                onClick = { onSelect(SetType.WARMUP) },
            )
            Spacer(Modifier.height(8.dp))
            SetTypeOption(
                badge = "1", accent = AccentColor,
                title = "Normal set", subtitle = "Standard working set",
                selected = current == SetType.NORMAL,
                onClick = { onSelect(SetType.NORMAL) },
            )
            Spacer(Modifier.height(8.dp))
            SetTypeOption(
                badge = "F", accent = FailureRed,
                title = "Failure set", subtitle = "Taken to muscular failure",
                selected = current == SetType.FAILURE,
                onClick = { onSelect(SetType.FAILURE) },
            )
        }
    }
}

@Composable
private fun SetTypeOption(
    badge: String,
    accent: Color,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(if (selected) accent.copy(alpha = 0.12f) else SubtleFillColor)
            .border(
                1.dp,
                if (selected) accent.copy(alpha = 0.5f) else Color.Transparent,
                RoundedCornerShape(13.dp),
            )
            .clickable { onClick() }
            .padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(badge, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = accent)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextColor)
            Text(subtitle, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = MutedColor)
        }
        if (selected) {
            Icon(Icons.Rounded.Check, null, tint = accent, modifier = Modifier.size(20.dp))
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
    keyboardType: KeyboardType = KeyboardType.Decimal,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (done) Color.Transparent else SubtleFillColor),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
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

@Composable
private fun LiveStatChip(icon: ImageVector, text: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(99.dp))
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(12.dp))
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
    }
}
