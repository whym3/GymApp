package com.example.gymapp

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gymapp.ui.theme.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle as DayTextStyle
import java.util.Locale

@Composable
fun HeartRateScreen(
    manager: HealthConnectManager,
    onBack: () -> Unit,
) {
    var latestBpm by remember { mutableStateOf<Long?>(null) }
    var samples by remember { mutableStateOf<List<Pair<Instant, Long>>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // readLatestHeartRate searches all history — same source as the Home card
        latestBpm = manager.readLatestHeartRate()
        samples = manager.readHeartRateSamples(7)
        loaded = true
    }

    val zone = remember { ZoneId.systemDefault() }
    val bpms = samples.map { it.second }
    // Use the dedicated latest-reading call so it matches the Home card exactly
    val latest = latestBpm ?: bpms.lastOrNull()

    // Average bpm per day, oldest → newest.
    val daily: List<Pair<LocalDate, Int>> = samples
        .groupBy { it.first.atZone(zone).toLocalDate() }
        .toSortedMap()
        .map { (date, list) -> date to (list.map { it.second }.average().toInt()) }

    Column(modifier = Modifier.fillMaxSize().background(BgColor)) {
        DetailTopBar(title = "Heart rate", subtitle = "Last 7 days", onBack = onBack)

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
                    modifier = Modifier.size(54.dp).background(MuscleChest.copy(alpha = 0.13f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.MonitorHeart, null, tint = MuscleChest, modifier = Modifier.size(28.dp))
                }
                Column {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(latest?.toString() ?: "—", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = TextColor)
                        Text("bpm", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = SubTextColor, modifier = Modifier.padding(bottom = 6.dp))
                    }
                    Text(
                        if (latest != null) "Latest reading" else "No readings this week",
                        fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MutedColor,
                    )
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
                Text(
                    "DAILY AVERAGE · BPM",
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MutedColor,
                    letterSpacing = 0.3.sp, modifier = Modifier.padding(horizontal = 4.dp),
                )
                Spacer(Modifier.height(10.dp))
                if (daily.size >= 2) {
                    HeartRateChart(daily = daily, modifier = Modifier.fillMaxWidth().height(185.dp))
                } else {
                    Box(modifier = Modifier.fillMaxWidth().height(185.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (!loaded) "Loading…"
                            else "Not enough heart-rate data this week",
                            fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MutedColor, textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            // Stats (over all samples in the last 7 days)
            if (bpms.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .border(1.dp, LineColor, RoundedCornerShape(18.dp))
                        .background(CardColor),
                ) {
                    HrStat("${bpms.minOrNull() ?: 0}", "Min", Modifier.weight(1f))
                    Box(modifier = Modifier.width(1.dp).height(54.dp).background(LineColor).align(Alignment.CenterVertically))
                    HrStat("${bpms.average().toInt()}", "Average", Modifier.weight(1f))
                    Box(modifier = Modifier.width(1.dp).height(54.dp).background(LineColor).align(Alignment.CenterVertically))
                    HrStat("${bpms.maxOrNull() ?: 0}", "Max", Modifier.weight(1f))
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(modifier = Modifier.size(6.dp).background(GoodColor, CircleShape))
                    Text("Synced from Health Connect", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MutedColor)
                }
            }
        }
    }
}

@Composable
private fun HrStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextColor)
        Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = MutedColor)
    }
}

@Composable
private fun HeartRateChart(
    daily: List<Pair<LocalDate, Int>>,
    modifier: Modifier = Modifier,
) {
    val color = MuscleChest
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        // Use dp→px so padding is screen-density independent and labels never clip
        val padX = 16.dp.toPx()
        val padTop = 16.dp.toPx()
        val padBot = 30.dp.toPx()  // room for 9.5sp labels + 6px gap above

        val data = daily.map { it.second.toFloat() }
        val minV = data.minOrNull() ?: 0f
        val maxV = data.maxOrNull() ?: 0f
        val range = (maxV - minV).coerceAtLeast(1f)
        val lo = minV - range * 0.25f
        val hi = maxV + range * 0.25f

        val pts = data.mapIndexed { i, v ->
            Offset(
                x = padX + (i.toFloat() / (data.size - 1)) * (w - padX * 2),
                y = padTop + (1f - (v - lo) / (hi - lo)) * (h - padTop - padBot),
            )
        }

        listOf(0f, 0.5f, 1f).forEach { t ->
            val y = padTop + t * (h - padTop - padBot)
            drawLine(androidx.compose.ui.graphics.Color(0x14888888), Offset(padX, y), Offset(w - padX, y), strokeWidth = 1f)
        }

        val line = Path().apply {
            moveTo(pts.first().x, pts.first().y)
            for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
        }
        val area = Path().apply {
            addPath(line)
            lineTo(pts.last().x, h - padBot)
            lineTo(pts.first().x, h - padBot)
            close()
        }
        drawPath(
            area,
            Brush.verticalGradient(
                colors = listOf(color.copy(alpha = 0.25f), color.copy(alpha = 0f)),
                startY = padTop, endY = h - padBot,
            ),
        )
        drawPath(line, color = color, style = Stroke(width = 2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))

        pts.forEachIndexed { i, pt ->
            val isLast = i == pts.size - 1
            drawCircle(if (isLast) color else AppPalette.bgElev, if (isLast) 4.5f else 3f, pt)
            drawCircle(color, if (isLast) 4.5f else 3f, pt, style = Stroke(1.6f))
        }

        // Day-of-week labels under each point
        val style = TextStyle(fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, color = MutedColor)
        daily.forEachIndexed { i, (date, _) ->
            val lbl = date.dayOfWeek.getDisplayName(DayTextStyle.NARROW, Locale.getDefault())
            val m = textMeasurer.measure(lbl, style)
            drawText(textMeasurer, lbl, topLeft = Offset(pts[i].x - m.size.width / 2f, h - padBot + 6f), style = style)
        }
    }
}
