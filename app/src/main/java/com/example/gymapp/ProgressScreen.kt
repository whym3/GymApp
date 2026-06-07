package com.example.gymapp

import androidx.compose.animation.core.*
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.gymapp.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val muscleOrder = listOf("Chest", "Back", "Legs", "Shoulders", "Arms", "Core")
private fun shortDate(millis: Long) = SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(millis))

@Composable
fun ProgressScreen() {
    var tab by remember { mutableStateOf("strength") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
            .statusBarsPadding(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(top = 12.dp)) {
            Text("Progress", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = TextColor)
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CardColor)
                    .border(1.dp, LineColor, RoundedCornerShape(14.dp))
                    .padding(4.dp),
            ) {
                listOf("strength" to "Strength", "body" to "Body").forEach { (id, label) ->
                    val on = tab == id
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (on) CardElevColor else Color.Transparent)
                            .clickable { tab = id }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (on) TextColor else MutedColor)
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (tab == "strength") StrengthTab() else BodyTab()
        }
    }
}

@Composable
private fun StrengthTab() {
    val workouts = WorkoutRepository.workouts.sortedBy { it.dateMillis }
    val options = workouts.flatMap { w -> w.exercises.map { it.name } }.distinct().sorted()

    if (options.isEmpty()) {
        EmptyState(
            icon = Icons.Rounded.TrendingUp,
            title = "No strength data yet",
            message = "Log a workout and your best set per session will be charted here.",
        )
        return
    }

    var selectedRaw by remember { mutableStateOf("") }
    val selected = selectedRaw.takeIf { it in options } ?: options.first()
    var ddOpen by remember { mutableStateOf(false) }

    val series = workouts.mapNotNull { w ->
        val best = w.exercises.filter { it.name == selected }
            .flatMap { it.sets }
            .mapNotNull { it.weight.toDoubleOrNull() }
            .maxOrNull()
        best?.let { w.dateMillis to it.toFloat() }
    }

    // Dropdown
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(13.dp))
                .border(1.dp, LineColor, RoundedCornerShape(13.dp))
                .background(CardColor)
                .clickable { ddOpen = !ddOpen }
                .padding(horizontal = 15.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(modifier = Modifier.size(8.dp).background(AccentColor, CircleShape))
                Text(selected, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextColor)
            }
            Icon(if (ddOpen) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.ChevronRight, null, tint = SubTextColor, modifier = Modifier.size(18.dp))
        }
        if (ddOpen) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = 56.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .border(1.dp, LineStrongColor, RoundedCornerShape(13.dp))
                    .background(CardElevColor)
                    .zIndex(10f),
            ) {
                options.forEach { o ->
                    val isSel = o == selected
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isSel) AccentSoftColor else Color.Transparent)
                            .clickable { selectedRaw = o; ddOpen = false }
                            .padding(horizontal = 15.dp, vertical = 12.dp),
                    ) {
                        Text(o, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (isSel) AccentColor else TextColor)
                    }
                }
            }
        }
    }

    // Chart card
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, LineColor, RoundedCornerShape(18.dp))
            .background(CardColor)
            .padding(horizontal = 12.dp, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column {
                Text("BEST SET · KG", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MutedColor, letterSpacing = 0.3.sp)
                Text(
                    if (series.isEmpty()) "—" else "${series.last().second.toInt()} kg",
                    fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextColor,
                )
            }
            if (series.size >= 2) {
                val delta = series.last().second - series.first().second
                val sign = if (delta >= 0) "+" else ""
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.Rounded.TrendingUp, null, tint = GoodColor, modifier = Modifier.size(15.dp))
                    Text("$sign${trimF(delta)} kg", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = GoodColor)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (series.size >= 2) {
            LineChartView(
                data = series.map { it.second },
                labels = series.map { shortDate(it.first) },
                color = AccentColor,
                modifier = Modifier.fillMaxWidth().height(150.dp),
            )
        } else {
            ChartPlaceholder("Log this exercise at least twice to see a trend")
        }
    }

    // Mini stats
    val best = series.maxByOrNull { it.second }?.second
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MiniStat("Best", if (best != null) "${best.toInt()} kg" else "—", Modifier.weight(1f))
        MiniStat("Sessions", "${series.size}", Modifier.weight(1f))
        MiniStat(
            "Est. 1RM",
            if (best != null) "${(best * 1.07).toInt()} kg" else "—",
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun BodyTab() {
    val workouts = WorkoutRepository.workouts
    val weights = UserStore.weightHistory   // (time, kg) — appended on each profile weight change

    // Bodyweight card
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, LineColor, RoundedCornerShape(18.dp))
            .background(CardColor)
            .padding(horizontal = 12.dp, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column {
                Text("BODYWEIGHT · KG", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MutedColor, letterSpacing = 0.3.sp)
                Text(
                    weights.lastOrNull()?.let { "${trimF(it.second)} kg" } ?: "—",
                    fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextColor,
                )
            }
            if (weights.size >= 2) {
                val delta = weights.last().second - weights.first().second
                val sign = if (delta >= 0) "+" else ""
                Text("$sign${trimF(delta)} kg", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = GoodColor)
            }
        }
        Spacer(Modifier.height(8.dp))
        if (weights.size >= 2) {
            LineChartView(
                data = weights.map { it.second.toFloat() },
                labels = weights.map { shortDate(it.first) },
                color = MuscleBack,
                modifier = Modifier.fillMaxWidth().height(150.dp),
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(modifier = Modifier.size(6.dp).background(GoodColor, CircleShape))
                Text("Updated from your profile", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MutedColor)
            }
        } else {
            ChartPlaceholder("Set your weight in Profile — the graph builds from each change")
        }
    }

    // BMI card
    val bmi = UserStore.bmi
    if (bmi != null) {
        val category = bmiCategory(bmi)
        val catColor = when (category) {
            "Normal" -> GoodColor
            "Underweight" -> MuscleCore
            "Overweight" -> MuscleArms
            else -> MuscleChest
        }
        val h = UserStore.heightCm
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .border(1.dp, LineColor, RoundedCornerShape(18.dp))
                .background(CardColor)
                .padding(16.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Column {
                    Text("BODY MASS INDEX", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MutedColor, letterSpacing = 0.3.sp)
                    Text(String.format(Locale.US, "%.1f", bmi), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextColor)
                }
                Text(
                    category, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = catColor,
                    modifier = Modifier
                        .background(catColor.copy(alpha = 0.13f), RoundedCornerShape(99.dp))
                        .padding(horizontal = 11.dp, vertical = 5.dp),
                )
            }
            if (h != null && weights.size >= 2) {
                val m = h / 100.0
                val firstBmi = weights.first().second / (m * m)
                val deltaBmi = bmi - firstBmi
                val sign = if (deltaBmi >= 0) "+" else ""
                Spacer(Modifier.height(6.dp))
                Text("$sign${String.format(Locale.US, "%.1f", deltaBmi)} since first entry", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MutedColor)
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .border(1.dp, LineColor, RoundedCornerShape(18.dp))
                .background(CardColor)
                .padding(16.dp),
        ) {
            Text("Body mass index", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextColor)
            Spacer(Modifier.height(4.dp))
            Text("Add your height and weight in Profile to calculate and track BMI.", fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = MutedColor)
        }
    }

    // Weekly volume by muscle group (last 7 days)
    val weekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
    val volByGroup = muscleOrder.map { g ->
        val vol = workouts.filter { it.dateMillis >= weekAgo }.sumOf { w ->
            w.exercises.filter { it.group == g }.sumOf { e ->
                e.sets.filter { it.done }.sumOf { s ->
                    (s.weight.toDoubleOrNull() ?: 0.0) * (s.reps.toIntOrNull() ?: 0)
                }
            }
        }
        g to vol
    }
    val maxVol = (volByGroup.maxOfOrNull { it.second } ?: 0.0).coerceAtLeast(1.0)
    val animProgress by animateFloatAsState(targetValue = 1f, animationSpec = tween(900), label = "bars")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, LineColor, RoundedCornerShape(18.dp))
            .background(CardColor)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Text("Weekly volume", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = TextColor)
        Text("by muscle group · last 7 days", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MutedColor)

        if (volByGroup.all { it.second == 0.0 }) {
            Text("No volume logged this week yet", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MutedColor)
        } else {
            volByGroup.forEach { (group, vol) ->
                val col = muscleColor(group)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(group, modifier = Modifier.width(72.dp), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = SubTextColor)
                    Box(
                        modifier = Modifier
                            .weight(1f).height(10.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(LineColor),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth((vol / maxVol).toFloat() * animProgress)
                                .background(col, RoundedCornerShape(99.dp)),
                        )
                    }
                    Text(
                        if (vol >= 1000) String.format(Locale.US, "%.1fk", vol / 1000) else vol.toInt().toString(),
                        modifier = Modifier.width(40.dp), textAlign = TextAlign.End,
                        fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = TextColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, LineColor, RoundedCornerShape(14.dp))
            .background(CardColor)
            .padding(12.dp),
    ) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextColor)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = MutedColor)
    }
}

@Composable
private fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, LineColor, RoundedCornerShape(18.dp))
            .background(CardColor)
            .padding(vertical = 40.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(56.dp).background(CardElevColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = MutedColor, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextColor)
        Spacer(Modifier.height(6.dp))
        Text(message, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = SubTextColor, textAlign = TextAlign.Center, lineHeight = 19.sp)
    }
}

@Composable
private fun ChartPlaceholder(message: String) {
    Box(
        modifier = Modifier.fillMaxWidth().height(150.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(message, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MutedColor, textAlign = TextAlign.Center)
    }
}

private fun trimF(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else String.format(Locale.US, "%.1f", v)
private fun trimF(v: Float): String = trimF(v.toDouble())

// ── Canvas line chart ─────────────────────────────────────────────────────────

@Composable
private fun LineChartView(
    data: List<Float>,
    labels: List<String>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()

    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val padX = 14f; val padTop = 16f; val padBot = 26f

        val minV = data.minOrNull() ?: 0f
        val maxV = data.maxOrNull() ?: 1f
        val range = (maxV - minV).coerceAtLeast(0.01f)
        val lo = minV - range * 0.35f
        val hi = maxV + range * 0.25f

        val pts = data.mapIndexed { i, v ->
            Offset(
                x = padX + (i.toFloat() / (data.size - 1)) * (w - padX * 2),
                y = padTop + (1f - (v - lo) / (hi - lo)) * (h - padTop - padBot),
            )
        }

        listOf(0f, 0.5f, 1f).forEach { t ->
            val y = padTop + t * (h - padTop - padBot)
            drawLine(Color(0x14888888), Offset(padX, y), Offset(w - padX, y), strokeWidth = 1f)
        }

        val linePath = smoothCatmullRom(pts)
        val areaPath = Path().apply {
            addPath(linePath)
            lineTo(pts.last().x, h - padBot)
            lineTo(pts.first().x, h - padBot)
            close()
        }
        drawPath(
            areaPath,
            Brush.verticalGradient(
                colors = listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0f)),
                startY = padTop, endY = h - padBot,
            ),
        )
        drawPath(linePath, color = color, style = Stroke(width = 2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))

        pts.forEachIndexed { i, pt ->
            val isLast = i == pts.size - 1
            val r = if (isLast) 5f else 3.2f
            drawCircle(if (isLast) color else AppPalette.bgElev, r, pt)
            drawCircle(color, r, pt, style = Stroke(2f))
        }

        labels.forEachIndexed { i, lbl ->
            val m = textMeasurer.measure(lbl, TextStyle(fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, color = MutedColor))
            drawText(
                textMeasurer = textMeasurer, text = lbl,
                topLeft = Offset(pts[i].x - m.size.width / 2f, h - padBot + 6f),
                style = TextStyle(fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, color = MutedColor),
            )
        }
    }
}

private fun smoothCatmullRom(pts: List<Offset>): Path {
    val path = Path()
    if (pts.size < 2) return path
    path.moveTo(pts[0].x, pts[0].y)
    for (i in 0 until pts.size - 1) {
        val p0 = if (i > 0) pts[i - 1] else pts[i]
        val p1 = pts[i]
        val p2 = pts[i + 1]
        val p3 = if (i + 2 < pts.size) pts[i + 2] else p2
        val c1x = p1.x + (p2.x - p0.x) / 6f
        val c1y = p1.y + (p2.y - p0.y) / 6f
        val c2x = p2.x - (p3.x - p1.x) / 6f
        val c2y = p2.y - (p3.y - p1.y) / 6f
        path.cubicTo(c1x, c1y, c2x, c2y, p2.x, p2.y)
    }
    return path
}
